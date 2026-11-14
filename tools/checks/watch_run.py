#!/usr/bin/env python3
"""Waits for the Dev Builds run of one commit on GitHub and prints why it failed.

The build only happens on GitHub, so what a change did is read from there: the run the commit
started, the conclusion of each of its jobs, and the compiler errors the failed ones carry as
annotations. Run it in the background and read its output, because the wait can outlast one command.

    python tools/checks/watch_run.py <sha> [--wait 540]
"""

import argparse
import json
import time
import urllib.request

REPOSITORY = "DevGBX9/Immersive-Titles"
WORKFLOW = "Dev Builds"


def get(path):
    """A GitHub api response, retried while the network is down, or {} if it stays down."""
    for _ in range(8):
        try:
            request = urllib.request.Request(
                "https://api.github.com" + path,
                headers={"Accept": "application/vnd.github+json", "User-Agent": "immersive-titles"},
            )
            with urllib.request.urlopen(request, timeout=25) as response:
                return json.load(response)
        except Exception:
            time.sleep(8)

    return {}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sha")
    parser.add_argument("--wait", type=int, default=540)
    arguments = parser.parse_args()

    deadline = time.time() + arguments.wait
    run = None

    while time.time() < deadline:
        runs = get(f"/repos/{REPOSITORY}/actions/runs?per_page=15").get("workflow_runs", [])
        runs = [r for r in runs if r["name"] == WORKFLOW and r["head_sha"].startswith(arguments.sha)]
        if runs:
            run = runs[0]
            print(time.strftime("%H:%M:%S"), run["run_number"], run["status"], run["conclusion"], flush=True)
            if run["status"] == "completed":
                break
        time.sleep(20)

    if not run:
        print(f"no {WORKFLOW} run for {arguments.sha}")
        return 1

    jobs = get(f"/repos/{REPOSITORY}/actions/runs/{run['id']}/jobs").get("jobs", [])
    for job in jobs:
        print(" ", job["name"], "->", job["conclusion"])

    for job in jobs:
        if job["conclusion"] != "failure":
            continue

        for annotation in get(f"/repos/{REPOSITORY}/check-runs/{job['id']}/annotations") or []:
            message = annotation["message"]
            if "ubuntu-latest label" in message:
                continue
            print(message.replace("%0A", "\n"))

    return 0 if run["conclusion"] == "success" else 1


if __name__ == "__main__":
    raise SystemExit(main())

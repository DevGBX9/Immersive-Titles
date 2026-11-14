#!/usr/bin/env python3
"""Checks that every Java source of the mod is structurally balanced, branch by branch.

This is a text check and not a compiler: it strips comments, string and character literals and then
counts braces, parentheses and brackets, so a file edited by hand cannot be left with a brace that
never closes. It proves nothing about types or about the game's API - only a build on GitHub does
that - but it is the one thing that can be seen here without building.

The sources are preprocessed, so a `//#if` block holds two versions of the same code and its two
halves are counted apart: what has to balance is the file each version actually compiles, not the
two branches added together (a call whose arguments run over several lines and that is written once
per branch would otherwise read as two unclosed calls). The first pass counts the branch that is
written out, the second the one commented with `//$$`.

    python tools/checks/check_java_shape.py
"""

import pathlib
import re
import sys

SOURCES = pathlib.Path("src/main/java")

OPENERS = {"{": "}", "(": ")", "[": "]"}
MARKER = re.compile(r"^//#(if|else|endif)\b")


def strip(text):
    """The file without its comments and literals, so braces inside them are not counted."""
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\])*'", "''", text)
    return text


def branches(text):
    """The two texts a preprocessed file stands for: the written branch and the commented one."""
    written, commented = [], []
    state = "written"  # which half of the `//#if` block is being read

    for line in text.split("\n"):
        marker = MARKER.match(line.strip())
        if marker:
            what = marker.group(1)
            if what == "if":
                state = "written"
            elif what == "else":
                state = "commented"
            else:
                state = "both"
            continue

        if line.startswith("//$$"):
            commented.append(line[4:])
            continue

        written.append(line)
        if state != "commented":
            commented.append(line)

    return strip("\n".join(written)), strip("\n".join(commented))


def main():
    failures = 0
    files = sorted(SOURCES.rglob("*.java"))

    for path in files:
        for name, body in zip(("written", "commented"), branches(path.read_text(encoding="utf-8"))):
            problems = [
                f"{name} branch: {body.count(opener)} {opener} against {body.count(closer)} {closer}"
                for opener, closer in OPENERS.items()
                if body.count(opener) != body.count(closer)
            ]

            if problems:
                failures += 1
                print(f"UNBALANCED {path}: {', '.join(problems)}")

    if failures:
        print(f"{failures} branch(es) of {len(files)} source(s) do not balance")
        return 1

    print(f"all {len(files)} sources balance, in both branches")
    return 0


if __name__ == "__main__":
    sys.exit(main())

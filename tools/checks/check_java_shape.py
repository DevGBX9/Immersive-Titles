#!/usr/bin/env python3
"""Checks that every Java source of the mod is structurally balanced, version by version.

This is a text check and not a compiler: it strips comments, string and character literals and then
counts braces, parentheses and brackets, so a file edited by hand cannot be left with a brace that
never closes. It proves nothing about types or about the game's API - only a build on GitHub does
that - but it is the one thing that can be seen here without building.

The sources are preprocessed, and a `//#if` block holds one version of the code per branch, so the
branches cannot be added together: a call whose arguments run over several lines and that is written
once per branch would read as two unclosed calls. The guards are therefore evaluated, once per game
version the mod builds for, the way the preprocessor evaluates them - `//#if` lines pick a branch, a
`//$$` line is code only while the branch it is in is not the one that is taken - and what has to
balance is the file each of those versions actually compiles.

    python tools/checks/check_java_shape.py
"""

import json
import pathlib
import re
import sys

SOURCES = pathlib.Path("src/main/java")
SETTINGS = pathlib.Path("settings.json")

OPENERS = {"{": "}", "(": ")", "[": "]"}
MARKER = re.compile(r"^//#(if|else|endif)\b")
CONDITION = re.compile(r"^MC\s*(>=|>|<=|<|==|!=)\s*([\d.]+)$")
STUB = "//$$"


def strip(text):
    """The file without its comments and literals, so braces inside them are not counted."""
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\])*'", "''", text)
    return text


def version(text):
    """A game version as a tuple, so that 26.1.2 sorts above 26.0 and 1.21.11 below 26.0."""
    return tuple(int(part) for part in text.strip().split("."))


def holds(condition, mc):
    """Whether `MC <comparison> <version>` holds for the given game version, and, or, negated."""
    condition = condition.strip()

    if "||" in condition:
        return any(holds(part, mc) for part in condition.split("||"))

    if "&&" in condition:
        return all(holds(part, mc) for part in condition.split("&&"))

    if condition.startswith("!"):
        return not holds(condition[1:], mc)

    match = CONDITION.match(condition)
    if not match:
        raise ValueError(f"cannot read the condition `{condition}`")

    operator, bound = match.group(1), version(match.group(2))
    return {
        ">=": mc >= bound,
        ">": mc > bound,
        "<=": mc <= bound,
        "<": mc < bound,
        "==": mc == bound,
        "!=": mc != bound,
    }[operator]


def compiled(text, mc):
    """The file as the preprocessor writes it for a game version, without its stubs."""
    lines = []
    stack = []

    for line in text.split("\n"):
        marker = MARKER.match(line.strip())
        if marker:
            what = marker.group(1)
            if what == "if":
                stack.append(holds(line.strip()[len("//#if"):], mc))
            elif what == "else":
                if not stack:
                    raise ValueError(f"`//#else` without an `//#if`: {line!r}")
                stack[-1] = not stack[-1]
            elif stack:
                stack.pop()
            else:
                raise ValueError(f"`//#endif` without an `//#if`: {line!r}")
            continue

        # a stub is the same code with its marker in front, so that it can be commented back in
        # when the branch it belongs to is the one that is taken: the marker sits behind the
        # indentation of the line it stands for, and the line is read without it
        code = line.lstrip()[len(STUB):] if line.lstrip().startswith(STUB) else line
        if all(stack):
            lines.append(code)

    return strip("\n".join(lines))


def versions():
    """The game versions the mod builds for, newest first, as the build lists them."""
    settings = json.loads(SETTINGS.read_text(encoding="utf-8"))
    return [version(v) for v in settings["versions"]]


def main():
    failures = 0
    files = sorted(SOURCES.rglob("*.java"))
    targets = versions()

    for path in files:
        text = path.read_text(encoding="utf-8")
        for mc in targets:
            try:
                body = compiled(text, mc)
            except ValueError as error:
                failures += 1
                print(f"UNREADABLE {path} at {'.'.join(map(str, mc))}: {error}")
                continue

            problems = [
                f"{opener} {body.count(opener)} against {body.count(closer)} {closer}"
                for opener, closer in OPENERS.items()
                if body.count(opener) != body.count(closer)
            ]

            if problems:
                failures += 1
                print(f"UNBALANCED {path} at {'.'.join(map(str, mc))}: {', '.join(problems)}")

    if failures:
        print(f"{failures} version(s) of the sources do not balance")
        return 1

    print(f"all {len(files)} sources balance, at every one of the {len(targets)} versions")
    return 0


if __name__ == "__main__":
    sys.exit(main())

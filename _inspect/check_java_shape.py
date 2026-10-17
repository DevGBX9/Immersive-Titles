#!/usr/bin/env python3
"""Checks that every Java source of the mod is structurally balanced.

This is a text check and not a compiler: it strips comments, string and character literals and then
counts braces, parentheses and brackets, so a file edited by hand cannot be left with a brace that
never closes. It proves nothing about types or about the game's API - only a build on GitHub does
that - but it is the one thing that can be seen here without building.

    python _inspect/check_java_shape.py
"""

import pathlib
import re
import sys

SOURCES = pathlib.Path("src/main/java")

OPENERS = {"{": "}", "(": ")", "[": "]"}


def strip(text):
    """The file without its comments and literals, so braces inside them are not counted."""
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\])*'", "''", text)
    return text


def main():
    failures = 0
    files = sorted(SOURCES.rglob("*.java"))

    for path in files:
        body = strip(path.read_text(encoding="utf-8"))
        problems = []

        for opener, closer in OPENERS.items():
            if body.count(opener) != body.count(closer):
                problems.append(f"{body.count(opener)} {opener} against {body.count(closer)} {closer}")

        if problems:
            failures += 1
            print(f"UNBALANCED {path}: {', '.join(problems)}")

    if failures:
        print(f"{failures} of {len(files)} source(s) do not balance")
        return 1

    print(f"all {len(files)} sources balance")
    return 0


if __name__ == "__main__":
    sys.exit(main())

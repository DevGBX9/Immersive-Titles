#!/usr/bin/env python3
"""Checks the global title customization: model, geometry, overlay and editor."""
import json
import pathlib
import re
import sys

CLIENT = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client")
CUSTOMIZATION = CLIENT / "TitleCustomization.java"
GEOMETRY = CLIENT / "TitleGeometry.java"
OVERLAY = CLIENT / "TitleOverlay.java"
SCREEN = CLIENT / "TitleCustomizationScreen.java"
COLLECTION = CLIENT / "BiomeCollectionScreen.java"
LANG = pathlib.Path("src/main/resources/assets/immersive_titles/lang/en_us.json")
GEAR = pathlib.Path("src/main/resources/assets/immersive_titles/textures/gui/gear.png")

failures = []

def check(label, condition):
    if condition:
        print(f"  ok   {label}")
    else:
        print(f"  FAIL {label}")
        failures.append(label)

def body(path):
    return path.read_text(encoding="utf-8") if path.is_file() else ""

def compact(text):
    return " ".join(text.split())

def has(path, needle, label):
    check(label, compact(needle) in compact(body(path)))

def method(text, signature):
    pattern = r"\s+".join(re.escape(part) for part in signature.split())
    match = re.search(pattern + r"\s*\{(.*?)\n\t\}", text, re.S)
    return match.group(1) if match else ""

def config_checks():
    print("the global customization model:")
    text = body(CUSTOMIZATION)
    check("TitleCustomization.java exists", bool(text))
    has(CUSTOMIZATION, "public final class TitleCustomization", "the model is a public final class")
    has(CUSTOMIZATION, "public static final int VERSION = 1;", "the schema version is 1")
    has(CUSTOMIZATION, 'resolve("immersive_titles.json")', "the file is config/immersive_titles.json")
    has(CUSTOMIZATION, "FabricLoader.getInstance().getConfigDir()", "the file is under the Fabric config dir")
    for name, value in (("PLATE_MIN_SCALE", "0.50F"), ("PLATE_MAX_SCALE", "1.75F"),
                        ("PLATE_MAX_OFFSET", "0.40F"), ("NAME_MIN_SCALE", "0.50F"),
                        ("NAME_MAX_SCALE", "1.50F"), ("NAME_MAX_OFFSET", "0.50F"),
                        ("CORNER_MIN_SCALE", "0.50F"), ("CORNER_MAX_SCALE", "3.00F")):
        has(CUSTOMIZATION, f"{name} = {value}", f"{name} is {value}")
    for key in ('"version"', '"plate"', '"name"', '"corner"', '"offsetX"', '"offsetY"', '"scale"'):
        has(CUSTOMIZATION, key, f"the schema writes {key}")
    has(CUSTOMIZATION, "Float.isFinite", "non-finite values are rejected per field")
    has(CUSTOMIZATION, "public static TitleCustomization live()", "the live instance is readable")
    has(CUSTOMIZATION, "public static void replace(", "the live instance can be replaced")
    has(CUSTOMIZATION, "public TitleCustomization copy()", "the working copy is creatable")
    has(CUSTOMIZATION, "public void reset()", "the working copy can be reset")
    has(CUSTOMIZATION, "public boolean save()", "the model saves itself")
    has(CUSTOMIZATION, "StandardCopyOption.ATOMIC_MOVE", "the save is atomic")
    has(CUSTOMIZATION, "AtomicMoveNotSupportedException", "a filesystem without atomic move still saves")
    loader = method(text, "public static void load()")
    check("the loader never saves", loader != "" and "save()" not in loader)

def main():
    config_checks()
    if failures:
        print(f"\n{len(failures)} title customization check(s) failed")
        return 1
    print("\nthe title customization model holds")
    return 0

if __name__ == "__main__":
    sys.exit(main())

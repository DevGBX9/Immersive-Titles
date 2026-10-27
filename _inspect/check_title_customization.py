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

def geometry_checks():
    print("the shared title geometry:")
    text = body(GEOMETRY)
    check("TitleGeometry.java exists", bool(text))
    for record in (
        "record Plate(int x, int y, int width, int height, float scale)",
        "record Box(float left, float top, float right, float bottom)",
        "record Name(float centerX, float centerY, float scale, Box box, "
        "float roomCenterX, float roomCenterY, float roomWidth, float roomHeight)",
        "record Corner(int x, int baseline, int kickerX, int kickerY, float nameScale, "
        "float kickerScale, int kickerSpacing, int lineGap, Box box)",
        "record Parts(Component kicker, Component name)",
    ):
        has(GEOMETRY, record, record.split("(")[0].replace("record ", "the ") + " record exists")
    for signature in (
        "static Plate plate(int screenWidth, int screenHeight, TitleCustomization values)",
        "static Name name(Plate plate, Font font, Component text, TitleCustomization values)",
        "static Parts split(Component title)",
        "static Corner corner(int screenWidth, int screenHeight, Font font, Component kicker, "
        "Component name, TitleCustomization values)",
        "static Corner cornerRaw(int screenWidth, int screenHeight, Font font, Component kicker, "
        "Component name, TitleCustomization values)",
    ):
        has(GEOMETRY, signature, signature.split("(")[0] + " is declared")
    has(GEOMETRY, "Corner raw = cornerRaw(", "the drawn corner is the raw corner plus its clamp")
    # the default path keeps today's exact expressions and order
    has(GEOMETRY, "Math.min(screenWidth / (float) CONTENT_WIDTH, screenHeight / (float) CONTENT_HEIGHT)",
        "the plate fits the screen with the old expression")
    has(GEOMETRY, "(screenWidth - width) / 2 + Math.round(values.plateOffsetX() * screenWidth)",
        "the plate is centred with integer halving before the offset")
    has(GEOMETRY, "NAME_ROOM_HEIGHT * scale * NAME_MAX_HEIGHT / FONT_CAP_HEIGHT",
        "the name height fit keeps its old order")
    has(GEOMETRY, "int textWidth = Math.max(1, font.width(text))",
        "the name width is the font's, at least one pixel")
    has(GEOMETRY, "NAME_ROOM_WIDTH * scale * NAME_MAX_WIDTH / textWidth",
        "the name width fit keeps its old order")
    has(GEOMETRY, "(NAME_CENTER_Y - FRAME_CROP) * scale", "the name centre subtracts the frame crop")
    has(GEOMETRY, "auto * values.nameScale()", "the name multiplier is applied to the automatic fit")
    has(GEOMETRY, "roomWidth / textWidth", "the name is hard-capped to the room width")
    has(GEOMETRY, "roomHeight / FONT_CAP_HEIGHT", "the name is hard-capped to the room height")
    has(GEOMETRY, "CORNER_MARGIN + Math.round(values.cornerOffsetX() * screenWidth)",
        "the corner starts at the old margin plus its offset")
    has(GEOMETRY, "screenHeight - CORNER_BOTTOM_MARGIN - (int) (FONT_CAP_HEIGHT * nameScale)",
        "the corner baseline keeps the old expression")
    has(GEOMETRY, "Math.round(values.cornerOffsetY() * screenHeight)", "the corner baseline takes its offset")
    has(GEOMETRY, "font.lineHeight * nameScale", "the hotbar fallback still uses the line height")
    has(GEOMETRY, "values.cornerOffsetX() == 0.0F", "the exact-default corner path is guarded")
    has(GEOMETRY, "CORNER_SCREEN_MARGIN = 4", "the corner screen margin is 4 px")
    has(GEOMETRY, "(screenWidth - 8)", "the corner fit cap keeps the 4 px margins")
    overlay = body(OVERLAY)
    for name in ("FRAME_CROP", "CONTENT_WIDTH", "CONTENT_HEIGHT", "NAME_CENTER_X", "NAME_CENTER_Y",
                 "NAME_ROOM_WIDTH", "NAME_ROOM_HEIGHT", "NAME_MAX_WIDTH", "NAME_MAX_HEIGHT",
                 "FONT_CAP_HEIGHT", "CORNER_MARGIN", "CORNER_BOTTOM_MARGIN", "CORNER_NAME_SCALE",
                 "CORNER_KICKER_SCALE", "CORNER_KICKER_SPACING", "CORNER_LINE_GAP", "CORNER_HUD_ROOM",
                 "CORNER_HUD_GAP", "CORNER_HUD_BAR_HALF"):
        check(f"{name} is package-private",
              re.search(rf"static final (?:int|float) {name}\b", overlay) is not None
              and re.search(rf"private static final (?:int|float) {name}\b", overlay) is None)

def overlay_checks():
    print("the overlay through the shared geometry:")
    text = body(OVERLAY)
    has(OVERLAY, "private static TitleCustomization revealCustomization", "the reveal keeps a snapshot")
    has(OVERLAY, "private static TitleCustomization cornerCustomization", "the corner keeps a snapshot")
    has(OVERLAY, "revealCustomization = TitleCustomization.live().copy();",
        "the reveal snapshots the live values when it starts")
    has(OVERLAY, "cornerCustomization = TitleCustomization.live().copy();",
        "the corner snapshots the live values when it starts")
    has(OVERLAY, "TitleGeometry.plate(screenWidth, screenHeight, revealCustomization)",
        "the plate rectangle comes from the geometry")
    has(OVERLAY, "TitleGeometry.name(plate, font, revealName, revealCustomization)",
        "the name box comes from the geometry")
    has(OVERLAY, "TitleGeometry.corner(", "the corner comes from the geometry")
    has(OVERLAY, "TitleGeometry.split(", "the corner split comes from the geometry")
    check("the old corner row helper is gone",
          "private static int cornerNameRow" not in text)
    for signature in (
        "static void drawPlate(GuiGraphicsExtractor graphics, Identifier texture, "
        "TitleGeometry.Plate plate)",
        "static void drawName(GuiGraphicsExtractor graphics, Font font, Component name, "
        "TitleGeometry.Name geometry, int alpha)",
        "static void drawCorner(GuiGraphicsExtractor graphics, Font font, Component kicker, "
        "Component name, TitleGeometry.Corner geometry, int color)",
    ):
        has(OVERLAY, signature, signature.split("(")[0] + " is shared with the editor")
    has(OVERLAY, "drawPlate(graphics, texture, plate)", "the reveal draws the plate through the helper")
    has(OVERLAY, "drawName(graphics, font, revealName, geometry, alpha)",
        "the reveal draws the name through the helper")
    has(OVERLAY, "drawCorner(graphics, font, cornerKicker, cornerName, geometry, color)",
        "the corner draws through the helper")
    has(pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/WandererTitlesClient.java"),
        "TitleCustomization.load();", "the client loads the global file")
    check("the config loads before the overlay is initialized",
          re.search(r"TitleCustomization\.load\(\);\s*TitleOverlay\.initialize\(\);",
                    body(pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/"
                                      "WandererTitlesClient.java"))) is not None)

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
    geometry_checks()
    config_checks()
    overlay_checks()
    if failures:
        print(f"\n{len(failures)} title customization check(s) failed")
        return 1
    print("\nthe title customization model holds")
    return 0

if __name__ == "__main__":
    sys.exit(main())

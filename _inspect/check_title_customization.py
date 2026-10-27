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

def screen_checks():
    print("the customization screen:")
    text = body(SCREEN)
    check("TitleCustomizationScreen.java exists", bool(text))
    has(SCREEN, "public final class TitleCustomizationScreen extends Screen",
        "the editor is a normal screen")
    has(SCREEN, "private final Screen parent", "the editor holds the collection it came from")
    has(SCREEN, "TitleCustomization.live().copy()", "the editor works on a copy")
    has(SCREEN, "height >= 210", "the hints are dropped below 210 px")
    has(SCREEN, "Math.min(availableWidth / (float) width, availableHeight / (float) height)",
        "the stage keeps the screen's aspect")
    has(SCREEN, "0xFF0E0E12", "the stage has its near-black backdrop")
    has(SCREEN, "enableScissor(stageLeft, stageTop", "the preview is clipped to the stage")
    has(SCREEN, "frame_0100.png", "the preview uses the real settled frame")
    has(SCREEN, "TitleOverlay.drawPlate(", "the preview draws the real plate")
    has(SCREEN, "TitleOverlay.drawName(", "the preview draws the real name")
    has(SCREEN, "TitleOverlay.drawCorner(", "the preview draws the real corner title")
    has(SCREEN, "TitleGeometry.name(plate, font, SAMPLE, working)",
        "the large name is fitted from the whole sample")
    has(SCREEN, "TitleOverlay.drawName(graphics, font, SAMPLE, name, 255)",
        "the large name draws the whole sample")
    has(SCREEN, "TitleOverlay.drawCorner(graphics, font, parts.kicker(), parts.name(), corner",
        "the corner title draws the split sample")
    has(SCREEN, "screen.immersive_titles.customize.sample", "the preview uses the sample key")
    has(SCREEN, "ChatFormatting.BOLD", "the sample is bold")
    has(SCREEN, "screen.immersive_titles.customize.hint.move", "the move hint is drawn")
    has(SCREEN, "screen.immersive_titles.customize.hint.save", "the save hint is drawn")
    has(SCREEN, "working.save()", "Save writes the working copy")
    has(SCREEN, "TitleCustomization.replace(working)", "a successful Save replaces the live values")
    has(SCREEN, "SAVED_MESSAGE_MS = 2000L", "the saved message lasts 2000 ms")
    has(SCREEN, "working.reset()", "Reset returns the working copy to defaults")
    has(SCREEN, "minecraft.gui.setScreen(parent)", "Done and Escape return to the collection")
    save_body = method(text, "private void save()")
    reset_body = method(text, "private void reset()")
    close_body = method(text, "public void onClose()")
    check("Save keeps the screen open", save_body != "" and "setScreen" not in save_body)
    check("Reset does not save", reset_body != "" and "save()" not in reset_body)
    check("closing does not save", close_body != "" and "save()" not in close_body)
    check("the editor draws no background of its own",
          not any(part in text for part in ("extractBackground", "renderBackground", "fillGradient")))
    collection = body(COLLECTION)
    has(COLLECTION, "new TitleCustomizationScreen(this)", "the collection opens the editor with itself as parent")
    has(COLLECTION, "GEAR_SIZE = 16", "the gear is 16 px wide")
    has(COLLECTION, "GEAR_GAP = 8", "the gear sits 8 px right of Done")
    has(COLLECTION, "doneButton.getX() + doneButton.getWidth() + GEAR_GAP",
        "the gear is placed immediately right of Done with the gap")
    has(COLLECTION, "screen.immersive_titles.collection.customize", "the gear has its tooltip/narration key")
    has(COLLECTION, "drawGearIcon(graphics, customizeButton)", "the gear icon is painted over its face")
    has(COLLECTION, '"textures/gui/gear.png"', "the gear is a direct texture path")
    check("the gear is not a gui sprite", 'guiSprite("gui/gear")' not in collection)
    check("only the collection opens the editor",
          collection.count("new TitleCustomizationScreen(") == 1
          and "new TitleCustomizationScreen(" not in body(OVERLAY))

def lang_checks():
    print("the editor's language and gear:")
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    expected = {
        "screen.immersive_titles.collection.customize": "Customize titles",
        "screen.immersive_titles.customize.title": "Customize Titles",
        "screen.immersive_titles.customize.hint.move":
            "Drag to move - drag a corner or scroll to resize",
        "screen.immersive_titles.customize.hint.save":
            "Save keeps changes - closing without Save discards them",
        "screen.immersive_titles.customize.save": "Save",
        "screen.immersive_titles.customize.reset": "Reset",
        "screen.immersive_titles.customize.saved": "Saved",
        "screen.immersive_titles.customize.save_failed": "Could not save",
        "screen.immersive_titles.customize.sample": "PALE GARDEN",
    }
    for key, value in expected.items():
        check(f"{key} is {value!r}", lang.get(key) == value)
    from PIL import Image
    check("gear.png exists", GEAR.is_file())
    if GEAR.is_file():
        image = Image.open(GEAR).convert("RGBA")
        check("the gear is 16x16", image.size == (16, 16))
        pixels = list(image.getdata())
        check("the gear keeps its transparent corners", sum(1 for p in pixels if p[3] == 0) == 80)
        check("the gear has 176 opaque pixels", sum(1 for p in pixels if p[3] == 255) == 176)
        palette = {(27, 27, 32, 255), (42, 42, 50, 255), (201, 205, 212, 255), (237, 239, 242, 255)}
        check("the gear uses the four planned colours", {p for p in pixels if p[3] != 0} == palette)

def interaction_checks():
    print("the editor interactions:")
    text = body(SCREEN)
    has(SCREEN, "HANDLE_SIZE = 5", "a handle is 5 px")
    has(SCREEN, "HANDLE_HIT_RADIUS = 7", "a handle is grabbed within 7 screen px")
    has(SCREEN, "WHEEL_STEP = 1.05F", "one wheel notch is 1.05")
    has(SCREEN, "0xFF3B30", "selection is red")
    has(SCREEN, "private enum Element", "the three elements are an enum")
    has(SCREEN, "private enum Drag", "the drag kinds are an enum")
    has(SCREEN, "public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)",
        "the editor reads clicks")
    has(SCREEN, "public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)",
        "the editor reads drags")
    has(SCREEN, "public boolean mouseReleased(MouseButtonEvent event)", "the editor reads releases")
    has(SCREEN, "public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical)",
        "the editor reads the wheel")
    has(SCREEN, "super.mouseClicked(event, doubleClick)", "the buttons receive their clicks first")
    has(SCREEN, "stageLeft + HANDLE_SIZE / 2.0", "handles are kept inside the stage's left edge")
    has(SCREEN, "stageLeft + stageWidth - HANDLE_SIZE / 2.0",
        "handles are kept inside the stage's right edge")
    has(SCREEN, "drag == Drag.MOVE", "a move drag is handled")
    has(SCREEN, "drag == Drag.RESIZE", "a resize drag is handled")
    has(SCREEN, "dragStartOffsetX", "a move remembers its starting offset")
    has(SCREEN, "dragDenominatorX", "a move converts screen pixels back to its own units")
    has(SCREEN, "Math.hypot", "a resize measures the pointer's distance from the anchor")
    has(SCREEN, "resizeStartScale * ratio", "a resize scales from the scale it started at")
    has(SCREEN, "working.setCornerOffsetX(working.cornerOffsetX() +",
        "a corner resize keeps the block on its centre")
    has(SCREEN, "vertical > 0 ? WHEEL_STEP : 1.0F / WHEEL_STEP",
        "the wheel grows up and shrinks down")
    has(SCREEN, "private void clampStoredOffsets()",
        "the stored offsets are clamped to the drawn limits")
    has(SCREEN, "TitleGeometry.cornerRaw(", "the corner is measured before it is clamped")
    has(SCREEN, "clampStoredOffsets();", "edits clamp the stored offsets")
    edited = method(text, "private void edited()")
    check("an edit clears the save message", edited != "" and "message = null" in edited)
    hit = method(text, "private Element hitElement(double virtualX, double virtualY)")
    check("the click order is name, corner, plate",
          "Element.NAME" in hit and "Element.CORNER" in hit and "Element.PLATE" in hit
          and hit.index("Element.NAME") < hit.index("Element.CORNER") < hit.index("Element.PLATE"))
    released = method(text, "public boolean mouseReleased(MouseButtonEvent event)")
    check("releasing ends the drag and keeps the selection",
          "drag = Drag.NONE" in released and "selected = null" not in released)

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
    check("a missing version is repaired field by field",
          "if (version != null && (!version.isJsonPrimitive()" in text
          and "version == null ||" not in text)

def main():
    geometry_checks()
    config_checks()
    overlay_checks()
    screen_checks()
    interaction_checks()
    lang_checks()
    if failures:
        print(f"\n{len(failures)} title customization check(s) failed")
        return 1
    print("\nthe title customization model holds")
    return 0

if __name__ == "__main__":
    sys.exit(main())

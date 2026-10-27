# Global Title Customization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a global customization screen, reached from a gear in the Collection, where the large plate, the large name and the small corner title can be moved and scaled, with the real title assets and maths previewed and the result saved to `config/immersive_titles.json`.

**Architecture:** One mutable global `TitleCustomization` model is loaded once in the client entrypoint. A new shared `TitleGeometry` owns every layout formula and clamp, and both `TitleOverlay` and the new `TitleCustomizationScreen` draw through it. The editor uses the current GUI-scaled screen as its virtual coordinate space, scales that whole space into a stage, and manipulates a working copy of the model. Save writes the copy atomically and replaces the live model; closing without Save drops the copy.

**Tech Stack:** Java 25, Minecraft 26.x client APIs (`Screen`, `GuiGraphicsExtractor`, `Button`), Gson (Minecraft's bundled copy), Fabric Loader config directory, Python static checks, GitHub Actions build.

**Spec:** `docs/superpowers/specs/2026-10-27-title-customization-design.md`

## Global Constraints

- **Never build on the owner's machine.** No `gradlew`, no compile task, no daemon, no local jar. Verification here is Python text/image checks only.
- **The build happens on GitHub.** Commit and push to `origin/main`; the `Dev Builds` workflow must build both 26.2 and 26.3.
- **Both versions compile the same `src/main` sources.** Use only APIs already used by the shared client sources; no 26.3-only call.
- **Default rendering is pixel-identical.** With the default config, the plate rectangle, name centre/scale and corner baseline/scale must match today's expressions, including integer rounding.
- **One global file, not per world:** `<Fabric config dir>/immersive_titles.json`, schema version 1.
- **No new build dependency.** Gson is Minecraft's bundled library; do not edit `common.gradle`, `build.gradle` or `settings.json`.
- **Existing behaviour is untouched:** discovery, re-entry, structure visits, filters, tabs, reveal playback, pause-hold, sound and the Collection's screen transitions.
- **All static checks must pass.** There are 17 existing `_inspect/check_*.py` checks; this plan adds `check_title_customization.py`, making 18.
- **Anything over 30 MiB is not downloaded without the owner's explicit permission.** This plan expects no such download.
- **Commits are small and per task.** Every task ends with a commit on `main`; the final task pushes.

## Review Focus

These are the inputs and conditions the spec implies but no single formula test can prove; each one is pinned by a check in the task that owns the code, and by the owner's manual checklist.

1. **Hand-edited or partial config.** A missing `plate`, `name` or `corner` object, a missing field, `NaN`, `Infinity`, or `version: 2` must give the default for the affected field only, must not crash, and must not be written back until Save. Owning task: Task 1.
2. **Default look at extreme screens.** At 320x240, at 16:9 and at ultrawide, the plate rectangle, name box and corner baseline must be the same as today, and the hotbar fallback must still be reachable at exact defaults. Owning task: Tasks 2 and 3.
3. **A long real title against the short sample.** A name tuned against `PALE GARDEN` must be re-clamped at draw time so a longer real name stays inside the plate and inside its room. Owning task: Task 2.
4. **Window resize or GUI scale change while the editor is open.** The stage and buttons must re-layout in `init()`, the working copy and selection must survive, and handles must stay grabbable. Owning task: Tasks 4 and 5.
5. **Save failure and corrupt config.** A read-only config directory, a full disk or invalid JSON must show the error (or fall back to defaults on load), must not replace the live configuration and must not crash. Owning task: Tasks 1 and 4.

## File Structure

- **Create `src/main/java/com/devgx9/wanderers_titles/client/TitleCustomization.java`** — the global mutable values, their ranges, JSON load/save, per-field defaults and validation. No drawing, no Minecraft screen classes.
- **Create `src/main/java/com/devgx9/wanderers_titles/client/TitleGeometry.java`** — every layout formula and clamp, as records. Used by the overlay and the editor; owns no state and draws nothing.
- **Create `src/main/java/com/devgx9/wanderers_titles/client/TitleCustomizationScreen.java`** — the editor: stage layout, preview composition, selection, handles, drag/resize/wheel, Save/Reset/Done and messages.
- **Modify `src/main/java/com/devgx9/wanderers_titles/client/TitleOverlay.java`** — make the layout constants package-private, snapshot the customization when a reveal/corner title starts, compute through `TitleGeometry`, and expose the three drawing helpers the editor reuses.
- **Modify `src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java`** — add the gear `Button` after Done, paint its face and icon, open the editor with the Collection as parent.
- **Modify `src/main/java/com/devgx9/wanderers_titles/client/WandererTitlesClient.java`** — call `TitleCustomization.load()` before `TitleOverlay.initialize()`.
- **Modify `src/main/resources/assets/immersive_titles/lang/en_us.json`** — add the 9 keys from spec section 11.
- **Create `src/main/resources/assets/immersive_titles/textures/gui/gear.png`** — 16x16 RGBA gear.
- **Create `_inspect/check_title_customization.py`** — the one new static check, grown section by section across Tasks 1–5.
- **Create `docs/superpowers/plans/2026-10-27-title-customization.md`** — this plan.

---

### Task 1: The global values and their file

**Files:**
- Create: `src/main/java/com/devgx9/wanderers_titles/client/TitleCustomization.java`
- Create: `_inspect/check_title_customization.py`

**Interfaces:**
- Consumes: `WandererTitles.MOD_ID` and `WandererTitles.LOGGER`, both already public.
- Produces: the model every later task reads and writes:
  - `public static TitleCustomization live()` — the live instance.
  - `public static void replace(TitleCustomization values)` — copies `values` into the live instance.
  - `public static TitleCustomization defaults()` — a fresh default instance.
  - `public static void load()` — reads `file()` into the live instance; never writes.
  - `public static Path file()` — `<config dir>/immersive_titles.json`.
  - `public TitleCustomization copy()`.
  - `public void reset()`.
  - `public boolean save()` — writes this instance atomically; returns false and logs on failure.
  - Getters and setters, all setters clamping: `plateOffsetX/Y()`, `plateScale()`, `nameOffsetX/Y()`, `nameScale()`, `cornerOffsetX/Y()`, `cornerScale()` and their `set...` forms.

**Values (exact, from spec 7.7 and 8.2):**

| Field | Default | Stored range |
|---|---|---|
| `plateOffsetX`, `plateOffsetY` | `0.0F` | `-0.40F .. 0.40F` |
| `plateScale` | `1.0F` | `0.50F .. 1.75F` |
| `nameOffsetX`, `nameOffsetY` | `0.0F` | `-0.50F .. 0.50F` |
| `nameScale` | `1.0F` | `0.50F .. 1.50F` |
| `cornerOffsetX`, `cornerOffsetY` | `0.0F` | `-1.00F .. 1.00F` (a storage sanity bound; the 4 px screen clamp lives in `TitleGeometry`) |
| `cornerScale` | `1.0F` | `0.50F .. 3.00F` |

- [ ] **Step 1: Write the failing config check**

Create `_inspect/check_title_customization.py` with this skeleton, and only a `config_checks()` section for now:

```python
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
```

Run: `python _inspect/check_title_customization.py`
Expected: FAIL with `TitleCustomization.java exists` false, and `TitleCustomization.java` missing from most assertions.

- [ ] **Step 2: Implement the model**

Create `TitleCustomization.java` with the license header used by the other client sources and these members:

```java
public final class TitleCustomization
{
    public static final int VERSION = 1;
    static final float PLATE_MIN_SCALE = 0.50F;
    static final float PLATE_MAX_SCALE = 1.75F;
    static final float PLATE_MAX_OFFSET = 0.40F;
    static final float NAME_MIN_SCALE = 0.50F;
    static final float NAME_MAX_SCALE = 1.50F;
    static final float NAME_MAX_OFFSET = 0.50F;
    static final float CORNER_MIN_SCALE = 0.50F;
    static final float CORNER_MAX_SCALE = 3.00F;
    static final float CORNER_MAX_OFFSET = 1.00F;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TitleCustomization live = new TitleCustomization();

    private float plateOffsetX;
    private float plateOffsetY;
    private float plateScale = 1.0F;
    private float nameOffsetX;
    private float nameOffsetY;
    private float nameScale = 1.0F;
    private float cornerOffsetX;
    private float cornerOffsetY;
    private float cornerScale = 1.0F;
}
```

- `file()` returns `FabricLoader.getInstance().getConfigDir().resolve("immersive_titles.json")`.
- `load()` calls `fromJson` in a `try` that catches `IOException` and `RuntimeException`, warns through `WandererTitles.LOGGER`, and leaves the live instance at `defaults()`. A missing file is silent defaults, not a warning.
- `fromJson(JsonObject root)`:
  - `null` root, or a present `version` that is not a number or whose number is not exactly `1.0`, returns `defaults()`; the unsupported version logs one warning. Compare `getAsDouble() != 1.0D`, not `getAsInt()`, so `1.5` is rejected rather than truncated to `1`.
  - Each object is read independently: `object(root, "plate")`, `"name"`, `"corner"`. A missing or non-object value means that whole object's fields take their defaults.
  - Every field goes through `readFloat(JsonObject object, String name, float fallback, float min, float max)`: absent, non-numeric, non-finite or non-primitive returns `fallback`; otherwise `Math.max(min, Math.min(max, value))`.
  - Read names exactly: `offsetX`, `offsetY`, `scale`. Unknown fields are ignored because only the named members are read.
- `save()`:
  - `Files.createDirectories(file.getParent())`.
  - Pretty JSON built field by field into a `JsonObject` (version then the three objects, each with `offsetX`, `offsetY`, `scale`), written to `file.resolveSibling(file.getFileName().toString() + ".tmp")` with UTF-8 and `CREATE, TRUNCATE_EXISTING, WRITE`. The `.toString()` is required: Java does not concatenate a `Path` with a `String`.
  - `Files.move(tmp, file, REPLACE_EXISTING, ATOMIC_MOVE)`; catch `AtomicMoveNotSupportedException` and retry with `REPLACE_EXISTING` alone; on any `IOException` log and return `false`.
- `copy()` returns a field-for-field copy. `replace(values)` copies the fields through the setters. `reset()` restores the defaults.
- Every setter clamps with `Math.max(min, Math.min(max, value))` and ignores a non-finite input by keeping the current value.

- [ ] **Step 3: Run the check to verify it passes**

Run: `python _inspect/check_title_customization.py`
Expected: `the title customization model holds`, exit 0.

- [ ] **Step 4: Check the Java shape and commit**

Run: `python _inspect/check_java_shape.py`
Expected: `all <n> sources balance`.

```bash
git add src/main/java/com/devgx9/wanderers_titles/client/TitleCustomization.java _inspect/check_title_customization.py
git commit -m "Add the global title customization model and config"
```

---

### Task 2: One geometry for the game and the preview

**Files:**
- Create: `src/main/java/com/devgx9/wanderers_titles/client/TitleGeometry.java`
- Modify: `src/main/java/com/devgx9/wanderers_titles/client/TitleOverlay.java` (constant visibility only)
- Modify: `_inspect/check_title_customization.py`

**Interfaces:**
- Consumes: `TitleCustomization` from Task 1; the constants of `TitleOverlay`.
- Produces, all package-private static in `TitleGeometry`:
  - `record Plate(int x, int y, int width, int height, float scale)`
  - `record Box(float left, float top, float right, float bottom)`
  - `record Name(float centerX, float centerY, float scale, Box box, float roomCenterX, float roomCenterY, float roomWidth, float roomHeight)`
  - `record Corner(int x, int baseline, int kickerX, int kickerY, float nameScale, float kickerScale, int kickerSpacing, int lineGap, Box box)`
  - `record Parts(Component kicker, Component name)`
  - `static Plate plate(int screenWidth, int screenHeight, TitleCustomization values)`
  - `static Name name(Plate plate, Font font, Component text, TitleCustomization values)`
  - `static Parts split(Component title)`
  - `static Corner corner(int screenWidth, int screenHeight, Font font, Component kicker, Component name, TitleCustomization values)`
  - `static Corner cornerRaw(int screenWidth, int screenHeight, Font font, Component kicker, Component name, TitleCustomization values)` — the same layout before the 4 px screen clamp, so the editor can measure what the stored offset should be. `corner` is `cornerRaw` plus the clamp.
  - `static Box union(Box first, Box second)`
  - `static final int CORNER_SCREEN_MARGIN = 4`

**Constant changes in `TitleOverlay` (visibility only):** `FRAME_CROP`, `CONTENT_WIDTH`, `CONTENT_HEIGHT`, `NAME_CENTER_X`, `NAME_CENTER_Y`, `NAME_ROOM_WIDTH`, `NAME_ROOM_HEIGHT`, `NAME_MAX_WIDTH`, `NAME_MAX_HEIGHT`, `FONT_CAP_HEIGHT`, `CORNER_MARGIN`, `CORNER_BOTTOM_MARGIN`, `CORNER_NAME_SCALE`, `CORNER_KICKER_SCALE`, `CORNER_KICKER_SPACING`, `CORNER_LINE_GAP`, `CORNER_HUD_ROOM`, `CORNER_HUD_GAP`, `CORNER_HUD_BAR_HALF` lose `private` and keep their values. No other edit in this task.

- [ ] **Step 1: Write the failing geometry check**

Add `geometry_checks()` to `_inspect/check_title_customization.py` and call it from `main()` before `config_checks()`:

```python
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
```

Run: `python _inspect/check_title_customization.py`
Expected: FAIL with `TitleGeometry.java exists` false.

- [ ] **Step 2: Implement `TitleGeometry`**

Plate, with the old expression order preserved:

```java
float fit = Math.min(screenWidth / (float) CONTENT_WIDTH, screenHeight / (float) CONTENT_HEIGHT);
float scale = fit * values.plateScale();
int width = Math.max(1, Math.round(CONTENT_WIDTH * scale));
int height = Math.max(1, Math.round(CONTENT_HEIGHT * scale));
int x = (screenWidth - width) / 2 + Math.round(values.plateOffsetX() * screenWidth);
int y = (screenHeight - height) / 2 + Math.round(values.plateOffsetY() * screenHeight);
return new Plate(x, y, width, height, scale);
```

Name, in this order:

```java
float scale = plate.scale();
int textWidth = Math.max(1, font.width(text));
float roomWidth = NAME_ROOM_WIDTH * scale;
float roomHeight = NAME_ROOM_HEIGHT * scale;
float auto = Math.min(NAME_ROOM_HEIGHT * scale * NAME_MAX_HEIGHT / FONT_CAP_HEIGHT,
        NAME_ROOM_WIDTH * scale * NAME_MAX_WIDTH / textWidth);
float textScale = Math.min(auto * values.nameScale(),
        Math.min(roomWidth / textWidth, roomHeight / FONT_CAP_HEIGHT));
float width = textWidth * textScale;
float height = FONT_CAP_HEIGHT * textScale;
float roomCenterX = plate.x() + NAME_CENTER_X * scale;
float roomCenterY = plate.y() + (NAME_CENTER_Y - FRAME_CROP) * scale;
float centerX = roomCenterX + values.nameOffsetX() * roomWidth;
float centerY = roomCenterY + values.nameOffsetY() * roomHeight;
```

Clamp each centre so `centerX +/- width/2` stays inside `roomCenterX +/- roomWidth/2`; if the limits cross, use the room centre. Return `Name` with `new Box(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2)`.

`split` mirrors today's `startCornerTitle`: last space, kicker is everything before it, name is the last word, both carry the original style, and a one-word title has a `null` kicker.

Corner, in this order. `cornerRaw` is the whole layout below without the 4 px screen clamp; `corner` calls it, then clamps:

```java
float m = values.cornerScale();
boolean exact = values.cornerOffsetX() == 0.0F && values.cornerOffsetY() == 0.0F && m == 1.0F;
float nameScale = CORNER_NAME_SCALE * m;
float kickerScale = CORNER_KICKER_SCALE * m;
float nameRoom = font.width(name) * nameScale;
float kickerRoom = kicker == null ? 0.0F
        : (font.width(kicker) + CORNER_KICKER_SPACING * Math.max(0, kicker.getString().length() - 1))
                * kickerScale;
float blockWidth = Math.max(nameRoom, kickerRoom);
float blockHeight = FONT_CAP_HEIGHT * nameScale + CORNER_LINE_GAP * m + FONT_CAP_HEIGHT * kickerScale;
```

If `!exact`, reduce `m` by `min(1, (screenWidth - 8) / blockWidth, (screenHeight - 8) / blockHeight)` and recompute `nameScale`, `kickerScale`, `nameRoom`, `kickerRoom`, `blockWidth` and `blockHeight`; this is the only fit cap, and the exact-default path skips it so today's drawing is unchanged. Then:

```java
int x = CORNER_MARGIN + Math.round(values.cornerOffsetX() * screenWidth);
int baseline = screenHeight - CORNER_BOTTOM_MARGIN - (int) (FONT_CAP_HEIGHT * nameScale)
        + Math.round(values.cornerOffsetY() * screenHeight);
```

If `exact` and `CORNER_MARGIN + nameRoom > screenWidth / 2.0F - CORNER_HUD_BAR_HALF`, replace `baseline` with `screenHeight - CORNER_HUD_ROOM - CORNER_HUD_GAP - (int) (font.lineHeight * nameScale)`. The kicker is at `kickerX = x + Math.round((nameRoom - kickerRoom) / 2.0F)` and `kickerY = baseline - (int) (CORNER_LINE_GAP * m) - (int) (FONT_CAP_HEIGHT * kickerScale)`.

Build the union box of the name box and the kicker box. `cornerRaw` returns `new Corner(x, baseline, kickerX, kickerY, nameScale, kickerScale, CORNER_KICKER_SPACING, (int) (CORNER_LINE_GAP * m), box)` with no screen clamp, and it includes the hotbar fallback above when `exact`. `corner` starts with the exact line `Corner raw = cornerRaw(screenWidth, screenHeight, font, kicker, name, values);` and, only when `!exact`, shifts both `x`/`kickerX` and `baseline`/`kickerY` by the smallest whole-pixel vector that puts `raw.box()` inside `[4, screenWidth - 4] x [4, screenHeight - 4]`; shift `raw.box()` by the same vector and return a new `Corner` with the shifted coordinates and box. The fit cap guarantees such a vector exists. The spacing stays 3 inside the scaled pen, which is what makes the effective room `3 * m` exactly as spec 9.3 asks. The editor uses `cornerRaw` to measure the unclamped block, so it can write back the offset at the limit instead of only hiding the overflow at draw time.

- [ ] **Step 3: Run the checks and commit**

Run: `python _inspect/check_title_customization.py`
Expected: every geometry line `ok`, `the title customization model holds`.

Run: `python _inspect/check_java_shape.py`
Expected: `all <n> sources balance`.

```bash
git add src/main/java/com/devgx9/wanderers_titles/client/TitleGeometry.java src/main/java/com/devgx9/wanderers_titles/client/TitleOverlay.java _inspect/check_title_customization.py
git commit -m "Hold every title layout in one shared geometry"
```

---

### Task 3: Draw the live titles through the geometry

**Files:**
- Modify: `src/main/java/com/devgx9/wanderers_titles/client/TitleOverlay.java`
- Modify: `src/main/java/com/devgx9/wanderers_titles/client/WandererTitlesClient.java`
- Modify: `_inspect/check_title_customization.py`

**Interfaces:**
- Consumes: `TitleGeometry.Plate`, `Name`, `Corner`, `Parts` and the static methods it needs from Task 2.
- Produces, package-private static in `TitleOverlay`, the three drawing helpers the editor reuses:
  - `static void drawPlate(GuiGraphicsExtractor graphics, Identifier texture, TitleGeometry.Plate plate)`
  - `static void drawName(GuiGraphicsExtractor graphics, Font font, Component name, TitleGeometry.Name geometry, int alpha)`
  - `static void drawCorner(GuiGraphicsExtractor graphics, Font font, Component kicker, Component name, TitleGeometry.Corner geometry, int color)`
- Produces two snapshots: `private static TitleCustomization revealCustomization` and `cornerCustomization`, both set when the title starts.

- [ ] **Step 1: Write the failing overlay check**

Add `overlay_checks()` and call it from `main()` after `geometry_checks()`:

```python
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
```

Run: `python _inspect/check_title_customization.py`
Expected: FAIL on the snapshot, helper and client-load lines.

- [ ] **Step 2: Integrate the overlay**

- Add the two snapshot fields next to the playback fields, initialised with `TitleCustomization.defaults()`. In `startReveal`, assign `revealCustomization = TitleCustomization.live().copy();` after `revealName = name;`. In `startCornerTitle`, assign `cornerCustomization = TitleCustomization.live().copy();` before the split, and replace the hand-written last-space split with `TitleGeometry.Parts parts = TitleGeometry.split(name); cornerKicker = parts.kicker(); cornerName = parts.name();`. The one-word case stays a `null` kicker.
- In `renderSequence`: keep the frame timing, texture lookup, `keepFrameTexture` and `renderClipSeam` exactly. Replace the fit block with `TitleGeometry.Plate plate = TitleGeometry.plate(screenWidth, screenHeight, revealCustomization);`; call `renderClipSeam(graphics, texture, plate.x(), plate.y(), plate.width(), plate.height(), screenWidth, screenHeight);`, then `drawPlate(graphics, texture, plate);`, then `renderName(graphics, plate, frame);`.
- `renderName(GuiGraphicsExtractor graphics, TitleGeometry.Plate plate, int frame)` keeps the alpha guard (`alpha <= 0 || revealName == null`), computes `TitleGeometry.Name geometry = TitleGeometry.name(plate, font, revealName, revealCustomization);` and calls `drawName(graphics, font, revealName, geometry, alpha)`.
- `drawPlate` blits `plate.width()` by `plate.height()` at `plate.x()/plate.y()` from `u=0`, `v=FRAME_CROP`, region `CONTENT_WIDTH x CONTENT_HEIGHT`, texture `FRAME_WIDTH x FRAME_HEIGHT` — the same 11-argument call the overlay makes today.
- `drawName` translates to `geometry.box().left()`, `geometry.box().top()`, scales by `geometry.scale()`, and draws the component with `(alpha << 24) | TITLE_TEXT_RGB` and `shadow=false`. This is today's `centerX - width/2` translate, written from the box.
- In `renderCornerTitle`, keep the playback guard and alpha exactly, then `TitleGeometry.Corner geometry = TitleGeometry.corner(w, h, font, cornerKicker, cornerName, cornerCustomization);` and `drawCorner(...)`. `w` and `h` are the client window's GUI-scaled width and height.
- `drawCorner` draws the kicker letter by letter when it is not null, at `geometry.kickerX()/kickerY()` scaled by `kickerScale`, with `pen += font.width(one) + geometry.kickerSpacing()`; then draws the name at `geometry.x()/baseline()` scaled by `nameScale`, with shadow on and the passed colour.
- Delete `cornerNameRow` and the old `drawCornerWord`; their maths now lives in `TitleGeometry`.

- [ ] **Step 3: Load the file before the overlay**

In `WandererTitlesClient.onInitializeClient`, inside the `//#if MC >= 26.2` guard, put `TitleCustomization.load();` immediately before `TitleOverlay.initialize();`.

- [ ] **Step 4: Run the checks and commit**

Run: `python _inspect/check_title_customization.py`
Expected: all lines `ok`.

Run: `python _inspect/check_java_shape.py` and `python _inspect/check_gaze_reveal.py`
Expected: all sources balance; the gaze/reveal check passes.

```bash
git add src/main/java/com/devgx9/wanderers_titles/client/TitleOverlay.java src/main/java/com/devgx9/wanderers_titles/client/WandererTitlesClient.java _inspect/check_title_customization.py
git commit -m "Draw both titles through the shared geometry"
```

---

### Task 4: The editor screen, its buttons, the gear and the assets

**Files:**
- Create: `src/main/java/com/devgx9/wanderers_titles/client/TitleCustomizationScreen.java`
- Create: `src/main/resources/assets/immersive_titles/textures/gui/gear.png`
- Modify: `src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java`
- Modify: `src/main/resources/assets/immersive_titles/lang/en_us.json`
- Modify: `_inspect/check_title_customization.py`

**Interfaces:**
- Consumes: `TitleCustomization`, `TitleGeometry`, and the three `TitleOverlay` drawing helpers.
- Produces: `public TitleCustomizationScreen(Screen parent)`; the Collection passes `this`, and Done/Escape returns to that same instance.

**Layout values (exact):**

| Thing | Value |
|---|---|
| Screen margin | `8` |
| Title row | `8` |
| First hint row | `20`; second `30` (line height 9 + 1) |
| Stage top with hints | `44`; without hints `24`; hints are drawn only when `height >= 210` |
| Stage bottom | `buttonY - 16` |
| Button row | `20` tall, `6` gaps, `height - 8 - 20` at the top |
| Button widths | `max(1, min(100, (width - 2*8 - 2*6) / 3))` each, centred |

At the smallest supported 320 px width this is 97 px, so the spec's 80 px floor holds on every supported screen; the `max(1, ...)` only guards a narrower, unsupported window instead of letting the row overflow it.

| Message row | centred at `buttonY - 12` |
| Stage backdrop | `0xFF0E0E12`; 1 px border `0xFF3A3A42` |
| Saved / failed text | `0xFF55FF55` / `0xFFFF5555`; saved shown for `2000L` ms |

- [ ] **Step 1: Write the failing screen, gear and language checks**

Add `screen_checks()` and `lang_checks()` to the checker; call both from `main()`:

```python
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
```

Run: `python _inspect/check_title_customization.py`
Expected: FAIL with `TitleCustomizationScreen.java exists` false, the gear asset missing and the nine language keys missing.

- [ ] **Step 2: Write the language keys**

Add the nine key/value pairs from the table to `en_us.json`, next to the other `screen.immersive_titles.*` keys, with the exact values above.

- [ ] **Step 3: Draw the gear asset**

Create the 16x16 RGBA PNG from this matrix. `.` is transparent `(0,0,0,0)`, `#` is outline `(27,27,32,255)`, `s` is body `(201,205,212,255)`, `l` is highlight `(237,239,242,255)`, `c` is the dark hub `(42,42,50,255)`:

```text
......####......
......#ss#......
..#####ss#####..
..#ss##ss##ss#..
..#sssllllsss#..
..##sll##lls##..
####ll#cc#ll####
#sssl#cccc#lsss#
#sssl#cccc#lsss#
####ll#cc#ll####
..##sll##lls##..
..#sssllllsss#..
..#ss##ss##ss#..
..#####ss#####..
......#ss#......
......####......
```

Write it with a short Python/Pillow command into `src/main/resources/assets/immersive_titles/textures/gui/gear.png`; do not resample or smooth it.

- [ ] **Step 4: Implement the screen shell**

Create `public final class TitleCustomizationScreen extends Screen` with the license header and:

```java
private static final int HANDLE_SIZE = 5;
private static final int HANDLE_HIT_RADIUS = 7;
private static final float WHEEL_STEP = 1.05F;
private static final int SELECTION_RED = 0xFF3B30;
private static final long SAVED_MESSAGE_MS = 2000L;
private final Screen parent;
private final TitleCustomization working;
private final TitleGeometry.Parts parts;
private Button saveButton;
private Button resetButton;
private Button doneButton;
private Component message;
private int messageColor;
private long messageUntilMs;
private int stageLeft, stageTop, stageWidth, stageHeight;
private float stageScale, virtualLeft, virtualTop;
private static final Identifier FRAME = Identifier.fromNamespaceAndPath(
        WandererTitles.MOD_ID, "textures/title/frame_0100.png");
private static final Identifier BUTTON_SPRITE = Identifier.fromNamespaceAndPath(
        WandererTitles.MOD_ID, "widget/button");
private static final Identifier LIT_BUTTON_SPRITE = Identifier.fromNamespaceAndPath(
        WandererTitles.MOD_ID, "widget/button_highlighted");
private static final Component SAMPLE = Component.translatable(
        "screen.immersive_titles.customize.sample").withStyle(ChatFormatting.BOLD);
```

- Constructor: `super(Component.translatable("screen.immersive_titles.customize.title")); this.parent = parent; this.working = TitleCustomization.live().copy(); this.parts = TitleGeometry.split(SAMPLE);`
- `init()`: compute the stage as in the layout table and recreate the three buttons; use the current `width`, `height` and `font`, and do not reset `working` or `message`. Concretely: `buttonY = height - 8 - 20`; `stageTop = height >= 210 ? 44 : 24`; `stageBottom = buttonY - 16`; `availableWidth = width - 2 * 8`; `availableHeight = stageBottom - stageTop`. Save is labelled `screen.immersive_titles.customize.save` and calls `save()`, Reset is labelled `screen.immersive_titles.customize.reset` and calls `reset()`, and Done is labelled `gui.done` and calls `onClose()`. The stage fit is `fit = Math.min(availableWidth / (float) width, availableHeight / (float) height)`, the stage rectangle is `floor(width * fit)` by `floor(height * fit)`, and `stageScale = Math.min(stageWidth / (float) width, stageHeight / (float) height)`; centre the virtual screen inside the rounded stage with `virtualLeft/virtualTop`.
- `extractRenderState`: draw the title and the two hints when `height >= 210`, then the stage backdrop and its 1 px border. Centre the three lines with the collection's own form, `graphics.text(font, text, (width - font.width(text)) / 2, y, color, true)`, rather than a `centeredText` helper, so the same call is known to exist on both versions. Compute `TitleGeometry.Plate plate = TitleGeometry.plate(width, height, working);`, `TitleGeometry.Name name = TitleGeometry.name(plate, font, SAMPLE, working);` and `TitleGeometry.Corner corner = TitleGeometry.corner(width, height, font, parts.kicker(), parts.name(), working);`. Then `graphics.enableScissor(stageLeft, stageTop, stageLeft + stageWidth, stageTop + stageHeight)` in screen space **before** the pose is changed, push the pose, translate `virtualLeft/virtualTop`, scale `stageScale`, draw the preview with `TitleOverlay.drawPlate(graphics, FRAME, plate)`, `TitleOverlay.drawName(graphics, font, SAMPLE, name, 255)` and `TitleOverlay.drawCorner(graphics, font, parts.kicker(), parts.name(), corner, 0xFFFFFFFF)`, and pop the pose. Task 5 draws the selection outline here, while the scissor is still in screen space. Then disable the scissor, draw the handles, call `super.extractRenderState(...)`, and paint the pack face and the label of each of the three buttons. The message line is drawn at `buttonY - 12` in `messageColor` while it is live. Do not write the words `extractBackground`, `renderBackground` or `fillGradient` anywhere in this file, not even in a comment: the background check reads the whole file.
- `save()`: on `working.save()` success call `TitleCustomization.replace(working)`, set the saved message and `messageUntilMs = Util.getMillis() + SAVED_MESSAGE_MS`; on failure set the failed message with `messageUntilMs = 0L` (it stays until the next edit). It never changes the screen.
- `reset()`: `working.reset()` and `message = null`; it never calls `save()`.
- `onClose()`: `minecraft.gui.setScreen(parent)` only.
- Button faces: the game draws the buttons first; paint `BUTTON_SPRITE` or `LIT_BUTTON_SPRITE` over each face with `button.isHoveredOrFocused()` choosing the lit one, then redraw the button's label in `0xFFFFFFFF` at the game's centred label position. Draw a 1 px `0xFFC9CCD1` border around a focused button so the keyboard is visible.
- The saved message expires in `extractRenderState` once `messageUntilMs != 0` and `Util.getMillis() > messageUntilMs`.

- [ ] **Step 5: Add the gear to the Collection**

In `BiomeCollectionScreen`:
- Add `GEAR_SIZE = 16`, `GEAR_GAP = 8`, the `customizeButton` field and `GEAR_TEXTURE = Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID, "textures/gui/gear.png")`.
- In `init()`, after Done is built and added, build the gear at `doneButton.getX() + doneButton.getWidth() + GEAR_GAP`, same `y`, `16x16`, with an empty label, `.tooltip(Tooltip.create(Component.translatable("screen.immersive_titles.collection.customize")))`, `.createNarration(defaultNarration -> Component.translatable("screen.immersive_titles.collection.customize"))`, and an action that calls `minecraft.gui.setScreen(new TitleCustomizationScreen(this))`. Add it with `addRenderableWidget`. This is the same empty-label-plus-tooltip-plus-narration shape the two filter buttons already use.
- In `drawButtonFaces`, after the Done face, paint `customizeButton` with `drawButtonFace(graphics, customizeButton)` and then `drawGearIcon(graphics, customizeButton)`. Do not redraw the gear's label: the opaque pack face covers the game's label, and the gear is the visible face.
- `drawGearIcon` blits `GEAR_TEXTURE` at the button's `x/y` as a 16x16 texture with the 11-argument `graphics.blit` call, no smoothing.

- [ ] **Step 6: Run the checks and commit**

Run: `python _inspect/check_title_customization.py`
Expected: all screen, language and gear lines `ok`.

Run: `python _inspect/check_java_shape.py`, `python _inspect/check_lang_keys.py`, `python _inspect/check_gui_sprites.py`, `python _inspect/check_filter_faces.py`, `python _inspect/check_screen_switch.py`
Expected: all pass.

```bash
git add src/main/java/com/devgx9/wanderers_titles/client/TitleCustomizationScreen.java src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java src/main/resources/assets/immersive_titles/lang/en_us.json src/main/resources/assets/immersive_titles/textures/gui/gear.png _inspect/check_title_customization.py
git commit -m "Open the title editor from a gear in the collection"
```

---

### Task 5: Selection, handles, dragging, resizing and the wheel

**Files:**
- Modify: `src/main/java/com/devgx9/wanderers_titles/client/TitleCustomizationScreen.java`
- Modify: `_inspect/check_title_customization.py`

**Interfaces:**
- Consumes: the stage fields and `working` copy from Task 4; `TitleGeometry.Box` and the current `Plate`/`Name`/`Corner` records.
- Produces: `private enum Element { PLATE, NAME, CORNER }`, `private enum Drag { NONE, MOVE, RESIZE }`, and the four mouse overrides. No other class calls these. The task adds this state to the screen:

```java
private Element selected = Element.PLATE;
private Drag drag = Drag.NONE;
private float dragStartOffsetX, dragStartOffsetY;
private float dragDenominatorX, dragDenominatorY;
private double dragMouseX, dragMouseY;
private float resizeStartScale;
private double resizeStartDistance;
private double anchorX, anchorY;
```

**Interaction values (exact):**

| Thing | Value |
|---|---|
| Selection red | `0xFF3B30` |
| Outline shadow | `0xFF000000`, 1 px outside the red |
| Handle | 5x5 red square with a 7x7 black shadow |
| Handle hit radius | 7 screen pixels around the clamped handle centre |
| Wheel step | `1.05F` per notch (up grows, down shrinks) |
| Click order | name, corner, plate; empty stage clears |
| Drag denominators | screen `width/height` for plate and corner, `roomWidth/roomHeight` for name |

- [ ] **Step 1: Write the failing interaction check**

Add `interaction_checks()` and call it from `main()` after `screen_checks()`:

```python
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
```

Run: `python _inspect/check_title_customization.py`
Expected: FAIL on the interaction constants and methods.

- [ ] **Step 2: Add the element bounds and the hit test**

Add `boxOf(Element)`: plate is `new Box(plate.x(), plate.y(), plate.x() + plate.width(), plate.y() + plate.height())`; name is the `Name.box()`; corner is the `Corner.box()`. The geometry is recomputed from `working` on every call, so an edit is visible immediately. Initialise `selected = Element.PLATE` so the plate shows its red bounds when the editor opens; `drag` starts as `Drag.NONE`. The hit test is `private Element hitElement(double virtualX, double virtualY)`.

Map virtual to screen with `virtualLeft + x * stageScale` and `virtualTop + y * stageScale`, and back with `(screen - virtualLeft) / stageScale`. Four handle points are the four corners of `boxOf(selected)`, clamped on each axis to `stageLeft + HANDLE_SIZE / 2.0 .. stageLeft + stageWidth - HANDLE_SIZE / 2.0` (and the same for y), rounded for drawing. A handle is hit when the pointer is within `HANDLE_HIT_RADIUS` on both axes of one of those points.

Add `private void clampStoredOffsets()` and call it after every move, resize and wheel step. It writes the limit into the offset itself, so the next drag starts from the edge instead of from an invisible backlog, and a scale change cannot jump an element that was only visually clamped:

- name: the text box is centred on `roomCenter + offset * room`, so the largest offset that still fits is `maxOffsetX = max(0, (roomWidth - boxWidth) / (2 * roomWidth))` and the same for y. Clamp `working.setNameOffsetX(...)` and `setNameOffsetY(...)` into `±maxOffset`. The box width and height are independent of the offset, so this is exact.
- corner: when `working` is at the exact defaults, return without touching it (that is the hotbar path). Otherwise `TitleGeometry.Corner raw = TitleGeometry.cornerRaw(width, height, font, parts.kicker(), parts.name(), working);` is the block before the 4 px clamp. The smallest whole-pixel shift that brings it inside is `shiftX = max(TitleGeometry.CORNER_SCREEN_MARGIN - raw.box().left(), min(width - TitleGeometry.CORNER_SCREEN_MARGIN - raw.box().right(), 0))` and `shiftY = max(TitleGeometry.CORNER_SCREEN_MARGIN - raw.box().top(), min(height - TitleGeometry.CORNER_SCREEN_MARGIN - raw.box().bottom(), 0))`. Add `shiftX / (float) width` and `shiftY / (float) height` to the stored offsets. When the block is already inside, both shifts are zero.
- plate: nothing; its setter already clamps the stored offset to ±0.40.

- [ ] **Step 3: Add the four mouse overrides**

Use these fields and branch shapes, so the drag bookkeeping is one named state: `dragStartOffsetX`, `dragStartOffsetY`, `dragDenominatorX`, `dragDenominatorY`, `dragMouseX`, `dragMouseY`, `resizeStartScale`, `resizeStartDistance`, `anchorX`, `anchorY`; `mouseDragged` branches with `if (drag == Drag.MOVE)` and `else if (drag == Drag.RESIZE)`. Declare the four overrides with these exact signatures: `public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)`, `public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)`, `public boolean mouseReleased(MouseButtonEvent event)`, and `public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical)`.

- `mouseClicked`: call `super.mouseClicked(event, doubleClick)` first and return true when it consumed the click, so Save, Reset and Done keep their own presses. Only the left button and only inside the stage. If the selected element's handle is hit, start a resize; otherwise select the first of name, corner, plate whose box contains the virtual point and start a move immediately; empty stage space sets `selected = null` and consumes the click. On any press that starts a move or a resize, record the starting offsets, the denominators, the anchor, the starting scale, the starting distance and the mouse position before the first drag arrives. For a handle resize, the starting distance is measured from the anchor to the clamped handle point that was hit, not to the unclamped corner, so a handle sitting on the stage edge does not jump the scale on the first move.
- `mouseDragged`: if no drag, defer to `super`. A move adds `(virtualX - dragMouseX) / dragDenominatorX` to the starting offset on each axis, using `width`/`height` for plate and corner and the geometry's `roomWidth`/`roomHeight` for the name, then calls `clampStoredOffsets()` so the stored value stops exactly where the drawn value stops. A resize computes `distance = Math.hypot(vx - anchorX, vy - anchorY)`, `ratio = max(1, distance) / max(1, resizeStartDistance)`, and applies `resizeStartScale * ratio` through the element's setter; the setter and `applyScale` call `clampStoredOffsets()` as well. After either, call `edited()`, which sets `message = null`.
- `mouseReleased`: if a drag was running, set `drag = Drag.NONE` and return true; the selection stays. Otherwise defer to `super`.
- `mouseScrolled`: only inside the stage and only with a selection and a non-zero vertical amount. `notches = max(1, (int) abs(vertical))`, `factor = vertical > 0 ? WHEEL_STEP : 1.0F / WHEEL_STEP`, multiply the element's current scale by `factor` once per notch, and apply it through the same resize path, with the anchor at the element's current box centre. Call `clampStoredOffsets()` and then `edited()` after the step, so the wheel also clears the save message. Return true when it handled the wheel.

- [ ] **Step 4: Preserve the centre when the corner is resized**

`applyScale(float)` switches on `selected`. Plate just calls the setter; its geometry centres it on the same point. Name calls the setter and then `clampStoredOffsets()`; because the stored offset is already at the effective limit, a smaller name keeps the same centre and a larger one is stopped at the room edge instead of jumping. For the corner:
1. `working.setCornerScale(value)`.
2. Recompute `boxOf(Element.CORNER)`.
3. `dx = anchorX - (box.left + box.right) / 2`, `dy = anchorY - (box.top + box.bottom) / 2`.
4. `working.setCornerOffsetX(working.cornerOffsetX() + (float) (dx / width))` and the same for y with `height`.
5. Call `clampStoredOffsets()`; at a screen edge the block cannot keep a centre that would take it outside, so this is where the physical limit wins.

The anchor is captured when the resize begins (`anchorX/anchorY` from the element's box centre) and stays fixed for the whole drag. For the wheel, capture the anchor the same way before applying the step.

- [ ] **Step 5: Draw the selection**

After the preview pose has been popped, and while the screen-space scissor is still enabled, draw the selected box's outline in screen space: a 1 px black shadow just outside the rect, then a 1 px `0xFF3B30` line inside it, using four `fill` calls for the shadow and four for the red line (do not call `outline`, which is not guaranteed on 26.2). After the scissor is disabled, draw four handles at the clamped points: a 7x7 black square and a 5x5 red square centred on each. The red outline itself is clipped to the stage; the handles are not, because they are clamped inside it.

- [ ] **Step 6: Re-layout without losing the edit**

In `init()`, recompute the stage, virtual origin and buttons as Task 4 says, keep the same `working` instance, keep `selected`, and set `drag = Drag.NONE`. A window resize or GUI-scale change therefore keeps the arranged values and the selection, and the next draw re-clamps the geometry.

- [ ] **Step 7: Run the checks and commit**

By the end of this task the checker's `main()` reads, in order:

```python
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
```

Run: `python _inspect/check_title_customization.py`
Expected: all interaction lines `ok`, and the whole check ends with `the title customization model holds`.

Run: `python _inspect/check_java_shape.py` and `python _inspect/check_screen_switch.py`
Expected: all sources balance; screens still change through `Gui.setScreen`.

```bash
git add src/main/java/com/devgx9/wanderers_titles/client/TitleCustomizationScreen.java _inspect/check_title_customization.py
git commit -m "Move and resize the title elements in the editor"
```

---

### Task 6: Full static verification, GitHub build and backup

**Files:**
- No source changes. This task runs the checks, pushes, reads the build and refreshes the backup.

- [ ] **Step 1: Run all eighteen checks**

```powershell
$checks = Get-ChildItem "_inspect" -Filter "check_*.py" | Sort-Object Name
$failed = 0
foreach ($check in $checks) {
    python $check.FullName
    if ($LASTEXITCODE -ne 0) { $failed++ }
}
"$($checks.Count) checks, $failed failed"
```

Expected: `18 checks, 0 failed`. The new check is `check_title_customization.py`; the other seventeen are the existing files. Do not run the five `measure_*.py` / `preview_*.py` helpers as checks.

- [ ] **Step 2: Confirm the working tree and commit the documentation**

```powershell
git diff --check
git status --short
git add docs && git commit -m "Add the title customization design and plan"
```

Expected: `git diff --check` prints nothing; the only pending files are `docs/` (and nothing else). The design and plan are part of the repository, so the backup carries them.

- [ ] **Step 3: Push and find the run**

```powershell
git push origin main
git rev-parse HEAD
```

Then read `https://api.github.com/repos/DevGBX9/Immersive-Titles/actions/runs?per_page=1` with the web fetch tool and confirm `head_sha` is the commit just pushed, `head_branch` is `main`, and the workflow is `Dev Builds`. Poll the same run URL until `status` is `completed`.

- [ ] **Step 4: Confirm both jars**

When the run completes, require `conclusion == "success"`. Read the run's `check_suite_id`, then:

- `https://api.github.com/repos/DevGBX9/Immersive-Titles/check-suites/<check_suite_id>/check-runs`
- for each check run, `https://api.github.com/repos/DevGBX9/Immersive-Titles/check-runs/<id>/annotations`

Find the two notice annotations whose message starts `Built mod jar:`; they carry `<name> (<n> bytes)`. Require `immersive_titles-mc26.2-v0.1.0.jar` and `immersive_titles-mc26.3-v0.1.0.jar` (names follow the current version), both with a non-zero byte count. Also read `https://api.github.com/repos/DevGBX9/Immersive-Titles/actions/runs/<id>/artifacts` and report the `mod-jars` artifact size. If the annotations are unavailable, report that fact explicitly and the artifact size; do not claim a jar size that was not read.

- [ ] **Step 5: Refresh the full backup**

Create `C:\Users\GBX\Desktop\Immersive-Titles-Global-Title-Customization-Backup-2026-10-27.zip` with Python's `zipfile`:

- one root folder named `Wanderers Titles/`, containing the whole repository including `.git`, `.github`, `_inspect`, `docs`, `gradle`, `src`, `templates`, `versions` and every root file;
- excluded: `build/`, `.gradle/`, `run/`, `mod-jars/`, and any `*.zip`;
- a UTF-8 Arabic state file at the ZIP root, `حالة-المشروع-2026-10-27.txt`, naming the feature, the commit, the GitHub run id and conclusion, the two jar names and sizes, the 18 passing checks, and the restore instruction (`extract the ZIP, then open the Wanderers Titles folder`).

Then report the ZIP's exact byte size and SHA-256:

```powershell
$zip = "C:\Users\GBX\Desktop\Immersive-Titles-Global-Title-Customization-Backup-2026-10-27.zip"
(Get-Item $zip).Length
(Get-FileHash -Algorithm SHA256 $zip).Hash
```

- [ ] **Step 6: Report and hand over the manual checklist**

Report in Arabic: the 18 checks, the commit, the run id and conclusion, the two jar sizes, the artifact size, the ZIP size and SHA-256. Then give the owner the manual checklist from spec section 12:

- the gear is visible only in the Collection, on both pages and both filters;
- the editor opens with the default preview, and the stage matches the screen's shape;
- each element selects red, drags and resizes, and stops at its limits;
- Save shows "Saved", keeps the screen open, and the values survive closing and restart;
- Reset returns the preview to default but does not write until Save;
- closing without Save discards;
- the default large reveal and corner title look exactly as before, and the Collection's hover/filter/tab behaviour is unchanged;
- a corrupt config falls back to defaults without crashing.

---

## Self-Review Notes

The following were checked against the spec and are recorded so the executor can see the decisions that are easy to miss:

- **Spec coverage:** 7.1 → Task 4; 7.2 → Task 4; 7.3 → Task 4; 7.4 → Task 5; 7.5 → Task 5; 7.6 → Task 5; 7.7 → Tasks 2 and 5; 7.8 → Task 4; 8.1–8.3 → Task 1; 9.1–9.4 → Tasks 2 and 3; 10 → Tasks 1, 2, 4 and 5; 11 → Task 4; 12 → Task 6.
- **Default equivalence:** the plate keeps `(screenWidth - width) / 2` before the offset, the name keeps the two `min` expressions in their original order, and the corner keeps `height - CORNER_BOTTOM_MARGIN - (int) (FONT_CAP_HEIGHT * nameScale)` plus the exact line-height hotbar fallback. The corner fit cap and the 4 px clamp are skipped when `offsetX == 0 && offsetY == 0 && scale == 1`, so today's drawing is the default path.
- **Corner spacing:** the pen keeps `CORNER_KICKER_SPACING = 3` inside the scaled pose; the effective room is `3 * m`, which is what spec 9.3's `3 * m` means. The gap is `6 * m` in screen pixels because it is applied outside the scaled pen.
- **Snapshots:** a reveal or corner title that is already playing keeps the values it started with; Save takes effect on the next playback, which is the spec's edge case 10.
- **Config safety:** every field is read on its own with a default and a finite check, so a partial or hand-edited file cannot turn a missing `scale` into a clamped `0.0`. Load never writes; only Save writes, atomically.
- **Stored offsets at the limit:** the editor writes the geometric limit back into the offset after every edit (`clampStoredOffsets`). This is what keeps a drag from accumulating an invisible backlog at an edge and what stops a name or corner from jumping sideways when it is scaled down after being dragged to a limit. The plate needs none of this because its setter's ±0.40 is the whole limit.
- **No local build:** Task 6 never invokes Gradle. The only build is the GitHub workflow.



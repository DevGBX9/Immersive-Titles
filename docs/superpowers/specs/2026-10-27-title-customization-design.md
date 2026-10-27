# Immersive Titles — Global Title Customization

- Date: 2026-10-27
- Status: design for owner approval
- Scope: Minecraft 26.2 and 26.3, shared `src/main` source, Fabric client

## 1. Intent

Players can adjust how the mod's two titles are placed and sized:

- the large cinematic title: its ornamental plate and its name, each adjusted on its own;
- the small corner title: the whole block (kicker above, name below) adjusted as one.

The adjustment is global for the installation, not per world. It is made from a screen
reached by a gear button inside the Collection screen only. The screen previews the real
title assets with the real layout maths, so what the player arranges is what the game draws.

## 2. Goals

- A gear button in the Collection screen opens the customization screen.
- One global configuration file, loaded on client start and shared by every world.
- The large plate and the large name are separate selectable elements. Each has its own
  position and scale, but the name stays on the plate.
- The corner title is one selectable element with position and scale.
- The preview uses the real plate frames, the real name, the real corner title and the real
  layout code. It is not a mock.
- Selection is shown in red, movement is by dragging the element, scaling is by dragging a
  corner handle.
- Save writes the configuration and keeps the screen open with a confirmation message.
- Reset returns the preview to the defaults; it does not save. Save is still required.
- Closing the screen without Save discards the edits.
- At default values the large title and the corner title are pixel-identical to today, and
  every non-customization behaviour (discovery, re-entry, filters, tabs) is unchanged.

## 3. Non-goals

- Per-world, per-server or per-profile title settings.
- Editing colours, fonts, text content, the plate artwork, or the reveal animation timing.
- A key binding or a button outside the Collection screen for opening the editor.
- Touch gestures, gamepad editing, or keyboard nudging of elements.
- Changing the Collection's filter hover rules, its tabs, discovery marking, structure
  visits, or reveal playback.

## 4. Anti-goals

- A preview that is drawn with different maths or different assets from the game.
- A name that can be dragged off its plate, or grown larger than the plate's empty room.
- Any change to the default look or timing of the titles.
- Auto-saving when the screen is closed.
- A malformed or hand-edited config crashing the game or drawing off-screen.

## 5. Current state (what the feature builds on)

`TitleOverlay` draws both titles:

- Large plate: 187 frames, 1440x720, the outer 9 rows cropped top and bottom, so the drawn
  content is `CONTENT_WIDTH = 1440` by `CONTENT_HEIGHT = 702`. The plate is fitted into the
  GUI-scaled screen with
  `fit = min(screenW / 1440.0F, screenH / 702.0F)`, centred, and the strip of screen outside
  the 2:1 picture is filled with the clip's edge row/column (`renderClipSeam`).
- Large name: drawn at the plate's middle, `NAME_CENTER_X = 720.0F`,
  `NAME_CENTER_Y = 269.5F` in frame pixels (269.5 is measured from the top of the frame, so
  the crop is subtracted when drawing). The text scale is
  `min(120 * fit * 0.70 / 7, 807 * fit * 0.94 / fontWidth)`, where 7 is the font cap height.
  The cap is centred on the plate's middle.
- Corner title: the name is the last word of the title, the kicker is every word before it.
  The name is drawn at `x = 20`, baseline
  `y = screenH - 20 - (int)(7 * 2.5)`, scale 2.5; the kicker is centred above it, scale 1.0,
  gap 6, letter spacing 3. A name wide enough to reach the hotbar is moved above the HUD
  instead (`CORNER_HUD_ROOM = 39`, `CORNER_HUD_GAP = 4`).
- The large reveal and the corner title never play at the same time; starting a reveal
  cancels a corner title. The editor shows both at once for editing only.

## 6. Decisions already made with the owner

- The setting is global.
- The gear is only in the Collection screen.
- The large plate and large name are separate selectable elements.
- The preview is a 16:9-style stage (the game screen's aspect) in the middle of the screen,
  with the Save/Reset/Done bar below it. The owner approved this layout.
- Save keeps the screen open and shows a confirmation that the save happened.
- Closing without pressing Save discards the edits.
- Reset only changes the preview; Save persists the defaults if the player wants that.

## 7. Screen and interaction specification

### 7.1 Gear button in the Collection

- Size 16x16, in the footer row, immediately to the right of the Done button with an 8 px
  gap, vertically aligned with Done. It is inside the Collection block's width at all
  supported GUI sizes (checked down to 320x240).
- It is a game `Button` with an empty label, the same pack button sprite face as the other
  Collection buttons (plain normally, lit while hovered, exactly like the filters), and a
  16x16 gear icon drawn over it.
- Tooltip and narration: `screen.immersive_titles.collection.customize`.
- It is created in `BiomeCollectionScreen.init()` and exists on both pages and with either
  filter, because it belongs to the screen, not the list.
- Pressing it opens `TitleCustomizationScreen` with the current Collection instance as its
  parent. Done in the editor returns to that same Collection instance, keeping its page,
  filter and scroll position.

### 7.2 Customization screen layout

- A normal `Screen` covering the game, with the same menu background the Collection gets.
- Header, from the top: the screen title (white, game font, centred); below it two muted
  hint lines when the GUI height is at least 210 px:
  - `screen.immersive_titles.customize.hint.move`
  - `screen.immersive_titles.customize.hint.save`
- Preview stage: the largest rectangle with the same aspect ratio as the GUI-scaled window,
  fitted into the room between the header and the button bar and centred. It has a near-black
  backdrop (`0xFF0E0E12`) and a 1 px grey border.
- The stage represents the whole game screen, scaled down uniformly. The virtual coordinate
  system inside it is the current GUI-scaled `width x height`; the preview is drawn there and
  then transformed into the stage by one translate + uniform scale. This keeps the preview
  exactly proportional to the game at any window size and GUI scale.
- Below the stage: a centred row of three 20 px tall buttons, Save, Reset, Done, each at most
  100 px wide and at least 80 px wide, with 6 px gaps; the row shrinks to fit narrow screens.
  They use the same pack button faces as the Collection buttons.
- The Save confirmation/error line is centred immediately above the button row.
- If the GUI height is below 210 px the two hint lines are omitted and the stage starts
  higher. At the smallest supported GUI sizes the stage still gets the remaining room; it is
  never allowed to overlap the header title or the button row.

### 7.3 Preview content

- The preview draws, with the current unsaved working values:
  - the large plate using the real settled frame `textures/title/frame_0100.png` (frame 100
    is fully formed, the name is fully opaque there, and it has no fade artefacts);
  - the sample large name, fully opaque, using the same fit rules as the game;
  - the corner title (kicker + name) at its edited position and scale;
  - a static preview only: no reveal animation is played, because the editor is for placing
    the settled title, and an animation would move the element under the cursor.
- Sample content is `screen.immersive_titles.customize.sample` = `PALE GARDEN`, styled bold.
  It is deliberately two words so the corner title shows both its kicker (`PALE`) and its
  name (`GARDEN`). The sample is fixed; it does not change with the player's world.
- The sample is drawn with the real font and the real fit maths, so a long real title can be
  smaller than the sample, but it can never overflow the plate because the renderer clamps
  it (see 9.2).
- Both titles are shown together even though they never play together in game; this is an
  editor-only composition.

### 7.4 Selection and handles

- Selection order when elements overlap, topmost first: large name, corner title, plate.
  Clicking empty stage space clears the selection.
- The selected element gets:
  - a 1 px red outline (`0xFF3B30`) with a 1 px black shadow line outside it so it reads on
    the white plate artwork and on the dark backdrop;
  - four red square handles, 5x5 screen pixels, at the corners of the element's bounds.
- Bounds used for selection and handles:
  - plate: the whole drawn content rectangle (1440x702 at the plate's current scale), not
    just the visible ink. The transparent margins are part of the plate's geometry.
  - name: the text box (font width x 7 cap height at the current text scale).
  - corner: the union of the kicker box and the name box, including the gap between them.
- Handles are drawn in screen space at fixed size and are not scaled with the stage. Their
  hit radius is 7 screen pixels, so they remain usable even when the corner title is small.
- Each handle is clamped into the stage rectangle, inset by half the handle size, so all
  four handles are always fully visible. When an element's true corner is outside the stage
  (a plate scaled above the screen width, for example), its handle sits on the nearest stage
  edge instead. The red outline itself is clipped to the stage; an element larger than the
  stage is then read by its four clamped handles, and its scale is always reachable with the
  mouse wheel (see 7.6).

### 7.5 Dragging (move)

- Mouse down on an element selects it and starts moving it immediately; no separate grab
  step.
- Mouse move translates the element by the pointer delta converted back to virtual screen
  units (`deltaScreen / stageScale`).
- The element is clamped continuously during the drag; the element never visually leaves
  its allowed range.
- Mouse release ends the drag. The selection stays.

### 7.6 Resizing (scale)

- Mouse down within the hit radius of a selected element's corner handle starts resizing.
- All resizing is uniform (one scale factor); no non-uniform stretch, so the artwork and the
  text keep their proportions.
- Scaling is anchored at the centre of the element's current bounds. The scale factor is the
  ratio of the pointer's distance from that centre now to its distance when the drag began.
  If the handle was clamped to the stage edge, the distance to the clamped handle is the
  baseline, so the first movement does not jump the scale.
- Mouse wheel over the stage is the second resize control: with an element selected, one
  notch up grows it by a factor of 1.05 and one notch down shrinks it by the same factor.
  This works even when every true corner is outside the stage, which is the case for the
  plate at any scale above the default on a 16:9 screen. The wheel uses the same clamps,
  applies one step per notch and does not require the pointer to be over the element. With
  nothing selected the wheel does nothing in the stage.
- The plate's scale changes its whole assembly (clip and seam bands). The name rides the
  plate: its centre is plate-relative and its automatic fit uses the plate's new scale. The
  name's own scale multiplier is unchanged by a plate resize.
- The name's handle changes only the name multiplier.
- The corner title's handle changes only the corner scale; its rendered bounds are kept
  centred on the same point, and the stored offset is adjusted to preserve that centre.
- Limits are applied on every mouse move, so the drag stops at the limit instead of jumping
  back later.

### 7.7 Limits (exact)

Large plate:

- scale multiplier relative to the fitted plate: `0.50 .. 1.75`.
- offset as a fraction of the virtual screen, from the default centred position:
  `-0.40 .. +0.40` on both axes. This keeps the plate's content-rectangle centre inside the
  central 80% of the screen.
- no fit cap: the plate may legitimately be larger than the screen and be cropped.

Large name:

- its box must stay entirely inside the plate's empty room, the same 807x120 frame-pixel
  room the game already sizes the name against, scaled with the plate. The name therefore
  can never separate from the plate.
- scale multiplier on top of the automatic fit: `0.50 .. 1.50`, then hard-capped so
  `textWidth <= roomWidth` and `textHeight <= roomHeight`. The cap is what stops a name from
  growing through the ornaments.
- position offsets are fractions of that room: `-0.50 .. +0.50` on both axes, then clamped so
  the whole text box stays inside the room. For a long name the horizontal slack is small;
  for a short name it can move further, which is the natural reading of "the name has room
  to move but stays on its plate".

Corner title:

- scale multiplier applied to the whole block (name 2.5 -> 2.5 * m, kicker 1.0 -> 1.0 * m,
  gap 6 -> 6 * m, letter spacing 3 -> 3 * m): `0.50 .. 3.00`, further capped only if the block
  would be wider/taller than the screen at that size.
- offset as a fraction of the virtual screen from the default corner position, then clamped
  so the whole block stays inside a 4 px margin on every screen edge.
- The existing hotbar-avoidance fallback applies only at the exact defaults
  (`offsetX == 0`, `offsetY == 0`, `scale == 1`). Once the player has moved or scaled the
  corner title, their placement wins and the automatic lift is not applied. This keeps the
  preview honest: it never needs to simulate a jump that is not part of the player's edit.

### 7.8 Save, Reset, Done, messages

- Save:
  - validates and clamps the working values;
  - writes the global config atomically (temporary file then move);
  - on success: replaces the live configuration, shows
    `screen.immersive_titles.customize.saved` in green above the button row for 2000 ms,
    keeps the screen open and keeps the selection;
  - on failure: logs the error, shows `screen.immersive_titles.customize.save_failed` in red,
    keeps the screen open, and does not replace the live configuration.
- Reset:
  - replaces the working copy with the defaults, so the preview returns to the default
    placement and size;
  - clears any save confirmation;
  - does not write the file; the player must press Save to persist the defaults;
  - keeps the screen open and keeps the selection.
- Done / Escape:
  - closes the editor and returns to the Collection;
  - discards the working copy; the live configuration and the file stay as last saved;
  - no confirmation dialog (this was the owner's explicit choice).
- Any edit after a successful save clears the confirmation line, because the state is no
  longer the saved state.

## 8. Model and persistence

### 8.1 File

- Path: `<Fabric config dir>/immersive_titles.json`, normally `config/immersive_titles.json`.
- Global, not per world. One file for the installation.
- Loaded once during `onInitializeClient`, before the overlay is initialized.
- Missing file = defaults. Unreadable file or syntactically invalid JSON = defaults + a
  warning in the log; the game does not crash and the file is not overwritten until the
  player presses Save.
- A syntactically valid file with missing fields or missing sub-objects is repaired field by
  field: every absent field takes its default and every present field is kept after
  validation. A missing `plate`, `name` or `corner` object therefore means defaults for that
  object only, and a missing `scale` means 1.0, not 0.0 clamped up to the minimum.
- Unknown fields are ignored for forward compatibility.
- A `version` other than 1 is treated as unsupported and falls back to defaults with a
  warning.

### 8.2 Schema (version 1)

```json
{
  "version": 1,
  "plate": {
    "offsetX": 0.0,
    "offsetY": 0.0,
    "scale": 1.0
  },
  "name": {
    "offsetX": 0.0,
    "offsetY": 0.0,
    "scale": 1.0
  },
  "corner": {
    "offsetX": 0.0,
    "offsetY": 0.0,
    "scale": 1.0
  }
}
```

- `plate.offsetX/Y`: fraction of the virtual screen width/height.
- `plate.scale`: multiplier on the fitted plate.
- `name.offsetX/Y`: fraction of the plate's empty room width/height.
- `name.scale`: multiplier on the automatic name fit.
- `corner.offsetX/Y`: fraction of the virtual screen width/height.
- `corner.scale`: multiplier on the whole corner block.
- Defaults are exactly the zeros and ones above and must represent today's rendering.

### 8.3 Validation

- Reject NaN and infinite values per field: the affected field takes its default, the rest
  of the file is kept.
- Clamp stored values to the ranges in 7.7 before applying or writing.
- The renderer re-applies the geometric clamps (name inside room, corner inside screen,
  plate centre inside the screen) on every frame, so even a hand-edited out-of-range file
  cannot draw outside the allowed area.

## 9. Rendering integration

### 9.1 One geometry implementation

- Extract the layout maths into one shared helper (suggested `TitleGeometry` in the client
  package) used by both `TitleOverlay` and the preview. The preview must not re-implement the
  formulas.
- The helper exposes, for a given `width`, `height`, `Font`, sample `Component` and
  `TitleCustomization`:
  - plate rectangle and frame-pixel-to-screen scale;
  - name centre, text scale and text box;
  - corner block origin, name scale, kicker scale, gap and bounds.
- The helper applies the clamps in 7.7. The overlay and the editor therefore agree by
  construction.

### 9.2 Large title

- Plate: replace the current fitted rectangle with the transformed one:
  `scale = fit * plate.scale`; the clip rectangle is `round(1440 * scale)` by
  `round(702 * scale)`, centred then shifted by `round(plate.offsetX * width)` and
  `round(plate.offsetY * height)`.
- Seam bands: the same bands are drawn from the transformed clip edges to the screen edges,
  sampling the same edge rows/columns. Moving or scaling the plate carries the flash
  assembly with it. Bands that fall outside the screen are skipped as they are now.
- Name: centre = transformed plate origin + `(720, 260.5) * scale` + the name offset in room
  units; text scale = the existing fit formula at `scale`, multiplied by `name.scale`, then
  hard-capped to the room; centre clamped so the box stays inside the room.
- The animation, alpha, frame timing, sound, pause-hold behaviour and discovery logic are
  untouched.

### 9.3 Corner title

- The block's x is `20 + round(corner.offsetX * width)`.
- The baseline is `height - 20 - (int)(7 * 2.5 * m) + round(corner.offsetY * height)`, with
  the exact-defaults hotbar fallback described in 7.7.
- The name scale is `2.5 * m`, the kicker scale `1.0 * m`, the gap `6 * m`, the letter
  spacing `3 * m` (spacing is inside the scaled sum, matching the current drawing).
- The kicker stays centred over the name. The block is clamped inside the 4 px screen margin.
- Fade in, hold, fade out and all corner playback logic are untouched.

### 9.4 Default equivalence

- With the default config the computed plate rectangle, name centre, name text scale and
  corner baseline/scale must be identical to the current expressions, including integer
  rounding. The implementation should keep the current expression order and use
  `fit * 1.0F`, `+ 0.0F * width` style no-ops only where they provably do not change the
  result, or branch to the exact legacy expression when the config is at defaults.
- The corner hotbar fallback is part of the default path and must remain reachable exactly
  as today.
- A static check must compare the constants and expressions, and the owner's manual test
  must confirm the default reveals look unchanged.

## 10. Edge cases

- Window resize or GUI scale change while the editor is open: re-layout the stage and
  buttons in `init()`; keep the working copy and selection; re-clamp only at draw time.
- GUI height below 210: drop the hint lines, keep the title, stage and buttons.
- Very narrow screen: buttons shrink together; the gear stays inside the Collection block.
- Long real title: the name's hard fit cap keeps it inside the plate; the saved offsets are
  clamped at draw time so a value tuned for a short sample cannot push a long name out.
- Plate moved partly off-screen: allowed, but its centre stays in the central 80% and the
  name is clipped with it. Handles whose true corners are outside the stage are clamped to
  the stage edge and stay grabbable; the wheel always scales the selected element.
- Corner title scaled very large: if it cannot fit the screen at the requested scale, the
  effective scale is reduced to the largest that fits.
- Save write failure (read-only config dir, disk full): message in red, no live change, no
  crash.
- Config file missing: defaults, no file created until Save.
- Config file corrupt: defaults, warning, no crash; the file is only replaced on Save.
- No world open (Collection opened from Mod Menu): the editor works; the setting is global.
- Opening the editor while a reveal plays behind it: the editor is a screen and the HUD is
  not visible; saving takes effect for the next reveal. No reveal state is modified.

## 11. Assets and language

New asset:

- `assets/immersive_titles/textures/gui/gear.png`: 16x16 RGBA pixel-art gear, silver body
  with a dark centre and a dark outline, transparent background, drawn at 16x16 without
  smoothing. It must read on both the plain and lit pack button faces.

Reused assets:

- `textures/title/frame_0100.png` for the preview plate.
- The pack's `widget/button`, `widget/button_highlighted` (and the disabled sprite if
  needed) for the editor buttons and the gear face.

New language keys in `en_us.json`:

- `screen.immersive_titles.collection.customize` = "Customize titles"
- `screen.immersive_titles.customize.title` = "Customize Titles"
- `screen.immersive_titles.customize.hint.move` = "Drag to move - drag a corner or scroll to resize"
- `screen.immersive_titles.customize.hint.save` = "Save keeps changes - closing without Save discards them"
- `screen.immersive_titles.customize.save` = "Save"
- `screen.immersive_titles.customize.reset` = "Reset"
- `screen.immersive_titles.customize.saved` = "Saved"
- `screen.immersive_titles.customize.save_failed` = "Could not save"
- `screen.immersive_titles.customize.sample` = "PALE GARDEN"

The existing keys and the Collection's own labels are not changed.

## 12. Verification

No local Gradle build is allowed. Verification on the owner's machine is static only.

Static checks to add under `_inspect/` (Python, read-only):

- the config path, schema keys, defaults and version;
- the exact clamp ranges and the presence of the default-equivalence path;
- the shared geometry helper is used by both the overlay and the editor, not duplicated;
- the gear is added only in `BiomeCollectionScreen`;
- the preview uses `frame_0100.png` and the real sample, not a mock;
- the save/reset/close semantics and the 2000 ms confirmation;
- the clamped corner handles and the mouse-wheel scaling path;
- the corner default fallback is preserved;
- the lang keys and the gear texture exist;
- all existing static checks still pass.

GitHub verification:

- commit and push to `origin/main`;
- the `_step.build` workflow must build both 26.2 and 26.3;
- confirm the run conclusion and the two jar sizes, then report them.

Owner manual checklist (after the build):

- the gear is visible only in the Collection, on both pages and both filters;
- the editor opens with the default preview, and the stage matches the screen's shape;
- each element selects red, drags and resizes, and stops at its limits;
- Save shows "Saved", keeps the screen open, and the values survive closing and restart;
- Reset returns the preview to default but does not write until Save;
- closing without Save discards;
- the default large reveal and corner title look exactly as before, and the Collection's
  hover/filter/tab behaviour is unchanged;
- a corrupt config falls back to defaults without crashing.

After a successful build: refresh the full project backup ZIP (including `.git`) with the
Arabic state TXT at the root, and report its size and SHA-256 in chat.

## 13. Calls made in this design (owner should check these)

1. Gear position: footer row, right of Done.
2. Preview sample: fixed bold "PALE GARDEN"; static plate frame 100; no animation.
3. Name containment: the name's whole text box stays inside the plate's empty room, so the
   plate and name cannot separate at all.
4. Resize anchored at the element's centre with four corner handles clamped to the stage
   edge, plus mouse-wheel scaling for the selected element (1.05 per notch).
5. Limit numbers: plate scale 0.50-1.75 and offsets +/-0.40; name scale 0.50-1.50 plus fit
   cap and offsets +/-0.50 of the room; corner scale 0.50-3.00 and screen-edge clamp.
6. Corner hotbar avoidance applies only at exact defaults; custom placements are not
   auto-lifted.
7. Editor buttons and confirmation line above them; hints dropped below 210 px height.
8. Config at `config/immersive_titles.json`, schema version 1 as above.
9. Editor buttons and the gear reuse the pack's button faces.

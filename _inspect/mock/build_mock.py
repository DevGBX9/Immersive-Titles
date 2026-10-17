#!/usr/bin/env python3
"""Builds the layout mock of the collection screen as one self-contained page.

The preview panel serves a single file and nothing beside it, so every picture the mock needs - the
game's own window, button, scroller and experience bar sprites, taken out of the cached client jar,
and the mod's two frame lines - is written into the page as a data URL. The page has to be looked at,
so it is worth the few kilobytes, and it can be opened straight from the disk as well.

Three screens are drawn, one under the other: the page of the biomes and the page of the structures,
both of them with the dark gui of the Reimagined GUI resource pack under them, and then the page of the
biomes again with the game's own textures - the same screen down to the pixel with a different pack in
front of it. None of them is the game - the type is a stand-in for the game's own font - but every
rectangle is at the gui pixel the code puts it at, the block, the rows, the scrollbar and the bar
counting the collection are the sprites of the pack and of the jar nine sliced by the browser from the
numbers their mcmeta files carry, and the geometry is worked out the way init() works it out, off the
same numbers.

    python _inspect/mock/build_mock.py
"""

import base64
import json
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
JAR = Path.home() / ".gradle" / "caches" / "fabric-loom" / "26.3" / "minecraft-client.jar"
LINES = HERE.parents[1] / "src" / "main" / "resources" / "assets" / "immersive_titles" / "textures" / "gui"
# the sprites the mod ships: the dark gui of the resource pack, copied into the mod's own assets with
# the permission of its author (see the CREDITS.md beside them), which is what the first two screens of
# the mock - and the screen in the game - are drawn with
SHIPPED = HERE.parents[1] / "src" / "main" / "resources" / "assets" / "immersive_titles" /     "textures" / "gui" / "sprites"

PAGE = """<!doctype html>
<html><head><meta charset="utf-8"><title>Biome collection layout mock</title>
<style>
  html,body{margin:0;background:#101418;color:#ddd;font:12px monospace}
  p{margin:8px 12px;max-width:1200px;line-height:1.5}
  h2{font:12px monospace;margin:14px 12px 4px;color:#9cc}
  /* one gui pixel is two device pixels here, which is what the game does at gui scale 2: a row is
     twenty-eight device pixels tall and a line of text is eighteen */
  #frame{transform-origin:0 0;transform:scale(var(--fit,1))}
  #screens{position:relative;margin-top:12px}
  .screen{position:absolute;left:0;overflow:hidden;width:1366px;height:768px;
          background:linear-gradient(#2b4a72,#5c7f4a 45%,#3f5d33)}
  .screen>div,.screen>div>div{position:absolute;image-rendering:pixelated;
          background-repeat:no-repeat;background-size:100% 100%}
  .txt{background:none !important;font:18px/18px monospace;color:#fff;text-shadow:2px 2px 0 #000}
  .row{font:18px/28px monospace;color:#fff;text-shadow:2px 2px 0 #000}
  .sec{font:18px/28px monospace;color:#ffaa00;text-shadow:2px 2px 0 #000}
  .btn{font:18px/32px monospace;color:#fff;text-shadow:2px 2px 0 #000}
  .tab{font:18px/32px monospace;text-shadow:2px 2px 0 #000;text-align:center}
  .fil{font:18px/32px monospace;text-shadow:2px 2px 0 #000;text-align:center}
  /* the game's own window and its own button, nine sliced from their sprites' own numbers: the window
     keeps eight pixels a side, the button twenty on the left and right and four over and under */
  .window{border-style:solid;border-width:16px;border-image-slice:8 fill;border-image-repeat:stretch;
          box-sizing:border-box}
  .button{border-style:solid;border-width:8px 40px;border-image-slice:4 20 4 20 fill;
          border-image-repeat:stretch;box-sizing:border-box}
</style></head><body>
<p><b>Mock of the collection screen at gui scale 2</b> (1366x768 device pixels, the window the game
saw in the screenshot this was drawn from). The list is 320 gui pixels wide at most and centred, and
the two lines framing it are the mod's own drawing at the size they are made at: a needle with a
thread through it, 228 x 22 gui pixels over the list - as wide as the strip of the two tabs, which
rests on the frame of the list - and 140 x 18 under it. Over that: the title at four fifths of the
game's own font, the line counting the discoveries and the bar that counts them at a glance, a row of
fourteen gui pixels with a small check and a clear white name for a biome that was found and a dimmer
name for one that is still waiting, and the row the mouse is on held in a thin gold line. The bar
counting the collection is the game's own experience bar, trough and fill both, cut out of its two
sprites at the share the line over it counts. The block the list is held in is the game's own window,
and the two small buttons beside it are set off the frame of the list rather than off the list itself,
which is the whole of what the two of them are drawn here to show: neither of them is over the block
any more. The second screen is the page that collects nothing yet, with the button that closes the
screen under the keyboard's focus and its silver border.</p>
<div id="frame"><div id="screens"></div></div>
<script>
// the numbers the screen's constants make, in gui pixels
const K = { titleY: 8, titleSize: 0.8, progressY: 20, textHeight: 9,
            barY: 31, barWidth: 182, barHeight: 5,
            upLineY: 38, upLineW: 228, upLineH: 22, tabsY: 62, tabHeight: 20, tabGap: 2,
            tabWidth: 113, tabStripWidth: 228, listTop: 90, framePadding: 8, shadowOffset: 2,
            downLineOffset: 10, downLineW: 140, downLineH: 18, doneWidth: 110, doneHeight: 16,
            panelMargin: 20, listMaxWidth: 320, listMaxHeightPercent: 50, rowHeight: 14, rowPitch: 16,
            filterSize: 16, filterGap: 4, sideSpace: 36, screenMargin: 8, scrollBarWidth: 4,
            scrollBarMargin: 3, checkX: 7, nameX: 17, textMargin: 6, forgetSize: 12,
            text: 9, guiWidth: 683, guiHeight: 384 };

// the colours the screen draws itself, which is everything its sprites are not: the one palette it
// has, since the collection is no longer read in two themes
const LOOK = { shadow: 'rgba(0,0,0,0.35)',
               title: '#e8e8e8', count: '#c9c9c9', section: '#e0b15a', rule: 'rgba(255,255,255,0.20)',
               found: '#f2f2f2', waiting: '#787878', source: '#6a6a6a', check: '#66e06a',
               hover: '#e0b15a', tabActive: 'rgba(0,0,0,0.60)', tabInactive: 'rgba(0,0,0,0.25)',
               accent: '#e0b15a', tabOn: '#ffffff', tabOff: '#8a8a8a' };

// the two sets of sprites the same screen is drawn with below: the game's own out of the jar, and the
// dark ones of the Reimagined GUI resource pack, which are what a player with that pack sees. Every
// drawing function below reads the set that is current, so a screen is drawn by setting it first.
const SETS = __IMAGES__;
let IMAGES = SETS.vanilla;

const footer = K.downLineOffset + K.downLineH + 2 + K.doneHeight;

// the two pages of the collection, written in the strip of tabs under the plate: the strip is as wide
// as the line drawn over it and each tab is half of it, so a name is written in the middle of its own
// half rather than in a box fitted around it (see tabStrip)
const TAB = { BIOMES: 'Biomes', STRUCTURES: 'Structures' };

const px = n => n * 2;

function layout(){
  const width = K.guiWidth, height = K.guiHeight;
  let listWidth = Math.max(0, Math.min(K.listMaxWidth, width - 2*K.sideSpace));
  const room = Math.max(0, height - K.listTop - footer - 2*K.screenMargin);
  let listHeight = Math.min(room, Math.floor(height * K.listMaxHeightPercent / 100));
  const spare = height - (K.listTop + listHeight + footer);

  listWidth = Math.max(0, listWidth - (width - listWidth) % 2);
  if (spare > 1) listHeight -= spare % 2;

  const blockTop = Math.max(K.screenMargin, Math.floor((height - (K.listTop + listHeight + footer)) / 2));
  const listLeft = Math.floor((width - listWidth) / 2);

  return { listLeft, listWidth, listHeight, blockTop,
           listTop: blockTop + K.listTop, listBottom: blockTop + K.listTop + listHeight };
}

const layoutOf = layout();

function box(screen, x, y, w, h, style){
  const e = document.createElement('div');
  e.style.left = px(x)+'px'; e.style.top = px(y)+'px';
  e.style.width = px(w)+'px'; e.style.height = px(h)+'px';
  Object.assign(e.style, style||{});
  screen.appendChild(e);
  return e;
}

function frame(screen, x, y, w, h, color){
  box(screen, x, y, w, 1, {background: color});
  box(screen, x, y + h - 1, w, 1, {background: color});
  box(screen, x, y + 1, 1, h - 2, {background: color});
  box(screen, x + w - 1, y + 1, 1, h - 2, {background: color});
}

// one of the game's own sprites nine sliced to the box it is drawn in: the numbers are the ones the
// sprite's mcmeta carries, scaled up with the rest of the mock (see the .window and .button rules)
function sprite(screen, x, y, w, h, image, kind){
  const e = box(screen, x, y, w, h, {});
  e.classList.add(kind);
  e.style.borderImageSource = `url(${image})`;
  return e;
}

function centred(screen, y, text, color, height){
  const e = box(screen, 0, y, K.guiWidth, height || K.text, {});
  e.className = 'txt'; e.textContent = text;
  e.style.textAlign = 'center'; e.style.width = px(K.guiWidth)+'px';
  if (color) e.style.color = color;
  return e;
}

// the title of the screen, set at four fifths of the game's own font: the mock writes it in a stand-in
// font and scales the line, which is what the screen does with the game's own font
function title(screen, theme){
  theme = LOOK;
  const e = box(screen, 0, layoutOf.blockTop + K.titleY, K.guiWidth, K.text, {});
  e.className = 'txt'; e.textContent = 'Collection';
  e.style.textAlign = 'center'; e.style.width = px(K.guiWidth)+'px';
  e.style.color = theme.title;
  e.style.transformOrigin = 'center top';
  e.style.transform = `scale(${K.titleSize})`;
}

// the bar that counts the collection at a glance, under the line that counts it in words: the game's
// own experience bar in the mod's theme - the trough of it whole, the found share cut out of the
// sprite it is filled with - and the flat trough and fill of the game's own theme
function progressBar(screen, theme, found, total){
  const x = Math.floor((K.guiWidth - K.barWidth) / 2);
  const y = layoutOf.blockTop + K.barY;
  const filled = total ? Math.round(found / total * K.barWidth) : 0;

  box(screen, x, y, K.barWidth, K.barHeight, {backgroundImage:`url(${IMAGES.bar_track})`});

  if (filled > 0){
    box(screen, x, y, filled, K.barHeight, {backgroundImage:`url(${IMAGES.bar_fill})`,
        backgroundSize: `${px(K.barWidth)}px ${px(K.barHeight)}px`});
  }
}

function tabStrip(screen, page, theme){
  let x = layoutOf.listLeft + Math.floor((layoutOf.listWidth - K.tabStripWidth) / 2);

  for (const name of [TAB.BIOMES, TAB.STRUCTURES]){
    const reading = name === TAB[page];
    const b = sprite(screen, x, layoutOf.blockTop + K.tabsY, K.tabWidth, K.tabHeight,
        IMAGES.button, 'button');

    if (reading ? theme.tabActive : theme.tabInactive){
      box(screen, x, layoutOf.blockTop + K.tabsY, K.tabWidth, K.tabHeight,
          {background: reading ? theme.tabActive : theme.tabInactive});
    }

    if (reading && theme.accent){
      box(screen, x, layoutOf.blockTop + K.tabsY + K.tabHeight - 1, K.tabWidth, 1,
          {background: theme.accent});
    }

    b.className = 'tab'; b.textContent = name; b.style.lineHeight = px(K.tabHeight)+'px';
    b.style.color = reading ? theme.tabOn : theme.tabOff;
    x += K.tabWidth + K.tabGap;
  }
}

function rows(screen, found, waiting, waitingNames, theme, hoveredIndex){
  const rowRight = layoutOf.listLeft + layoutOf.listWidth - K.scrollBarWidth - K.scrollBarMargin;
  let index = 0;
  const row = (text, style, kind) => {
    const y = layoutOf.listTop + index * K.rowPitch;
    if (y >= layoutOf.listBottom) return;
    const e = sprite(screen, layoutOf.listLeft, y, rowRight - layoutOf.listLeft, K.rowHeight,
        IMAGES[kind], 'button');

    // the row the mouse is on, held in a thin line of the theme's own
    if (index === hoveredIndex && theme.hover){
      frame(screen, layoutOf.listLeft, y, rowRight - layoutOf.listLeft, K.rowHeight, theme.hover);
    }

    // the text is written over the row rather than inside it, at the two places the screen writes a
    // check and a name at, since the border of a nine-sliced button is not room the text sits in
    const t = box(screen, layoutOf.listLeft + K.nameX, y,
        rowRight - layoutOf.listLeft - K.nameX, K.rowHeight, {});
    t.className = 'row'; t.style.lineHeight = px(K.rowHeight)+'px';
    Object.assign(t.style, style);
    t.textContent = text;
    index++;
  };

  const section = (name, count) => {
    const y = layoutOf.listTop + index * K.rowPitch;
    if (y >= layoutOf.listBottom) return;
    box(screen, layoutOf.listLeft, y - 1, rowRight - layoutOf.listLeft, 0.5, {background: theme.rule});
    const e = box(screen, 0, y, K.guiWidth, K.rowHeight, {});
    e.className = 'sec'; e.style.lineHeight = px(K.rowHeight)+'px'; e.style.textAlign = 'left';
    e.style.color = theme.section; e.style.paddingLeft = px(K.nameX)+'px'; e.textContent = name;
    const c = box(screen, 0, y, K.guiWidth, K.rowHeight, {});
    c.className = 'sec'; c.style.color = theme.count; c.style.lineHeight = px(K.rowHeight)+'px';
    c.style.textAlign = 'right'; c.style.paddingRight = px(K.textMargin)+'px';
    c.textContent = count;
    index++;
  };

  section('Overworld', '15 / 56');
  found.forEach(name => row('\\u2714  ' + name, {color: theme.found}, 'button'));
  for (const name of waiting){
    row('    ' + name, {color: theme.waiting}, 'button_disabled');
  }

  return rowRight;
}

function build(page, images, options){
  // the set of sprites this screen is drawn with, and the one palette the screen draws itself in
  IMAGES = images;
  const theme = LOOK;
  const screen = document.createElement('div');
  screen.className = 'screen';
  document.getElementById('screens').appendChild(screen);

  // what is behind the block: the game's own menu background over the blurred world, which the screen
  // leaves to the game and the mock stands in for (it has no world to blur and no menu background of
  // the game's to draw, so what is drawn here is the shade that background leaves over the world)
  box(screen, 0, 0, K.guiWidth, K.guiHeight,
      {background:'linear-gradient(rgba(18,18,22,0.63), rgba(18,18,22,0.69))'});

  title(screen, theme);

  if (page === 'BIOMES'){
    centred(screen, layoutOf.blockTop + K.progressY, '15 of 67 biomes discovered in your worlds',
        theme.count);
    progressBar(screen, theme, 15, 67);
  }

  tabStrip(screen, page, theme);

  // the two lines of the mod's own, centred on the list, and the block of the list between them
  const centredOn = (width, y, height, style) =>
      box(screen, layoutOf.listLeft + (layoutOf.listWidth - width)/2, y, width, height, style);

  centredOn(K.upLineW, layoutOf.blockTop + K.upLineY, K.upLineH,
      {backgroundImage:`url(${IMAGES.up_line})`});

  const left = layoutOf.listLeft - K.framePadding, top = layoutOf.listTop - K.framePadding;
  const right = layoutOf.listLeft + layoutOf.listWidth + K.framePadding;
  const bottom = layoutOf.listBottom + K.framePadding;

  if (theme.shadow){
    box(screen, left + K.shadowOffset, top + K.shadowOffset, right - left, bottom - top,
        {background: theme.shadow});
  }

  // the block the list is held in: the game's own window in the mod's theme, one of the game's own
  // buttons in the game's own theme, both of them nine sliced to the size the block comes to
  sprite(screen, left, top, right - left, bottom - top, IMAGES.window, 'window');

  let rowRight = layoutOf.listLeft + layoutOf.listWidth - K.scrollBarWidth - K.scrollBarMargin;

  if (page === 'BIOMES'){
    rowRight = rows(screen,
        ['Plains', 'Forest', 'Birch Forest', 'Savanna', 'Ocean', 'River'],
        ['Taiga', 'Snowy Plains', 'Desert', 'Sunflower Plains', 'Swamp', 'Jungle'],
        null, theme, options.hovered);

    // the scrollbar, the game's own scroller in its groove, drawn four pixels wide
    box(screen, layoutOf.listLeft + layoutOf.listWidth - K.scrollBarWidth, layoutOf.listTop,
        K.scrollBarWidth, layoutOf.listBottom - layoutOf.listTop,
        {backgroundImage:`url(${IMAGES.scroller_background})`});
    box(screen, layoutOf.listLeft + layoutOf.listWidth - K.scrollBarWidth, layoutOf.listTop + 16,
        K.scrollBarWidth, 96, {backgroundImage:`url(${IMAGES.scroller})`});
  } else {
    const e = box(screen, 0, layoutOf.listTop +
        Math.floor((layoutOf.listBottom - layoutOf.listTop - K.text)/2), K.guiWidth, K.text, {});
    e.className = 'txt'; e.style.textAlign = 'center'; e.style.width = px(K.guiWidth)+'px';
    e.style.color = theme.count; e.textContent = 'Structures are not collected yet';
  }

  centredOn(K.downLineW, layoutOf.listBottom + K.downLineOffset, K.downLineH,
      {backgroundImage:`url(${IMAGES.down_line})`});

  if (page === 'BIOMES'){
    // the two filter buttons stand beside the frame of the list and not beside the list itself, level
    // with its first row: what the gap is measured from is the frame, which is what they would
    // otherwise be drawn over (see FILTER_BUTTON_GAP in the screen)
    const offList = K.framePadding + K.filterGap;

    [[true, '\\u2714', theme.check], [false, '?', '#ffffff']].forEach(([onLeft, mark, color]) => {
      const x = onLeft ? layoutOf.listLeft - offList - K.filterSize
                       : layoutOf.listLeft + layoutOf.listWidth + offList;
      const b = box(screen, x, layoutOf.listTop, K.filterSize, K.filterSize,
          {backgroundImage:`url(${IMAGES.button})`});
      b.className = 'fil'; b.textContent = mark; b.style.color = color;
      b.style.lineHeight = px(K.filterSize)+'px';
    });
  }

  const doneY = layoutOf.listBottom + K.downLineOffset + K.downLineH + 2;
  const done = sprite(screen, Math.floor(K.guiWidth/2 - K.doneWidth/2), doneY, K.doneWidth,
      K.doneHeight, IMAGES.button, 'button');
  done.className = 'btn'; done.textContent = 'Done'; done.style.textAlign = 'center';
  done.style.lineHeight = px(K.doneHeight)+'px';
  done.style.color = '#ffffff';

  if (options.focused){
    frame(screen, Math.floor(K.guiWidth/2 - K.doneWidth/2) - 1, doneY - 1, K.doneWidth + 2,
        K.doneHeight + 2, '#c9ccd1');
  }

  return screen;
}

build('BIOMES', SETS.shipped, {hovered: 2});
build('STRUCTURES', SETS.shipped, {focused: true});
build('BIOMES', SETS.vanilla, {});

// one gui pixel of the mock is two device pixels, so the whole thing is shown at half size at most
function fit(){
  const room = Math.max(320, document.documentElement.clientWidth - 32);
  const scale = Math.min(0.5, room / 1366);
  document.getElementById('frame').style.setProperty('--fit', scale);
  const step = 768 * scale + 40;
  document.getElementById('screens').style.height = (3 * step) + 'px';
  document.querySelectorAll('.screen').forEach((s, i) => { s.style.top = (i * step) + 'px'; });
  document.body.style.height = (3 * step + 320) + 'px';
}
addEventListener('resize', fit); fit();
</script></body></html>
"""


def data_url(raw):
    return "data:image/png;base64," + base64.b64encode(raw).decode("ascii")


SPRITES = (("button", "widget/button"),
           ("button_disabled", "widget/button_disabled"),
           ("scroller", "widget/scroller"),
           ("scroller_background", "widget/scroller_background"),
           ("window", "social_interactions/background"),
           ("bar_track", "hud/experience_bar_background"),
           ("bar_fill", "hud/experience_bar_progress"))


def main():
    # the two drawings of the mod's own, which no pack redraws: the same two in both sets
    lines = {key: data_url((LINES / name).read_bytes())
             for key, name in (("up_line", "collection_up_line.png"),
                               ("down_line", "collection_down_line.png"))}

    vanilla = dict(lines)
    shipped = dict(lines)

    with zipfile.ZipFile(JAR) as jar:
        for key, name in SPRITES:
            vanilla[key] = data_url(jar.read(f"assets/minecraft/textures/gui/sprites/{name}.png"))

            # the mod ships the four sprites the screen draws itself out of and not the scroller, which
            # is a sprite of the game's own in the mod as well: there is nothing to fall back to, so a
            # sprite the mod does not ship is simply not drawn
            source = SHIPPED / f"{name}.png"

            if source.is_file():
                shipped[key] = data_url(source.read_bytes())

    page = PAGE.replace("__IMAGES__", json.dumps({"vanilla": vanilla, "shipped": shipped}))
    target = HERE / "layout.html"
    target.write_text(page, encoding="utf-8")
    print(f"{target} written, {target.stat().st_size} bytes")


if __name__ == "__main__":
    main()

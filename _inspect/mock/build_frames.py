#!/usr/bin/env python3
"""Draws the collection block in the game's own window sprite, three ways, to be looked at.

The dark theme of the collection is drawn out of flat colours today - a charcoal band around the
panel, a thin border around the band and a shadow under the block. The question this page answers is
what the same block looks like when the frame comes out of the game's own textures instead, which are
the ones on disk in the client jar and in the folder the owner pointed at.

Three ways of using `gui/sprites/social_interactions/background` - the game's own window, a raised
sprite with a nine-slice of eight pixels and an interior of #353535 - are drawn one under the other,
all of them with the same rows, the same scrollbar and the same bar counting the collection:

  A  the whole block is the window sprite: the frame band and the panel are the one picture, so the
     block is the grey window the game's own lists sit in and the rows are read against it,
  B  the window sprite is the band around the panel and the panel stays the mod's charcoal, so the
     block keeps the near-black list it has and gains a lit, bevelled frame around it,
  C  the band stays the mod's own thin one and the window sprite is the panel, so the list itself is
     the game's window - bevel, outline and all - inside the frame it already had.

The sprites are read out of the client jar that is already on disk and written into the page as data
URLs, and the nine-slices are done by the browser's own border-image, from the same numbers the
sprites' mcmeta files carry. Nothing here is the game: it is a page to be looked at, so that the way
the frame is drawn can be chosen by eye before it is written into the screen.

    python _inspect/mock/build_frames.py
"""

import base64
import json
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
JAR = Path.home() / ".gradle" / "caches" / "fabric-loom" / "26.3" / "minecraft-client.jar"

# one gui pixel of the mock is three device pixels, so a one-pixel line of a sprite is a line and the
# bevel of a nine-slice can be read for what it is
SCALE = 3

PAGE = """<!doctype html>
<html><head><meta charset="utf-8"><title>Collection block: the game's window sprite</title>
<style>
  html,body{margin:0;background:#101418;color:#ddd;font:12px monospace}
  p{margin:8px 14px;max-width:1100px;line-height:1.55}
  h2{font:13px monospace;margin:16px 14px 6px;color:#9cc}
  #screens{position:relative;margin-top:8px}
  .screen{position:absolute;left:0;overflow:hidden;width:__W__px;height:__H__px;
          background:linear-gradient(#2b4a72,#5c7f4a 45%,#3f5d33)}
  .screen>div{position:absolute;image-rendering:pixelated}
  .txt{font:__F__px/__L__px monospace;color:#fff;text-shadow:2px 2px 0 #000}
  .row,.sec{font:__F__px/__RH__px monospace;text-shadow:2px 2px 0 #000}
  .tab{font:__F__px/__L__px monospace;text-align:center;text-shadow:2px 2px 0 #000}
  .btn{font:__F__px/__L__px monospace;text-align:center;text-shadow:2px 2px 0 #000}
  /* the nine-slices, from the sprites' own mcmeta: the window keeps eight pixels on every side, the
     button twenty on the left and right and four over and under */
  .window{border-style:solid;border-width:__W8__px;border-image-slice:8 fill;
          border-image-repeat:stretch;box-sizing:border-box}
  .window2{border-style:solid;border-width:__W8__px;border-image-slice:8;
          border-image-repeat:stretch;box-sizing:border-box}
  .button{border-style:solid;border-width:__W4__px __W20__px;border-image-slice:4 20 4 20 fill;
          border-image-repeat:stretch;box-sizing:border-box}
  .barborder{border-style:solid;border-width:__W1__px;border-image-slice:1 fill;
             border-image-repeat:stretch;box-sizing:border-box}
  .barfill{border-style:solid;border-width:__W1__px 0 __W1__px __W1__px;border-image-slice:1 0 1 1 fill;
           border-image-repeat:stretch;box-sizing:border-box}
  #frame{transform-origin:0 0;transform:scale(var(--fit,1))}
</style></head><body>
<p><b>The block, three ways, in the game's own window sprite.</b> Every rectangle is at the gui pixel
the screen puts it at, one gui pixel is three device pixels, and the sprites are the game's own, nine
sliced by the browser from the numbers their mcmeta files carry: <code>social_interactions/background</code>
(the window, eight pixels a side, interior #353535), <code>widget/button</code> (the rows, twenty
pixels on the left and right and four over and under) and <code>widget/button_disabled</code> (the rows
that are still waiting). The bar counting the collection is the game's own experience bar,
<code>hud/experience_bar_background</code> and <code>hud/experience_bar_progress</code>, 182 x 5 gui
pixels with the found share of it cut out of the progress sprite.</p>
<div id="frame"><div id="screens"></div></div>
<script>
const IMAGES = __IMAGES__;
const S = __SCALE__, K = __K__;
const px = n => n * S;

function screen(title){
  const s = document.createElement('div');
  s.className = 'screen';
  s.style.backgroundImage = 'linear-gradient(rgba(18,18,22,0.63), rgba(18,18,22,0.69))';
  document.getElementById('screens').appendChild(s);
  const h = document.createElement('div');
  h.className = 'txt'; h.style.left = 0; h.style.top = px(8); h.style.width = '100%';
  h.style.textAlign = 'center'; h.textContent = title;
  s.appendChild(h);
  return s;
}

function box(parent, x, y, w, h, style){
  const e = document.createElement('div');
  e.style.left = px(x)+'px'; e.style.top = px(y)+'px';
  e.style.width = px(w)+'px'; e.style.height = px(h)+'px';
  Object.assign(e.style, style || {});
  parent.appendChild(e);
  return e;
}

function frame(parent, x, y, w, h, color){
  box(parent, x, y, w, 1, {background: color});
  box(parent, x, y + h - 1, w, 1, {background: color});
  box(parent, x + 1, y + 1, 1, h - 2, {background: color});
  box(parent, x + w - 2, y + 1, 1, h - 2, {background: color});
}

function title(s, text, size, color, y){
  const e = box(s, 0, y, K.screenW, K.text, {});
  e.className = 'txt'; e.style.textAlign = 'center'; e.style.width = '100%';
  e.style.color = color; e.style.transformOrigin = 'center top';
  e.style.transform = `scale(${size})`; e.textContent = text;
}

function progressBar(s, x, y, found, total){
  const w = K.barW, filled = total ? Math.round(found / total * w) : 0;
  if (filled > 0){
    box(s, x, y, filled, K.barH, {backgroundImage: `url(${IMAGES.bar_fill})`,
        backgroundSize: px(w)+'px '+px(K.barH)+'px'});
  }
  box(s, x, y, w, K.barH, {backgroundImage: `url(${IMAGES.bar_track})`,
      backgroundSize: px(w)+'px '+px(K.barH)+'px'});
}

function tabs(s, y, x, w){
  const names = ['Biomes', 'Structures'];
  let left = x;
  for (let i = 0; i < 2; i++){
    const b = box(s, left, y, K.tabW, 20, {});
    b.className = 'tab'; b.textContent = names[i];
    b.classList.add('button');
    b.style.borderImageSource = `url(${IMAGES.button})`;
    b.style.lineHeight = px(20)+'px';
    b.style.color = i === 0 ? '#ffffff' : '#8a8a8a';
    if (i === 0){
      box(s, left, y, K.tabW, 20, {background: 'rgba(0,0,0,0.60)'});
      box(s, left, y + 19, K.tabW, 1, {background: '#e0b15a'});
    } else {
      box(s, left, y, K.tabW, 20, {background: 'rgba(0,0,0,0.25)'});
    }
    left += K.tabW + 2;
  }
}

function rows(s, left, top, right, bottom, hovered){
  const rowRight = right - 4 - 3;
  let index = 0;
  const pitch = 16, height = 14;
  const at = () => top + index * pitch;

  const section = (name, count) => {
    const y = at();
    if (y >= bottom) return;
    box(s, left, y - 1, rowRight - left, 1, {background: 'rgba(255,255,255,0.20)'});
    const l = box(s, 0, y, K.screenW, height, {});
    l.className = 'sec'; l.style.color = '#e0b15a'; l.style.paddingLeft = px(17)+'px';
    l.style.width = '100%'; l.textContent = name;
    const c = box(s, 0, y, K.screenW, height, {});
    c.className = 'sec'; c.style.color = '#c9c9c9'; c.style.textAlign = 'right';
    c.style.width = '100%'; c.style.paddingRight = px(6)+'px'; c.textContent = count;
    index++;
  };

  const row = (name, found) => {
    const y = at();
    if (y >= bottom) return;
    const r = box(s, left, y, rowRight - left, height, {});
    r.classList.add('button');
    r.style.borderImageSource = `url(${found ? IMAGES.button : IMAGES.waiting})`;
    r.style.lineHeight = px(height)+'px';
    r.style.color = found ? '#f2f2f2' : '#787878';
    r.style.font = '__F__px/__RH__px monospace';
    r.style.paddingLeft = px(17)+'px';
    r.textContent = name;
    if (index === hovered) frame(s, left, y, rowRight - left, height, '#e0b15a');
    index++;
  };

  section('Overworld', '15 / 56');
  row('Plains', true); row('Forest', true); row('Birch Forest', true);
  row('Taiga', false); row('Snowy Plains', false); row('Desert', false);
  row('Savanna', true); row('Ocean', true);

  box(s, right - 4, top, 4, bottom - top, {backgroundImage: `url(${IMAGES.scroller_background})`,
      backgroundSize: px(4)+'px '+px(bottom - top)+'px'});
  box(s, right - 4, top + 16, 4, 96, {backgroundImage: `url(${IMAGES.scroller})`,
      backgroundSize: px(4)+'px '+px(96)+'px'});
}

function done(s, centerX, y){
  const b = box(s, centerX - 55, y, 110, 16, {});
  b.classList.add('button'); b.style.borderImageSource = `url(${IMAGES.button})`;
  b.className += ' btn'; b.textContent = 'Done'; b.style.lineHeight = px(16)+'px';
}

function block(variant){
  const s = screen(variant.title);
  const pad = variant.pad;
  const left = K.listLeft - pad, top = K.listTop - pad;
  const right = K.listLeft + K.listW + pad, bottom = K.listTop + K.listH + pad;

  title(s, 'Collection', 0.8, '#e8e8e8', 8);
  title(s, '15 of 67 biomes discovered in your worlds', 1.0, '#c9c9c9', 20);
  progressBar(s, Math.floor((K.screenW - K.barW) / 2), 31, 15, 67);
  tabs(s, 66, K.listLeft + Math.floor((K.listW - K.tabW * 2 - 2) / 2), K.listLeft);

  if (variant.shadow)
    box(s, left + 2, top + 2, right - left, bottom - top, {background: 'rgba(0,0,0,0.35)'});

  if (variant.band === 'window'){
    const e = box(s, left, top, right - left, bottom - top, {});
    e.classList.add('window'); e.style.borderImageSource = `url(${IMAGES.window})`;
  } else {
    box(s, left, top, right - left, bottom - top, {background: '#23262b'});
    frame(s, left, top, right - left, bottom - top, '#3d444e');
  }

  if (variant.panel === 'window'){
    const e = box(s, K.listLeft, K.listTop, K.listW, K.listH, {});
    e.classList.add('window'); e.style.borderImageSource = `url(${IMAGES.window})`;
  } else if (variant.panel === 'charcoal'){
    box(s, K.listLeft, K.listTop, K.listW, K.listH, {background: 'rgba(24,26,30,0.94)'});
    box(s, K.listLeft, K.listTop, K.listW, 1, {background: '#2c3138'});
  }

  rows(s, K.listLeft, K.listTop, K.listLeft + K.listW, K.listTop + K.listH, 2);
  done(s, Math.floor(K.screenW / 2), K.listTop + K.listH + 5 + 18 + 3);
  return s;
}

const VARIANTS = [
  {title: 'A  the whole block is the game\\u2019s window sprite (padding 8)',
   band: 'window', panel: 'none', pad: 8, shadow: true},
  {title: 'B  the window sprite is the band, the panel stays the mod\\u2019s charcoal (padding 8)',
   band: 'window', panel: 'charcoal', pad: 8, shadow: true},
  {title: 'C  the band stays the mod\\u2019s, the window sprite is the panel (padding 3)',
   band: 'own', panel: 'window', pad: 3, shadow: true}
];

VARIANTS.forEach(block);

function fit(){
  const room = Math.max(320, document.documentElement.clientWidth - 32);
  const scale = Math.min(1, room / __W__);
  document.getElementById('frame').style.setProperty('--fit', scale);
  const step = __H__ * scale + 30;
  document.getElementById('screens').style.height = (3 * step) + 'px';
  document.querySelectorAll('.screen').forEach((s, i) => { s.style.top = (i * step) + 'px'; });
  document.body.style.height = (3 * step + 220) + 'px';
}
addEventListener('resize', fit); fit();
</script></body></html>
"""


def data_url(raw):
    return "data:image/png;base64," + base64.b64encode(raw).decode("ascii")


def main():
    images = {}
    with zipfile.ZipFile(JAR) as jar:
        for key, name in (("button", "widget/button"), ("waiting", "widget/button_disabled"),
                          ("window", "social_interactions/background"),
                          ("scroller", "widget/scroller"),
                          ("scroller_background", "widget/scroller_background"),
                          ("bar_track", "hud/experience_bar_background"),
                          ("bar_fill", "hud/experience_bar_progress")):
            images[key] = data_url(jar.read(f"assets/minecraft/textures/gui/sprites/{name}.png"))

    # the geometry, in gui pixels: a 683 x 357 screen - a 1366 x 768 window at gui scale 2 - with the
    # list 320 wide and 112 tall and the block set in the middle of the room the title and the button
    # leave (the numbers init() works out for that screen, read off the screen's own constants)
    k = {"screenW": 683, "screenH": 357, "listLeft": 181, "listTop": 140, "listW": 320, "listH": 112,
         "text": 9, "barW": 182, "barH": 5, "tabW": 113}

    page = (PAGE.replace("__IMAGES__", json.dumps(images))
                .replace("__SCALE__", str(SCALE))
                .replace("__K__", json.dumps(k))
                .replace("__W__", str(k["screenW"] * SCALE))
                .replace("__H__", str(k["screenH"] * SCALE))
                .replace("__F__", str(9 * SCALE))
                .replace("__L__", str(9 * SCALE))
                .replace("__RH__", str(14 * SCALE))
                .replace("__W1__", str(1 * SCALE))
                .replace("__W4__", str(4 * SCALE))
                .replace("__W8__", str(8 * SCALE))
                .replace("__W20__", str(20 * SCALE)))

    target = HERE / "frames.html"
    target.write_text(page, encoding="utf-8")
    print(f"{target} written, {target.stat().st_size} bytes")


if __name__ == "__main__":
    main()

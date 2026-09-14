"""Vaani design-system tokens + sharp-corner drawing helpers (light 'cream' theme)."""
from PIL import Image, ImageDraw, ImageFont
import os
import sys
sys.path.insert(0, "/tmp")
import vaani_icons as IC

W, H = 1080, 2340
PAD = 64

# ---- palette (charcoal / coffee / cream / slate) ----
CREAM_BG  = (243, 234, 219)  # background
CREAM_HI  = (251, 246, 236)  # raised surface
CREAM_LO  = (236, 224, 205)  # sunken / track
CHARCOAL  = (27, 26, 24)     # ink text / dark surface
CHAR_HI   = (33, 31, 29)     # dark surface raised
INK_SOFT  = (74, 64, 56)     # secondary text
MUTED     = (138, 126, 112)  # muted text
COFFEE    = (129, 95, 65)    # primary / warm accent
COFFEE_DK = (101, 72, 51)
COFFEE_LT = (161, 124, 91)
SLATE     = (99, 112, 125)   # secondary / cool accent
SLATE_DK  = (78, 90, 102)
SLATE_LT  = (155, 165, 174)
LINE      = (216, 202, 180)  # hairline
SUCCESS   = (122, 138, 107)  # sage
WARNING   = (201, 161, 91)   # honey
DANGER    = (181, 112, 94)   # clay

_FONTS = {}
def F(sz, bold=True):
    key = (sz, bold)
    if key in _FONTS: return _FONTS[key]
    for p in ([
        "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]):
        try:
            f = ImageFont.truetype(p, sz); _FONTS[key] = f; return f
        except Exception: continue
    f = ImageFont.load_default(); _FONTS[key] = f; return f

def MONO(sz):
    key = ("mono", sz)
    if key in _FONTS: return _FONTS[key]
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"]:
        try:
            f = ImageFont.truetype(p, sz); _FONTS[key] = f; return f
        except Exception: continue
    return F(sz, False)

def canvas(bg=CREAM_BG):
    img = Image.new("RGB", (W, H), bg)
    return img, ImageDraw.Draw(img)

def rect(d, x, y, w, h, c):           # SHARP corners always
    d.rectangle([x, y, x + w, y + h], fill=c)

def stroke(d, x, y, w, h, c, wid=2):
    d.rectangle([x, y, x + w, y + h], outline=c, width=wid)

def hline(d, x, y, w, c, t=2):
    d.rectangle([x, y, x + w, y + t], fill=c)

def text(d, x, y, s, f, c):
    d.text((x, y), s, font=f, fill=c)

def center(d, s, y, f, c, cx=W // 2):
    w = d.textlength(s, font=f); d.text((cx - w / 2, y), s, font=f, fill=c)

def rtext(d, xr, y, s, f, c):         # right-aligned to xr
    w = d.textlength(s, font=f); d.text((xr - w, y), s, font=f, fill=c)

def statusbar(d, dark=False):
    ink = CREAM_HI if dark else CHARCOAL
    text(d, PAD, 54, "9:41", F(30), ink)
    IC.wifi(d, W - PAD - 150, 48, 40, ink)
    IC.battery(d, W - PAD - 96, 46, 46, ink, pct=0.82)

def bottomnav(d, active, dark=False):
    """5-tab bottom nav, sharp, with real line icons.
    tabs: Library, Search, [REC mic FAB], Tasks, Settings."""
    navy = CHAR_HI if dark else CREAM_HI
    y = H - 150
    rect(d, 0, y, W, 150, navy)
    hline(d, 0, y, W, (58,54,50) if dark else LINE, 2)
    tabs = [("Library", IC.library), ("Search", IC.search), ("", None),
            ("Tasks", IC.check_square), ("Settings", IC.sliders)]
    n = len(tabs); seg = W / n
    for i, (t, icon) in enumerate(tabs):
        cx = seg * i + seg / 2
        if i == 2:
            rect(d, cx - 54, y + 22, 108, 108, COFFEE)  # center record FAB (square)
            IC.mic(d, cx - 30, y + 34, 60, CREAM_HI)
            center(d, "REC", y + 100, F(18), CREAM_HI, cx=cx)
            continue
        on = (t == active)
        col = COFFEE if on else (MUTED if not dark else (150,140,128))
        icon(d, cx - 24, y + 30, 48, col)
        center(d, t, y + 86, F(20, on), col, cx=cx)
        if on:
            rect(d, cx - 30, y + 8, 60, 5, COFFEE)   # active top marker

def check(d, x, y, s, c):
    """Draw a real checkmark (no font glyph)."""
    d.line([(x, y+s*0.55), (x+s*0.4, y+s*0.9), (x+s, y+s*0.15)], fill=c, width=max(3, s//8))

def save(img, name):
    out = f"/home/sanjiv/projects/Vanni/docs/screens/{name}.png"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out); print("wrote", out)
    return out

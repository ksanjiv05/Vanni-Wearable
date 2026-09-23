#!/usr/bin/env python3
"""
Generate all Google Play Store console assets for Vaani, on-brand with the design system:
  cream #F3EADB bg, coffee #835F41 primary, charcoal ink #1B1A18, sharp corners, flat/matte.

Outputs to store-assets/:
  - icon-512.png            512x512   app icon (required)
  - feature-graphic.png    1024x500   feature graphic banner (required)
  - promo-banner.png       1024x500   alt marketing banner
  - phone/1..5.png         1080x2340  framed phone screenshots (from docs/screens mockups)
  - tv-banner.png          1280x720   optional TV/large banner
All PNG, no external services.
"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCREENS = os.path.join(ROOT, "docs", "screens")
OUT = os.path.join(ROOT, "store-assets")
os.makedirs(os.path.join(OUT, "phone"), exist_ok=True)

# ---- brand palette ----
CREAM   = (243, 234, 219)   # #F3EADB background
SURFACE = (251, 246, 236)   # #FBF6EC
SUNKEN  = (236, 224, 205)   # #ECE0CD
INK     = (27, 26, 24)      # #1B1A18
SECOND  = (74, 64, 56)      # #4A4038
MUTED   = (138, 126, 112)   # #8A7E70
COFFEE  = (131, 95, 65)     # #835F41
COFFEE_HI = (161, 124, 91)  # #A17C5B
COFFEE_LO = (101, 72, 51)   # #654833
SLATE   = (99, 112, 125)    # #63707D
HAIR    = (216, 202, 180)   # #D8CAB4
SAGE    = (122, 138, 107)   # #7A8A6B
HONEY   = (201, 161, 91)    # #C9A15B
CHARCOAL= (21, 20, 18)      # dark bg #151412

F_SANS  = "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf"
F_BOLD  = "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf"
F_MONO  = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"

def font(path, size):
    return ImageFont.truetype(path, size)

def draw_v_mark(d, cx, cy, s, color, weight):
    """Draw the coffee 'V' wordmark as a clean, even filled chevron centered at (cx,cy)."""
    t = weight  # stroke thickness
    # Outer V outline points (top-left, down to bottom point, up to top-right),
    # then the inner notch back — a single filled polygon = crisp, even join.
    poly = [
        (cx - s,       cy - s),          # outer top-left
        (cx - s + t,   cy - s),          # top-left inner edge
        (cx,           cy + s - t*0.6),  # inner bottom point
        (cx + s - t,   cy - s),          # top-right inner edge
        (cx + s,       cy - s),          # outer top-right
        (cx,           cy + s),          # outer bottom point
    ]
    d.polygon(poly, fill=color)

def rounded_none_rect(d, box, fill=None, outline=None, width=1):
    """Sharp-corner rectangle (design rule: border-radius 0)."""
    d.rectangle(box, fill=fill, outline=outline, width=width)

# =========================================================================
# 1. APP ICON  512x512
# =========================================================================
def make_icon():
    S = 512
    img = Image.new("RGBA", (S, S), CREAM + (255,))
    d = ImageDraw.Draw(img)
    # subtle inner sunken frame for depth (flat, hairline only)
    inset = 44
    rounded_none_rect(d, [inset, inset, S-inset, S-inset], outline=HAIR, width=3)
    # big coffee V mark, centered
    draw_v_mark(d, S//2, S//2 - 10, 118, COFFEE, 46)
    # tiny mono 'ai' dot accent — a recording pulse dot
    d.ellipse([S//2+120, S//2-150, S//2+150, S//2-120], fill=COFFEE_HI)
    img.save(os.path.join(OUT, "icon-512.png"))
    # also a maskable 1024 hi-res for other stores
    img.resize((1024, 1024), Image.LANCZOS).save(os.path.join(OUT, "icon-1024.png"))
    print("icon-512.png, icon-1024.png")

# =========================================================================
# Waveform helper (brand hero motif)
# =========================================================================
def draw_waveform(d, x0, y0, w, h, color, bars=48, seed=7):
    import math
    bw = w / (bars * 1.6)
    gap = bw * 0.6
    for i in range(bars):
        # deterministic pseudo-random envelope, tallest in the middle
        t = i / bars
        env = math.sin(t * math.pi)  # 0..1..0
        amp = (0.25 + 0.75 * env) * (0.5 + 0.5 * abs(math.sin(i * 1.7 + seed)))
        bh = max(4, amp * h)
        bx = x0 + i * (bw + gap)
        d.rectangle([bx, y0 + (h - bh) / 2, bx + bw, y0 + (h + bh) / 2], fill=color)

# =========================================================================
# 2. FEATURE GRAPHIC  1024x500  (required marketing banner)
# =========================================================================
def make_feature_graphic():
    W, Hh = 1024, 500
    img = Image.new("RGB", (W, Hh), CREAM)
    d = ImageDraw.Draw(img)
    # left: brand block; right: motif
    # hairline framing bar top + bottom (editorial)
    d.rectangle([0, 0, W, 6], fill=COFFEE)
    d.rectangle([0, Hh-6, W, Hh], fill=COFFEE)
    # V mark badge (sharp square, coffee outline)
    bx, by, bs = 70, 150, 150
    rounded_none_rect(d, [bx, by, bx+bs, by+bs], fill=SURFACE, outline=COFFEE, width=4)
    draw_v_mark(d, bx+bs//2, by+bs//2-4, 40, COFFEE, 16)
    # wordmark
    d.text((bx+bs+40, 150), "Vaani", font=font(F_BOLD, 96), fill=INK)
    d.text((bx+bs+44, 258), "voice notes, on-device", font=font(F_SANS, 40), fill=SECOND)
    # tagline overline (mono) — measured to fit left of the right-side panel (starts x=684)
    d.text((bx+bs+46, 328), "WAKE  \u00b7  RECORD  \u00b7  SYNC", font=font(F_MONO, 26), fill=MUTED)
    # right-side waveform motif inside a sunken panel
    pw = 300
    rounded_none_rect(d, [W-pw-40, 120, W-40, Hh-120], fill=SUNKEN)
    draw_waveform(d, W-pw-10, 200, pw-60, 180, COFFEE, bars=26, seed=3)
    # small "Hi ESP" chip
    chip_w = 150
    rounded_none_rect(d, [W-pw-10, 150, W-pw-10+chip_w, 190], fill=COFFEE)
    d.text((W-pw+2, 158), "\u201cHi ESP\u201d", font=font(F_BOLD, 22), fill=CREAM)
    img.save(os.path.join(OUT, "feature-graphic.png"))
    print("feature-graphic.png")

# =========================================================================
# 3. PROMO BANNER  1024x500  (dark variant)
# =========================================================================
def make_promo_banner():
    W, Hh = 1024, 500
    img = Image.new("RGB", (W, Hh), CHARCOAL)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, 5], fill=COFFEE_HI)
    # centered brand
    draw_v_mark(d, W//2, 175, 60, COFFEE_HI, 22)
    tw = d.textlength("Vaani", font=font(F_BOLD, 84))
    d.text(((W-tw)//2, 250), "Vaani", font=font(F_BOLD, 84), fill=CREAM)
    sub = "Private voice notes. Wearable capture. Zero cloud."
    sw = d.textlength(sub, font=font(F_SANS, 34))
    d.text(((W-sw)//2, 360), sub, font=font(F_SANS, 34), fill=(200, 190, 175))
    # side waveforms in coffee
    draw_waveform(d, 60, 200, 200, 120, COFFEE_HI, bars=16, seed=1)
    draw_waveform(d, W-260, 200, 200, 120, COFFEE_HI, bars=16, seed=9)
    img.save(os.path.join(OUT, "promo-banner.png"))
    print("promo-banner.png")

# =========================================================================
# 4. TV / large banner  1280x720
# =========================================================================
def make_tv_banner():
    W, Hh = 1280, 720
    img = Image.new("RGB", (W, Hh), CREAM)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, 8], fill=COFFEE)
    d.rectangle([0, Hh-8, W, Hh], fill=COFFEE)
    draw_v_mark(d, W//2, 250, 90, COFFEE, 34)
    tw = d.textlength("Vaani", font=font(F_BOLD, 130))
    d.text(((W-tw)//2, 360), "Vaani", font=font(F_BOLD, 130), fill=INK)
    sub = "voice notes, on-device"
    sw = d.textlength(sub, font=font(F_SANS, 48))
    d.text(((W-sw)//2, 510), sub, font=font(F_SANS, 48), fill=SECOND)
    draw_waveform(d, W//2-300, 600, 600, 70, COFFEE_HI, bars=40, seed=5)
    img.save(os.path.join(OUT, "tv-banner.png"))
    print("tv-banner.png")

# =========================================================================
# 5. PHONE SCREENSHOTS  — frame each mockup with a caption band
# =========================================================================
CAPTIONS = [
    ("02-library.png",     "All your notes, on your phone", "Transcribed & searchable \u2014 no cloud"),
    ("03-note-detail.png", "Summaries, key points & to-dos", "Play the original audio anytime"),
    ("04-chat.png",        "Chat with your own notes",       "On-device RAG with real citations"),
    ("07-device.png",      "Pair your Vaani wearable",        "Auto-syncs recordings the moment you connect"),
    ("05-search.png",      "Find anything you said",          "Fuzzy search across every note"),
]
def make_phone_shots():
    W, Hh = 1080, 2340
    band = 300   # caption band height at top
    for idx, (fn, title, sub) in enumerate(CAPTIONS, 1):
        src = os.path.join(SCREENS, fn)
        if not os.path.exists(src):
            print("  (skip, missing:", fn, ")"); continue
        mock = Image.open(src).convert("RGB")
        canvas = Image.new("RGB", (W, Hh), CREAM)
        d = ImageDraw.Draw(canvas)
        # caption band (surface, coffee left accent bar, hairline divider)
        rounded_none_rect(d, [0, 0, W, band], fill=SURFACE)
        d.rectangle([0, 0, 12, band], fill=COFFEE)               # left accent
        d.rectangle([0, band-2, W, band], fill=HAIR)             # hairline
        d.text((70, 78), title, font=font(F_BOLD, 66), fill=INK)
        d.text((72, 168), sub, font=font(F_SANS, 40), fill=SECOND)
        # place the mockup scaled to fill below the band
        avail_h = Hh - band
        scale = min(W / mock.width, avail_h / mock.height)
        nw, nh = int(mock.width*scale), int(mock.height*scale)
        m = mock.resize((nw, nh), Image.LANCZOS)
        canvas.paste(m, ((W-nw)//2, band + (avail_h-nh)//2))
        canvas.save(os.path.join(OUT, "phone", f"{idx}.png"))
    print("phone/1..%d.png" % len(CAPTIONS))

if __name__ == "__main__":
    make_icon()
    make_feature_graphic()
    make_promo_banner()
    make_tv_banner()
    make_phone_shots()
    print("\nAll assets ->", OUT)

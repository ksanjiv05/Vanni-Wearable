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
# 5. PHONE SCREENSHOTS — premium marketing frames:
#    branded gradient bg + headline + description + feature bullets
#    + the real app screen embedded in a device frame (rounded, shadowed).
# =========================================================================
from PIL import ImageFilter

# (source mockup, headline, one-line description, [feature bullets])
CAPTIONS = [
    ("02-library.png",
     "Every note,\nbeautifully organized",
     "Speak your mind — Vaani transcribes and files it, grouped by day.",
     ["Automatic transcription, fully on-device",
      "Search across everything you've said",
      "No account. No cloud. No listening in."]),
    ("03-note-detail.png",
     "From voice to\nstructured note",
     "Every recording becomes a summary, key points and to-dos — automatically.",
     ["AI summary of each recording",
      "Action items extracted for you",
      "Play back the original audio anytime"]),
    ("04-chat.png",
     "Chat with\nyour own notes",
     "Ask a question and get answers drawn from everything you've captured.",
     ["Retrieval-augmented chat over your notes",
      "Every answer cites its source note",
      "Runs privately, right on your phone"]),
    ("07-device.png",
     "Pair your\nVaani wearable",
     "Hands-free capture on the go, synced the moment you connect.",
     ["Say \u201cHi ESP\u201d to record anywhere",
      "Auto-syncs new recordings on connect",
      "Encrypted BLE \u2014 your voice stays yours"]),
    ("05-search.png",
     "Find anything\nyou ever said",
     "Fuzzy search that forgives typos and understands what you meant.",
     ["Instant keyword + semantic search",
      "Matches even misspelled words",
      "Jump straight to the moment"]),
]

def _vgradient(w, h, top, bot):
    base = Image.new("RGB", (w, h), top)
    d = ImageDraw.Draw(base)
    for y in range(h):
        t = y / (h - 1)
        c = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (w, y)], fill=c)
    return base

def _device_frame(screen_img, screen_w, bezel=20, radius=54, screen_radius=40):
    """Return an RGBA image of the mockup embedded in a dark phone frame."""
    sw = screen_w
    sh = int(sw * screen_img.height / screen_img.width)
    screen = screen_img.resize((sw, sh), Image.LANCZOS).convert("RGB")
    # round the screen corners
    smask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(smask).rounded_rectangle([0, 0, sw, sh], radius=screen_radius, fill=255)
    fw, fh = sw + 2 * bezel, sh + 2 * bezel
    frame = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    fd = ImageDraw.Draw(frame)
    # body
    fd.rounded_rectangle([0, 0, fw, fh], radius=radius, fill=(24, 22, 20, 255))
    # thin coffee rim highlight
    fd.rounded_rectangle([2, 2, fw - 2, fh - 2], radius=radius - 2, outline=(131, 95, 65, 255), width=3)
    frame.paste(screen, (bezel, bezel), smask)
    # camera dot
    fd.ellipse([fw // 2 - 7, bezel // 2 - 1, fw // 2 + 7, bezel // 2 + 13], fill=(60, 55, 50, 255))
    return frame

def _wrap(d, text, font, max_w):
    words, lines, cur = text.split(), [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        if d.textlength(trial, font=font) <= max_w:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines

def make_phone_shots():
    W, Hh = 1080, 2340
    MARGIN = 84
    for idx, (fn, headline, desc, bullets) in enumerate(CAPTIONS, 1):
        src = os.path.join(SCREENS, fn)
        if not os.path.exists(src):
            print("  (skip, missing:", fn, ")"); continue
        # branded warm gradient background
        canvas = _vgradient(W, Hh, (247, 240, 228), (230, 214, 190)).convert("RGBA")
        d = ImageDraw.Draw(canvas)
        # top hairline brand bar + wordmark
        d.rectangle([0, 0, W, 8], fill=COFFEE)
        draw_v_mark(d, MARGIN + 22, 78, 24, COFFEE, 10)
        d.text((MARGIN + 58, 56), "Vaani", font=font(F_BOLD, 44), fill=INK)

        # headline (multi-line, may contain explicit \n)
        y = 168
        hf = font(F_BOLD, 78)
        for ln in headline.split("\n"):
            d.text((MARGIN, y), ln, font=hf, fill=INK)
            y += 92
        # coffee accent underline
        y += 6
        d.rectangle([MARGIN, y, MARGIN + 120, y + 8], fill=COFFEE)
        y += 46
        # description (wrapped)
        df = font(F_SANS, 38)
        for ln in _wrap(d, desc, df, W - 2 * MARGIN):
            d.text((MARGIN, y), ln, font=df, fill=SECOND)
            y += 52
        # feature bullets — sharp coffee square + text
        y += 24
        bf = font(F_SANS, 34)
        for b in bullets:
            d.rectangle([MARGIN, y + 8, MARGIN + 18, y + 26], fill=COFFEE)
            for j, ln in enumerate(_wrap(d, b, bf, W - 2 * MARGIN - 44)):
                d.text((MARGIN + 44, y + j * 46), ln, font=bf, fill=INK)
            y += 46 * max(1, len(_wrap(d, b, bf, W - 2 * MARGIN - 44))) + 22

        # device frame with the real app screen, floating with a soft shadow
        mock = Image.open(src).convert("RGB")
        screen_w = 760
        frame = _device_frame(mock, screen_w)
        fw, fh = frame.size
        fx = (W - fw) // 2
        # anchor the frame just below the text block, bleeding off the bottom edge
        fy = max(y + 40, Hh - fh + 150)
        # soft shadow
        shadow = Image.new("RGBA", (W, Hh), (0, 0, 0, 0))
        sd = ImageDraw.Draw(shadow)
        sd.rounded_rectangle([fx + 6, fy + 26, fx + fw + 6, fy + fh + 26], radius=54, fill=(60, 40, 25, 110))
        shadow = shadow.filter(ImageFilter.GaussianBlur(34))
        canvas = Image.alpha_composite(canvas, shadow)
        canvas.alpha_composite(frame, (fx, fy))

        canvas.convert("RGB").save(os.path.join(OUT, "phone", f"{idx}.png"))
    print("phone/1..%d.png (premium)" % len(CAPTIONS))

if __name__ == "__main__":
    make_icon()
    make_feature_graphic()
    make_promo_banner()
    make_tv_banner()
    make_phone_shots()
    print("\nAll assets ->", OUT)

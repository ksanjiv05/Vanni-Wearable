#!/usr/bin/env python3
"""Vaani — 'Ink' theme candidate palette board."""
from PIL import Image, ImageDraw, ImageFont

W, H = 1600, 2180
BG = (16, 18, 22)
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

def font(sz, bold=True):
    paths = [
        "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for p in paths:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            continue
    return ImageFont.load_default()

def hx(c):
    return "#%02X%02X%02X" % c

def swatch(x, y, w, h, color, label=None, sub=None, textdark=False, r=14):
    d.rounded_rectangle([x, y, x + w, y + h], radius=r, fill=color)
    tc = (20, 22, 26) if textdark else (240, 242, 246)
    if label:
        d.text((x + 16, y + h - 44), label, font=font(20), fill=tc)
    if sub:
        d.text((x + 16, y + 12), sub, font=font(16, False), fill=tc)

# ---- Four Ink candidates: (name, tagline, ramp 50..900, accent, accent2) ----
CANDIDATES = [
    ("A · Midnight Ink", "Neutral blue-black · calm, editorial",
     ["#F5F7FA","#E4E8EF","#C7CEDB","#9AA4B8","#6B7691","#48526B","#2E3548","#1C2130","#12151F","#0A0C12"],
     "#5B8DEF", "#7DD3C0"),
    ("B · Indigo Ink", "Warmer, ink-pen indigo · premium, distinctive",
     ["#F4F3FB","#E5E2F4","#C9C3E6","#A096D0","#6E62AE","#4B3F8A","#332A66","#221C47","#161230","#0C0A1C"],
     "#8B7CF6", "#F5A97F"),
    ("C · Slate Ink", "Cool graphite · restrained, pro-tool",
     ["#F6F7F8","#E7E9EC","#CBD0D6","#9AA2AD","#6A7481","#495159","#31373D","#20242A","#14171B","#0A0C0E"],
     "#4FB0C6", "#E0A458"),
    ("D · Teal-Ink", "Ink base + teal soul · fresh, modern",
     ["#F2F8F7","#DCECEA","#B7D9D4","#7FB8B0","#4E8E86","#2F6A63","#1F4A45","#15332F","#0D211E","#071312"],
     "#2DD4BF", "#F472B6"),
]

d.text((60, 40), "VAANI  ·  \u201cInk\u201d theme candidates", font=font(46), fill=(240,242,246))
d.text((60, 100), "Pick a base palette + accent. Each row: 50\u2192900 ramp, then accent / accent-2, shown on dark & light surface.",
       font=font(20, False), fill=(150,158,172))

top = 170
row_h = 470
pad = 60
ramp_w = (W - pad*2 - 40)  # full width for ramp

for i, (name, tag, ramp, acc, acc2) in enumerate(CANDIDATES):
    y = top + i * row_h
    # header
    d.text((pad, y), name, font=font(30), fill=(240,242,246))
    d.text((pad, y + 40), tag, font=font(19, False), fill=(150,158,172))
    # ramp
    ry = y + 80
    cols = len(ramp)
    cw = (ramp_w - (cols-1)*8) / cols
    for j, hexc in enumerate(ramp):
        c = tuple(int(hexc[k:k+2],16) for k in (1,3,5))
        sx = pad + j*(cw+8)
        swatch(sx, ry, cw, 110, c, label=str([50,100,200,300,400,500,600,700,800,900][j]),
               sub=hexc, textdark=(j<=3))
    # accents + surface previews
    py = ry + 130
    ac = tuple(int(acc[k:k+2],16) for k in (1,3,5))
    a2 = tuple(int(acc2[k:k+2],16) for k in (1,3,5))
    dark = tuple(int(ramp[8][k:k+2],16) for k in (1,3,5))
    light = tuple(int(ramp[0][k:k+2],16) for k in (1,3,5))
    swatch(pad, py, 240, 150, ac, label="ACCENT "+acc, textdark=True, r=16)
    swatch(pad+260, py, 240, 150, a2, label="ACCENT-2 "+acc2, textdark=True, r=16)
    # dark surface card preview
    sx = pad+540
    swatch(sx, py, 480, 150, dark, r=16)
    d.rounded_rectangle([sx+20, py+22, sx+300, py+58], radius=8, fill=ac)
    d.text((sx+34, py+30), "Transcribe", font=font(18), fill=(20,22,26))
    d.text((sx+20, py+78), "Standup with Ravi", font=font(20), fill=(235,238,244))
    d.text((sx+20, py+108), "00:14:20  \u00b7  3 speakers", font=font(15,False), fill=(150,158,172))
    # light surface card preview
    lx = pad+540+500
    swatch(lx, py, W-pad-(lx), 150, light, r=16)
    d.rounded_rectangle([lx+20, py+22, lx+300, py+58], radius=8, fill=ac)
    d.text((lx+34, py+30), "Transcribe", font=font(18), fill=(255,255,255))
    d.text((lx+20, py+78), "Standup with Ravi", font=font(20), fill=(28,30,36))
    d.text((lx+20, py+108), "00:14:20  \u00b7  3 speakers", font=font(15,False), fill=(90,98,112))

out = "/home/sanjiv/projects/Vanni/docs/ink-theme-candidates.png"
img.save(out)
print("wrote", out, img.size)

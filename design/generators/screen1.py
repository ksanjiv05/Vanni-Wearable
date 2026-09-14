#!/usr/bin/env python3
"""Vaani — Screen 1: Onboarding (welcome + setup steps). Light 'cream' theme, SHARP corners (radius 0)."""
from PIL import Image, ImageDraw, ImageFont
import sys; sys.path.insert(0, "/tmp")
import vaani_icons as IC

# phone canvas
W, H = 1080, 2340
# ---- tokens (charcoal/coffee/cream/slate, light theme) ----
CREAM_BG   = (243, 234, 219)   # #F3EADB  background
CREAM_HI   = (251, 246, 236)   # #FBF6EC  raised surface
CHARCOAL   = (27, 26, 24)      # #1B1A18  ink text
INK_SOFT   = (74, 64, 56)      # #4A4038  secondary text
MUTED      = (138, 126, 112)   # #8A7E70  muted
COFFEE     = (129, 95, 65)     # #835F41  primary
COFFEE_DK  = (101, 72, 51)     # #654833
SLATE      = (99, 112, 125)    # #63707D  secondary accent
SLATE_DK   = (78, 90, 102)     # #4E5A66
LINE       = (216, 202, 180)   # hairline on cream
SUCCESS    = (122, 138, 107)   # sage
img = Image.new("RGB", (W, H), CREAM_BG)
d = ImageDraw.Draw(img)

def F(sz, bold=True):
    for p in ([
        "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]):
        try: return ImageFont.truetype(p, sz)
        except Exception: continue
    return ImageFont.load_default()

def rect(x,y,w,h,c): d.rectangle([x,y,x+w,y+h],fill=c)          # SHARP corners only
def line(x,y,w,h,c): d.rectangle([x,y,x+w,y+h],fill=c)
def center(txt,y,fnt,c,cx=W//2):
    w=d.textlength(txt,font=fnt); d.text((cx-w/2,y),txt,font=fnt,fill=c)

PAD=64

# ---- status bar ----
d.text((PAD,54),"9:41",font=F(30),fill=CHARCOAL)
IC.wifi(d, W-PAD-150, 48, 40, CHARCOAL)
IC.battery(d, W-PAD-96, 46, 46, CHARCOAL, pct=0.82)

# ---- top: brand + step indicator ----
y=150
# wordmark
d.text((PAD,y),"VAANI",font=F(40),fill=CHARCOAL)
d.text((PAD+232,y+12),"beta",font=F(22),fill=MUTED)
# step dots (sharp squares): 3 steps to match the 3-step list, step 1 active
sx=W-PAD-3*40
for i in range(3):
    c=COFFEE if i==0 else LINE
    rect(sx+i*40,y+14,24,24,c)

# ---- hero block ----
y=330
# a sharp-cornered ink 'device' emblem, flat
ew,eh=W-PAD*2, 520
rect(PAD,y,ew,eh,CHARCOAL)
# inner cream inset (framed, sharp)
rect(PAD+40,y+40,ew-80,eh-80,CREAM_HI)
# stylized waveform (flat bars, coffee + slate)
bars=[70,140,90,200,120,260,160,300,180,240,120,180,90,150,70,120,60,100,150,220,120]
bx=PAD+90; base=y+eh//2
import math
for i,bh in enumerate(bars):
    col = COFFEE if i%3 else SLATE
    rect(bx+i*40, base-bh//2, 22, bh, col)
# little 'REC' tag sharp
rect(PAD+80,y+80,150,54,COFFEE)
d.text((PAD+104,y+92),"REC",font=F(28),fill=CREAM_HI)

# ---- headline ----
y=920
d.text((PAD,y),"Your voice,",font=F(72),fill=CHARCOAL)
d.text((PAD,y+84),"quietly organised.",font=F(72),fill=COFFEE)
y+=200
d.text((PAD,y),"Record with your Vaani device. It syncs to",font=F(30,False),fill=INK_SOFT)
d.text((PAD,y+44),"your phone, transcribes on Sarvam, and turns",font=F(30,False),fill=INK_SOFT)
d.text((PAD,y+88),"every conversation into searchable notes \u2014",font=F(30,False),fill=INK_SOFT)
d.text((PAD,y+132),"all indexed privately, on-device.",font=F(30,False),fill=INK_SOFT)

# ---- 3 setup steps list (sharp tiles) ----
y=1400
steps=[("1","Pair your device","Bluetooth \u00b7 secure numeric match",True),
       ("2","Add your Sarvam key","Billed to your own account",False),
       ("3","Set recording consent","Know the law where you record",False)]
for i,(n,t,s,active) in enumerate(steps):
    ty=y+i*150
    rect(PAD,ty,W-PAD*2,130, CREAM_HI if active else CREAM_BG)
    if not active: line(PAD,ty,W-PAD*2,2,LINE); line(PAD,ty+130,W-PAD*2,2,LINE)
    # number square
    nb=COFFEE if active else LINE
    rect(PAD+24,ty+30,70,70,nb)
    center(n,ty+42,F(38),CREAM_HI if active else MUTED,cx=PAD+24+35)
    d.text((PAD+130,ty+34),t,font=F(34),fill=CHARCOAL)
    d.text((PAD+130,ty+80),s,font=F(24,False),fill=MUTED)
    # step 3 = consent: show a square checkbox; others a chevron
    if i==2:
        stroke_col=COFFEE
        d.rectangle([W-PAD-86,ty+38,W-PAD-38,ty+86],outline=stroke_col,width=3)
    else:
        IC.chevron_right(d, W-PAD-72, ty+40, 52, COFFEE if active else MUTED)

# ---- primary CTA (sharp, coffee) ----
y=1980
rect(PAD,y,W-PAD*2,120,COFFEE)
center("Pair your device",y+36,F(38),CREAM_HI)
# secondary
y+=150
center("I'll set up later",y,F(30,False),SLATE_DK)

# ---- bottom privacy strip ----
y=2210
line(PAD,y,W-PAD*2,2,LINE)
IC.shield(d, PAD, y+18, 34, SLATE)
d.text((PAD+48,y+24),"Audio is encrypted on device \u00b7 search stays offline",font=F(23,False),fill=MUTED)

out="/home/sanjiv/projects/Vanni/docs/screens/01-onboarding.png"
import os; os.makedirs(os.path.dirname(out),exist_ok=True)
img.save(out); print("wrote",out,img.size)

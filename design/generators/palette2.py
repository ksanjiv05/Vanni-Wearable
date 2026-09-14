#!/usr/bin/env python3
"""Vaani — flat 'engineer' ink palettes (editor-theme inspired, desaturated)."""
from PIL import Image, ImageDraw, ImageFont

W, H = 1600, 2320
BG = (13, 15, 18)
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

def font(sz, bold=True):
    for p in ([
        "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]):
        try: return ImageFont.truetype(p, sz)
        except Exception: continue
    return ImageFont.load_default()

def T(hexc): return tuple(int(hexc[k:k+2],16) for k in (1,3,5))

def swatch(x,y,w,h,color,label=None,sub=None,textdark=False,r=12):
    d.rounded_rectangle([x,y,x+w,y+h],radius=r,fill=color)
    tc=(24,26,30) if textdark else (232,235,240)
    if label: d.text((x+14,y+h-40),label,font=font(19),fill=tc)
    if sub: d.text((x+14,y+11),sub,font=font(15,False),fill=tc)

# Editor-theme inspired FLAT palettes. Ramp = surface ink (dark), then 3 muted accents.
CANDIDATES = [
    ("A · Nord", "Arctic, desaturated blue-grey \u00b7 the classic engineer palette",
     ["#ECEFF4","#D8DEE9","#AEB8CC","#7B88A1","#5E6779","#4C566A","#3B4252","#2E3440","#242933","#1B1F27"],
     [("#88C0D0","frost / primary"),("#81A1C1","steel"),("#A3BE8C","success green")]),
    ("B · One Dark", "Atom One Dark \u00b7 muted, familiar, easy on eyes",
     ["#E6E6E6","#D0D3D9","#ABB2BF","#828997","#5C6370","#4B5263","#3E4451","#2C313A","#21252B","#181A1F"],
     [("#61AFEF","blue / primary"),("#98C379","green"),("#E5C07B","amber")]),
    ("C · Tokyo Night", "Deep navy ink, soft accents \u00b7 premium dark",
     ["#C0CAF5","#A9B1D6","#8A93B8","#6B7394","#565F89","#414868","#343A52","#24283B","#1A1B26","#13141C"],
     [("#7AA2F7","indigo / primary"),("#9ECE6A","green"),("#BB9AF7","muted violet")]),
    ("D · GitHub Dark", "The default dev surface \u00b7 neutral slate, blue accent",
     ["#F0F3F6","#D0D7DE","#AFB8C1","#8B949E","#6E7681","#57606A","#3D444D","#2D333B","#22272E","#1C2128"],
     [("#539BF5","blue / primary"),("#57AB5A","green"),("#E3B341","yellow")]),
    ("E · Gruvbox (dark)", "Warm retro terminal \u00b7 low-contrast, cozy",
     ["#FBF1C7","#EBDBB2","#D5C4A1","#BDAE93","#A89984","#7C6F64","#504945","#3C3836","#32302F","#282828"],
     [("#83A598","aqua-blue"),("#B8BB26","lime"),("#FE8019","orange")]),
]

d.text((60,40),"VAANI  \u00b7  flat engineer palettes",font=font(46),fill=(232,235,240))
d.text((60,100),"Editor-theme inspired \u00b7 desaturated, matte, no gloss. Ramp 50\u2192900 ink surface + 3 muted accents + dark/light card.",
       font=font(19,False),fill=(140,148,162))

top=170; row_h=420; pad=60
ramp_w=W-pad*2

for i,(name,tag,ramp,accents) in enumerate(CANDIDATES):
    y=top+i*row_h
    d.text((pad,y),name,font=font(29),fill=(232,235,240))
    d.text((pad,y+38),tag,font=font(17,False),fill=(140,148,162))
    ry=y+72; cols=len(ramp); cw=(ramp_w-(cols-1)*7)/cols
    labels=[50,100,200,300,400,500,600,700,800,900]
    for j,hexc in enumerate(ramp):
        sx=pad+j*(cw+7)
        swatch(sx,ry,cw,100,T(hexc),label=str(labels[j]),sub=hexc,textdark=(j<=3))
    py=ry+118
    # 3 accents
    aw=200
    for k,(ah,al) in enumerate(accents):
        ax=pad+k*(aw+16)
        swatch(ax,py,aw,130,T(ah),label=al,sub=ah,textdark=True,r=14)
    # dark card preview (surface = ramp[8])
    sx=pad+3*(aw+16)+10
    dark=T(ramp[8]); acc=T(accents[0][0]); light=T(ramp[0])
    cw2=(W-pad-sx-520)
    swatch(sx,py,cw2,130,dark,r=14)
    d.rounded_rectangle([sx+18,py+20,sx+150,py+52],radius=7,fill=acc)
    d.text((sx+30,py+27),"Ready",font=font(16),fill=(24,26,30))
    d.text((sx+18,py+66),"Standup with Ravi",font=font(19),fill=T(ramp[1]))
    d.text((sx+18,py+95),"00:14:20 \u00b7 3 speakers",font=font(14,False),fill=T(ramp[3]))
    # light card
    lx=sx+cw2+18
    swatch(lx,py,W-pad-lx,130,light,r=14)
    d.rounded_rectangle([lx+18,py+20,lx+150,py+52],radius=7,fill=acc)
    d.text((lx+30,py+27),"Ready",font=font(16),fill=(255,255,255))
    d.text((lx+18,py+66),"Standup with Ravi",font=font(19),fill=T(ramp[8]))
    d.text((lx+18,py+95),"00:14:20 \u00b7 3 speakers",font=font(14,False),fill=T(ramp[5]))

out="/home/sanjiv/projects/Vanni/docs/ink-flat-engineer-palettes.png"
img.save(out); print("wrote",out,img.size)

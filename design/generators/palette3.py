#!/usr/bin/env python3
"""Vaani — 'Charcoal / Coffee / Cream / Slate' theme spec + preview board."""
from PIL import Image, ImageDraw, ImageFont

W, H = 1600, 1780
img = Image.new("RGB", (W, H), (20, 19, 18))
d = ImageDraw.Draw(img)

def font(sz, bold=True):
    for p in ([
        "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]):
        try: return ImageFont.truetype(p, sz)
        except Exception: continue
    return ImageFont.load_default()

def T(h): return tuple(int(h[k:k+2],16) for k in (1,3,5))
def sw(x,y,w,h,c,label=None,sub=None,dark=False,r=12):
    d.rounded_rectangle([x,y,x+w,y+h],radius=r,fill=c)
    tc=(28,25,22) if dark else (238,233,225)
    if label: d.text((x+14,y+h-38),label,font=font(18),fill=tc)
    if sub: d.text((x+14,y+11),sub,font=font(14,False),fill=tc)

# ---- The four families, each a muted ramp ----
CHARCOAL = ["#3A3936","#302F2C","#282724","#211F1D","#1B1A18","#151412","#100F0E"]  # near-black warm
COFFEE   = ["#C9B29B","#B79A7D","#A17C5B","#835F41","#654833","#4A3626","#33251A"]  # warm brown accent
CREAM    = ["#FBF6EC","#F3EADB","#E8DAC4","#D9C7AC","#C7B091","#B29873","#96794F"]  # light text/surface
SLATE    = ["#B4BCC4","#98A2AD","#7B8794","#63707D","#4E5A66","#3C4650","#2C343C"]  # cool blue-grey accent

d.text((60,40),"VAANI  \u00b7  Charcoal \u00b7 Coffee \u00b7 Cream \u00b7 Slate",font=font(42),fill=(238,233,225))
d.text((60,96),"Warm-neutral, matte, flat. Charcoal surfaces \u00b7 coffee = primary/warm accent \u00b7 cream = text \u00b7 slate = cool secondary.",
       font=font(18,False),fill=T("#8A8378"))

# ramps
labels=[100,200,300,400,600,800,900]
rows=[("CHARCOAL  (surfaces / background)",CHARCOAL,[3,4,5,6]),
      ("COFFEE  (primary \u00b7 warm accent)",COFFEE,[0,1,2]),
      ("CREAM  (text / high surface)",CREAM,[0,1,2,3]),
      ("SLATE  (secondary \u00b7 cool accent)",SLATE,[0,1,2,3])]
top=150; rh=170; pad=60; rw=W-pad*2
for i,(nm,ramp,darkidx) in enumerate(rows):
    y=top+i*rh
    d.text((pad,y),nm,font=font(22),fill=(238,233,225))
    ry=y+36; cols=len(ramp); cw=(rw-(cols-1)*8)/cols
    for j,hx in enumerate(ramp):
        sx=pad+j*(cw+8)
        sw(sx,ry,cw,96,T(hx),label=str(labels[j]),sub=hx,dark=(j in darkidx),r=12)

# ---- role tokens ----
ty=top+4*rh+8
d.text((pad,ty),"SEMANTIC ROLES",font=font(22),fill=(238,233,225))
roles=[("background","#151412",False),("surface","#1B1A18",False),("surface-hi","#211F1D",False),
       ("primary (coffee)","#A17C5B",True),("on-primary","#1B1A18",True),
       ("secondary (slate)","#7B8794",True),("text","#F3EADB",True),
       ("text-muted","#96897B",True),("success","#8A9A7B",True),("warning","#C9A15B",True),("danger","#B5705E",True)]
ry=ty+38; cw=(rw-(len(roles)-1)*8)/len(roles)
for j,(nm,hx,dk) in enumerate(roles):
    sx=pad+j*(cw+8)
    d.rounded_rectangle([sx,ry,sx+cw,ry+120],radius=10,fill=T(hx))
    tc=(28,25,22) if dk else (238,233,225)
    d.text((sx+8,ry+120-52),nm,font=font(12),fill=tc)
    d.text((sx+8,ry+8),hx,font=font(11,False),fill=tc)

# ---- two full card previews (dark + light) ----
py=ry+150
d.text((pad,py),"IN CONTEXT",font=font(22),fill=(238,233,225))
py+=40
# DARK card
cardw=(rw-40)/2
def note_card(x,y,w,h,bg,text,muted,chip_bg,chip_tx,title_c):
    d.rounded_rectangle([x,y,x+w,y+h],radius=18,fill=bg)
    # chip
    d.rounded_rectangle([x+24,y+24,x+150,y+60],radius=9,fill=chip_bg)
    d.text((x+38,y+31),"Ready",font=font(16),fill=chip_tx)
    d.text((x+w-150,y+30),"2.4 min ago",font=font(14,False),fill=muted)
    d.text((x+24,y+80),"Standup with Ravi & Priya",font=font(24),fill=title_c)
    d.text((x+24,y+118),"Decided to push Atlas to next sprint; Ravi",font=font(16,False),fill=text)
    d.text((x+24,y+142),"owns the migration doc by Friday.",font=font(16,False),fill=text)
    # meta row
    d.text((x+24,y+185),"00:14:20  \u00b7  3 speakers  \u00b7  #project-atlas",font=font(14,False),fill=muted)
    # todo pill + citation pill
    d.rounded_rectangle([x+24,y+220,x+220,y+256],radius=9,fill=chip_bg)
    d.text((x+38,y+227),"2 to-dos",font=font(15),fill=chip_tx)
note_card(pad,py,cardw,290,T("#1B1A18"),T("#C9BEB0"),T("#8A7E70"),T("#A17C5B"),T("#1B1A18"),T("#F3EADB"))
note_card(pad+cardw+40,py,cardw,290,T("#F3EADB"),T("#4A4038"),T("#8A7E70"),T("#835F41"),T("#FBF6EC"),T("#211F1D"))

out="/home/sanjiv/projects/Vanni/docs/theme-charcoal-coffee.png"
img.save(out); print("wrote",out,img.size)

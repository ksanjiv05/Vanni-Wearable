#!/usr/bin/env python3
import sys; sys.path.insert(0, "/tmp")
from PIL import Image, ImageDraw, ImageFont
import vaani_icons as I

CREAM=(243,234,219); CHAR=(27,26,24); COFFEE=(129,95,65)
icons=[("library",I.library),("search",I.search),("mic",I.mic),("check_square",I.check_square),
 ("sliders",I.sliders),("play",I.play),("chevron_right",I.chevron_right),("chevron_left",I.chevron_left),
 ("arrow_up",I.arrow_up),("arrow_down",I.arrow_down),("more_h",I.more_h),("close",I.close),
 ("bluetooth",I.bluetooth),("key",I.key),("shield",I.shield),("battery",I.battery),
 ("wifi",I.wifi),("clock",I.clock),("trash",I.trash),("download",I.download),
 ("refresh",I.refresh),("zap",I.zap),("waveform",I.waveform)]
cols=6; cell=180; rows=(len(icons)+cols-1)//cols
W=cols*cell; H=rows*cell+80
img=Image.new("RGB",(W,H),CREAM); d=ImageDraw.Draw(img)
f=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",16)
d.text((20,20),"Vaani icons (line, sharp)",font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",28),fill=CHAR)
for i,(nm,fn) in enumerate(icons):
    cx=(i%cols)*cell; cy=(i//cols)*cell+80
    s=84; x=cx+(cell-s)//2; y=cy+20
    fn(d,x,y,s,COFFEE)
    w=d.textlength(nm,font=f); d.text((cx+cell/2-w/2, cy+120),nm,font=f,fill=CHAR)
img.save("/home/sanjiv/projects/Vanni/docs/icon-sheet.png"); print("wrote icon-sheet.png",img.size)

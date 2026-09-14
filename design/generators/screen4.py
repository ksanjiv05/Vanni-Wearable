#!/usr/bin/env python3
"""Vaani Screen 4: Chat — RAG answers with citation chips. REFINED per UX review."""
import sys; sys.path.insert(0, "/tmp")
from vaani_ui import *
import vaani_icons as IC

img, d = canvas()
statusbar(d)

y = 130
text(d, PAD, y, "Ask your notes", F(50), CHARCOAL)
y += 70
text(d, PAD, y, "Answers cite the exact moment \u00b7 tap to hear it", F(24, False), MUTED)
hline(d, PAD, y+56, W-PAD*2, LINE, 2)

# ---- user bubble (right, coffee, sharp) ----
y += 104
q = "What did I commit to in Tuesday's standup?"
qw = int(d.textlength(q, font=F(27, False))) + 56
rect(d, W-PAD-qw, y, qw, 88, COFFEE)
text(d, W-PAD-qw+28, y+28, q, F(27, False), CREAM_HI)

# ---- assistant answer card (left, coffee accent bar for consistency) ----
y += 128
CARD_H = 430
rect(d, PAD, y, W-PAD*2, CARD_H, CREAM_HI); rect(d, PAD, y, 8, CARD_H, COFFEE)
text(d, PAD+40, y+28, "FROM YOUR NOTES", F(20), MUTED)
ans=["You committed to two things in Tuesday's",
     "standup with Ravi and Priya:",
     "",
     "1.  Share the Atlas migration doc by Friday [C1]",
     "2.  Spike a fix for the API rate-limit risk [C2]"]
yy=y+68
for ln in ans:
    text(d, PAD+40, yy, ln, F(27, False), INK_SOFT); yy += 46
# SOURCES row — chips CONSTRAINED to content column, inside the card
yy += 18
text(d, PAD+40, yy, "SOURCES", F(20), MUTED); yy+=40
col_w = W-PAD*2  # 952
chip_w = (col_w - 40*2 - 24) // 2   # two chips fit inside card with 24 gap
for i,(tag,c,label,tm) in enumerate([("C1",COFFEE,"Standup \u00b7 Ravi","07:48"),
                                     ("C2",SLATE,"Standup \u00b7 Priya","11:03")]):
    bx = PAD+40 + i*(chip_w+24)
    rect(d, bx, yy, chip_w, 96, CREAM_LO)
    rect(d, bx, yy, 8, 96, c)                      # color left-bar
    rect(d, bx+22, yy+22, 52, 52, c)               # [C1] badge
    center(d, tag, yy+30, MONO(24), CREAM_HI, cx=bx+48)
    text(d, bx+92, yy+18, label, F(22), CHARCOAL)
    IC.play(d, bx+92, yy+52, 28, COFFEE)
    text(d, bx+126, yy+52, tm, MONO(22), COFFEE)

# ---- follow-up chips (DISTINCT anatomy: surface + hairline stroke, no bar/time) ----
y += CARD_H + 40
text(d, PAD, y, "FOLLOW-UP", F(20), MUTED); y+=44
xx=PAD
for s in ["Who owns the doc?","What did Priya flag?"]:
    w=int(d.textlength(s,font=F(23,False)))+48
    if xx+w > W-PAD: xx=PAD; y+=72
    rect(d, xx, y, w, 64, CREAM_HI); stroke(d, xx, y, w, 64, SLATE, 2)
    text(d, xx+24, y+16, s, F(23, False), SLATE_DK)
    xx += w+16

# ---- input bar (sticky) with real send icon ----
py = H - 150
rect(d, 0, py, W, 150, CREAM_HI); hline(d, 0, py, W, LINE, 2)
rect(d, PAD, py+37, W-PAD*2-92, 76, CREAM_BG); stroke(d, PAD, py+37, W-PAD*2-92, 76, LINE, 2)
text(d, PAD+28, py+57, "Ask anything about your notes\u2026", F(25, False), MUTED)
rect(d, W-PAD-76, py+37, 76, 76, COFFEE); IC.arrow_up(d, W-PAD-76+18, py+37+18, 40, CREAM_HI)
save(img, "04-chat")

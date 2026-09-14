#!/usr/bin/env python3
"""Vaani Screen 3: Note detail — summary, key points, to-dos, transcript w/ speaker labels + tap-to-seek, audio player."""
import sys; sys.path.insert(0, "/tmp")
from vaani_ui import *
import vaani_icons as IC

img, d = canvas()
statusbar(d)

# ---- app bar ----
y = 116
IC.chevron_left(d, PAD, y, 52, CHARCOAL)               # back
IC.more_h(d, W-PAD-48, y+6, 48, CHARCOAL)              # overflow (kebab dots)
# title
y += 90
text(d, PAD, y, "Standup with Ravi", F(48), CHARCOAL)
y += 66
text(d, PAD, y, "Today 09:32  \u00b7  00:14:20  \u00b7  3 speakers", F(24, False), MUTED)
# tags
y += 54
for i,(t,c) in enumerate([("#atlas",COFFEE),("#standup",SLATE)]):
    w=int(d.textlength(t,font=F(22)))+40
    xx=PAD+ (i* (w+16)) if i==0 else PAD+ (int(d.textlength("#atlas",font=F(22)))+40+16)
    rect(d, xx, y, w, 44, CREAM_HI); stroke(d, xx, y, w, 44, c, 2)
    text(d, xx+20, y+8, t, F(22), c)

# ---- SUMMARY card ----
y += 84
rect(d, PAD, y, W-PAD*2, 250, CREAM_HI); rect(d, PAD, y, 8, 250, COFFEE)
text(d, PAD+40, y+22, "SUMMARY", F(22), MUTED)
for i,ln in enumerate([
    "The team agreed to move project Atlas to the next",
    "sprint. Ravi will own the migration doc and share it",
    "by Friday. Priya flagged the API rate-limit risk and",
    "will spike a fix. Budget signoff still pending."]):
    text(d, PAD+40, y+66+i*42, ln, F(26, False), INK_SOFT)

# ---- KEY POINTS ----
y += 288
text(d, PAD, y, "KEY POINTS", F(24), MUTED)
y += 46
for pt,tm in [("Atlas moved to next sprint","04:12"),
              ("Ravi owns migration doc","07:48"),
              ("API rate-limit risk raised","11:03")]:
    rect(d, PAD, y, W-PAD*2, 78, CREAM_HI); hline(d, PAD, y+78, W-PAD*2, LINE, 2)
    rect(d, PAD+28, y+30, 18, 18, SLATE)
    text(d, PAD+66, y+22, pt, F(27, False), CHARCOAL)
    rtext(d, W-PAD-24, y+24, tm, MONO(24), COFFEE)     # tap-to-seek timestamp (mono)
    y += 92

# ---- TO-DOS ----
y += 8
text(d, PAD, y, "TO-DOS", F(24), MUTED)
y += 46
for done,tx,who in [(True,"Send migration doc","Ravi \u00b7 by Fri"),
                    (False,"Spike rate-limit fix","Priya"),]:
    rect(d, PAD, y, W-PAD*2, 84, CREAM_HI); hline(d, PAD, y+84, W-PAD*2, LINE, 2)
    # checkbox (sharp, 48x48 hit area, 40 visual)
    if done: rect(d, PAD+24, y+18, 48, 48, COFFEE); check(d, PAD+34, y+30, 28, CREAM_HI)
    else: stroke(d, PAD+24, y+18, 48, 48, MUTED, 3)
    text(d, PAD+92, y+18, tx, F(28, not done), CHARCOAL if not done else MUTED)
    text(d, PAD+92, y+52, who, F(21, False), MUTED)
    y += 98

# ---- TRANSCRIPT (speaker labels + seek) ----
y += 8
text(d, PAD, y, "TRANSCRIPT", F(24), MUTED)
rtext(d, W-PAD, y, "Hinglish \u00b7 codemix", F(22, False), MUTED)
y += 48
lines=[("S1","04:08","So Atlas ka migration next sprint mein le lete hain.",COFFEE),
       ("S2","04:20","Theek hai, main doc bana ke Friday tak share karta hoon.",SLATE),
       ("S3","04:36","Rate limit ka ek risk hai, I'll spike a fix.",COFFEE_DK)]
for sp,tm,tx,c in lines:
    rect(d, PAD, y, 64, 64, c); center(d, sp, y+18, F(26), CREAM_HI, cx=PAD+32)
    rtext(d, W-PAD, y+6, tm, MONO(22), MUTED)
    text(d, PAD+84, y+4, tx[:38], F(24, False), CHARCOAL)
    if len(tx)>38: text(d, PAD+84, y+36, tx[38:], F(24, False), CHARCOAL)
    y += 92

# ---- sticky audio player (charcoal, sharp) bottom ----
py = H - 160
rect(d, 0, py, W, 160, CHARCOAL)
rect(d, PAD, py+46, 68, 68, COFFEE); IC.play(d, PAD+16, py+62, 36, CREAM_HI)
# scrubber
hline(d, PAD+100, py+76, W-PAD*2-100, (70,64,58), 6)
hline(d, PAD+100, py+76, int((W-PAD*2-100)*0.3), COFFEE, 6)
rect(d, PAD+100+int((W-PAD*2-100)*0.3)-6, py+66, 12, 26, COFFEE)
text(d, PAD+100, py+100, "04:20", MONO(22), (176,164,150))
rtext(d, W-PAD, py+100, "14:20", MONO(22), (176,164,150))
save(img, "03-note-detail")

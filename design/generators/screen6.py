#!/usr/bin/env python3
"""Vaani Screen 6: Tasks — all to-dos across notes, grouped by due date, swipe complete, back-link."""
import sys; sys.path.insert(0, "/tmp")
from vaani_ui import *

img, d = canvas()
statusbar(d)

y = 130
text(d, PAD, y, "Tasks", F(58), CHARCOAL)
rtext(d, W-PAD, y+18, "6 open", F(28, False), MUTED)
y += 100
# segmented control (sharp): All / Open / Done
seg=["Open","All","Done"]; sw=(W-PAD*2)//3
for i,s in enumerate(seg):
    on=(i==0)
    rect(d, PAD+i*sw, y, sw, 68, COFFEE if on else CREAM_HI)
    if not on: stroke(d, PAD+i*sw, y, sw, 68, LINE, 2)
    center(d, s, y+18, F(26, on), CREAM_HI if on else INK_SOFT, cx=PAD+i*sw+sw//2)

def todo(d, y, done, tx, note, tm, who, prio):
    rect(d, PAD, y, W-PAD*2, 150, CREAM_HI); hline(d, PAD, y+150, W-PAD*2, LINE, 2)
    pc={"HIGH":DANGER,"MED":WARNING,"LOW":SLATE}[prio]
    rect(d, PAD, y, 8, 150, pc)  # priority accent bar
    if done: rect(d, PAD+34, y+30, 38, 38, COFFEE); check(d, PAD+42, y+38, 22, CREAM_HI)
    else: stroke(d, PAD+34, y+30, 38, 38, MUTED, 3)
    text(d, PAD+96, y+22, tx, F(30, not done), MUTED if done else CHARCOAL)
    # back-link to source note
    text(d, PAD+96, y+68, "from  "+note, F(22, False), SLATE_DK)
    rtext(d, W-PAD-28, y+70, "\u25b6 "+tm, MONO(20), COFFEE)
    # meta
    text(d, PAD+96, y+104, who, F(21, False), MUTED)
    # priority tag
    w=int(d.textlength(prio,font=F(18)))+28
    rect(d, W-PAD-28-w, y+100, w, 36, pc); text(d, W-PAD-28-w+14, y+105, prio, F(18), CREAM_HI)

y += 108
text(d, PAD, y, "OVERDUE", F(22), DANGER); hline(d, PAD+160, y+14, W-PAD*2-160, LINE, 2)
y += 52
todo(d, y, False, "Send migration doc", "Standup with Ravi", "07:48", "Assigned to you \u00b7 due Fri", "HIGH")
y += 168
text(d, PAD, y, "TODAY", F(22), MUTED); hline(d, PAD+130, y+14, W-PAD*2-130, LINE, 2)
y += 52
todo(d, y, False, "Spike rate-limit fix", "Standup with Ravi", "11:03", "Priya", "MED")
y += 168
todo(d, y, False, "Confirm budget with finance", "Vendor call", "--:--", "Assigned to you", "MED")
y += 168
text(d, PAD, y, "DONE TODAY", F(22), SUCCESS); hline(d, PAD+200, y+14, W-PAD*2-200, LINE, 2)
y += 52
todo(d, y, True, "Approve sharp-corner UI", "Design review", "08:14", "Completed 2h ago", "LOW")

bottomnav(d, "Tasks")
save(img, "06-tasks")

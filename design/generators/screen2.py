#!/usr/bin/env python3
"""Vaani Screen 2: Library / Home — notes grouped by day with pipeline status chips."""
import sys; sys.path.insert(0, "/tmp")
from vaani_ui import *
import vaani_icons as IC

img, d = canvas()
statusbar(d)

# ---- header ----
y = 130
text(d, PAD, y, "Library", F(58), CHARCOAL)
# sync status pill (sharp)
rect(d, W-PAD-260, y+6, 260, 60, CREAM_HI); stroke(d, W-PAD-260, y+6, 260, 60, LINE, 2)
rect(d, W-PAD-260+18, y+24, 22, 22, SUCCESS)
text(d, W-PAD-260+52, y+18, "Synced 2m", F(24, False), INK_SOFT)
y += 96
text(d, PAD, y, "38 notes  \u00b7  4.2 h recorded", F(26, False), MUTED)

# ---- pending sync banner (charcoal, sharp) ----
y += 72
rect(d, PAD, y, W-PAD*2, 118, CHARCOAL)
rect(d, PAD+24, y+30, 58, 58, COFFEE); IC.arrow_down(d, PAD+24+13, y+30+13, 32, CREAM_HI)
text(d, PAD+108, y+22, "Syncing from device\u2026", F(30), CREAM_HI)
text(d, PAD+108, y+64, "3 recordings  \u00b7  47 MB  \u00b7  over Wi-Fi", F(23, False), (176,164,150))
rtext(d, W-PAD-24, y+42, "34%", F(34), COFFEE_LT)
# progress track
hline(d, PAD, y+114, W-PAD*2, CREAM_LO, 4)
hline(d, PAD, y+114, int((W-PAD*2)*0.34), COFFEE, 4)

# ---- day group: TODAY ----
def status_chip(d, x, y, label, kind):
    cols = {"ready": (SUCCESS, CREAM_HI), "proc": (COFFEE, CREAM_HI),
            "sync": (SLATE, CREAM_HI), "queued": (CREAM_LO, MUTED)}
    bg, fg = cols[kind]
    w = int(d.textlength(label, font=F(22))) + 44
    rect(d, x, y, w, 46, bg)
    text(d, x+22, y+9, label, F(22), fg)
    return w

def note_row(d, y, title, snippet, meta, chip, kind, todos=0):
    rect(d, PAD, y, W-PAD*2, 226, CREAM_HI)
    hline(d, PAD, y+226, W-PAD*2, LINE, 2)
    # left accent bar (coffee) - sharp
    rect(d, PAD, y, 8, 226, COFFEE if kind!="queued" else CREAM_LO)
    text(d, PAD+40, y+24, title, F(34), CHARCOAL)
    rtext(d, W-PAD-28, y+30, meta.split("  ")[0], F(22, False), MUTED)
    text(d, PAD+40, y+74, snippet, F(25, False), INK_SOFT)
    text(d, PAD+40, y+112, meta, F(22, False), MUTED)
    cx = PAD+40
    w = status_chip(d, cx, y+156, chip, kind); cx += w + 16
    if todos:
        w2 = int(d.textlength(f"{todos} to-dos", font=F(22))) + 44
        rect(d, cx, y+156, w2, 46, CREAM_LO); text(d, cx+22, y+165, f"{todos} to-dos", F(22), SLATE_DK)

y += 168
text(d, PAD, y, "TODAY", F(24), MUTED); hline(d, PAD+140, y+14, W-PAD*2-140, LINE, 2)
y += 54
note_row(d, y, "Standup with Ravi & Priya",
         "Pushed Atlas to next sprint; Ravi owns migration\u2026",
         "00:14:20  \u00b7  3 speakers  \u00b7  #atlas", "Ready", "ready", todos=2)
y += 244
note_row(d, y, "Call with vendor",
         "Transcribing 8 min of audio\u2026",
         "00:08:03  \u00b7  processing", "Transcribing 62%", "proc")
y += 244
text(d, PAD, y, "YESTERDAY", F(24), MUTED); hline(d, PAD+190, y+14, W-PAD*2-190, LINE, 2)
y += 54
note_row(d, y, "Design review",
         "Sharp-corner system approved; cream stays default\u2026",
         "00:22:41  \u00b7  2 speakers  \u00b7  #ui", "Ready", "ready", todos=4)

bottomnav(d, "Library")
save(img, "02-library")

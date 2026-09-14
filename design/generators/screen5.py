#!/usr/bin/env python3
"""Vaani Screen 5: Search — hybrid semantic+keyword, filter chips, offline badge."""
import sys; sys.path.insert(0, "/tmp")
from vaani_ui import *
import vaani_icons as IC

img, d = canvas()
statusbar(d)

y = 130
# search field (sharp) with real magnifier
rect(d, PAD, y, W-PAD*2, 96, CREAM_HI); stroke(d, PAD, y, W-PAD*2, 96, CHARCOAL, 3)
IC.search(d, PAD+24, y+28, 40, MUTED)
text(d, PAD+84, y+28, "budget decision", F(30), CHARCOAL)
IC.close(d, W-PAD-52, y+28, 40, MUTED)
y += 128
# offline-capable badge with wifi-off style (draw a wifi + slash)
bw = 340
rect(d, PAD, y, bw, 56, CREAM_HI); stroke(d, PAD, y, bw, 56, SLATE, 2)
IC.wifi(d, PAD+16, y+8, 38, SLATE)
d.line([(PAD+18, y+46),(PAD+50, y+14)], fill=SLATE, width=4)  # slash = offline
text(d, PAD+68, y+14, "Works offline", F(23, False), INK_SOFT)
rtext(d, W-PAD, y+14, "142 results \u00b7 38 ms", F(23, False), MUTED)

# ---- filter chips ----
y += 84
text(d, PAD, y, "FILTERS", F(20), MUTED); y+=40
chips=[("Last week",True),("#atlas",True),("Ravi",False),("Has to-do",False),("Summaries",False)]
xx=PAD
for lbl,on in chips:
    w=int(d.textlength(lbl,font=F(23,False)))+48
    if xx+w > W-PAD: xx=PAD; y+=72
    if on: rect(d, xx, y, w, 58, COFFEE); text(d, xx+24, y+13, lbl, F(23), CREAM_HI)
    else: rect(d, xx, y, w, 58, CREAM_HI); stroke(d, xx, y, w, 58, LINE, 2); text(d, xx+24, y+13, lbl, F(23, False), INK_SOFT)
    xx += w+16

# ---- results ----
y += 96
text(d, PAD, y, "TOP MATCHES", F(20), MUTED); y+=46
def res(d, y, kind, kc, title, snip, tm, score):
    rect(d, PAD, y, W-PAD*2, 168, CREAM_HI); hline(d, PAD, y+168, W-PAD*2, LINE, 2)
    # kind tag (sharp)
    w=int(d.textlength(kind,font=F(19)))+32
    rect(d, PAD+28, y+26, w, 40, kc); text(d, PAD+28+16, y+32, kind, F(19), CREAM_HI)
    rtext(d, W-PAD-28, y+30, score, MONO(21), COFFEE)
    text(d, PAD+28, y+80, title, F(28), CHARCOAL)
    text(d, PAD+28, y+118, snip, F(23, False), INK_SOFT)
    rtext(d, W-PAD-28, y+118, tm, MONO(20), MUTED)
res(d, y, "SUMMARY", SLATE, "Design review",
    "\u2026budget signoff still pending, will revisit\u2026", "08:14", "0.91")
y+=186
res(d, y, "TRANSCRIPT", COFFEE, "Standup with Ravi",
    "\u2026the marketing budget decision we deferred\u2026", "11:20", "0.88")
y+=186
res(d, y, "KEY POINT", SLATE_DK, "Q3 planning",
    "Budget cap raised to \u20b92L for pilot\u2026", "03:02", "0.83")
y+=186
res(d, y, "TO-DO", SUCCESS, "Vendor call",
    "Confirm final budget with finance by Mon", "\u2014", "0.79")

bottomnav(d, "Search")
save(img, "05-search")

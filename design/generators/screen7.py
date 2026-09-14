#!/usr/bin/env python3
"""Vaani Screen 7: Device — battery, storage, backlog, firmware/OTA, identify, unpair."""
import sys; sys.path.insert(0, "/tmp")
from vaani_ui import *
import vaani_icons as IC

img, d = canvas()
statusbar(d)

# ---- app bar with back arrow (Device is pushed from Library/Settings) ----
IC.chevron_left(d, PAD, 116, 52, CHARCOAL)
y = 200
text(d, PAD, y, "Device", F(58), CHARCOAL)
y += 108
# ---- device status card (charcoal hero) ----
rect(d, PAD, y, W-PAD*2, 360, CHARCOAL)
# device emblem (sharp)
rect(d, PAD+40, y+50, 150, 150, CHAR_HI); stroke(d, PAD+40, y+50, 150, 150, COFFEE, 3)
rect(d, PAD+95, y+80, 40, 90, COFFEE)  # mic body
rect(d, PAD+40+55, y+50+120, 40, 10, COFFEE)
text(d, PAD+220, y+44, "Vaani One", F(40), CREAM_HI)
# connected pill
rect(d, PAD+220, y+104, 200, 50, CHAR_HI); stroke(d, PAD+220, y+104, 200, 50, SUCCESS, 2)
rect(d, PAD+238, y+118, 20, 20, SUCCESS); text(d, PAD+270, y+114, "Connected", F(23, False), (200,192,182))
text(d, PAD+220, y+176, "Last sync 2 min ago \u00b7 BLE", F(23, False), (176,164,150))
# battery + storage stats row
sy=y+250
for i,(lbl,val,pct,c) in enumerate([("BATTERY","82%",0.82,SUCCESS),("STORAGE","6.1 / 32 GB",0.19,COFFEE)]):
    bx=PAD+40+i*((W-PAD*2-80)//2+0)
    bx=PAD+40+i*430
    text(d, bx, sy, lbl, F(20), (150,140,128))
    text(d, bx, sy+30, val, F(34), CREAM_HI)
    hline(d, bx, sy+86, 360, (70,64,58), 6)
    hline(d, bx, sy+86, int(360*pct), c, 6)

# ---- pending backlog ----
y += 400
rect(d, PAD, y, W-PAD*2, 130, CREAM_HI); rect(d, PAD, y, 8, 130, COFFEE)
text(d, PAD+40, y+24, "Pending on device", F(30), CHARCOAL)
text(d, PAD+40, y+70, "3 recordings \u00b7 47 MB \u00b7 ready to sync", F(23, False), MUTED)
rect(d, W-PAD-200, y+38, 176, 60, COFFEE); center(d, "Sync now", y+52, F(25), CREAM_HI, cx=W-PAD-200+88)

# ---- settings rows ----
y += 168
def row(d, y, title, sub, right, rc=MUTED):
    rect(d, PAD, y, W-PAD*2, 118, CREAM_HI); hline(d, PAD, y+118, W-PAD*2, LINE, 2)
    text(d, PAD+32, y+24, title, F(30), CHARCOAL)
    text(d, PAD+32, y+68, sub, F(22, False), MUTED)
    rtext(d, W-PAD-32, y+42, right, F(26, False), rc)
row(d, y, "Firmware", "v1.0.3 \u00b7 up to date", "Check", SLATE_DK); y+=134
row(d, y, "Identify device", "Blink the LED to find it", "Blink", COFFEE); y+=134
row(d, y, "Recording indicator", "LED always on while recording", "On", SUCCESS); y+=134
row(d, y, "Time sync", "Drift corrected on every connect", "\u00b112 ms", MUTED); y+=134
# unpair (danger, sharp)
rect(d, PAD, y, W-PAD*2, 110, CREAM_HI); stroke(d, PAD, y, W-PAD*2, 110, DANGER, 2)
IC.trash(d, PAD+W-PAD*2-260, y+30, 48, DANGER)
center(d, "Unpair device", y+38, F(30), DANGER)

save(img, "07-device")

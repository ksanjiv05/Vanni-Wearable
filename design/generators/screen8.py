#!/usr/bin/env python3
"""Vaani Screen 8: Settings — API key, quality, budget cap + usage, language, local-only, export/delete."""
import sys; sys.path.insert(0, "/tmp")
from vaani_ui import *
import vaani_icons as IC

img, d = canvas()
statusbar(d)

y = 130
text(d, PAD, y, "Settings", F(58), CHARCOAL)
y += 116

# ---- API key card ----
rect(d, PAD, y, W-PAD*2, 150, CREAM_HI); rect(d, PAD, y, 8, 150, SUCCESS)
text(d, PAD+40, y+24, "Sarvam API key", F(30), CHARCOAL)
text(d, PAD+40, y+68, "sk_live \u00b7\u00b7\u00b7\u00b7 4c9a  \u00b7  validated", MONO(23), MUTED)
rect(d, PAD+40, y+104, 22, 22, SUCCESS); text(d, PAD+72, y+100, "Billed to your account", F(21, False), INK_SOFT)
rtext(d, W-PAD-76, y+58, "Manage", F(25, False), SLATE_DK); IC.chevron_right(d, W-PAD-64, y+52, 44, SLATE_DK)

# ---- budget cap + usage ----
y += 186
rect(d, PAD, y, W-PAD*2, 250, CREAM_HI)
text(d, PAD+40, y+24, "MONTHLY BUDGET", F(20), MUTED)
text(d, PAD+40, y+56, "\u20b91,204", F(46), CHARCOAL)
rtext(d, W-PAD-40, y+64, "of \u20b91,800 cap", F(24, False), MUTED)
# usage bar (sharp), 67% + honey warning threshold tick at ~85%
hline(d, PAD+40, y+140, W-PAD*2-80, CREAM_LO, 12)
hline(d, PAD+40, y+140, int((W-PAD*2-80)*0.67), COFFEE, 12)
tx = PAD+40+int((W-PAD*2-80)*0.85)
rect(d, tx-2, y+130, 5, 32, WARNING)   # warning threshold marker
text(d, PAD+40, y+168, "67% used \u00b7 ASR is 86% of spend", F(22, False), INK_SOFT)
rect(d, PAD+40, y+204, 200, 0, CREAM_HI)
rect(d, W-PAD-220, y+196, 196, 56, CREAM_BG); stroke(d, W-PAD-220, y+196, 196, 56, LINE, 2)
center(d, "Adjust cap", y+210, F(23), INK_SOFT, cx=W-PAD-220+98)

# ---- toggle/list rows ----
def row(d, y, title, sub, right=None, toggle=None, rc=SLATE_DK):
    rect(d, PAD, y, W-PAD*2, 118, CREAM_HI); hline(d, PAD, y+118, W-PAD*2, LINE, 2)
    text(d, PAD+32, y+24, title, F(29), CHARCOAL)
    text(d, PAD+32, y+66, sub, F(21, False), MUTED)
    if toggle is not None:
        tw,th=96,52; tx=W-PAD-32-tw; ty=y+33
        rect(d, tx, ty, tw, th, COFFEE if toggle else CREAM_LO)
        rect(d, tx+(tw-46 if toggle else 4), ty+4, 42, th-8, CREAM_HI)  # sharp knob
    elif right:
        # strip trailing chevron marker and draw a real chevron icon
        rr = right.replace(" \u203a", "").replace("\u203a", "").strip()
        if rr:
            rtext(d, W-PAD-72, y+40, rr, F(25, False), rc)
        IC.chevron_right(d, W-PAD-60, y+34, 44, rc)

y += 286
text(d, PAD, y, "PROCESSING", F(20), MUTED); y+=42
row(d, y, "Transcription quality", "Best (batch + diarization)", right="Best \u203a"); y+=134
row(d, y, "Default mode", "Codemix \u00b7 Hinglish", right="Codemix \u203a"); y+=134
row(d, y, "Battery & cost saver", "Skip silence with on-device VAD", toggle=True); y+=134
row(d, y, "Local-only mode", "Never send audio; queue for later", toggle=False); y+=134

y += 16
text(d, PAD, y, "DATA", F(20), MUTED); y+=42
row(d, y, "Export everything", "Notes + audio as JSON + files", right="\u203a"); y+=134
# delete all (danger)
rect(d, PAD, y, W-PAD*2, 110, CREAM_HI); stroke(d, PAD, y, W-PAD*2, 110, DANGER, 2)
IC.trash(d, PAD+32, y+34, 44, DANGER)
text(d, PAD+92, y+22, "Delete everything", F(29), DANGER)
text(d, PAD+92, y+64, "Wipes notes, audio, vectors & key", F(21, False), MUTED)

bottomnav(d, "Settings")
save(img, "08-settings")

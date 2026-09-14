"""Vaani icon set — Lucide/Feather-style line icons drawn with PIL primitives.
Stroke-based, flat, sharp joins. Each fn: icon(d, x, y, s, c, w) draws in an s x s box
at top-left (x,y) with stroke color c and width w."""
import math

def _w(s): return max(3, int(s * 0.09))

def _L(d, pts, c, w):
    d.line(pts, fill=c, width=w, joint="curve")

def _rect(d, x, y, w0, h0, c, w):
    d.line([(x,y),(x+w0,y),(x+w0,y+h0),(x,y+h0),(x,y)], fill=c, width=w, joint="curve")

# ---------- nav / core ----------
def library(d, x, y, s, c, w=None):
    w = w or _w(s)
    # stacked books / list rows
    for i in range(3):
        yy = y + s*0.18 + i*s*0.28
        _L(d, [(x+s*0.14, yy), (x+s*0.86, yy)], c, w)
    d.ellipse([x+s*0.05,y+s*0.12,x+s*0.12,y+s*0.19], fill=c)

def search(d, x, y, s, c, w=None):
    w = w or _w(s)
    r = s*0.30
    cx, cy = x+s*0.40, y+s*0.40
    d.ellipse([cx-r, cy-r, cx+r, cy+r], outline=c, width=w)
    _L(d, [(cx+r*0.72, cy+r*0.72), (x+s*0.86, y+s*0.86)], c, w)

def mic(d, x, y, s, c, w=None):
    w = w or _w(s)
    # capsule body (sharp: use rect since no radius) + stand
    bw = s*0.26
    _rect(d, x+s*0.5-bw/2, y+s*0.12, bw, s*0.42, c, w)
    # arc stand
    d.arc([x+s*0.28, y+s*0.30, x+s*0.72, y+s*0.72], 20, 160, fill=c, width=w)
    _L(d, [(x+s*0.5, y+s*0.72), (x+s*0.5, y+s*0.9)], c, w)
    _L(d, [(x+s*0.34, y+s*0.9), (x+s*0.66, y+s*0.9)], c, w)

def check_square(d, x, y, s, c, w=None):
    w = w or _w(s)
    _rect(d, x+s*0.12, y+s*0.12, s*0.76, s*0.76, c, w)
    _L(d, [(x+s*0.30, y+s*0.52), (x+s*0.45, y+s*0.67), (x+s*0.72, y+s*0.33)], c, w)

def sliders(d, x, y, s, c, w=None):
    w = w or _w(s)
    for i,(yy,kx) in enumerate([(0.28,0.66),(0.5,0.36),(0.72,0.6)]):
        _L(d, [(x+s*0.12, y+s*yy), (x+s*0.88, y+s*yy)], c, w)
        d.rectangle([x+s*kx-w, y+s*yy-s*0.08, x+s*kx+w, y+s*yy+s*0.08], fill=c)

# ---------- controls ----------
def play(d, x, y, s, c, w=None):
    d.polygon([(x+s*0.30,y+s*0.22),(x+s*0.30,y+s*0.78),(x+s*0.78,y+s*0.5)], fill=c)

def chevron_right(d, x, y, s, c, w=None):
    w = w or _w(s)
    _L(d, [(x+s*0.40, y+s*0.24), (x+s*0.64, y+s*0.5), (x+s*0.40, y+s*0.76)], c, w)

def chevron_left(d, x, y, s, c, w=None):
    w = w or _w(s)
    _L(d, [(x+s*0.60, y+s*0.24), (x+s*0.36, y+s*0.5), (x+s*0.60, y+s*0.76)], c, w)

def arrow_up(d, x, y, s, c, w=None):
    w = w or _w(s)
    _L(d, [(x+s*0.5, y+s*0.82), (x+s*0.5, y+s*0.18)], c, w)
    _L(d, [(x+s*0.26, y+s*0.42), (x+s*0.5, y+s*0.18), (x+s*0.74, y+s*0.42)], c, w)

def arrow_down(d, x, y, s, c, w=None):
    w = w or _w(s)
    _L(d, [(x+s*0.5, y+s*0.18), (x+s*0.5, y+s*0.82)], c, w)
    _L(d, [(x+s*0.26, y+s*0.58), (x+s*0.5, y+s*0.82), (x+s*0.74, y+s*0.58)], c, w)

def more_h(d, x, y, s, c, w=None):
    r = s*0.06
    for fx in (0.24, 0.5, 0.76):
        d.ellipse([x+s*fx-r, y+s*0.5-r, x+s*fx+r, y+s*0.5+r], fill=c)

def close(d, x, y, s, c, w=None):
    w = w or _w(s)
    _L(d, [(x+s*0.26,y+s*0.26),(x+s*0.74,y+s*0.74)], c, w)
    _L(d, [(x+s*0.74,y+s*0.26),(x+s*0.26,y+s*0.74)], c, w)

# ---------- domain ----------
def bluetooth(d, x, y, s, c, w=None):
    w = w or _w(s)
    cx = x+s*0.5
    _L(d, [(cx, y+s*0.12),(cx, y+s*0.88),(x+s*0.72, y+s*0.68),(x+s*0.30, y+s*0.34)], c, w)
    _L(d, [(cx, y+s*0.12),(x+s*0.72, y+s*0.34),(x+s*0.30, y+s*0.68)], c, w)

def key(d, x, y, s, c, w=None):
    w = w or _w(s)
    r=s*0.18
    d.ellipse([x+s*0.12, y+s*0.30, x+s*0.12+2*r, y+s*0.30+2*r], outline=c, width=w)
    _L(d, [(x+s*0.12+2*r-s*0.02, y+s*0.30+r),(x+s*0.86, y+s*0.30+r)], c, w)
    _L(d, [(x+s*0.72, y+s*0.30+r),(x+s*0.72, y+s*0.30+r+s*0.14)], c, w)
    _L(d, [(x+s*0.84, y+s*0.30+r),(x+s*0.84, y+s*0.30+r+s*0.10)], c, w)

def shield(d, x, y, s, c, w=None):
    w = w or _w(s)
    _L(d, [(x+s*0.5,y+s*0.12),(x+s*0.82,y+s*0.26),(x+s*0.82,y+s*0.54),
           (x+s*0.5,y+s*0.88),(x+s*0.18,y+s*0.54),(x+s*0.18,y+s*0.26),(x+s*0.5,y+s*0.12)], c, w)

def battery(d, x, y, s, c, pct=0.8, w=None):
    w = w or _w(s)
    _rect(d, x+s*0.10, y+s*0.34, s*0.70, s*0.32, c, w)
    d.rectangle([x+s*0.80, y+s*0.42, x+s*0.86, y+s*0.58], fill=c)
    d.rectangle([x+s*0.14, y+s*0.38, x+s*0.14+s*0.62*pct, y+s*0.62], fill=c)

def wifi(d, x, y, s, c, w=None):
    w = w or _w(s)
    for i,r in enumerate([0.42,0.28,0.14]):
        d.arc([x+s*0.5-s*r, y+s*0.30-s*r+s*0.2, x+s*0.5+s*r, y+s*0.30+s*r+s*0.2], 210, 330, fill=c, width=w)
    d.ellipse([x+s*0.46, y+s*0.72, x+s*0.54, y+s*0.80], fill=c)

def clock(d, x, y, s, c, w=None):
    w = w or _w(s)
    d.ellipse([x+s*0.14,y+s*0.14,x+s*0.86,y+s*0.86], outline=c, width=w)
    _L(d, [(x+s*0.5,y+s*0.5),(x+s*0.5,y+s*0.28)], c, w)
    _L(d, [(x+s*0.5,y+s*0.5),(x+s*0.66,y+s*0.58)], c, w)

def trash(d, x, y, s, c, w=None):
    w = w or _w(s)
    _L(d, [(x+s*0.16,y+s*0.26),(x+s*0.84,y+s*0.26)], c, w)
    _L(d, [(x+s*0.38,y+s*0.26),(x+s*0.40,y+s*0.16),(x+s*0.60,y+s*0.16),(x+s*0.62,y+s*0.26)], c, w)
    _rect(d, x+s*0.24, y+s*0.26, s*0.52, s*0.60, c, w)
    for fx in (0.40,0.5,0.60):
        _L(d, [(x+s*fx,y+s*0.36),(x+s*fx,y+s*0.76)], c, w)

def download(d, x, y, s, c, w=None):
    w = w or _w(s)
    _L(d, [(x+s*0.5,y+s*0.14),(x+s*0.5,y+s*0.62)], c, w)
    _L(d, [(x+s*0.30,y+s*0.44),(x+s*0.5,y+s*0.64),(x+s*0.70,y+s*0.44)], c, w)
    _L(d, [(x+s*0.18,y+s*0.80),(x+s*0.82,y+s*0.80)], c, w)

def refresh(d, x, y, s, c, w=None):
    w = w or _w(s)
    d.arc([x+s*0.18,y+s*0.18,x+s*0.82,y+s*0.82], 40, 300, fill=c, width=w)
    _L(d, [(x+s*0.78,y+s*0.16),(x+s*0.82,y+s*0.34),(x+s*0.64,y+s*0.30)], c, w)

def zap(d, x, y, s, c, w=None):
    d.polygon([(x+s*0.55,y+s*0.12),(x+s*0.30,y+s*0.54),(x+s*0.48,y+s*0.54),
               (x+s*0.42,y+s*0.88),(x+s*0.70,y+s*0.44),(x+s*0.50,y+s*0.44)], fill=c)

def waveform(d, x, y, s, c, w=None):
    w = w or _w(s)
    bars=[0.3,0.6,0.4,0.85,0.5,0.7,0.35]
    n=len(bars); step=s*0.76/n
    for i,b in enumerate(bars):
        bx=x+s*0.12+i*step+step*0.2
        _L(d, [(bx, y+s*0.5-s*b*0.4),(bx, y+s*0.5+s*b*0.4)], c, w)

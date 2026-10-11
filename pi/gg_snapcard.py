"""Info card added under a saved snapshot, so the photo itself is never covered.

The card uses the dashboard's monitor colours: warm dark casing, cream text,
teal accent, and one colour per event and per class. Pillow is used for clean
type (it ships with ultralytics); without it a plain OpenCV card is drawn.
"""

from datetime import datetime

import cv2
import numpy as np

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    Image = None

BG = (33, 38, 44)
BG2 = (25, 29, 34)
INK = (220, 230, 237)
MUTED = (150, 167, 179)
WELL = (20, 24, 28)
TEAL = (132, 140, 18)

EVENTS = {
    "alert": ("LIMIT REACHED", (32, 45, 217)),
    "suppressed": ("SUPPRESSED", (115, 107, 94)),
    "test": ("TEST ALERT", (23, 163, 232)),
    "capture": ("MANUAL CAPTURE", (132, 140, 18)),
}
CLASSES = (
    ("bottle", "Bottle", (212, 123, 15)),
    ("bag", "Bag", (201, 43, 163)),
    ("food_container", "Container", (74, 158, 30)),
)
OK, WARN, BAD = (68, 158, 47), (23, 163, 232), (32, 45, 217)

FONT_DIRS = ("/usr/share/fonts/truetype/dejavu/", "/usr/share/fonts/dejavu/")


def _font(size, bold=True):
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    for d in FONT_DIRS:
        try:
            return ImageFont.truetype(d + name, size)
        except OSError:
            continue
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def level_color(items, limit):
    if limit <= 0:
        return OK
    r = items / limit
    return BAD if r >= 0.8 else WARN if r >= 0.5 else OK


def _when(ts):
    try:
        t = datetime.fromisoformat(str(ts))
        return t.strftime("%d %b %Y"), t.strftime("%H:%M:%S")
    except ValueError:
        s = str(ts or "")
        return s[:10], s[11:19]


def card_height(width):
    return max(112, int(round(width * 0.2)))


def add_card(frame, tag, state, peak=None):
    h, w = frame.shape[:2]
    ch = card_height(w)
    out = np.empty((h + ch, w, 3), dtype=np.uint8)
    out[:h] = frame
    out[h:] = BG
    if Image is None:
        _plain(out, h, w, ch, tag, state)
        return out
    rgb = Image.fromarray(cv2.cvtColor(out, cv2.COLOR_BGR2RGB))
    _draw(ImageDraw.Draw(rgb), h, w, ch, tag, state, peak)
    return cv2.cvtColor(np.asarray(rgb), cv2.COLOR_RGB2BGR)


def _rgb(c):
    return (c[2], c[1], c[0])


def _draw(d, top, w, ch, tag, state, peak):
    s = w / 640.0
    px = lambda v: max(1, int(round(v * s)))
    label, ev = EVENTS.get(tag, (tag.upper(), TEAL))
    counts = state.get("counts") or {}
    items = int(state.get("smoothed") or 0)
    limit = int(state.get("threshold") or 0)
    day, clock = _when(state.get("timestamp"))

    d.rectangle([0, top, w, top + px(5)], fill=_rgb(ev))
    y0 = top + px(5)
    d.rectangle([0, y0, w, top + ch], fill=_rgb(BG))
    pad = px(14)

    f_pill, f_big, f_mid, f_sm = _font(px(12)), _font(px(26)), _font(px(15)), _font(px(11), False)
    pill_w = d.textlength(label, font=f_pill) + px(22)
    py = y0 + px(10)
    d.rounded_rectangle([pad, py, pad + pill_w, py + px(22)], radius=px(11), fill=_rgb(ev),
                        outline=(0, 0, 0), width=px(2))
    d.text((pad + px(11), py + px(11)), label, font=f_pill, fill=(255, 255, 255), anchor="lm")
    d.text((pad, py + px(30)), clock, font=f_big, fill=_rgb(INK))
    d.text((pad, py + px(62)), f"{day}  ·  Garbage-Guard  ·  CAM 01", font=f_sm, fill=_rgb(MUTED))

    mx = int(w * 0.40)
    d.text((mx, py), "ITEMS IN AREA", font=f_sm, fill=_rgb(MUTED))
    d.text((mx, py + px(14)), str(items), font=f_big, fill=_rgb(INK))
    tw = d.textlength(str(items), font=f_big)
    d.text((mx + tw + px(6), py + px(26)), f"/ {limit} limit", font=f_sm, fill=_rgb(MUTED))
    by = py + px(50)
    bw = int(w * 0.24)
    d.rounded_rectangle([mx, by, mx + bw, by + px(10)], radius=px(5), fill=_rgb(WELL))
    frac = min(1.0, items / limit) if limit > 0 else 0.0
    if frac > 0:
        d.rounded_rectangle([mx, by, mx + max(px(10), int(bw * frac)), by + px(10)], radius=px(5),
                            fill=_rgb(level_color(items, limit)))
    extra = []
    if peak:
        extra.append(f"peak {peak * 100:.0f}%")
    if state.get("latency_ms"):
        extra.append(f"{float(state['latency_ms']):.0f} ms")
    if extra:
        d.text((mx, by + px(16)), "  ·  ".join(extra), font=f_sm, fill=_rgb(MUTED))

    rx = int(w * 0.70)
    ry = py
    row = px(23)
    for key, name, col in CLASSES:
        d.rounded_rectangle([rx, ry + px(3), rx + px(14), ry + px(17)], radius=px(4), fill=_rgb(col),
                            outline=(0, 0, 0), width=1)
        d.text((rx + px(22), ry + px(10)), name, font=f_mid, fill=_rgb(INK), anchor="lm")
        d.text((w - pad, ry + px(10)), str(int(counts.get(key, 0))), font=f_mid,
               fill=_rgb(INK), anchor="rm")
        ry += row


def _plain(out, top, w, ch, tag, state):
    label, ev = EVENTS.get(tag, (tag.upper(), TEAL))
    cv2.rectangle(out, (0, top), (w, top + 5), ev, -1)
    c = state.get("counts") or {}
    font = cv2.FONT_HERSHEY_SIMPLEX
    cv2.putText(out, label, (12, top + 30), font, 0.6, ev, 2, cv2.LINE_AA)
    cv2.putText(out, str(state.get("timestamp", "")), (12, top + 58), font, 0.55, INK, 1, cv2.LINE_AA)
    cv2.putText(out, f"Items {state.get('smoothed', 0)} / {state.get('threshold', 0)}",
                (12, top + 86), font, 0.55, INK, 1, cv2.LINE_AA)
    x = int(w * 0.55)
    for i, (key, name, col) in enumerate(CLASSES):
        y = top + 30 + i * 26
        cv2.rectangle(out, (x, y - 12), (x + 12, y), col, -1)
        cv2.putText(out, f"{name} {int(c.get(key, 0))}", (x + 20, y), font, 0.55, INK, 1, cv2.LINE_AA)

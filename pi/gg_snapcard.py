"""Info card added under a saved snapshot, so the photo itself is never covered.

The card is one slim row, about 7.5% of the width, so the photo keeps almost
all of the height. It uses the dashboard's monitor colours: warm dark casing,
cream text, teal accent, and one colour per event and per class. Pillow is used for clean
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
    return max(40, int(round(width * 0.075)))


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
    s = ch / 48.0
    px = lambda v: max(1, int(round(v * s)))
    label, ev = EVENTS.get(tag, (tag.upper(), TEAL))
    counts = state.get("counts") or {}
    items = int(state.get("smoothed") or 0)
    limit = int(state.get("threshold") or 0)
    day, clock = _when(state.get("timestamp"))

    stripe = px(3)
    d.rectangle([0, top, w, top + stripe], fill=_rgb(ev))
    cy = top + stripe + (ch - stripe) // 2
    right = w - px(10)
    gap = px(14)

    f_pill, f_time, f_num = _font(px(11)), _font(px(16)), _font(px(15))
    f_sm, f_name = _font(px(10), False), _font(px(11), False)

    def divider(x):
        d.line([x, cy - px(14), x, cy + px(14)], fill=_rgb(WELL), width=px(2))

    x = px(10)
    pw = int(d.textlength(label, font=f_pill)) + px(18)
    d.rounded_rectangle([x, cy - px(11), x + pw, cy + px(11)], radius=px(11), fill=_rgb(ev),
                        outline=(0, 0, 0), width=px(2))
    d.text((x + pw // 2, cy), label, font=f_pill, fill=(255, 255, 255), anchor="mm")
    x += pw + gap

    bw = int(max(d.textlength(clock, font=f_time), d.textlength(day, font=f_sm)))
    if x + bw <= right:
        d.text((x, cy - px(6)), clock, font=f_time, fill=_rgb(INK), anchor="lm")
        d.text((x, cy + px(12)), day, font=f_sm, fill=_rgb(MUTED), anchor="lm")
        x += bw + gap
        divider(x - gap // 2)

    num, tail = str(items), f" / {limit}"
    iw = max(int(d.textlength(num, font=f_num) + d.textlength(tail, font=f_name)), px(64))
    if x + iw <= right:
        d.text((x, cy - px(6)), num, font=f_num, fill=_rgb(INK), anchor="lm")
        d.text((x + d.textlength(num, font=f_num), cy - px(5)), tail, font=f_name,
               fill=_rgb(MUTED), anchor="lm")
        by = cy + px(9)
        d.rounded_rectangle([x, by, x + iw, by + px(5)], radius=px(3), fill=_rgb(WELL))
        frac = min(1.0, items / limit) if limit > 0 else 0.0
        if frac > 0:
            d.rounded_rectangle([x, by, x + max(px(5), int(iw * frac)), by + px(5)], radius=px(3),
                                fill=_rgb(level_color(items, limit)))
        x += iw + gap
        divider(x - gap // 2)

    for key, name, col in CLASSES:
        n = str(int(counts.get(key, 0)))
        cw = px(10) + px(5) + int(d.textlength(name, font=f_name)) + px(5) + int(d.textlength(n, font=f_num))
        if x + cw > right:
            break
        d.rounded_rectangle([x, cy - px(5), x + px(10), cy + px(5)], radius=px(3), fill=_rgb(col),
                            outline=(0, 0, 0), width=1)
        tx = x + px(15)
        d.text((tx, cy), name, font=f_name, fill=_rgb(INK), anchor="lm")
        tx += d.textlength(name, font=f_name) + px(5)
        d.text((tx, cy), n, font=f_num, fill=_rgb(INK), anchor="lm")
        x += cw + px(12)

    if peak:
        pk = f"peak {peak * 100:.0f}%"
        if x + d.textlength(pk, font=f_sm) <= right:
            d.text((right, cy), pk, font=f_sm, fill=_rgb(MUTED), anchor="rm")


def _plain(out, top, w, ch, tag, state):
    label, ev = EVENTS.get(tag, (tag.upper(), TEAL))
    c = state.get("counts") or {}
    font = cv2.FONT_HERSHEY_SIMPLEX
    sc = ch / 48.0
    y = top + int(ch * 0.62)
    cv2.rectangle(out, (0, top), (w, top + max(1, int(3 * sc))), ev, -1)
    cv2.putText(out, label, (int(10 * sc), y), font, 0.45 * sc, ev, 1, cv2.LINE_AA)
    cv2.putText(out, f"{state.get('timestamp', '')}  {state.get('smoothed', 0)}/{state.get('threshold', 0)}",
                (int(140 * sc), y), font, 0.42 * sc, INK, 1, cv2.LINE_AA)
    cv2.putText(out, f"B {int(c.get('bottle', 0))}  Bg {int(c.get('bag', 0))}  C {int(c.get('food_container', 0))}",
                (int(w * 0.72), y), font, 0.42 * sc, INK, 1, cv2.LINE_AA)

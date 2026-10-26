"""Which camera stream to read, and which part of each frame the model sees.

The sub-stream (640x360) makes small or far items only a few pixels wide. The
main stream is read instead, and the model gets only the box around the ROI,
at `detect_size` pixels, so items in the water keep their detail. The picture
for the dashboard is scaled down to DISPLAY_W so the stream to the screen and
phones stays light. If the main stream gives no picture, the sub-stream is used.
"""

import re

import cv2

DEFAULTS = {"stream": "main", "detect_size": 960, "crop": True}
SIZES = (640, 960, 1280)
DISPLAY_W = 960
CROP_PAD = 0.04
MIN_CROP = 320
MAIN_TRIES = 2

_CHANNEL = re.compile(r"(/Streaming/Channels/\d*?)(\d)0(\d)\b", re.IGNORECASE)


def stream_urls(url):
    """Return (main, sub) RTSP addresses for a Hikvision channel URL.

    Channel 101 is the main stream and 102 the sub-stream. A URL that is not a
    Hikvision channel (a file, a webcam, another camera) is used as is for both.
    """
    url = str(url)
    m = _CHANNEL.search(url)
    if not m:
        return url, url
    main = url[:m.start()] + f"{m.group(1)}{m.group(2)}01" + url[m.end():]
    sub = url[:m.start()] + f"{m.group(1)}{m.group(2)}02" + url[m.end():]
    return main, sub


def clean_settings(s):
    s["stream"] = "sub" if s.get("stream") == "sub" else "main"
    try:
        size = int(s.get("detect_size", DEFAULTS["detect_size"]))
    except (TypeError, ValueError):
        size = DEFAULTS["detect_size"]
    s["detect_size"] = min(SIZES, key=lambda v: abs(v - size))
    s["crop"] = bool(s.get("crop", True))
    return s


def crop_box(polygon, width, height, pad=CROP_PAD, min_side=MIN_CROP):
    """Pixel box (x1, y1, x2, y2) around the ROI, padded and kept inside the frame.

    None means use the whole frame: no ROI, or the box would cover nearly all of it.
    """
    if not polygon or len(polygon) < 3:
        return None
    xs = [p[0] for p in polygon]
    ys = [p[1] for p in polygon]
    px, py = pad * width, pad * height
    x1, x2 = min(xs) - px, max(xs) + px
    y1, y2 = min(ys) - py, max(ys) + py
    x1, x2 = _grow(x1, x2, min(min_side, width), width)
    y1, y2 = _grow(y1, y2, min(min_side, height), height)
    box = (int(x1), int(y1), int(round(x2)), int(round(y2)))
    if (box[2] - box[0]) * (box[3] - box[1]) >= 0.95 * width * height:
        return None
    return box


def _grow(a, b, need, limit):
    a, b = max(0.0, a), min(float(limit), b)
    short = need - (b - a)
    if short > 0:
        a -= short / 2
        b += short / 2
        if a < 0:
            b, a = b - a, 0.0
        if b > limit:
            a, b = max(0.0, a - (b - limit)), float(limit)
    return a, b


def shift(box, dx, dy):
    x1, y1, x2, y2 = box
    return (x1 + dx, y1 + dy, x2 + dx, y2 + dy)


def scale_box(box, f):
    return tuple(v * f for v in box)


def for_display(frame, width=DISPLAY_W):
    """The frame scaled to the dashboard width, and the factor used."""
    h, w = frame.shape[:2]
    if w <= width:
        return frame.copy(), 1.0
    f = width / w
    return cv2.resize(frame, (width, int(round(h * f))), interpolation=cv2.INTER_AREA), f


class StreamChoice:
    """Keeps track of which stream is in use, and falls back to the sub-stream."""

    def __init__(self, source, wanted="main"):
        self.main, self.sub = stream_urls(source)
        self.split = self.main != self.sub
        self.wanted = wanted
        self.failed = 0
        self.note = ""

    @property
    def active(self):
        if not self.split or self.wanted == "sub" or self.failed >= MAIN_TRIES:
            return "sub"
        return "main"

    @property
    def url(self):
        return self.main if self.active == "main" else self.sub

    def want(self, wanted):
        self.wanted = "sub" if wanted == "sub" else "main"
        self.failed = 0
        self.note = ""

    def main_gave_nothing(self):
        if self.active != "main":
            return False
        self.failed += 1
        if self.failed >= MAIN_TRIES:
            self.note = "Main stream gave no picture, using the sub-stream"
            return True
        return False

    def fields(self):
        return {"stream": self.active, "stream_wanted": self.wanted,
                "stream_note": self.note}

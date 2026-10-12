"""Estimate of how much of Firebase's free monthly allowance has been used.

Firebase counts downloads exactly, but only the console can see that number:
reading it needs a Google Cloud service account, which is not something to
keep on the Pi. So the Pi counts what it sends instead, multiplied by how many
phones were watching at the time, since each watching phone downloads it, plus
what the Pi itself downloads (answers, headers and TLS handshakes). The
result is an estimate, roughly within a third of the real figure, and it is
labelled as such everywhere it is shown.

What it is for is the decision, not the number: slow the preview down at 90%
and stop it at 100%, so a phone left open on the live screen cannot use up the
month. The counters reset on the 1st; Firebase's own cycle may differ by a day
or two.
"""

import json
import os
import time
from datetime import datetime, timezone
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
STORE = BASE_DIR / "gg_usage.json"

GB = 1024 ** 3
MONTH_LIMIT = 10 * GB
STORE_LIMIT = 1 * GB
WARN, HIGH = 0.70, 0.90
DAY_PREVIEW_MB = 300

KINDS = ("state", "preview", "snaps", "alerts", "pi", "other")


def _month(ts):
    return datetime.fromtimestamp(ts, timezone.utc).strftime("%Y-%m")


def _day(ts):
    return datetime.fromtimestamp(ts, timezone.utc).strftime("%Y-%m-%d")


def _blank(ts):
    return {"month": _month(ts), "day": _day(ts), "down": 0, "up": 0,
            "day_preview": 0, "by_kind": {k: 0 for k in KINDS}, "since": ts}


class Usage:
    def __init__(self, path=STORE, clock=time.time, limit=MONTH_LIMIT, day_preview_mb=DAY_PREVIEW_MB):
        self.path = Path(path)
        self.clock = clock
        self.limit = limit
        self.day_preview = day_preview_mb * 1024 * 1024
        self.data = self._load()
        self._dirty = False
        self._saved = 0.0

    def _load(self):
        try:
            d = json.loads(self.path.read_text())
        except (OSError, ValueError):
            return _blank(self.clock())
        base = _blank(self.clock())
        base.update({k: d.get(k, base[k]) for k in base})
        base["by_kind"] = {k: int(d.get("by_kind", {}).get(k, 0)) for k in KINDS}
        return self._roll(base)

    def _roll(self, d):
        now = self.clock()
        if d["month"] != _month(now):
            d = _blank(now)
            self._dirty = True
        elif d["day"] != _day(now):
            d["day"], d["day_preview"] = _day(now), 0
            self._dirty = True
        return d

    def add(self, kind, nbytes, watchers=0):
        """Count one write. `watchers` is how many phones will download it."""
        if nbytes <= 0:
            return
        self.data = self._roll(self.data)
        d = self.data
        d["up"] += int(nbytes)
        pulls = max(0, int(watchers))
        if kind in ("alerts", "snaps"):
            pulls = max(pulls, 1)
        down = int(nbytes) * pulls
        d["down"] += down
        d["by_kind"][kind if kind in KINDS else "other"] += down
        if kind == "preview":
            d["day_preview"] += down
        self._dirty = True

    def add_down(self, kind, nbytes):
        """Count bytes the Pi itself downloads (answers, headers, TLS handshakes)."""
        if nbytes <= 0:
            return
        self.data = self._roll(self.data)
        self.data["down"] += int(nbytes)
        self.data["by_kind"][kind if kind in KINDS else "other"] += int(nbytes)
        self._dirty = True

    # ---------- what the rest of the Pi asks ----------
    def fraction(self):
        self.data = self._roll(self.data)
        return min(9.99, self.data["down"] / self.limit) if self.limit else 0.0

    def level(self):
        f = self.fraction()
        return "bad" if f >= 1.0 else "near" if f >= HIGH else "warn" if f >= WARN else "ok"

    def preview_every(self, normal_s):
        """Seconds between preview frames, stretched or stopped as the month fills."""
        self.fraction()
        if self.data["day_preview"] >= self.day_preview or self.fraction() >= 1.0:
            return None
        if self.fraction() >= HIGH:
            return max(normal_s, 10)
        return normal_s

    def why_slow(self):
        self.fraction()
        if self.data["day_preview"] >= self.day_preview:
            return "Preview paused: this day's picture budget is used up. It resets at midnight UTC."
        f = self.fraction()
        if f >= 1.0:
            return "Preview off: the free Firebase allowance for this month is used up. State and alerts still sync."
        if f >= HIGH:
            return "Preview slowed to save the last of this month's free Firebase allowance."
        return None

    def report(self):
        self.fraction()
        d = self.data
        return {"month": d["month"], "down_bytes": d["down"], "up_bytes": d["up"],
                "limit_bytes": self.limit, "fraction": round(self.fraction(), 4),
                "level": self.level(), "by_kind": dict(d["by_kind"]),
                "day": d["day"], "day_preview_bytes": d["day_preview"],
                "day_preview_limit": self.day_preview, "note": self.why_slow(),
                "estimate": True}

    # ---------- disk ----------
    def save(self, every=30.0, force=False):
        now = self.clock()
        if not self._dirty or (not force and now - self._saved < every):
            return False
        tmp = self.path.with_suffix(".tmp")
        try:
            tmp.write_text(json.dumps(self.data))
            os.replace(tmp, self.path)
        except OSError:
            return False
        self._dirty, self._saved = False, now
        return True

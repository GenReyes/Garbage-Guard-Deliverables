"""Tells the dashboard whether the Pi's clock can be trusted.

The Pi has no battery-backed clock. After a power cut with no internet it
starts with the time it last saved, which can be hours or days wrong, so the
dashboard shows a warning instead of a confident wrong time.
"""

import subprocess
import time

ZONE = "Asia/Manila"
CACHE_S = 30
SETTLE_S = 150


def _uptime():
    try:
        with open("/proc/uptime") as f:
            return float(f.read().split()[0])
    except (OSError, ValueError, IndexError):
        return None


def _timedatectl():
    out = subprocess.run(["timedatectl", "show", "-p", "NTPSynchronized", "--value"],
                         capture_output=True, text=True, timeout=3)
    return out.stdout.strip().lower() == "yes"


class Clock:
    def __init__(self, probe=_timedatectl, now=time.time, uptime=_uptime):
        self.probe, self.now, self.uptime = probe, now, uptime
        self._at, self._ok = None, True

    def synced(self):
        t = self.now()
        if self._at is None or t - self._at >= CACHE_S:
            self._at = t
            try:
                self._ok = bool(self.probe())
            except Exception:
                self._ok = True   # no way to tell, so do not nag
        return self._ok

    def settling(self):
        up = self.uptime()
        return (not self.synced()) and up is not None and up < SETTLE_S

    def fields(self):
        return {"clock_synced": self.synced(), "clock_settling": self.settling(), "clock_zone": ZONE}

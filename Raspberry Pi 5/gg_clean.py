"""Clean copies of snapshots: the camera frame alone, with no boxes, area outline
or info card, saved next to the annotated picture so it can be used as training
data.

A clean copy has the same file name as its annotated picture and lives in the
clean/ folder inside the snapshots folder, so a record never needs a second
database column and the folder can be copied off as it is.

  clean_mode "off"     no clean copies
             "manual"  only for pictures taken with Capture
             "all"     also for detection alerts, muted alerts and tests
"""
import os
import time
from pathlib import Path

DEFAULTS = {"clean_mode": "all"}
MODES = ("off", "manual", "all")
FOLDER = "clean"
CACHE_S = 10


def wanted(mode, tag):
    if mode == "all":
        return True
    return mode == "manual" and tag == "capture"


def path_for(snapshot_path):
    p = Path(snapshot_path)
    return p.parent / FOLDER / p.name


def name_if_present(snapshot_dir, name):
    if not name:
        return None
    return name if (Path(snapshot_dir) / FOLDER / name).is_file() else None


def remove(snapshot_path):
    try:
        target = path_for(snapshot_path)
        if target.is_file():
            target.unlink()
            return True
    except OSError:
        pass
    return False


class Clean:
    def __init__(self, settings, snapshot_dir, save=None, mono=time.monotonic):
        self.settings = settings
        for k, v in DEFAULTS.items():
            settings.setdefault(k, v)
        self.dir = Path(snapshot_dir) / FOLDER
        self._save = save or (lambda: None)
        self.mono = mono
        self._stats = None
        self._at = None

    def configure(self, mode=None):
        if mode in MODES:
            self.settings["clean_mode"] = mode
            self._save()
        return self.fields()

    def wanted(self, tag):
        return wanted(self.settings.get("clean_mode"), tag)

    def forget(self):
        self._at = None

    def stats(self):
        now = self.mono()
        if self._at is not None and now - self._at < CACHE_S:
            return self._stats
        count = size = 0
        try:
            with os.scandir(self.dir) as it:
                for e in it:
                    if e.is_file() and e.name.lower().endswith(".jpg"):
                        count += 1
                        size += e.stat().st_size
        except OSError:
            pass
        self._stats, self._at = (count, size), now
        return self._stats

    def fields(self):
        count, size = self.stats()
        return {"clean_mode": self.settings["clean_mode"], "clean_count": count,
                "clean_bytes": size}

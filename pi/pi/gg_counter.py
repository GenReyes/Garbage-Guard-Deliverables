import time
from collections import Counter, deque
from dataclasses import dataclass
from statistics import median


def scale_polygon(points, width, height):
    if not points or len(points) < 3:
        return None
    return [(x * width, y * height) for x, y in points]


def point_in_polygon(x, y, polygon):
    if polygon is None:
        return True
    inside = False
    j = len(polygon) - 1
    for i in range(len(polygon)):
        xi, yi = polygon[i]
        xj, yj = polygon[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def tally(detections, polygon):
    counts = Counter()
    flags = []
    peak = 0.0
    for d in detections:
        x1, y1, x2, y2 = d["box"]
        inside = point_in_polygon((x1 + x2) / 2.0, (y1 + y2) / 2.0, polygon)
        flags.append(inside)
        if inside:
            counts[d["name"]] += 1
            peak = max(peak, d["conf"])
    return flags, counts, sum(counts.values()), peak


def rearm_level(threshold):
    return max(0, threshold - max(1, threshold // 4))


@dataclass
class Step:
    smoothed: int
    warming: bool
    active: bool = False
    alert: bool = False
    cleared: bool = False
    suppressed: dict = None


class AccumulationCounter:
    def __init__(self, threshold=20, window=15, cooldown=60.0, min_spike=3):
        self.window = window
        self.cooldown = cooldown
        self.min_spike = min_spike
        self.history = deque(maxlen=window)
        self.active = False
        self.last_alert = float("-inf")
        self._run = 0
        self._run_peak = 0
        self._run_passed = False
        self.set_threshold(threshold)

    def set_threshold(self, threshold):
        self.on = max(1, int(threshold))
        self.off = rearm_level(self.on)

    def update(self, raw, now=None):
        now = time.time() if now is None else now
        self.history.append(int(raw))
        warming = len(self.history) < self.window
        smoothed = int(median(self.history))
        step = Step(smoothed=smoothed, warming=warming)
        step.suppressed = self._track_spike(int(raw), smoothed, warming)

        if not warming:
            if not self.active and smoothed >= self.on:
                if now - self.last_alert >= self.cooldown:
                    self.active = True
                    self.last_alert = now
                    step.alert = True
            elif self.active and smoothed <= self.off:
                self.active = False
                step.cleared = True

        step.active = self.active
        return step

    def _track_spike(self, raw, smoothed, warming):
        if raw >= self.on:
            self._run += 1
            self._run_peak = max(self._run_peak, raw)
            if self.active or (smoothed >= self.on and not warming):
                self._run_passed = True
            return None

        result = None
        if self._run >= self.min_spike and not self._run_passed:
            result = {"frames": self._run, "peak": self._run_peak}
        self._run = 0
        self._run_peak = 0
        self._run_passed = False
        return result

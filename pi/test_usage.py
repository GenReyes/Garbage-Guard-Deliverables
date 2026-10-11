import json
import tempfile
import unittest
from pathlib import Path

import gg_usage

MB = 1024 * 1024
GB = 1024 ** 3


class Clock:
    def __init__(self, t=1767225600.0):
        self.t = t

    def __call__(self):
        return self.t


def make(tmp, clock, **kw):
    return gg_usage.Usage(path=Path(tmp) / "u.json", clock=clock, **kw)


class UsageTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.clock = Clock()
        self.u = make(self.dir.name, self.clock)

    def tearDown(self):
        self.dir.cleanup()

    def test_watchers_multiply_downloads(self):
        self.u.add("state", 1000, watchers=3)
        self.assertEqual(self.u.data["up"], 1000)
        self.assertEqual(self.u.data["down"], 3000)

    def test_no_watchers_costs_no_download(self):
        self.u.add("preview", 40000, watchers=0)
        self.assertEqual(self.u.data["down"], 0)
        self.assertEqual(self.u.data["up"], 40000)

    def test_alerts_count_once_even_unwatched(self):
        self.u.add("alerts", 500, watchers=0)
        self.assertEqual(self.u.data["down"], 500)

    def test_levels(self):
        self.assertEqual(self.u.level(), "ok")
        self.u.add("state", 8 * GB, watchers=1)
        self.assertEqual(self.u.level(), "warn")
        self.u.add("state", 1.5 * GB, watchers=1)
        self.assertEqual(self.u.level(), "near")
        self.u.add("state", 1 * GB, watchers=1)
        self.assertEqual(self.u.level(), "bad")

    def test_preview_slows_then_stops(self):
        self.assertEqual(self.u.preview_every(3), 3)
        self.u.add("state", 9.2 * GB, watchers=1)
        self.assertEqual(self.u.preview_every(3), 10)
        self.assertIn("slowed", self.u.why_slow())
        self.u.add("state", 1 * GB, watchers=1)
        self.assertIsNone(self.u.preview_every(3))

    def test_daily_preview_budget(self):
        u = make(self.dir.name, self.clock, day_preview_mb=1)
        u.add("preview", 2 * MB, watchers=1)
        self.assertIsNone(u.preview_every(3))
        self.assertIn("day", u.why_slow())
        self.clock.t += 86400
        self.assertEqual(u.preview_every(3), 3)
        self.assertEqual(u.data["day_preview"], 0)

    def test_month_resets_everything(self):
        self.u.add("state", 5 * GB, watchers=1)
        self.clock.t += 32 * 86400
        self.u.add("state", 10, watchers=1)
        self.assertEqual(self.u.data["down"], 10)
        self.assertEqual(self.u.level(), "ok")

    def test_saved_and_reloaded(self):
        self.u.add("snaps", 300000, watchers=1)
        self.assertTrue(self.u.save(force=True))
        again = make(self.dir.name, self.clock)
        self.assertEqual(again.data["down"], 300000)
        self.assertEqual(again.data["by_kind"]["snaps"], 300000)

    def test_save_is_throttled(self):
        self.u.add("state", 10, watchers=1)
        self.assertTrue(self.u.save(force=True))
        self.u.add("state", 10, watchers=1)
        self.assertFalse(self.u.save())
        self.clock.t += 31
        self.assertTrue(self.u.save())

    def test_stale_file_from_last_month_is_dropped(self):
        p = Path(self.dir.name) / "u.json"
        p.write_text(json.dumps({"month": "2019-01", "day": "2019-01-05", "down": 99, "up": 99,
                                 "day_preview": 99, "by_kind": {"state": 99}, "since": 0}))
        u = make(self.dir.name, self.clock)
        self.assertEqual(u.data["down"], 0)

    def test_report_shape(self):
        self.u.add("preview", 1000, watchers=2)
        r = self.u.report()
        self.assertTrue(r["estimate"])
        self.assertEqual(r["down_bytes"], 2000)
        self.assertEqual(r["limit_bytes"], 10 * GB)
        self.assertEqual(r["level"], "ok")
        self.assertIsNone(r["note"])


if __name__ == "__main__":
    unittest.main(verbosity=2)

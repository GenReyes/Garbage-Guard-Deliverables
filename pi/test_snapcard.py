import unittest

import numpy as np

import gg_snapcard as sc


class SnapCardTests(unittest.TestCase):
    STATE = {"timestamp": "2026-10-11 14:34:55", "smoothed": 22, "threshold": 20, "latency_ms": 68.2,
             "counts": {"bottle": 9, "bag": 6, "food_container": 7}}

    def test_photo_is_never_covered(self):
        frame = np.random.randint(0, 255, (360, 640, 3), dtype=np.uint8)
        out = sc.add_card(frame, "alert", self.STATE, 0.91)
        self.assertEqual(out.shape, (360 + sc.card_height(640), 640, 3))
        self.assertTrue(np.array_equal(out[:360], frame))

    def test_scales_with_width(self):
        self.assertGreater(sc.card_height(1920), sc.card_height(640))
        self.assertEqual(sc.card_height(320), 40)
        self.assertEqual(sc.card_height(640), 48)
        self.assertLess(sc.card_height(1920) / 1080, 0.15)

    def test_every_event_and_bad_timestamp(self):
        frame = np.zeros((120, 200, 3), dtype=np.uint8)
        for tag in ("alert", "suppressed", "test", "capture", "other"):
            out = sc.add_card(frame, tag, dict(self.STATE, timestamp="??"), None)
            self.assertEqual(out.shape[1], 200)

    def test_plain_fallback(self):
        saved, sc.Image = sc.Image, None
        try:
            out = sc.add_card(np.zeros((100, 300, 3), dtype=np.uint8), "test", self.STATE)
            self.assertEqual(out.shape[0], 100 + sc.card_height(300))
        finally:
            sc.Image = saved

    def test_level_colors(self):
        self.assertEqual(sc.level_color(5, 20), sc.OK)
        self.assertEqual(sc.level_color(12, 20), sc.WARN)
        self.assertEqual(sc.level_color(19, 20), sc.BAD)


if __name__ == "__main__":
    unittest.main()

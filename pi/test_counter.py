import unittest

from gg_counter import AccumulationCounter, point_in_polygon, rearm_level, scale_polygon, tally

FPS = 14.0
T0 = 1000.0

WATER = [(0.10, 0.40), (0.90, 0.40), (0.95, 0.95), (0.05, 0.95)]


def det(cx, cy, name="bottle", conf=0.8, size=20):
    return {"box": (cx - size, cy - size, cx + size, cy + size), "name": name, "conf": conf}


def run(counter, series, start=T0):
    steps = []
    for i, raw in enumerate(series):
        steps.append(counter.update(raw, now=start + i / FPS))
    return steps


def alert_indexes(steps):
    return [i for i, s in enumerate(steps) if s.alert]


class RoiTests(unittest.TestCase):
    def test_no_polygon_counts_everything(self):
        dets = [det(100, 50), det(300, 200, "bag"), det(500, 300, "food_container")]
        _, counts, total, _ = tally(dets, None)
        self.assertEqual(total, 3)
        self.assertEqual(counts["bag"], 1)

    def test_bridge_wall_outside_roi_is_ignored(self):
        poly = scale_polygon(WATER, 640, 360)
        wall = det(320, 60)
        water_items = [det(200, 250), det(320, 280, "bag"), det(450, 300, "food_container")]
        flags, counts, total, _ = tally([wall] + water_items, poly)
        self.assertEqual(flags[0], False)
        self.assertEqual(total, 3)
        self.assertEqual(counts["bottle"], 1)

    def test_box_is_judged_by_its_center(self):
        poly = scale_polygon(WATER, 640, 360)
        straddling_in = {"box": (300, 130, 340, 200), "name": "bag", "conf": 0.7}
        straddling_out = {"box": (300, 90, 340, 140), "name": "bag", "conf": 0.7}
        flags, _, total, _ = tally([straddling_in, straddling_out], poly)
        self.assertEqual(flags, [True, False])
        self.assertEqual(total, 1)

    def test_same_result_at_any_resolution(self):
        small = scale_polygon(WATER, 640, 360)
        large = scale_polygon(WATER, 1280, 720)
        pts = [(0.5, 0.7), (0.5, 0.1), (0.05, 0.5), (0.97, 0.97)]
        for fx, fy in pts:
            self.assertEqual(
                point_in_polygon(fx * 640, fy * 360, small),
                point_in_polygon(fx * 1280, fy * 720, large),
            )

    def test_polygon_with_fewer_than_three_points_means_whole_frame(self):
        self.assertIsNone(scale_polygon([(0.1, 0.1), (0.9, 0.9)], 640, 360))
        self.assertIsNone(scale_polygon([], 640, 360))

    def test_peak_confidence_only_uses_items_inside(self):
        poly = scale_polygon(WATER, 640, 360)
        outside = det(320, 60, conf=0.99)
        inside = det(320, 280, conf=0.61)
        _, _, _, peak = tally([outside, inside], poly)
        self.assertAlmostEqual(peak, 0.61)


class CounterTests(unittest.TestCase):
    def test_single_frame_at_startup_does_not_fire(self):
        steps = run(AccumulationCounter(), [25] + [0] * 40)
        self.assertEqual(alert_indexes(steps), [])

    def test_quiet_scene_never_fires(self):
        steps = run(AccumulationCounter(), [3] * 300)
        self.assertEqual(alert_indexes(steps), [])

    def test_alert_fires_exactly_at_threshold(self):
        series = []
        for level in range(1, 26):
            series += [level] * 15
        steps = run(AccumulationCounter(threshold=20), series)
        fired = alert_indexes(steps)
        self.assertEqual(len(fired), 1)
        self.assertEqual(series[fired[0]], 20)
        self.assertTrue(all(s.smoothed < 20 for s in steps[: fired[0]]))

    def test_sustained_rise_fires_once_on_eighth_frame(self):
        steps = run(AccumulationCounter(), [0] * 30 + [25] * 60)
        self.assertEqual(alert_indexes(steps), [37])

    def test_short_spike_is_suppressed_and_reported(self):
        steps = run(AccumulationCounter(), [2] * 30 + [25] * 5 + [2] * 30)
        self.assertEqual(alert_indexes(steps), [])
        reports = [s.suppressed for s in steps if s.suppressed]
        self.assertEqual(reports, [{"frames": 5, "peak": 25}])

    def test_two_frame_glitch_is_ignored_silently(self):
        steps = run(AccumulationCounter(), [2] * 30 + [25] * 2 + [2] * 30)
        self.assertEqual(alert_indexes(steps), [])
        self.assertEqual([s.suppressed for s in steps if s.suppressed], [])

    def test_hovering_around_threshold_does_not_chatter(self):
        series = [0] * 30 + [25] * 20 + [16, 22] * 50
        steps = run(AccumulationCounter(), series)
        self.assertEqual(len(alert_indexes(steps)), 1)
        self.assertEqual(sum(s.cleared for s in steps), 0)

    def test_clears_only_after_dropping_to_rearm_level(self):
        steps = run(AccumulationCounter(), [25] * 30 + [17] * 30)
        self.assertEqual(sum(s.cleared for s in steps), 0)
        steps = run(AccumulationCounter(), [25] * 30 + [10] * 30)
        self.assertEqual(sum(s.cleared for s in steps), 1)

    def test_cooldown_blocks_a_quick_second_alert_then_allows_it(self):
        c = AccumulationCounter()
        steps = run(c, [25] * 30 + [10] * 30 + [25] * 30)
        self.assertEqual(len(alert_indexes(steps)), 1)
        last = T0 + len(steps) / FPS
        later = c.update(25, now=T0 + 61)
        self.assertTrue(later.alert)
        self.assertGreater(T0 + 61, last)

    def test_no_second_alert_while_the_first_is_still_active(self):
        c = AccumulationCounter()
        run(c, [25] * 30)
        step = c.update(25, now=T0 + 500)
        self.assertFalse(step.alert)
        self.assertTrue(step.active)

    def test_custom_threshold_of_three_for_bench_testing(self):
        steps = run(AccumulationCounter(threshold=3), [0] * 30 + [3] * 20)
        self.assertEqual(len(alert_indexes(steps)), 1)
        steps = run(AccumulationCounter(threshold=3), [0] * 30 + [2] * 30)
        self.assertEqual(alert_indexes(steps), [])

    def test_rearm_level_scales_with_threshold(self):
        self.assertEqual(rearm_level(20), 15)
        self.assertEqual(rearm_level(8), 6)
        self.assertEqual(rearm_level(3), 2)
        self.assertEqual(rearm_level(1), 0)

    def test_changing_threshold_live_updates_rearm_level(self):
        c = AccumulationCounter(threshold=20)
        c.set_threshold(8)
        self.assertEqual((c.on, c.off), (8, 6))
        steps = run(c, [0] * 30 + [9] * 20)
        self.assertEqual(len(alert_indexes(steps)), 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)

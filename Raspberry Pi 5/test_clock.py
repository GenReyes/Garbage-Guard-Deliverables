import unittest

import gg_clock as g


class ClockTests(unittest.TestCase):
    def test_reports_probe_and_caches(self):
        calls, t = [], [0]
        c = g.Clock(probe=lambda: calls.append(1) or False, now=lambda: t[0])
        self.assertFalse(c.synced())
        self.assertFalse(c.synced())
        self.assertEqual(len(calls), 1)
        t[0] = 31
        c.synced()
        self.assertEqual(len(calls), 2)

    def test_unknown_is_not_a_warning(self):
        def boom():
            raise FileNotFoundError("timedatectl")
        self.assertTrue(g.Clock(probe=boom).synced())

    def test_fields(self):
        self.assertEqual(g.Clock(probe=lambda: True).fields(),
                         {"clock_synced": True, "clock_zone": "Asia/Manila"})


if __name__ == "__main__":
    unittest.main()

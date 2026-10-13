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
                         {"clock_synced": True, "clock_settling": False, "clock_zone": "Asia/Manila"})

    def test_just_after_boot_is_settling_not_a_fault(self):
        up = [20.0]
        c = g.Clock(probe=lambda: False, uptime=lambda: up[0])
        self.assertEqual((c.synced(), c.settling()), (False, True))
        up[0] = 200.0
        self.assertEqual((c.synced(), c.settling()), (False, False))

    def test_unknown_uptime_does_not_hide_the_warning(self):
        self.assertFalse(g.Clock(probe=lambda: False, uptime=lambda: None).settling())


if __name__ == "__main__":
    unittest.main()

import unittest

import gg_power as p


class FakeUps:
    def __init__(self):
        self.s = {"ok": True, "percent": 80, "on_ac": False}

    def status(self):
        return dict(self.s)

    def set(self, **kw):
        self.s.update(kw)


class FakeScreen:
    def __init__(self):
        self.wakes = 0

    def wake(self):
        self.wakes += 1


class Rig:
    def __init__(self, uptime_offset=10000, refuse=0, **settings):
        self.t = 0.0
        self.offset = uptime_offset
        self.ups = FakeUps()
        self.screen = FakeScreen()
        self.events = []
        self.refuse = refuse
        self.settings = dict(settings)
        self.power = p.Power(self.settings, ups=self.ups, screen=self.screen,
                             shutdown=self._off, log=lambda e: self.events.append(("log", e)),
                             flush=lambda s: self.events.append(("flush", s)),
                             clock=lambda: self.t, uptime=lambda: self.t + self.offset)

    def _off(self):
        self.events.append(("poweroff",))
        if self.refuse > 0:
            self.refuse -= 1
            return False
        return True

    def run(self, seconds, step=2.0):
        end = self.t + seconds
        while self.t < end:
            self.t += step
            self.power.tick()
        return self.power.state

    def settle(self):
        self.run(10)


def poweroffs(r):
    return sum(1 for e in r.events if e == ("poweroff",))


class PowerTests(unittest.TestCase):
    def test_nothing_happens_above_the_threshold(self):
        r = Rig()
        r.ups.set(percent=40)
        self.assertEqual(r.run(600), "armed")
        self.assertEqual(r.events, [])

    def test_never_runs_on_the_charger(self):
        r = Rig()
        r.ups.set(percent=8, on_ac=True)
        self.assertEqual(r.run(900), "armed")
        self.assertEqual(r.events, [])

    def test_debounce_then_five_minute_countdown_then_shutdown(self):
        r = Rig()
        r.settle()
        r.ups.set(percent=15)
        self.assertEqual(r.run(28), "armed")
        self.assertEqual(r.run(4), "counting")
        left = r.power.fields()["shutdown_in_s"]
        self.assertTrue(290 <= left <= 300)
        self.assertEqual(r.run(290), "counting")
        self.assertEqual(poweroffs(r), 0)
        r.run(20)
        self.assertEqual(poweroffs(r), 1)
        self.assertEqual([e[0] for e in r.events], ["log", "flush", "poweroff"])
        self.assertEqual(r.events[0], ("log", "low_battery_shutdown"))
        self.assertEqual(r.events[1], ("flush", 8))

    def test_one_low_reading_is_not_enough(self):
        r = Rig()
        r.settle()
        r.ups.set(percent=14)
        r.run(20)
        r.ups.set(percent=30)
        r.run(10)
        r.ups.set(percent=14)
        self.assertEqual(r.run(20), "armed")

    def test_boot_grace_blocks_everything_for_three_minutes(self):
        r = Rig(uptime_offset=0)
        r.ups.set(percent=12)
        self.assertEqual(r.run(170), "boot_grace")
        self.assertEqual(r.events, [])
        r.run(10)
        self.assertIn(r.power.state, ("armed",))
        self.assertEqual(r.run(40), "counting")

    def test_booting_low_gives_a_countdown_not_an_instant_shutdown(self):
        r = Rig(uptime_offset=0)
        r.ups.set(percent=12)
        r.run(200)
        r.run(40)
        self.assertEqual(r.power.state, "counting")
        self.assertEqual(poweroffs(r), 0)

    def test_cancel_pauses_and_floor_cannot_be_cancelled(self):
        r = Rig()
        r.settle()
        r.ups.set(percent=14)
        r.run(40)
        self.assertEqual(r.power.state, "counting")
        self.assertTrue(r.power.cancel()["ok"])
        self.assertEqual(r.run(600), "paused")
        self.assertEqual(poweroffs(r), 0)
        r.ups.set(percent=5)
        r.run(10)
        self.assertEqual(r.power.state, "counting")
        self.assertTrue(r.power.fields()["shutdown_floor"])
        res = r.power.cancel()
        self.assertFalse(res["ok"])
        self.assertEqual(r.power.state, "counting")
        self.assertLessEqual(r.power.fields()["shutdown_in_s"], 60)
        r.run(70)
        self.assertEqual(poweroffs(r), 1)

    def test_floor_shortens_a_running_countdown(self):
        r = Rig()
        r.settle()
        r.ups.set(percent=14)
        r.run(40)
        r.ups.set(percent=5)
        r.run(2)
        self.assertLessEqual(r.power.fields()["shutdown_in_s"], 60)
        self.assertTrue(r.power.fields()["shutdown_floor"])

    def test_rearms_when_charger_is_plugged_in(self):
        r = Rig()
        r.settle()
        r.ups.set(percent=14)
        r.run(40)
        r.power.cancel()
        r.run(10)
        self.assertEqual(r.power.state, "paused")
        r.ups.set(on_ac=True)
        self.assertEqual(r.run(4), "armed")

    def test_rearms_when_battery_rises_five_percent_above_setting(self):
        r = Rig()
        r.settle()
        r.ups.set(percent=14)
        r.run(40)
        r.power.cancel()
        r.ups.set(percent=19)
        self.assertEqual(r.run(10), "paused")
        r.ups.set(percent=20)
        self.assertEqual(r.run(4), "armed")

    def test_rearms_when_switched_on_again(self):
        r = Rig()
        r.settle()
        r.ups.set(percent=14)
        r.run(40)
        r.power.cancel()
        r.power.configure(on=True)
        self.assertEqual(r.power.state, "armed")

    def test_charger_cancels_a_running_countdown(self):
        r = Rig()
        r.settle()
        r.ups.set(percent=14)
        r.run(40)
        self.assertEqual(r.power.state, "counting")
        r.ups.set(on_ac=True)
        self.assertEqual(r.run(4), "armed")
        self.assertIsNone(r.power.fields()["shutdown_in_s"])
        r.run(400)
        self.assertEqual(poweroffs(r), 0)

    def test_unreadable_gauge_never_shuts_down(self):
        r = Rig()
        r.settle()
        r.ups.s = {"ok": False, "error": "I2C read failed"}
        self.assertEqual(r.run(900), "off")
        self.assertEqual(r.events, [])

    def test_unreadable_gauge_cancels_a_countdown(self):
        r = Rig()
        r.settle()
        r.ups.set(percent=14)
        r.run(40)
        self.assertEqual(r.power.state, "counting")
        r.ups.s = {"ok": False}
        r.run(4)
        self.assertEqual(r.power.state, "off")
        self.assertEqual(poweroffs(r), 0)

    def test_needs_several_good_reads_before_arming(self):
        r = Rig()
        r.ups.set(percent=14)
        r.run(2)
        self.assertEqual(r.power.state, "off")
        r.run(6)
        self.assertEqual(r.power.state, "armed")

    def test_switched_off_does_nothing(self):
        r = Rig(auto_shutdown=False)
        r.ups.set(percent=3)
        self.assertEqual(r.run(900), "off")
        self.assertEqual(r.events, [])

    def test_settings_are_clamped(self):
        r = Rig()
        self.assertEqual(r.power.configure(pct=2)["auto_shutdown_pct"], 10)
        self.assertEqual(r.power.configure(pct=99)["auto_shutdown_pct"], 50)
        self.assertEqual(r.power.configure(pct="x")["auto_shutdown_pct"], 50)

    def test_screen_is_woken_while_counting(self):
        r = Rig()
        r.settle()
        r.ups.set(percent=14)
        r.run(40)
        before = r.screen.wakes
        r.run(10)
        self.assertGreater(r.screen.wakes, before)

    def test_refused_poweroff_is_retried(self):
        r = Rig(refuse=1)
        r.settle()
        r.ups.set(percent=14)
        r.run(340)
        self.assertEqual(poweroffs(r), 1)
        r.run(40)
        self.assertEqual(poweroffs(r), 2)

    def test_fields_when_idle(self):
        r = Rig()
        f = r.power.fields()
        self.assertEqual((f["auto_shutdown"], f["auto_shutdown_pct"]), (True, 15))
        self.assertIsNone(f["shutdown_in_s"])


if __name__ == "__main__":
    unittest.main()

import unittest

import gg_screen as g


class FakeUps:
    def __init__(self, ok=True, on_ac=True):
        self.s = {"ok": ok, "on_ac": on_ac}

    def status(self):
        return dict(self.s)


class Rig:
    def __init__(self, have=True, fail=False, **settings):
        self.t = 0.0
        self.calls = []
        self.saved = 0
        self.fail = fail
        self.ups = FakeUps()
        self.settings = dict(settings)
        self.screen = g.Screen(self.settings, save=self._save, ups=self.ups, run=self._run,
                               clock=lambda: self.t, have=lambda n: "/bin/wlopm" if have else None,
                               environ={})

    def _save(self):
        self.saved += 1

    def _run(self, cmd, env):
        self.calls.append((cmd, env))
        return (not self.fail), ("boom" if self.fail else "")

    def at(self, t):
        self.t = t
        return self.screen.tick()


class ScreenTests(unittest.TestCase):
    def test_off_after_timeout_and_wakes_on_touch(self):
        r = Rig()
        self.assertEqual(r.at(599), "on")
        self.assertEqual(r.at(600), "off")
        self.assertEqual(r.calls[-1][0], ["wlopm", "--off", "*"])
        r.t = 700
        self.assertEqual(r.screen.touch(), "on")
        self.assertEqual(r.calls[-1][0], ["wlopm", "--on", "*"])
        self.assertEqual(r.at(1299), "on")
        self.assertEqual(r.at(1300), "off")

    def test_touch_while_on_only_resets_timer(self):
        r = Rig()
        r.t = 500
        r.screen.touch()
        self.assertEqual(r.at(1099), "on")
        self.assertEqual(r.calls, [])

    def test_dim_mode_runs_no_command(self):
        r = Rig(screen_mode="dim")
        self.assertEqual(r.at(600), "dim")
        self.assertEqual(r.calls, [])
        r.screen.touch()
        self.assertEqual((r.screen.state, r.calls), ("on", []))

    def test_never_mode_stays_on_but_manual_sleep_works(self):
        r = Rig(screen_mode="never")
        self.assertEqual(r.at(99999), "on")
        r.screen.sleep()
        self.assertEqual(r.at(100000), "off")
        self.assertEqual(r.screen.wake(), "on")

    def test_battery_timeout_is_shorter(self):
        r = Rig()
        r.ups.s["on_ac"] = False
        self.assertEqual(r.at(59), "on")
        self.assertEqual(r.at(60), "off")

    def test_unreadable_ups_uses_normal_timeout(self):
        r = Rig()
        r.ups.s = {"ok": False}
        self.assertEqual(r.at(60), "on")
        self.assertEqual(r.at(600), "off")

    def test_alert_wakes_only_when_enabled(self):
        r = Rig()
        r.at(600)
        r.screen.alert()
        self.assertEqual(r.screen.state, "on")
        r2 = Rig(screen_wake_on_alert=False)
        r2.at(600)
        r2.screen.alert()
        self.assertEqual(r2.screen.state, "off")

    def test_missing_or_failing_wlopm_falls_back_to_dim(self):
        for rig in (Rig(have=False), Rig(fail=True)):
            self.assertEqual(rig.at(600), "dim")
            self.assertTrue(rig.screen.note)

    def test_wlopm_gets_wayland_environment(self):
        r = Rig()
        r.at(600)
        env = r.calls[0][1]
        self.assertEqual(env["WAYLAND_DISPLAY"], "wayland-0")
        self.assertIn("/run/user/", env["XDG_RUNTIME_DIR"])

    def test_settings_are_clamped_and_saved(self):
        r = Rig()
        out = r.screen.configure(mode="dim", timeout_s=5, timeout_battery_s=1,
                                 dim_pct=99, wake_on_alert=False)
        self.assertEqual(out["screen_mode"], "dim")
        self.assertEqual((out["screen_timeout_s"], out["screen_timeout_battery_s"]), (60, 30))
        self.assertEqual(out["screen_dim_pct"], 50)
        self.assertFalse(out["screen_wake_on_alert"])
        self.assertEqual(r.screen.configure(timeout_s=99999)["screen_timeout_s"], 3600)
        self.assertEqual(r.screen.configure(dim_pct=1)["screen_dim_pct"], 5)
        self.assertGreaterEqual(r.saved, 3)

    def test_bad_values_are_ignored(self):
        r = Rig()
        out = r.screen.configure(mode="sideways", timeout_s="abc", dim_pct=None)
        self.assertEqual((out["screen_mode"], out["screen_timeout_s"]), ("off", 600))

    def test_changing_mode_wakes_a_sleeping_screen(self):
        r = Rig()
        r.at(600)
        r.screen.configure(mode="never")
        self.assertEqual(r.screen.state, "on")

    def test_fields_include_state(self):
        r = Rig()
        self.assertEqual(r.screen.fields()["screen"], "on")
        self.assertNotIn("screen", r.screen.fields(settings_only=True))


if __name__ == "__main__":
    unittest.main()

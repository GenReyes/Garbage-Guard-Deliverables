import unittest

import gg_screen as g


class FakeUps:
    def __init__(self, ok=True, on_ac=True):
        self.s = {"ok": ok, "on_ac": on_ac}

    def status(self):
        return dict(self.s)


class FakeWlr:
    """A stand-in compositor: outputs that really go on and off.

    `refuse` is the set of switches that return an error, `cycle_only` the
    outputs that report success but only really come back after an off and on
    in one command, and `stuck` the outputs that stay off however they are
    asked, so a wake that cannot work can be tested.
    """

    def __init__(self, outputs=(("HDMI-A-2", True, "1024x600"),), refuse=(), stuck=(),
                 cycle_only=()):
        self.out = {n: {"enabled": e, "mode": m} for n, e, m in outputs}
        self.refuse = set(refuse)
        self.stuck = set(stuck)
        self.cycle_only = set(cycle_only)
        self.cmds = []
        self.readable = True

    def listing(self):
        text = ""
        for n, o in self.out.items():
            text += f'{n} "Panel ({n})"\n  Enabled: {"yes" if o["enabled"] else "no"}\n'
            if o["mode"]:
                text += f'  Modes:\n    {o["mode"]} px, 60.000000 Hz (preferred, current)\n'
        return text

    def run(self, cmd, env):
        if cmd == ["wlr-randr"]:
            if not self.readable:
                return False, "cannot read"
            return True, self.listing()
        self.cmds.append(cmd[1:])
        args, name, bad, cycled = cmd[1:], None, False, set()
        i = 0
        while i < len(args):
            if args[i] == "--output":
                name = args[i + 1]
                i += 2
                continue
            if args[i] in ("--on", "--off"):
                want = args[i] == "--on"
                how = "off" if not want else ("mode" if "--mode" in args else "plain")
                if how in self.refuse or f"{how}:{name}" in self.refuse:
                    bad = True
                elif name in self.out:
                    if not want:
                        self.out[name]["enabled"] = False
                        cycled.add(name)
                    elif name in self.stuck:
                        pass
                    elif name in self.cycle_only and name not in cycled:
                        pass
                    else:
                        self.out[name]["enabled"] = True
            i += 1
        return (not bad), ("failed to apply configuration" if bad else "")


class Rig:
    def __init__(self, have=True, wlr=None, **settings):
        self.t = 0.0
        self.saved = 0
        self.wlr = wlr or FakeWlr()
        self.ups = FakeUps()
        self.settings = dict(settings)
        self.screen = g.Screen(self.settings, save=self._save, ups=self.ups, run=self.wlr.run,
                               clock=lambda: self.t,
                               have=lambda n: "/bin/wlr-randr" if have else None,
                               environ={})

    def _save(self):
        self.saved += 1

    def at(self, t):
        self.t = t
        return self.screen.tick()

    def lit(self, name="HDMI-A-2"):
        return self.wlr.out[name]["enabled"]

    def switches(self):
        return [c for c in self.wlr.cmds]


class ScreenTests(unittest.TestCase):
    def test_off_after_timeout_and_wakes_on_touch(self):
        r = Rig()
        self.assertEqual(r.at(599), "on")
        self.assertEqual(r.at(600), "off")
        self.assertFalse(r.lit())
        r.t = 700
        self.assertEqual(r.screen.touch(), "on")
        self.assertTrue(r.lit())
        self.assertEqual(r.at(1299), "on")
        self.assertEqual(r.at(1300), "off")

    def test_touch_while_on_only_resets_timer(self):
        r = Rig()
        r.t = 500
        r.screen.touch()
        self.assertEqual(r.at(1099), "on")
        self.assertEqual(r.switches(), [])

    def test_dim_mode_runs_no_command(self):
        r = Rig(screen_mode="dim")
        self.assertEqual(r.at(600), "dim")
        self.assertEqual(r.switches(), [])
        self.assertTrue(r.lit())
        r.screen.touch()
        self.assertEqual((r.screen.state, r.switches()), ("on", []))

    def test_never_mode_stays_on_but_manual_sleep_works(self):
        r = Rig(screen_mode="never")
        self.assertEqual(r.at(99999), "on")
        r.screen.sleep()
        self.assertEqual(r.at(100000), "off")
        self.assertEqual(r.screen.wake(), "on")
        self.assertTrue(r.lit())

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

    def test_missing_or_unreadable_wlr_randr_falls_back_to_dim(self):
        self.assertEqual(Rig(have=False).at(600), "dim")
        r = Rig()
        r.wlr.readable = False
        self.assertEqual(r.at(600), "dim")
        self.assertTrue(r.screen.note)

    def test_a_refused_switch_off_leaves_the_screen_on(self):
        r = Rig(wlr=FakeWlr(refuse=("off",)))
        self.assertEqual(r.at(600), "dim")
        self.assertTrue(r.lit())

    def test_wlr_randr_gets_wayland_environment(self):
        calls = []
        r = Rig()
        inner = r.wlr.run
        r.screen._runner = lambda c, e: (calls.append(e), inner(c, e))[1]
        r.at(600)
        self.assertEqual(calls[0]["WAYLAND_DISPLAY"], "wayland-0")
        self.assertIn("/run/user/", calls[0]["XDG_RUNTIME_DIR"])

    def test_only_enabled_screens_are_switched_and_the_same_one_comes_back(self):
        r = Rig(wlr=FakeWlr(outputs=(("HDMI-A-2", True, "1024x600"), ("HDMI-A-1", False, None))))
        r.at(600)
        self.assertFalse(r.lit("HDMI-A-2"))
        self.assertNotIn("HDMI-A-1", [c[1] for c in r.switches()])
        r.screen.touch()
        self.assertTrue(r.lit("HDMI-A-2"))
        self.assertFalse(r.lit("HDMI-A-1"))

    def test_no_enabled_screen_falls_back_to_dim(self):
        r = Rig(wlr=FakeWlr(outputs=(("HDMI-A-2", False, "1024x600"),)))
        self.assertEqual(r.at(600), "dim")

    def test_off_needs_a_touch_source_that_can_wake_it(self):
        r = Rig()
        r.screen.wake_source = type("W", (), {"ready": False})()
        self.assertEqual(r.at(600), "dim")
        self.assertIn("touch", r.screen.note)
        r2 = Rig()
        r2.screen.wake_source = type("W", (), {"ready": True})()
        self.assertEqual(r2.at(600), "off")

    def test_start_turns_a_screen_left_off_back_on(self):
        r = Rig(wlr=FakeWlr(outputs=(("HDMI-A-2", False, "1024x600"),)))
        r.screen.recover()
        self.assertTrue(r.lit())

    # ---------- waking when the compositor misbehaves ----------
    def test_an_error_on_a_screen_that_is_really_on_is_not_a_failure(self):
        """wlr-randr says "failed to apply configuration" for an output already on."""
        r = Rig()
        r.at(600)
        r.wlr.out["HDMI-A-2"]["enabled"] = True   # something else turned it back on
        r.wlr.refuse.add("plain")
        self.assertEqual(r.screen.touch(), "on")
        self.assertFalse(r.screen.off_broken)
        self.assertIsNone(r.screen.fields()["screen_note"])

    def test_wake_retries_with_the_screens_own_mode(self):
        r = Rig(wlr=FakeWlr(refuse=("plain",)))
        r.at(600)
        self.assertFalse(r.lit())
        r.screen.touch()
        self.assertTrue(r.lit())
        self.assertIn(["--output", "HDMI-A-2", "--on", "--mode", "1024x600"], r.switches())
        self.assertFalse(r.screen.off_broken)

    def test_wake_retries_with_an_off_and_on_cycle(self):
        r = Rig(wlr=FakeWlr(cycle_only=("HDMI-A-2",)))
        r.at(600)
        r.screen.touch()
        self.assertTrue(r.lit())
        self.assertIn(["--output", "HDMI-A-2", "--off", "--output", "HDMI-A-2", "--on"],
                      r.switches())

    def test_a_screen_that_will_not_come_back_keeps_being_retried(self):
        r = Rig(wlr=FakeWlr(stuck=("HDMI-A-2",)))
        r.at(600)
        self.assertEqual(r.screen.touch(), "on")
        self.assertFalse(r.lit())
        self.assertTrue(r.screen.recovering)
        self.assertTrue(r.screen.fields()["screen_note"])
        r.wlr.stuck.clear()
        r.at(601)
        self.assertTrue(r.lit())
        self.assertFalse(r.screen.recovering)

    def test_off_mode_is_not_used_again_after_a_failed_wake(self):
        r = Rig(wlr=FakeWlr(stuck=("HDMI-A-2",)))
        r.at(600)
        r.screen.touch()
        r.wlr.stuck.clear()
        r.at(601)
        self.assertTrue(r.lit())
        self.assertEqual(r.at(1300), "dim")
        self.assertTrue(r.lit())
        self.assertIn("Dim is used instead", r.screen.note)

    def test_choosing_off_again_is_a_deliberate_retry(self):
        r = Rig(wlr=FakeWlr(stuck=("HDMI-A-2",)))
        r.at(600)
        r.screen.touch()
        r.wlr.stuck.clear()
        r.at(601)
        r.screen.configure(mode="off")
        self.assertFalse(r.screen.off_broken)
        self.assertEqual(r.at(1300), "off")

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
        self.assertTrue(r.lit())

    def test_fields_include_state(self):
        r = Rig()
        self.assertEqual(r.screen.fields()["screen"], "on")
        self.assertNotIn("screen", r.screen.fields(settings_only=True))


if __name__ == "__main__":
    unittest.main()

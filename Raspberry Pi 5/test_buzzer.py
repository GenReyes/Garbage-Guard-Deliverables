import os
import tempfile
import unittest
from datetime import datetime

import gg_buzzer as g


class Rig:
    def __init__(self, **settings):
        self.settings = dict(settings)
        self.backend = g.FakeBackend()
        self.t = [1000.0]
        self.at = datetime(2026, 10, 13, 12, 0)
        self.synced = True
        self.alert = False
        self.saved = 0
        self.pauses = []
        self.b = g.Buzzer(self.settings, save=self._save, backend=self.backend,
                          clock_synced=lambda: self.synced, now=lambda: self.at,
                          mono=lambda: self.t[0], pause=self.pauses.append,
                          alert_on=lambda: self.alert)

    def _save(self):
        self.saved += 1

    def hz(self):
        return [e[1] for e in self.backend.events if e[0] == "tone"]

    def clear(self):
        self.backend.events.clear()
        self.pauses.clear()


class PlayTests(unittest.TestCase):
    def test_plays_each_step_then_silence(self):
        r = Rig()
        self.assertTrue(r.b.play("test"))
        r.b.drain()
        self.assertEqual(r.hz(), [1760, 2360])
        self.assertEqual(r.pauses, [0.14, 0.06, 0.16])
        self.assertEqual(r.backend.events[-1], ("silence",))

    def test_volume_sets_the_level(self):
        r = Rig(buzzer_volume="high")
        r.b.play("manual")
        r.b.drain()
        self.assertEqual({e[2] for e in r.backend.events if e[0] == "tone"}, {0.5})

    def test_unknown_sound_is_ignored(self):
        self.assertFalse(Rig().b.play("nope"))

    def test_unavailable_hardware_plays_nothing(self):
        r = Rig()
        r.backend.available, r.backend.note = False, "no pin"
        self.assertFalse(r.b.play("accumulation"))
        f = r.b.fields()
        self.assertEqual((f["buzzer_available"], f["buzzer_note"]), (False, "no pin"))

    def test_backend_errors_do_not_escape(self):
        r = Rig()
        r.backend.tone = lambda *a: (_ for _ in ()).throw(OSError("boom"))
        r.b.play("test")
        r.b.drain()
        self.assertIsNone(r.b.playing)


class PriorityTests(unittest.TestCase):
    def test_higher_interrupts_lower(self):
        r = Rig()
        r.b.play("muted")
        first = []

        def pause(secs):
            if not first:
                first.append(1)
                r.b.play("accumulation")
        r.b._pause = pause
        r.b.drain()
        self.assertEqual(r.hz(), [2400, 2000, 2000, 2000])

    def test_lower_is_dropped_while_higher_plays(self):
        r = Rig()
        r.b.play("accumulation")
        dropped = []

        def pause(secs):
            if not dropped:
                dropped.append(r.b.play("muted"))
        r.b._pause = pause
        r.b.drain()
        self.assertEqual(dropped, [False])
        self.assertEqual(r.hz(), [2000, 2000, 2000])

    def test_lower_pending_does_not_replace_higher_pending(self):
        r = Rig()
        r.b.play("accumulation")
        self.assertFalse(r.b.play("manual"))
        r.b.drain()
        self.assertEqual(r.hz(), [2000, 2000, 2000])

    def test_equal_priority_waits_its_turn(self):
        r = Rig()
        r.b.play("muted")
        seen = []

        def pause(secs):
            if not seen:
                seen.append(r.b.play("unmuted"))
        r.b._pause = pause
        r.b.drain()
        self.assertEqual(seen, [True])
        self.assertEqual(r.hz(), [2400, 2000, 1600, 1600, 2000, 2400])


class SwitchTests(unittest.TestCase):
    def test_master_off_is_silent(self):
        r = Rig(buzzer_on=False)
        for n in ("accumulation", "test", "muted"):
            self.assertFalse(r.b.play(n))

    def test_groups_have_their_own_switches(self):
        r = Rig(buzzer_alerts=False, buzzer_countdown=False)
        self.assertFalse(r.b.play("accumulation"))
        self.assertFalse(r.b.play("countdown"))
        self.assertFalse(r.b.play("shutdown_low"))
        self.assertTrue(r.b.play("muted"))

    def test_button_sounds_are_off_by_default(self):
        r = Rig()
        self.assertFalse(r.b.ui("tap"))
        r.b.configure(ui=True)
        self.assertTrue(r.b.ui("tap"))
        self.assertFalse(r.b.ui("accumulation"))

    def test_test_button_bypasses_switches(self):
        r = Rig(buzzer_on=False, buzzer_alerts=False)
        self.assertTrue(r.b.test("accumulation"))
        r.backend.available = False
        self.assertFalse(r.b.test("accumulation"))

    def test_turning_off_stops_a_sound_in_progress(self):
        r = Rig()
        r.b.play("accumulation")
        n = []

        def pause(secs):
            n.append(1)
            if len(n) == 1:
                r.b.configure(on=False)
        r.b._pause = pause
        r.b.drain()
        self.assertEqual(r.hz(), [2000])


class QuietTests(unittest.TestCase):
    def test_window_inside_a_day(self):
        self.assertTrue(g.in_window(13 * 60, 12 * 60, 14 * 60))
        self.assertFalse(g.in_window(14 * 60, 12 * 60, 14 * 60))

    def test_window_across_midnight(self):
        self.assertTrue(g.in_window(23 * 60, 22 * 60, 6 * 60))
        self.assertTrue(g.in_window(5 * 60, 22 * 60, 6 * 60))
        self.assertFalse(g.in_window(12 * 60, 22 * 60, 6 * 60))

    def test_same_start_and_end_is_empty(self):
        self.assertFalse(g.in_window(0, 600, 600))

    def test_quiet_hours_silence_everything(self):
        r = Rig(quiet_on=True)
        r.at = datetime(2026, 10, 13, 23, 30)
        self.assertTrue(r.b.quiet_now())
        for n in ("accumulation", "shutdown_low", "countdown", "muted"):
            self.assertFalse(r.b.play(n))
        self.assertTrue(r.b.fields()["quiet_now"])

    def test_not_applied_when_the_clock_is_not_synced(self):
        r = Rig(quiet_on=True)
        r.at, r.synced = datetime(2026, 10, 13, 23, 30), False
        self.assertFalse(r.b.quiet_now())
        self.assertTrue(r.b.play("accumulation"))
        self.assertEqual(r.b.fields()["quiet_note"], g.NOT_SYNCED)

    def test_off_means_off(self):
        r = Rig()
        r.at = datetime(2026, 10, 13, 23, 30)
        self.assertFalse(r.b.quiet_now())


class ConfigureTests(unittest.TestCase):
    def test_clamps_and_validates(self):
        r = Rig()
        f = r.b.configure(volume="loud", repeat_s=5, quiet_from="7:5", quiet_to="25:00")
        self.assertEqual((f["buzzer_volume"], f["buzzer_repeat_s"], f["quiet_from"], f["quiet_to"]),
                         ("medium", 10, "07:05", "06:00"))
        self.assertEqual(r.b.configure(repeat_s=9999)["buzzer_repeat_s"], 300)
        self.assertEqual(r.b.configure(repeat_s="x")["buzzer_repeat_s"], 300)
        self.assertGreaterEqual(r.saved, 3)

    def test_defaults(self):
        f = Rig().b.fields()
        self.assertEqual((f["buzzer_on"], f["buzzer_ui"], f["buzzer_repeat_s"], f["quiet_on"]),
                         (True, False, 30, False))


class EventTests(unittest.TestCase):
    def test_every_history_event_has_its_sound(self):
        r = Rig()
        for kind, sound in g.EVENT_SOUND.items():
            r.b.event(kind)
            r.b.drain()
            self.assertTrue(r.hz(), kind)
            r.clear()

    def test_suppressed_and_unknown_are_silent(self):
        r = Rig()
        self.assertFalse(r.b.event("suppressed"))
        self.assertFalse(r.b.event("whatever"))

    def test_sounds_stay_in_the_band_a_small_buzzer_can_be_heard(self):
        for name, (_p, _g, seq) in g.SOUNDS.items():
            for hz, _ms in seq:
                self.assertTrue(hz == 0 or 1000 <= hz <= 3000, name)


class RepeatTests(unittest.TestCase):
    def test_repeats_while_alert_is_raised(self):
        r = Rig()
        r.b.event("accumulation")
        r.b.drain()
        r.clear()
        r.alert = True
        r.t[0] += 29
        r.b.tick()
        r.b.drain()
        self.assertEqual(r.hz(), [])
        r.t[0] += 2
        r.b.tick()
        r.b.drain()
        self.assertEqual(r.hz(), [2000, 2000, 2000])

    def test_stops_when_alert_clears_or_is_muted(self):
        r = Rig()
        r.b.event("accumulation")
        r.b.drain()
        r.clear()
        r.alert = False
        r.t[0] += 100
        r.b.tick()
        r.b.drain()
        self.assertEqual(r.hz(), [])
        self.assertIsNone(r.b.next_repeat)

    def test_repeat_can_be_switched_off(self):
        r = Rig(buzzer_repeat=False)
        r.b.event("accumulation")
        r.b.drain()
        r.clear()
        r.alert = True
        r.t[0] += 500
        r.b.tick()
        r.b.drain()
        self.assertEqual(r.hz(), [])

    def test_interval_follows_the_setting(self):
        r = Rig(buzzer_repeat_s=60)
        r.b.event("accumulation")
        r.b.drain()
        r.clear()
        r.alert = True
        r.t[0] += 45
        r.b.tick()
        r.b.drain()
        self.assertEqual(r.hz(), [])
        r.t[0] += 20
        r.b.tick()
        r.b.drain()
        self.assertTrue(r.hz())

    def test_a_late_unmute_waits_a_full_interval(self):
        r = Rig()
        r.alert = True
        r.b.tick()
        r.b.drain()
        self.assertEqual(r.hz(), [])
        r.t[0] += 31
        r.b.tick()
        r.b.drain()
        self.assertTrue(r.hz())


class ThreadTests(unittest.TestCase):
    def test_the_player_thread_plays_a_sound(self):
        import time
        backend = g.FakeBackend()
        b = g.Buzzer({"buzzer_ui": True}, backend=backend)
        b.start()
        b.start()
        b.play("tap")
        for _ in range(100):
            if backend.events and backend.events[-1] == ("silence",):
                break
            time.sleep(0.01)
        self.assertEqual([e[1] for e in backend.events if e[0] == "tone"], [2600])


class BackendTests(unittest.TestCase):
    class Lg:
        def __init__(self, fail_chip=()):
            self.calls, self.fail = [], fail_chip

        def gpiochip_open(self, chip):
            if chip in self.fail:
                raise OSError("no chip")
            return chip + 100

        def gpio_claim_output(self, h, pin, level):
            self.calls.append(("claim", h, pin, level))

        def tx_pwm(self, h, pin, hz, duty):
            self.calls.append(("pwm", hz, round(duty, 1)))

        def gpio_write(self, h, pin, level):
            self.calls.append(("write", level))

        def gpiochip_close(self, h):
            self.calls.append(("close",))

    def test_gpio_holds_the_pin_high_and_inverts_the_duty(self):
        lg = self.Lg()
        b = g.GpioBackend(lgpio=lg)
        self.assertTrue(b.available)
        self.assertEqual(lg.calls[0], ("claim", 104, 12, 1))
        b.tone(2000, 0.25)
        self.assertEqual(lg.calls[-1], ("pwm", 2000.0, 75.0))
        b.silence()
        self.assertEqual(lg.calls[-1], ("write", 1))

    def test_gpio_falls_back_to_the_other_chip(self):
        b = g.GpioBackend(lgpio=self.Lg(fail_chip=(4,)))
        self.assertTrue(b.available)

    def test_gpio_reports_why_it_failed(self):
        b = g.GpioBackend(lgpio=self.Lg(fail_chip=(4, 0)))
        self.assertFalse(b.available)
        self.assertIn("could not be opened", b.note)

    def test_sysfs_without_pwm_is_unavailable(self):
        with tempfile.TemporaryDirectory() as d:
            b = g.SysfsPwmBackend(root=d)
            self.assertFalse(b.available)
            self.assertIn("pwm-2chan", b.note)

    @staticmethod
    def channel(d, chip, n=None):
        path = os.path.join(d, chip, "pwm0")
        os.makedirs(path)
        open(os.path.join(path, "enable"), "w").close()
        if n:
            with open(os.path.join(d, chip, "npwm"), "w") as f:
                f.write(n)

    def test_sysfs_picks_the_four_channel_chip(self):
        with tempfile.TemporaryDirectory() as d:
            self.channel(d, "pwmchip0", "2")
            self.channel(d, "pwmchip2", "4")
            self.assertIn("pwmchip2", g.SysfsPwmBackend(root=d).dir)

    def test_sysfs_reports_a_channel_it_cannot_use(self):
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(os.path.join(d, "pwmchip0", "pwm0"))
            b = g.SysfsPwmBackend(root=d, sleep=lambda s: None)
            self.assertFalse(b.available)
            self.assertIn("not writable", b.note)

    def test_sysfs_writes_period_and_duty(self):
        with tempfile.TemporaryDirectory() as d:
            ch = os.path.join(d, "pwmchip0", "pwm0")
            self.channel(d, "pwmchip0")
            b = g.SysfsPwmBackend(root=d)
            self.assertTrue(b.available)
            b.tone(2000, 0.25)
            def read(n):
                with open(os.path.join(ch, n)) as f:
                    return f.read()
            self.assertEqual((read("period"), read("duty_cycle"), read("enable")), ("500000", "375000", "1"))
            b.silence()
            self.assertEqual(read("enable"), "0")

    def test_fake_kind_needs_no_hardware(self):
        self.assertTrue(g.make_backend("fake").available)


if __name__ == "__main__":
    unittest.main()

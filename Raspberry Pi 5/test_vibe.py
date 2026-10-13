import unittest
from datetime import datetime

import gg_buzzer as gb
import gg_vibe as g


class Rig:
    def __init__(self, **settings):
        self.settings = dict(settings)
        self.backend = gb.FakeBackend()
        self.at = datetime(2026, 10, 13, 12, 0)
        self.pauses = []
        self.v = g.Vibe(self.settings, backend=self.backend, now=lambda: self.at,
                        pause=self.pauses.append)

    def levels(self):
        return [e[2] for e in self.backend.events if e[0] == "tone"]


class VibeTests(unittest.TestCase):
    def test_pulses_follow_the_pattern(self):
        r = Rig()
        r.v.play("accumulation")
        r.v.drain()
        self.assertEqual(r.levels(), [0.7, 0.7, 0.7])
        self.assertEqual(r.pauses, [0.3, 0.12, 0.3, 0.12, 0.3])
        self.assertEqual(r.backend.events[-1], ("silence",))

    def test_every_tone_uses_the_pwm_rate_not_a_pitch(self):
        r = Rig()
        r.v.play("test")
        r.v.drain()
        self.assertEqual({e[1] for e in r.backend.events if e[0] == "tone"}, {g.PWM_HZ})

    def test_strength_sets_the_duty(self):
        r = Rig(vibe_strength="high")
        r.v.play("manual")
        r.v.drain()
        self.assertEqual(r.levels(), [1.0])
        r.v.configure(strength="low")
        r.v.play("manual")
        r.v.drain()
        self.assertEqual(r.levels()[-1], 0.4)

    def test_sounds_without_a_pattern_do_not_vibrate(self):
        r = Rig()
        for n in ("tap", "switch", "ready", "muted", "unmuted"):
            self.assertFalse(r.v.play(n), n)

    def test_off_and_quiet_hours_silence_it(self):
        r = Rig(vibe_on=False)
        self.assertFalse(r.v.play("accumulation"))
        r = Rig(quiet_on=True)
        r.at = datetime(2026, 10, 13, 23, 0)
        self.assertFalse(r.v.play("accumulation"))
        self.assertTrue(r.v.test("accumulation"))

    def test_it_is_independent_of_the_buzzer_switches(self):
        r = Rig(buzzer_on=False, buzzer_alerts=False)
        self.assertTrue(r.v.play("accumulation"))

    def test_configure_validates(self):
        r = Rig()
        f = r.v.configure(on=False, strength="huge")
        self.assertEqual((f["vibe_on"], f["vibe_strength"]), (False, "medium"))

    def test_unavailable_reports_why(self):
        r = Rig()
        r.backend.available, r.backend.note = False, "no pwm"
        self.assertFalse(r.v.play("test"))
        f = r.v.fields()
        self.assertEqual((f["vibe_available"], f["vibe_note"]), (False, "no pwm"))

    def test_turning_off_stops_a_pattern(self):
        r = Rig()
        r.v.play("accumulation")
        n = []

        def pause(secs):
            n.append(1)
            if len(n) == 1:
                r.v.configure(on=False)
        r.v._pause = pause
        r.v.drain()
        self.assertEqual(len(r.levels()), 1)


class FollowTests(unittest.TestCase):
    def test_the_buzzer_hands_every_sound_on(self):
        settings = {}
        sound = gb.Buzzer(settings, backend=gb.FakeBackend(), pause=lambda s: None)
        shake = g.Vibe(settings, backend=gb.FakeBackend(), pause=lambda s: None)
        sound.also.append(shake.play)
        sound.event("accumulation")
        shake.drain()
        self.assertEqual(len([e for e in shake.backend.events if e[0] == "tone"]), 3)

    def test_it_still_vibrates_when_the_buzzer_is_off_or_missing(self):
        settings = {"buzzer_on": False}
        sound = gb.Buzzer(settings, backend=gb.FakeBackend(), pause=lambda s: None)
        sound.backend.available = False
        shake = g.Vibe(settings, backend=gb.FakeBackend(), pause=lambda s: None)
        sound.also.append(shake.play)
        sound.event("accumulation")
        shake.drain()
        self.assertTrue(shake.backend.events)

    def test_a_failing_follower_does_not_break_the_buzzer(self):
        sound = gb.Buzzer({}, backend=gb.FakeBackend(), pause=lambda s: None)
        sound.also.append(lambda n, f: 1 / 0)
        self.assertTrue(sound.play("test"))


class BackendTests(unittest.TestCase):
    def test_backend_uses_the_second_channel_and_idles_low(self):
        be = g.make_backend("sysfs")
        self.assertEqual(be.channel, 1)
        self.assertFalse(be.active_low)

    def test_auto_follows_the_pin_setup(self):
        self.assertEqual(g.make_backend("auto", pin_is_pwm=lambda p: False).pin, 13)
        self.assertEqual(g.make_backend("auto", pin_is_pwm=lambda p: p == 13).channel, 1)


if __name__ == "__main__":
    unittest.main()

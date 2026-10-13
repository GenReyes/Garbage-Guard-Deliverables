"""Vibration motor module on GPIO13 (PWM0 channel 1), the buzzer's sibling.

It plays patterns of pulses instead of tones: strength is the PWM duty cycle
(how much of the time the motor has power), and a pattern is a list of
(on, milliseconds) steps. It follows the buzzer's sounds, so the alarm, its
repeat, the countdown and the tests shake as well as sound, and it obeys the
same quiet hours. The module is a motor with its own driver, so the pin only
carries a signal.
"""
import os

import gg_buzzer as b

GPIO_PIN = 13
PWM_CHANNEL = 1
ACTIVE_LOW = False
PWM_HZ = 1000

DEFAULTS = {"vibe_on": True, "vibe_strength": "medium"}
STRENGTHS = {"low": 0.4, "medium": 0.7, "high": 1.0}

# name: (priority, group, [(on, ms), ...]); on is 0 or 1, 0 is a pause.
PATTERNS = {
    "accumulation": (5, "alerts", [(1, 300), (0, 120), (1, 300), (0, 120), (1, 300)]),
    "shutdown_low": (5, "countdown", [(1, 600)]),
    "countdown": (4, "countdown", [(1, 150)]),
    "test": (3, "events", [(1, 120), (0, 90), (1, 220)]),
    "auto_muted": (3, "events", [(1, 100), (0, 80), (1, 100)]),
    "shutdown_manual": (3, "countdown", [(1, 400)]),
    "manual": (2, "events", [(1, 70)]),
}


def make_backend(kind=None, pin_is_pwm=b.pin_is_pwm):
    kind = (kind or os.environ.get("GG_VIBE_BACKEND") or os.environ.get("GG_BUZZER_BACKEND") or "auto").lower()
    if kind == "fake":
        return b.FakeBackend(show=True)
    if kind == "auto":
        kind = "sysfs" if pin_is_pwm(GPIO_PIN) else "gpio"
    if kind == "sysfs":
        return b.SysfsPwmBackend(channel=PWM_CHANNEL, active_low=ACTIVE_LOW)
    return b.GpioBackend(pin=GPIO_PIN, active_low=ACTIVE_LOW)


class Vibe(b.Buzzer):
    TABLE = PATTERNS
    LABEL = "VIBE"

    def __init__(self, settings, **kw):
        super().__init__(settings, **kw)
        for k, v in DEFAULTS.items():
            settings.setdefault(k, v)

    def configure(self, on=None, strength=None):
        with self.cond:
            s = self.settings
            if on is not None:
                s["vibe_on"] = bool(on)
            if strength in STRENGTHS:
                s["vibe_strength"] = strength
            if not s["vibe_on"]:
                self.pending = None
                self.cond.notify_all()
            self._save()
        return self.fields()

    def _allowed(self, group):
        return bool(self.settings["vibe_on"]) and not self.quiet_now()

    def _master(self):
        return self.settings["vibe_on"]

    def _level(self):
        return STRENGTHS.get(self.settings["vibe_strength"], STRENGTHS["medium"])

    def _on(self, _on, level):
        self.backend.tone(PWM_HZ, level)

    def tick(self):
        pass

    def fields(self):
        s = self.settings
        return {"vibe_on": bool(s["vibe_on"]), "vibe_strength": s["vibe_strength"],
                "vibe_available": bool(self.backend.available),
                "vibe_note": None if self.backend.available else (self.backend.note or "the vibration motor is not available")}

"""Passive buzzer on the Pi 5: a short, different sound for each kind of event.

A passive buzzer only sounds when it is fed a square wave, and the pitch is the
wave's frequency, so each sound here is a list of (Hz, milliseconds) steps.
The module is low level trigger: it sounds while its input pin is low, so the
pin is held high when silent.

Rules this file keeps (see BUZZER_PLAN.md):
  it never blocks or breaks detection: sounds are played on their own thread,
    a sound that cannot be played is dropped, and every error is swallowed
  a higher priority sound interrupts a lower one, a lower one that arrives while
    a higher one plays is dropped, so sounds never pile up
  quiet hours silence everything, with no exception
  no hardware is fine: the feature reports why it is unavailable
"""
import os
import threading
import time
from datetime import datetime

GPIO_PIN = 12
ACTIVE_LOW = True

DEFAULTS = {"buzzer_on": True, "buzzer_volume": "medium", "buzzer_alerts": True,
            "buzzer_countdown": True, "buzzer_ui": False, "buzzer_repeat": True,
            "buzzer_repeat_s": 30, "quiet_on": False, "quiet_from": "22:00",
            "quiet_to": "06:00"}
VOLUMES = {"low": 0.08, "medium": 0.25, "high": 0.5}
REPEAT_RANGE = (10, 300)
NOT_SYNCED = "The Pi's clock is not synchronised, so quiet hours are not applied."

# name: (priority, group, [(Hz, ms), ...]); 0 Hz is a rest.
# Groups: alerts and countdown have their own switch, ui is the button sounds,
# events follow the master switch only.
SOUNDS = {
    "accumulation": (5, "alerts", [(2000, 250), (0, 100), (2000, 250), (0, 100), (2000, 250)]),
    "shutdown_low": (5, "countdown", [(2300, 150), (2000, 150), (1700, 300)]),
    "countdown": (4, "countdown", [(2000, 200)]),
    "test": (3, "events", [(1800, 140), (0, 60), (2300, 160)]),
    "auto_muted": (3, "events", [(2000, 150), (0, 50), (1500, 300)]),
    "shutdown_manual": (3, "countdown", [(2300, 150), (2000, 150), (1700, 300)]),
    "manual": (2, "events", [(2200, 40), (0, 40), (2200, 40)]),
    "muted": (2, "events", [(2300, 60), (2000, 60), (1700, 60)]),
    "unmuted": (2, "events", [(1700, 60), (2000, 60), (2300, 60)]),
    "ready": (1, "events", [(1700, 80), (2000, 80), (2300, 160)]),
    "tap": (0, "ui", [(2200, 15)]),
    "switch": (0, "ui", [(1900, 35), (2300, 45)]),
}
UI_SOUNDS = ("tap", "switch")
EVENT_SOUND = {"accumulation": "accumulation", "low_battery_shutdown": "shutdown_low",
               "manual_shutdown": "shutdown_manual", "test": "test",
               "auto_muted": "auto_muted", "manual": "manual", "muted": "muted",
               "unmuted": "unmuted"}


def _minutes(text):
    try:
        h, m = str(text).split(":")
        h, m = int(h), int(m)
    except (ValueError, TypeError):
        return None
    return h * 60 + m if 0 <= h < 24 and 0 <= m < 60 else None


def in_window(now_min, start, end):
    if start == end:
        return False
    if start < end:
        return start <= now_min < end
    return now_min >= start or now_min < end


class FakeBackend:
    available, note = True, None

    def __init__(self, show=False):
        self.events, self.show = [], show

    def tone(self, hz, level):
        self.events.append(("tone", hz, level))
        if self.show:
            print(f"[BUZZER] {hz} Hz at {level:.2f}", flush=True)

    def silence(self):
        self.events.append(("silence",))

    def close(self):
        pass


class GpioBackend:
    def __init__(self, pin=GPIO_PIN, active_low=ACTIVE_LOW, lgpio=None):
        self.pin, self.active_low = pin, active_low
        self.available, self.note = False, None
        self.lg = self.handle = None
        try:
            self.lg = lgpio or __import__("lgpio")
        except ImportError:
            self.note = "the lgpio library is not installed"
            return
        error = None
        for chip in (4, 0):
            try:
                self.handle = self.lg.gpiochip_open(chip)
                self.lg.gpio_claim_output(self.handle, pin, 1 if active_low else 0)
                self.available = True
                return
            except Exception as e:
                error = e
                self.handle = None
        self.note = f"the buzzer pin could not be opened ({error})"

    def tone(self, hz, level):
        duty = (1 - level) if self.active_low else level
        self.lg.tx_pwm(self.handle, self.pin, float(hz), duty * 100)

    def silence(self):
        try:
            self.lg.tx_pwm(self.handle, self.pin, 0, 0)
        except Exception:
            pass
        self.lg.gpio_write(self.handle, self.pin, 1 if self.active_low else 0)

    def close(self):
        try:
            self.silence()
            self.lg.gpiochip_close(self.handle)
        except Exception:
            pass


class SysfsPwmBackend:
    ROOT = "/sys/class/pwm"

    def __init__(self, channel=0, active_low=ACTIVE_LOW, root=None, sleep=time.sleep):
        self.active_low, self.channel = active_low, channel
        self.available, self.note, self.dir = False, None, None
        root = root or self.ROOT
        try:
            chips = sorted(c for c in os.listdir(root) if c.startswith("pwmchip"))
        except OSError:
            chips = []
        if not chips:
            self.note = "no hardware PWM found; add the pwm-2chan overlay to config.txt"
            return
        chip = os.path.join(root, self._pick(root, chips))
        self.dir = os.path.join(chip, f"pwm{channel}")
        try:
            if not os.path.isdir(self.dir):
                self._write(os.path.join(chip, "export"), channel)
            for _ in range(20):
                if os.access(os.path.join(self.dir, "enable"), os.W_OK):
                    break
                sleep(0.05)
            else:
                raise PermissionError("the PWM files are not writable by this user")
            self.available = True
        except OSError as e:
            self.note = f"hardware PWM could not be opened ({e})"

    @staticmethod
    def _pick(root, chips):
        wanted = os.environ.get("GG_PWM_CHIP")
        if wanted and f"pwmchip{wanted}" in chips:
            return f"pwmchip{wanted}"
        for c in chips:
            try:
                with open(os.path.join(root, c, "npwm")) as f:
                    if f.read().strip() == "4":
                        return c
            except OSError:
                pass
        return chips[0]

    @staticmethod
    def _write(path, value):
        with open(path, "w") as f:
            f.write(str(int(value)))

    def tone(self, hz, level):
        period = int(1e9 / hz)
        on = (1 - level) if self.active_low else level
        self._write(os.path.join(self.dir, "enable"), 0)
        self._write(os.path.join(self.dir, "duty_cycle"), 0)
        self._write(os.path.join(self.dir, "period"), period)
        self._write(os.path.join(self.dir, "duty_cycle"), int(period * on))
        self._write(os.path.join(self.dir, "enable"), 1)

    def silence(self):
        self._write(os.path.join(self.dir, "enable"), 0)

    def close(self):
        try:
            self.silence()
        except OSError:
            pass


def make_backend(kind=None):
    kind = (kind or os.environ.get("GG_BUZZER_BACKEND") or "gpio").lower()
    if kind == "fake":
        return FakeBackend(show=True)
    if kind == "sysfs":
        return SysfsPwmBackend()
    return GpioBackend()


class Buzzer:
    def __init__(self, settings, save=None, backend=None, clock_synced=lambda: True,
                 now=datetime.now, mono=time.monotonic, pause=None, alert_on=None):
        self.settings = settings
        for k, v in DEFAULTS.items():
            settings.setdefault(k, v)
        self._save = save or (lambda: None)
        self.backend = backend or FakeBackend()
        self.clock_synced = clock_synced
        self.now = now
        self.mono = mono
        self._pause = pause or self._wait
        self.alert_on = alert_on or (lambda: False)
        self.cond = threading.Condition()
        self.pending = None
        self.playing = None
        self.next_repeat = None
        self.started = False

    # ---------- settings ----------
    def configure(self, on=None, volume=None, alerts=None, countdown=None, ui=None,
                  repeat=None, repeat_s=None, quiet_on=None, quiet_from=None, quiet_to=None):
        with self.cond:
            s = self.settings
            for key, val in (("buzzer_on", on), ("buzzer_alerts", alerts),
                             ("buzzer_countdown", countdown), ("buzzer_ui", ui),
                             ("buzzer_repeat", repeat), ("quiet_on", quiet_on)):
                if val is not None:
                    s[key] = bool(val)
            if volume in VOLUMES:
                s["buzzer_volume"] = volume
            if repeat_s is not None:
                try:
                    s["buzzer_repeat_s"] = max(REPEAT_RANGE[0], min(REPEAT_RANGE[1], int(repeat_s)))
                except (TypeError, ValueError):
                    pass
            for key, val in (("quiet_from", quiet_from), ("quiet_to", quiet_to)):
                if val is not None and _minutes(val) is not None:
                    h, m = divmod(_minutes(val), 60)
                    s[key] = f"{h:02d}:{m:02d}"
            if not s["buzzer_on"]:
                self.pending = None
                self.cond.notify_all()
            self._save()
        return self.fields()

    def quiet_now(self):
        s = self.settings
        if not s["quiet_on"] or not self.clock_synced():
            return False
        start, end = _minutes(s["quiet_from"]), _minutes(s["quiet_to"])
        if start is None or end is None:
            return False
        n = self.now()
        return in_window(n.hour * 60 + n.minute, start, end)

    def fields(self):
        s = self.settings
        out = {k: s[k] for k in DEFAULTS}
        out["buzzer_available"] = bool(self.backend.available)
        out["buzzer_note"] = None if self.backend.available else (self.backend.note or "the buzzer is not available")
        out["quiet_now"] = self.quiet_now()
        out["quiet_note"] = NOT_SYNCED if (s["quiet_on"] and not self.clock_synced()) else None
        return out

    # ---------- playing ----------
    def _allowed(self, group):
        s = self.settings
        if not s["buzzer_on"] or self.quiet_now():
            return False
        return {"alerts": s["buzzer_alerts"], "countdown": s["buzzer_countdown"],
                "ui": s["buzzer_ui"], "events": True}.get(group, False)

    def play(self, name, force=False):
        spec = SOUNDS.get(name)
        if spec is None:
            return False
        prio, group, seq = spec
        try:
            with self.cond:
                if not self.backend.available:
                    return False
                if not force and not self._allowed(group):
                    return False
                if self.playing is not None and prio < self.playing:
                    return False
                if self.pending is not None and prio < self.pending[0]:
                    return False
                self.pending = (prio, name, seq)
                if self.playing is None or prio > self.playing:
                    self.cond.notify_all()
                return True
        except Exception as e:
            print(f"[BUZZER] {e}", flush=True)
            return False

    def event(self, kind):
        name = EVENT_SOUND.get(kind)
        if name is None:
            return False
        ok = self.play(name)
        if name == "accumulation":
            self.next_repeat = self.mono() + self.settings["buzzer_repeat_s"]
        return ok

    def ui(self, sound):
        return self.play(sound) if sound in UI_SOUNDS else False

    def test(self, name):
        return self.play(name, force=True)

    def tick(self):
        if not self.alert_on():
            self.next_repeat = None
            return
        if not self.settings["buzzer_repeat"]:
            return
        t = self.mono()
        gap = self.settings["buzzer_repeat_s"]
        if self.next_repeat is None:
            self.next_repeat = t + gap
        elif t >= self.next_repeat:
            self.next_repeat = t + gap
            self.play("accumulation")

    def _wait(self, secs):
        with self.cond:
            self.cond.wait(secs)

    def _outranked(self, prio):
        with self.cond:
            return self.pending is not None and self.pending[0] > prio

    def _run(self, item):
        prio, _name, seq = item
        level = VOLUMES.get(self.settings["buzzer_volume"], VOLUMES["medium"])
        try:
            for hz, ms in seq:
                if self._outranked(prio) or not self.settings["buzzer_on"]:
                    break
                if hz:
                    self.backend.tone(hz, level)
                else:
                    self.backend.silence()
                self._pause(ms / 1000.0)
        finally:
            try:
                self.backend.silence()
            except Exception as e:
                print(f"[BUZZER] {e}", flush=True)

    def drain(self):
        while True:
            with self.cond:
                item, self.pending = self.pending, None
                if item is None:
                    return
                self.playing = item[0]
            try:
                self._run(item)
            except Exception as e:
                print(f"[BUZZER] {e}", flush=True)
            finally:
                with self.cond:
                    self.playing = None

    def start(self):
        if self.started:
            return
        self.started = True

        def loop():
            while True:
                try:
                    self.tick()
                    self.drain()
                    with self.cond:
                        if self.pending is None:
                            self.cond.wait(0.5)
                except Exception as e:
                    print(f"[BUZZER] {e}", flush=True)
                    time.sleep(1.0)
        threading.Thread(target=loop, daemon=True).start()

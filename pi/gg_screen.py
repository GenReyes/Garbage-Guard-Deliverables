"""Screen saver for the Pi's own touchscreen.

Off mode switches the HDMI output off with wlr-randr (the cheap 7 inch board
then turns its backlight off); wlopm does not work on this Pi's display
driver. While the output is off the compositor may not pass taps on, so
gg_touch watches the touchscreen directly and wakes it. Dim mode only reports
"dim" and the dashboard page draws a dark layer over itself, because these
boards have no software brightness control. Detection, alerts and Firebase
are never touched.

The timer is shorter while the Pi runs on battery.
"""
import os
import shutil
import subprocess
import threading
import time

DEFAULTS = {"screen_mode": "off", "screen_timeout_s": 600, "screen_timeout_battery_s": 60,
            "screen_dim_pct": 20, "screen_wake_on_alert": True}
MODES = ("off", "dim", "never")
LIMITS = {"screen_timeout_s": (60, 3600), "screen_timeout_battery_s": (30, 3600),
          "screen_dim_pct": (5, 50)}


def _run(cmd, env):
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=5, env=env)
        return r.returncode == 0, (r.stderr or r.stdout).strip()
    except (OSError, subprocess.TimeoutExpired) as e:
        return False, str(e)


def _clamp(v, key):
    lo, hi = LIMITS[key]
    return max(lo, min(hi, int(v)))


class Screen:
    def __init__(self, settings, save=None, ups=None, run=_run, clock=time.monotonic,
                 have=shutil.which, environ=None):
        self.settings = settings
        for k, v in DEFAULTS.items():
            settings.setdefault(k, v)
        self._save = save or (lambda: None)
        self.ups = ups
        self._runner = run
        self.clock = clock
        self.have = have
        self.environ = os.environ if environ is None else environ
        self.lock = threading.RLock()
        self.state = "on"
        self.last_touch = clock()
        self.note = None
        self.off_outputs = []
        self.wake_source = None

    def _env(self):
        env = dict(self.environ)
        env.setdefault("WAYLAND_DISPLAY", "wayland-0")
        env.setdefault("XDG_RUNTIME_DIR", f"/run/user/{os.getuid()}")
        return env

    def _outputs(self):
        ok, text = self._runner(["wlr-randr"], self._env())
        if not ok:
            return None
        rows = []
        for line in text.splitlines():
            if line and not line[0].isspace():
                rows.append([line.split()[0], False])
            elif rows and line.strip().startswith("Enabled:"):
                rows[-1][1] = line.split(":", 1)[1].strip().lower() == "yes"
        return [(n, e) for n, e in rows]

    def _switch(self, names, flag):
        done = []
        for n in names:
            ok, msg = self._runner(["wlr-randr", "--output", n, flag], self._env())
            if not ok:
                self.note = msg or f"wlr-randr {flag} failed"
                print(f"[SCREEN] wlr-randr {n} {flag} failed: {self.note}", flush=True)
                return False, done
            done.append(n)
        return True, done

    def _power_off(self):
        if not self.have("wlr-randr"):
            self.note = "wlr-randr is not installed"
            return False
        if self.wake_source is not None and not self.wake_source.ready:
            self.note = "no touchscreen could be read, so a tap could not wake it"
            return False
        rows = self._outputs()
        names = [n for n, e in rows or [] if e]
        if not names:
            self.note = "no enabled screen found"
            return False
        ok, done = self._switch(names, "--off")
        if not ok:
            self._switch(done, "--on")
            return False
        self.off_outputs = names
        self.note = None
        return True

    def _power_on(self):
        names = list(self.off_outputs)
        if not names:
            names = [n for n, e in self._outputs() or [] if not e]
        ok, _ = self._switch(names, "--on")
        self.off_outputs = [] if ok else names
        return ok

    def recover(self):
        if self.have("wlr-randr") and any(not e for n, e in self._outputs() or []):
            self._power_on()

    def on_battery(self):
        if self.ups is None:
            return False
        st = self.ups.status()
        return bool(st.get("ok")) and st.get("on_ac") is False

    def timeout(self):
        key = "screen_timeout_battery_s" if self.on_battery() else "screen_timeout_s"
        return int(self.settings[key])

    def touch(self):
        with self.lock:
            self.last_touch = self.clock()
            if self.state != "on":
                self.wake()
            return self.state

    def wake(self):
        with self.lock:
            if self.state == "off":
                self._power_on()
            self.state = "on"
            self.last_touch = self.clock()
            return self.state

    def sleep(self):
        with self.lock:
            if self.state != "on":
                return self.state
            how = self.settings["screen_mode"]
            if how == "off" or how == "never":
                self.state = "off" if self._power_off() else "dim"
            else:
                self.state = "dim"
            return self.state

    def alert(self):
        if self.settings.get("screen_wake_on_alert"):
            self.wake()

    def tick(self):
        with self.lock:
            if self.settings["screen_mode"] == "never":
                return self.state
            if self.state == "on" and self.clock() - self.last_touch >= self.timeout():
                self.sleep()
            return self.state

    def configure(self, mode=None, timeout_s=None, timeout_battery_s=None, dim_pct=None,
                  wake_on_alert=None):
        with self.lock:
            s = self.settings
            if mode in MODES:
                if mode != s["screen_mode"] and self.state != "on":
                    self.wake()
                s["screen_mode"] = mode
            for key, v in (("screen_timeout_s", timeout_s),
                           ("screen_timeout_battery_s", timeout_battery_s),
                           ("screen_dim_pct", dim_pct)):
                if v is not None:
                    try:
                        s[key] = _clamp(v, key)
                    except (TypeError, ValueError):
                        pass
            if wake_on_alert is not None:
                s["screen_wake_on_alert"] = bool(wake_on_alert)
            self._save()
            return self.fields(settings_only=True)

    def fields(self, settings_only=False):
        s = self.settings
        out = {k: s[k] for k in DEFAULTS}
        if not settings_only:
            out["screen"] = self.state
        return out

    def start(self):
        self.recover()

        def loop():
            while True:
                try:
                    self.tick()
                except Exception as e:
                    print(f"[SCREEN] {e}", flush=True)
                time.sleep(1.0)
        threading.Thread(target=loop, daemon=True).start()

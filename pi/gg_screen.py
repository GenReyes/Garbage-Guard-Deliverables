"""Screen saver for the Pi's own touchscreen.

Off mode switches the HDMI output off with wlr-randr (the cheap 7 inch board
then turns its backlight off); wlopm does not work on this Pi's display
driver. While the output is off the compositor may not pass taps on, so
gg_touch watches the touchscreen directly and wakes it.

Waking is the part that must never fail: a screen left off cannot be brought
back by the person standing in front of it. So wake checks what the screen is
really doing, tries three ways to bring it back, keeps retrying in the
background, and refuses to use Off mode again until someone chooses it, so one
bad wake cannot repeat. Dim mode only reports
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
BROKEN = ("The screen did not come back last time, so Dim is used instead. "
          "Tap Off to try again.")
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
        self.off_broken = False
        self.recovering = False

    def _env(self):
        env = dict(self.environ)
        env.setdefault("WAYLAND_DISPLAY", "wayland-0")
        env.setdefault("XDG_RUNTIME_DIR", f"/run/user/{os.getuid()}")
        return env

    def _outputs(self):
        """[{name, enabled, mode}] from wlr-randr, or None if it cannot be read."""
        ok, text = self._runner(["wlr-randr"], self._env())
        if not ok:
            return None
        rows = []
        for line in text.splitlines():
            if line and not line[0].isspace():
                rows.append({"name": line.split()[0], "enabled": False, "mode": None})
            elif not rows:
                continue
            elif line.strip().startswith("Enabled:"):
                rows[-1]["enabled"] = line.split(":", 1)[1].strip().lower() == "yes"
            elif "current" in line and " px," in line:
                rows[-1]["mode"] = line.strip().split(" px,")[0]
        return rows

    def _enabled_now(self, names):
        """True when every one of these outputs reads as enabled right now."""
        rows = self._outputs()
        if rows is None:
            return None
        state = {r["name"]: r["enabled"] for r in rows}
        return all(state.get(n) for n in names)

    def _try(self, args, why):
        ok, msg = self._runner(["wlr-randr"] + args, self._env())
        if not ok:
            print(f"[SCREEN] {why} failed: {msg or 'wlr-randr returned an error'}", flush=True)
        return ok, msg

    def _power_off(self):
        if self.off_broken:
            self.note = BROKEN
            return False
        if not self.have("wlr-randr"):
            self.note = "wlr-randr is not installed"
            return False
        if self.wake_source is not None and not self.wake_source.ready:
            self.note = "no touchscreen could be read, so a tap could not wake it"
            return False
        rows = self._outputs()
        if rows is None:
            self.note = "the screen could not be read"
            return False
        live = [r for r in rows if r["enabled"]]
        if not live:
            self.note = "no enabled screen found"
            return False
        self.off_outputs = [(r["name"], r["mode"]) for r in live]
        for name, _mode in self.off_outputs:
            ok, msg = self._try(["--output", name, "--off"], f"switching {name} off")
            if not ok:
                self.note = msg or "the screen could not be switched off"
                self._power_on()
                return False
        self.note = None
        return True

    def _power_on(self):
        """Bring the screen back. Tries three ways and checks the result each time."""
        pairs = list(self.off_outputs)
        if not pairs:
            rows = self._outputs() or []
            pairs = [(r["name"], r["mode"]) for r in rows if not r["enabled"]]
        if not pairs:
            self.off_outputs, self.recovering = [], False
            return True
        names = [n for n, _ in pairs]
        if self._enabled_now(names):
            self.off_outputs, self.recovering = [], False
            return True
        for name, mode in pairs:
            plans = [(["--output", name, "--on"], "switching it on")]
            if mode:
                plans.append((["--output", name, "--on", "--mode", mode],
                              f"switching it on at {mode}"))
            plans.append((["--output", name, "--off", "--output", name, "--on"],
                          "switching it off and on again"))
            for args, why in plans:
                self._try(args, why)
                if self._enabled_now([name]) is not False:
                    break
            else:
                self.recovering = True
                self.note = "The screen will not come back on. Trying again."
                print(f"[SCREEN] {name} is still off after three tries", flush=True)
                return False
        self.off_outputs, self.recovering = [], False
        self.note = BROKEN if self.off_broken else None
        return True

    def recover(self):
        """Turn a screen left off (a crash, a failed wake) back on at start-up."""
        if not self.have("wlr-randr"):
            return
        rows = self._outputs()
        if rows and any(not r["enabled"] for r in rows):
            print("[SCREEN] a screen was left off, switching it back on", flush=True)
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
            if self.state == "off" and not self._power_on():
                # The screen is still dark and nobody can reach it. Keep trying,
                # and do not use Off mode again until someone asks for it.
                self.off_broken = True
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
            if self.recovering:
                self._power_on()
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
                if mode == "off":
                    # Choosing Off again is a deliberate retry after a bad wake.
                    self.off_broken = False
                    self.note = None
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
            out["screen_note"] = self.note if (self.off_broken or self.recovering) else None
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

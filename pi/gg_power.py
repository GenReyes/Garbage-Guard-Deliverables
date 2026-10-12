"""Clean shutdown before the UPS battery runs flat.

A sudden power cut can corrupt the SD card and the SQLite history, so when the
Pi has been on battery at or below the chosen percentage for a while, a
countdown starts and the Pi powers itself off when it ends. The charger
coming back, or the Cancel button, stops it.

States reported to the dashboard and the app:
  off         switched off, or the battery gauge cannot be read
  boot_grace  first minutes after boot, nothing is checked
  armed       watching the battery
  counting    countdown running
  paused      cancelled, re-arms when the charger is plugged in or the
              battery rises 5% above the setting, or when switched on again
"""
import subprocess
import threading
import time

DEFAULTS = {"auto_shutdown": True, "auto_shutdown_pct": 15}
PCT_RANGE = (10, 50)
GRACE_S = 180
DEBOUNCE_S = 30
COUNTDOWN_S = 300
FLOOR_PCT = 5
FLOOR_DEBOUNCE_S = 6
FLOOR_WARN_S = 60
REARM_MARGIN = 5
GOOD_READS = 3
RETRY_S = 30
POLL_S = 2.0


def system_uptime():
    try:
        with open("/proc/uptime") as f:
            return float(f.read().split()[0])
    except (OSError, ValueError, IndexError):
        return None


def poweroff():
    for cmd in (["systemctl", "poweroff"], ["sudo", "-n", "/sbin/poweroff"]):
        try:
            r = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
            if r.returncode == 0:
                return True
            print(f"[POWER] {' '.join(cmd)} refused: {(r.stderr or r.stdout).strip()}", flush=True)
        except (OSError, subprocess.TimeoutExpired) as e:
            print(f"[POWER] {' '.join(cmd)} failed: {e}", flush=True)
    return False


class Power:
    def __init__(self, settings, save=None, ups=None, screen=None, shutdown=poweroff,
                 log=None, flush=None, clock=time.monotonic, uptime=system_uptime,
                 spawn=None):
        self.settings = settings
        for k, v in DEFAULTS.items():
            settings.setdefault(k, v)
        self._save = save or (lambda: None)
        self.ups = ups
        self.screen = screen
        self._shutdown = shutdown
        self._log = log
        self._flush = flush
        self.clock = clock
        self._uptime = uptime
        self._spawn = spawn or (lambda f: threading.Thread(target=f, daemon=True).start())
        self.born = clock()
        self.lock = threading.RLock()
        self.state = "off"
        self.floor = False
        self.deadline = None
        self.low_since = None
        self.good_streak = 0
        self.shutting = False
        self.retry_at = 0.0

    # ---------- settings ----------
    def configure(self, on=None, pct=None):
        with self.lock:
            s = self.settings
            if pct is not None:
                try:
                    s["auto_shutdown_pct"] = max(PCT_RANGE[0], min(PCT_RANGE[1], int(pct)))
                except (TypeError, ValueError):
                    pass
            if on is not None:
                s["auto_shutdown"] = bool(on)
                if on and self.state in ("paused", "off"):
                    self._reset("armed")
                elif not on:
                    self._reset("off")
            self._save()
            return self.fields()

    def cancel(self):
        with self.lock:
            if self.floor and self.state == "counting":
                return {"ok": False, "error": "floor",
                        "message": "The battery is almost empty, so this shutdown cannot be cancelled."}
            if self.state == "counting":
                self._reset("paused")
            return {"ok": True, **self.fields()}

    def manual_shutdown(self):
        """Power off now because a person asked (dashboard button or phone).

        The history row is written first and the reply goes back at once; the
        send to Firebase and the power off follow a moment later on their own
        thread. A phone command is answered from inside the cloud loop, which
        holds the lock the send needs, so waiting here would only time out.
        """
        with self.lock:
            if self.shutting:
                return {"ok": True, "already": True}
            self.shutting = True
            print("[POWER] manual shutdown requested", flush=True)
            try:
                if self._log:
                    self._log("manual_shutdown")
            except Exception as e:
                print(f"[POWER] could not record the shutdown: {e}", flush=True)
        self._spawn(self._finish_manual)
        return {"ok": True}

    def _finish_manual(self):
        try:
            if self._flush:
                self._flush(8)
        except Exception as e:
            print(f"[POWER] could not send the shutdown: {e}", flush=True)
        if not self._shutdown():
            print("[POWER] shutdown was refused", flush=True)
            self.shutting = False

    def _reset(self, state):
        self.state = state
        self.deadline = None
        self.floor = False
        self.low_since = None

    # ---------- what the dashboard and app read ----------
    def left(self, now=None):
        if self.state != "counting" or self.deadline is None:
            return None
        now = self.clock() if now is None else now
        return max(0, int(self.deadline - now + 0.999))

    def fields(self, now=None):
        s = self.settings
        return {"auto_shutdown": bool(s["auto_shutdown"]),
                "auto_shutdown_pct": int(s["auto_shutdown_pct"]),
                "shutdown_state": "powering_off" if self.shutting else self.state,
                "shutdown_in_s": self.left(now),
                "shutdown_floor": bool(self.floor and self.state == "counting")}

    # ---------- the state machine ----------
    def _in_grace(self, now):
        up = self._uptime()
        if up is None:
            up = now - self.born
        return up < GRACE_S

    def tick(self):
        with self.lock:
            now = self.clock()
            if not self.settings["auto_shutdown"] or self.ups is None:
                self._reset("off")
                return self.state
            st = self.ups.status()
            pct = st.get("percent")
            if not st.get("ok") or not isinstance(pct, (int, float)):
                self.good_streak = 0
                self._reset("off")
                return self.state
            self.good_streak += 1
            if self.good_streak < GOOD_READS:
                if self.state != "counting":
                    self._reset("off")
                return self.state
            if self.state == "off":
                self.state = "armed"
            if self._in_grace(now):
                self._reset("boot_grace")
                return self.state
            if self.state == "boot_grace":
                self.state = "armed"

            if st.get("on_ac"):
                self._reset("armed")
                return self.state

            limit = int(self.settings["auto_shutdown_pct"])
            if self.state == "paused" and pct >= limit + REARM_MARGIN:
                self._reset("armed")

            if pct <= FLOOR_PCT:
                self._floor(now)
            elif self.state == "armed":
                self._arm_check(now, pct, limit)
            if self.state == "counting":
                if self.screen is not None:
                    self.screen.wake()
                if self.deadline - now <= 0:
                    self._go()
            return self.state

    def _arm_check(self, now, pct, limit):
        if pct > limit:
            self.low_since = None
            return
        self.low_since = self.low_since or now
        if now - self.low_since >= DEBOUNCE_S:
            self.state, self.deadline, self.floor = "counting", now + COUNTDOWN_S, False

    def _floor(self, now):
        if self.state == "counting":
            if not self.floor:
                self.floor = True
                self.deadline = min(self.deadline, now + FLOOR_WARN_S)
            return
        self.low_since = self.low_since or now
        if now - self.low_since >= FLOOR_DEBOUNCE_S:
            self.state, self.floor = "counting", True
            self.deadline = now + FLOOR_WARN_S

    def _go(self):
        now = self.clock()
        if self.shutting or now < self.retry_at:
            return
        self.shutting = True
        print("[POWER] battery low, shutting down", flush=True)
        try:
            if self._log:
                self._log("low_battery_shutdown")
            if self._flush:
                self._flush(8)
        except Exception as e:
            print(f"[POWER] could not record the shutdown: {e}", flush=True)
        ok = self._shutdown()
        if not ok:
            print("[POWER] shutdown was refused; will try again", flush=True)
            self.retry_at = self.clock() + RETRY_S
            self.shutting = False

    def start(self):
        def loop():
            while True:
                try:
                    self.tick()
                except Exception as e:
                    print(f"[POWER] {e}", flush=True)
                time.sleep(POLL_S)
        threading.Thread(target=loop, daemon=True).start()

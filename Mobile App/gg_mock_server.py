"""
Garbage-Guard mock server - for mobile app development without the Pi.

Serves exactly the same API as the real system, with invented data.
No camera, no model, no installs. Python 3 standard library only.

    python3 gg_mock_server.py

Then point the app at http://<this-machine-ip>:8080
"""

import base64
import os
import json
import math
import random
import re
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PORT = 8080
DASHBOARD = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pi", "dashboard.html")

FRAME_B64 = (
    "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAA0JCgsKCA0LCgsODg0PEyAVExISEyccHhcgLikxMC4pLSwzOko+MzZGNywtQFdB"
    "RkxOUlNSMj5aYVpQYEpRUk//2wBDAQ4ODhMREyYVFSZPNS01T09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09P"
    "T09PT09PT09PT09PT0//wAARCAC0AUADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAA"
    "AgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6"
    "Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXG"
    "x8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREA"
    "AgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5"
    "OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPE"
    "xcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDm6KKKACiiigAooooAKKKKACiiigAooooA"
    "KKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooA"
    "KKKKACiiigDp/AEMU+uTrPEki/ZX4dQRyVB6+xI+hNSWfh3b45NjJB/osTGcDqDF1UfN94ZIU9e/1qPwBNFBrk7TypGv2V+X"
    "YAcFSevsCfoDXUWXiCwXSrPUp5k+1XXl20u9wpJVsMSBwANzN24I6ZFAFC6sLC21jXNZv7RJILbYIoyBseRkBORg8ksvJGPm"
    "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"
    "5KfK3fQUHJVHGTvoQ0UUVqbhRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFW4/8AkGS/73+FVKtx/wDIMl/3v8K1"
    "pbv0ZjW2XqipRRRWRsFFFFABVvTf+Phv93+oqpVvTf8Aj4b/AHf6itaH8RGOI/hSKlFFFZGwUUUUAFFFFABWjqX/AB52P/XP"
    "+grOrR1L/jzsf+uf9BWU/jj8/wAjGp/Eh8/yM6iiitTYKKKKALelf8hGL8f5Gorv/j8n/wCujfzqXSv+QjF+P8jUV3/x+T/9"
    "dG/nWX/L35GK/jP0/VkNFFFamwUUUUAFFFFABRRRQAUUUUAFFFFABRWlRSuZ85m0VpUUXDnM2itKii4c5m1chUtp0oUEnd0H"
    "4VNTldl6GrpzUXqZ1G5JWM/yJv8Ank//AHyaPIm/55P/AN8mtHzX9f0o81/X9Kq9Lz/AXtKvZGd5E3/PJ/8Avk0eRN/zyf8A"
    "75NaPmv6/pR5r+v6UXpef4B7Sr2RneRN/wA8n/75NWbCN0nYujKNvUjHcVY81/X9KPMf+9VQnSjJS1Jm6k4uLsZVFaVFYXN+"
    "czaK0qKLhzmbRWlRRcOcza0dS/487H/rn/QUtXWWKeCIGZUKrjBrGpK0otmNWpaUZPp/kYFFbn2WL/n6T9P8aPssX/P0n6f4"
    "0/bw/q4/rUP6uYdFbn2WL/n6T9P8aPssX/P0n6f40e3h/Vw+tQ/q5naV/wAhGL8f5Gorv/j8n/66N/OtmGGKKUP9oQ47cf41"
    "VlIaV2HQsSKUZqVRtdiYVFKo5LsZVFaVFbXN+czaK0qKLhzmbRWlRRcOczaK0qKLhzmbRWlRRcOczaK0qKLhzhRRRUkBRRRQ"
    "AUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQ"
    "AUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQ"
    "AUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQ"
    "AUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQ"
    "AUUUUAFFFFABRRRQAUUUUAFFFFABRRRQB//Z"
)
FRAME = base64.b64decode("".join(FRAME_B64))

STATE = {
    "conf": 0.40,
    "threshold": 20,
    "roi": [],
    "started": time.time(),
    "cam_off_until": 0.0,
    "reconnects": 0,
    "mute_until": 0.0,
    "mute_total": 0,
    "auto_mute": False,
    "auto_mute_s": 900,
    "auto_muted": False,
    "in_alert": False,
    # Screen saver (pi/gg_screen.py). The mock has no real screen; it only keeps the state.
    "screen_mode": "off",
    "screen_timeout_s": 600,
    "screen_timeout_battery_s": 60,
    "screen_dim_pct": 20,
    "screen_wake_on_alert": True,
    "screen": "on",
    "screen_touched": time.time(),
    # Low-battery shutdown (pi/gg_power.py).
    "auto_shutdown": True,
    "auto_shutdown_pct": 15,
    "shutdown_paused": False,
    "low_until": 0.0,        # a pretend low battery lasts until this time
    "low_floor": False,      # the pretend low battery is the 5% one
    "count_until": 0.0,      # the countdown ends at this time
    "powering_off_until": 0.0,
    "off_until": 0.0,        # the pretend Pi is switched off until this time
    # Pi buzzer (Raspberry Pi 5/gg_buzzer.py). The mock only keeps the settings.
    "buzzer_on": True,
    "buzzer_volume": "medium",
    "buzzer_alerts": True,
    "buzzer_countdown": True,
    "buzzer_ui": False,
    "buzzer_repeat": True,
    "buzzer_repeat_s": 30,
    "quiet_on": False,
    "quiet_from": "22:00",
    "quiet_to": "06:00",
    # Pi vibration motor (Raspberry Pi 5/gg_vibe.py).
    "vibe_on": True,
    "vibe_strength": "medium",
}

BUZZER_SOUNDS = ("accumulation", "shutdown_low", "countdown", "test", "auto_muted",
                 "shutdown_manual", "manual", "muted", "unmuted", "ready", "tap", "switch")
BUZZER_KEYS = ("buzzer_on", "buzzer_volume", "buzzer_alerts", "buzzer_countdown", "buzzer_ui",
               "buzzer_repeat", "buzzer_repeat_s", "quiet_on", "quiet_from", "quiet_to")
NOT_SYNCED = "The Pi's clock is not synchronised, so quiet hours are not applied."


def _minutes(text):
    try:
        h, m = str(text).split(":")
        h, m = int(h), int(m)
    except (ValueError, TypeError):
        return None
    return h * 60 + m if 0 <= h < 24 and 0 <= m < 60 else None


def buzzer_fields():
    """Same fields as the Pi. GG_MOCK_NOBUZZER=1 pretends the buzzer cannot start,
    GG_MOCK_CLOCK_OFF=1 pretends the Pi's clock is not synchronised."""
    synced = not os.environ.get("GG_MOCK_CLOCK_OFF")
    nobuzz = bool(os.environ.get("GG_MOCK_NOBUZZER"))
    quiet = False
    start, end = _minutes(STATE["quiet_from"]), _minutes(STATE["quiet_to"])
    if STATE["quiet_on"] and synced and start is not None and end is not None and start != end:
        try:
            from zoneinfo import ZoneInfo
            now = datetime.now(ZoneInfo("Asia/Manila"))
        except Exception:
            now = datetime.now(timezone(timedelta(hours=8)))
        n = now.hour * 60 + now.minute
        quiet = (start <= n < end) if start < end else (n >= start or n < end)
    out = {k: STATE[k] for k in BUZZER_KEYS}
    out.update({
        "vibe_on": STATE["vibe_on"],
        "vibe_strength": STATE["vibe_strength"],
        "vibe_available": not os.environ.get("GG_MOCK_NOVIBE"),
        "vibe_note": "no hardware PWM found; add the pwm-2chan overlay to config.txt" if os.environ.get("GG_MOCK_NOVIBE") else None,
        "buzzer_available": not nobuzz,
        "buzzer_note": "the lgpio library is not installed" if nobuzz else None,
        "quiet_now": quiet,
        "quiet_note": NOT_SYNCED if (STATE["quiet_on"] and not synced) else None,
        "clock_synced": synced,
        "clock_settling": False,
        "clock_zone": "Asia/Manila",
    })
    return out


def screen_now():
    """What the pretend screen is doing, after applying the idle timer."""
    if STATE["screen"] == "on" and STATE["screen_mode"] != "never":
        on_battery = not build_battery_core()["on_ac"]
        limit = STATE["screen_timeout_battery_s"] if on_battery else STATE["screen_timeout_s"]
        if time.time() - STATE["screen_touched"] > limit:
            STATE["screen"] = "dim" if STATE["screen_mode"] == "dim" else "off"
    return STATE["screen"]


def wake_screen():
    STATE["screen"] = "on"
    STATE["screen_touched"] = time.time()


def shutdown_fields():
    """The low-battery state machine, cut down to what the app needs to see."""
    now = time.time()
    low = now < STATE["low_until"]
    if now < STATE["powering_off_until"]:
        state = "powering_off"
    elif not STATE["auto_shutdown"] and not (low and STATE["low_floor"]):
        state = "off"
    elif low and STATE["shutdown_paused"] and not STATE["low_floor"]:
        state = "paused"
    elif low:
        state = "counting"
        if STATE["count_until"] <= now:
            # The countdown ran out: write the row, then go dark for a while.
            add_row("low_battery_shutdown", 0, 0, 0, 0, None, datetime.now(), False)
            STATE["low_until"] = 0.0
            STATE["powering_off_until"] = now + 4
            STATE["off_until"] = now + 4 + 25
            state = "powering_off"
    else:
        STATE["shutdown_paused"] = False
        state = "armed"
    left = int(STATE["count_until"] - now) if state == "counting" else None
    return {
        "auto_shutdown": STATE["auto_shutdown"],
        "auto_shutdown_pct": STATE["auto_shutdown_pct"],
        "shutdown_state": state,
        "shutdown_in_s": max(0, left) if left is not None else None,
        "shutdown_floor": bool(low and STATE["low_floor"]),
    }


def switched_off():
    """True while the pretend Pi is powered off. It boots again by itself so testing can go on."""
    now = time.time()
    return STATE["powering_off_until"] <= now < STATE["off_until"]


def muted():
    return STATE["mute_until"] == float("inf") or STATE["mute_until"] > time.time()


def mute_left():
    if STATE["mute_until"] == float("inf"):
        return None
    left = STATE["mute_until"] - time.time()
    return int(left) if left > 0 else 0

LOG = []
VIEWERS = {}
NEXT_ID = [1]
SNAPSHOT_NAME = re.compile(r"^[A-Za-z0-9_.-]+\.jpg$")


def add_row(event, total, b, g, c, peak, when, with_image=True):
    rid = NEXT_ID[0]
    NEXT_ID[0] += 1
    LOG.insert(0, {
        "id": rid,
        "timestamp": when.isoformat(sep=" ", timespec="seconds"),
        "event": event,
        "smoothed_count": total,
        "raw_count": total,
        "n_bottle": b,
        "n_bag": g,
        "n_food_container": c,
        "peak_confidence": peak,
        "threshold_used": STATE["threshold"],
        "snapshot_path": f"/home/garbageguard/gg/snapshots/{event}_{rid}.jpg" if with_image else None,
        "snapshot": f"{event}_{rid}.jpg" if with_image else None,
        "read": 0,
    })


def seed_log():
    now = datetime.now()
    rows = [
        ("test", 0, 0, 0, 0, None, 140, False),
        ("accumulation", 20, 7, 5, 8, 0.88, 96, True),
        ("manual", 9, 4, 2, 3, 0.74, 52, True),
        ("suppressed", 4, 2, 1, 1, 0.55, 39, True),
        ("accumulation", 22, 9, 6, 7, 0.91, 14, True),
    ]
    for event, total, b, g, c, peak, mins, img in rows:
        add_row(event, total, b, g, c, peak, now - timedelta(minutes=mins), img)


def fake_counts():
    """Counts that drift up and down so the app has something moving."""
    t = time.time() - STATE["started"]
    base = 11 + 9 * math.sin(t / 25.0)
    bottle = max(0, int(base * 0.45 + random.randint(-1, 1)))
    bag = max(0, int(base * 0.25 + random.randint(-1, 1)))
    cont = max(0, int(base * 0.30 + random.randint(-1, 1)))
    return {"bottle": bottle, "bag": bag, "food_container": cont}


def build_state():
    counts = fake_counts()
    total = sum(counts.values())
    latency = round(random.uniform(62, 78), 1)
    active = total >= STATE["threshold"]
    if active and not STATE["in_alert"] and STATE["auto_mute"] and not muted():
        secs = STATE["auto_mute_s"]
        STATE["mute_total"] = secs
        STATE["mute_until"] = time.time() + secs if secs else float("inf")
        STATE["auto_muted"] = True
        add_row("auto_muted", total, 0, 0, 0, None, datetime.now(), False)
    STATE["in_alert"] = active
    return {
        "connected": time.time() >= STATE["cam_off_until"] and not os.environ.get("GG_MOCK_NOCAM"),
        "reconnects": STATE["reconnects"],
        "counts": counts,
        "raw": total,
        "smoothed": total,
        "threshold": STATE["threshold"],
        "rearm": max(0, STATE["threshold"] - max(1, STATE["threshold"] // 4)),
        "conf": STATE["conf"],
        "roi": STATE["roi"],
        "latency_ms": latency,
        "avg_latency_ms": 68.2,
        "fps": round(1000.0 / latency, 1),
        "cam_fps": 14.9,
        "alert_active": total >= STATE["threshold"],
        "muted": muted(),
        "mute_left": mute_left(),
        "mute_total": STATE["mute_total"],
        "auto_muted": STATE["auto_muted"] and muted(),
        "auto_mute": STATE["auto_mute"],
        "auto_mute_s": STATE["auto_mute_s"],
        "frames": int((time.time() - STATE["started"]) * 14),
        "uptime_s": int(time.time() - STATE["started"]),
        "screen_mode": STATE["screen_mode"],
        "screen_timeout_s": STATE["screen_timeout_s"],
        "screen_timeout_battery_s": STATE["screen_timeout_battery_s"],
        "screen_dim_pct": STATE["screen_dim_pct"],
        "screen_wake_on_alert": STATE["screen_wake_on_alert"],
        "screen": screen_now(),
        "screen_note": None,
        **buzzer_fields(),
    }


def build_net_details():
    """Invented adapters in the shape gg_net.details() returns on the Pi."""
    return {
        "backend": "mock", "internet": True, "default_dev": "wlan0",
        "camera_ip": "192.168.8.64", "local": False,
        "interfaces": [
            {"device": "wlan0", "type": "wifi", "role": "wifi", "state": "connected",
             "connection": "Mock Wi-Fi", "ips": ["192.168.1.101/24"], "gateway": "192.168.1.1",
             "dns": ["192.168.1.1", "8.8.8.8"], "mac": "D8:3A:DD:00:00:01", "speed": None,
             "method": "auto", "default": True,
             "link": {"ssid": "Mock Wi-Fi", "signal": 74, "rate": "270 Mbit/s", "channel": "36",
                      "freq": "5180 MHz", "security": "WPA2", "bssid": "AA:BB:CC:DD:EE:FF"}},
            {"device": "eth0", "type": "ethernet", "role": "camera", "state": "connected",
             "connection": "camera-link", "ips": ["192.168.8.10/24"], "gateway": None,
             "dns": [], "mac": "D8:3A:DD:00:00:02", "speed": "100 Mb/s",
             "method": "manual", "default": False},
        ],
    }


def build_battery():
    """The UPS readout plus the low-battery shutdown fields, as /api/battery gives them."""
    out = build_battery_core()
    out.update(shutdown_fields())
    return out


def build_battery_core():
    """Invented UPS HAT readout, same fields as pi/gg_ups.py. Two minutes on
    battery, then two minutes charging, so both looks can be seen. During a
    pretend low battery (POST /api/mock/low_battery) it sits just under the
    shutdown threshold, on battery."""
    t = time.time()
    phase = int(t // 120) % 2
    step = (t % 120) / 120.0
    charging = phase == 1
    percent = int(round(58 + 30 * step)) if charging else int(round(88 - 30 * step))
    if t < STATE["low_until"] or t < STATE["powering_off_until"]:
        charging = False
        percent = 4 if STATE["low_floor"] else max(1, STATE["auto_shutdown_pct"] - 1)
    volts = round(14.2 + 2.4 * percent / 100.0, 3)
    amps = round(1.15 + 0.1 * math.sin(t / 3), 3) if charging else round(-0.78 - 0.08 * math.sin(t / 3), 3)
    cell = round(volts / 4, 3)
    return {
        "ok": True,
        "percent": percent,
        "voltage_v": volts,
        "current_a": amps,
        "power_w": round(volts * amps, 2),
        "capacity_mah": int(5000 * percent / 100),
        "minutes_left": int((100 - percent) * 2.1) if charging else int(percent * 4.3),
        "on_ac": charging,
        "charging": charging,
        "state": "charging" if charging else "discharging",
        "vbus_v": 15.1 if charging else 0.0,
        "vbus_a": 2.2 if charging else 0.0,
        "vbus_w": 33.2 if charging else 0.0,
        "cells_v": [cell, round(cell + 0.004, 3), round(cell - 0.006, 3), round(cell + 0.002, 3)],
        "t": t,
    }


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def _send(self, code, ctype, body):
        if isinstance(body, str):
            body = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _json(self, obj):
        self._send(200, "application/json", json.dumps(obj))

    def _json_status(self, code, obj):
        self._send(code, "application/json", json.dumps(obj))

    def _body(self):
        n = int(self.headers.get("Content-Length") or 0)
        if not n:
            return {}
        try:
            return json.loads(self.rfile.read(n) or b"{}")
        except json.JSONDecodeError:
            return {}

    def _viewer_key(self):
        ua = self.headers.get("User-Agent") or ""
        tag = (self.headers.get("X-GG-Client") or "").lower()
        kind = "app" if (tag in ("android", "app", "mobile", "ios") or (not tag and ("Dalvik" in ua or "okhttp" in ua))) else "browser"
        cid = self.headers.get("X-GG-Client-Id")
        return (cid or f"{self.client_address[0]}|{kind}"), kind

    def do_OPTIONS(self):
        self._send(204, "text/plain", b"")

    def do_GET(self):
        path = urlparse(self.path).path
        q = parse_qs(urlparse(self.path).query)
        if switched_off():
            # A Pi that is powered off answers nothing. The mock says so with a 503.
            return self._send(503, "text/plain", "powered off")

        if path == "/api/state":
            ip = self.client_address[0]
            if ip not in ("127.0.0.1", "::1"):
                key, kind = self._viewer_key()
                old = VIEWERS.get(key)
                now = time.time()
                since = old["since"] if old and now - old["seen"] <= 4 else now
                VIEWERS[key] = {"ip": ip, "kind": kind, "since": since, "seen": now,
                                "hits": (old["hits"] + 1) if old and since == old["since"] else 1,
                                "device": self.headers.get("X-GG-Device"), "os": self.headers.get("X-GG-OS"),
                                "app_version": self.headers.get("X-GG-App-Version"),
                                "mode": self.headers.get("X-GG-Mode"), "ua": self.headers.get("User-Agent")}
            st = build_state()
            now = time.time()
            rows = [{"ip": v["ip"], "kind": v["kind"], "id": k if "|" not in k else None,
                     "device": v["device"], "os": v["os"], "browser": None, "app_version": v["app_version"],
                     "mode": v["mode"], "since_s": int(now - v["since"]), "age_s": round(now - v["seen"], 1),
                     "hits": v["hits"], "ua": v["ua"], "mac": None, "hostname": None}
                    for k, v in VIEWERS.items() if now - v["seen"] <= 4]
            st["viewers"] = {"apps": sum(r["kind"] == "app" for r in rows),
                             "browsers": sum(r["kind"] == "browser" for r in rows), "list": rows}
            return self._json(st)
        if path == "/api/battery":
            return self._json(build_battery())
        if path == "/api/net/details":
            return self._json(build_net_details())
        if path == "/api/alerts":
            limit = int((q.get("limit") or [50])[0])
            return self._json({"alerts": LOG[:limit]})
        if path == "/frame.jpg":
            return self._send(200, "image/jpeg", FRAME)
        if path.startswith("/snapshots/"):
            name = path[len("/snapshots/"):]
            known = {r["snapshot"] for r in LOG if r["snapshot"]}
            if SNAPSHOT_NAME.match(name) and name in known:
                return self._send(200, "image/jpeg", FRAME)
            return self._send(404, "text/plain", "not found")
        if path in ("/", "/index.html") and os.path.exists(DASHBOARD):
            with open(DASHBOARD, "rb") as f:
                return self._send(200, "text/html; charset=utf-8", f.read())
        if path == "/api/net":
            return self._json({"local": False, "has_wifi_hw": False})
        if path in ("/", "/index.html"):
            return self._send(200, "text/html",
                              "<h2>Garbage-Guard mock server</h2>"
                              "<p>API is live. Endpoints: /api/state, /api/alerts, "
                              "/frame.jpg</p>")
        return self._send(404, "text/plain", "not found")

    def do_POST(self):
        path = urlparse(self.path).path
        data = self._body()
        # Optional: answer commands slowly, to see how the app waits for the Pi.
        # Set GG_MOCK_POST_DELAY to a number of seconds before starting the server.
        # The app gives up after about 4 seconds on Wi-Fi, so 2 shows a wait and 6 shows a failure.
        slow = float(os.environ.get("GG_MOCK_POST_DELAY") or 0)
        if slow > 0:
            time.sleep(slow)
        if switched_off():
            return self._send(503, "text/plain", "powered off")

        if path == "/api/settings":
            conf = data.get("conf")
            if conf is not None:
                conf = float(conf)
                STATE["conf"] = conf / 100.0 if conf > 1 else conf
            if data.get("threshold") is not None:
                STATE["threshold"] = max(1, int(data["threshold"]))
            return self._json({"ok": True, "settings": {
                "conf": STATE["conf"],
                "threshold": STATE["threshold"],
                "roi": STATE["roi"],
            }})

        if path == "/api/client/bye":
            VIEWERS.pop(self._viewer_key()[0], None)
            return self._json({"ok": True})

        if path == "/api/camera/reconnect":
            STATE["cam_off_until"] = time.time() + 3
            STATE["reconnects"] += 1
            return self._json({"ok": True})

        if path == "/api/restart":
            STATE["started"] = time.time()
            STATE["cam_off_until"] = time.time() + 4
            return self._json({"ok": True})

        if path == "/api/roi":
            pts = data.get("points") or []
            STATE["roi"] = [[float(p[0]), float(p[1])] for p in pts] if len(pts) >= 3 else []
            return self._json({"ok": True, "points": STATE["roi"]})

        if path == "/api/mute":
            was = muted()
            STATE["auto_muted"] = False
            if data.get("cancel"):
                STATE["mute_until"] = 0.0
                STATE["mute_total"] = 0
            elif data.get("seconds") in (0, None):
                STATE["mute_until"] = float("inf")
                STATE["mute_total"] = 0
            else:
                STATE["mute_total"] = int(data["seconds"])
                STATE["mute_until"] = time.time() + int(data["seconds"])
            now = muted()
            if now != was:
                add_row("muted" if now else "unmuted", 0, 0, 0, 0, None,
                        datetime.now(), False)
            return self._json({"ok": True, "muted": now, "mute_left": mute_left()})

        if path == "/api/auto_mute":
            if data.get("on") is not None:
                STATE["auto_mute"] = bool(data["on"])
            if data.get("seconds") is not None:
                STATE["auto_mute_s"] = max(0, int(data["seconds"]))
            return self._json({"ok": True, "auto_mute": STATE["auto_mute"],
                               "auto_mute_s": STATE["auto_mute_s"]})

        if path == "/api/alerts/read":
            ids = data.get("ids")
            val = 0 if data.get("read") is False else 1
            n = 0
            for r in LOG:
                if data.get("all") or (isinstance(ids, list) and r["id"] in ids):
                    r["read"] = val
                    n += 1
            return self._json({"ok": True, "changed": n})

        if path == "/api/alerts/delete":
            ids = data.get("ids")
            dfrom, dto = data.get("from"), data.get("to")
            keep, gone = [], 0
            for r in LOG:
                hit = False
                if data.get("all"):
                    hit = True
                elif isinstance(ids, list) and r["id"] in ids:
                    hit = True
                elif dfrom or dto:
                    day = r["timestamp"][:10]
                    hit = (not dfrom or day >= dfrom) and (not dto or day <= dto)
                if hit:
                    gone += 1
                else:
                    keep.append(r)
            files = sum(1 for r in LOG if r not in keep and r["snapshot"])
            LOG[:] = keep
            return self._json({"ok": True, "deleted": gone, "files_removed": files})

        if path == "/api/buzzer":
            for key, name in (("buzzer_on", "on"), ("buzzer_alerts", "alerts"), ("buzzer_countdown", "countdown"),
                              ("buzzer_ui", "ui"), ("buzzer_repeat", "repeat"), ("quiet_on", "quiet_on")):
                if data.get(name) is not None:
                    STATE[key] = bool(data[name])
            if data.get("volume") in ("low", "medium", "high"):
                STATE["buzzer_volume"] = data["volume"]
            if data.get("repeat_s") is not None:
                try:
                    STATE["buzzer_repeat_s"] = min(300, max(10, int(data["repeat_s"])))
                except (TypeError, ValueError):
                    pass
            for key, name in (("quiet_from", "quiet_from"), ("quiet_to", "quiet_to")):
                m = _minutes(data.get(name)) if data.get(name) is not None else None
                if m is not None:
                    STATE[key] = f"{m // 60:02d}:{m % 60:02d}"
            return self._json({"ok": True, **buzzer_fields()})

        if path == "/api/buzzer/test":
            if data.get("sound") not in BUZZER_SOUNDS:
                return self._json_status(400, {"ok": False, "error": "bad_sound"})
            if os.environ.get("GG_MOCK_NOBUZZER"):
                return self._json_status(409, {"ok": False, "error": "unavailable",
                                               "message": "the lgpio library is not installed"})
            print(f"[BUZZER] would play {data['sound']}", flush=True)
            return self._json({"ok": True})

        if path == "/api/vibe":
            if data.get("on") is not None:
                STATE["vibe_on"] = bool(data["on"])
            if data.get("strength") in ("low", "medium", "high"):
                STATE["vibe_strength"] = data["strength"]
            return self._json({"ok": True, **{k: v for k, v in buzzer_fields().items() if k.startswith("vibe_")}})

        if path == "/api/vibe/test":
            if data.get("pattern", "test") not in ("accumulation", "shutdown_low", "countdown", "test",
                                                    "auto_muted", "shutdown_manual", "manual"):
                return self._json_status(400, {"ok": False, "error": "bad_pattern"})
            if os.environ.get("GG_MOCK_NOVIBE"):
                return self._json_status(409, {"ok": False, "error": "unavailable",
                                               "message": "no hardware PWM found; add the pwm-2chan overlay to config.txt"})
            print("[VIBE] would vibrate", flush=True)
            return self._json({"ok": True})

        if path == "/api/buzzer/ui":
            if self.client_address[0] not in ("127.0.0.1", "::1"):
                return self._json_status(403, {"ok": False, "error": "local_only"})
            return self._json({"ok": True, "played": False})

        if path == "/api/screen_saver":
            if data.get("mode") in ("off", "dim", "never"):
                STATE["screen_mode"] = data["mode"]
                wake_screen()  # changing the mode wakes a sleeping screen
            if data.get("timeout_s") is not None:
                STATE["screen_timeout_s"] = min(3600, max(60, int(data["timeout_s"])))
            if data.get("timeout_battery_s") is not None:
                STATE["screen_timeout_battery_s"] = min(3600, max(30, int(data["timeout_battery_s"])))
            if data.get("dim_pct") is not None:
                STATE["screen_dim_pct"] = min(50, max(5, int(data["dim_pct"])))
            if data.get("wake_on_alert") is not None:
                STATE["screen_wake_on_alert"] = bool(data["wake_on_alert"])
            return self._json({"ok": True, **{k: STATE[k] for k in (
                "screen_mode", "screen_timeout_s", "screen_timeout_battery_s",
                "screen_dim_pct", "screen_wake_on_alert")}})

        if path == "/api/screen":
            action = data.get("action")
            if action == "wake":
                wake_screen()
            elif action == "sleep":
                STATE["screen"] = "dim" if STATE["screen_mode"] == "dim" else "off"
            else:
                return self._send(400, "application/json", json.dumps({"ok": False, "error": "bad_action"}))
            return self._json({"ok": True, "screen": STATE["screen"]})

        if path == "/api/auto_shutdown":
            if data.get("cancel"):
                if time.time() < STATE["low_until"] and STATE["low_floor"]:
                    return self._send(409, "application/json", json.dumps({"ok": False, "error": "floor"}))
                STATE["shutdown_paused"] = True
            if data.get("on") is not None:
                STATE["auto_shutdown"] = bool(data["on"])
                if STATE["auto_shutdown"] and STATE["shutdown_paused"]:
                    # Switching it on again re-arms a paused shutdown.
                    STATE["shutdown_paused"] = False
                    STATE["count_until"] = time.time() + 300
            if data.get("pct") is not None:
                STATE["auto_shutdown_pct"] = min(50, max(10, int(data["pct"])))
            return self._json({"ok": True, **shutdown_fields()})

        if path == "/api/shutdown":
            if data.get("confirm") is not True:
                return self._send(400, "application/json", json.dumps({"ok": False, "error": "confirm_required"}))
            if time.time() < STATE["powering_off_until"]:
                return self._json({"ok": True, "already": True})
            add_row("manual_shutdown", 0, 0, 0, 0, None, datetime.now(), False)
            STATE["powering_off_until"] = time.time() + 6
            STATE["off_until"] = time.time() + 6 + 25
            return self._json({"ok": True})

        # Mock only: pretend the battery has just dropped under the threshold,
        # so the countdown, Cancel and the shutdown row can be tested with no Pi.
        # Body (all optional): {"seconds": 300, "floor": false}. The real Pi
        # counts 300 seconds, or 60 at the 5% floor.
        if path == "/api/mock/low_battery":
            floor = bool(data.get("floor"))
            seconds = int(data.get("seconds") or (60 if floor else 300))
            STATE["low_floor"] = floor
            STATE["shutdown_paused"] = False
            STATE["count_until"] = time.time() + seconds
            STATE["low_until"] = time.time() + seconds + 120
            wake_screen()
            return self._json({"ok": True, **shutdown_fields()})

        if path in ("/api/test_alert", "/api/capture"):
            s = build_state()
            c = s["counts"]
            event = "test" if path.endswith("test_alert") else "manual"
            add_row(event, s["smoothed"], c["bottle"], c["bag"],
                    c["food_container"], 0.77, datetime.now())
            return self._json({"ok": True})

        return self._send(404, "text/plain", "not found")


if __name__ == "__main__":
    seed_log()
    print("=" * 56)
    print(" GARBAGE-GUARD MOCK SERVER")
    print("=" * 56)
    print(f" http://localhost:{PORT}/api/state")
    print(f" http://localhost:{PORT}/api/alerts")
    print(f" http://localhost:{PORT}/frame.jpg")
    print(f" http://localhost:{PORT}/snapshots/<name from /api/alerts>")
    print(" From a phone, use this machine's LAN IP instead of localhost.")
    print(" Ctrl+C to stop.")
    print("=" * 56)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()

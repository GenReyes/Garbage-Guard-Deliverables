"""
Garbage-Guard mock server - for mobile app development without the Pi.

Serves exactly the same API as the real system, with invented data.
No camera, no model, no installs. Python 3 standard library only.

    python3 gg_mock_server.py

Then point the app at http://<this-machine-ip>:8080
"""

import base64
import json
import math
import random
import re
import time
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PORT = 8080

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
    "mute_until": 0.0,
    "mute_total": 0,
    "auto_mute": False,
    "auto_mute_s": 900,
    "auto_muted": False,
    "in_alert": False,
}


def muted():
    return STATE["mute_until"] == float("inf") or STATE["mute_until"] > time.time()


def mute_left():
    if STATE["mute_until"] == float("inf"):
        return None
    left = STATE["mute_until"] - time.time()
    return int(left) if left > 0 else 0

LOG = []
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
        "connected": True,
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

    def _body(self):
        n = int(self.headers.get("Content-Length") or 0)
        if not n:
            return {}
        try:
            return json.loads(self.rfile.read(n) or b"{}")
        except json.JSONDecodeError:
            return {}

    def do_OPTIONS(self):
        self._send(204, "text/plain", b"")

    def do_GET(self):
        path = urlparse(self.path).path
        q = parse_qs(urlparse(self.path).query)

        if path == "/api/state":
            return self._json(build_state())
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
        if path in ("/", "/index.html"):
            return self._send(200, "text/html",
                              "<h2>Garbage-Guard mock server</h2>"
                              "<p>API is live. Endpoints: /api/state, /api/alerts, "
                              "/frame.jpg</p>")
        return self._send(404, "text/plain", "not found")

    def do_POST(self):
        path = urlparse(self.path).path
        data = self._body()

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

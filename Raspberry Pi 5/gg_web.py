import ipaddress
import json
import os
import re
import socket
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import gg_cloud
import gg_net
import gg_buzzer
import gg_clean
import gg_clock
import gg_power
import gg_screen
import gg_ups
import gg_vibe
import gg_window

BASE_DIR = Path(__file__).resolve().parent
STREAM_FPS = 12
SNAPSHOT_NAME = re.compile(r"^[A-Za-z0-9_.-]+\.jpg$")


LOOPBACK = ("127.0.0.1", "::1", "::ffff:127.0.0.1")
VIEWER_TTL = 4.0

_viewers = {}
_viewers_lock = threading.Lock()
_names = {}

UA_ANDROID = re.compile(r"Android ([\d.]+)(?:; ([^;)]+?))?(?: Build/|\)|;)")
UA_IOS = re.compile(r"(iPhone|iPad).*?OS ([\d_]+)")


def client_kind(headers):
    tag = (headers.get("X-GG-Client") or "").lower()
    if tag:
        return "app" if tag in ("android", "app", "mobile", "ios") else "browser"
    ua = headers.get("User-Agent") or ""
    return "app" if ("Dalvik" in ua or "okhttp" in ua) else "browser"


def describe_ua(ua):
    ua = ua or ""
    os_name, model, browser = None, None, None
    m = UA_ANDROID.search(ua)
    if m:
        os_name = "Android " + m.group(1)
        if m.group(2) and m.group(2).strip() not in ("K", "U", "wv"):
            model = m.group(2).strip()
    elif UA_IOS.search(ua):
        m = UA_IOS.search(ua)
        os_name, model = "iOS " + m.group(2).replace("_", "."), m.group(1)
    elif "Windows" in ua:
        os_name = "Windows"
    elif "Mac OS X" in ua:
        os_name = "macOS"
    elif "Linux" in ua:
        os_name = "Linux"
    for key, name in (("Edg/", "Edge"), ("Firefox/", "Firefox"), ("Chrome/", "Chrome"),
                      ("Safari/", "Safari"), ("Dalvik", "Android app")):
        if key in ua:
            browser = name
            break
    return os_name, model, browser


TAILNET = ipaddress.ip_network("100.64.0.0/10")


def link_kind(ip):
    """How a client reaches the Pi, judged from its address alone."""
    try:
        a = ipaddress.ip_address(ip.replace("::ffff:", ""))
    except ValueError:
        return "remote"
    if a.version == 4 and a in TAILNET:
        return "remote"
    if a.is_private or a.is_link_local:
        return "local"
    return "remote"


def mac_for(ip):
    try:
        with open("/proc/net/arp") as f:
            for line in f.read().splitlines()[1:]:
                parts = line.split()
                if parts and parts[0] == ip and parts[3] != "00:00:00:00:00:00":
                    return parts[3].upper()
    except OSError:
        pass
    return None


def _lookup_name(ip):
    try:
        name = socket.gethostbyaddr(ip)[0]
    except OSError:
        name = None
    _names[ip] = (name, time.time())


def hostname_for(ip):
    hit = _names.get(ip)
    if hit is None or time.time() - hit[1] > 600:
        _names[ip] = (hit[0] if hit else None, time.time())
        threading.Thread(target=_lookup_name, args=(ip,), daemon=True).start()
    return _names[ip][0]


def note_viewer(ip, kind, now=None, headers=None):
    now = time.time() if now is None else now
    headers = headers or {}
    cid = (headers.get("X-GG-Client-Id") or "")[:64]
    key = cid or f"{ip}|{kind}"
    with _viewers_lock:
        row = _viewers.get(key)
        if row is None or now - row["seen"] > VIEWER_TTL:
            row = {"since": now, "hits": 0}
            _viewers[key] = row
        row.update(ip=ip, kind=kind, seen=now, id=cid or None)
        row["hits"] += 1
        for field, name in (("X-GG-Device", "device"), ("X-GG-OS", "os"), ("X-GG-App-Version", "app_version"),
                            ("X-GG-Mode", "mode"), ("User-Agent", "ua")):
            if headers.get(field):
                row[name] = str(headers.get(field))[:160]


def forget_viewer(ip, kind, cid=None):
    with _viewers_lock:
        _viewers.pop(cid or f"{ip}|{kind}", None)


def viewers(now=None):
    now = time.time() if now is None else now
    with _viewers_lock:
        for k in [k for k, r in _viewers.items() if now - r["seen"] > VIEWER_TTL]:
            del _viewers[k]
        rows = []
        for r in sorted(_viewers.values(), key=lambda r: (r["kind"] != "app", r["since"])):
            os_name, model, browser = describe_ua(r.get("ua"))
            rows.append({"ip": r["ip"], "kind": r["kind"], "id": r.get("id"),
                         "device": r.get("device") or model, "os": r.get("os") or os_name,
                         "browser": None if r["kind"] == "app" else browser,
                         "app_version": r.get("app_version"), "mode": r.get("mode"),
                         "since_s": int(now - r["since"]), "age_s": round(now - r["seen"], 1),
                         "hits": r["hits"], "ua": r.get("ua")})
    shared = {}
    for row in rows:
        shared[row["ip"]] = shared.get(row["ip"], 0) + 1
    for row in rows:
        kind = link_kind(row["ip"])
        if row["ip"] == "Firebase":
            row["via"], row["mac"] = "cloud", None
        elif kind == "remote":
            row["via"], row["mac"] = "remote", None
        else:
            row["via"] = "shared" if shared[row["ip"]] > 1 else "direct"
            row["mac"] = mac_for(row["ip"])
        row["hostname"] = None if row["via"] == "cloud" else hostname_for(row["ip"])
    return {"apps": sum(r["kind"] == "app" for r in rows),
            "browsers": sum(r["kind"] == "browser" for r in rows), "list": rows}


def make_handler(engine, net=None, ups=None, screen=None, power=None, clock=None, buzzer=None, vibe=None,
                 clean=None):
    net = net or gg_net.make_net()
    ups = ups or gg_ups.Ups()
    screen = screen or gg_screen.Screen(engine.settings, ups=ups)
    power = power or gg_power.Power(engine.settings, ups=ups, screen=screen)
    clock = clock or gg_clock.Clock()
    buzzer = buzzer or gg_buzzer.Buzzer(engine.settings, backend=gg_buzzer.FakeBackend())
    vibe = vibe or gg_vibe.Vibe(engine.settings, backend=gg_buzzer.FakeBackend())
    clean = clean or getattr(engine, "clean", None) or gg_clean.Clean(
        engine.settings, getattr(engine, "snapshot_dir", "snapshots"))

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, *args):
            pass

        def _send(self, code, ctype, body, extra=None):
            if isinstance(body, str):
                body = body.encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            for k, v in (extra or {}).items():
                self.send_header(k, v)
            self.end_headers()
            try:
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError):
                pass

        def _json(self, obj, code=200):
            self._send(code, "application/json", json.dumps(obj))

        def _body(self):
            n = int(self.headers.get("Content-Length") or 0)
            if not n:
                return {}
            try:
                return json.loads(self.rfile.read(n) or b"{}")
            except json.JSONDecodeError:
                return {}

        def _local(self):
            if self.client_address[0] in LOOPBACK:
                return True
            self._json({"ok": False, "error": "local_only",
                        "message": "Wi-Fi can only be changed on the Pi's own screen."}, 403)
            return False

        def do_GET(self):
            path = urlparse(self.path).path
            query = parse_qs(urlparse(self.path).query)

            if path in ("/start", "/wifi"):
                f = BASE_DIR / "wifi.html"
                if not f.is_file():
                    return self._send(404, "text/plain", "wifi.html missing")
                return self._send(200, "text/html; charset=utf-8", f.read_bytes())

            if path == "/api/net":
                st = dict(net.status())
                st["local"] = self.client_address[0] in LOOPBACK
                return self._json(st)

            if path == "/api/net/details":
                d = net.details()
                d["local"] = self.client_address[0] in LOOPBACK
                return self._json(d)

            if path == "/api/cloud":
                if not self._local():
                    return
                return self._json(gg_cloud.public_config())

            if path == "/api/wifi/scan":
                if not self._local():
                    return
                return self._json(net.scan(rescan=(query.get("rescan") or ["1"])[0] != "0"))

            if path in ("/", "/index.html"):
                f = BASE_DIR / "dashboard.html"
                if not f.is_file():
                    return self._send(404, "text/plain", "dashboard.html missing")
                return self._send(200, "text/html; charset=utf-8", f.read_bytes())

            if path == "/api/battery":
                return self._json({**ups.status(), **power.fields()})

            if path == "/api/state":
                ip = self.client_address[0]
                if ip not in LOOPBACK:
                    note_viewer(ip, client_kind(self.headers), headers=self.headers)
                st = engine.snapshot_state()
                st["viewers"] = viewers()
                st.update(screen.fields())
                st.update(clock.fields())
                st.update(buzzer.fields())
                st.update(vibe.fields())
                st.update(clean.fields())
                st["cloud"] = {k: gg_cloud.STATUS.get(k)
                               for k in ("enabled", "ok", "error", "watchers", "usage")}
                return self._json(st)

            if path == "/api/alerts":
                limit = int((query.get("limit") or [50])[0])
                return self._json({"alerts": engine.recent_alerts(limit)})

            if path == "/frame.jpg":
                jpg = engine.get_jpeg()
                if jpg is None:
                    return self._send(503, "text/plain", "no frame yet")
                return self._send(200, "image/jpeg", jpg)

            if path == "/stream.mjpg":
                return self._stream()

            if path.startswith("/snapshots/clean/"):
                return self._snapshot(path[len("/snapshots/clean/"):], gg_clean.FOLDER)

            if path.startswith("/snapshots/"):
                return self._snapshot(path[len("/snapshots/"):])

            return self._send(404, "text/plain", "not found")

        def do_POST(self):
            path = urlparse(self.path).path
            data = self._body()

            if path == "/api/wifi/connect":
                if not self._local():
                    return
                res = net.connect(str(data.get("ssid") or ""), data.get("password") or None,
                                  bool(data.get("hidden")))
                buzzer.play("wifi_ok" if res.get("ok") else "wifi_fail")
                return self._json(res)

            if path == "/api/net/ipv4":
                if not self._local():
                    return
                return self._json(net.set_ipv4(str(data.get("connection") or ""), data))

            if path == "/api/cloud":
                if not self._local():
                    return
                return self._json(cloud_settings(data))

            if path == "/api/cloud/restore":
                if not self._local():
                    return
                try:
                    good = json.loads(gg_cloud.GOOD.read_text())
                except (OSError, ValueError):
                    return self._json({"ok": False, "message": "No working settings have been saved yet."})
                gg_cloud.save_config(good)
                gg_cloud.start()
                return self._json({"ok": True, "message": "Restored the last working Firebase settings."})

            if path == "/api/wifi/fix_camera":
                if not self._local():
                    return
                return self._json(net.fix_camera_route())

            if path == "/api/client/bye":
                forget_viewer(self.client_address[0], client_kind(self.headers),
                              self.headers.get("X-GG-Client-Id"))
                return self._json({"ok": True})

            if path == "/api/camera/reconnect":
                engine.reconnect("button")
                threading.Thread(target=net.camera_up, daemon=True).start()
                return self._json({"ok": True})

            if path == "/api/restart":
                self._json({"ok": True})
                threading.Thread(target=restart_process, daemon=True).start()
                return

            if path == "/api/window":
                # Only the Pi's own screen may hide its window.
                if self.client_address[0] not in LOOPBACK:
                    return self._json({"ok": False, "error": "local_only"}, 403)
                if data.get("mode") == "mini":
                    return self._json(gg_window.minimize())
                return self._json(gg_window.restore())

            if path == "/api/settings":
                conf = data.get("conf")
                if conf is not None and float(conf) > 1:
                    conf = float(conf) / 100.0
                s = engine.update_settings(conf=conf,
                                           threshold=data.get("threshold"))
                buzzer.play("saved")
                return self._json({"ok": True, "settings": s})

            if path == "/api/roi":
                pts = engine.set_roi(data.get("points"))
                return self._json({"ok": True, "points": pts})

            if path == "/api/test_alert":
                engine.fire_test_alert()
                return self._json({"ok": True})

            if path == "/api/capture":
                saved = engine.capture_snapshot()
                return self._json({"ok": saved})

            if path == "/api/mute":
                res = engine.set_mute(seconds=data.get("seconds"),
                                      cancel=bool(data.get("cancel")))
                return self._json({"ok": True, **res})

            if path == "/api/auto_mute":
                on = data.get("on")
                secs = data.get("seconds")
                res = engine.set_auto_mute(on=None if on is None else bool(on),
                                           seconds=None if secs is None else int(secs))
                return self._json({"ok": True, **res})

            if path == "/api/screen_saver":
                res = screen.configure(mode=data.get("mode"), timeout_s=data.get("timeout_s"),
                                       timeout_battery_s=data.get("timeout_battery_s"),
                                       dim_pct=data.get("dim_pct"),
                                       wake_on_alert=data.get("wake_on_alert"))
                buzzer.play("saved")
                return self._json({"ok": True, **res})

            if path == "/api/screen":
                action = data.get("action")
                if action == "wake":
                    screen.wake()
                elif action == "sleep":
                    screen.sleep()
                else:
                    return self._json({"ok": False, "error": "bad_action"}, 400)
                return self._json({"ok": True, "screen": screen.state})

            if path == "/api/screen/touch":
                if self.client_address[0] not in LOOPBACK:
                    return self._json({"ok": False, "error": "local_only"}, 403)
                return self._json({"ok": True, "screen": screen.touch()})

            if path == "/api/auto_shutdown":
                if data.get("cancel"):
                    res = power.cancel()
                    return self._json(res, 200 if res.get("ok") else 409)
                on, pct = data.get("on"), data.get("pct")
                res = power.configure(on=None if on is None else bool(on), pct=pct)
                return self._json({"ok": True, **res})

            if path == "/api/buzzer":
                res = buzzer.configure(on=data.get("on"), volume=data.get("volume"),
                                       alerts=data.get("alerts"), countdown=data.get("countdown"),
                                       ui=data.get("ui"), repeat=data.get("repeat"),
                                       repeat_s=data.get("repeat_s"), quiet_on=data.get("quiet_on"),
                                       quiet_from=data.get("quiet_from"), quiet_to=data.get("quiet_to"))
                buzzer.play("saved")
                return self._json({"ok": True, **res})

            if path == "/api/buzzer/test":
                name = data.get("sound")
                if name not in gg_buzzer.SOUNDS:
                    return self._json({"ok": False, "error": "bad_sound"}, 400)
                if not buzzer.test(name):
                    return self._json({"ok": False, "error": "unavailable",
                                       "message": buzzer.fields()["buzzer_note"]}, 409)
                return self._json({"ok": True})

            if path == "/api/vibe":
                return self._json({"ok": True, **vibe.configure(on=data.get("on"), strength=data.get("strength"))})

            if path == "/api/clean":
                mode = data.get("mode")
                if mode not in gg_clean.MODES:
                    return self._json({"ok": False, "error": "bad_mode"}, 400)
                res = clean.configure(mode=mode)
                buzzer.play("saved")
                return self._json({"ok": True, **res})

            if path == "/api/vibe/test":
                name = data.get("pattern", "test")
                if name not in gg_vibe.PATTERNS:
                    return self._json({"ok": False, "error": "bad_pattern"}, 400)
                if not vibe.test(name):
                    return self._json({"ok": False, "error": "unavailable",
                                       "message": vibe.fields()["vibe_note"]}, 409)
                return self._json({"ok": True})

            if path == "/api/buzzer/ui":
                if self.client_address[0] not in LOOPBACK:
                    return self._json({"ok": False, "error": "local_only"}, 403)
                return self._json({"ok": True, "played": buzzer.ui(data.get("sound"))})

            if path == "/api/shutdown":
                if not data.get("confirm"):
                    return self._json({"ok": False, "error": "confirm_required"}, 400)
                res = power.manual_shutdown()
                return self._json(res, 200 if res.get("ok") else 500)

            if path == "/api/alerts/read":
                ids = data.get("ids")
                res = engine.mark_read(
                    ids=ids if isinstance(ids, list) else None,
                    every=bool(data.get("all")),
                    read=data.get("read", True) is not False,
                )
                return self._json({"ok": True, **res})

            if path == "/api/alerts/delete":
                ids = data.get("ids")
                res = engine.delete_alerts(
                    ids=ids if isinstance(ids, list) else None,
                    every=bool(data.get("all")),
                    dfrom=data.get("from") or None,
                    dto=data.get("to") or None,
                )
                return self._json({"ok": True, **res})

            return self._send(404, "text/plain", "not found")

        def _snapshot(self, name, sub=None):
            if not SNAPSHOT_NAME.match(name):
                return self._send(404, "text/plain", "not found")
            folder = Path(engine.snapshot_dir).resolve()
            if sub:
                folder = folder / sub
            target = (folder / name).resolve()
            if target.parent != folder or not target.is_file():
                return self._send(404, "text/plain", "not found")
            return self._send(200, "image/jpeg", target.read_bytes())

        def _stream(self):
            self.send_response(200)
            self.send_header("Age", "0")
            self.send_header("Cache-Control", "no-cache, private")
            self.send_header("Pragma", "no-cache")
            self.send_header("Content-Type",
                             "multipart/x-mixed-replace; boundary=ggframe")
            self.end_headers()
            last = None
            try:
                while True:
                    jpg = engine.get_jpeg()
                    if jpg is not None and jpg is not last:
                        last = jpg
                        self.wfile.write(b"--ggframe\r\n")
                        self.wfile.write(b"Content-Type: image/jpeg\r\n")
                        self.wfile.write(
                            f"Content-Length: {len(jpg)}\r\n\r\n".encode())
                        self.wfile.write(jpg)
                        self.wfile.write(b"\r\n")
                    time.sleep(1.0 / STREAM_FPS)
            except (BrokenPipeError, ConnectionResetError):
                pass

    return Handler


class QuietServer(ThreadingHTTPServer):
    def handle_error(self, request, client_address):
        err = sys.exc_info()[1]
        if isinstance(err, (BrokenPipeError, ConnectionResetError, ConnectionAbortedError)):
            return
        super().handle_error(request, client_address)


def cloud_settings(data):
    old = gg_cloud.load_config() or {}
    cfg = {k: str(data.get(k) or "").strip() for k in ("database_url", "api_key", "email")}
    cfg["password"] = data.get("password") or old.get("password") or ""
    cfg["enabled"] = bool(data.get("enabled", True))
    if not cfg["enabled"]:
        gg_cloud.save_config(dict(old, enabled=False) if old else cfg)
        gg_cloud.start()
        return {"ok": True, "message": "Firebase sync is off. The app still works on the same Wi-Fi."}
    res = gg_cloud.test_config(cfg)
    if res.get("ok") and not data.get("test_only"):
        gg_cloud.save_config(cfg, good=True)
        gg_cloud.start()
        res["message"] = "Saved and connected. These are now the working settings."
    elif not res.get("ok") and data.get("save_anyway"):
        gg_cloud.save_config(cfg)
        gg_cloud.start()
        res["saved"] = True
    return res


def restart_process(delay=0.6):
    time.sleep(delay)
    print("Restarting Garbage-Guard from the dashboard.", flush=True)
    os.execv(sys.executable, [sys.executable] + sys.argv)


def serve(engine, port=8080, ups=None, screen=None, power=None, buzzer=None, clock=None, vibe=None,
          clean=None):
    server = QuietServer(("0.0.0.0", port), make_handler(engine, ups=ups, screen=screen, power=power,
                                                         buzzer=buzzer, clock=clock, vibe=vibe,
                                                         clean=clean))
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server

import json
import re
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

BASE_DIR = Path(__file__).resolve().parent
STREAM_FPS = 12
SNAPSHOT_NAME = re.compile(r"^[A-Za-z0-9_.-]+\.jpg$")


def make_handler(engine):
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

        def do_GET(self):
            path = urlparse(self.path).path
            query = parse_qs(urlparse(self.path).query)

            if path in ("/", "/index.html"):
                f = BASE_DIR / "dashboard.html"
                if not f.is_file():
                    return self._send(404, "text/plain", "dashboard.html missing")
                return self._send(200, "text/html; charset=utf-8", f.read_bytes())

            if path == "/api/state":
                return self._json(engine.snapshot_state())

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

            if path.startswith("/snapshots/"):
                return self._snapshot(path[len("/snapshots/"):])

            return self._send(404, "text/plain", "not found")

        def do_POST(self):
            path = urlparse(self.path).path
            data = self._body()

            if path == "/api/settings":
                conf = data.get("conf")
                if conf is not None and float(conf) > 1:
                    conf = float(conf) / 100.0
                s = engine.update_settings(conf=conf,
                                           threshold=data.get("threshold"))
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

        def _snapshot(self, name):
            if not SNAPSHOT_NAME.match(name):
                return self._send(404, "text/plain", "not found")
            folder = Path(engine.snapshot_dir).resolve()
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


def serve(engine, port=8080):
    server = QuietServer(("0.0.0.0", port), make_handler(engine))
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server

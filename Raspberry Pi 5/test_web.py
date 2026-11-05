import http.client
import io
import json
import shutil
import subprocess
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

import gg_buzzer
import gg_model
import gg_power
import gg_screen
import gg_web as w

DALVIK = "Dalvik/2.1.0 (Linux; U; Android 14; SM-A546E Build/UP1A.231005.007)"
CHROME = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/141.0 Mobile Safari/537.36"


class ViewerTests(unittest.TestCase):
    def setUp(self):
        w._viewers.clear()
        w._names.clear()

    def test_kinds(self):
        self.assertEqual(w.client_kind({"User-Agent": DALVIK}), "app")
        self.assertEqual(w.client_kind({"User-Agent": CHROME}), "browser")
        self.assertEqual(w.client_kind({"X-GG-Client": "android", "User-Agent": "x"}), "app")

    def test_describe(self):
        self.assertEqual(w.describe_ua(DALVIK)[:2], ("Android 14", "SM-A546E"))
        self.assertEqual(w.describe_ua(CHROME), ("Android 10", None, "Chrome"))

    def test_counts_and_expiry(self):
        w.note_viewer("192.168.1.30", "app", now=100.0)
        w.note_viewer("192.168.1.31", "browser", now=100.0)
        v = w.viewers(now=102.0)
        self.assertEqual((v["apps"], v["browsers"]), (1, 1))
        self.assertEqual(w.viewers(now=100.0 + w.VIEWER_TTL + 0.5)["list"], [])

    def test_same_phone_counts_once_and_tracks_since(self):
        w.note_viewer("192.168.1.30", "app", now=1.0)
        w.note_viewer("192.168.1.30", "app", now=3.0)
        row = w.viewers(now=3.5)["list"][0]
        self.assertEqual((row["hits"], row["since_s"]), (2, 2))

    def test_two_phones_behind_one_address(self):
        w.note_viewer("10.0.0.5", "app", now=1.0, headers={"X-GG-Client-Id": "a"})
        w.note_viewer("10.0.0.5", "app", now=1.0, headers={"X-GG-Client-Id": "b"})
        self.assertEqual(w.viewers(now=2.0)["apps"], 2)

    def test_headers_fill_details(self):
        w.note_viewer("192.168.1.30", "app", now=1.0, headers={
            "User-Agent": DALVIK, "X-GG-Device": "Galaxy A54", "X-GG-App-Version": "1.2", "X-GG-Mode": "background"})
        row = w.viewers(now=1.5)["list"][0]
        self.assertEqual((row["device"], row["os"], row["app_version"], row["mode"]),
                         ("Galaxy A54", "Android 14", "1.2", "background"))

    def test_how_connected(self):
        self.assertEqual(w.link_kind("192.168.1.30"), "local")
        self.assertEqual(w.link_kind("100.101.5.9"), "remote")
        self.assertEqual(w.link_kind("8.8.8.8"), "remote")
        w.note_viewer("192.168.1.233", "app", now=1.0, headers={"X-GG-Client-Id": "a"})
        w.note_viewer("192.168.1.233", "app", now=1.0, headers={"X-GG-Client-Id": "b"})
        w.note_viewer("192.168.1.40", "app", now=1.0)
        w.note_viewer("100.80.1.2", "app", now=1.0)
        via = {(r["ip"], r["id"]): r["via"] for r in w.viewers(now=1.5)["list"]}
        self.assertEqual(via[("192.168.1.233", "a")], "shared")
        self.assertEqual(via[("192.168.1.40", None)], "direct")
        self.assertEqual(via[("100.80.1.2", None)], "remote")

    def test_bye_removes_at_once(self):
        w.note_viewer("192.168.1.30", "app", now=1.0)
        w.forget_viewer("192.168.1.30", "app")
        self.assertEqual(w.viewers(now=1.1)["apps"], 0)


class FakeEngine:
    def __init__(self):
        self.settings = {}

    def snapshot_state(self):
        return {"connected": True}

    def update_settings(self, conf=None, threshold=None):
        return dict(self.settings)

    def set_labels(self, labels):
        self.labels = {**getattr(self, "labels", {}), **labels}
        return self.labels

    def alerts_total(self):
        return 300

    def recent_alerts(self, limit=50):
        return [{"id": 300 - i} for i in range(int(limit))]

    def alerts_sig(self):
        return "s1"

    def upload_model(self, version, stream, length):
        try:
            name = gg_model.install(stream, length, version, self.model_dir)
        except gg_model.UploadError as e:
            return e.status, {"ok": False, "error": e.code, "message": e.message}
        return 200, {"ok": True, "pending": name}


def model_zip(prefix=""):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for name, data in (("model.ncnn.param", b"p"), ("model.ncnn.bin", b"b" * 1000),
                           ("metadata.yaml", b"names: {0: bottle, 1: bag, 2: food_container}")):
            z.writestr(prefix + name, data)
    return buf.getvalue()


class FakeUps:
    def status(self):
        return {"ok": True, "percent": 50, "on_ac": True}


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.engine = FakeEngine()
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.engine.model_dir = Path(tmp.name)
        ups = FakeUps()
        self.screen = gg_screen.Screen(self.engine.settings, ups=ups, run=lambda c, e: (True, "HDMI-A-2 x\n  Enabled: yes\n" if c == ["wlr-randr"] else ""),
                                       have=lambda n: "/x")
        self.offs = []
        self.power = gg_power.Power(self.engine.settings, ups=ups, screen=self.screen,
                                    shutdown=lambda: self.offs.append(1) or True,
                                    spawn=lambda f: f())
        self.sounds = gg_buzzer.FakeBackend()
        self.buzzer = gg_buzzer.Buzzer(self.engine.settings, backend=self.sounds)
        self.server = w.QuietServer(("127.0.0.1", 0), w.make_handler(
            self.engine, net=object(), ups=ups, screen=self.screen, power=self.power,
            buzzer=self.buzzer))
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.base = f"http://127.0.0.1:{self.server.server_address[1]}"
        self.addCleanup(self.server.shutdown)

    def call(self, path, body=None):
        req = urllib.request.Request(self.base + path, method="POST" if body is not None else "GET",
                                     data=None if body is None else json.dumps(body).encode())
        try:
            with urllib.request.urlopen(req, timeout=5) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read() or b"{}")

    def test_state_carries_screen_fields(self):
        code, st = self.call("/api/state")
        self.assertEqual((st["screen"], st["screen_mode"], st["screen_timeout_battery_s"]), ("on", "off", 60))

    def test_state_carries_buzzer_fields(self):
        code, st = self.call("/api/state")
        self.assertEqual((st["buzzer_on"], st["buzzer_available"], st["quiet_now"]), (True, True, False))

    def test_buzzer_settings_are_clamped(self):
        code, res = self.call("/api/buzzer", {"volume": "high", "repeat_s": 5, "quiet_from": "bad", "ui": True})
        self.assertEqual((res["ok"], res["buzzer_volume"], res["buzzer_repeat_s"], res["quiet_from"], res["buzzer_ui"]),
                         (True, "high", 10, "22:00", True))

    def test_buzzer_test_plays_and_rejects_unknown(self):
        self.assertEqual(self.call("/api/buzzer/test", {"sound": "nope"})[0], 400)
        self.assertEqual(self.call("/api/buzzer/test", {"sound": "test"})[0], 200)
        self.buzzer.drain()
        self.assertEqual([e[1] for e in self.sounds.events if e[0] == "tone"], [1800, 2300])
        self.sounds.available, self.sounds.note = False, "no pin"
        code, res = self.call("/api/buzzer/test", {"sound": "test"})
        self.assertEqual((code, res["error"], res["message"]), (409, "unavailable", "no pin"))

    def test_button_sound_endpoint(self):
        self.call("/api/buzzer", {"ui": True})
        self.buzzer.drain()
        code, res = self.call("/api/buzzer/ui", {"sound": "tap"})
        self.assertEqual((code, res["played"]), (200, True))

    def test_every_planned_button_sound_is_accepted(self):
        self.call("/api/buzzer", {"ui": True})
        self.buzzer.drain()
        for name in gg_buzzer.UI_SOUNDS:
            code, res = self.call("/api/buzzer/ui", {"sound": name})
            self.assertEqual((code, res["ok"]), (200, True), name)
        self.assertEqual(self.call("/api/buzzer/ui", {"sound": "accumulation"})[1]["played"], False)
        self.assertEqual(self.call("/api/buzzer/ui", {"sound": "boot"})[1]["played"], False)

    def test_wifi_result_and_saves_make_a_sound(self):
        class Net:
            result = {"ok": True}

            def connect(self, ssid, password, hidden):
                return self.result
        net = Net()
        server = w.QuietServer(("127.0.0.1", 0), w.make_handler(
            self.engine, net=net, ups=FakeUps(), screen=self.screen, power=self.power,
            buzzer=self.buzzer))
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.shutdown)
        base = self.base
        self.base = f"http://127.0.0.1:{server.server_address[1]}"
        self.addCleanup(setattr, self, "base", base)

        def last():
            self.buzzer.drain()
            tones = [e[1] for e in self.sounds.events if e[0] == "tone"]
            self.sounds.events.clear()
            return tones
        self.call("/api/wifi/connect", {"ssid": "x"})
        self.assertEqual(last(), [2000, 2600])
        net.result = {"ok": False}
        self.call("/api/wifi/connect", {"ssid": "x"})
        self.assertEqual(last(), [1700, 1400])
        self.call("/api/buzzer", {"volume": "low"})
        self.assertEqual(last(), [2300, 2700])
        self.call("/api/screen_saver", {"mode": "dim"})
        self.assertEqual(last(), [2300, 2700])
        self.call("/api/settings", {"threshold": 5})
        self.assertEqual(last(), [2300, 2700])

    def test_clean_copy_setting(self):
        code, st = self.call("/api/state")
        self.assertEqual((st["clean_mode"], st["clean_count"]), ("all", 0))
        code, res = self.call("/api/clean", {"mode": "off"})
        self.assertEqual((code, res["clean_mode"]), (200, "off"))
        self.assertEqual(self.call("/api/clean", {"mode": "lots"})[0], 400)
        self.assertEqual(self.call("/api/state")[1]["clean_mode"], "off")

    def test_clean_pictures_are_served_from_their_folder(self):
        import os
        import tempfile
        d = tempfile.TemporaryDirectory()
        self.addCleanup(d.cleanup)
        os.mkdir(os.path.join(d.name, "clean"))
        with open(os.path.join(d.name, "clean", "capture_1.jpg"), "wb") as f:
            f.write(b"CLEAN")
        with open(os.path.join(d.name, "capture_1.jpg"), "wb") as f:
            f.write(b"BOXES")
        self.engine.snapshot_dir = d.name

        def get(path):
            try:
                with urllib.request.urlopen(self.base + path, timeout=5) as r:
                    return r.status, r.read()
            except urllib.error.HTTPError as e:
                return e.code, b""
        self.assertEqual(get("/snapshots/clean/capture_1.jpg"), (200, b"CLEAN"))
        self.assertEqual(get("/snapshots/capture_1.jpg"), (200, b"BOXES"))
        self.assertEqual(get("/snapshots/clean/missing.jpg")[0], 404)
        self.assertEqual(get("/snapshots/clean/..%2Fcapture_1.jpg")[0], 404)

    def test_labels_endpoint(self):
        code, res = self.call("/api/labels", {"id": True})
        self.assertEqual((code, res["ok"], res["labels"]), (200, True, {"id": True}))

    def test_alerts_and_state_carry_the_total(self):
        code, res = self.call("/api/alerts?limit=2")
        self.assertEqual((res["total"], len(res["alerts"])), (300, 2))
        self.assertEqual(self.call("/api/state")[1]["alerts_total"], 300)
        self.assertNotIn("total", self.call("/api/alerts?limit=2&sig=s1")[1])

    def test_square_thumbnail(self):
        try:
            import cv2
            import numpy as np
        except ImportError:
            self.skipTest("numpy and OpenCV are needed")
        import os
        import tempfile
        d = tempfile.TemporaryDirectory()
        self.addCleanup(d.cleanup)
        cv2.imwrite(os.path.join(d.name, "alert_1.jpg"), np.zeros((540, 960, 3), np.uint8))
        self.engine.snapshot_dir = d.name
        with urllib.request.urlopen(self.base + "/snapshots/alert_1.jpg?thumb=1", timeout=5) as r:
            img = cv2.imdecode(np.frombuffer(r.read(), np.uint8), cv2.IMREAD_COLOR)
            self.assertEqual(r.headers.get_all("Cache-Control"), ["max-age=86400"])
        self.assertEqual(img.shape[:2], (w.THUMB_PX, w.THUMB_PX))

    def test_state_carries_clock(self):
        code, st = self.call("/api/state")
        self.assertIn(st["clock_synced"], (True, False))
        self.assertEqual(st["clock_zone"], "Asia/Manila")

    def test_screen_saver_settings_are_clamped(self):
        code, res = self.call("/api/screen_saver", {"mode": "dim", "timeout_s": 5, "dim_pct": 90})
        self.assertEqual((res["ok"], res["screen_mode"], res["screen_timeout_s"], res["screen_dim_pct"]),
                         (True, "dim", 60, 50))

    def test_sleep_and_wake(self):
        self.assertEqual(self.call("/api/screen", {"action": "sleep"})[1]["screen"], "off")
        self.assertEqual(self.call("/api/screen", {"action": "wake"})[1]["screen"], "on")
        self.assertEqual(self.call("/api/screen", {"action": "nope"})[0], 400)

    def test_touch_is_local_only(self):
        self.screen.sleep()
        self.assertEqual(self.call("/api/screen/touch", {})[1]["screen"], "on")
        old = w.LOOPBACK
        w.LOOPBACK = ()
        self.addCleanup(setattr, w, "LOOPBACK", old)
        self.assertEqual(self.call("/api/screen/touch", {})[0], 403)

    def test_shutdown_needs_confirmation(self):
        self.assertEqual(self.call("/api/shutdown", {})[0], 400)
        self.assertEqual(self.offs, [])
        self.assertEqual(self.call("/api/shutdown", {"confirm": True})[0], 200)
        self.assertEqual(self.offs, [1])

    def test_battery_has_shutdown_fields_and_settings_endpoint(self):
        code, b = self.call("/api/battery")
        self.assertEqual((b["percent"], b["shutdown_state"], b["auto_shutdown_pct"]), (50, "off", 15))
        code, res = self.call("/api/auto_shutdown", {"on": True, "pct": 99})
        self.assertEqual((res["ok"], res["auto_shutdown_pct"]), (True, 50))
        self.assertEqual(self.call("/api/auto_shutdown", {"cancel": True})[0], 200)


    def raw(self, path, body=b"", headers=None, length=True):
        port = self.server.server_address[1]
        conn = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
        conn.putrequest("POST", path)
        for k, v in (headers or {"Content-Type": "application/zip"}).items():
            conn.putheader(k, v)
        if length is True:
            conn.putheader("Content-Length", str(len(body)))
        elif length is not None:
            conn.putheader("Content-Length", str(length))
        conn.endheaders()
        if body:
            conn.send(body)
        r = conn.getresponse()
        out = r.status, json.loads(r.read() or b"{}")
        conn.close()
        return out

    def test_model_upload_raw_zip(self):
        code, res = self.raw("/api/model/upload?version=12", model_zip("best_ncnn_model/"))
        self.assertEqual((code, res["pending"]), (200, "garbageguard_v12_best_ncnn_model"))
        self.assertTrue((self.engine.model_dir / res["pending"] / "model.ncnn.bin").is_file())
        code, res = self.raw("/api/model/upload?version=v13", model_zip(),
                             {"Content-Type": "application/octet-stream"})
        self.assertEqual(code, 200)

    def test_model_upload_size_limits(self):
        code, res = self.raw("/api/model/upload?version=v12", length=gg_model.MAX_UPLOAD + 1)
        self.assertEqual((code, res["error"]), (413, "too_large"))
        code, res = self.raw("/api/model/upload?version=v12", length=None)
        self.assertEqual(code, 413)
        self.assertFalse((self.engine.model_dir / "garbageguard_v12_best_ncnn_model").exists())

    def test_model_upload_refusals_still_reply(self):
        data = model_zip()
        self.assertEqual(self.raw("/api/model/upload?version=v12", data)[0], 200)
        code, res = self.raw("/api/model/upload?version=v12", data)
        self.assertEqual((code, res["error"]), (409, "exists"))
        code, res = self.raw("/api/model/upload?version=a/b", data)
        self.assertEqual((code, res["error"]), (400, "bad_version"))
        code, res = self.raw("/api/model/upload?version=v14", b"not a zip at all")
        self.assertEqual((code, res["error"]), (400, "bad_zip"))
        code, res = self.raw("/api/model/upload?version=v14", data,
                             {"Content-Type": "multipart/form-data; boundary=x"})
        self.assertEqual(code, 415)

    def test_dashboard_folder_zip_is_read_by_python(self):
        """The dashboard packs a picked folder with its own zip writer; Python must read it."""
        node = shutil.which("node")
        if not node:
            self.skipTest("Node.js is needed to run the dashboard's zip writer")
        html = (Path(w.BASE_DIR) / "dashboard.html").read_text(encoding="utf-8")
        start, end = html.index("// zip-writer-start"), html.index("// zip-writer-end")
        script = html[start:end] + """
const enc = new TextEncoder();
const files = [{name: 'best_ncnn_model/model.ncnn.param', data: enc.encode('7767517 param')},
               {name: 'best_ncnn_model/model.ncnn.bin', data: new Uint8Array(70000).map((_, i) => i % 251)},
               {name: 'best_ncnn_model/metadata.yaml', data: enc.encode('names:\\n  0: bottle\\n')}];
process.stdout.write(Buffer.from(storeZip(files)).toString('base64'));
"""
        out = subprocess.run([node, "-e", script], capture_output=True, text=True, timeout=30)
        self.assertEqual(out.returncode, 0, out.stderr)
        import base64
        data = base64.b64decode(out.stdout)
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            self.assertIsNone(z.testzip())
            self.assertEqual(z.read("best_ncnn_model/model.ncnn.bin"), bytes(i % 251 for i in range(70000)))
        code, res = self.raw("/api/model/upload?version=v15", data)
        self.assertEqual(code, 200, res)

if __name__ == "__main__":
    unittest.main()

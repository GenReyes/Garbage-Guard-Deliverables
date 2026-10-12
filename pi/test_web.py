import json
import threading
import unittest
import urllib.error
import urllib.request

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


class FakeUps:
    def status(self):
        return {"ok": True, "percent": 50, "on_ac": True}


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.engine = FakeEngine()
        ups = FakeUps()
        self.screen = gg_screen.Screen(self.engine.settings, ups=ups, run=lambda c, e: (True, ""),
                                       have=lambda n: "/x")
        self.power = gg_power.Power(self.engine.settings, ups=ups, screen=self.screen)
        self.server = w.QuietServer(("127.0.0.1", 0), w.make_handler(
            self.engine, net=object(), ups=ups, screen=self.screen, power=self.power))
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

    def test_battery_has_shutdown_fields_and_settings_endpoint(self):
        code, b = self.call("/api/battery")
        self.assertEqual((b["percent"], b["shutdown_state"], b["auto_shutdown_pct"]), (50, "off", 15))
        code, res = self.call("/api/auto_shutdown", {"on": True, "pct": 99})
        self.assertEqual((res["ok"], res["auto_shutdown_pct"]), (True, 50))
        self.assertEqual(self.call("/api/auto_shutdown", {"cancel": True})[0], 200)


if __name__ == "__main__":
    unittest.main()

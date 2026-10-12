import json
import unittest
from urllib.parse import urlparse, parse_qs

import cv2
import numpy as np

import pathlib
import tempfile

import gg_cloud as gc
import gg_usage

JPG = cv2.imencode(".jpg", np.full((720, 1280, 3), 90, np.uint8))[1].tobytes()


class FakeWorld:
    """A Firebase tree in a dict, plus the Pi's local API."""

    def __init__(self):
        self.db = {}
        self.posts = []
        self.alerts = [
            {"id": 2, "timestamp": "2026-10-12 01:00:00", "event": "accumulation", "smoothed_count": 21,
             "raw_count": 21, "n_bottle": 9, "n_bag": 6, "n_food_container": 6, "peak_confidence": 0.9,
             "threshold_used": 20, "read": 0, "snapshot": "alert_2.jpg", "snapshot_path": "/x/alert_2.jpg"},
            {"id": 1, "timestamp": "2026-10-12 00:50:00", "event": "test", "smoothed_count": 3,
             "raw_count": 3, "n_bottle": 1, "n_bag": 1, "n_food_container": 1, "peak_confidence": 0.7,
             "threshold_used": 20, "read": 1, "snapshot": None, "snapshot_path": None},
        ]
        self.state = {"connected": True, "counts": {"bottle": 9}, "smoothed": 21, "threshold": 20,
                      "alert_active": True, "muted": False, "auto_mute": False, "viewers": {"apps": 1},
                      "screen": "on", "screen_mode": "off"}
        self.battery = {"ok": True, "percent": 80, "shutdown_state": "armed"}

    def _node(self, parts, make=False):
        cur = self.db
        for p in parts[:-1]:
            if p not in cur or not isinstance(cur[p], dict):
                if not make:
                    return None, None
                cur[p] = {}
            cur = cur[p]
        return cur, parts[-1]

    def http(self, method, url, body=None, timeout=15):
        u = urlparse(url)
        if u.netloc == "127.0.0.1:8080":
            return self.local(method, u.path + ("?" + u.query if u.query else ""), body)
        parts = [p for p in u.path[:-5].split("/") if p]
        q = parse_qs(u.query)
        parent, key = self._node(parts, make=method in ("PUT", "PATCH"))
        if method == "GET":
            val = None if parent is None else parent.get(key)
            if "shallow" in q and isinstance(val, dict):
                val = {k: True for k in val}
            return 200, json.dumps(val).encode()
        if method == "PUT":
            parent[key] = body
        elif method == "PATCH":
            node = parent.setdefault(key, {})
            for k, v in body.items():
                if v is None:
                    node.pop(k, None)
                else:
                    node[k] = v
        elif method == "DELETE" and parent is not None:
            parent.pop(key, None)
        return 200, b"null"

    def local(self, method, path, body):
        if path == "/api/state":
            return 200, json.dumps(self.state).encode()
        if path == "/api/battery":
            return 200, json.dumps(self.battery).encode()
        if path.startswith("/api/alerts?"):
            return 200, json.dumps({"alerts": self.alerts}).encode()
        if path.startswith("/snapshots/") or path == "/frame.jpg":
            return 200, JPG
        if path == "/api/net/details":
            return 200, json.dumps({"interfaces": [
                {"role": "wired", "state": "connected", "ips": ["192.168.1.15/24"]},
                {"role": "camera", "state": "connected", "ips": ["192.168.1.101/24"]}]}).encode()
        if method == "POST":
            self.posts.append((path, body))
            return 200, b'{"ok": true}'
        return 404, b""


class CloudTests(unittest.TestCase):
    def setUp(self):
        self.w = FakeWorld()
        self.t = [1000.0]
        self.noted = []
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        usage = gg_usage.Usage(path=pathlib.Path(self.tmp.name) / "u.json", clock=lambda: self.t[0])
        self.c = gc.Cloud({"database_url": "https://x.firebasedatabase.app"}, token=False, http=self.w.http,
                          clock=lambda: self.t[0], note=lambda ip, kind, headers: self.noted.append(headers),
                          usage=usage)

    def test_state_is_slim_and_skips_repeats(self):
        self.assertTrue(self.c.push_state())
        st = self.w.db["gg"]["state"]
        self.assertNotIn("viewers", st)
        self.assertEqual(st["battery"]["percent"], 80)
        self.assertFalse(self.c.push_state())
        self.w.state["smoothed"] = 22
        self.w.state["counts"] = {"bottle": 10}
        self.assertFalse(self.c.push_state())
        self.w.state["muted"] = True
        self.assertTrue(self.c.push_state())

    def test_screen_change_is_pushed_at_once(self):
        self.assertTrue(self.c.push_state())
        self.assertFalse(self.c.push_state())
        self.w.state["screen"] = "off"
        self.assertTrue(self.c.push_state())
        self.assertEqual(self.w.db["gg"]["state"]["screen"], "off")

    def test_shutdown_state_change_is_pushed_at_once(self):
        self.c.push_state()
        self.w.battery["shutdown_state"] = "counting"
        self.w.battery["shutdown_in_s"] = 299
        self.assertTrue(self.c.push_state())
        self.assertEqual(self.w.db["gg"]["state"]["battery"]["shutdown_in_s"], 299)

    def test_countdown_is_sent_every_few_seconds_and_commands_polled_fast(self):
        self.w.battery["shutdown_state"] = "counting"
        self.c.push_state()
        self.t[0] += 1
        self.assertFalse(self.c.push_state())
        self.t[0] += 3
        self.assertTrue(self.c.push_state())
        self.assertTrue(self.c.counting)
        self.c.next.pop("commands", None)
        self.c.tick()
        self.t[0] += 2.5
        self.w.db.setdefault("gg", {}).setdefault("commands", {})["c1"] = {"cmd": "auto_shutdown", "ts": 1,
                                                                          "args": {"cancel": True}}
        self.c.tick()
        self.assertIn(("/api/auto_shutdown", {"cancel": True}), self.w.posts)

    def test_flush_sends_history_and_state_now(self):
        self.assertTrue(self.c.flush(2))
        gg = self.w.db["gg"]
        self.assertIn("2", gg["alerts"])
        self.assertIn("state", gg)
        self.assertIn("pi", gg)

    def test_screen_and_power_commands_are_known(self):
        for name in ("screen_saver", "screen", "auto_shutdown"):
            self.assertIn(name, gc.COMMANDS)
        self.assertIn("screen_timeout_battery_s", gc.STATE_KEYS)

    def test_counts_are_pushed_only_while_watched(self):
        self.c.watchers = {"p1": {"ts": 1}}
        self.assertTrue(self.c.push_state())
        self.w.state["smoothed"] = 22
        self.assertTrue(self.c.push_state())

    def test_writes_are_silent(self):
        urls = []
        http = self.w.http
        self.c.http = lambda m, u, b=None, timeout=15: (urls.append((m, u)), http(m, u, b))[1]
        self.c.push_state()
        self.c.read_watchers()
        self.assertTrue(all("print=silent" in u for m, u in urls if m in ("PUT", "PATCH", "DELETE")))
        self.assertTrue(all("print=silent" not in u for m, u in urls if m == "GET"))

    def test_slow_polling_when_nobody_watches(self):
        gets = []
        http = self.w.http
        self.c.http = lambda m, u, b=None, timeout=15: (gets.append(u) if m == "GET" and "watchers" in u else None,
                                                         http(m, u, b))[1]
        for _ in range(60):
            self.c.tick()
            self.t[0] += 1
        self.assertLessEqual(len(gets), 6)
        self.assertIn("pi", self.w.db["gg"])

    def test_alert_changes_bump_the_revision(self):
        self.c.push_pi()
        first = self.w.db["gg"]["pi"]["alerts_rev"]
        self.t[0] += 5
        self.c.tick()
        self.assertGreater(self.w.db["gg"]["pi"]["alerts_rev"], first)
        rev = self.w.db["gg"]["pi"]["alerts_rev"]
        self.t[0] += 11
        self.c.tick()
        self.assertEqual(self.w.db["gg"]["pi"]["alerts_rev"], rev)

    def test_alerts_and_snapshots(self):
        changed, up = self.c.sync_alerts()
        self.assertEqual((changed, up), (2, 1))
        snap = self.w.db["gg"]["snaps"]["2"]
        self.assertLessEqual(snap["w"], 960)
        self.assertLess(snap["bytes"], 300 * 1024)
        self.assertNotIn("snapshot_path", self.w.db["gg"]["alerts"]["2"])
        self.assertEqual(self.c.sync_alerts(), (0, 0))
        self.w.alerts.pop(0)
        self.c.sync_alerts()
        self.assertNotIn("2", self.w.db["gg"]["alerts"])
        self.assertNotIn("2", self.w.db["gg"].get("snaps", {}))

    def test_commands_run_once_through_local_api(self):
        self.w.db = {"gg": {"commands": {"a1": {"cmd": "mute", "args": {"seconds": 900}, "by": "u"},
                                         "a2": {"cmd": "restart", "by": "u"}}}}
        self.assertEqual(self.c.run_commands(), 2)
        self.assertEqual(self.w.posts, [("/api/mute", {"seconds": 900})])
        self.assertEqual(self.w.db["gg"]["commands"], {})
        self.assertTrue(self.w.db["gg"]["results"]["a1"]["ok"])
        self.assertEqual(self.w.db["gg"]["results"]["a2"]["error"], "unknown_command")

    def test_watchers_drive_preview_and_phone_list(self):
        self.w.db = {"gg": {"watchers": {"p1": {"ts": 999_000, "device": "Galaxy A35"},
                                         "old": {"ts": 900_000}}}}
        active = self.c.read_watchers()
        self.assertEqual(list(active), ["p1"])
        self.assertNotIn("old", self.w.db["gg"]["watchers"])
        self.assertEqual(self.noted[0]["X-GG-Device"], "Galaxy A35")
        self.c.tick()
        self.assertIn("preview", self.w.db["gg"])
        self.assertLessEqual(self.w.db["gg"]["preview"]["w"], 480)

    def test_no_preview_when_nobody_watches(self):
        self.c.tick()
        self.assertNotIn("preview", self.w.db.get("gg", {}))

    def test_pi_heartbeat_lists_local_addresses_not_camera(self):
        self.c.push_pi()
        self.assertEqual(self.w.db["gg"]["pi"]["local_urls"], ["http://192.168.1.15:8080"])

    def test_start_is_off_without_config(self):
        saved = gc.CONFIG
        gc.CONFIG = gc.BASE_DIR / "no_such_file.json"
        try:
            self.assertIsNone(gc.start())
        finally:
            gc.CONFIG = saved


GOOD_CFG = {"database_url": "https://gg-test-default-rtdb.asia-southeast1.firebasedatabase.app",
            "api_key": "AIza" + "x" * 35, "email": "pi@example.com", "password": "secret1"}


class ConfigTests(unittest.TestCase):
    def test_field_checks(self):
        self.assertEqual(gc.check_config(GOOD_CFG), {})
        bad = gc.check_config({"database_url": "http://example.com", "api_key": "abc", "email": "x", "password": "1"})
        self.assertEqual(set(bad), {"database_url", "api_key", "email", "password"})
        self.assertEqual(gc.check_config(dict(GOOD_CFG, database_url="https://gg-test.firebaseio.com/")), {})

    def fake(self, login=200, role="pi", db=200, msg=""):
        def http(method, url, body=None, timeout=15):
            if "identitytoolkit" in url:
                if login != 200:
                    return login, json.dumps({"error": {"message": msg}}).encode()
                return 200, json.dumps({"idToken": "T", "localId": "UID1", "expiresIn": "3600"}).encode()
            self.assertIn("gg_users/UID1.json?auth=T", url)
            return db, json.dumps(role).encode()
        return http

    def test_connection_test_outcomes(self):
        self.assertTrue(gc.test_config(GOOD_CFG, self.fake())["ok"])
        r = gc.test_config(GOOD_CFG, self.fake(login=400, msg="INVALID_LOGIN_CREDENTIALS"))
        self.assertEqual(r["message"], "Wrong email or password.")
        self.assertEqual(gc.test_config(GOOD_CFG, self.fake(db=401))["error"], "rules")
        r = gc.test_config(GOOD_CFG, self.fake(role=None))
        self.assertEqual((r["error"], r["uid"]), ("role", "UID1"))
        self.assertEqual(gc.test_config(dict(GOOD_CFG, api_key="x"), self.fake())["error"], "invalid")

    def test_token_is_reused_until_it_expires(self):
        calls, t = [], [0.0]

        def http(method, url, body=None, timeout=15):
            calls.append(url)
            return 200, b'{"idToken": "T", "localId": "U", "expiresIn": "3600"}'
        get = gc.token_source(GOOD_CFG, http, clock=lambda: t[0])
        get(); get()
        self.assertEqual(len(calls), 1)
        t[0] = 3400
        get()
        self.assertEqual(len(calls), 2)




class UsageWiring(unittest.TestCase):
    def setUp(self):
        self.w = FakeWorld()
        self.t = [1000.0]
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.u = gg_usage.Usage(path=pathlib.Path(self.tmp.name) / "u.json", clock=lambda: self.t[0])
        self.c = gc.Cloud({"database_url": "https://x.firebasedatabase.app"}, token=False,
                          http=self.w.http, clock=lambda: self.t[0], usage=self.u)

    def test_pushes_are_counted(self):
        self.c.push_state()
        self.assertGreater(self.u.data["up"], 0)
        self.assertEqual(self.u.data["by_kind"]["state"], 0)
        self.assertGreater(self.u.data["by_kind"]["pi"], 0)
        self.c.watchers = {"a": {"ts": 1}, "b": {"ts": 1}}
        self.w.state["smoothed"] = 99
        self.c.push_state()
        self.assertGreater(self.u.data["by_kind"]["state"], 0)

    def test_preview_stops_when_month_is_spent(self):
        self.c.watchers = {"a": {"ts": 1}}
        self.assertTrue(self.c.push_preview())
        self.u.add("state", 11 * gg_usage.GB, watchers=1)
        self.c.tick()
        self.assertIsNone(self.u.preview_every(3))

    def test_usage_published(self):
        self.c.push_state()
        rep = self.c.push_usage()
        self.assertTrue(self.w.db["gg"]["usage"]["estimate"])
        self.assertEqual(rep["level"], "ok")


if __name__ == "__main__":
    unittest.main()

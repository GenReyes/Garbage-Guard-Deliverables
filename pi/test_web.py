import unittest

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

    def test_bye_removes_at_once(self):
        w.note_viewer("192.168.1.30", "app", now=1.0)
        w.forget_viewer("192.168.1.30", "app")
        self.assertEqual(w.viewers(now=1.1)["apps"], 0)


if __name__ == "__main__":
    unittest.main()

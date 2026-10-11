import unittest

import gg_web as w


class ViewerTests(unittest.TestCase):
    def setUp(self):
        w._viewers.clear()

    def test_kinds(self):
        self.assertEqual(w.client_kind({"User-Agent": "Dalvik/2.1.0 (Linux; U; Android 14)"}), "app")
        self.assertEqual(w.client_kind({"User-Agent": "Mozilla/5.0 Chrome"}), "browser")
        self.assertEqual(w.client_kind({"X-GG-Client": "android", "User-Agent": "x"}), "app")

    def test_counts_and_expiry(self):
        w.note_viewer("192.168.1.30", "app", now=100.0)
        w.note_viewer("192.168.1.31", "browser", now=100.0)
        v = w.viewers(now=105.0)
        self.assertEqual((v["apps"], v["browsers"]), (1, 1))
        self.assertEqual(w.viewers(now=100.0 + w.VIEWER_TTL + 1)["list"], [])

    def test_same_phone_counts_once(self):
        w.note_viewer("192.168.1.30", "app", now=1.0)
        w.note_viewer("192.168.1.30", "app", now=2.0)
        self.assertEqual(w.viewers(now=3.0)["apps"], 1)


if __name__ == "__main__":
    unittest.main()

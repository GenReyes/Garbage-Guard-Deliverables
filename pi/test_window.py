import unittest
from unittest import mock

import gg_window


def runner(results):
    calls = []

    def fake(cmd):
        calls.append(cmd)
        for key, val in results.items():
            if key in cmd:
                return val
        return True, ""
    fake.calls = calls
    return fake


class WindowTests(unittest.TestCase):
    def setUp(self):
        p = [mock.patch.object(gg_window.shutil, "which", return_value="/usr/bin/wlrctl"),
             mock.patch.object(gg_window.time, "sleep")]
        for x in p:
            x.start()
            self.addCleanup(x.stop)

    def test_minimize_uses_app_id_and_verifies(self):
        fake = runner({})
        with mock.patch.object(gg_window, "_run", fake):
            r = gg_window.minimize()
        self.assertEqual(r["how"], "minimized")
        self.assertEqual(fake.calls[0], ["wlrctl", "toplevel", "minimize", "app_id:chromium"])
        self.assertIn("state:minimized", fake.calls[1])

    def test_minimize_closes_when_window_did_not_hide(self):
        fake = runner({"state:minimized": (False, "")})
        with mock.patch.object(gg_window, "_run", fake):
            r = gg_window.minimize()
        self.assertEqual(r["how"], "closed")
        self.assertEqual(fake.calls[-1][0], "pkill")

    def test_minimize_closes_when_wlrctl_fails(self):
        fake = runner({"minimize": (False, "boom")})
        with mock.patch.object(gg_window, "_run", fake):
            r = gg_window.minimize()
        self.assertEqual(r["how"], "closed")

    def test_restore_focuses_when_visible(self):
        fake = runner({})
        with mock.patch.object(gg_window, "_run", fake):
            r = gg_window.restore()
        self.assertEqual(r["how"], "focused")

    def test_restore_relaunches_when_still_hidden(self):
        fake = runner({"state:-minimized": (False, "")})
        with mock.patch.object(gg_window, "_run", fake), \
                mock.patch.object(gg_window.subprocess, "Popen") as popen:
            r = gg_window.restore()
        self.assertEqual(r["how"], "launched")
        self.assertTrue(popen.called)


if __name__ == "__main__":
    unittest.main()

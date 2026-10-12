import os
import struct
import tempfile
import unittest

import gg_touch as t


def event(ty, code, val):
    return struct.pack("llHHi", 0, 0, ty, code, val)


def make_sysfs(root, name, absmask, props):
    d = os.path.join(root, name, "device")
    os.makedirs(os.path.join(d, "capabilities"))
    open(os.path.join(d, "capabilities", "abs"), "w").write(absmask)
    open(os.path.join(d, "properties"), "w").write(props)


class TouchTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.sys = self.tmp.name

    def test_finds_the_multitouch_screen_and_skips_touchpads_and_mice(self):
        make_sysfs(self.sys, "event3", "260800000000003", "2")
        make_sysfs(self.sys, "event4", "260800000000003", "5")
        make_sysfs(self.sys, "event5", "0", "0")
        self.assertEqual(t.find_devices(self.sys, "/dev/input"), ["/dev/input/event3"])

    def test_single_touch_screen_is_found(self):
        make_sysfs(self.sys, "event0", "3", "2")
        self.assertEqual(t.find_devices(self.sys, "/dev/input"), ["/dev/input/event0"])

    def test_multiword_mask_is_read(self):
        make_sysfs(self.sys, "event1", "0 260800000000003", "2")
        self.assertTrue(t.is_touchscreen(os.path.join(self.sys, "event1", "device")))

    def test_touch_down_calls_back_but_moves_and_lifts_do_not(self):
        calls = []
        now = [10.0]
        tc = t.Touch(lambda: calls.append(1), self.sys, clock=lambda: now[0])
        r, w = os.pipe()
        os.set_blocking(r, False)
        os.write(w, event(3, 0x35, 100) + event(3, 0x36, 50) + event(1, 0x14A, 0) + event(3, 0x39, -1))
        tc.pump(r)
        self.assertEqual(calls, [])
        os.write(w, event(3, 0x39, 7) + event(1, 0x14A, 1))
        tc.pump(r)
        self.assertEqual(len(calls), 1)
        now[0] += 0.4
        os.write(w, event(1, 0x14A, 1))
        tc.pump(r)
        self.assertEqual(len(calls), 1)
        now[0] += 1.0
        os.write(w, event(1, 0x14A, 1))
        tc.pump(r)
        self.assertEqual(len(calls), 2)

    def test_closed_device_is_reported(self):
        tc = t.Touch(lambda: None, self.sys)
        r, w = os.pipe()
        os.close(w)
        self.assertFalse(tc.pump(r))

    def test_not_ready_without_a_readable_screen(self):
        tc = t.Touch(lambda: None, self.sys, devdir=self.sys)
        self.assertFalse(tc.start())
        self.assertFalse(tc.ready)

    def test_ready_when_a_device_opens(self):
        make_sysfs(self.sys, "event9", "260800000000003", "2")
        dev = tempfile.mkdtemp(dir=self.tmp.name)
        open(os.path.join(dev, "event9"), "w").close()
        tc = t.Touch(lambda: None, self.sys, devdir=dev)
        self.assertTrue(tc.open_all())
        self.assertTrue(tc.ready)
        for fd in tc.fds:
            os.close(fd)


if __name__ == "__main__":
    unittest.main()

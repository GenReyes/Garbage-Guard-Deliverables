"""Watches the touchscreen directly so a tap can wake the screen.

While the output is switched off the compositor may not deliver taps to the
dashboard, so this reads the kernel's input events itself. It needs read
access to /dev/input (the default Pi user is in the input group). If no
touchscreen can be read, `ready` stays False and the screen saver uses Dim
instead of Off.
"""
import glob
import os
import select
import struct
import threading
import time

EVENT = struct.Struct("llHHi")
EV_KEY, EV_ABS = 1, 3
BTN_TOUCH = 0x14A
ABS_X, ABS_Y, ABS_MT_X, ABS_MT_TRACKING_ID = 0, 1, 0x35, 0x39
PROP_DIRECT = 1


def _mask(path):
    try:
        words = open(path).read().split()
    except OSError:
        return 0
    val = 0
    for w in words:
        val = (val << (4 * len(w))) | int(w, 16)
    return val


def is_touchscreen(dev):
    absm = _mask(os.path.join(dev, "capabilities", "abs"))
    direct = _mask(os.path.join(dev, "properties")) >> PROP_DIRECT & 1
    multi = absm >> ABS_MT_X & 1
    single = (absm & 1) and (absm >> ABS_Y & 1)
    return bool(direct and (multi or single))


def find_devices(sysfs="/sys/class/input", devdir="/dev/input"):
    out = []
    for p in sorted(glob.glob(os.path.join(sysfs, "event*"))):
        if is_touchscreen(os.path.join(p, "device")):
            out.append(os.path.join(devdir, os.path.basename(p)))
    return out


def is_touch(ev_type, code, value):
    return (ev_type == EV_KEY and code == BTN_TOUCH and value == 1) or \
           (ev_type == EV_ABS and code == ABS_MT_TRACKING_ID and value >= 0)


class Touch:
    def __init__(self, on_touch, sysfs="/sys/class/input", devdir="/dev/input", clock=time.monotonic):
        self.on_touch = on_touch
        self.sysfs, self.devdir = sysfs, devdir
        self.clock = clock
        self.ready = False
        self.fds = {}
        self.last = 0.0

    def open_all(self):
        for path in find_devices(self.sysfs, self.devdir):
            try:
                fd = os.open(path, os.O_RDONLY | os.O_NONBLOCK)
            except OSError as e:
                print(f"[TOUCH] cannot read {path}: {e}", flush=True)
                continue
            self.fds[fd] = path
        self.ready = bool(self.fds)
        return self.ready

    def pump(self, fd):
        try:
            data = os.read(fd, EVENT.size * 64)
        except BlockingIOError:
            return True
        except OSError:
            data = b""
        if not data:
            return False
        for i in range(0, len(data) - EVENT.size + 1, EVENT.size):
            _, _, t, c, v = EVENT.unpack_from(data, i)
            if is_touch(t, c, v):
                now = self.clock()
                if now - self.last >= 1.0:
                    self.last = now
                    self.on_touch()
        return True

    def start(self):
        if not self.open_all():
            print("[TOUCH] no readable touchscreen; Off mode will use Dim", flush=True)
            return False

        def loop():
            while self.fds:
                ready, _, _ = select.select(list(self.fds), [], [], 2.0)
                for fd in ready:
                    if not self.pump(fd):
                        os.close(fd)
                        self.fds.pop(fd, None)
                if not self.fds:
                    self.ready = False
        threading.Thread(target=loop, daemon=True).start()
        return True

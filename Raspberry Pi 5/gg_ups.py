"""Waveshare UPS HAT (E) reader.

The HAT's MCU answers on I2C bus 1 at 0x2D. Register map from the Waveshare
"UPS HAT (E) Register" page; every value is 16-bit little-endian, and only
the battery current is signed (positive charging, negative feeding the Pi).
"""
import threading
import time

ADDR = 0x2D
BUS = 1
POLL_S = 2.0


def _u16(b, i):
    return b[i] | (b[i + 1] << 8)


def _s16(b, i):
    v = _u16(b, i)
    return v - 0x10000 if v & 0x8000 else v


class Ups:
    def __init__(self, bus=BUS, addr=ADDR):
        self.addr = addr
        self.lock = threading.Lock()
        self.last = {"ok": False, "error": "not read yet"}
        try:
            from smbus2 import SMBus
            self.bus = SMBus(bus)
        except Exception as e:  # no smbus2, no I2C, not a Pi
            self.bus = None
            self.last = {"ok": False, "error": str(e)}
        if self.bus is not None:
            threading.Thread(target=self._loop, daemon=True).start()

    def _read(self):
        r = self.bus.read_i2c_block_data
        st = r(self.addr, 0x02, 1)[0]
        vb = r(self.addr, 0x10, 6)
        bt = r(self.addr, 0x20, 12)
        cl = r(self.addr, 0x30, 8)
        volts = _u16(bt, 0) / 1000.0
        amps = _s16(bt, 2) / 1000.0
        on_ac = bool(st & 0x20)
        charging = bool(st & 0x80)
        state = st & 0x07
        if state == 0b101:
            mode = "full"
        elif charging:
            mode = "charging"
        elif on_ac:
            mode = "idle"
        else:
            mode = "discharging"
        if charging:
            left = _u16(bt, 10)
        elif not on_ac:
            left = _u16(bt, 8)
        else:
            left = None
        return {
            "ok": True,
            "percent": min(100, _u16(bt, 4)),
            "voltage_v": round(volts, 3),
            "current_a": round(amps, 3),
            "power_w": round(volts * amps, 2),
            "capacity_mah": _u16(bt, 6),
            "minutes_left": left,
            "on_ac": on_ac,
            "charging": charging,
            "state": mode,
            "vbus_v": round(_u16(vb, 0) / 1000.0, 3),
            "vbus_a": round(_u16(vb, 2) / 1000.0, 3),
            "vbus_w": round(_u16(vb, 4) / 1000.0, 2),
            "cells_v": [round(_u16(cl, i) / 1000.0, 3) for i in (0, 2, 4, 6)],
            "t": time.time(),
        }

    def _loop(self):
        while True:
            try:
                data = self._read()
            except OSError as e:
                data = {"ok": False, "error": f"I2C read failed: {e}"}
            with self.lock:
                self.last = data
            time.sleep(POLL_S)

    def status(self):
        with self.lock:
            return dict(self.last)

"""Plays a sweep of tones so the buzzer can be judged by ear.

  python buzzer_bench.py gpio      software PWM (what runs now)
  python buzzer_bench.py sysfs     hardware PWM (needs the overlay)
  python buzzer_bench.py gpio 2000 2400   only these frequencies

Stop the detector first (or run this before it starts): it holds the pin.
"""
import sys
import time

import gg_buzzer as g

kind = sys.argv[1] if len(sys.argv) > 1 else "gpio"
freqs = [int(a) for a in sys.argv[2:]] or [1000, 1500, 2000, 2500, 3000, 3500, 4000]
backend = g.make_backend(kind)
print(f"backend {kind}: available={backend.available} note={backend.note}")
if backend.available:
    for level_name in ("low", "medium", "high"):
        print(f"--- volume {level_name}")
        for hz in freqs:
            print(f"{hz} Hz")
            backend.tone(hz, g.VOLUMES[level_name])
            time.sleep(0.7)
            backend.silence()
            time.sleep(0.2)
    backend.close()

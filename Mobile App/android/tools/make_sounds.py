"""Builds the app's own sounds from the Pi buzzer's sound table.

Every sound the Pi buzzer can play has the same tune in the app, so a key
pressed on the phone sounds like the same key on the touchscreen. The tunes
are read from SOUNDS in "Raspberry Pi 5/gg_buzzer.py", played one octave
lower and as soft sine notes (the phone speaker makes the buzzer's square
wave sound harsh), and written to res/raw/gg_sfx_<name>.wav.

Run from anywhere after the Pi's sound table changes:
    python3 "Mobile App/android/tools/make_sounds.py"
"""
import ast
import math
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
TABLE = ROOT / "Raspberry Pi 5" / "gg_buzzer.py"
OUT = ROOT / "Mobile App" / "android" / "app" / "src" / "main" / "res" / "raw"
RATE = 22050
SHIFT = 0.5          # one octave down
ATTACK = 0.004       # seconds
RELEASE = 0.012      # seconds
MIN_NOTE = 0.022     # very short buzzer clicks are stretched so a phone can sound them
# The sounds the app plays (see Sfx in notify/Sounds.kt). The rest are Pi-only.
APP = {"tap", "switch_on", "switch_off", "choice", "step_up", "step_down", "tab",
       "open", "back", "confirm", "danger", "denied", "logo", "theme_light",
       "theme_dark", "point", "point_undo", "boot", "saved", "cam_lost",
       "cam_back", "wifi_ok", "wifi_fail"}


def read_table():
    tree = ast.parse(TABLE.read_text(encoding="utf-8"))
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(getattr(t, "id", "") == "SOUNDS" for t in node.targets):
            return ast.literal_eval(node.value)
    raise SystemExit("SOUNDS not found in " + str(TABLE))


def note(hz, secs, level):
    n = int(RATE * secs)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / ATTACK) * min(1.0, (secs - t) / RELEASE)
        env *= math.exp(-2.2 * t / max(secs, 0.05))
        phase += 2 * math.pi * hz / RATE
        s = math.sin(phase) + 0.18 * math.sin(2 * phase)
        out.append(level * env * s / 1.18)
    return out


def render(seq, level):
    samples = []
    for hz, ms in seq:
        secs = max(ms / 1000.0, MIN_NOTE if hz else 0)
        samples += note(hz * SHIFT, secs, level) if hz else [0.0] * int(RATE * secs)
    return samples + [0.0] * int(RATE * 0.01)


def write(path, samples):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in samples))


def main():
    sounds = read_table()
    missing = APP - sounds.keys()
    if missing:
        raise SystemExit("not in the Pi's table: " + ", ".join(sorted(missing)))
    for name in sorted(APP):
        _prio, group, seq = sounds[name]
        # Button sounds stay quieter than events, as on the Pi.
        level = 0.5 if group == "ui" else 0.75
        write(OUT / f"gg_sfx_{name}.wav", render(seq, level))
    print(f"wrote {len(APP)} sounds to {OUT}")


if __name__ == "__main__":
    main()

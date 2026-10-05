# Garbage-Guard — Deliverables

Capstone project: computer-vision detection of floating solid waste in
creeks, running on a Raspberry Pi 5 with a Hikvision camera over RTSP.

## Running it

```
source ~/gg-env/bin/activate
cd ~/gg
./start.sh
```

Opens the dashboard fullscreen at `http://127.0.0.1:8080`. `Ctrl+C` stops
everything and closes the browser.

## Files

| File | Purpose |
|---|---|
| `garbageguard_live.py` | Detection engine: camera, model, counting, alerts |
| `gg_counter.py` | Accumulation counting and ROI logic, unit tested |
| `gg_web.py` | Local web server backing the dashboard |
| `dashboard.html` | Touchscreen dashboard UI |
| `gg_mock_server.py` | Fake API server for app development without the Pi |
| `test_counter.py` | Unit tests for the counting logic — `python3 test_counter.py` |
| `start.sh` | Launcher: checks the camera, opens the dashboard fullscreen |

## Not in this repo

`camera.txt` (RTSP credentials) and `gg_settings.json` (per-device settings)
are excluded on purpose — see `.gitignore`. Create `camera.txt` on each
device with the camera's RTSP URL as its only line.

See `GarbageGuard_API.md` and `GarbageGuard_Field_Reference.md` for the full
API reference and field operating instructions.

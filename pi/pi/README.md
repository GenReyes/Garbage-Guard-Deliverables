# pi/ — the Raspberry Pi side

This is the actual system: the part that talks to the camera, runs the
model, counts garbage, and serves the touchscreen dashboard. It's the half
of the project that's been changing constantly — new UI styling, mute,
fullscreen, deletion, scaling fixes. If you're Brian or Gen working on the
detection or dashboard, this folder is yours.

If you're King Perth working on the mobile app, you don't need anything in
here except `dashboard.html`, and only as a reference — see
[`../mobile-app/README.md`](../mobile-app/README.md) instead. You don't have
a camera, a Pi, or the model, so none of the Python here will run for you.

## Files

| File | What it does |
|---|---|
| `garbageguard_live.py` | The detection engine. Loads the NCNN model, reads the camera over RTSP, runs ByteTrack, applies the ROI, and serves the web API. This is what you run. |
| `gg_counter.py` | The accumulation counting and ROI-filtering logic, pulled out on its own so it can be unit tested without a camera or a model. |
| `gg_web.py` | The HTTP server behind the dashboard: serves `dashboard.html`, the live frame, and the `/api/*` endpoints. |
| `dashboard.html` | The touchscreen UI. Single file, no build step, no dependencies — open it in any browser once the server is running. |
| `test_counter.py` | Unit tests for `gg_counter.py`. Run these any time the counting logic changes, with no camera needed. |
| `start.sh` | The launcher. Checks the camera's reachable, activates the Python environment, opens the dashboard fullscreen, and starts detection. |
| `GarbageGuard_Field_Reference.md` | Everything needed to run this at the actual creek site: wiring, startup, ROI setup, calibration, troubleshooting. |

## Not in this repo, on purpose

Two files have to exist on the Pi but are never committed, because they
hold secrets or change per device:

- **`camera.txt`** — one line, the camera's RTSP URL including its
  password. Create it yourself on each Pi.
- **`gg_settings.json`** — detection confidence, accumulation threshold,
  and the ROI polygon. Generated automatically the first time the script
  runs, then it persists across restarts.

Both are listed in the repo's `.gitignore`. If you clone this fresh, the
script creates `gg_settings.json` on its own; you create `camera.txt`
yourself, one line, the RTSP URL.

## Running it

```bash
source ~/gg-env/bin/activate
cd ~/gg
./start.sh
```

This checks the camera is reachable, brings up the network link if it
isn't, and opens the dashboard fullscreen at `http://127.0.0.1:8080`.
`Ctrl+C` stops detection and closes the browser.

If `start.sh` isn't executable yet:

```bash
chmod +x start.sh
```

## Running the tests

No camera, no model, no Pi required — this runs anywhere Python 3 is
installed:

```bash
python3 test_counter.py
```

Expect `Ran 19 tests` and `OK`. If you change anything in `gg_counter.py`,
run this before anything else.

## What depends on what

```
start.sh
  └─ garbageguard_live.py
       ├─ gg_counter.py      (counting, ROI math — imported directly)
       └─ gg_web.py          (HTTP server — imported directly)
                └─ dashboard.html  (served as a static file, not imported)
```

`dashboard.html` only talks to the other three over HTTP, the same way a
phone would. That's deliberate — it's what makes the API in
`../mobile-app/GarbageGuard_API.md` an accurate description of this system
rather than a guess.

## Requirements on the Pi

- Python 3 with a virtual environment at `~/gg-env` containing `ultralytics`,
  OpenCV, and `lap`
- The NCNN model folder, `garbageguard_v5_best_ncnn_model/` (not in this
  repo — it's a 9.3 MB binary; copy it onto the Pi directly rather than
  committing it)
- A reachable camera and a `camera.txt` pointing at it

Nothing here needs internet access to run, only to install.

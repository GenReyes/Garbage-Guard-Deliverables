# pi/ — the Raspberry Pi side

This is the actual system: the part that talks to the camera, runs the
model, counts garbage, and serves the touchscreen dashboard. It's the half
of the project that's been changing constantly — new UI styling, mute,
fullscreen, deletion, scaling fixes. If you're Brian or Gen working on the
detection or dashboard, this folder is yours.

If you're working on the mobile app (Brian), you don't need anything in
here except `dashboard.html`, and only as a reference — see
[`../mobile-app/README.md`](../mobile-app/README.md) instead. The app is
built against the mock server, so none of the Python here has to run for it.

## Files

| File | What it does |
|---|---|
| `garbageguard_live.py` | The detection engine. Loads the NCNN model, reads the camera over RTSP, runs ByteTrack, applies the ROI, and serves the web API. This is what you run. |
| `gg_counter.py` | The accumulation counting and ROI-filtering logic, pulled out on its own so it can be unit tested without a camera or a model. |
| `gg_web.py` | The HTTP server behind the dashboard: serves `dashboard.html`, the live frame, and the `/api/*` endpoints. |
| `dashboard.html` | The touchscreen UI. Single file, no build step, no dependencies — open it in any browser once the server is running. |
| `test_counter.py` | Unit tests for `gg_counter.py`. Run these any time the counting logic changes, with no camera needed. |
| `gg_net.py` | Wi-Fi helper. Wraps `nmcli` to read network status, scan, connect and fix the camera-cable route. Has a fake mode for testing without a Pi. |
| `test_net.py` | Unit tests for `gg_net.py`. No Pi or NetworkManager needed. |
| `gg_snapcard.py` | Draws the info card under each saved snapshot (event, time, items vs limit, per-class counts), so the photo itself is never covered. |
| `test_snapcard.py` | Unit tests for `gg_snapcard.py`. |
| `test_web.py` | Unit tests for the connected-phone tracking in `gg_web.py`. |
| `wifi.html` | The startup splash (`/start`) and the network screen (`/wifi`): live status of Wi-Fi, the internet cable and the camera cable, full details per connection, Wi-Fi setup with an on-screen keyboard, and checked IP settings. |
| `install_autostart.sh` | One-time installer that makes Garbage-Guard start at boot. `--remove` undoes it. |
| `start.sh` | The launcher. Checks the camera's reachable, activates the Python environment, opens the dashboard fullscreen (via `/start`), and starts detection. |
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

Expect `Ran 24 tests` and `OK`. If you change anything in `gg_counter.py`,
run this before anything else.

```bash
python3 test_net.py
```

Expect `Ran 27 tests` and `OK`.

```bash
python3 test_window.py
```

Expect `Ran 5 tests` and `OK`.

```bash
python3 test_snapcard.py
python3 test_web.py
```

Expect `Ran 5 tests` and `Ran 7 tests`, both `OK`.

## Starting at boot, and Wi-Fi setup

```bash
cd ~/gg
./install_autostart.sh
```

This adds Garbage-Guard to the desktop's autostart list. After a reboot the
Pi opens the dashboard fullscreen by itself. It also offers to turn off screen
blanking. Desktop auto login must be on (`sudo raspi-config`, System Options,
Boot / Auto Login, Desktop Autologin). Undo with `./install_autostart.sh --remove`.
Boot messages go to `~/gg/gg.log`.

To reach the Pi desktop by touch (for settings or fixes), tap the minimize
button next to Wi-Fi on the dashboard. The dashboard window is minimized and
detection keeps running. Tap the **Garbage-Guard** icon on the desktop or in
the app menu (or its taskbar button) to bring the dashboard back fullscreen.
This uses `wlrctl`, which the installer offers to add; without it the button
closes the window instead and the same icon reopens it.

**Camera refresh button** (circular arrows, next to Capture):

- **Tap:** reconnects the camera. Use it after unplugging and replugging the
  camera cable. The arrows keep turning until the picture is back.
- **Hold 5 seconds:** restarts all of Garbage-Guard (detection, camera and
  dashboard). The outline fills while you hold; let go early to cancel.
  Settings and history are kept.

The Pi also reconnects by itself if no frame arrives for 12 seconds. The
status pill reads **No camera** when the Pi is fine but the camera is not,
and **No link to Pi** only when the dashboard cannot reach the Pi at all.

**Battery panel (Waveshare UPS HAT (E)).** Tap the panel for full details:
voltage, current, power, the four cell voltages and the charger input. The dashboard reads the HAT over I2C.
Two one-time steps on the Pi:

```bash
sudo raspi-config nonint do_i2c 0
source ~/gg-env/bin/activate && pip install smbus2
```

Reboot after enabling I2C. Without them the panel shows "No UPS found" and
everything else works normally.

**"Power supply is not capable of supplying 5V at 5A" pop-up.** The UPS HAT
does not announce 5 A over USB-C the way the official Pi supply does, so the
Pi assumes 3 A. Tell the Pi the supply is good for 5 A, once:

```bash
sudo rpi-eeprom-config --edit
```

Add the line `PSU_MAX_CURRENT=5000`, save (Ctrl+O, Enter, Ctrl+X), then
`sudo reboot`.

On every start the browser opens `/start`:

- Wi-Fi connected: it goes straight to the dashboard.
- No Wi-Fi after about 25 seconds: it opens the Wi-Fi setup screen. Pick a
  network, type the password on the on-screen keyboard, done. **Skip** or
  **Continue without Wi-Fi** goes to the dashboard, which works fine offline.
- No Wi-Fi adapter: straight to the dashboard.

Later, the small Wi-Fi button next to Dark mode reopens setup. The setup
endpoints only answer to the Pi's own screen, so a phone on the network cannot
change the Wi-Fi.

To try the screens on any computer with no Pi, start the server with
`GG_FAKE_NET=offline` (or `connected`, `conflict`, `nohw`). The fake network
accepts the password `goodpass`.

This is not verified on real Pi hardware yet. See the checklist in
`GarbageGuard_Field_Reference.md`, section 2.

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

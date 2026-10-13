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
| `gg_cloud.py` | Firebase sync. Copies state, history, compressed snapshots and a preview still to a Firebase Realtime Database, and runs commands phones leave there. Uses only the Python standard library plus OpenCV, so nothing new to install. |
| `test_cloud.py` | Unit tests for `gg_cloud.py`, against a fake Firebase. No internet needed. |
| `gg_clock.py` | Reports whether the Pi's clock is synchronised, so the dashboard can warn when the time may be wrong. |
| `test_clock.py` | Unit tests for `gg_clock.py`. |
| `gg_usage.py` | Estimates how much of Firebase's free monthly allowance has been used, and slows or stops the preview before it runs out. |
| `test_usage.py` | Unit tests for `gg_usage.py`. |
| `gg_screen.py` | The Pi screen saver: turns the HDMI output off (or dims) after a timer, shorter on battery, wakes on touch or alert. |
| `gg_power.py` | Clean shutdown before the UPS battery runs flat: debounce, 5 minute countdown, Cancel, 5% floor, boot grace. |
| `test_screen.py`, `test_power.py` | Unit tests for the two above, with a fake `wlr-randr` and a fake UPS. |
| `gg_touch.py` | Reads the touchscreen directly so a tap can wake the screen while the output is off. |
| `test_touch.py` | Unit tests for `gg_touch.py`. |
| `firebase.rules.json` | Security rules to paste into the Firebase console. |
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
python3 test_cloud.py
python3 test_usage.py
python3 test_clock.py
python3 test_screen.py
python3 test_touch.py
python3 test_power.py
```

Expect `Ran 5 tests`, `Ran 13 tests`, `Ran 22 tests`, `Ran 11 tests`, `Ran 17 tests`, `Ran 7 tests` and `Ran 20 tests`, all `OK`.

## Screen saver and low-battery shutdown

Both live in the **Settings** tab of the dashboard, the third tab after Monitor
and History. The screen saver has its own card and the low-battery shutdown is
in the Power card. Press **Save** at the bottom after changing a value; leaving
the tab without saving drops the change. The Power card also has a red
**Shut down** button. It asks "Shut down the Pi?", writes
a "Manual shutdown" row to History, sends it to Firebase and powers off. Start
the Pi again with the switch on the UPS HAT.

**One-time setup on the Pi**

```bash
sudo raspi-config        # Display Options > Screen Blanking > No
```

Off mode uses `wlr-randr`, which is already installed. (`wlopm` does not work
on this Pi's display driver: it reports the screen as off but cannot turn it
on, so it is not used.) Test it by hand before relying on it:

```bash
wlr-randr
wlr-randr --output HDMI-A-2 --off; sleep 10; wlr-randr --output HDMI-A-2 --on
```

Replace `HDMI-A-2` with the name `wlr-randr` shows. The panel should go dark and
come back. The Pi's own screen found this way is switched automatically; the
name is never typed into the program. After it comes back, check that the
dashboard still fills the screen and that touch lands where you tap.

Waking is checked, not assumed: the Pi reads back whether the screen really
came on, and tries a plain switch-on, then the screen's own mode, then an
off-and-on cycle. If all three fail it keeps retrying, shows a red line in the
Screen saver card, and uses Dim instead of Off until someone taps Off again. A screen
left off when the program starts is switched back on.

While the output is off the desktop may not pass taps on, so `gg_touch.py`
reads the touchscreen itself. It needs read access to `/dev/input`, which the
default Pi user has through the `input` group (check with `groups`). If no
touchscreen can be read, the log shows `[TOUCH] no readable touchscreen` and Off
mode uses Dim instead. If the output is still off when the program starts (for
example after a crash), it is switched back on at start-up. If the cheap board
keeps its backlight lit with the output off, use Dim and note it as a hardware
limitation.

Auto shutdown calls `systemctl poweroff`. Test it once from the way the
program is really started (the autostart entry), not from your own terminal. If
it is refused, the log shows `[POWER] ... refused` and you add a sudoers line:

```bash
echo 'pi ALL=(root) NOPASSWD: /sbin/poweroff' | sudo tee /etc/sudoers.d/gg-poweroff
```

(replace `pi` with the Pi's user name). The Pi stays off after a low-battery
shutdown until someone switches the Waveshare UPS HAT's own power switch off and
on again; it does not restart by itself when the charger returns. The full
shutdown takes 30 to 60 seconds on the test Pi (the screen goes dark 2 to 3
seconds after the countdown reaches 0:00, and the board and its peripherals take
longer to finish).

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

## Firebase (phones away from the Pi's Wi-Fi)

Same Wi-Fi always comes first: phones on the Pi's network talk to the Pi
directly, with live video and full-size snapshots. Firebase is the fallback
for phones elsewhere, and the Pi keeps it up to date either way.

1. Set up the Firebase project once, following section 5 of
   `mobile-app/FIREBASE.md` (database in Singapore, Email/Password sign-in, a
   user for the Pi and one per phone, the `gg_users` list, and the rules from
   `firebase.rules.json`).
2. On the Pi: Dashboard → Wi-Fi button → **Firebase** card. Fill in the
   database URL, the Web API key, and the **Pi's** email and password. The
   chips above the keyboard (`https://`, `-default-rtdb`,
   `.asia-southeast1`, `.firebasedatabase.app`, `AIza`) save typing.
3. Press **Test** to only check, or **Test and save** to check and save.
   Saving only happens when the test passes, and that set becomes the
   **working set**. **Restore working** puts it back after a bad edit.
4. **Sync on / Off** pauses it without losing the settings.

The settings live in `~/gg/gg_firebase.json` (and the working set in
`gg_firebase.good.json`), readable only by the Pi user. Both are git-ignored,
so the password never reaches GitHub. Status shows on the Network screen's
Firebase card and in the dashboard's phone panel (**Firebase synced** /
**Firebase error** / **Firebase off**). Phones using Firebase appear in that
panel with the label **Firebase**.

Live video does not go through Firebase. Phones away from the Wi-Fi get a
still picture every 3 seconds, and only while they say they are watching.

### Staying inside the free allowance

The free Spark plan gives 10 GB of downloads a month, 1 GB of storage and 100
connections at once. Only downloads are at risk, and the preview is what
spends them: roughly 30–50 MB for every hour a phone sits on the live screen.
Everything else, idle, is a few MB a month.

Firebase's own usage figure can only be read with a Google Cloud service
account, which is not something to keep on the Pi. So `gg_usage.py` counts
what the Pi sends, multiplied by how many phones were watching at the time,
and keeps a running total in `gg_usage.json`:

| When | What the Pi does on its own |
|---|---|
| 70% of the month used | the bar turns amber |
| 90% used | the preview slows to one picture every 10 s |
| 100% used | the preview stops; state, alerts and commands carry on |
| 300 MB of preview in one day | the preview pauses until midnight UTC |

The bar shows on the Firebase card and in the dashboard's phone panel. It is
an estimate and is labelled "about": it can be a third out either way, and
the exact figure is in the Firebase console under Realtime Database → Usage.
Counters reset on the 1st. Going over the limit on Spark blocks the database
until the next month; nothing is charged.

**Running a second project (for example Smart-bin) is best done in its own
Firebase project**, because the limits are counted per project.

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

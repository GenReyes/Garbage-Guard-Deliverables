# Pi Screen Saver and Low Battery Shutdown: Plan for the Dashboard and App

Hardware: Raspberry Pi 5 (Raspberry Pi OS Bookworm, labwc Wayland desktop), unbranded 7 inch 1024x600 IPS HDMI display, 5 point capacitive touch over USB. The Pi side of the cable is micro HDMI (Pi 5 has no mini HDMI port).

## 1. What this screen can and cannot do

| Question | Answer |
|---|---|
| Can the Pi 5 turn the HDMI output off? | Yes. `wlopm --off '*'` puts the HDMI output to sleep (DPMS) and `wlopm --on '*'` wakes it. Chromium keeps running, nothing restarts. |
| Does the screen go dark when HDMI is off? | Most of these boards turn the backlight off after a short "No Signal" message, but some keep it lit. **Test this first** (step 2). |
| Can the Pi lower the real backlight? | Almost certainly not. Cheap HDMI boards have no software brightness control (no DDC/CI, no backlight driver). Brightness only changes with the board's own switch or buttons, if it has any. |
| So how does "dim" work? | A black layer over the dashboard with adjustable transparency. It looks dimmer but the backlight draws the same power, so it saves almost no screen power. State this plainly in the thesis as a hardware limitation; the real saving comes from Off mode. |
| Does touch work when the screen is off or dimmed? | Dimmed: yes. Off: **probably, but untested.** Touch runs on its own USB cable, which stays powered, but the compositor may swallow a tap while the output is asleep. Test it in step 2 before relying on it. |
| Real power saving | **Off mode** (backlight off, if step 2 confirms it) plus pausing the live video in the dashboard while asleep. The page currently fetches a frame every 120 ms, so stopping that cuts Pi CPU load in both modes. |

## 2. Test before writing code (5 minutes on the Pi)

Run in a terminal on the Pi desktop:

```bash
sudo apt install wlopm        # if not already there
wlr-randr                     # note the output name, usually HDMI-A-1
wlopm --off '*'; sleep 10; wlopm --on '*'
```

While it is off, check in a dim room whether the panel is fully black or still glowing / showing "No Signal". If a USB power meter is available, note the screen's current with the output on and off. Those numbers are useful for the thesis results chapter.

Then test touch while the output is off, because wake-on-touch depends on it:

1. Open the dashboard in Chromium. In the browser console run `addEventListener('pointerdown', () => console.log('tap'))`, or use any page that shows a tap.
2. Run `wlopm --off '*'`, wait 5 seconds, tap the screen, then run `wlopm --on '*'` and check whether `tap` was logged.
3. If the tap was **not** received, wake-on-touch needs another route: a small thread that watches the touch device (`/dev/input/event*` with `evdev`) and calls `touch()`. Plan for that before building the page side.

Also check the environment the app will run in. `wlopm` only works in a process that has `WAYLAND_DISPLAY` and `XDG_RUNTIME_DIR` set. Run the command from `start.sh` or the autostart entry (not your own terminal) and confirm it still works.

Also turn off the OS's own blanking so it does not fight ours: `sudo raspi-config` > Display Options > Screen Blanking > No.

## 3. Recommended behaviour

- Modes: **Off** (default), **Dim**, **Never**.
- Timer: default **10 minutes** with no touch, adjustable 1 to 60 minutes.
- **On battery timer**: when the UPS says the Pi is running on battery (`on_ac` false), use a shorter timer, default **1 minute**, adjustable 30 seconds to 60 minutes. This is what makes it a real battery saver: the screen is the biggest saving the Pi can make, and it only matters on battery. If the UPS cannot be read, use the normal timer.
- Dim level: default **20%** brightness, adjustable 5 to 50%. Only used in Dim mode.
- **Wake on alert**: on by default. A new accumulation alert wakes the screen so the operator sees it. This matters more than the saving itself on a monitoring device.
- First touch only wakes. The tap that wakes the screen must not press whatever button is under the finger.
- Detection, alerts, Firebase and the phone app keep running the whole time. Only the local screen sleeps.

If step 2 shows the backlight stays on with HDMI off, make **Dim** the default and note it in the thesis as a hardware limitation.

## 4. Shared contract (Pi and app use the same names)

### Settings (saved on the Pi, survive restart)

| Key | Type | Default | Meaning |
|---|---|---|---|
| `screen_mode` | `"off"` / `"dim"` / `"never"` | `"off"` | What happens after the timer runs out |
| `screen_timeout_s` | int, 60 to 3600 | `600` | Seconds without a touch, on charger |
| `screen_timeout_battery_s` | int, 30 to 3600 | `60` | Seconds without a touch, on battery |
| `screen_dim_pct` | int, 5 to 50 | `20` | Brightness kept in Dim mode |
| `screen_wake_on_alert` | bool | `true` | Wake on a new accumulation alert |

### New state fields in `GET /api/state`

All five settings above, plus:

| Field | Values | Meaning |
|---|---|---|
| `screen` | `"on"` / `"dim"` / `"off"` | What the Pi screen is doing now |

### `POST /api/screen_saver` (save settings, any client)

```json
{ "mode": "dim", "timeout_s": 600, "timeout_battery_s": 60, "dim_pct": 20, "wake_on_alert": true }
```

Any field may be sent alone. Values are clamped on the Pi. Returns `{"ok": true, "screen_mode": "...", "screen_timeout_s": 600, "screen_timeout_battery_s": 60, "screen_dim_pct": 20, "screen_wake_on_alert": true}`.

### `POST /api/screen` (sleep or wake now, any client)

```json
{ "action": "wake" }
{ "action": "sleep" }
```

Returns `{"ok": true, "screen": "on"}`.

### `POST /api/screen/touch` (local only, from the kiosk page)

No body. Resets the idle timer. Refuse non-loopback callers with 403 like `/api/window` does.

### Firebase

Add `"screen_saver": "/api/screen_saver"` and `"screen": "/api/screen"` to `COMMANDS` in `gg_cloud.py`, and the six new fields to `STATE_KEYS`, so the app can do the same over the internet.

**Push on change.** In standby the Pi only sends state every 120 seconds, so a phone on another network would see a screen change up to 2 minutes late. Whenever `screen` changes, tell the cloud thread to send state right away (one extra write per change, not a faster timer). The same applies to `shutdown_state` in section 7.

### History (optional)

A `screen_off` / `screen_on` row is not needed and would clutter the log. Leave it out.

## 5. Pi side (Genesis)

1. **`pi/gg_screen.py`** (new, same style as `gg_window.py`). Holds the timer and state.
   - `touch()`: record the time. If the screen is not `on`, wake it.
   - `tick()`: called about once a second (a small thread). When idle time passes `screen_timeout_s` and mode is not `never`, go to sleep.
   - Sleep in Off mode runs `wlopm --off '*'` and sets `screen = "off"`. Sleep in Dim mode only sets `screen = "dim"` (the page draws the dimming).
   - Wake runs `wlopm --on '*'` (harmless if already on), sets `screen = "on"` and resets the timer.
   - If `wlopm` is missing, fall back to Dim so the feature still does something.
   - Read and save the five settings through the engine's settings file, the same way `auto_mute` is stored.
   - Pick the timer from the UPS: `screen_timeout_battery_s` when `ups.status()` says `ok` and `on_ac` is false, otherwise `screen_timeout_s`. Share the one `Ups` object created in `serve()` (section 7) instead of making another.
   - Run `wlopm` with `WAYLAND_DISPLAY` and `XDG_RUNTIME_DIR` set explicitly (for example `WAYLAND_DISPLAY=wayland-0` and `XDG_RUNTIME_DIR=/run/user/$UID`), because the process may not inherit them. Log a warning if the command fails instead of failing silently, and fall back to Dim.
   - If step 2 showed taps are lost while the output is off, add the touch-device watcher thread described there.
   - When `screen` changes, ask the cloud thread to send state right away (section 4).
2. **`gg_web.py`**: add the three endpoints above and put the screen fields into `/api/state`.
3. **Wake on alert**: where the engine records a new `accumulation` alert, call `gg_screen.wake()` if `screen_wake_on_alert` is true.
4. **`dashboard.html`**:
   - On `pointerdown` anywhere, call `/api/screen/touch`, at most once every 15 seconds (no need to send every tap).
   - In `refresh()` (runs every 1 s), read `s.screen`:
     - `dim`: show a full screen black layer with opacity `1 - screen_dim_pct/100`.
     - `off`: show the same layer fully black.
     - `on`: hide it.
   - The layer sits above everything and catches the waking tap: on tap it calls `/api/screen/touch`, hides itself, and does nothing else. This is what stops a blind tap from pressing Delete all or Clear.
   - While `screen` is `dim` or `off`, stop `pullFrame` and resume it on wake.
   - If the page is hidden (dashboard minimized), stop sending touches. The Pi timer can still turn the screen off, which is fine.
5. **Settings card** in the empty space under Monitored area, styled like Detection settings:
   - Title "Screen saver", icon of a moon or display.
   - Mode: three buttons Off / Dim / Never.
   - Turn off after: stepper in minutes (1 to 60, step 1).
   - On battery, turn off after: stepper in seconds (30 to 3600, step 30), labelled "On battery".
   - Dim level: stepper in % (5 to 50, step 5), shown only in Dim mode.
   - Wake on alert: switch, same as the auto-mute switch.
   - Buttons: Save, Sleep now.
   - Hint line: "Touch the screen to wake it. The first tap only wakes."
6. **Docs and tests**: add the endpoints and fields to `mobile-app/GarbageGuard_API.md` (the "What changed" line at the top too), and a `test_screen.py` that fakes `wlopm` and checks timeout, the shorter on-battery timeout (and normal timeout when the UPS is unreadable), wake on touch, wake on alert, and clamping.

## 6. App side (Brian)

1. **Models**: add `screenMode`, `screenTimeoutS`, `screenTimeoutBatteryS`, `screenDimPct`, `screenWakeOnAlert`, `screen` to the state model, with defaults so older Pi builds without these fields still parse.
2. **Repository**: `setScreenSaver(mode, timeoutS, timeoutBatteryS, dimPct, wakeOnAlert)` and `screen(action)` over LAN, and the `screen_saver` / `screen` Firebase commands for remote.
3. **Settings screen**: a "Pi screen saver" card with the same controls and limits as the dashboard card. Hide the card if `screen_mode` is missing from state (older Pi).
4. **Monitor screen**: a small chip "Pi screen: On / Dimmed / Off" with a Wake button when it is not on. Handy for the defence demo: wake the Pi from the phone.
5. **Mock server**: add the fields and both endpoints to `gg_mock_server.py` so the app can be built before Genesis finishes.

## 7. Low battery auto shutdown

The Pi runs on the Waveshare UPS HAT (E). `gg_ups.py` already reads `percent`, `on_ac`, `charging` and `minutes_left` every 2 s. A clean shutdown before the battery dies protects the SD card and the SQLite log, which a sudden power cut can corrupt.

### Behaviour

- Setting: on by default, shut down at **15%** (adjustable 10 to 50%).
- **Unreadable gauge means do nothing.** If `ups.status()` has `ok: false` (I2C error, HAT missing), the logic goes to `off`, cancels any countdown, and never shuts down. A failed read must not look like 0%. Require several good readings in a row before the logic arms again.
- When the battery is **on battery power** (`on_ac` false) and at or below the setting for 30 seconds straight (the gauge jumps around, so one low reading is not enough), a **5 minute countdown** starts.
- During the countdown the Pi screen wakes, the dashboard shows a full width banner "Shutting down in 4:59. Plug in the charger or tap Cancel", and the app shows the same banner plus a notification. If the buzzer from the haptic plan is fitted, it beeps once a minute.
- **Cancel** stops the countdown and pauses auto shutdown. It re-arms on its own when the charger is plugged in or the battery climbs 5% above the setting, or when the user switches it on again.
- **Plugging in the charger** during the countdown cancels it automatically.
- **Hard floor at 5%**: cannot be cancelled. The HAT cuts power by itself soon after, so a clean shutdown with a 60 second warning is better than a crash.
- Before powering off the Pi writes a `low_battery_shutdown` history row and pushes it to Firebase, so the app shows why the Pi went offline instead of just "offline". The cloud thread normally sends alerts only every 10 seconds, so the shutdown code must call a "send now" on it and **wait for it to finish (up to 8 seconds)** before running poweroff. Also send state once with `shutdown_state` set so the app sees the final status.

"5 minutes before it reaches the percentage" is hard to do reliably because the HAT's time estimate swings a lot. Starting the 5 minute countdown **at** the percentage gives the same result, as long as the setting has a margin built in. That is why the default is 15% and not 5%.

### Booting near or below the threshold (Brian's worry)

There is no shutdown loop, for three reasons (and expect one visible effect, below):

1. **A halted Pi 5 stays off.** After `systemctl poweroff` it does not restart by itself. Someone has to press the power button or replug power, so it cannot cycle on its own.
2. **Boot grace period.** Nothing is checked for the first 3 minutes after boot. The gauge needs time to settle, and the operator needs time to see the screen.
3. **Always a countdown, never an instant shutdown** (except the 5% floor). If someone boots on a low battery, they see the banner and can tap Cancel or plug in. With the charger plugged in (`on_ac` true) auto shutdown never runs at all, so a Pi booted on a charger at 8% just charges.

The only case where the Pi turns off soon after boot is booting on battery below 5% with no charger. That is correct, because the battery is about to die anyway.

**What Brian will see in testing.** Booting on battery at 12% with a 15% setting is not a bug: the Pi shuts down about 8 minutes later (3 minutes grace + 30 seconds debounce + 5 minute countdown) unless someone taps Cancel or plugs in.

**Open decision: restarting after a low-battery shutdown.** The HAT keeps the Pi powered while halted, so it stays off even after the charger returns, until someone presses the power button. That is fine for the lab, but a monitoring device left alone stays dead. Decide before the defence whether to leave it (simplest) or add an auto-restart when the charger returns, and write the choice in the thesis either way.

### Shared contract

Settings (saved on the Pi): `auto_shutdown` (bool, default `true`), `auto_shutdown_pct` (int 10 to 50, default `15`).

Extra fields in `GET /api/battery` (already mirrored to Firebase under `battery`, so the app gets them for free):

| Field | Values | Meaning |
|---|---|---|
| `auto_shutdown` | bool | Feature switched on |
| `auto_shutdown_pct` | int | Threshold |
| `shutdown_state` | `"off"` / `"armed"` / `"counting"` / `"paused"` / `"boot_grace"` | Where the logic is now |
| `shutdown_in_s` | int or `null` | Seconds left while `counting` |

`POST /api/auto_shutdown`:

```json
{ "on": true, "pct": 15 }
{ "cancel": true }
```

Either field may be sent alone. `cancel` moves `counting` to `paused`. Add `"auto_shutdown": "/api/auto_shutdown"` to `COMMANDS` in `gg_cloud.py`.

**Firebase timing.** The `battery` block rides on state, which is sent every 120 seconds in standby. That is far too slow for a 5 minute countdown. Rule for the cloud thread: send state immediately whenever `shutdown_state` changes, and every 3 seconds while it is `counting` (send nothing extra otherwise). A Cancel sent from the app also takes up to the command poll interval to arrive (10 seconds in standby), so while `counting` the Pi polls commands every 2 seconds.

### Pi side (Genesis)

1. `pi/gg_power.py` (new): a thread that reads `ups.status()` every 2 s and runs the state machine above (including the unreadable-gauge rule and the "send now, then wait" step before poweroff). It shuts down with `systemctl poweroff` (works for the logged in desktop user on Pi OS without sudo; if it is refused, add a sudoers line for `/sbin/poweroff`). **Test it from the same way the app is started** (autostart or `start.sh`), not from your own terminal, because permissions can differ. The `gg_ups.Ups()` object is currently created inside `make_handler`, so create it once in `serve()` and pass it to both.
2. `gg_web.py`: the endpoint above and the extra fields in `/api/battery`.
3. `dashboard.html`: the countdown banner with a big Cancel button (wake the screen first, and the banner sits above the screen saver layer). Settings: put a small "Auto shutdown" row (switch plus % stepper) in the Screen saver card, renamed "Power", so it all fits in the one empty space.
4. `test_power.py` with a fake UPS: boot grace, 30 s debounce, cancel and re-arm, charger cancels, floor cannot be cancelled, never runs on AC, `ok: false` never shuts down or counts, shutdown waits for the cloud flush.

### App side (Brian)

1. Read the new `battery` fields. Show the countdown banner with Cancel on every screen while `shutdown_state` is `counting`.
   **Notification limit:** Realtime Database cannot wake an app that is closed. Real push notifications need Firebase Cloud Messaging with a Cloud Function, which requires the paid Blaze plan. For the demo, use a foreground service (with its persistent notification) that keeps listening while the user has the countdown option on, and say in the thesis that push messaging is the production route. The app should also show the last known time of the Pi's heartbeat so a stale banner is obvious.
2. Settings: "Auto shutdown" switch and % stepper in the same Power card.
3. History: show `low_battery_shutdown` rows with a battery icon.
4. Mock server: let it fake a battery that drains so the countdown can be tested without the Pi.

## 8. Order of work

1. Genesis runs the tests in step 2 and reports whether Off really goes black and whether a tap is received while the output is off.
2. Both agree on section 4 as written (change names now, not later). Also agree on the Firebase timing rules in sections 4 and 7.
3. Brian updates the mock server and app; Genesis builds the Pi side. Neither blocks the other.
4. Test together: timer, first-tap wake, wake on alert, wake from the app over LAN and over Firebase.
5. Test auto shutdown last on the real UPS: set the threshold just under the current percentage, unplug the charger, and check the countdown, Cancel, re-arm and shutdown.

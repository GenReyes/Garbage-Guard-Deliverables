# Garbage-Guard API — for the mobile app

Everything the mobile app needs to talk to the Raspberry Pi. App developer: Brian.

**What changed in this version (Pi update after 7.0):** a Pi screen saver
(`POST /api/screen_saver`, `POST /api/screen`, and six `screen*` fields in
`/api/state`) and a low-battery auto shutdown (`POST /api/auto_shutdown`, and
five new fields in `/api/battery`). A new history event, `low_battery_shutdown`,
is written just before the Pi powers itself off. The Firebase commands
`screen_saver`, `screen` and `auto_shutdown` run the same endpoints. Details
under "Screen saver" and "Low battery shutdown" below. Everything else is
unchanged.

**Earlier version:** auto-mute (`POST /api/auto_mute`, the
`auto_mute`, `auto_mute_s`, `auto_muted` and `mute_total` state fields, and
an `auto_muted` history event), plus the FPS fields `fps` and `cam_fps`.
Detection labels on `/frame.jpg` are now numbered from 1 for the objects in
view instead of showing the tracker's internal ID. Earlier additions below.


**Mute.** An operator can silence an active accumulation alert for a set
time. `POST /api/mute`, plus `muted` and `mute_left` in `/api/state`. Muting
and unmuting are logged as history rows, so a quiet stretch is explained
rather than looking like a missed detection.

**Read and unread.** Every history row carries a `read` flag, set through
`POST /api/alerts/read`. It lives on the server, not in browser storage, so
the touchscreen and the app agree on what has been seen.

The previous version added deletion: `POST /api/alerts/delete` removes
records by id, by date range, or all at once, and deletes the saved snapshot
images with them. Each row also carries an `id`.

The previous version added snapshots: a `snapshot` field on each row,
`GET /snapshots/<name>` to load the image, `POST /api/capture` to save one on
demand, and the `manual` event type. Everything else is unchanged.

**You do not need to run `garbageguard_live.py`.** It needs the camera, the
9.3 MB model, and a Linux environment with OpenCV, Ultralytics and `lap`
installed. That is why it failed last time. The app only makes HTTP requests.

---

## Getting started without the Pi

Run the mock server on your own laptop:

```bash
python3 gg_mock_server.py
```

Python 3 only. Nothing to install. It serves the exact same API with invented
data, including a placeholder camera frame and counts that drift up and down
so the UI has something to react to.

```
http://localhost:8080/api/state
```

From a phone or emulator, use your laptop's LAN IP instead of `localhost`.

When the real Pi is available, change one base URL and nothing else.

---

## Base URL

| Environment | URL |
|---|---|
| Mock server | `http://<your-laptop-ip>:8080` |
| Real Pi | `http://192.168.1.101:8080` |

The Pi and the phone must be on the same network. A router with **client
isolation** enabled will block this even when both devices are connected —
that has already bitten this project once.

---

## Endpoints

### `GET /api/state`

Poll about once per second. This drives the whole main screen.

```json
{
  "connected": true,
  "counts": { "bottle": 6, "bag": 3, "food_container": 3 },
  "raw": 12,
  "smoothed": 12,
  "threshold": 20,
  "rearm": 15,
  "conf": 0.40,
  "roi": [[0.1, 0.45], [0.92, 0.38], [0.95, 0.9], [0.06, 0.95]],
  "latency_ms": 67.4,
  "avg_latency_ms": 68.2,
  "fps": 14.8,
  "cam_fps": 14.9,
  "alert_active": false,
  "frames": 10432,
  "uptime_s": 745
}
```

| Field | Meaning |
|---|---|
| `connected` | Camera delivered a frame in the last 3 seconds. `false` with a working request means the Pi is fine but the camera is not: show "No camera". A failed request means "No link to Pi". |
| `reconnects` | How many times the Pi has restarted the camera stream (button or the 12 s no-frame watchdog). |
| `counts` | Per-class counts currently inside the ROI. Class names are fixed: `bottle`, `bag`, `food_container`. |
| `raw` | Items detected this frame. |
| `smoothed` | Median over the last 15 frames. **Display this one**, not `raw`. |
| `threshold` | Alert fires at this count. |
| `rearm` | Alert resets below this. Read-only, derived from `threshold`. |
| `conf` | Detection confidence as a fraction, 0.0–1.0. Multiply by 100 for display. |
| `roi` | Polygon points, normalised 0–1. Empty list means no ROI, whole frame active. |
| `latency_ms` | Current frame processing time. This is the thesis Performance Efficiency metric. |
| `fps` | Detection loop speed, frames actually processed per second (measured over the last 2 s). |
| `cam_fps` | Camera stream frame rate, frames per second arriving from the camera (measured over the last 2 s). Both read `0` while `connected` is false. |
| `alert_active` | Accumulation alert is currently raised. |
| `muted` | True while an operator has silenced the alert. |
| `mute_left` | Seconds of mute remaining. `null` means no expiry, `0` means not muted. |
| `mute_total` | Length of the current timed mute in seconds, for a countdown bar (`mute_left / mute_total`). `0` when not muted or muted with no expiry. |
| `auto_mute` | Auto-mute is switched on. |
| `auto_mute_s` | How long auto-mute silences each new alert. `0` means until someone unmutes. |
| `auto_muted` | The current mute was applied by auto-mute rather than by hand. |
| `screen` | What the Pi's own screen is doing now: `"on"`, `"dim"` or `"off"`. Detection and alerts keep running in every case. |
| `screen_mode` | `"off"` (backlight off after the timer, the default), `"dim"` (dark layer over the dashboard) or `"never"`. |
| `screen_timeout_s` | Seconds without a touch before the screen sleeps while on the charger. 60 to 3600, default 600. |
| `screen_timeout_battery_s` | The same, while the Pi runs on battery. 30 to 3600, default 60. |
| `screen_dim_pct` | Brightness kept in Dim mode. 5 to 50, default 20. |
| `screen_wake_on_alert` | A new accumulation alert wakes the screen. Default `true`. |
| `screen_note` | `null` normally. A sentence to show when the screen could not be switched back on: the Pi keeps retrying and stops using Off mode until someone taps Off again. Show it as a warning next to the screen saver settings. |
| `clock_synced` | `true` when the Pi's clock is synchronised with internet time. `false` after a power cut with no internet, when its time may be hours or days off. Show a warning next to any time taken from the Pi. Also in the Firebase `state`. |
| `clock_settling` | `true` for the first two and a half minutes after boot while `clock_synced` is `false`. The Pi is still asking an internet time server, so do not warn yet; treat it as synced for display. |
| `clock_zone` | Always `Asia/Manila`. |
| `cloud` | Firebase sync status: `{"enabled", "ok", "error", "watchers", "usage"}`. `watchers` is how many phones are watching through Firebase. `usage` is the Pi's estimate of this month's free allowance (same object as `/gg/usage`), or `null`. See `FIREBASE.md`. |
| `viewers` | Devices that polled `/api/state` in the last 4 s: `{"apps", "browsers", "list": [...]}`. Each list entry has `ip`, `kind` (`app` or `browser`), `id`, `device`, `os`, `browser`, `app_version`, `mode`, `since_s` (connected for), `age_s` (last poll), `hits`, `mac`, `hostname`, `ua`, and `via`: `direct` (the IP and MAC are the phone's own), `shared` (several phones arrive from one address, so the IP and MAC belong to a router in between, such as a second Wi-Fi router), `remote` (Tailscale or the internet; `mac` is null), or `cloud` (the phone reaches the Pi through Firebase; `ip` is `"Firebase"`, `mac` and `hostname` are null). Show a short note for `shared`, `remote` and `cloud` so the numbers are not mistaken for the phone's own. The Pi screen shows a phone badge with the count, and tapping it shows these details. See "Telling the Pi who you are" below. |

---

### `GET /frame.jpg`

Returns one JPEG of the annotated camera view, with detection boxes and the
ROI drawn on. Poll it roughly every 120 ms for a live view.

Add a changing query parameter to defeat caching:

```
/frame.jpg?t=1727534891234
```

There is also `/stream.mjpg` (multipart MJPEG), but polling proved more
reliable on the Pi's browser. Use polling unless you have a reason not to.

---

### `GET /api/alerts?limit=50`

`limit=0` returns every record (the dashboard's History does this). Each
reply carries a `sig` that changes whenever a record is added, deleted or
marked read. Send it back as `&sig=...` and, if nothing changed, the reply is
just `{"same": true, "sig": "..."}`, so polling stays cheap.

```json
{
  "alerts": [
    {
      "id": 42,
      "timestamp": "2026-09-28 20:51:30",
      "event": "accumulation",
      "smoothed_count": 22,
      "raw_count": 22,
      "n_bottle": 9,
      "n_bag": 6,
      "n_food_container": 7,
      "peak_confidence": 0.91,
      "threshold_used": 20,
      "read": 0,
      "snapshot_path": "/home/garbageguard/gg/snapshots/alert_20260928_205130_412233.jpg",
      "snapshot": "alert_20260928_205130_412233.jpg"
    }
  ]
}
```

Newest first. Six `event` values:

| Event | Meaning | Suggested display |
|---|---|---|
| `accumulation` | Threshold crossed. The real alert. | Red row |
| `suppressed` | A brief spike that smoothing filtered out. Not an alert. | Grey row |
| `test` | Someone pressed Test alert. | Amber row |
| `manual` | Someone pressed Capture snapshot. | Teal row |
| `muted` | An operator silenced the alert. | Grey row |
| `auto_muted` | Auto-mute silenced a new alert. The `accumulation` row before it is still recorded. | Grey row |
| `unmuted` | The mute was lifted, by hand or automatically. | Grey row |
| `low_battery_shutdown` | The Pi shut itself down because the battery was low. Written once, just before power off. | Orange row, battery icon |
| `manual_shutdown` | Someone pressed Shut down on the dashboard or in the app. Written once, just before power off. | Orange row, power icon |

`read` is `0` or `1`. Show unread rows at full weight and read rows dimmed,
and put the unread count on the history tab or badge.

`peak_confidence` is a fraction. Show as a percentage. It can be `null`.

**Use `snapshot`, not `snapshot_path`.** `snapshot` is the bare file name and
is only filled in when the image actually exists on the Pi. It is `null` when
there is no image, for example an old row from before this feature, or a test
alert pressed while the camera was offline. Always handle `null`: show
"No image" rather than a broken picture. `snapshot_path` is the Pi's internal
path and is kept only for the database.

Some timestamps from older rows use a `T` separator
(`2026-09-07T19:31:58`). Replace `T` with a space before displaying.

---

### `GET /snapshots/<name>`

Returns the saved JPEG for a history entry. Take `<name>` from the row's
`snapshot` field:

```
/snapshots/alert_20260928_205130_412233.jpg
```

The image is the camera view at the moment of the event, with the detection
boxes and the monitored-area outline. Snapshots saved from this version on
have a slim info strip added **below** the photo, one row about 7.5% of
the width in height (48 px on a 640 px wide picture): event, time and date,
items against the limit with a bar, the per-class counts and peak
confidence, in the dashboard's colours. Nothing is drawn over the photo.
Snapshots from 11 to 12 October 2026 have a taller card (about 20% of the
width), and older ones a dark band across the top.

It is evidence, so show it as large as the screen allows. The Pi's full-size
view fits the picture to the full screen height and puts Close and the
details in a narrow column beside it, rather than a bar underneath; the same
works well on a phone held sideways. If you crop thumbnails, crop from the
top so the photo stays in view.

Returns 404 for any name that is not a saved snapshot.

---

### `POST /api/settings`

```json
{ "conf": 40, "threshold": 20 }
```

`conf` accepts either a percentage (40) or a fraction (0.40). Both fields are
optional. Clamped server-side to 5–95% and a minimum threshold of 1.

Returns `{"ok": true, "settings": {...}}`. Settings persist on the Pi across
restarts.

---

### `POST /api/roi`

```json
{ "points": [[0.1, 0.45], [0.92, 0.38], [0.95, 0.9], [0.06, 0.95]] }
```

Coordinates are **normalised 0–1**, not pixels, so they survive a change of
resolution. Convert from a tap like this:

```
x = (tapX - imageLeft) / imageWidth
y = (tapY - imageTop)  / imageHeight
```

Fewer than 3 points clears the ROI. Returns the stored points.

---

### `POST /api/test_alert`

No body. Writes a `test` row to the log and saves a snapshot. Useful for
demonstrating the alert path without waiting for real garbage.

---

### `POST /api/capture`

No body. Saves a snapshot of the live view right now and logs it as a
`manual` row. Returns `{"ok": true}`, or `{"ok": false}` if the camera has
not delivered a frame yet. Refresh the history afterwards to show the row.

---

### `POST /api/mute`

Silences the accumulation alert.

```json
{ "seconds": 900 }
{ "seconds": 0 }
{ "cancel": true }
```

`seconds` greater than zero mutes for that long. `0` mutes with no expiry.
`cancel` lifts it immediately. Returns `{"ok": true, "muted": true,
"mute_left": 899}`.

On the touchscreen the mute bar is always on screen, disabled when there is
no alert, so the feature is discoverable rather than appearing only at the
moment something is wrong. Worth matching in the app.

**Three behaviours worth building around:**

While muted, a crossing of the limit does **not** create an `accumulation`
row and does not save a snapshot. The count keeps running and `alert_active`
still reports true, so show a muted state rather than the red alert.

The mute **clears itself** when the count falls back below the re-arm value.
That is deliberate: otherwise the next genuine accumulation would be silently
swallowed. Poll `/api/state` and follow `muted` rather than running your own
timer, so the app stays right across a reload.

Muting and unmuting each write a history row, so a quiet period is explained
in the record.

---

### `POST /api/auto_mute`

Arms or disarms auto-mute. When it is on, every new accumulation alert is
still recorded (row and snapshot) and is then muted straight away for the
chosen time, so the countdown starts on its own.

```json
{ "on": true, "seconds": 900 }
{ "on": false }
{ "seconds": 1800 }
```

Either field may be sent alone. `seconds` of `0` means until someone
unmutes. Returns `{"ok": true, "auto_mute": true, "auto_mute_s": 900}`.
The setting is saved on the Pi and survives a restart. Like a manual mute,
an auto-mute lifts itself when the count falls below the re-arm value.

---

### `POST /api/screen_saver`

Saves the Pi screen saver settings. Any field may be sent alone; values are
clamped on the Pi and kept across restarts.

```json
{ "mode": "dim", "timeout_s": 600, "timeout_battery_s": 60, "dim_pct": 20, "wake_on_alert": true }
```

Returns `{"ok": true, "screen_mode": "dim", "screen_timeout_s": 600,
"screen_timeout_battery_s": 60, "screen_dim_pct": 20, "screen_wake_on_alert": true}`.

Off mode switches the HDMI output off (with `wlr-randr`), which turns the backlight off on most of
these boards. Waking checks that the screen really came back: it tries a plain
switch-on, then the screen's own mode, then an off-and-on cycle, and keeps
retrying in the background. If it still cannot, `screen_note` explains it and
the Pi uses Dim until someone picks Off again, so a screen cannot be left dark
with no way to wake it. Dim mode cannot change the real backlight (the board has no
brightness control), so the Pi dashboard draws a dark layer and the power
saving is small. Changing the mode wakes a sleeping screen.

### `POST /api/screen`

Sleeps or wakes the Pi's screen now.

```json
{ "action": "wake" }
{ "action": "sleep" }
```

Returns `{"ok": true, "screen": "on"}`. Any other `action` returns `400`.
`sleep` works in every mode, including `never`. Handy for a "Wake the Pi"
button on the Monitor screen. Taps on the Pi's own screen go through
`POST /api/screen/touch`, which answers only to the Pi itself (`403 local_only`).

---

### `POST /api/auto_shutdown`

Settings and Cancel for the low-battery shutdown (see "Low battery shutdown"
below).

```json
{ "on": true, "pct": 15 }
{ "cancel": true }
```

Either field of the first form may be sent alone; `pct` is clamped to 10 to 50.
`{"cancel": true}` stops a running countdown and pauses auto shutdown. It
returns `409 {"ok": false, "error": "floor"}` if the battery is at the 5%
floor, because that shutdown cannot be cancelled. Switching `on` to `true`
re-arms a paused shutdown. Other answers: `{"ok": true, "auto_shutdown": true,
"auto_shutdown_pct": 15, "shutdown_state": "armed", "shutdown_in_s": null,
"shutdown_floor": false}`.

---

### `POST /api/shutdown`

Powers the Pi off now. The body must be `{"confirm": true}`; anything else
answers `400 {"ok": false, "error": "confirm_required"}`, so a stray request
cannot switch the Pi off. The app should ask "Shut down the Pi?" first and say
that it can only be started again with the switch on the UPS HAT.

The Pi writes a `manual_shutdown` history row, answers `{"ok": true}` at once,
then sends the history and state to Firebase and powers off a moment later.
`/api/battery` reports `shutdown_state: "powering_off"` in between. Expect the
Pi to go offline 30 to 60 seconds later, so show "Pi is shutting down" rather
than an error. If the Pi cannot power off, the state returns to normal and
nothing else changes. A second request while it is powering off answers
`{"ok": true, "already": true}`. Over Firebase this is the `shutdown` command
with `{"confirm": true}`.

---

### `POST /api/buzzer`

Settings for the Pi's own buzzer (a passive buzzer in its case). Every field is
optional and clamped: `{"on": true, "volume": "low"|"medium"|"high", "alerts":
true, "countdown": true, "ui": false, "repeat": true, "repeat_s": 30,
"quiet_on": false, "quiet_from": "22:00", "quiet_to": "06:00"}`. `repeat_s` is
10 to 300. Times are `HH:MM` on the Pi's clock (Philippine time); a bad time is
ignored. The reply is `{"ok": true}` plus the fields below. The same fields are
in `GET /api/state`:

| Field | Meaning |
|---|---|
| `buzzer_on` | Master switch. |
| `buzzer_volume` | `low`, `medium` or `high`. |
| `buzzer_alerts` | The alarm sounds when the limit is reached. |
| `buzzer_countdown` | The low-battery countdown and the shutdown tune. |
| `buzzer_ui` | Tap and switch sounds on the Pi's touchscreen. Off by default. |
| `buzzer_repeat`, `buzzer_repeat_s` | Repeat the alarm every this many seconds while the alert is raised and not muted. |
| `quiet_on`, `quiet_from`, `quiet_to` | Daily window that silences the buzzer. Wraps past midnight. |
| `quiet_now` | `true` while quiet hours are silencing it. |
| `quiet_note` | `null`, or a sentence saying quiet hours are not applied because the Pi's clock is not synchronised. |
| `buzzer_available` | `false` when the buzzer could not be started. |
| `buzzer_note` | Why it is unavailable, to show as a warning. `null` otherwise. |

Muting an alert (from the app or the Pi) stops the alarm and its repeat. Quiet
hours silence the buzzer only; History and the phone's notification are
unaffected.

### `POST /api/buzzer/test`

`{"sound": "accumulation"}` plays one sound so the person setting it up can
hear it. It ignores the switches and quiet hours, but not a missing buzzer:
that answers `409 {"ok": false, "error": "unavailable", "message": "..."}`.
An unknown name answers `400 bad_sound`. Sounds: `accumulation`, `shutdown_low`,
`countdown`, `test`, `auto_muted`, `shutdown_manual`, `manual`, `muted`,
`unmuted`, `ready`, `boot`, `wifi_ok`, `wifi_fail`, `cam_lost`, `cam_back`,
`phone_join`, `saved`, and the button sounds `tap`, `switch`, `switch_on`,
`switch_off`, `choice`, `step_up`, `step_down`, `tab`, `open`, `back`, `confirm`,
`danger`, `denied`, `logo`, `theme_light`, `theme_dark`, `minimize`, `restore`,
`point`, `point_undo`, `wake`.
Over Firebase these are the `buzzer` and
`buzzer_test` commands.

### `POST /api/clean`

Clean copies: the camera picture alone, with no boxes, area outline or info
card, saved next to a snapshot so it can be labelled for training.
`{"mode": "off"|"manual"|"all"}`. `manual` keeps them for Capture only, `all`
also for detection alerts, muted alerts and test alerts (the default). Any
other mode answers `400 bad_mode`. Reply: `{"ok": true}` plus the fields below,
which are also in `GET /api/state`.

| Field | Meaning |
|---|---|
| `clean_mode` | `off`, `manual` or `all`. |
| `clean_count` | How many clean pictures are on the Pi. |
| `clean_bytes` | How much space they use, in bytes. Refreshed every 10 s. |

A history row that has a clean copy carries `"clean": "<name>"` (the same name
as `snapshot`), otherwise `"clean": null`. Load it from
`GET /snapshots/clean/<name>`. Deleting the row deletes both pictures. Over
Firebase this is the `clean` command; the history rows carry `has_clean`, but
the clean pictures themselves are never uploaded, so they can only be opened on
the Pi's own Wi-Fi.

### `POST /api/alerts/read`

```json
{ "ids": [42, 41] }
{ "all": true }
{ "ids": [42], "read": false }
```

Marks rows read, or unread with `"read": false`. Returns
`{"ok": true, "changed": 2}`.

---

### `POST /api/alerts/delete`

Deletes history records **and their snapshot image files**. This exists to
save space on the Pi's SD card, so the images really are removed from disk.

Three ways to call it. They can be combined, but normally you use one:

```json
{ "ids": [42, 41, 40] }
{ "from": "2026-10-01", "to": "2026-10-03" }
{ "all": true }
```

`from` and `to` are plain `YYYY-MM-DD` dates and are inclusive. Either one may
be omitted for an open-ended range.

Returns:

```json
{ "ok": true, "deleted": 3, "files_removed": 2 }
```

`deleted` counts rows, `files_removed` counts images actually unlinked — the
two differ when some rows had no snapshot.

An empty body deletes nothing and returns zeros, so a mistaken call is safe.

**This is irreversible.** Ask the user to confirm before calling it, and say
how many records will go. The touchscreen dashboard shows a confirmation
dialog naming the count; matching that is the safe pattern.

---

### `POST /api/camera/reconnect`

Drops the camera stream and opens it again, and brings the `camera-link`
network profile up if the camera does not answer a ping. Body: none.
Returns `{"ok": true}` at once; watch `connected` in `/api/state` for the
result (usually back within 5–15 s). The Pi also does this by itself when no
frame arrives for 12 seconds. Safe for the app to offer as a "Reconnect
camera" action.

### `POST /api/restart`

Restarts the whole Garbage-Guard program on the Pi (detection, camera, web
server). Returns `{"ok": true}`, then the server is gone for a few seconds.
Poll `/api/state` until `uptime_s` starts again from near zero. Settings, the
monitored area and history are kept. Ask the user to confirm before calling it.

---

## Telling the Pi who you are

The Pi screen shows which phones are connected. Send these headers on
**every** request so it can show the phone properly (all optional, but please
send the first two):

| Header | Example | Shown as |
|---|---|---|
| `X-GG-Client` | `android` | Marks the request as the app, not a browser. |
| `X-GG-Client-Id` | a random UUID, made once and saved | Keeps two phones apart even behind one address. |
| `X-GG-Device` | `Galaxy A54` (`Build.MANUFACTURER + " " + Build.MODEL`) | Device |
| `X-GG-OS` | `Android 14` | System |
| `X-GG-App-Version` | `1.0` (`BuildConfig.VERSION_NAME`) | App version |
| `X-GG-Mode` | `foreground` or `background` | "Open" or "In background" |

A phone counts as gone 4 seconds after its last `/api/state`. To show it
gone at once, call `POST /api/client/bye` (same headers, empty body) when the
user disconnects from the Pi or the background watcher stops.

## Network details

`GET /api/net/details` (read-only, anyone may call it) returns every network
adapter: `device`, `type`, `state`, `connection`, `role` (`wifi`, `wired`, or
`camera` for the camera cable), `ips`, `gateway`, `dns`, `mac`, `speed`,
`method` (`auto` or `manual`), `default` (carries the internet), and for Wi-Fi
a `link` object with `ssid`, `signal`, `rate`, `channel`, `freq`, `security`,
`bssid`. Top level: `internet`, `default_dev`, `camera_ip`, `local`.

`POST /api/net/ipv4` (Pi screen only) sets a connection to automatic or a
checked static address:
`{"connection": "Wired connection 1", "method": "manual", "address": "192.168.1.50", "prefix": 24, "gateway": "192.168.1.1", "dns": ["8.8.8.8"]}`.
Invalid input returns `{"ok": false, "error": "invalid", "fields": {...}}`
with one message per field. The camera cable's profile cannot be changed.

## Pi-only endpoints (the app does not use these)

The Pi's own screen has Wi-Fi setup. These exist in the real server only and
are not in the mock server. The app should not call them.

| Endpoint | Notes |
|---|---|
| `GET /start`, `GET /wifi` | HTML pages for the touchscreen startup splash and Wi-Fi setup. |
| `POST /api/buzzer/ui` | `{"sound": "<a button sound>"}`: any of the button sounds listed under `/api/buzzer/test`. The touchscreen's own button sounds. Local only; anyone else gets `403 local_only`. |
| `GET /api/net` | Network status: `wifi_connected`, `ssid`, `internet`, `has_wifi_hw`, `camera_conflict`, `local`. Read-only, safe to call. |
| `GET /api/wifi/scan`, `POST /api/wifi/connect`, `POST /api/wifi/fix_camera` | Answer only to requests from the Pi itself. Anyone else gets `403 local_only`. Passwords are never logged or returned. |

`GET /api/battery` (UPS HAT readout: `ok`, `percent`, `voltage_v`, `current_a`,
`power_w`, `on_ac`, `charging`, `state`, `minutes_left`, `capacity_mah`,
`cells_v`, `vbus_v`, `vbus_a`, `vbus_w`) and `POST /api/window`
(minimize or restore the Pi's dashboard window, local only) were added for the
Pi's own screen. The app uses `/api/battery` for its header battery indicator
and does not call `/api/window`. `/api/battery` is read-only and returns
`{"ok": false, "error": ...}` when no UPS is attached.

## Low battery shutdown

The Pi runs on a UPS HAT. A sudden power cut can corrupt the SD card and the
history database, so the Pi shuts itself down cleanly first. `/api/battery`
(and the `battery` block in Firebase `/gg/state`) carries these extra fields,
present even when the UPS cannot be read:

| Field | Values | Meaning |
|---|---|---|
| `auto_shutdown` | bool | Feature switched on (default `true`) |
| `auto_shutdown_pct` | int, 10 to 50 | Threshold, default 15 |
| `shutdown_state` | `"off"`, `"boot_grace"`, `"armed"`, `"counting"`, `"paused"` | Where the logic is now. `off` also means the battery gauge cannot be read, in which case nothing will ever shut down |
| `shutdown_in_s` | int or `null` | Seconds left while `counting` |
| `shutdown_floor` | bool | The countdown is the 5% one, which cannot be cancelled. Hide the Cancel button |

How it behaves:

- On battery (`on_ac` false) at or below the threshold for 30 seconds in a
  row, a 5 minute countdown starts. Show a banner with the time left and a
  Cancel button on every screen while `shutdown_state` is `counting`.
- Plugging in the charger cancels it. Cancel pauses it; it re-arms when the
  charger is plugged in, when the battery rises 5% above the threshold, or
  when `on` is sent again.
- At 5% or lower the shutdown shortens to 60 seconds and cannot be cancelled
  (the charger still stops it).
- Nothing is checked for 3 minutes after the Pi boots, and the gauge must give
  three good readings in a row first. Booting at 12% with a 15% threshold
  therefore shuts down about 8 minutes later unless someone cancels or plugs in.
- Just before powering off, the Pi writes a history row with the event
  `low_battery_shutdown` and sends the history and state to Firebase, so the
  app can say why the Pi went offline. It stays off until someone switches the
  UPS HAT's power switch off and on again.
- Over Firebase the Pi sends state immediately when `shutdown_state` changes
  and every 3 seconds while `counting`, and checks for commands every 2 seconds
  meanwhile, so a Cancel from the app arrives quickly. A closed app cannot be
  woken by the database; see `FIREBASE.md` for the notification note.

`GET /api/cloud`, `POST /api/cloud` and `POST /api/cloud/restore` hold the
Pi's own Firebase settings (Network → Firebase on the Pi screen). They answer
only to the Pi itself. The password is never returned, only `has_password`.

For phone access away from the Pi's Wi-Fi, see **`FIREBASE.md`**.

## Naming, please keep these exact

The mockup used some names that do not match the system. These matter for the
thesis:

| Use this | Not this | Why |
|---|---|---|
| Detection Confidence | Accuracy Threshold | The thesis defines Accuracy as a scored metric. A settable field called Accuracy invites an awkward question at defence. |
| Peak Confidence | Peak Accuracy | Same reason. |
| `bottle`, `bag`, `food_container` | Drinkware, Plasticware | These are the model's actual class names. |
| Latency in ms | FPS only | Latency is the metric the thesis scores. FPS can be shown alongside. |
| Monitored area | ROI | Users understand "the area being watched". ROI is fine in code and API names. |

Default threshold is **20**, matching the thesis. Default confidence is
**40%**, which was established by testing.

---

## Things worth knowing

**The count reads low.** Model recall is about 0.70, so roughly 7 of every 10
items present are detected. Do not present the count as an exact tally of what
is in the water.

**Show `smoothed`, not `raw`.** `raw` flickers frame to frame.

**Handle `connected: false`.** The camera can drop out. The API keeps
responding; only `connected` goes false.

**Handle the Pi being unreachable entirely.** Show a clear offline state
rather than a spinner. This will happen at the site.

---

## Visual style

The touchscreen dashboard now uses a soft brutalist style: a warm stone
background (`#E8E0D8`), thick near-black outlines (`#1A1A1A`), hard offset
shadows, and pressed-in input wells. Status colours are green `#3BB54A`,
amber `#F2B233`, and red `#E23B2E`, with teal `#1F9E94` for actions and
settings. There is a dark mode too. Matching these keeps the app and the
touchscreen looking like one product.

Class colours match the boxes drawn on the video:

| Class | Colour |
|---|---|
| `bottle` | `#0080FF` |
| `bag` | `#C800FF` |
| `food_container` | `#00DC00` |

---

## Reference implementation

`dashboard.html` is the working touchscreen UI and calls every endpoint above.
If any behaviour here is unclear, its JavaScript shows exactly how it is used.
It is plain HTML with no build step — open it in an editor and read the script
at the bottom.

---

## Files to take

| File | Purpose |
|---|---|
| `gg_mock_server.py` | Run this. Fake API for development. |
| `GarbageGuard_API.md` | This document. |
| `dashboard.html` | Reference implementation, optional but useful. |
| `SYNC_PLAN.md` | Plan for remote access and syncing the app with the Pi (Firebase and alternatives). Read before starting any cloud work. |

You do not need `garbageguard_live.py`, `gg_counter.py`, `gg_web.py`, the
model folder, or the camera.

## What the Android app uses today

As of app version 10.0, when the Pi is out of reach the app uses Firebase as
`FIREBASE.md` describes: it reads `/gg/state`, `/gg/alerts`, `/gg/snaps`,
`/gg/preview`, `/gg/usage` and `/gg/pi`, writes `/gg/watchers/<clientId>`,
and sends commands through `/gg/commands` and `/gg/results`.

On local Wi-Fi the app calls: `/api/state`, `/frame.jpg`,
`/api/alerts`, `/snapshots/<name>`, `/api/settings`, `/api/roi`,
`/api/test_alert`, `/api/capture`, `/api/mute`, `/api/auto_mute`,
`/api/alerts/read`, `/api/alerts/delete`, `/api/battery`,
`/api/camera/reconnect`, `/api/restart`, `/api/net/details`,
`/api/screen_saver`, `/api/screen`, `/api/auto_shutdown`, `/api/shutdown`,
`/api/buzzer`, `/api/buzzer/test` and
`/api/client/bye`. It sends all six `X-GG-*` headers on these calls. It does
not use `/stream.mjpg`, `/api/window`, `/api/net/ipv4` or the Wi-Fi setup
endpoints.

---

### `POST /api/view`

Which camera stream is read and how much of the picture the model sees.
All fields are optional.

```json
{"stream": "main", "detect_size": 960, "crop": true}
```

- `stream`: `main` (sharp, default) or `sub` (the light 640x360 stream).
  Changing it reconnects the camera.
- `detect_size`: 640, 960 or 1280 pixels for the model. Bigger finds
  smaller items but takes longer.
- `crop`: `true` gives the model only the box around the monitored area.

Reply: `{"ok": true, "stream": "main", "detect_size": 960, "crop": true}`.
The state (`/api/state`) also carries `stream`, `stream_note`,
`frame_size`, `detect_size`, `crop` and `detect_box`. Firebase command: `view`.

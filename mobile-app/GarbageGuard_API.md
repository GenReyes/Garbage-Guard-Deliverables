# Garbage-Guard API — for the mobile app

For King Perth. Everything the app needs to talk to the Raspberry Pi.

**What changed in this version:** two features.

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
  "alert_active": false,
  "frames": 10432,
  "uptime_s": 745
}
```

| Field | Meaning |
|---|---|
| `connected` | Camera is delivering frames. `false` means show an offline state. |
| `counts` | Per-class counts currently inside the ROI. Class names are fixed: `bottle`, `bag`, `food_container`. |
| `raw` | Items detected this frame. |
| `smoothed` | Median over the last 15 frames. **Display this one**, not `raw`. |
| `threshold` | Alert fires at this count. |
| `rearm` | Alert resets below this. Read-only, derived from `threshold`. |
| `conf` | Detection confidence as a fraction, 0.0–1.0. Multiply by 100 for display. |
| `roi` | Polygon points, normalised 0–1. Empty list means no ROI, whole frame active. |
| `latency_ms` | Current frame processing time. This is the thesis Performance Efficiency metric. |
| `alert_active` | Accumulation alert is currently raised. |
| `muted` | True while an operator has silenced the alert. |
| `mute_left` | Seconds of mute remaining. `null` means no expiry, `0` means not muted. |

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
| `unmuted` | The mute was lifted, by hand or automatically. | Grey row |

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
boxes, the monitored-area outline, and a header stamped with the time, item
count, and per-class breakdown. It is evidence, so show it full width.

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

You do not need `garbageguard_live.py`, `gg_counter.py`, `gg_web.py`, the
model folder, or the camera.

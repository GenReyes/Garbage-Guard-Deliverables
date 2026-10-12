# Changelog

Changes to Garbage-Guard, newest first. App versions refer to the Android app
in `mobile-app/`; Pi entries refer to the detector, dashboard and Firebase sync
in `pi/`. Entries before version 7 are summarised from the commit history.

## Version 7.0 (2026-10-12)

This release reduces the Firebase traffic that the system produces while no
phone is watching. Nothing changes on the same Wi-Fi network, where the app
still talks to the Pi directly.

### Pi

- HTTPS connections to Firebase are kept open and reused, one per thread and
  host. Each new connection costs about 3.5 KB of billed download for the TLS
  handshake, and the Pi previously opened one for every request. A connection
  that the server has already closed is retried once on a fresh one.
- Every write (PUT, PATCH, DELETE) is sent with `print=silent`. Without it,
  Firebase returns the written data and bills that answer as a download.
- Polling slows down while no phone is watching:

  | Task | Phone watching | Nobody watching | Before |
  |---|---|---|---|
  | State push to `/gg/state` | every 3 s | every 2 min | every 30 s |
  | Command and watcher check | every 2 s | every 10 s | every 2 s |
  | Heartbeat to `/gg/pi` | every 30 s | every 60 s | every 30 s |

- While idle, a changing item count alone no longer triggers a state write.
  Changes to the alert, mute, connection, threshold and auto-mute fields are
  still written at once.
- The heartbeat at `/gg/pi` carries a new field, `alerts_rev`. Its value
  changes only when the Pi adds, edits or removes rows in `/gg/alerts` or
  `/gg/snaps`, and the heartbeat is sent immediately when it does.
- The usage meter now includes what the Pi itself downloads (answers, response
  headers and TLS handshakes) under a new `pi` category. Earlier estimates
  counted only what watching phones downloaded.
- New settings with defaults: `idle_commands_s` (10), `pi_s` (30) and
  `idle_pi_s` (60). The default of `idle_state_s` rises from 30 to 120.
- `firebase.rules.json` gains a write rule for `/gg/usage`. The rules must be
  published again in the Firebase console for this to take effect.
- `test_cloud.py` gains tests for silent writes, slow polling when idle,
  count pushes only while watched, and the `alerts_rev` field.

### Android app

- The alert history is downloaded only when `alerts_rev` changes, instead of
  every 10 s. Against an older Pi that does not send the field, the app keeps
  the previous behaviour.
- Connections to Firebase are returned to the pool after each request, so the
  next request skips the TLS handshake. A connection is closed only after an
  error.
- The watcher entry at `/gg/watchers/<id>` is written and removed with
  `print=silent`.
- A command sent through Firebase now waits up to 15 s for the Pi's answer
  (previously 10 s), because the Pi checks for commands every 10 s when idle.
- The Pi heartbeat is read every 10 s in the foreground (previously every
  30 s), so a change in `alerts_rev` is noticed promptly.
- `versionCode` 9, `versionName` 7.0.

### Documentation and files

- `mobile-app/FIREBASE.md` describes the new intervals and `alerts_rev`.
- `mobile-app/Garbage-Guard v7.apk` replaces the version 6 build.

## Version 6.2 (2026-10-12)

- Android app: stronger Neumorphism depth on raised and sunk surfaces.
- Android app: two launcher icons, light and dark; the one matching the theme
  is switched on when the app leaves the screen.

## Version 6.1 (2026-10-12)

- Android app: pictures sit inside the glass outline, so the outline no longer
  covers the edge of the frame.
- Android app: fullscreen has two matching panels beside a centred picture.
- Android app: the snapshot page shows the picture in a viewing pane with the
  readings in one table.

## Version 6.0 (2026-10-12)

- Android app: the Live Feed card and snapshot page take the picture's own
  shape, so nothing is cropped.
- Android app: picture-first fullscreen; a tap on the picture hides the
  readouts and keys.
- Android app: the layout is scaled from a 411 dp phone, so every screen size
  shows the same design.
- Android app: the hidden game gains full-screen water and three boss types.
- Pi dashboard: larger picture in fullscreen and full-size snapshots, and a
  slimmer snapshot card.

## Version 5.0 (2026-10-12)

- Android app: Firebase fallback. The app uses local Wi-Fi first, Firebase
  second, and returns to local Wi-Fi by itself.
- Android app: Firebase card in Settings with account setup, test and save,
  and the month's data use.
- Android app: the hidden game gains lives, combos and boss fights.
- Pi: `gg_cloud.py` syncs state, history, compressed snapshots and a preview
  still to Firebase Realtime Database and runs commands left there by phones.
- Pi: `gg_usage.py` estimates the month's Firebase downloads; the preview
  slows at 90 % of the allowance and stops at 100 %.
- Pi dashboard: Firebase card, settings page and usage bar.

## Version 4.0 (2026-10-12)

- Android app: subtitles and hints stay on one line by stepping the text size
  down.
- Android app: every pressable control gives a sound and a tick.
- Android app: "Watching now" shows how each phone reaches the Pi (direct,
  shared address or remote).
- Pi dashboard: the phone panel marks each phone as direct, shared address or
  remote, and the API gains a `via` field.

## Version 3.0 (2026-10-12)

- Android app: one-line mute bar with a fixed size in every state.
- Android app: a separate notification channel and sound for each kind of
  event.
- Android app: "Sound and feel" settings for button sounds, vibration and
  keeping the screen on.

## Version 2.0 (2026-10-12)

- Android app: identifies itself to the Pi on every request and signs off
  when it disconnects.
- Android app: "Watching now" sheet listing every phone and browser the Pi
  reports.
- Android app: fullscreen follows the dashboard, with only the monitor in the
  reverse theme.
- Pi dashboard: a phone counts as gone 4 s after its last poll; the phone
  badge opens a details panel.

## Version 1.0 (2026-10-11)

- Android app: camera reconnect key, Pi restart from Settings, read-only
  network sheet and separate icons for bottle, bag and container.
- Android app: Pi battery indicator and a theme-aware Live Feed card.
- Pi dashboard: UPS battery panel, minimize to the desktop, camera status,
  refresh button, network screen, themed snapshots and TV fullscreen.
- Pi: new endpoints `/api/camera/reconnect`, `/api/restart` and
  `/api/net/details`.

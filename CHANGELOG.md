# Changelog

Changes to Garbage-Guard, newest first. App versions refer to the Android app
in `mobile-app/`; Pi entries refer to the detector, dashboard and Firebase sync
in `pi/`. Entries before version 7 are summarised from the commit history.

## App version 9.1: switches and choice bars (2026-10-12)

### App

- Switches stand on their own, with no key round them. On, the groove fills
  with teal and a soft teal light spreads round it. The groove's shade no
  longer uses the page's light and dark shadow pair, which looked wrong on
  teal in the light theme.
- Choice bars (Appearance, Screen saver) show the chosen option as a raised
  key with its own rounded shape and shadow, standing in a sunk tray, so the
  choice is pushed out and the rest stay pushed in.
- The History symbols use the same bar.

## App version 9: History filters, date range and bulk actions (2026-10-12)

### App

- One row of six symbols on the History page picks the kind of record: all,
  alerts, captures, tests, mutes, and power (the two shutdown events). They
  are the same size and sit on one line, and the filter applies at once.
- A date range that really filters the list. Days are picked from a calendar,
  not typed, so a day that does not exist cannot be entered (leap years
  included) and a day in the future cannot be chosen. Picking a From day
  after the To day moves To along, and the other way round, so the range can
  never run backwards. Either end may be left open. Quick picks: Today, 7
  days, 30 days, Any.
- Mark read and Delete act on exactly the records on the page. With no filter
  that is everything (the old Mark all read, and a new Delete all). With a
  filter or dates it is only those records, so a date range can now be marked
  read as well as deleted. A line under the tools says what is shown, and the
  Delete confirmation repeats it with the count.
- With only dates set, Delete sends the range to the Pi, so records older
  than the 200 the app has loaded are deleted too. With a kind filter it
  deletes the loaded records by id.

## App version 8: Pi power in the app (2026-10-12)

App side of the screen saver and low-battery shutdown plan
(`mobile-app/SCREEN_SAVER_PLAN.md`, sections 6 and 7). Works on the Pi's Wi-Fi
and through Firebase.

### App

- New "Pi power" card in Settings, the twin of the dashboard's Power card:
  screen saver mode (Off, Dim, Never), the two timers, the dim level, wake on
  alert, the auto shutdown switch and its percentage, Save, and Sleep now or
  Wake. The card and each part of it are left out on a Pi that does not report
  them.
- Monitor page: a line showing whether the Pi screen is on, dimmed or off,
  with a Wake or Sleep key.
- A countdown banner over every page while the Pi counts down to a low-battery
  shutdown, with Cancel. The time is counted down on the phone between
  readings. Cancel is hidden for the 5% shutdown that cannot be stopped. The
  same banner says "The Pi is shutting down" while it powers off.
- A notification when the countdown starts and the app is not on screen, and
  one for a `low_battery_shutdown` row. It needs the app's watch service to be
  running; a fully closed app cannot be woken by the database.
- "Shut down the Pi" button with a confirmation that says the Pi can only be
  started again with the switch on the UPS HAT.
- History shows `low_battery_shutdown` (battery symbol) and `manual_shutdown`
  (power symbol) as orange rows.
- Version 7.1, included here: the app no longer closes when the phone has no
  internet and Firebase is set up. A failed Firebase read in the background
  had nothing to catch it.

### Mock server

- `gg_mock_server.py` has the six screen fields, the five shutdown fields,
  `POST /api/screen_saver`, `/api/screen`, `/api/auto_shutdown` and
  `/api/shutdown`, and the two new history rows.
- `POST /api/mock/low_battery` (mock only) pretends the battery has dropped
  under the threshold, so the countdown, Cancel and the shutdown row can be
  tested with no Pi. Body, all optional: `{"seconds": 300, "floor": false}`.
  After a shutdown the mock answers 503 for about 25 seconds, then "boots"
  again.

## Pi screen saver and low-battery shutdown (2026-10-12)

Pi side of the plan. The app needs the matching changes (see
`mobile-app/GarbageGuard_API.md`, sections "Screen saver" and "Low battery
shutdown", and `mobile-app/SCREEN_SAVER_PLAN.md`).

### Pi

- Screen saver with three modes: Off (HDMI output off, backlight off on most
  boards), Dim (a dark layer; the board has no real brightness control, so it
  saves little) and Never. Timer 1 to 60 minutes on the charger and a shorter
  one (30 seconds to 60 minutes, default 1 minute) on battery. The first tap
  only wakes the screen, a new alert wakes it, and detection, alerts and
  Firebase keep running. Off uses `wlr-randr` (`wlopm` was tried and does not work on this Pi's display driver), and a touchscreen watcher wakes the screen because the desktop may not pass taps on while the output is off. If `wlr-randr` or the touchscreen cannot be used, the Pi falls back to Dim.
- Waking is verified and retried (plain switch-on, the screen's own mode, then
  an off-and-on cycle). If the screen still will not come back the Pi keeps
  retrying, warns in the Power card and on the phone (`screen_note`), and uses
  Dim until someone picks Off again. A screen left off is switched on at
  start-up. This follows a case on the test Pi where the panel stayed dark
  after sleeping with a Raspberry Pi Connect screen share open.
- Low-battery auto shutdown at a chosen percentage (default 15, range 10 to 50):
  30 second debounce, a 5 minute countdown with a Cancel banner on the Pi and
  in the app, automatic cancel when the charger is plugged in, a 5% floor that
  cannot be cancelled, a 3 minute boot grace, and no action at all if the
  battery gauge cannot be read. A `low_battery_shutdown` history row is sent to
  Firebase before power off.
- New Power card on the dashboard with all of the above, plus a "Sleep now"
  button. New endpoints `POST /api/screen_saver`, `/api/screen`,
  `/api/auto_shutdown` (and the Pi-only `/api/screen/touch`), new state and
  battery fields, and three new Firebase commands.
- Shut down button in the Power card, with an "Are you sure?" dialog. It writes
  a `manual_shutdown` history row ("Manual shutdown"), sends it to Firebase and
  powers off. New `POST /api/shutdown` (needs `{"confirm": true}`), a new
  `shutdown` Firebase command, and `shutdown_state: "powering_off"`.
- Firebase sync: a change of screen state or shutdown state is written at
  once, even in standby; during a countdown state is written every 3 seconds
  and commands are polled every 2 seconds. The cloud thread can now send
  everything immediately (`flush`), which the shutdown uses.
- History shows the new event with an orange "Low battery shutdown" tag.

## Pi update after 7.0 (2026-10-12)

Pi dashboard and network screen only. The app and the Firebase data are
unchanged.

### Pi dashboard

- The Firebase status has four states instead of three. Each has its own
  symbol and colour, in both themes:

  | State | When | Symbol | Colour |
  |---|---|---|---|
  | Firebase off | sync switched off or not set up | cloud with a slash | grey |
  | Firebase on standby | signed in, but no phone on another network is watching | cloud with a pause sign | slate |
  | Firebase active | at least one phone on another network is watching | cloud | orange |
  | Firebase error | sign-in or a write failed | cloud with an exclamation mark | red |

  Previously the chip said "Firebase synced" whenever the Pi was signed in,
  even with nobody using Firebase. Standby matches the slow idle polling
  added in 7.0; the Pi switches to active within a few seconds of a phone's
  watcher entry appearing.
- The chip in the phone panel shows the state, and "Firebase active · 2
  phones" when more than one phone is watching. Its tooltip explains the
  state.
- The small cloud next to the phone badge follows the same states: slate on
  standby, orange when active, red on error or when the month's allowance is
  used up. It is hidden when Firebase is off.

### Network screen

- The Firebase card's chip reads Standby or Active instead of Syncing, and
  the line under it says "Waiting for a phone away from this Wi-Fi" or how
  many phones on another network are using it.

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
  - Fullscreen: the screen fills the monitor's height and takes the camera's
    own shape, so there are no black bars beside the picture. FPS, an items
    gauge and the class counts move to the left bezel; Capture, Mute and
    Auto-mute to the right; the status chip to the top bar.
  - Full-size snapshot: the picture fills the screen height, with Close and
    the details in a narrow column beside it.
  - Snapshot card: one row about 7.5 % of the picture's width tall, instead
    of 20 %. Snapshots saved before keep their old card.

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
- Pi dashboard: Firebase card, settings page and usage bar, and a cloud
  mark beside the phone badge. The Firebase fields accept pasted text.

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

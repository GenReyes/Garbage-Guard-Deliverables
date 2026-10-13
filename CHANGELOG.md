# Changelog

Changes to Garbage-Guard, newest first. App versions refer to the Android app
in `mobile-app/`; Pi entries refer to the detector, dashboard and Firebase sync
in `pi/`. Entries before version 7 are summarised from the commit history.

## App version 9.11: closing the app ends the session; demo link (2026-10-13)

### App

- Going back to the start page (Settings, Pi address, Change) now drops the
  connection and removes the "Watching the Pi" notification.
- Closing the app, by swiping it away from the recent apps or forcing it to
  stop, ends the session: the notification goes, and the next launch opens
  at the start page with the address still filled in. An app that was only
  put in the background comes back to the page it was on, still connected.
  Alerts therefore arrive while the app is in the background, and stop once
  it is closed.
- Demo mode has a Demo link card in Settings: Wi-Fi (commands accepted at
  once), Firebase (accepted after about 4 seconds) and No answer (timed out
  after 8 seconds), to show the waiting, accepted and timed-out looks with
  no Pi. The last two also show the app as it looks through Firebase.
- The accepted and failed flash lasts a little longer, so it is easier to see.

## App version 9.10: Screen saver and Power as two cards (2026-10-13)

### App

- Settings has a Pi screen saver card (mode, timers, dim level, wake on
  alert, Sleep now or Wake) and a Pi power card (auto shutdown, its level,
  Shut down), matching the dashboard's two cards. Each sends and locks on
  its own, so a screen saver change no longer holds the shutdown level.

## App version 9.9: Save is back for detection settings (2026-10-13)

### App

- Detection confidence and the accumulation limit go to the Pi only when
  Save is pressed, since they decide what raises an alert. Save and both
  fields lock, grey and show the running line until the Pi answers.
- The Pi power fields still send themselves a second after the last press.

## App version 9.8: wait for the Pi before a second press (2026-10-13)

### App

- A key that sends a command locks until the Pi answers, so a second press
  cannot send it twice. While it waits the key stays pushed in and greyed,
  with a line running down round its border as the time allowed runs out
  (about 6 seconds on Wi-Fi, 17 through Firebase). It flashes teal when the
  Pi confirms and red when it does not, with a line of text either way.
  Other controls stay usable meanwhile.
- Number fields and the screen saver bar send themselves. Each press starts
  a one second count again, shown as an amber line filling round the field;
  when it runs out the final value goes as one command and the field locks
  until the Pi answers. The Save keys are gone.
- Fewer Firebase writes: ten presses are one command, fields of one card go
  together in one command, only the values that changed are sent, and a
  value stepped back to what the Pi already has sends nothing.
- Switches move at once and fall back by themselves if the Pi does not
  confirm.

### Mock server

- `GG_MOCK_POST_DELAY` (seconds) makes the mock answer commands slowly, to
  see the waiting state without Firebase.

## App version 9.7: a strip of their own for Android's buttons (2026-10-13)

### App

- The tabs no longer run on behind Android's three buttons or gesture
  handle. Under the tabs there is a second ink line and a shallow sunk tray
  in the page's colours for the system buttons to sit in. On a phone that
  shows neither buttons nor a handle, the tray and its line are not drawn.

## App version 9.6: less Firebase data for the same screens (2026-10-13)

Cuts what the app and the Pi download from Firebase when the Pi is out of
reach on local Wi-Fi. Nothing changes on local Wi-Fi. `FIREBASE.md` has the
new fields.

### App

- History shows a 160 px thumbnail of each Firebase snapshot instead of the
  960 px copy. The full picture is fetched only when a record is opened. A
  snapshot from before thumbnails existed falls back to the full picture.
- Firebase snapshots are kept on the phone, up to 64 MB, and downloaded
  once. The oldest files go first when the limit is reached, and the files
  of deleted History rows are removed.
- `/gg/state` arrives over one streaming connection while the app is in
  use, so only changes are downloaded. Polling every 3 s takes over
  whenever the stream is down.
- The watcher entry carries a `screen` field, "live" or "other", and is
  rewritten at once when the live screen opens or closes.

### Pi

- Every snapshot uploaded to `/gg/snaps/<id>` carries a `thumb` (160 px, at
  most 8 KB). Snapshots uploaded earlier get one a few at a time.
- `/gg/preview` is 360 px wide and at most 40 KB (was 480 px and 60 KB),
  and is sent only while a phone is on the live screen. A phone running an
  older app does not say which screen it is on and is treated as on the
  live screen.
- `/gg/watchers` and `/gg/commands` are read over streaming connections
  instead of being fetched every 2 s. Polling takes over while a stream is
  down. Stream traffic is counted in the usage estimate.

## Pi buzzer sound engine (2026-10-13)

### Pi

- New `gg_buzzer.py`: a passive buzzer module on GPIO12 plays a short, different
  sound for each History event (alert, test, capture, mute, unmute, auto-mute,
  both shutdowns), the low-battery countdown, and a "ready" tune at start-up.
  Sounds are written as steps of (Hz, milliseconds) in the 1.5 to 3 kHz band a
  small buzzer can be heard in; a higher priority sound interrupts a lower one
  and a lower one is dropped, so sounds never pile up.
- The alarm repeats every 30 seconds (10 to 300) while the alert is raised and
  not muted. Muting stops it. Quiet hours (default 22:00 to 06:00, off until
  switched on) silence everything, and are not applied when the Pi's clock is
  not synchronised.
- Settings: master, volume (low, medium, high), alerts, countdown, button sounds
  (off by default), repeat and its interval, quiet hours. New `POST /api/buzzer`,
  `POST /api/buzzer/test`, local-only `POST /api/buzzer/ui`, Firebase commands
  `buzzer` and `buzzer_test`, and the settings and `buzzer_available`,
  `buzzer_note`, `quiet_now`, `quiet_note` in the state.
- The low-battery countdown beeps once a minute, once a second for the last 10
  seconds, and every 2 seconds at the 5% floor. The countdown loop now checks
  once a second while counting.
- If the buzzer cannot start (no library, no wiring) the state says why and
  nothing else changes.
- The detector uses hardware PWM when GPIO12 is set up for it and software PWM
  otherwise (`GG_BUZZER_BACKEND` forces one). `buzzer_bench.py` plays a tone sweep
  for judging pitch and volume by ear.
- Not yet: the dashboard Sound card and the vibration motor. The hardware
  backends are untested until the buzzer is wired.

## Mock server and brief for the Pi buzzer (2026-10-13)

### App

- `gg_mock_server.py` answers `POST /api/buzzer`, `/api/buzzer/test` and
  `/api/buzzer/ui`, and `/api/state` carries the buzzer fields and the clock
  fields (`clock_synced`, `clock_settling`, `clock_zone`). `GG_MOCK_NOBUZZER=1`
  and `GG_MOCK_CLOCK_OFF=1` pretend the buzzer or the clock is broken.
- New `BUZZER_APP.md` lists the Pi buzzer card to build in the app.

## Pi dashboard Sound card (2026-10-13)

### Pi

- Settings has a new Sound card under Screen saver and Power: the buzzer's
  master switch, Low, Medium or High volume, switches for Alerts, Countdown and
  shutdown, and Button sounds, Repeat alarm with its interval (10 to 300
  seconds), Quiet hours with From and Until in 30 minute steps (shown in 12-hour
  time), and Test alarm and Test tune buttons. One Save at the bottom applies
  everything, and it now stays on screen while the page scrolls.
- A red line says why when the buzzer is unavailable, and a line says when quiet
  hours are silencing it or the Pi's clock is not synchronised.
- With Button sounds on, the touchscreen's own taps and switches play a short
  tick through `POST /api/buzzer/ui`. The card is hidden on a Pi that has no
  buzzer code.

## Pi clock warning waits for the first sync (2026-10-13)

### Pi

- The "Clock not synced" pill no longer flashes for the first two and a half
  minutes after boot, while the Pi is still asking an internet time server. The
  state carries `clock_settling` (`true` in that window) next to `clock_synced`,
  which stays honest. The warning appears only if the clock is still unsynced
  after that.

## Pi dashboard clock in Philippine time (2026-10-13)

### Pi

- A new line under the Dark button shows the date and time in Philippine time
  (for example "Tue 13 Oct, 9:16 PM"), whatever time zone the viewing device is
  set to. The fullscreen view's clock follows it, with seconds.
- Every time on the dashboard is now 12-hour with AM or PM, including the
  History list, the snapshot viewer and the delete confirmation.
- The Pi has no battery-backed clock, so after a power cut with no internet its
  time can be wrong. The state now carries `clock_synced` (from
  `timedatectl`) and `clock_zone` (`Asia/Manila`). When the clock is not
  synchronised the pill turns amber and reads "Clock not synced". If the Pi
  cannot tell, it reports synced rather than nagging.
- New `gg_clock.py` and `test_clock.py`. Stored record times are unchanged
  (the Pi's local time, `YYYY-MM-DD HH:MM:SS`).

## Pi dashboard Settings tab (2026-10-13)

- A third tab, **Settings**, next to Monitor and History. The Power card moved
  there from the Monitor tab and is now two cards: **Screen saver** (mode,
  timers, dim level, wake on alert, Sleep now) and **Power** (auto shutdown, its
  threshold, Shut down). One **Save** at the bottom saves both, as before.
- The Monitor tab keeps only Detection settings and Monitored area. At 1024 by
  600 they fit exactly, so the right-hand column no longer scrolls.
- Leaving Settings with unsaved changes drops them, so the screen never keeps
  showing values that were not saved.
- No change to any endpoint, setting or Firebase command.

## Pi dashboard History matches the app (2026-10-12)

Follows `mobile-app/HISTORY_TOOLS.md`, which lists what the app's History page
does from version 9.0 to 9.3. No server change: the same `/api/alerts`,
`/api/alerts/read` and `/api/alerts/delete` endpoints as before.

- Filter by kind: All, Alerts, Captures, Tests, Mutes, Power. Power covers
  both `low_battery_shutdown` and `manual_shutdown`. Suppressed rows show
  only under All.
- The From and To dates now filter the table instead of only feeding Delete.
  Quick picks for Today, 7 days, 30 days and Any. Future days cannot be
  picked, and choosing a From after the To moves the other one along, so the
  range can never run backwards.
- One **Mark read** and one **Delete** that act on exactly the rows shown,
  replacing Mark all read, Delete range and Delete all. A line under the
  tools says what is shown ("Showing 2 alerts on 12 Oct") and the delete
  confirmation repeats it with the count. With dates alone it says "at
  least", because that delete also clears records older than the 300 rows
  the page holds.
- Every event now has its own look, ranked by urgency the same way as the
  app: the two loudest wear their colour on the whole border, the quieter
  ones keep the ink border and show a band down the left edge, and the
  unread dot takes the row's colour. The shutdown rows show their symbol
  where the item count would be, because a shutdown has no count and "0"
  reads as a real measurement.

## App version 9.5: no pale halo on coloured surfaces (2026-10-12)

### App

- The soft shadow pair is made for the page colour, and its pale half showed
  as a square halo behind anything standing on a coloured surface. Event
  labels in History now carry only a hard shadow in their own pill shape,
  and keys on the mute bar, the sleeping-screen line and the countdown
  banner drop the soft pair while those surfaces are tinted.

## App version 9.4: tabs as the bottom edge of the screen (2026-10-12)

### App

- The Monitor, History and Settings tabs are no longer a bar set inside a
  strip. They are three cells from edge to edge under one ink line, with no
  frame of their own, and each cell runs on down behind Android's three
  buttons or its gesture handle. The open page is still the raised cell.
- Android's grey veil behind its three buttons is switched off, so the
  buttons sit on the tabs themselves.

## App version 9.3: bars for tabs and steppers, a look for every event (2026-10-12)

### App

- The three bottom tabs are one bar, like the choice bars: the page in view
  is the raised cell and the other two are sunk.
- Steppers are one bar as well. Minus and plus are raised cells at its ends,
  the number sits sunk between them, and a key sinks while it is held.
- Every kind of History record has its own look, ranked by urgency:
  accumulation (red edge, pulse, most lift), low-battery shutdown (orange
  edge, strong tint), manual shutdown (orange edge, light tint), auto-mute
  (amber edge), then test (amber band), capture (teal band), mute and unmute
  (purple band) and suppressed (grey band). The first four carry their colour
  on the whole edge; the rest keep the ink edge and show a colour band down
  the left. The unread dot takes the same colour.

## App version 9.2: one-piece choice bars, stronger power rows (2026-10-12)

### App

- Choice bars (Appearance, Screen saver, the History symbols) are one bar
  again: every option is a cell of the same outline, divided by the same ink
  line. The chosen cell is the raised one, a lit face that casts a shadow on
  its neighbours, and the cells not chosen are a shade deeper with their own
  inner shadow. This replaces the separate key of 9.1, which looked detached
  from the bar.
- History rows for the two shutdown events sit between an alert and an
  ordinary record: an orange edge, tint and shadow and a little more lift,
  with no pulse. Their unread dot is orange, and the event's symbol takes the
  place of the item count, which a shutdown does not have.

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

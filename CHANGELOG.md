# Changelog

Changes to Garbage-Guard, newest first. App versions refer to the Android app
in `mobile-app/`; Pi entries refer to the detector, dashboard and Firebase sync
in `pi/`. Entries before version 7 are summarised from the commit history.

## Pi: sharper detection from the main stream, full History (2026-10-26)

### Pi

- The Pi now reads the camera's main stream instead of the 640x360
  sub-stream, so small or far items are no longer a few blurred pixels.
- The model gets only the box around the monitored area, at 960 px by
  default. An item in the water is now drawn with several times as many
  pixels as before, depending on how much of the picture the area covers. Detections outside that box are not looked for at all.
- If the main stream gives no picture twice in a row, the Pi goes back to the
  sub-stream on its own and says so in the camera pill's tooltip. If the model
  cannot run at the chosen size, it goes back to 640 px.
- The live picture on the screen and phones is 960x540, and annotated
  snapshots are the same size. Clean copies for labelling are saved at the
  full main-stream resolution.
- New `gg_view.py` and `test_view.py`, `POST /api/view`, the `view` Firebase
  command, and the `--stream`, `--detect-size` and `--no-crop` options.
- History showed at most 300 records. The dashboard now loads every record,
  and the 5-second refresh only downloads the list again when something has
  changed (`/api/alerts?limit=0&sig=...`).
- Camera setup needed once: in the camera's Video/Audio page, set the
  **Main Stream** to 1920x1080, H.264, 15 fps.

## App version 12: Pi vibration removed (2026-10-17)

### App

- The Pi buzzer card no longer has the Pi vibration switch, strength bar or
  Test vibration key, in real use or in the demo.
- The app no longer calls `/api/vibe` or `/api/vibe/test`, or sends the `vibe`
  Firebase command, and no longer reads `vibe_*` fields from the state.
- Save on the buzzer card sends one `POST /api/buzzer` (or one `buzzer`
  command) with the changed fields.
- The phone's own vibration (alerts, key clicks, Light/Normal/Strong) is
  unchanged.

## Pi: vibration motor removed (2026-10-17)

The motor module was too weak to be worth keeping, so the code for it is gone.

### Pi

- Removed `gg_vibe.py` and `test_vibe.py`, the `/api/vibe` and
  `/api/vibe/test` endpoints, and the Firebase commands `vibe` and `vibe_test`.
- The state no longer carries `vibe_on`, `vibe_strength`, `vibe_available` or
  `vibe_note`. Old `vibe_*` keys left in `gg_settings.json` do nothing.
- The Settings page's Quiet card is now just Quiet hours; the vibration switch,
  strength bar and Test vibration key are removed.
- The buzzer no longer has the `also` hook that fed the motor.
- Update the Pi as usual (clone, copy, reboot). Unplug the module from GPIO13
  (pin 33), 3.3 V (pin 1) and ground (pin 39).

## App version 11.2: clean copies in the demo (2026-10-14)

### App

- Demo: the snapshot viewer offers the Annotated / Clean switch, as on the
  Pi's dashboard. Demo pictures are drawn on the phone, so the clean copy is
  the same scene without the boxes. It used to say the clean copy was on the
  Pi's Wi-Fi.
- Demo: new snapshots get a clean copy as the Clean copies bar asks (every
  snapshot, Capture only, or none), and the bar's picture count grows with
  them.

## App version 11.1: a vibration for every event, Simulation in the demo (2026-10-14)

### App

- Each kind of notification now vibrates in its own rhythm, so an alert can
  be told apart in a pocket: three long pulses for Limit reached, short then
  long for a test, a long buzz and two short ones for low battery, and gentle
  patterns for capture, mute, unmute, auto-mute and shutdown, which used to
  make no vibration at all. The channels have new ids, because Android fixes a
  channel's vibration when it is made; the old ones are removed.
- While the app is open, the same patterns play for routine events and for
  the low-battery countdown.
- New patterns of the app's own: Delete and Shut down (a double heavy pulse),
  the logo (a rising three-step), the app opening (a soft ramp with the
  chime), Saved (a short double click), Denied (two quick buzzes), the Pi
  lost and back, the camera lost and back, and the Pi's charger plugged in or
  pulled out.
- The count on the water is felt as it climbs: a light double pulse at half
  the limit and a firmer triple at four fifths, each once on the way up.
- Settings, Sound and feel: Light, Normal or Strong vibration. It sets the
  app's own patterns; key clicks keep the phone's touch setting. The app now
  asks for the `VIBRATE` permission, which Android grants on install.
- Demo: a Simulation card in Settings. Off, the demo runs by itself as before.
  On, the automatic wave and battery cycle stop and the card drives the
  pretend Pi: the count (step, empty, fill to the limit), Test alert and
  Capture, the Pi and camera online or gone, the battery level and charger,
  a one-minute low-battery countdown that powers the Pi off, the Pi's screen
  (on, dim, off), the buzzer missing, and a second phone connecting. Each
  change raises the same alerts, sounds and vibrations as the real event.
- The demo's battery now runs down and charges over three minutes each
  rather than one.

## App version 11: a sound for every action, clean copies (2026-10-14)

The app side of the Pi's sound plan and clean copies.

### App

- Every kind of action has its own sound, the same tune the Pi's buzzer
  plays for it, an octave lower and softer for a phone speaker: switch on
  and off, choice bars, + and −, tabs, open, back and close, Save and other
  confirm keys, Delete and other danger keys, the logo, the theme key,
  placing and undoing area points, and Apply with too few points.
- Events have sounds too: a chime when the app opens (the Pi's power-on
  tune), Saved or Denied when the Pi confirms or refuses a command, and
  sounds when the Pi or its camera drops out or comes back.
- Each sound has a vibration that fits it (confirm, reject, toggle on and
  off, a tick for steps). Both follow the App sounds and Vibration switches.
- `Mobile App/android/tools/make_sounds.py` builds the app's sounds from the
  Pi's `SOUNDS` table, so the two stay alike.
- History: a Clean copies bar (Off, Manual, All) with how many clean
  pictures the Pi keeps and their size. The snapshot viewer gets an
  Annotated / Clean switch. Over Firebase it says the clean copy is on the
  Pi's Wi-Fi only, since clean pictures are never uploaded.
- Pi buzzer card: "Play a Pi sound" plays any of the Pi's sounds.

## Pi: sounds for the rest of the touchscreen (2026-10-14)

### Pi

- New button sounds `point` and `point_undo` for placing and undoing a point
  while drawing the monitored area, and `wake` for the first tap on the dark
  screen saver.
- The battery card, Draw and Edit play `open`, Apply plays `confirm`, Clear plays
  `danger`. Apply with fewer than 3 points, or Edit with no saved area, plays
  only `denied`.
- Tapping outside a dialog to close it, and tapping the full-size picture to go
  back, play `back`.
- Every warning message plays `denied` (at most once per tap).
- Wi-Fi page: network rows and connection cards play `open`.

## Pi: clean copies for labelling (2026-10-14)

### Pi

- Every snapshot can also save the camera picture alone, with no boxes, area
  outline or info card, in `snapshots/clean/` under the same file name. It is
  the exact frame the model judged, at the camera sub-stream's resolution.
- A Clean copies chooser in the History header: Off, Manual (Capture only) or
  All (also detection alerts, muted alerts and tests; the default). It shows
  how many clean pictures there are and how much space they use.
- The History viewer gets an Annotated / Clean switch for records that have
  both. Deleting a record deletes both pictures.
- API: `POST /api/clean`, `GET /snapshots/clean/<name>`, `clean_*` fields in
  `/api/state`, a `clean` field on history rows. Firebase: `clean` command,
  `clean_*` state, `has_clean` on history rows; clean pictures are not uploaded.

## Pi: sound plan from Brian (2026-10-14)

### Pi

- 17 button sounds (switch on and off, choice, step up and down, tab, open, back,
  confirm, danger, denied, logo, theme, minimize, restore) plus the old `tap` and
  `switch`. The dashboard and the Wi-Fi page pick the sound from a `data-snd`
  tag or from the kind of control, and a failed request plays `denied`.
- New event sounds: `boot`, `wifi_ok`, `wifi_fail`, `cam_lost`, `cam_back`,
  `phone_join`, `saved`.
- A boot chime at power on: `start.sh` runs `python3 gg_buzzer.py --play boot`
  in autostart mode, before the camera and model start. It honours the buzzer
  switch and quiet hours and closes the pin before the detector opens it.
- The Pi plays `wifi_ok` or `wifi_fail` when a Wi-Fi connect finishes, and
  `saved` after settings, buzzer or screen saver changes are saved.
- `cam_lost`, `cam_back` and `phone_join` exist and can be tested but nothing
  plays them yet.
- `/api/buzzer/ui` accepts every button sound; the app's test list can use any
  sound name.

## Pi: v7 model is the default (2026-10-14)

### Pi

- The detector now loads `garbageguard_v7_best_ncnn_model` by default. Make it
  on the Pi with `yolo export model=garbageguard_v7_best.pt format=ncnn imgsz=640`.
  The v5 folder still works with `--model garbageguard_v5_best_ncnn_model`.

## Pi: Test vibration over Firebase (2026-10-13)

### Pi

- New Firebase command `vibe_test` (`{}` or `{"pattern": "test"}`), so the app's
  Test vibration key can work away from the Pi's Wi-Fi too. Needs a Pi update
  and an app change to call it.

## App version 10: Pi buzzer and vibration card (2026-10-13)

App side of `Mobile App/BUZZER_APP.md`. Works on the Pi's Wi-Fi and through
Firebase.

### App

- New "Pi buzzer" card in Settings, the twin of the dashboard's Sound card:
  master switch, volume, alerts, countdown and shutdown, button sounds,
  repeat alarm and its interval, and quiet hours with From and Until shown
  in 12-hour time. Under it, the vibration motor's own switch and strength.
- Test alarm, Test tune and Test vibration keys. Each locks until the Pi
  answers, like every other command key.
- One Save for the card. It sends only the fields that changed: one
  command for the buzzer and one for the vibration motor, each only if
  something of its own changed.
- Warning lines when the buzzer or the motor is not available (with the
  Pi's reason), when quiet hours are silencing the buzzer now, and when the
  Pi's clock is not synchronised.
- Through Firebase the Pi's answer to a buzzer command is used to update
  the card, so no extra read of the state is made. A test reads nothing
  back. Test vibration has no Firebase command and is offered on Wi-Fi only.
- The card is hidden on a Pi without buzzer fields, and the vibration rows
  on a Pi without a motor.

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
- Tried on the Pi 5 with the buzzer wired: both PWM backends (hardware PWM is
  the steady one), the start-up tune, tests, button ticks, alarm repeat, mute,
  quiet hours, and the manual Shut down button.

## Pi vibration motor (2026-10-13)

### Pi

- New `gg_vibe.py`: a vibration motor module on GPIO13 plays pulse patterns
  alongside the buzzer's sounds (alarm and its repeat, countdown, shutdown
  tunes, tests, auto-mute, captures). Strength is the PWM duty (Low 40%,
  Medium 70%, High 100%, to be tuned on the bench), and it follows the same
  quiet hours and mute as the buzzer. It has its own switch and works with the
  buzzer off.
- New `POST /api/vibe`, `POST /api/vibe/test` and the Firebase command `vibe`.
  The state carries `vibe_on`, `vibe_strength`, `vibe_available`, `vibe_note`.
- The dashboard's Sound card has a Vibration switch, a Low, Medium or High
  strength, and a Test vibration button. The mock server and `BUZZER_APP.md`
  cover the same for the app (`GG_MOCK_NOVIBE=1` pretends it is broken).
- Not tested on real hardware: the motor module has not arrived.

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

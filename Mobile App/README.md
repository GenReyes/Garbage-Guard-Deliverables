# mobile-app/

The Garbage-Guard Android app and everything needed to work on it without
the Raspberry Pi, the camera, or the model. App developer: Brian.

## What is in this folder

| Item | What it is |
|---|---|
| `android/` | The Android Studio project (Kotlin, Jetpack Compose, package `com.garbageguard.app`). Open this folder in Android Studio. |
| `Garbage-Guard v12.apk` | The latest build, ready to install on a phone. Only the newest version is kept here. |
| `BUZZER_APP.md` | What the app needs to add for the Pi's buzzer: the card, the fields, the warnings, and how to try it on the mock server. |
| `gg_mock_server.py` | A stand-in for the Pi's server with the same API and invented data. Lets the whole app run with no hardware. |
| `GarbageGuard_API.md` | The API reference: every endpoint, with request and response examples. |
| `FIREBASE.md` | How the app reaches the Pi through Firebase when it is not on the Pi's Wi-Fi. The Pi side is built. |
| `HISTORY_TOOLS.md` | What the app's History page does (filters, date range, scoped Mark read and Delete, a look for every event), written so the web dashboard can match it. |
| `SYNC_PLAN.md` | Earlier comparison of options for remote access (Tailscale, Firebase, MQTT). Superseded by `FIREBASE.md`. |

## What the app does

The app is a second screen for the Pi. It reads the same API as the
touchscreen dashboard, so a change made on one shows on the other within a
few seconds.

- **Monitor:** item count against the limit, per-class counts, the live
  camera feed, capture, mute and auto-mute, and camera reconnect.
- **Fullscreen:** the picture takes the full height of the phone at the
  camera's own shape, with readouts and keys on the bezel either side.
  Tapping the picture hides them.
- **History:** every alert, capture, test, mute and shutdown event with its
  snapshot. A row of symbols filters by kind and a calendar sets a date
  range; Mark read and Delete then act on exactly the records shown.
- **Settings:** detection confidence, accumulation limit, the monitored
  area (drawn on the live picture), light and dark theme, network details,
  connected phones, and restarting Garbage-Guard on the Pi.
- **Pi battery:** the UPS HAT readout in the header, with full details on tap.
- **Pi buzzer:** the buzzer's switch, volume, alarm repeat and quiet hours, and Test keys.
- **Pi power:** the Pi's screen saver, waking or sleeping its screen from the
  phone, the low-battery auto shutdown with a countdown banner and Cancel,
  and a Shut down button. To try the countdown with no Pi, run the mock
  server and send `POST /api/mock/low_battery`.
- **Alerts:** a notification with the snapshot when the limit is reached,
  including while the app is in the background. Each kind of event has its
  own sound: a buzzer for the limit, and different tones for test,
  auto-mute, capture, mute and unmute. The sounds are separate notification
  channels, so each can be changed or silenced in Android's settings.
- **Sound and feel:** button sounds, vibration and keep-screen-on, each
  with a switch in Settings.
- **Firebase:** remote view when the phone is away from the Pi's Wi-Fi,
  with the month's data use shown in Settings.
- **Any screen:** the layout is drawn once for a phone 411 dp wide and scaled
  to fit, so small phones, large phones and tablets show the same design.
  Phones stay upright except in fullscreen and full-size views; on a tablet
  held sideways the pages sit as a phone-wide column in the middle.
- **Home-screen icon:** the light logo or the dark one, following the theme
  the app was last left in. It changes when the app leaves the screen.
- **Demo mode:** runs on invented data with no Pi, for showing the app.

The look is the project's own "Brutal-Morph" style: the soft depth of
Neumorphism with the hard edges and contrast colours of Neo-Brutalism.

## How it connects

The phone and the Pi must be on the same Wi-Fi or hotspot. On first launch
the app asks for the Pi's address, for example `192.168.1.101:8080`, tests
it, and remembers it. On the same Wi-Fi there is no login and no cloud service; the app talks
to the Pi directly over plain HTTP on port 8080.

A router with client isolation switched on blocks this even when both
devices show as connected. The team has hit that once already, so test at
the venue early.

Away from the Pi's Wi-Fi, the app falls back to Firebase. Local Wi-Fi is
always tried first; Firebase is the second choice, and the app returns to
local Wi-Fi by itself when the Pi is in reach again. The header pill shows
"Cloud" in orange while Firebase is in use. Over Firebase the app shows a
still every 3 seconds in place of live video, and restarting the Pi is not
available. The details are in **`FIREBASE.md`**.

Firebase needs four values, entered once in Settings, Firebase, Account:
the database URL, the Web API key, and the email and password of one phone
account. "Test and save" checks them before they are kept, and the password
is stored encrypted on the phone. To build an APK with the values already
filled in, add these lines to `android/local.properties`, which is never
committed:

```
gg.fb.url=https://<project>-default-rtdb.<region>.firebasedatabase.app
gg.fb.key=<Web API key>
gg.fb.email=<phone account email>
gg.fb.password=<phone account password>
```

## Installing the app on a phone

Copy `Garbage-Guard v12.apk` to the phone, open it, and allow the install
when Android asks. Installing a newer version over an older one keeps the
saved Pi address. Android 8.0 or newer is required.

## Building it

1. Open `mobile-app/android` in Android Studio and let Gradle sync.
2. Press Run with a phone or emulator selected.

From a terminal, in `mobile-app/android`:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Working without the Pi

```bash
python3 gg_mock_server.py
```

Plain Python 3, nothing to install. It serves the same API as the Pi with
counts that drift up and down, a placeholder camera frame, a seeded history,
an invented battery, and invented network details. In the app, enter your
computer's LAN address with port 8080.

The app's own demo mode needs no server at all: choose "Try with demo data"
on the first screen.

## Where things are in the code

| Path under `android/app/src/main/java/com/garbageguard/app/` | Purpose |
|---|---|
| `data/GgRepository.kt` | Everything the screens need from the Pi, one function per endpoint. |
| `data/PiRepository.kt` | The real connection: HTTP calls and JSON parsing. |
| `data/FakeRepository.kt` | Invented data for demo mode. |
| `ui/AppViewModel.kt` | App state and the actions behind every button. |
| `ui/screens/` | Setup, Monitor, History, Settings, fullscreen, and the bottom sheets. |
| `ui/components/` | The shared look: shadows, buttons, icons, the live feed, the battery. |
| `notify/` | Alert notifications and their sounds, button sounds and vibration, and the background watcher. |

## Rules that come from the thesis

- Say "Detection Confidence", not "Accuracy". Accuracy is a scored metric
  in the thesis.
- Class names are `bottle`, `bag` and `food_container`.
- Show `smoothed`, not `raw`. The raw count flickers from frame to frame.
- Latency in milliseconds is the scored performance metric. Frame rate may
  be shown beside it.

`GarbageGuard_API.md` has the full list and the reasons.

## The dashboard as a reference

`../pi/dashboard.html` is the touchscreen UI and calls every endpoint the
API reference describes. When the reference is unclear, its JavaScript shows
how an endpoint is used. It is kept once, in `pi/`, so there is only one
copy to keep current.
# Garbage-Guard — Deliverables

Capstone project: computer-vision detection of floating solid waste in
creeks, running on a Raspberry Pi 5 with a Hikvision camera over RTSP.
A project made by 5 people in TUP-Manila.

## What is in this repository

### [`Raspberry Pi 5/`](Raspberry%20Pi%205/) — the system

Camera, model, detection, counting, alerts, the buzzer, the UPS battery
readout, Firebase sync and the touchscreen dashboard. Start in
[`Raspberry Pi 5/README.md`](Raspberry%20Pi%205/README.md).

### [`Mobile App/`](Mobile%20App/) — the Android app

The Android Studio project, the installable APK, a mock server that speaks
the same API as the Pi, the API reference and the Firebase guide. The app
shows the live count, feed, history, settings and Pi battery, and raises
alert notifications, on the Pi's Wi-Fi or through Firebase when away. Start
in [`Mobile App/README.md`](Mobile%20App/README.md).

### [`models/`](models/) — the trained detector

The v7 weights and their test results.

### [`Documents/`](Documents/) — the thesis

The manuscript, its figures, the cited sources and the Chapter IV field
material.

## Why the Pi and the app are split

The app only ever talks to the Pi over HTTP (or through Firebase). It has no
code dependency on anything in `Raspberry Pi 5/`, so it can be built and
tested against the mock server with no hardware in the room, and changes to
the dashboard's styling never touch the app's code.

`dashboard.html` lives once, in `Raspberry Pi 5/`. The app README points to
it as a reference rather than keeping a second copy.

## Quick links

| I want to... | Go to |
|---|---|
| Run the system on the Pi | [`Raspberry Pi 5/README.md`](Raspberry%20Pi%205/README.md) → Running it |
| Run the tests | [`Raspberry Pi 5/README.md`](Raspberry%20Pi%205/README.md) → Running the tests |
| Build the mobile app | [`Mobile App/README.md`](Mobile%20App/README.md) |
| Install the app on a phone | [`Mobile App/Garbage-Guard v13.apk`](Mobile%20App/Garbage-Guard%20v13.apk) |
| Look up an API endpoint | [`Mobile App/GarbageGuard_API.md`](Mobile%20App/GarbageGuard_API.md) |
| Set up or calibrate at the creek site | [`Raspberry Pi 5/GarbageGuard_Field_Reference.md`](Raspberry%20Pi%205/GarbageGuard_Field_Reference.md) |
| See what changed in each version | [`CHANGELOG.md`](CHANGELOG.md) |
| Read or edit the thesis paper | [`Documents/`](Documents/) |

## Not in this repo, on purpose

`camera.txt`, `gg_settings.json` and `gg_firebase.json` hold the camera's
RTSP password, per-device settings and the Pi's Firebase login. They are
excluded by `.gitignore` and documented in `Raspberry Pi 5/README.md`. Never
commit a filled-in copy of any of them.

# Garbage-Guard — Deliverables

Capstone project: computer-vision detection of floating solid waste in
creeks, running on a Raspberry Pi 5 with a Hikvision camera over RTSP.
A project made by 5 people in TUP-Manila.

## Two sides, two folders

The project splits cleanly into two halves that almost never need to change
together, so they live in separate folders:

### [`pi/`](pi/) — the Raspberry Pi side

The actual system. Camera, model, detection, counting, alerts, and the
touchscreen dashboard. This is the half that's been under constant change —
new UI styling, mute, fullscreen, history deletion, display scaling. If
you're working on detection or the dashboard, start in
[`pi/README.md`](pi/README.md).

### [`mobile-app/`](mobile-app/) — for the mobile app

Everything King Perth needs to build the app, with no Pi, camera, or model
required. A mock server that speaks the exact same API as the real system,
plus the full API reference. Start in
[`mobile-app/README.md`](mobile-app/README.md).

## Why split this way

The app only ever talks to the Pi over HTTP — it has no code dependency on
anything in `pi/`. Keeping them in separate folders means Perth can build
and test the entire app against the mock server before the hardware even
exists in the same room as him, and changes to the dashboard's styling
never touch anything he's working on.

`dashboard.html` lives once, in `pi/`, even though it's also useful to
Perth as a reference implementation — `mobile-app/README.md` points to it
rather than duplicating it, so there's only ever one copy to go stale.

## Quick links

| I want to... | Go to |
|---|---|
| Run the system on the Pi | [`pi/README.md`](pi/README.md) → Running it |
| Run the counting tests | [`pi/README.md`](pi/README.md) → Running the tests |
| Build the mobile app | [`mobile-app/README.md`](mobile-app/README.md) |
| Look up an API endpoint | [`mobile-app/GarbageGuard_API.md`](mobile-app/GarbageGuard_API.md) |
| Set up or calibrate at the creek site | [`pi/GarbageGuard_Field_Reference.md`](pi/GarbageGuard_Field_Reference.md) |

## Not in this repo, on purpose

`camera.txt` and `gg_settings.json` hold the camera's RTSP credentials and
per-device settings. They're excluded by `.gitignore` and documented in
`pi/README.md`. Never commit a filled-in copy of either.

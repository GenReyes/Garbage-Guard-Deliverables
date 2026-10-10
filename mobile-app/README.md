# mobile-app/ — for King Perth

Everything needed to build the mobile app, without needing the Raspberry Pi,
the camera, or the model. This folder is intentionally small: two files,
neither of which needs installing anything beyond Python 3.

## Why this folder is separate from `pi/`

The app talks to the Pi over plain HTTP. It never imports Pi-side code,
never touches the model, and never needs a camera plugged in anywhere. So
nothing in `../pi/` is a dependency of the app — it's just the thing the
app eventually points at. Keeping the two folders apart means you can build
and test the whole app before the hardware is even switched on.

## Files

| File | What it's for |
|---|---|
| `gg_mock_server.py` | Run this. A fake version of the Pi's server, same API, invented data. Lets you build the whole app with no hardware. |
| `GarbageGuard_API.md` | The API reference. Every endpoint, with real request and response examples. |
| `SYNC_PLAN.md` | Design plan for syncing the app and a portable dashboard with the Pi through Firebase or other options. Nothing is built yet. |

## Getting started

```bash
python3 gg_mock_server.py
```

No install step — it's plain Python 3 standard library, nothing to `pip
install`. It prints the URLs it's serving on startup. Point your app at:

```
http://localhost:8080
```

or, from a phone or emulator, your computer's LAN IP instead of
`localhost`.

The mock server invents a live-looking feed: counts that drift up and down
on their own, a placeholder camera frame, and a history log seeded with one
of each event type — including one row with no snapshot, so your UI has to
handle a missing image without crashing.

## When the real Pi is available

Change one thing: the base URL, from `http://localhost:8080` to
`http://<the-pi's-ip>:8080`. Nothing else in the app should need to change,
because the mock server implements the exact same API described in
`GarbageGuard_API.md`.

The Pi and the phone have to be on the same network for this to work. A
router with client isolation enabled will silently block it even when both
devices show as connected — that's a real problem the team already hit once
with the Pi itself, so it's worth testing early rather than assuming it'll
work at the venue.

## Read `GarbageGuard_API.md` for

- Every endpoint: what it returns, what it accepts, what each field means
- Which fields to actually display (`smoothed`, not `raw`) and why
- The event types a history row can have, and suggested colors for each
- The naming that has to match the thesis — "Detection Confidence," not
  "Accuracy"; the real class names, `bottle` / `bag` / `food_container`
- The mute and read/unread behavior, including the one auto-lift rule that
  matters: a mute clears itself once the count drops back below the
  re-arm value, so it can never silently swallow the next real alert

## Seeing the real thing for comparison

`../pi/dashboard.html` is the actual touchscreen UI, built against this
exact API. It's kept in `pi/` rather than duplicated here, since a second
copy would just drift out of sync with the real one over time. If anything
in the API doc is ambiguous, open that file and read the JavaScript at the
bottom — it calls every endpoint this doc describes, so it's a working
answer to "how is this actually used."

You don't need to run it or understand its styling. It's a reference for
behavior, not something the app has to match visually.

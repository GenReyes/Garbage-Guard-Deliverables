# Pi Buzzer Card: What the App Should Add

**Status: built in app version 10.** The rest of this file is kept as the record of what was agreed.

The Pi now has a passive buzzer in its case. It plays a short tune for each
History event, repeats the alarm while an alert is raised and not muted, and
can be silenced for a daily window (quiet hours). The dashboard's Settings tab
has a **Sound** card for it. This file is what the app needs to match.

The server side is finished and tested on the Pi. Everything here is in
`GarbageGuard_API.md` (the `/api/buzzer` sections) and `FIREBASE.md` (the
`buzzer` and `buzzer_test` commands). `gg_mock_server.py` now answers all of it,
so the card can be built with no Pi.

## 1. The card

Put it in Settings, next to the Screen saver and Power cards, and name it
**"Pi buzzer"** so nobody mistakes it for the phone's own sounds. Same controls
as the dashboard, in this order:

| Control | Field | Notes |
|---|---|---|
| Buzzer switch | `buzzer_on` | Master. Off silences everything below. |
| Volume | `buzzer_volume` | `low`, `medium`, `high`. |
| Alerts | `buzzer_alerts` | The alarm when the limit is reached. |
| Countdown and shutdown | `buzzer_countdown` | The low-battery beeps and the shutdown tune. |
| Button sounds | `buzzer_ui` | Ticks on the Pi's own touchscreen. Off by default. |
| Repeat alarm | `buzzer_repeat` | |
| Repeat every | `buzzer_repeat_s` | 10 to 300 seconds, steps of 10. Shown only while Repeat is on. |
| Quiet hours | `quiet_on` | |
| From, Until | `quiet_from`, `quiet_to` | `HH:MM` on the Pi's clock. Show them in 12-hour time ("10:00 PM"), step 30 minutes. Shown only while Quiet hours is on. |
| Test alarm | `POST /api/buzzer/test` `{"sound": "accumulation"}` | |
| Test tune | `POST /api/buzzer/test` `{"sound": "test"}` | |

Save the same way as the Screen saver card: one Save sends one
`POST /api/buzzer` with every buzzer field (all are optional and clamped by the Pi).
The test buttons use the volume the Pi has saved, so say that in a hint.

## 2. Things the card must show

- `buzzer_available` is `false`: a red line "The buzzer is not available: ..."
  with `buzzer_note`, and the Test buttons disabled. A Test while unavailable
  answers `409 unavailable` with the same message.
- `quiet_now` is `true`: a line "Quiet hours are on now, so the buzzer is silent."
- `quiet_note` is not `null`: show it. It means the Pi's clock is not
  synchronised, so quiet hours are not being applied.
- The Pi no longer has a vibration motor (dropped 2026-10-17). A Pi sends no
  `vibe_*` fields, so the app hides its Pi vibration rows when `vibe_on` is missing.
- Old Pi software has none of these fields. If `buzzer_on` is missing, hide the
  card.

## 3. Behaviour worth knowing

- Muting an alert, from the app or the Pi, stops the alarm and its repeat.
- Quiet hours silence only the buzzer. The phone's notification, the
  dashboard's banner and History are unchanged. There is no exception, not
  even for the low-battery floor warning.
- Quiet hours use the Pi's clock (Philippine time). Right after boot the Pi can
  have the wrong time for a minute or two; `clock_settling` is `true` then, and
  the app should not warn yet. `clock_synced: false` with `clock_settling:
  false` is a real problem worth a warning next to any time taken from the Pi.
- Over Firebase use the `buzzer` and `buzzer_test` commands with the same
  bodies. The settings arrive in the `state` node, so no extra read is needed.
- `POST /api/buzzer/ui` is for the Pi's own touchscreen and answers `403` to
  anyone else. The app never calls it.

## 4. Trying it with no Pi

```bash
python3 gg_mock_server.py                          # normal
GG_MOCK_NOBUZZER=1 python3 gg_mock_server.py       # buzzer cannot start
GG_MOCK_CLOCK_OFF=1 python3 gg_mock_server.py      # clock not synchronised
```

The mock keeps the settings, applies the same limits as the Pi, works out
`quiet_now` from the real Philippine time, and prints a line instead of making
a sound when a Test is sent.

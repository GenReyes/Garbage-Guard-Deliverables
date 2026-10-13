# History Page: What the App Does Now, for the Web Dashboard

The Android app's History page changed in versions 9.0 to 9.3. This file
lists what it does so that the dashboard's History tab (`pi/dashboard.html`)
can match it.

**No server change is needed.** Everything here uses the endpoints that
already exist: `GET /api/alerts`, `POST /api/alerts/read` and
`POST /api/alerts/delete`.

## 1. What the dashboard has today

| Feature | Dashboard | App 9.3 |
|---|---|---|
| From and To dates | Yes, used only by Delete range | Yes, and they filter the list |
| Mark all read | Yes | Yes |
| Delete range | Yes | Yes |
| Delete all | Yes | Yes |
| Shutdown events shown in orange | Yes | Yes |
| Filter by kind of event | No | Yes |
| From cannot be after To | No | Yes |
| Mark a date range or a kind as read | No | Yes |
| Actions act on exactly what is shown | No | Yes |
| A line saying what is shown | No | Yes |
| A look for every event, ranked by urgency | Partly | Yes |

## 2. Filter by kind

One bar of six symbols on a single line. The chosen one is raised and the
rest are sunk. It filters the table at once, with no Apply button.

| Filter | Symbol | Events it shows |
|---|---|---|
| All | layers | every event |
| Alerts | bell | `accumulation` |
| Captures | camera | `manual` |
| Tests | wrench | `test` |
| Mutes | crossed eye | `muted`, `auto_muted`, `unmuted` |
| Power | power | `low_battery_shutdown`, `manual_shutdown` |

`suppressed` rows appear only under All.

## 3. Dates

- The dates filter the table. Only rows whose date falls inside the range
  are shown. Either end may be left empty for an open range.
- **No future days.** Set `max` to today on both inputs.
- **From cannot be after To.** Set From's `max` to To's value and To's `min`
  to From's value. The app goes one step further: picking a From after the
  To moves To along to match, and the other way round, so the range can
  never run backwards.
- Quick picks: Today, 7 days, 30 days, Any.
- The browser's own date input already rejects days that do not exist,
  leap years included, so nothing more is needed for that.

## 4. One Mark read and one Delete

Both buttons act on exactly the rows on screen. What to send:

| What is filtered | Mark read | Delete |
|---|---|---|
| Nothing | `{"all": true}` | `{"all": true}` |
| Dates only | `{"ids": [...]}` | `{"from": "2026-10-10", "to": "2026-10-12"}` |
| A kind, with or without dates | `{"ids": [...]}` | `{"ids": [...]}` |

Notes:

- `/api/alerts/read` has no date form, so a range is marked read by sending
  the ids of the rows shown.
- With dates only, Delete sends the range and not ids. The Pi then deletes
  every record in the range, including ones older than the page has loaded.
- Mark read is disabled when no shown row is unread. Delete is disabled when
  no row is shown.
- This replaces the three separate buttons (Mark all read, Delete range,
  Delete all) with two that follow the filter.

## 5. Say what is shown

A line under the tools states the scope in words, and the Delete
confirmation repeats it with the count:

- "Showing all 7 records"
- "Showing 2 alerts on 12 Oct"
- "Showing 3 captures from 10 Oct to 12 Oct"
- Confirmation: "Delete 2 alerts on 12 Oct and their snapshot images? This
  cannot be undone."

When nothing matches: "No records match. Change the filter or the dates."

## 6. A look for every event, ranked by urgency

Unread rows are raised; read rows are sunk and dimmed, as they are now. How
far an unread row stands out follows how urgent the event is.

- Ranks 1 to 3 carry their colour on the **whole border** and on the hard
  shadow.
- Ranks 4 to 6 keep the normal ink border and show their colour as a **band
  about 5 px wide down the left edge**.
- The unread dot takes the row's colour in every case.
- Only the accumulation alert pulses.

| Rank | Event | Colour | Border | Tint of the row | Lift |
|---|---|---|---|---|---|
| 1 | `accumulation` | red | red | red tint | most, with a slow pulse |
| 2 | `low_battery_shutdown` | orange | orange | strong (about 22%) | high |
| 3 | `manual_shutdown` | orange | orange | light (about 12%) | normal |
| 3 | `auto_muted` | amber | amber | light (about 16%) | normal |
| 4 | `test` | amber | ink, amber band | faint (about 10%) | normal |
| 4 | `manual` | teal | ink, teal band | faint (about 10%) | normal |
| 5 | `muted`, `unmuted` | purple | ink, purple band | faint (about 8%) | low |
| 6 | `suppressed` | grey | ink, grey band | faintest (about 5%) | low |

The tint is the event's colour mixed into the normal raised surface by the
given amount. The dashboard's own colour variables map directly: `--bad`,
`--orange`, `--warn`, `--teal`, `--purple`, and the muted text colour for
grey.

For the two shutdown rows, show the event's symbol (battery or power) where
the item count would be. A shutdown has no count, and "0" there reads as a
real reading.

## 7. Where to look in the app

| Part | File in `mobile-app/android/app/src/main/java/com/garbageguard/app/` |
|---|---|
| Filter bar, date sheet, calendar | `ui/screens/HistoryTools.kt` |
| Filter and date logic, scoped Mark read and Delete | `ui/AppViewModel.kt`, section "history" |
| The look of each event (`lookOf`) | `ui/screens/HistoryScreen.kt` |

To try it without the Pi, run `mobile-app/gg_mock_server.py`. To get a
low-battery shutdown row, send `POST /api/mock/low_battery` with
`{"seconds": 5}` and wait for the countdown to end.

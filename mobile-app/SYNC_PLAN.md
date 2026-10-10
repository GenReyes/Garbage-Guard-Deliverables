# Garbage-Guard: Phone Sync Plan (Firebase and alternatives)

Status: **design only, nothing here is built yet.** The goal is a portable version of the touchscreen dashboard that lives on a phone and stays in sync with the mobile app, without exposing the Pi to the internet.

Facts about Firebase limits were checked on 2026-10-10. Re-check the pricing page before committing, because plans change.

## 1. What "synced" has to cover

| Need | How often | Size | Must work when away from home Wi-Fi? |
|---|---|---|---|
| Counts, alert state, mute state, FPS | every few seconds while someone is watching | ~300 bytes | yes |
| Alert events (history) | a few per day | ~1 KB each | yes |
| Alert snapshot photos | a few per day | 30-150 KB each | yes, ideally |
| Commands from phone (mute, auto-mute, threshold, ROI, capture, test alert) | rare | tiny | yes |
| Live video | continuous | big | optional |

Live video is the odd one out. It should not go through Firebase (see section 4).

## 2. Options compared

| Option | Cost | Live video | Alerts and push | Effort | Main catch |
|---|---|---|---|---|---|
| **A. Tailscale (or Cloudflare Tunnel) to the Pi** | Free tier is enough | Yes, same `/stream.mjpg` | Needs separate push | Very low, no code | Phone must have the Tailscale app. Tunnel option exposes the dashboard, which has no login. |
| **B. Firebase Firestore + FCM** | Spark plan, no card | No | Yes, FCM is free | Medium | No Cloud Storage or Cloud Functions on Spark. Daily write quota. |
| **C. MQTT broker (HiveMQ Cloud free, or self-hosted)** | Free tier | No | Needs a bridge for push | Medium | Phone app needs an MQTT client. No built-in database. |
| **D. Supabase (Postgres + Realtime + Storage)** | Free tier | No | Needs separate push | Medium | Free projects pause after inactivity. Different stack from what the team may know. |
| **E. Pi-hosted public URL (port forward)** | Free | Yes | n/a | Low | Do not do this. No authentication on the Pi server. |

### Recommendation

Do it in phases so something useful exists early:

1. **Phase 0, one evening:** install Tailscale on the Pi and a phone. The phone opens `http://<pi-tailscale-name>:8080` and gets the full existing dashboard including live video. This already delivers "portable UI". It only works for people on the same Tailscale network, which is fine for the team and a demo.
2. **Phase 1:** Firestore for state, alerts and commands, plus FCM push for alerts. This is what the mobile app talks to, and it works for anyone with the app and a login.
3. **Phase 2:** host the dashboard as a web app (PWA) on Firebase Hosting, using the same HTML with a "remote" adapter instead of the local one.

## 3. Architecture (local first)

```
Camera -> Pi (detection, ByteTrack, counting)  <- source of truth, works with no internet
              |  outbound only (no open ports)
              v
          Firestore  <-- Mobile app (Auth login)
              ^
              |  commands collection, Pi listens
          Portable dashboard (PWA, same HTML)
```

Rules that keep it robust:

- The Pi never depends on the cloud. If the internet drops, detection, the local dashboard, alerts and the buzzer all keep working. Sync code runs in its own thread and fails quietly.
- The Pi only makes **outbound** connections. No router changes, no exposed port.
- Commands are written by the phone to a `commands` collection. The Pi listens, applies them through the same functions the local API uses (`set_mute`, `set_auto_mute`, settings, ROI), writes the result back, and marks the command done.
- Anything the phone sends is validated on the Pi with the same limits as `/api/settings`.

## 4. Firebase on the free (Spark) plan

Limits to design around:

| Item | Spark allowance | What it means here |
|---|---|---|
| Firestore storage | 1 GiB | Plenty if old alerts are pruned |
| Firestore reads | 50,000 / day | Each phone listener counts per document change, not per second |
| Firestore writes | 20,000 / day | **The one to watch**, see below |
| Firestore deletes | 20,000 / day | Pruning is fine |
| Firestore egress | 10 GiB / month | Fine without photos |
| Hosting | 10 GB storage | Fine for a PWA |
| FCM push | free | Needs a sender, see below |
| Auth | free (email, Google, not phone/SMS) | Fine |
| **Cloud Storage** | **not on Spark** | Cannot store full snapshot photos without upgrading |
| **Cloud Functions** | **not on Spark** | Cannot run server code, so the Pi must send pushes itself |

The Blaze plan (card required) keeps the same free quotas and adds Storage and Functions, but billing alerts do not hard-cap spend. If the team does not want a card on file, stay on Spark and use the workarounds below.

### Write budget

Writing a state document every 5 seconds is 17,280 writes a day, which leaves almost nothing for alerts and commands. Instead:

- **Idle (nobody watching):** write state at most once per minute and on any change in count or alert level. About 1,500 writes a day.
- **Watching:** the phone sets a `presence` document with a timestamp every 30 seconds. While presence is fresh, the Pi writes state every 3-5 seconds.
- Alerts, mute changes and commands are always written immediately. They are rare.
- Hard guard in code: if the day's write counter passes about 15,000, drop to once per minute until midnight.

### Photos without Cloud Storage

On Spark, store a small thumbnail (JPEG about 320 px wide, roughly 15-25 KB) as base64 inside the alert document (Firestore documents can be up to 1 MiB). Full-size photos stay on the Pi and open only over Tailscale. If the team later moves to Blaze, switch to Cloud Storage and keep only the URL in the alert document.

### Push notifications without Cloud Functions

Two workable ways:

1. **Pi sends FCM directly** using the Firebase Admin SDK and a service-account key stored on the Pi. Simple, but the key is powerful. Keep it outside the repo (`.gitignore`), readable only by the Pi user, and scoped to messaging only if possible.
2. **Use ntfy.sh** (a free push service) for alerts and keep Firebase for data only. No key to guard.

Pick one before Phase 1. Never commit either secret.

## 5. Data model (Firestore)

```
devices/{deviceId}                    one Pi per document
  name, last_seen, online, version
  state                               map, overwritten each push
    bag, container, total_threshold, alert, level,
    fps, cam_fps, latency_ms, muted, mute_remaining_s,
    auto_mute, auto_mute_s, auto_muted
  settings                            map: conf, threshold, roi[], auto_mute, auto_mute_s

devices/{deviceId}/alerts/{alertId}
  ts, kind (alert, cleared, test, auto_muted), count, threshold,
  read (bool), thumb_b64 (optional, about 320 px)

devices/{deviceId}/commands/{cmdId}
  type (mute, unmute, auto_mute, settings, roi, capture, test_alert)
  args (map), created_by (uid), created_at,
  status (pending, done, rejected), result (string)

devices/{deviceId}/presence/{uid}     phone writes every 30 s while open
  ts
```

This mirrors the existing local API in `mobile-app/GarbageGuard_API.md`, so the same field names work in both places.

### Security rules (sketch)

- Only signed-in users listed in `devices/{id}/members/{uid}` can read the device.
- Members may create `commands` and write their own `presence`. They may not write `state`, `settings` or `alerts` directly.
- The Pi signs in with its own account and is the only writer of `state`, `settings` and `alerts`.
- Commands are validated on the Pi, because rules cannot check ranges well.

Test the rules with the Firebase emulator before going live. Test mode ("open to everyone") must never be left on.

## 6. Portable UI

`dashboard.html` currently calls `fetch("/api/...")`. To reuse it:

1. Put all network calls behind one small adapter with two implementations:
   - `local`: the existing `fetch` calls. Used on the Pi and over Tailscale.
   - `remote`: Firestore listeners for state and alerts, writes to `commands`.
2. Choose the adapter from the URL or a setting. The screens, styles and layout code stay identical, so the phone shows the same UI the touchscreen does.
3. Live video: `remote` mode shows "Live view available over Tailscale" and the latest alert thumbnail instead. Real remote video would need WebRTC plus a relay, which is a separate project.
4. Ship it as a PWA (manifest plus service worker) on Firebase Hosting so it installs to the home screen.

This keeps one UI to maintain instead of two.

## 7. Phases and rough effort

| Phase | What | Effort | Needs |
|---|---|---|---|
| 0 | Tailscale on Pi and phones | 1-2 hours | Tailscale account |
| 1a | Firebase project, Auth, rules, emulator tests | 1 day | Google account |
| 1b | `gg_sync.py` thread on the Pi: state push, alert push, command listener, write budget guard | 2-3 days | 1a, service account |
| 1c | Push notifications (FCM or ntfy) | 1 day | decision in section 4 |
| 2 | Adapter in `dashboard.html`, PWA, Hosting | 2-3 days | 1b |
| 3 | Optional: Blaze plan, Cloud Storage for full photos | 1 day | team decision on a card |

## 8. Open questions for the mobile team

- What is the mobile app built with (Flutter, React Native, native, web)? This decides which Firebase SDK is used and whether the app or the PWA is the main phone UI.
- Who needs access (team only, or the barangay staff too)? That decides the Auth method and the `members` model.
- Is a card on file acceptable for the Blaze plan, or must this stay on Spark?
- Is remote live video a requirement, or are alert photos enough?
- Are push notifications required when the phone is away from home, or only inside the app?

## 9. Not verified

- Free-tier numbers come from Firebase documentation read on 2026-10-10 and may have changed.
- None of the code in this plan exists yet, and nothing here has been run on the Pi.
- The Pi's upload bandwidth and battery or UPS runtime with a constant outbound connection have not been measured.

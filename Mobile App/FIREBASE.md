# Garbage-Guard and Firebase — guide for the mobile app

The Pi now copies its data to a Firebase **Realtime Database**, so the app
keeps working when the phone is away from the Pi's Wi-Fi.

**Rule number one: same Wi-Fi always comes first.** Firebase is only the
fallback. On the same Wi-Fi the app keeps using the Pi's own HTTP API
(`GarbageGuard_API.md`), exactly as it does today: live video, full-size
snapshots, instant commands. The Pi keeps updating Firebase in both cases,
so the app can switch at any moment.

| | Same Wi-Fi (local) | Away (Firebase) |
|---|---|---|
| State (counts, alert, mute, battery) | `GET /api/state` every 1 s | `/gg/state`, updated every 3 s while a phone watches; otherwise every 2 min, and at once when the alert or mute changes |
| History | `GET /api/alerts` | `/gg/alerts`, synced every 10 s |
| Snapshots | `GET /snapshots/<name>`, full size | `/gg/snaps/<id>`, compressed copy (960 px wide, at most 300 KB) |
| Live view | `/stream.mjpg` or `/frame.jpg`, real video | **No video.** `/gg/preview` is a 360 px still, refreshed every 3 s, only while a phone says it is on the live screen |
| Commands (mute, threshold, ROI, capture …) | `POST /api/...`, instant | write to `/gg/commands`, the Pi runs it within about 2 s (10 s when no phone is watching) and writes the answer to `/gg/results` |
| Restart Garbage-Guard | `POST /api/restart` | **not allowed** over Firebase, on purpose |
| Pi network settings, Wi-Fi | Pi screen only | not available |

### Live feed: please tell the user

Real video over Firebase is not possible on the free plan: there is no
streaming in Realtime Database, and 10 GB of downloads a month would run out
in hours. In Firebase mode show the preview still with a clear label, for
example **"Remote view: a still picture every 3 seconds. Join the Pi's Wi-Fi
for live video."** Grey out anything that only works locally (live video,
full-size snapshot download, restart).

---

## 1. Choosing local or Firebase

Do this when the app starts, when the network changes
(`ConnectivityManager.NetworkCallback`), and every 30 s while in Firebase mode:

1. Try every local address you know, with a **1.5 s timeout**, by calling
   `GET <url>/api/state`:
   - the address the user typed (as today)
   - every entry in `/gg/pi/local_urls` (the Pi writes its current local
     addresses there, for example `["http://192.168.1.15:8080"]`); save these
     so they are known even when Firebase is unreachable later
2. The first one that answers wins: **local mode**.
3. If none answer and Firebase settings are filled in: **Firebase mode**.
4. If neither works: "Can't reach the Pi" (the existing red state).

Show the mode in the header, for example a Wi-Fi icon for local and a cloud
icon (orange on the Pi screen) for Firebase.

Is the Pi itself online? Read `/gg/pi/ts` (milliseconds). If it is older
than 90 s, show "Pi offline since <time>" and treat everything else under
`/gg` as old.

---

## 2. Settings the user can type in

Like the Pi's network screen (Network → Firebase), the app needs a
**Firebase** settings page:

| Field | Example | Check |
|---|---|---|
| Database URL | `https://garbage-guard-xxxx-default-rtdb.asia-southeast1.firebasedatabase.app` | starts with `https://`, ends with `.firebasedatabase.app` or `.firebaseio.com`, no trailing `/` |
| Web API key | `AIzaSy…` | starts with `AIza`, 39 characters |
| Email | the phone user's Firebase account | looks like an email |
| Password | | at least 6 characters |

Buttons, the same as on the Pi:

- **Test**: sign in, then read `/gg_users/<uid>.json`. Show what went wrong
  in plain words (see the error table below).
- **Test and save**: save only when the test passes, and keep that set as
  the **working set**.
- **Restore working**: put back the last set that passed a test.

**Default values:** the app ships with the team's tested set as the default
(the same database URL and API key the Pi uses, plus the phone account).
Do **not** commit the password to GitHub: the repo is public. Put the
defaults in `local.properties` (already git-ignored) and read them through
`BuildConfig`, for example:

```
# local.properties, not committed
gg.fb.url=https://...firebasedatabase.app
gg.fb.key=AIza...
gg.fb.email=app1@garbage-guard.test
gg.fb.password=...
```

```kotlin
// app/build.gradle.kts, inside defaultConfig
val p = java.util.Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
fun prop(k: String) = "\"" + (p.getProperty(k) ?: "") + "\""
buildConfigField("String", "FB_URL", prop("gg.fb.url"))
buildConfigField("String", "FB_KEY", prop("gg.fb.key"))
buildConfigField("String", "FB_EMAIL", prop("gg.fb.email"))
buildConfigField("String", "FB_PASSWORD", prop("gg.fb.password"))
```

Store what the user saves in `EncryptedSharedPreferences` (password
included), never in plain logs.

Errors to translate (from the sign-in call):

| Firebase says | Show |
|---|---|
| `INVALID_LOGIN_CREDENTIALS`, `INVALID_PASSWORD`, `EMAIL_NOT_FOUND` | Wrong email or password. |
| `API key not valid` | The Web API key is wrong. |
| `PASSWORD_LOGIN_DISABLED` / `OPERATION_NOT_ALLOWED` | Email/Password sign-in is off in Firebase Authentication. |
| `TOO_MANY_ATTEMPTS_TRY_LATER` | Too many tries, wait a few minutes. |
| `USER_DISABLED` | This account is disabled. |
| reading `/gg_users/<uid>` gives 401 `Permission denied` | The account is not on the allowed list (`/gg_users`), or the rules were not pasted. |
| reading it gives 404 or `Firebase error. Please ensure that you have the URL of your Firebase database` | The database URL is wrong. |

---

## 3. Talking to Firebase: plain REST, no SDK needed

Because the user can change the URL and key at runtime, the simplest way is
the REST API with OkHttp (or `HttpURLConnection`). This is exactly what the
Pi does (`pi/gg_cloud.py`). The Firebase Android SDK also works, but it wants
a `google-services.json` or a full `FirebaseOptions` with an app ID, which
does not fit user-typed settings well.

### Sign in

```
POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=<API key>
{"email": "...", "password": "...", "returnSecureToken": true}
-> {"idToken": "...", "localId": "<uid>", "expiresIn": "3600", "refreshToken": "..."}
```

The token lasts an hour. Sign in again (or use the refresh token with
`https://securetoken.googleapis.com/v1/token?key=<API key>`) when fewer than
5 minutes are left.

### Read and write

```
GET    <db>/gg/state.json?auth=<idToken>
PUT    <db>/gg/watchers/<clientId>.json?auth=<idToken>     body = JSON
POST   <db>/gg/commands.json?auth=<idToken>                body = JSON   -> {"name": "<pushId>"}
GET    <db>/gg/results/<pushId>.json?auth=<idToken>
GET    <db>/gg/alerts.json?auth=<idToken>&orderBy="$key"&limitToLast=50
```

Add `shallow=true` to get only the keys of a big node.

### Kotlin sketch

```kotlin
class Firebase(private val url: String, private val key: String,
               private val email: String, private val password: String) {
    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()
    private var token: String? = null
    private var expires = 0L

    private fun auth(): String {
        if (token == null || System.currentTimeMillis() > expires - 300_000) {
            val body = JSONObject().put("email", email).put("password", password)
                .put("returnSecureToken", true).toString().toRequestBody(json)
            val req = Request.Builder()
                .url("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$key")
                .post(body).build()
            http.newCall(req).execute().use { r ->
                val o = JSONObject(r.body!!.string())
                if (!r.isSuccessful) throw IOException(o.optJSONObject("error")?.optString("message") ?: "sign-in failed")
                token = o.getString("idToken")
                expires = System.currentTimeMillis() + o.getString("expiresIn").toLong() * 1000
            }
        }
        return token!!
    }

    fun get(path: String, query: String = ""): String? {
        val req = Request.Builder().url("$url/$path.json?auth=${auth()}$query").get().build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string()?.takeIf { it != "null" }
        }
    }

    fun send(method: String, path: String, body: JSONObject): String? {
        val req = Request.Builder().url("$url/$path.json?auth=${auth()}")
            .method(method, body.toString().toRequestBody(json)).build()
        http.newCall(req).execute().use { r -> return r.body?.string() }
    }
}
```

For updates without polling, the REST API can also stream: send
`Accept: text/event-stream` to `GET <db>/gg/state.json?auth=...` and read
`put` / `patch` events. Polling `/gg/state` every 3 s is fine too and is
easier.

---

## 4. Data layout

Everything sits under `/gg`. Only the Pi writes, except `commands` and
`watchers`.

```
/gg_users/<uid>       "pi" or "app"                     who may use the data
/gg/pi                {online, ts, local_urls[], version, alerts_rev}  heartbeat, every 30 s (60 s when nobody watches); alerts_rev changes whenever /gg/alerts does
/gg/state             same fields as /api/state (no viewers), plus battery and ts
/gg/alerts/<id>       one history row
/gg/snaps/<id>        {jpg, w, h, bytes, thumb}         compressed snapshot; thumb is {jpg, w, h, bytes} at 160 px for lists
/gg/preview           {jpg, w, h, bytes, ts}            360 px still, only while a phone is on the live screen
/gg/watchers/<clientId>  {ts, device, os, app_version, mode, screen}   written by phones; screen is "live" or "other"
/gg/commands/<pushId>    {cmd, args, ts, by}                   written by phones
/gg/results/<pushId>     answer from the Pi, last 50 kept
/gg/usage                how much of the free monthly allowance is estimated used
```

### `/gg/state`

Fields: `connected`, `counts`, `smoothed`, `threshold`, `rearm`, `conf`,
`roi`, `latency_ms`, `avg_latency_ms`, `fps`, `cam_fps`, `alert_active`,
`muted`, `mute_left`, `mute_total`, `auto_mute`, `auto_mute_s`, `auto_muted`,
`frames`, `uptime_s`, `reconnects`, plus `battery` (same object as
`GET /api/battery`, or `null`) and `ts` (when the Pi wrote it, ms). Same
meaning as in `GarbageGuard_API.md`. `mute_left` was correct at `ts`, so
count it down yourself between updates.

### `/gg/alerts/<id>`

`id`, `timestamp`, `event`, `smoothed_count`, `raw_count`, `n_bottle`,
`n_bag`, `n_food_container`, `peak_confidence`, `threshold_used`, `read`,
`snapshot` (file name on the Pi), `has_snap`. The key is the row id as text.
Rows deleted on the Pi disappear here too. The Pi keeps the newest 200.

### `/gg/snaps/<id>`: snapshots

`jpg` is the JPEG as **base64**. Decode with
`BitmapFactory.decodeByteArray(Base64.decode(jpg, Base64.DEFAULT), ...)`.
It is the same picture as on the Pi, including the info card under the photo,
but resized to 960 px wide and compressed to at most 300 KB (Realtime
Database allows far bigger values, but every download counts against the
10 GB a month).

**When on the same Wi-Fi, always load `/snapshots/<snapshot>` from the Pi
instead.** It is sharper. A row can have `has_snap: true` while its
`snaps/<id>` is not there yet; the Pi uploads at most two pictures every
10 s, so show "Picture uploading…" and try again.

### `/gg/watchers/<clientId>`: telling the Pi you are watching

While the app is open in Firebase mode, write every **10 s**:

```json
{ "ts": {".sv": "timestamp"}, "device": "Galaxy A35", "os": "Android 15",
  "app_version": "5", "mode": "foreground" }
```

`clientId` is the same random id you send as `X-GG-Client-Id` locally.
`{".sv": "timestamp"}` makes Firebase fill in its own clock. Use
`"mode": "background"` when the app is in the background, and `DELETE` the
entry when the user leaves Firebase mode or closes the app.

Effects:

- The Pi sends `/gg/preview` and fast state updates only while at least one
  watcher was seen in the last 30 s. No watcher, no preview: this saves the
  monthly download allowance.
- The Pi screen lists the phone in its phone panel with the label
  **Firebase** (`via: "cloud"`) and a note that there is no IP or MAC.

### `/gg/commands`: changing things on the Pi

`POST` (not `PUT`) to `/gg/commands.json` so Firebase makes a unique,
time-ordered key:

```json
{ "cmd": "mute", "args": {"seconds": 900}, "ts": {".sv": "timestamp"}, "by": "Galaxy A35" }
```

| `cmd` | `args` (same body as the local endpoint) |
|---|---|
| `mute` | `{"seconds": 900}`, `{"seconds": 0}` or `{"cancel": true}` |
| `auto_mute` | `{"on": true, "seconds": 900}` |
| `settings` | `{"conf": 40, "threshold": 20}` |
| `roi` | `{"points": [[0.1, 0.45], ...]}` |
| `test_alert` | `{}` |
| `capture` | `{}` |
| `alerts_read` | `{"ids": [42]}` or `{"all": true}` |
| `alerts_delete` | `{"ids": [42]}`, `{"from": "...", "to": "..."}` or `{"all": true}` |
| `camera_reconnect` | `{}` |
| `screen_saver` | `{"mode": "dim", "timeout_s": 600, "timeout_battery_s": 60, "dim_pct": 20, "wake_on_alert": true}` (any field) |
| `screen` | `{"action": "wake"}` or `{"action": "sleep"}` |
| `auto_shutdown` | `{"on": true, "pct": 15}` or `{"cancel": true}` |
| `buzzer` | `{"on": true, "volume": "medium", "alerts": true, "countdown": true, "ui": false, "repeat": true, "repeat_s": 30, "quiet_on": false, "quiet_from": "22:00", "quiet_to": "06:00"}` (any field) |
| `vibe` | `{"on": true, "strength": "medium"}` (either field) |
| `vibe_test` | `{}` or `{"pattern": "test"}`. Shakes the motor alone. |
| `clean` | `{"mode": "off"}`, `"manual"` or `"all"`. Which snapshots also get a clean copy. |
| `buzzer_test` | `{"sound": "accumulation"}` |
| `shutdown` | `{"confirm": true}`. Powers the Pi off; the app must ask first. The result is written before the Pi goes offline, and the `manual_shutdown` history row follows within a few seconds. |

The Pi checks every 2 s, deletes the command first (so it runs at most once,
even if the Pi crashes halfway), runs it, and writes the local endpoint's
answer to `/gg/results/<pushId>`, with `cmd` and `ts` added:

```json
{ "ok": true, "muted": true, "mute_left": 899, "cmd": "mute", "ts": 1760241234567 }
```

Poll that result every second for up to 10 s, then show "Pi did not answer"
if nothing came. An unknown `cmd` gives `{"ok": false, "error": "unknown_command"}`.
`restart` is not on the list on purpose.

---

## 5. Firebase setup (one time, done by the team)

1. **Create the project** at console.firebase.google.com. Google Analytics
   is not needed. Stay on the free **Spark** plan.
2. **Build → Realtime Database → Create database.** Location: **Singapore
   (asia-southeast1)**. Start in **locked mode**.
3. **Build → Authentication → Get started → Email/Password → Enable.**
4. **Authentication → Users → Add user**, once for the Pi (for example
   `pi@garbage-guard.test`) and once per phone or team member (for example
   `app1@garbage-guard.test`). Copy each user's **UID**.
5. **Realtime Database → Data**: add a node `gg_users`, and under it one
   child per user: key = the UID, value = `"pi"` for the Pi's account and
   `"app"` for every phone account.
6. **Realtime Database → Rules**: paste the contents of
   `pi/firebase.rules.json` and press **Publish**.
7. **Project settings → General**: copy the **Web API key**. Copy the
   **database URL** from the top of the Realtime Database page.
8. On the Pi: **Network → Firebase**, fill in the URL, the key and the
   **Pi's** account, press **Test and save**. Syncing starts right away, no restart needed, and
   the dashboard's phone panel shows **Firebase synced**.
9. In the app: put the URL, the key and a **phone** account in the defaults
   (`local.properties`) and in the settings page.

What the rules allow: only accounts listed in `/gg_users` can read `/gg`.
Only the `"pi"` account can write the Pi's data. `"app"` accounts can add
commands (not change or delete them) and write their own watcher entry.
Anyone else gets "Permission denied". Removing a phone means deleting its
line in `gg_users` (or disabling the user).

---

## 6. Free plan limits (checked 2026-10-10, re-check before the defense)

Realtime Database on Spark: **100 simultaneous connections, 1 GB stored,
10 GB downloaded per month.** No per-operation limit.

Rough download use per phone in Firebase mode:

| What | Size | Use per hour |
|---|---|---|
| state every 3 s | ~1 KB | ~1.2 MB |
| preview every 3 s (only on the live screen) | ~25–40 KB | ~30–50 MB |
| one snapshot opened | up to 300 KB | — |

So the preview is the expensive part: about 250 hours a month for one phone.
Fetch `/gg/preview` only while the live screen is open, and stop the
watcher writes when the app goes to the background for long.

### `/gg/usage`: the allowance meter (new, please show this)

Firebase counts downloads exactly, but only the console can read that figure:
it needs a Google Cloud service account, which does not belong on the Pi. So
the Pi counts what it sends, multiplied by how many phones were watching, and
publishes the running total every 10 minutes (and only when it changed):

```json
{ "month": "2026-10", "down_bytes": 1288490188, "up_bytes": 420000000,
  "limit_bytes": 10737418240, "fraction": 0.12, "level": "ok",
  "by_kind": {"state": 90000000, "preview": 1100000000, "snaps": 70000000,
              "alerts": 28000000, "other": 0},
  "day": "2026-10-12", "day_preview_bytes": 41000000,
  "day_preview_limit": 314572800, "note": null, "estimate": true }
```

- `level` is `ok` under 70%, `warn` from 70%, `near` from 90%, `bad` at 100%.
  Use the same colours the Pi screen uses: green, amber, orange, red.
- `note` is a sentence to show the user when the preview has been slowed or
  stopped, and `null` the rest of the time. Show it verbatim next to the live
  view, otherwise a frozen preview looks like a bug.
- `estimate` is always true. Label the number **"about"**; it can be off by a
  third. The exact figure is in the Firebase console.
- Counters reset on the 1st (UTC). Firebase's own cycle may differ by a day
  or two.

**What the Pi does on its own, so you do not have to:**

| When | What happens |
|---|---|
| 90% of the month used | preview slows to one picture every 10 s |
| 100% used | preview stops; state, alerts and commands keep working |
| 300 MB of preview in one day | preview pauses until midnight UTC |

**What the app should do:**

1. Show a small bar on the Firebase settings page, the same as the Pi's:
   "about 1.20 GB of 10.00 GB this month", coloured by `level`.
2. When `note` is set, show it on the live screen instead of a stuck picture.
3. Stop reading `/gg/preview` the moment the live screen is closed, and
   `DELETE` the watcher entry when the app goes to the background for more
   than a minute. This is the single biggest saving, because the Pi only
   sends previews while a watcher is fresh.
4. Never poll `/gg/usage` faster than once a minute. It is tiny, but there is
   no reason to spend the allowance measuring the allowance.

**Sharing the project with Smart-bin:** use a **separate Firebase project**
for that demo. Limits are counted per project, so a second project keeps the
two demos from eating each other's 10 GB, and keeps the rules apart.

Push notifications to a closed app (FCM) are **not** built yet. They need a
server key on the Pi, which is a separate step. For now the app sees new
alerts while it is open or while a background job polls `/gg/alerts`.

---

## 7. Testing without the Pi

The mock server does not talk to Firebase. To test Firebase mode, run the
real Pi code with a test project, or fill a test database by hand in the
Firebase console using the layout above (a `gg/state` and a couple of
`gg/alerts` rows are enough to draw the screens).

## Screen saver and low-battery shutdown over Firebase

The new fields ride on `/gg/state`: `screen`, `screen_mode`, `screen_timeout_s`,
`screen_timeout_battery_s`, `screen_dim_pct`, `screen_wake_on_alert`, plus the
extra `battery` fields `auto_shutdown`, `auto_shutdown_pct`, `shutdown_state`,
`shutdown_in_s` and `shutdown_floor`. Field meanings are in `GarbageGuard_API.md`.
No rules change is needed.

Timing: a change in `screen` or `shutdown_state` is written at once, even in
standby. While `shutdown_state` is `counting` the Pi writes state every 3
seconds and polls commands every 2 seconds, so show `shutdown_in_s` as a local
countdown from the last value and send `auto_shutdown` with `{"cancel": true}`
for Cancel.

Notifications: the Realtime Database cannot wake a closed app. A phone in a
pocket only sees the countdown if the app is running a foreground service that
keeps its listener alive. Firebase Cloud Messaging with a Cloud Function would
fix that properly but needs the paid Blaze plan, so for the demo use the
foreground service and show the last Pi heartbeat (`/gg/pi/ts`) so a stale
banner is obvious.

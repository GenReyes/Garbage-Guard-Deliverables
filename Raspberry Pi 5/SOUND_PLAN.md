# Garbage-Guard: buzzer sound plan (dashboard, Wi-Fi page, boot)

For whoever works on `Raspberry Pi 5/dashboard.html`, `wifi.html` and `gg_buzzer.py`.
Based on main at commit c29bfe4.

## What happens now

| Where | What plays | Why some taps are silent |
|---|---|---|
| Dashboard | Only two sounds: `tap` (2200 Hz, 15 ms) and `switch` (1900 then 2300 Hz). Every button gets one of these two. | The listener (`pointerdown` near the end of dashboard.html) only reacts to `button, .sw, a`. The logo, the live feed screen, history rows and other clickable `span`/`div` elements make no sound. Disabled buttons are skipped silently. |
| Dashboard | Nothing at all unless **Button sounds** is on in Settings. | `buzzer_ui` defaults to `False` in `gg_buzzer.DEFAULTS`, so on a fresh Pi every tap is silent. Sounds also only play on the Pi's own screen (`KIOSK` check), never from a phone browser. That part is on purpose. |
| Wi-Fi page | Nothing. | `wifi.html` has no sound code. |
| Boot | `ready` (1700, 2000, 2300 Hz) plays once `garbageguard_live.py` has loaded the model and starts the web server. | Nothing plays at power on. The screen is dark and silent for the whole model load. |
| Server | `/api/buzzer/ui` only accepts `tap` and `switch` (`UI_SOUNDS`). | Any new sound name is rejected until it is added there. |

## Proposed sound set

The MH-FMD passive buzzer is loudest around 2 to 2.7 kHz, so everything stays in 1400 to 2800 Hz. Two rules keep the sounds easy to tell apart: pitch going **up** means forward, on, open, more or OK. Pitch going **down** means back, off, close, less or no. UI sounds stay under about 150 ms, so fast tapping never queues up.

Format is the same as `SOUNDS` in gg_buzzer.py: `name: (priority, group, [(Hz, ms), ...])`, where 0 Hz is a rest.

### Button sounds (group `ui`, follow the Button sounds switch)

| Name | Used for | Sequence |
|---|---|---|
| `tap` | Any other button (kept) | `[(2200, 15)]` |
| `switch_on` | Toggle switched on | `[(1900, 35), (2300, 45)]` |
| `switch_off` | Toggle switched off | `[(2300, 35), (1900, 45)]` |
| `choice` | Choice bars (Off/Dim/Never, Low/Med/High, History filters, date chips) | `[(2100, 25)]` |
| `step_up` | + on a stepper | `[(2500, 18)]` |
| `step_down` | − on a stepper | `[(1800, 18)]` |
| `tab` | Monitor / History / Settings tabs | `[(2000, 20), (0, 15), (2400, 25)]` |
| `open` | Opens something: fullscreen, phone list, a detail view, a dialog | `[(1800, 30), (2400, 40)]` |
| `back` | Back, Close, Cancel, No, exit fullscreen | `[(2400, 30), (1800, 40)]` |
| `confirm` | Save, Yes, Connect, Apply | `[(1900, 40), (0, 20), (2500, 80)]` |
| `danger` | Yes on Shut down / Delete / Clear | `[(2300, 80), (1600, 160)]` |
| `denied` | Tap on a disabled or locked button, or a request that failed | `[(1500, 60), (0, 40), (1500, 60)]` |
| `logo` | Tapping the Garbage-Guard logo | `[(1700, 60), (2000, 60), (2300, 60), (2700, 140)]` |
| `theme_light` | Sun side of the theme switch | `[(1900, 30), (2600, 50)]` |
| `theme_dark` | Moon side of the theme switch | `[(2600, 30), (1900, 50)]` |
| `minimize` | Minimize to the Pi desktop | `[(2600, 30), (2200, 30), (1700, 50)]` |
| `restore` | Bubble or icon brings the dashboard back | `[(1700, 30), (2200, 30), (2600, 50)]` |

### Event sounds (group `events`, follow the master Buzzer switch only)

| Name | When | Sequence |
|---|---|---|
| `boot` | Power on, before the model loads | `[(1500, 90), (1900, 90), (2300, 90), (2700, 220)]` |
| `ready` | Detection running (kept) | `[(1700, 80), (2000, 80), (2300, 160)]` |
| `wifi_ok` | Wi-Fi connected | `[(2000, 60), (0, 30), (2600, 140)]` |
| `wifi_fail` | Wi-Fi connect failed or wrong password | `[(1700, 120), (0, 40), (1400, 220)]` |
| `cam_lost` | Camera stream dropped | `[(2000, 80), (1500, 160)]` |
| `cam_back` | Camera stream came back | `[(1500, 80), (2000, 160)]` |
| `phone_join` | The mobile app connects | `[(2200, 30), (0, 20), (2600, 30)]` |
| `saved` | Server confirmed the settings were saved | `[(2300, 25), (0, 15), (2700, 40)]` |

Priorities: give the UI sounds 0, `phone_join` and `saved` 1, the Wi-Fi and camera sounds 2, and `boot` 3. Alerts (5) and shutdown sounds (5) still cut everything off.

## How to build it

### 1. gg_buzzer.py

- Add the names above to `SOUNDS`.
- Build the whitelist from the table so it can't drift out of date:
  ```python
  UI_SOUNDS = tuple(n for n, (_, g, _) in SOUNDS.items() if g == "ui")
  ```
- Keep `"switch"` as an alias for `switch_on`, so an older dashboard still works.
- Add a small command line entry so `start.sh` can play the boot sound before the main program has started:
  ```python
  if __name__ == "__main__":   # python3 gg_buzzer.py --play boot
      # load the saved settings file the same way garbageguard_live.py does,
      # honour buzzer_on and quiet hours, play the sound, wait for it, close the pin
  ```
  It has to close the PWM pin before it exits, because `garbageguard_live.py` opens the same pin a few seconds later.

### 2. Boot sound (start.sh)

At the very top of the autostart branch, before the camera and model start:
```sh
python3 gg_buzzer.py --play boot || true
```
Keep `buzzer.play("ready")` in garbageguard_live.py as it is. You then hear one chime at power on and another when detection is live.

### 3. gg_web.py

- `/api/buzzer/ui` already checks `sound in UI_SOUNDS`, so it accepts the new names once step 1 is done.
- Play the outcome on the server, where the real result is known:
  - `/api/wifi/connect`: `buzzer.play("wifi_ok")` on success, `buzzer.play("wifi_fail")` on failure.
  - `/api/settings`, `/api/buzzer`, `/api/screen_saver`: `buzzer.play("saved")` after a successful save (optional).
- `make_handler` already receives `buzzer`, so nothing new needs wiring in.

### 4. dashboard.html

Replace the two-sound guess with a `data-snd` attribute, plus sensible fallbacks:
```js
function uiSound(t){
  if(t.dataset.snd) return t.dataset.snd;
  if(t.disabled || t.getAttribute("aria-disabled") === "true") return "denied";
  if(t.matches(".sw")) return t.getAttribute("aria-checked") === "true" ? "switch_off" : "switch_on";
  if(t.matches(".step")) return Number(t.dataset.d) > 0 ? "step_up" : "step_down";
  if(t.matches(".tsw button")) return t.dataset.t === "dark" ? "theme_dark" : "theme_light";
  if(t.matches(".tabs button")) return "tab";
  if(t.matches("div.seg button, .fbar button")) return "choice";
  if(t.matches(".logo")) return "logo";
  return "tap";
}
document.addEventListener("pointerdown", e => {
  if(!KIOSK || !buzzUi) return;
  const t = e.target.closest("[data-snd], button, .sw, a, .logo");
  if(!t) return;
  fetch("/api/buzzer/ui", {method: "POST", headers: {"Content-Type": "application/json"},
    body: JSON.stringify({sound: uiSound(t)})}).catch(() => {});
}, true);
```
(The switch reads its state before the click flips it, which is why `true` maps to `switch_off`.)

Then tag the buttons the fallbacks can't guess:

| `data-snd` | Elements |
|---|---|
| `back` | `bdClose`, `fsClose`, `vClose`, `zClose`, `phnClose`, `cfmNo`, `offNo`, `pbCancel` |
| `confirm` | `pwrSave`, `cfmYes`, detection settings Save |
| `danger` | `offYes`, plus the Yes of any delete or clear dialog |
| `open` | `fsOpen`, `phn`, history rows or thumbnails that open a detail view |
| `minimize` | `miniBtn` |
| `restore` | `bub` |

Two more small changes:
- In the shared `post()` helper, play `denied` when a request comes back with `ok: false`. That way a failed Save sounds different from a good one.
- Optional: turn **Button sounds** on by default (`"buzzer_ui": True` in DEFAULTS). Otherwise most people never hear any of this.

### 5. wifi.html

wifi.html is written in older JavaScript (ES5: `var`, `function`), so the same idea is spelled the old way. The server already checks the Button sounds switch, so the page doesn't need to load it first.
```js
var KIOSK = ['localhost','127.0.0.1','[::1]'].indexOf(location.hostname) >= 0;
function snd(name){ if(!KIOSK) return;
  var x = new XMLHttpRequest(); x.open('POST','/api/buzzer/ui');
  x.setRequestHeader('Content-Type','application/json'); x.send(JSON.stringify({sound:name})); }
document.addEventListener('pointerdown', function(e){
  var t = e.target.closest('[data-snd], button, .logo'); if(!t) return;
  snd(t.getAttribute('data-snd') || (t.disabled ? 'denied' : 'tap'));
}, true);
```
Tags for wifi.html:

| `data-snd` | Elements |
|---|---|
| `back` | `iBack`, `ipBack`, `cfBack`, `pBack`, `cNo` (Change something) |
| `confirm` | `cYes` (Apply), `cfSave` (Test and save), `pGo` (connect) |
| `step_up` / `step_down` | `pPlus` / `pMinus` |
| `choice` | `mAuto`, `mMan`, `cfOn`, `cfOff` |
| `open` | `iWifi`, `iIp`, `ipSave` (Review), `hiddenBtn` (Other), `stSetup`, `stSkip`, `dNet`, `dGo` |
| `tap` | `rescan`, `cfTest`, `cfRestore`, `dFix`, `pShow` (show password) |

`wifi_ok` and `wifi_fail` come from the server (step 3), so the page itself doesn't play them.

### 6. Tests

- test_buzzer.py: every `ui` sound is 150 ms or shorter, every name in the tables above exists, and `UI_SOUNDS` matches the `ui` group.
- test_web.py: `/api/buzzer/ui` accepts each new UI name and still rejects an event name like `accumulation`.
- On the Pi, run `buzzer_bench.py` through the whole table. Retune any sound that comes out too faint at Low volume.

### 7. Mobile app

Nothing is required. If the app's Pi buzzer card has a test list, `boot`, `wifi_ok` and `logo` are good ones to add.

## Second pass: what is still missing (main at d0ccccf)

Most of the plan above is now on main: the new sounds are in `gg_buzzer.py`, both pages send them through `data-snd`, the boot chime runs from `start.sh`, and the server plays `wifi_ok`, `wifi_fail` and `saved`. A tap-by-tap check of both pages still found the gaps below.

### Why a tap can still be silent

Before treating a silent button as a bug, check these four conditions, because each one silences every UI sound on purpose:

1. **Button sounds is off.** `buzzer_ui` still defaults to `False` in `gg_buzzer.DEFAULTS`, so a fresh Pi or a reset settings file plays no tap sounds at all. This is the most likely reason the dashboard seemed to have no sound. Recommendation: change the default to `True`.
2. **The page was opened from another device.** Both pages only send sounds when they are opened on the Pi itself (`localhost`). A laptop or phone browser at `http://<pi>:8080` never triggers the buzzer, so test sounds on the Pi's own touchscreen.
3. **The master Buzzer switch is off or quiet hours are active.**
4. **The buzzer is unavailable** (the note under the Buzzer card says why).

### Dashboard buttons still using the generic `tap`

| Element | Suggested `data-snd` | Reason |
|---|---|---|
| `capture`, `fsCapture` | `shutter` (new) | Taking a picture should sound like a camera shutter. |
| `muteBtn`, `unmuteBtn`, `fsMuteBtn`, `fsUnmuteBtn` | `none` (skip) | The server already plays `muted` or `unmuted`, so a `tap` in front of it only adds noise. Have `uiSnd` return early when the name is `none`. |
| `rfBtn` (tap) | `refresh` (new) | Reconnecting the camera needs its own sound. |
| `rfBtn` (5 s hold) | `hold_tick` once per second, then `danger` when the restart starts | The hold currently gives no audible countdown, so the user cannot tell whether the hold is registering. |
| `refreshLogs`, `rbReload` | `refresh` (new) | |
| `newer` / `older` | `page_prev` / `page_next` (new) | Paging sounds like the steppers: up for older, down for newer. |
| `markAll` | `confirm` | |
| `delShown`, `pwrOff`, and the row delete button (`data-del`) | `caution` (new) | Each opens a confirm dialog. The `danger` sound stays on the dialog's Yes, so opening and confirming sound different. |
| Read/unread dot (`data-read`) | `choice` | It is a toggle, not a command. |
| `scrSleep` | `sleep` (new) | Falling and slower than `minimize`, so turning the screen off is not mistaken for minimizing. |
| `vOpen` | `open` | |

The sun and moon, tab, stepper, switch and choice-bar buttons are untagged but already get the right sound from the fallbacks in `uiSound()`. The buzzer and vibration test buttons play their own sounds.

### Wi-Fi page

| Element | Suggested `data-snd` | Reason |
|---|---|---|
| `skipTop` (Dashboard, top bar) | `open` | It matches `stSkip` and `dGo`, which already use `open`. |
| On-screen keyboard keys (`.k`) | `key` (new) | The keyboard is the busiest part of the page. A very short, high click keeps typing a password from sounding like a stream of button presses. |
| Backspace key | `key_del` (new) | |
| `Clear` key | `back` | |
| `Next field` key | `tab` | |
| IP field buttons (`.in`) | `choice` | Selecting a field is a choice, not an action. |

The keys are built as strings in JavaScript (`'<button class="k ...'`), so the attribute has to be added to that string. A delegated rule in the Wi-Fi listener, `if (t.matches('.k')) s = 'key';`, before the generic `tap` would also work.

### Event sounds that exist but are never played

`cam_lost`, `cam_back` and `phone_join` are in `SOUNDS`, but no code plays them.

- **Camera.** In `garbageguard_live.py`, the engine sets `self.connected` in several places (around lines 556, 598 and 651). Play `cam_lost` when it changes from `True` to `False` and `cam_back` when it changes back. Only play on a change, and skip the first connection after start-up, because `ready` already covers it. A small `_set_connected(value)` helper keeps this in one place.
- **Phone.** In `gg_web.note_viewer`, a new row is created when a client appears or returns after `VIEWER_TTL`. When that new row has `kind == "app"`, call the buzzer's `phone_join`. `note_viewer` has no access to the buzzer at the moment, so pass a callback in from `serve()`, the same way `power` receives `sound=buzzer.play`.

### Other events worth a sound

These are optional. Each one should play only on a change of state, never on every poll.

| Name (new) | When | Sequence | Priority |
|---|---|---|---|
| `charger_on` | UPS switches from battery to mains | `[(1800, 40), (2300, 80)]` | 2 |
| `charger_off` | UPS switches from mains to battery | `[(2300, 40), (1800, 80)]` | 2 |
| `wifi_lost` | Wi-Fi drops while Garbage-Guard is running | `[(2300, 80), (0, 30), (1700, 160)]` | 2 |
| `phone_leave` | The last app viewer times out | `[(2600, 30), (0, 20), (2200, 30)]` | 1 |

`charger_off` matters most, because it gives an audible warning before the low-battery countdown starts. `gg_power` already tracks whether the charger is plugged in for its pause logic, so the hook belongs there and uses the existing `sound` callback.

### Sequences for the new UI sounds

All of these stay inside the 1400 to 2800 Hz range and under the 150 ms UI limit that `test_buzzer.py` checks.

| Name | Sequence | Length |
|---|---|---|
| `shutter` | `[(2600, 10), (0, 25), (2000, 40)]` | 75 ms |
| `refresh` | `[(2200, 15), (0, 20), (2200, 15)]` | 50 ms |
| `page_next` | `[(2100, 15), (2400, 20)]` | 35 ms |
| `page_prev` | `[(2400, 15), (2100, 20)]` | 35 ms |
| `caution` | `[(2000, 30), (0, 20), (2000, 30)]` | 80 ms |
| `sleep` | `[(2200, 50), (1700, 90)]` | 140 ms |
| `hold_tick` | `[(2000, 20)]` | 20 ms |
| `key` | `[(2600, 8)]` | 8 ms |
| `key_del` | `[(1900, 12)]` | 12 ms |

Because `UI_SOUNDS` is built from the `ui` group, adding these rows to `SOUNDS` with group `"ui"` is enough for `/api/buzzer/ui` to accept them. `caution` and `denied` both repeat one note twice, but they differ by 500 Hz in pitch. If they still sound too alike on the real buzzer, lengthen the `caution` notes.

### Boot sound timing

`start.sh` plays `boot` only after the desktop has logged in and labwc has run its autostart, which on a Pi 5 takes roughly 20 to 40 seconds. The screen is dark and silent until then. To get a chime closer to power-on, add a small systemd one-shot service that runs early in boot:

```ini
# /etc/systemd/system/gg-boot-sound.service
[Unit]
Description=Garbage-Guard boot chime
After=local-fs.target
DefaultDependencies=no

[Service]
Type=oneshot
WorkingDirectory=/home/<user>/Garbage-Guard/Raspberry Pi 5
ExecStart=/usr/bin/timeout 5 /usr/bin/python3 gg_buzzer.py --play boot
ExecStartPost=/usr/bin/touch /run/gg-boot-sound

[Install]
WantedBy=sysinit.target
```

In `start.sh`, skip the chime when `/run/gg-boot-sound` exists, so it does not play twice. `/run` is cleared on every boot, so the next boot plays it again. `install_autostart.sh` could install and enable the unit, with `sudo`, alongside the labwc entry. `gg_buzzer.py --play` already reads the saved settings and respects the master switch and quiet hours, so the service needs no extra logic.

### Tests to add

- test_buzzer.py: the new `ui` names exist and each is 150 ms or shorter. This is already covered by the existing loop once the names are in `SOUNDS`.
- test_web.py: `/api/buzzer/ui` accepts `shutter`, `key` and `caution` and rejects `none`.
- garbageguard_live / gg_web: when the camera goes up, down and up again, it plays `cam_lost` then `cam_back` exactly once each. A second poll with the same state plays nothing.

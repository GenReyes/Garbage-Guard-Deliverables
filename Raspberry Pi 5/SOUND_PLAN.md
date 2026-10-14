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

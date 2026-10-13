# Pi buzzer and vibration plan

Status: **plan only**. Nothing here is built. It is written so that most of it
can be coded and tested without the Pi, and the hardware part is checked on
the bench afterwards.

## 1. What it is

A passive buzzer on the Pi 5 that plays a short, different sound for each kind
of event, matching what the Android app already does. A vibration motor
(driven by PWM) can share the same module later. One switch group in the
dashboard's Power card, and the same switches in the app.

The parts are an **MH-FMD buzzer module** and a **PWM vibration motor module**
(VCC, GND and IN pins, 3 to 5 V). See section 5 for what that means for the
wiring, and for the one thing to check before anything else.

A **passive** buzzer has no oscillator of its own. It makes a sound only when
the Pi sends it a square wave, and the pitch is the wave's frequency. That is
what makes distinct sounds possible; an active buzzer can only beep.

## 2. What the app does now (the sounds to match)

Measured from `Mobile App/android/app/src/main/res/raw/*.wav`. Pitches are
approximate.

| Event | App sound | Notes |
|---|---|---|
| `accumulation` (limit reached) | `gg_buzzer`: about 400 Hz buzz, three bursts, 1.05 s | The alarm. Loudest, most urgent |
| `test` | `gg_test`: 880 Hz 140 ms, then 1180 Hz 160 ms | Two-note rise |
| `auto_muted` | `gg_automute`: about 400 Hz, 0.5 s, fading | One low buzz |
| `manual` (capture) | `gg_capture`: a 1.5 kHz click, 0.12 s | Shutter |
| `muted` | `gg_mute`: falling glide, about 600 to 300 Hz, 0.2 s | |
| `unmuted` | `gg_unmute`: rising glide, about 300 to 600 Hz, 0.2 s | Mirror of mute |
| low battery shutdown | `gg_buzzer` | Same as the alarm |
| manual shutdown | `gg_mute` | Quiet |
| button tap | `gg_tap`: a 20 ms tick | |
| switch | `gg_switch`: 720 Hz then 1040 Hz, 80 ms | Two-step rise |

`suppressed`, read and delete make no sound in the app, so none here.

## 3. Sounds for the Pi

**Pitch problem.** Small passive buzzers are weak and thin below about
1 kHz and loudest around 2 to 4 kHz (check the part's data sheet for its
resonant frequency). The app's alarm is at 400 Hz, which would barely be
heard. So the Pi keeps each sound's **rhythm and shape** and moves it up into
the 1.5 to 3 kHz band. The values below are starting points, tuned by ear on
the bench.

Each sound is a list of (Hz, milliseconds); 0 Hz is a rest.

| Sound | Sequence | Priority |
|---|---|---|
| `accumulation` | 2000/250, rest 100, 2000/250, rest 100, 2000/250 | 5, alarm |
| `shutdown_low` (low battery, just before power off) | 2400/150, 1900/150, 1500/300 | 5 |
| `countdown` (low battery countdown) | 2000/200, once a minute; once a second for the last 10 s; fast repeat at the 5% floor | 4 |
| `test` | 1760/140, rest 60, 2360/160 | 3 |
| `auto_muted` | 1600/150, rest 50, 1000/300 | 3 |
| `shutdown_manual` | 2400/150, 1900/150, 1500/300 | 3 |
| `manual` (capture) | 2800/40, rest 40, 2800/40 | 2 |
| `muted` | 2400/60, 2000/60, 1600/60 | 2 |
| `unmuted` | 1600/60, 2000/60, 2400/60 | 2 |
| `ready` (detector started) | 1500/80, 2000/80, 2600/160 | 1 |
| `tap` (optional) | 2600/15 | 0 |
| `switch` (optional) | 1800/35, 2600/45 | 0 |

A higher priority sound interrupts a lower one. A lower one that arrives
while a higher one is playing is dropped, not queued, so sounds never pile up.

## 4. Rules that keep it from causing trouble

- **It must never slow or crash detection.** It runs on its own thread, drops
  sounds if it is backed up, and swallows its own errors, the same as the
  screen saver and sync code.
- **It respects Mute.** The alarm is played from the same place the app's
  notification comes from, which already does not fire while the alert is
  muted. A muted alert is silent on the Pi too.
- **No hardware is fine.** If the buzzer cannot be started, the feature says
  "unavailable" with a reason and the rest of the Pi works. The dashboard shows
  the reason, like the screen warning does.
- **Shutdown is not delayed.** The shutdown tune plays at the same time as the
  history write and Firebase send, never before them.
- **The alarm repeats until it is muted.** While the alert is raised and not
  muted, the alarm sounds again every `buzzer_repeat_s` seconds. Muting from the
  phone or the dashboard stops it, and so does the count dropping back below the
  re-arm value. Auto-mute, if armed, stops it after its own time (15 minutes
  now). Repeat can be switched off, so the alarm plays once.
- **Quiet hours.** An optional daily window, with a start and an end time, that
  silences the buzzer and the vibration motor. The phone notification and the
  dashboard banner still appear, and History still records everything. Wrapping
  past midnight (22:00 to 06:00) works. The one exception is the 5% floor
  warning, which cannot be cancelled and protects the data, so it still sounds.
  The window uses the Pi's clock. The Pi has no battery-backed clock, so with no
  internet at a field site its time can be wrong; if the clock has not been
  synchronised, quiet hours are not applied and the card says why.
- **Quiet by default for the small things.** Button sounds are off unless
  switched on. Alerts, the countdown and the shutdown tune are on.

## 5. Hardware

**First check: is the MH-FMD active or passive?** The name MH-FMD is sold for
both kinds, and the whole plan needs a **passive** one. An active one only makes
a single fixed beep, so every event would sound the same. Two quick ways to
tell, with the module not connected to the Pi's data pin:
- Look at the buzzer's top. An active buzzer is usually sealed with a sticker
  and has a tiny circuit board underneath; a passive one shows the bare green
  board underneath.
- Power it (VCC to 3.3 V, GND to GND) and touch IN to GND. An active module
  beeps continuously. A passive one stays silent or only clicks.
If it is active, order a passive module (it looks the same, and is often
listed as "passive buzzer module low level trigger").

**Buzzer module.** The module already has its own transistor, so it connects
straight to the Pi: VCC to 3.3 V or 5 V, GND to GND, IN to the PWM pin. No
extra transistor is needed. It is **low level trigger**, which means the sound
is on while IN is low. So the idle level must be high: the code holds the pin
high when silent, and a passive module still plays tones from PWM either way.
If it beeps while the Pi is booting (the pin floats before the program runs),
add a 10 k resistor from IN to 3.3 V.

**Vibration motor module.** VCC, GND, IN. It also has its own driver, so IN
connects straight to a PWM pin and no transistor or diode is needed. PWM duty
sets the strength. At 5 V it can draw around 100 mA, which the Pi's 5 V pin can
supply, but check how it behaves when the battery is low.

**Pins.** The Pi 5's PWM is on GPIO12 and GPIO13 (also 18 and 19; confirm on the
board's pinout). Two outputs, one for the buzzer and one for the motor, can use
the `pwm-2chan` overlay. Keep GPIO2 and GPIO3 free: the UPS HAT's battery
readings use I2C. Check the HAT's own pinout before choosing pins.

**Software PWM or hardware PWM.** Two ways to make the tone:
- `gpiozero` with `lgpio` (the Pi 5's GPIO library; the old `RPi.GPIO` does not
  work on a Pi 5). Easy, no setup, but the timing is software, so the pitch can
  warble while detection is using the CPU.
- Hardware PWM through the kernel's `/sys/class/pwm` after adding the overlay
  to `/boot/firmware/config.txt`. Steady pitch under load, but needs the
  overlay and permission to write the PWM files.

The code will have a small backend interface with both options and a fake one
for tests. Which is better is a bench question (section 8).

Install note: `gg-env` is a virtual environment. `lgpio` and `gpiozero` have to
be installed into it (or the environment made with system packages visible).

## 6. Pi code

- **`gg_buzzer.py`**, same style as `gg_screen.py` and `gg_power.py`.
  `Buzzer(settings, save, backend, clock)`; `play(name)`, `configure(...)`,
  `fields()`, `start()`. A player thread takes sounds from a small queue and
  applies the priority rule. Sequences are a table at the top of the file.
- **Backends:** `GpioBackend`, `SysfsPwmBackend`, `FakeBackend` for tests.
- **A vibration class** with the same shape: pulses by pattern and strength.
  Same module, second output.
- **Hooks:**
  - `Engine.on_alert` (already exists, called when the limit is reached and the
    alert is not muted) also plays `accumulation`.
  - The places in `garbageguard_live.py` that write the other history rows
    (mute, auto-mute, unmute, capture, test) play their sound beside the write,
    so a sound always matches a row in History.
  - `gg_power` plays `countdown` and `shutdown_low`; the manual shutdown plays
    `shutdown_manual`.
  - Start-up plays `ready`.
- **Settings** (saved in `gg_settings.json`): `buzzer_on` (true),
  `buzzer_volume` (low, medium, high), `buzzer_alerts` (true),
  `buzzer_countdown` (true), `buzzer_ui` (false), `buzzer_repeat` (true),
  `buzzer_repeat_s` (30, allowed 10 to 300), `quiet_on` (false),
  `quiet_from` ("22:00"), `quiet_to` ("06:00"), `vibe_on` (true),
  `vibe_strength` (low, medium, high).
  Volume is the PWM duty cycle: 50% is loudest, and lower duty is quieter.
- **State fields:** the same settings, plus `buzzer_available`, `buzzer_note`
  and `quiet_now` (true while quiet hours are silencing it).
- **API:**
  - `POST /api/buzzer` `{on, volume, alerts, countdown, ui, repeat, repeat_s,
    quiet_on, quiet_from, quiet_to}`, `POST /api/vibe` `{on, strength}`:
    settings, clamped like the others. Times must be `HH:MM`, anything else is
    ignored.
  - `POST /api/buzzer/test` `{"sound": "accumulation"}`: plays one sound so the
    person setting it up can hear it.
  - `POST /api/buzzer/ui` `{"sound": "tap"|"switch"}`: for the touchscreen's
    own button sounds. Loopback only, like `/api/screen/touch`.
- **Firebase commands:** `buzzer`, `vibe`, `buzzer_test`, so the phone can turn
  the Pi's sound off remotely. The commands table in `FIREBASE.md` gets three
  rows.

## 7. Dashboard and app

**Dashboard.** A "Sound" section in the Power card (or its own card if the
Power card gets too long): a master switch, a Low / Medium / High volume bar,
switches for Alerts, Countdown and Button sounds, a Repeat switch with its
interval, Quiet hours with a start and an end time (steppers like the other
numbers, not a pop-up clock, which is awkward on a touchscreen), the vibration
switch and strength, and **Test** buttons that play the alarm and a short vibration. A
warning line when the buzzer is unavailable. Touchscreen taps send `tap` and
`switch` to `/api/buzzer/ui` only when Button sounds is on.

**App (Brian).** A "Pi buzzer and vibration" card in Settings with the same
controls, over the local API and the Firebase commands. Labelled clearly, so it
is not confused with the existing phone sound and vibration switches, which
stay local to the phone. Hide the card if `buzzer_available` is missing from
state (older Pi). The mock server gets the fields and endpoints so the app can
be built without the Pi.

## 8. Order of work

1. **No Pi needed.** Write `gg_buzzer.py` with the fake backend and unit tests
   (priority, dropping, mute, settings, unavailable hardware), the hooks, API,
   Firebase commands, dashboard section (checked in screenshots against the
   fake Pi), mock server fields and docs. The real backends are written but
   untested.
2. **Bench, with the Pi.** Wire the buzzer. Run a ten-line sweep script to find
   the loudest frequency band and the quietest usable one. Compare software and
   hardware PWM while detection is running and pick one. Fix the pitches in the
   table.
3. **Integrate and tune.** Play every sound from the Test buttons, adjust by ear
   in the enclosure (it will sound different inside the case).
4. **Vibration motor.** Wire it with the transistor and diode. Settle its
   patterns and strengths.
5. **App side.** Brian builds the Settings card against the mock server, then
   the real Pi.

## 9. Decisions and what is still open

Decided with Gen:
- The parts are an MH-FMD buzzer module and a PWM vibration motor module with
  VCC, GND and IN pins, 3 to 5 V.
- The buzzer will be inside the case, which has a hole for the sound.
- The alarm repeats until muted from the phone or the dashboard, and the repeat
  can be switched off and its interval changed.
- Quiet hours exist, with a start and an end time the user sets.
- The touchscreen's button sounds exist but are off unless switched on.

Still open:
1. **Is the MH-FMD passive?** Test it (section 5) before ordering or wiring
   anything else.
2. **Repeat interval default.** The plan says 30 seconds. Shorter is more
   urgent; longer is less annoying.
3. **The floor warning during quiet hours.** The plan lets it sound. Say if it
   should stay silent too.
4. **Time on the Pi.** Confirm the Pi's time zone is Asia/Manila and that it
   gets the time from the internet at the place it will be used.
5. **The motor's job.** What should it do on a fixed Pi: pulse with the alarm,
   or only when the case is handled? Until then it pulses with the alarm and the
   shutdown tune.

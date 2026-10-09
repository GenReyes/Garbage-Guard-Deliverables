# Garbage-Guard — Field Reference

Everything needed to run the system at the creek. Print this or keep it on your phone.

---

## Quick facts

| Item | Value |
|---|---|
| Camera IP | `192.168.1.64` |
| Camera login | `admin` / (password stored in `~/gg/camera.txt`) |
| Pi IP on camera link | `192.168.1.101` |
| Dashboard | `http://127.0.0.1:8080` |
| Project folder | `~/gg` |
| Virtual environment | `~/gg-env` |
| Database | `~/gg/garbageguard.db` |
| Snapshots | `~/gg/snapshots/` |

---

## 1. Physical setup

1. PoE injector into mains power.
2. Injector **LAN** port → Raspberry Pi ethernet port.
3. Injector **POE** port → camera.
4. Pi power, touchscreen, keyboard.
5. Wait 60 seconds for the camera to boot before expecting anything.

An injector **adds** power to the cable. Do not use a splitter.

---

## 2. Starting the system

**Easiest:** double-click the **Garbage-Guard** icon on the desktop.

**Or from a terminal:**

```bash
~/gg/start.sh
```

The launcher checks the camera, brings up the network profile if needed,
activates the environment, opens the dashboard, and starts detection.

**Manual, if the launcher fails:**

```bash
source ~/gg-env/bin/activate
cd ~/gg
python garbageguard_live.py
```

Then in a second terminal:

```bash
chromium --no-proxy-server http://127.0.0.1:8080
```

The `--no-proxy-server` flag is required. Without it Chromium cannot reach
the dashboard.

---

## 3. Stopping the system

```bash
# Ctrl+C in the terminal running the script, then:
sudo shutdown -h now
```

Wait for the green LED to stop blinking before unplugging power.
Pulling power on a running Pi can corrupt the SD card.

---

## 4. Setting the monitored area, the ROI (do this once, on site)

The ROI is the water area. Anything detected outside it is ignored, which is
what stops the bridge wall being counted as garbage.

1. Mount and aim the camera in its final position first. The ROI depends
   entirely on the camera's exact view.
2. On the dashboard's **Monitor** tab, press **Draw**.
3. Tap the video to place points around the water. At least 3, more for an
   irregular bank. Follow the waterline.
4. **Undo last point** removes a mistake.
5. Press **Apply**.

The ROI is saved to `~/gg/gg_settings.json` and survives restarts.
**Edit** reloads the current shape for adjustment. **Clear** removes
it entirely, which means the whole frame is active again.

---

## 5. Threshold calibration (the field testing procedure)

The model's recall is about 0.70, so it detects roughly 7 of every 10 items
actually present. A displayed count of 20 does **not** mean 20 items are there.

Procedure, repeated across several sessions:

1. Note the dashboard's **Items** count.
2. Hand-count the items actually visible on the water at the same moment.
3. Record both numbers with the time and lighting conditions.
4. After several sessions, calculate the ratio of detected to actual.
5. Set the **Accumulation Limit** so the alert fires at the real-world count
   you want, not the detected count.

Record these in a table. This is the evidence for the thesis, and the
undercount was declared in advance in the progress report as a known
limitation, not a defect.

---

## 6. Settings on the dashboard

| Setting | Default | Notes |
|---|---|---|
| Detection Confidence | 40% | Tested default. Lower finds more but adds false positives. Do not run below 25% for real results. |
| Accumulation Limit | 20 | Thesis default. Alert re-arms at 15. Use 3 for bench tests. |

Press **Save** after changing either. They persist across restarts.

**Test alert** writes a test row to the log without waiting for a real
accumulation. Useful for demonstrating the alert path to the panel.

---

## 7. Verifying the counting logic

Runs in five seconds, needs no camera or model:

```bash
cd ~/gg
python3 test_counter.py
```

Expect `Ran 19 tests` and `OK`. This is the evidence for thesis test case
TC_UNIT_02 (accumulation counter logic).

---

## 8. Reading the results afterwards

**Alert history:**

```bash
cd ~/gg
sqlite3 garbageguard.db "SELECT timestamp, event, smoothed_count, n_bottle, n_bag, n_food_container FROM alerts ORDER BY id DESC LIMIT 20;"
```

**Latency, for the Performance Efficiency metric:**

```bash
sqlite3 garbageguard.db "SELECT AVG(mean_latency_ms), MIN(mean_latency_ms), MAX(max_latency_ms), COUNT(*) FROM perf_samples;"
```

The session summary also prints in the terminal when the script exits,
including the ISO 25010 band.

**Snapshots** are in `~/gg/snapshots/`, named by event and time. Every history entry saves one, with the detection boxes, the monitored area, and the count stamped on. On the **History** tab, press **View** on any row to open it, and **Full size** to fill the screen with it; tap anywhere or press **Close** to come back. **Capture** on the monitor's top edge saves one on demand, which is useful for recording field conditions during calibration.

**The live feed monitor.** The whole Live Feed card is drawn as a monitor so
the video gets as much room as possible. The top edge carries the camera
light, CAM 01 · LIVE, the camera frame rate (FPS), a one-line hint (it turns
amber and counts your points while you draw the monitored area), and the
**Capture** and fullscreen buttons. The bottom edge is the mute panel.

**Object labels.** Each box is labelled `#1`, `#2`, and so on for the objects
in view. A number stays with its object while it is tracked, and is freed
about two seconds after the object leaves so the next new object can reuse
it. The tracker's own IDs only ever count upwards for the whole run, which is
why they used to reach the hundreds. The count itself never used these
numbers, so this is a display change only.

**Fullscreen.** The square button at the top right of the monitor opens it. It gives the feed the whole screen, with a compact top bar (camera
status, latency, time) and a bottom bar (counts, accumulation status, Capture)
so nothing has to be left behind. The mute controls are there too. Exit with
the button at the top right, or the **Escape** key. The button is disabled
while you are drawing the monitored area, since the Apply and Undo controls
live on the main screen.

**Muting an alert.** The mute panel along the bottom of the monitor is always
there. Its display reads "No alert to mute" when nothing is wrong and the
Mute button is greyed out. When the limit is reached it turns amber and the button becomes
available: pick a duration and press **Mute**, or choose **Until I unmute**
for no expiry. While muted the red alert banner is hidden and no new
accumulation records or snapshots are written. The mute **lifts itself** once
the count falls below the re-arm value, so the next genuine accumulation is
never swallowed. Every mute and unmute is written to the history, which
matters for the thesis: a quiet stretch during testing is explained in the
record rather than looking like a missed detection.

**Auto-mute.** Flip the **Auto-mute** switch on the mute panel and choose a
duration in **Mute for**. The display then reads "Auto-mute armed". From then
on every new alert is still recorded, with its snapshot, and is muted straight
away; the display shows "Auto-muted" with a live countdown and an amber bar
that empties as the time runs out. **Until I unmute** keeps it muted until
someone presses **Unmute** or the count falls below the re-arm value. The
switch is saved on the Pi, so it survives a restart. Turn it off for
threshold calibration and acceptance testing, where you want to see each
alert.

**Read and unread.** Each history row has a dot: filled means unread, hollow
means read. Tap it to toggle, or press **Mark all read**. The History tab
shows the unread count, so anyone returning to the screen can see what
happened while they were away. The flag is stored on the Pi, so the
touchscreen and the mobile app agree.

**Deleting history to save card space.** The History tab has a trash button on each row, a **Delete all** button, and a From/To date range with **Delete range**. All three remove the snapshot image files as well as the database rows, so this is how you reclaim space on the SD card. Each asks for confirmation and names how many records will go. Deletion cannot be undone, so copy anything you need for the thesis before clearing a field session.

If `sqlite3` is not installed, the dashboard's own log table shows the same
alert data.

---

## 9. Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| Dashboard won't load, tab spins | Chromium proxy setting | Always launch with `chromium --no-proxy-server http://127.0.0.1:8080` |
| `ERROR: could not open the video source` | Camera unreachable | `ping 192.168.1.64`, then `sudo nmcli con up camera-link` |
| `401 Unauthorized` in terminal | Wrong camera password | Check `~/gg/camera.txt` |
| `No module named 'cv2'` | Environment not active | `source ~/gg-env/bin/activate` |
| `can't open file` | Wrong folder | `cd ~/gg` first |
| Video lags behind reality | Camera frame rate too high | Set sub-stream to 15 fps in the camera's Video/Audio page |
| Nothing detected | Confidence too high, or view unlike training data | Model expects trash on water seen from above. Indoor tests are not representative. |
| Bridge/bank counted as garbage | No ROI set | Draw the ROI over the water only |

**Camera web interface:** `http://192.168.1.64` in a browser on the Pi.
Use `Ctrl+Shift+R` if the page loads blank.

---

## 10. Known limitations to state, not hide

These were declared in advance in the progress report:

- **Undercounting.** Recall is about 0.70, so the live count reads low.
  Handled by threshold calibration, above.
- **`food_container` is the weakest class** at about 0.53 recall. The
  primary target if a v6 model is ever trained.
- **Static false positives** persist across every frame with a stable track
  ID, so smoothing cannot remove them. Only the ROI does.
- **Phone access needs a network.** The touchscreen works with no network at
  all, but a phone browser needs the Pi and phone on the same router or
  switch. A router with client isolation will block this.

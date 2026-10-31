# Garbage-Guard Field Kit

Print this, bring it to the creek, and fill it in by hand. Everything here feeds Chapter IV. Take a phone photo of each filled page at the end of the day.

## Before each field day

- [ ] Charger, extension lead and Wi-Fi repeater packed
- [ ] Camera clock set (camera settings, System, Time Settings, Sync with computer time)
- [ ] Settings checked and written down: model ____, detection size ____, stream ____, threshold ____
- [ ] Monitored area drawn tightly around the water, floating barrier and banks outside it
- [ ] Remote screen sharing turned off (it slows the Pi down and spoils the latency numbers)

## At the end of each field day

Run on the Pi, then copy both files to a PC:

```
cp ~/gg/garbageguard.db ~/gg_backup_$(date +%F).db
tar czf ~/snapshots_$(date +%F).tgz -C ~/gg snapshots
```

Do not use Delete in the History tab during the field week.

---

## Sheet 1: Uptime Log (Table 14)

| Date | Start time | Stop time | Hours | Model / size | Threshold | Weather and light | Crash, freeze or overheating? What happened? |
|---|---|---|---|---|---|---|---|
| | | | | | | | |
| | | | | | | | |
| | | | | | | | |
| | | | | | | | |
| | | | | | | | |
| | | | | | | | |
| | | | | | | | |

## Sheet 2: Threshold Calibration Record (Table 15)

Count the items inside the monitored area by eye, then read the count on the dashboard at the same moment.

| Date and time | Hand count | Dashboard count | Difference | Threshold before | Threshold after |
|---|---|---|---|---|---|
| | | | | | |
| | | | | | |
| | | | | | |
| | | | | | |
| | | | | | |
| | | | | | |
| | | | | | |
| | | | | | |

## Sheet 3: Test Case Trials (Tables 12, 13 and 16)

Do each test 10 times. Tick a box for a pass, cross it for a fail, and write what happened on a fail.

| Test | What to do | What counts as a pass | Trials 1 to 10 | Passed / 10 | Notes |
|---|---|---|---|---|---|
| TC_UNIT_02 | On the Pi: `python3 test_counter.py`, plus one simulated count up to the threshold | Alert once at the threshold, no second alert until the count falls to the re-arm level | ☐☐☐☐☐☐☐☐☐☐ | | |
| TC_UNIT_03 | Play a recorded video through the Pi | Latency is logged and nothing crashes | ☐☐☐☐☐☐☐☐☐☐ | | |
| TC_SYS_01 | Watch the live feed with real floating waste | Boxes on visible bottles, bags and containers; nothing on sunken items or leaves | ☐☐☐☐☐☐☐☐☐☐ | | |
| TC_SYS_02 | Let items build up past the threshold | Accumulation Alert is raised | ☐☐☐☐☐☐☐☐☐☐ | | |
| TC_SYS_03 | Run for a set time (write it down) | Latency stays steady, no crash or overheating | ☐☐☐☐☐☐☐☐☐☐ | | |
| TC_SYS_04 | Full run: switch on, feed appears, add items, count rises, alert, record saved, app notified | Every step happens in order (take a photo of each step once) | ☐☐☐☐☐☐☐☐☐☐ | | |
| TC_SYS_05 | Open the Monitor tab while detecting | Live boxes, labels, count and frame rate keep updating | ☐☐☐☐☐☐☐☐☐☐ | | |
| TC_SYS_06 | Put the same item inside, then outside, the area | Counted inside, ignored outside | ☐☐☐☐☐☐☐☐☐☐ | | |
| TC_SYS_07 | Let one item drift in and out of the area | Same ID number the whole time; count goes up one, then back down | ☐☐☐☐☐☐☐☐☐☐ | | |
| TC_SYS_08 | Raise an alert, take a Capture, open History | Both records there with time, counts and snapshot | ☐☐☐☐☐☐☐☐☐☐ | | |
| TC_SYS_09 | Raise an alert with the phone app connected | Dashboard shows it, buzzer sounds, phone gets a notification | ☐☐☐☐☐☐☐☐☐☐ | | |
| TC_SYS_10 | Change the threshold in Settings, repeat the count | Alert comes at the new value | ☐☐☐☐☐☐☐☐☐☐ | | |
| TC_ACC_01 | Count correct labels against all items seen, on several snapshots | Write correct / total; the target is 80% | Correct ____ / Total ____ | ____% | |
| TC_ACC_02 | Count alerts raised against threshold crossings you saw | The target is 80% | Raised ____ / Crossings ____ | ____% | |
| TC_ACC_03 | Read the latency records afterwards (done on the PC) | Average under 1.5 s, no crash | – | – | |

## Sheet 4: Usability Survey (Table 17)

For barangay personnel, after they have used the touchscreen and the phone app. One sheet per person.

Name (optional): ______________________ Position: ______________________ Date: __________

Encircle one number for each statement: 5 Strongly Agree, 4 Agree, 3 Neutral, 2 Disagree, 1 Strongly Disagree.

| Code | Statement | Rating |
|---|---|---|
| USR-U1 | Information displayed on the monitor is easy to read and understand. | 5  4  3  2  1 |
| USR-U2 | Visual bounding boxes on the camera feed clearly showed where the garbage is located on the creek. | 5  4  3  2  1 |
| USR-U3 | The Accumulation Alert is clear, noticeable and effectively informs when the creek needs cleaning. | 5  4  3  2  1 |
| USR-U4 | The system provides helpful insights that would assist local authorities in monitoring creek pollution. | 5  4  3  2  1 |
| USR-U5 | The Android application shows the alerts and the history clearly, and what it shows matches the touchscreen. | 5  4  3  2  1 |

Comments or suggestions:

______________________________________________________________________________

______________________________________________________________________________

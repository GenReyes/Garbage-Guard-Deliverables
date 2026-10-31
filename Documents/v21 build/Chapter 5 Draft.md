# Chapter V Draft: Conclusions

Draft for `Garbage-Guard v20.docx` and later. Written as if the study were finished. Every place that waits on a Chapter IV result still missing in v20 is marked **[CONFIRM: ...]** so it can be found with Ctrl+F. Nothing in those brackets was invented; each one names the table or test case the number must come from.

Notes for whoever inserts it:

- Headings: `V. CONCLUSIONS` (Heading 1), then `5.1 Summary of Findings`, `5.2 Conclusions`, `5.3 Recommendations` (Heading 2). The v20 table of contents lists only 5.1 Conclusion and 5.2 Recommendations, so the TOC needs the extra line. The three-part split follows HelmetGuard; the content covers every item the CIT template asks of Chapter V (what the study is about, findings against the objectives and the research gap, insights, limitations, future research, and comments from end users and the advisory committee).
- No new figures, tables or equations. If any are added, they continue after Chapter IV's last ones (Figure 58, Table 17 in v20).
- Only sources already in VIII. REFERENCES are cited.
- Model: Chapter IV reports v7 on the frozen test set, while the October 31 field session ran v8. The draft follows Chapter IV and flags the field model where it matters.

---

## V. CONCLUSIONS

This chapter closes the study. It summarizes the findings reported in Chapter IV, states the conclusions drawn from them for each specific objective, and gives recommendations for future researchers and for the offices that may adopt the system.

### 5.1 Summary of Findings

The study set out to develop a computer vision system that detects floating garbage in creeks with a convolutional neural network and YOLO26n, counts how much of it has built up in a monitored stretch of water, and alerts the people responsible once the count reaches a set level. The result is Garbage-Guard, a fixed monitoring unit built around a Raspberry Pi 5 and a Hikvision HiWatch HWI-B140H camera, with a touchscreen dashboard on the unit and an Android application for remote use. It was operated at a creek in Barangay 870, Pandacan, Manila. The following are the main findings.

1. Nine training runs were completed (v1, v3, v4, v5, v6, v6b, v7, v8 and v9) [CONFIRM: drop v9 here if it was not finished, and say "eight"]. Public datasets alone stopped improving the model after v3, and the score on all three classes rose only once the researchers added their own creek photographs in v5 (mAP@0.5 from 0.687 to 0.761). Two runs were found to be unreliable during the process: v6 had silently dropped every styrofoam image, and rebuilding the dataset for v6b moved some test images into training. The test partition was therefore frozen at 20% of the data from v7 onward.

2. On the frozen test set of 807 images, the reported model (v7) reached a precision of 85.2%, a recall of 68.5% and a mAP@0.5 of 0.745. Its precision falls in the Good band of Table 3 and meets the 80% target set in Section 1.3. On the 180 creek images within that set, precision dropped to 74.2% (Fair) and mAP@0.5 to 0.593. Food containers were the strongest class (creek mAP@0.5 of 0.871) and bags the weakest (creek recall of 0.217 on 20 test images). Recall was lower than precision in every class. [CONFIRM: if v8 or v9 becomes the reported model, replace these figures with its row in Table 11.]

3. The prototype detects, tracks and counts items only inside the monitored area drawn by the operator. The accumulation counter passed all 19 automated tests, raising the Accumulation Alert once when the smoothed count reached the threshold and not again until the count fell to the re-arm level. In the system tests, [CONFIRM: number of TC_SYS cases passed] of the ten test cases in Table 13 met their expected result [CONFIRM: name any case that failed]. During the field session the floating barrier across the creek was at first detected as an item, and redrawing the monitored area to exclude it removed those false detections.

4. In the field, the unit processed each frame in about 309 ms (0.31 s) with v8 at a detection size of 960 pixels, which is in the Excellent band of Table 3 (below 0.5 s). Bench tests on the 1280 x 720 main stream gave a median of 186 to 191 ms at about five frames per second, with the processor at 68 °C and no throttling. The picture on the touchscreen ran about one second behind the scene because of the camera's own encoding and network delay, which the latency measure does not include. A 1920 x 1080 main stream was too heavy to decode and fell six to seven seconds behind. [CONFIRM: mean, median and maximum latency from the latency records for the whole deployment, Section 4.4.]

5. The unit was operated at the creek for [CONFIRM: number of days and total hours, Table 14] in daylight. It ran [CONFIRM: without a crash or overheating / with the stops listed in Table 14]. The threshold was calibrated on site against hand counts and settled at [CONFIRM: final threshold, Table 15] items, lower than the default of 20, because of the size of the monitored stretch.

6. In the developer-side acceptance test, the system rated [CONFIRM: DEV-FS1 and DEV-FS2 results] for Functional Suitability and [CONFIRM: DEV-PE1 and DEV-PE2 results] for Performance Efficiency (Table 16). Live classification in the field reached [CONFIRM: TC_ACC_01 percentage and band], and the Accumulation Alert was raised in [CONFIRM: TC_ACC_02 successful alerts out of trials] trials.

7. [CONFIRM: number] end users [CONFIRM: who they were, as reported in Section 4.5] answered the usability survey after using the system at the creek. They gave a weighted mean of [CONFIRM: mean of all five statements] across the five usability statements, interpreted as [CONFIRM: band] under Table 9. The highest-rated statement was [CONFIRM: indicator and mean] and the lowest was [CONFIRM: indicator and mean].

### 5.2 Conclusions

Derived from the findings in relation to the objectives of the study, the researchers reached the following conclusions.

1. The first objective was met on the frozen test set. YOLO26n, exported to NCNN, detects bottles, bags and food containers with a precision of 85.2%, above the 80% target. The creek-only result of 74.2% shows that the model is weaker under field conditions than its headline figure suggests, the same kind of gap Nunkhaw et al. (2025) reported for floating waste in urban canals. The training history also shows what moved the result: images taken at the deployment site improved the model more than any amount of public data, which agrees with the point raised by Jia et al. (2023) that floating-litter detection suffers from a lack of data that matches real waterways.

2. The second objective was met. A Raspberry Pi 5 can run detection, ByteTrack tracking, area-limited counting, smoothing, alerting, event storage, the dashboard and the cloud link on one board, from a battery, without a separate computer. The monitored area does more than save processing time. It is also the operator's tool for keeping fixed objects such as the floating barrier out of the count, so drawing it carefully is part of installing the unit, not an afterthought.

3. Because recall is lower than precision, the system is more likely to count too few items than too many. In practice this means an alert will tend to come late rather than early, and false alarms are rare. The researchers concluded that the threshold should be set from an on-site hand count rather than left at the default, which is what the field calibration did when it settled at [CONFIRM: final threshold] items.

4. The third objective was met for Performance Efficiency. At about 0.2 to 0.3 seconds per frame the Raspberry Pi 5 is well inside the Excellent band, and floating waste moves slowly enough that five frames per second, smoothed over 15 frames, is sufficient. Li et al. (2024) reached 33 frames per second with a very small YOLO model on an edge device, but for a counting task on slow water the extra speed would not change the alert. Functional Suitability in the field was [CONFIRM: TC_ACC_01 and TC_ACC_02 bands], and Usability was rated [CONFIRM: survey band] by the end users. [CONFIRM: if any of these fell below the target, say so here and name the cause.]

5. The research gap set out in Section 2.4 and Table 2 was addressed. None of the systems reviewed combined a detector trained by the operator, running at the edge at creek level, with counting limited to a drawn area of water, one identity per item across frames, and a threshold alert backed by a saved snapshot. Garbage-Guard puts these together in one unit and was shown to work at a real creek under a barangay permit.

Several lessons came out of the work that may help later researchers. A test set has to be fixed once and never rebuilt, or the scores of later runs cannot be compared; this was the adviser's requirement from v7 onward, and the v6 and v6b problems showed why. Detecting small items far from the camera depended more on how the picture reached the model than on the model itself: cropping the full-resolution main stream to the monitored area kept bottles and bags large enough to detect, while a higher camera resolution only made the Raspberry Pi fall behind. A labelled snapshot with a clean copy beside it turned out to be useful twice, first as evidence for each alert and then as new training data.

The prototype has limits that the results make plain. It works in daylight only and covers one stretch of creek from one camera at about 5 to 6 m. Bags at the creek are rarely detected, so piles made mostly of plastic bags will be undercounted. The count says how many items are present, not how much space they cover, so a few large items and many small ones can give the same reading. The enclosure lost its weather rating once it was cut for the screen, fans and cables, so the unit must stand under cover with only the camera outside. The two USB case fans draw power from the extension cable, so they stop when mains power fails and the unit runs on its battery. The built-in Wi-Fi of the Raspberry Pi 5 could not join a network during testing, so internet access depends on a cabled Wi-Fi extender, and the camera's clock resets whenever it loses power.

The end users who took part in the survey [CONFIRM: summarize their written comments and suggestions, if the survey had an open-ended item; otherwise remove this sentence]. The advisory committee [CONFIRM: comments and suggestions from Dr. Jonel R. Macalisang and the panel during the final defense].

Garbage-Guard is therefore a workable early-warning tool for barangays and LGUs that must decide when a stretch of creek needs clearing. It replaces a periodic walk along the bank with a continuous count and a dated record of every build-up. In doing so it supports UN Sustainable Development Goals 6, 9 and 11 (United Nations, 2025).

### 5.3 Recommendations

As an improvement to the limitations identified in this study, the researchers recommend the following to future researchers and to the offices that may adopt the system.

1. **Collect more creek images of bags.** Bags were the weakest class at the creek, with a recall of 0.217 measured on only 20 test images. Future researchers should photograph and label plastic bags in the water at the deployment site, in different states of floating and sinking, and enlarge the creek portion of the test set so that each class is judged on more than a few dozen images.

2. **Retrain from the unit's own snapshots.** The clean copies saved with every alert and capture are already in the form needed for labelling. A future version should make this a routine: label the clean copies, retrain on them, and test on the frozen set before replacing the model on the unit, following the retrain loop in the V-Model.

3. **Extend operation to night and poor light.** Night operation was left out of this study by design. Future work should test an infrared camera or a camera with better low-light sensitivity, add night images to the training data, and report day and night results separately.

4. **Cover a wider area and longer distances.** One camera at about 5 to 6 m watches a single stretch. Future work may use more than one camera per unit, or a longer lens such as the 6 mm version of the same HiWatch camera, which gives more pixels per item at the same distance. A motorized varifocal camera would let the operator zoom from the dashboard, at the cost of a stronger power supply for the camera.

5. **Estimate the amount of garbage, not only the number of items.** A count treats a bottle cap and a large container alike. Instance segmentation, compared with bounding boxes by Kataoka et al. (2024), could estimate the area of water covered by waste, which says more about the risk of a blockage than the number of pieces.

6. **Weatherproof the enclosure and keep it cool on battery.** The next enclosure should restore a weather rating with sealed glands for the cables, a gasket around the screen and covered fan openings. The case fans should run from the UPS output, or be replaced by fans that do, so that cooling continues during a power cut.

7. **Make the network independent of the site.** Since the built-in Wi-Fi failed during testing, a future unit should carry its own connection, such as a USB LTE modem, so it can be placed where no Wi-Fi exists. The camera clock should also be synchronized from the Raspberry Pi so the date in the video is always correct.

8. **Measure the full delay from scene to alert.** The latency in this study covers detection and tracking only. Future work should also measure the camera's encoding and network delay, about one second in this study, so the time from an item appearing in the water to the alert reaching the phone is known end to end.

9. **Deploy longer, at more sites and in the rainy season.** The field test ran for [CONFIRM: number of days] in daylight at one creek. A deployment of several weeks across the dry and rainy seasons, at more than one creek, would show how the model handles high water, debris after storms and changing light.

10. **Combine the count with water-level sensing.** Flood monitoring systems such as those of Te et al. (2024) and Arante et al. (2025) alert on sensor readings. Pairing the garbage count with a water-level sensor at the same point could give barangays an earlier flood warning, since a blocked creek and a rising water level together point to a flood risk that neither shows alone.

11. **For barangays and LGUs.** Units should be placed upstream of culverts, bridges and drainage outfalls, where floating waste is most likely to collect and cause a blockage. The threshold at each site should be set from a hand count during installation and reviewed after the first few alerts, and the alert history should be used to plan cleanup schedules.

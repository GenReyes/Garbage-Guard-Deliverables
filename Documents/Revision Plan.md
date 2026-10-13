# Garbage-Guard Thesis Revision Plan

Source: Prof. Jonel's comments (as noted by Brian, 2026-10-10), mapped against the current Google Doc.

Current state of the doc: Chapters I to III exist. Chapter III has the V-Model with three test phases (Unit, System, Acceptance) and Tables 3 to 5 with Objective, Procedure and Expected Result only. There is no Chapter IV or V yet.

---

## 1. Objectives (Section 1.2)

| # | Task | Where |
|---|------|-------|
| 1.1 | Tie Specific Objective 1 to **Functional Suitability** explicitly. Each objective should name the ISO/IEC 25010:2023 characteristic and the test case that proves it. | 1.2 Objectives |
| 1.2 | Add a **block diagram of how the AI was trained**: data collection at the creek, annotation, 80/20 train/test split, YOLOv26n training, export to NCNN, deployment on the Pi 5. | New figure in 3.2 Block Diagram (or 3.4, Prototyping & Development) |
| 1.3 | Add **UI screenshots** that follow the process flow (live feed with bounding boxes and ROI, accumulation counter, alert, history log, Android app). | 3.3 Flowchart, then reused in Ch. IV |
| 1.4 | Make sure Specific Objective 2 mentions the parts that now exist: ByteTrack, ROI-based accumulation counting, the touchscreen dashboard and the Android app. | 1.2 Objectives, 1.3 Scope |

## 2. Chapter III: Methodology

### 2a. Testing parameters and process flow

| # | Task | Where |
|---|------|-------|
| 2.1 | Write a **Testing Parameters** subsection: what is measured (precision, recall, mAP@0.5, latency, uptime, alert correctness), how it is computed, and the pass mark (80%). | New subsection before 3.4 Validation Phase |
| 2.2 | Add a **System Flow test** that walks the flowchart end to end (camera on, detect, track, count inside ROI, save to SQLite, threshold alert, shown on dashboard and app). | Validation Phase, System Testing |
| 2.3 | Define the **trial count** for every test (for example, 10 trials per case) so Ch. IV can report "x out of 10". | Tables 3 to 5 |

### 2b. Usability and Performance

| # | Task | Where |
|---|------|-------|
| 2.4 | Plan a **field deployment at the creek for 1 week, or at least 4 to 5 days**. | 3.4 Acceptance Testing |
| 2.5 | Record **how many days/hours the system stayed running** without crash or overheating. This is the evidence for DEV-PE2 and the usability survey. | Table 6, plus a new uptime log table |
| 2.6 | Have the end-user respondents (Table 7, USR-U1 to U4) answer the survey after using the system during that deployment. | Table 7 |

### 2c. Test cases (Tables 3 to 5)

Prof's point: the tables only test the AI. Add test cases for the **system itself**. He said the Chapter III test cases are otherwise good enough.

| # | Task | Where |
|---|------|-------|
| 2.7 | Add columns **Actual Result** and **Pass/Fail** (or "Successful trials / 10") to every table. Leave them empty in Ch. III; fill them in Ch. IV. | Tables 3, 4, 5 |
| 2.8 | Rewrite **TC_UNIT_01**: test on a held-out **20% of the annotated images** that were never used in training, instead of a fixed "500 images". Report per-class and overall detection rate in percent. | Table 3 |
| 2.9 | Pass mark for TC_UNIT_01: **precision and accuracy of at least 80%**. Since this is object detection, report precision, recall and mAP@0.5 as well; "accuracy" alone is not standard for YOLO. | Table 3, Table 1 or 2 |
| 2.10 | Group the test cases under four headings Prof listed: **Evaluation, Classification, Accuracy, Overall**. | Tables 3 to 5 |
| 2.11 | Move or reframe TC_UNIT_02 and TC_UNIT_03 (see Section 6 below). | Tables 3 and 4 |
| 2.12 | New system test cases to add: dashboard shows live feed and counts; ROI excludes objects outside the zone; ByteTrack does not double count the same item; event saved to SQLite; alert shows on dashboard and Android app; threshold change in settings takes effect. | Table 4 |

## 3. Chapter IV: Results and Discussion (new)

| # | Task |
|---|------|
| 3.1 | **Test case results per Specific Objective**: one subsection per objective, each with its filled-in table and a short discussion. |
| 3.2 | **Explain how the GUI was made**: tools used (Flask/Python web server on the Pi, HTML dashboard, Kotlin/Jetpack Compose app), screenshots, and how each screen maps to a requirement. |
| 3.3 | **System Flow test case results**: the end-to-end run from 2.2, with screenshots of each step. |
| 3.4 | Uptime results from the creek deployment, latency averages against the Table 1 scale, and survey results for Table 7. |

## 4. Chapter V: Conclusion and Recommendations (new)

Recommendations should cover what the current system does not have:

| # | Recommendation |
|---|----------------|
| 4.1 | **Scalable multi-camera setup**, so one unit or server can monitor several creek points. |
| 4.2 | **Wireless camera** to remove the cable run between camera and Pi. |
| 4.3 | A **longer detection range**, for when the distance from camera to water must be greater (higher resolution or zoom camera, larger input size, or a model trained on far-away samples). |

## 5. Extras for the defense

| # | Task |
|---|------|
| 5.1 | Make a **Testing folder** for every test (one folder per test case ID: raw logs, screenshots, result sheet, video). |
| 5.2 | Record an **actual testing video**: setting up the camera, starting the system, then a real creek run with detection and the alert. |

## 6. Can TC_UNIT_02 and TC_UNIT_03 move to other V-Model phases?

Yes, and it matches what Prof said ("UNIT_02 is System testing").

- **TC_UNIT_02 (accumulation counter + GUI)** needs the camera, detector, tracker, counter, database and dashboard working together, so it is a **System test**. Merge it with TC_SYS_02.
- **TC_UNIT_03 (latency / overall)** measures the whole pipeline on the Pi 5, so it belongs in **System testing** (merge with TC_SYS_03). The "overall" part also feeds **Acceptance** (TC_ACC_03).
- Keep in **Unit testing**: TC_UNIT_01 (model on the 20% test set), plus small checks that really are single units, for example the counter logic fed with simulated detections, and the ROI filter on a still image. These are cheap and keep the Unit table from having only one row.

It does not remove work, since every case still needs results, but each test now sits in the phase that matches what it actually exercises, which is easier to defend. Confirm the regrouping with Prof Jonel, since he listed them under UNIT.

## 7. Other fixes spotted in the doc

- "II. LITERATURE REVIEW" appears twice; merge into one.
- Numbering is off: References is "IV" before Chapter IV exists, and Appendices and Bionote are both "V" in the TOC. Renumber after adding Chapters IV and V.
- No Recommendations section exists yet (covered by Section 4 above).

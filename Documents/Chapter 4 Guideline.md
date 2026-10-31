# Chapter 4 Guideline: Results and Discussion

This is a guide for writing Chapter IV of the Garbage-Guard manuscript. You built most of the system, so the content is yours to decide. What follows is what the chapter has to answer, what has to stay consistent with Chapters I to III, and some notes on where the raw data already sits.

Read it against `Documents/Garbage-Guard v21.docx` (the newest manuscript). It keeps the Chapter IV draft from v20 with every figure renumbered, and adds a first-pass Chapter V.

## What the chapter must do

The CIT Capstone Study Guidelines give Chapter IV two jobs. The first (CIT 4.1, Description and Structure) is to show the finished system with labelled photos and explain its parts, which answers the "design" and "develop" objectives. The second (CIT 4.2, Test/Analysis Results) is to report the test results against the procedures in Chapter III, present them in tables and figures, interpret them with statistics, and compare them with other studies, published systems or standards.

That's the whole requirement. CIT does not dictate the number of subsections or their names, and Chapters I and II already depart from the template where it made sense. Chapter IV can do the same.

In practice the chapter answers three questions, one per specific objective in Section 1.2:

1. How well does the YOLO26n model detect bottles, bags and food containers (precision, recall, mAP@0.5 on the held-out test set)?
2. Does the Raspberry Pi 5 prototype detect inside the drawn region, keep one identity per item, count correctly, and raise the alert on the dashboard and the app at the threshold?
3. How does the system score on Functional Suitability, Performance Efficiency and Usability (ISO/IEC 25010:2023)?

If a reader finishes Chapter IV and can't find a direct answer to each of these, something is missing.

## The current outline (yours to change)

The manuscript already lists these headings in the table of contents:

| Current heading | Placeholder says | CIT part |
|---|---|---|
| 4.1 Project Description | The finished prototype, where it was installed, what it does | 4.1 |
| 4.2 Project Structure | Dashboard and app, tools used, screenshots mapped to requirements | 4.1 |
| 4.3 Project Capabilities and Limitations | What works reliably and what doesn't, including the weak bag class and the fans stopping on battery | 4.1 / 4.2 |
| 4.4 Test Case Results | One part per objective, Tables 4 to 6 filled in | 4.2 |
| 4.5 System Flow Test Results | End-to-end run of the flowchart with a screenshot per step | 4.2 |
| 4.6 Field Deployment Results | Uptime, latency against Table 3, threshold calibration record | 4.2 |
| 4.7 Evaluation Results | Table 7 developer indicators and Table 8 survey results | 4.2 |

This outline is close to HelmetGuard's (Project Description, Project Structure, Capabilities and Limitations, Testing Results, Evaluation Results). You can merge, split, rename or reorder these. Two examples of reasonable changes: folding 4.5 into 4.4 since TC_SYS_04 already is the system flow test, or moving 4.3 to the end so the limitations come after the numbers that prove them. Whatever you settle on, tell Brian so the table of contents and Chapter V get updated.

## What is your call

- **How the structure section is organised.** By hardware then software, by data flow (camera, Pi, dashboard, cloud, app), or by subsystem. You know which order explains it best.
- **How deep the technical explanation goes.** CIT's sample walks through each component and the languages and frameworks used. You can go further into the detection loop (main-stream crop, 960 px inference, ByteTrack, smoothing, re-arm at 15) if it helps the reader understand a result.
- **Which training runs to show.** The deployed model, v7, came out of the eighth training run. You can show the history as a table or a chart, or report v7 only. If you show older runs, say that their scores are not directly comparable because the test set changed (this is noted in `models/README.md`).
- **Which charts to include.** HelmetGuard uses the training loss curves, precision and recall curves, mAP curves and a confusion matrix (their Figures 58 to 68). The Ultralytics output for v7 has all of these. Pick the ones that support a point you make in the text, not all of them by default.
- **Extra breakdowns.** PICTRETION tests accuracy at varying distances. A similar breakdown for Garbage-Guard (time of day, lighting, distance from the camera, how crowded the water is) would strengthen the chapter if you have the data. It isn't required.
- **Statistics beyond the formulas in Chapter III.** CIT asks for results to be "interpreted using various statistical techniques". Means and percentages against the Table 3 scale are the minimum. A per-class comparison, a confidence interval, or a simple comparison between lab and creek results would all count. Don't add a hypothesis test (ANOVA, t-test) unless Chapter III is updated to declare it first, because Chapter IV can only report tests that Chapter III said would be run.
- **Screenshots.** Appendix G already holds 21 interface screenshots (Figures G1 to G21). Section 4.2 can show a smaller selection in the main text and point to Appendix G for the rest, or rely on Appendix G entirely. Note that Brian is currently updating the region-of-interest Draw buttons in fullscreen mode, so some screens will change.
- **How you word the limitations.** Be honest and specific. The panel will respect "bag recall at the creek was 0.217 on 20 test images" more than "some classes need improvement".

## What must match the rest of the paper

**Headings.** Chapter heading is `IV. RESULTS AND DISCUSSION` (Heading 1). Sections are `4.1`, `4.2` and so on (Heading 2). Anything below that uses unnumbered bold headings, the way Chapter III uses "Phase 1: Verification" and "Unit Testing".

**Figure and table numbers.** From v21 the main text of Chapter III ends at Figure 48 and Table 9. Figure 27 (in 3.1) is the dimensioned enclosure drawing with its three cable entries, Figure 42 the unmodified junction box, Figure 43 the Mitsushi power extension, Figure 44 the USB case fan, Figure 45 the V-Model and Scrum hybrid, Figure 46 the training pipeline, and Figures 47 and 48 the team's own Roboflow screenshots. Chapter IV now starts at **Figure 49** (the assembled unit) and runs to Figure 61; tables still start at **Table 10**. Your draft's numbers were shifted by three in v21, so number anything new from Figure 62.

**Prototype photos.** The three photos of the finished prototype (closed unit with the touchscreen, side with fan and cable entries, opened enclosure) were taken out of Chapter III and belong in 4.1. They are in `Documents/Figures/Components/` as `Prototype Front.png`, `Prototype Side.png` and `Prototype Internal.png`. Appendix items keep their own letters (Figure G1, Listing H1), so don't renumber those.

**Caption format.** Copy what Chapters II and III do: a bold line `Figure 49`, then the title on the next line in italics, then the figure. A source goes underneath as `Note.` followed by the citation. Tables use the same pattern with `Table 10` above the table. Refer to every figure and table in the text before it appears ("Figure 49 shows..."), and put the figure before its explanation where possible, which is Brian's layout ruling.

**Equations.** The Chapter III formulas are numbered (1) to (5) on the right: Precision (1), Recall (2), Accuracy (3), Success Rate (4), Average Latency (5). Refer to them by number ("Equation 1"). If you add a formula, write it in Word's equation editor (Cambria Math) and number it on the right starting at (6).

**The numbers in Chapter III.** Chapter IV reports against what Chapter III promised, so these must line up:

- Table 3 scales: Excellent 95 to 100%, Very Good 90 to 94.99%, Good 80 to 89.99%, Fair 70 to 79.99%, Poor below 70%. Latency: Excellent below 0.5 s, Very Good 0.51 to 1.5 s, Good 1.51 to 3.0 s, Fair 3.1 to 5.0 s, Poor above 5.0 s.
- The precision target in Section 1.3 is at least 80%.
- The alert threshold is 20 items by default, re-arming at 15.
- Test case IDs: TC_UNIT_01 to 03 (Table 4), TC_SYS_01 to 10 (Table 5), TC_ACC_01 to 03 (Table 6), DEV-FS1, DEV-FS2, DEV-PE1, DEV-PE2 (Table 7), USR-U1 to U5 (Table 8).
- Usability uses the weighted mean and the Likert ranges in Table 9.
- The field deployment was planned for about a week, at least four to five days, with an uptime log and a threshold calibration record.

If the real results force a change to any of these (for example, the field run was three days, not five), the fix goes into Chapter III or Chapter I as well. Don't let Chapter IV quietly disagree with them.

**Names and terms.** Use the same names as the rest of the paper: Garbage-Guard, YOLO26n, Raspberry Pi 5, Hikvision HiWatch HWI-B140H, ByteTrack, NCNN, SQLite, Firebase, the "Accumulation Alert", the "monitored area" or region of interest, the touchscreen dashboard and the Android application. Class names are bottles, bags and food containers.

**Comparison with other studies.** CIT wants results compared with other work. The natural sources are the studies already reviewed in Chapter II and summarised in Table 2. Cite them in APA 7 (author, year). If you bring in a new source, it needs a full APA 7 entry added to VIII. REFERENCES.

**Tone.** Academic, third person ("the researchers"), past tense for what was done and found. No first person, no marketing words. Brian also asks for plain sentences without em dashes.

**No AI mentions.** Don't write anything in the chapter or in repo files that credits an AI tool. The Declaration (Section VII) already covers how AI tools were used.

## Where the data already is

You probably know most of this, but it saves digging.

| What | Where |
|---|---|
| v7 test results (807 images): P 0.852, R 0.685, mAP50 0.745, mAP50-95 0.535 | `models/README.md` |
| v7 creek-only results (180 images): P 0.742, R 0.534, mAP50 0.593, mAP50-95 0.436 | `models/README.md` |
| Per-class creek mAP50: bottle 0.729, bag 0.179, food container 0.871 | `models/README.md` |
| Training: 100 epochs max, early stop at 93, 1.37 h, imgsz 640, batch 16, RTX 2070 | `models/README.md` |
| Alerts, captures, mutes, power events, per-class counts, snapshot paths | `alerts` table in `~/gg/garbageguard.db` on the Pi |
| Latency every 100 frames (mean, median, max in ms) | `perf_samples` table in the same database |
| Session summary with mean, median, max latency, FPS and the ISO band | Printed in the terminal when `garbageguard_live.py` exits |
| Counter logic test (19 tests), the evidence for TC_UNIT_02 | `python3 test_counter.py` on the Pi |
| Snapshots with boxes, monitored area and count stamped on | `~/gg/snapshots/` |
| Calibration procedure (dashboard count against hand count) | Section 5 of `Raspberry Pi 5/GarbageGuard_Field_Reference.md` |
| Ready SQL for alert history and latency | Section 8 of the same file |

Copy the database and snapshots off the Pi before anyone uses the History tab's Delete button. That deletion can't be undone.

## Things that don't line up yet

These came up while reading the manuscript against the code. Some need a decision from you, some from Brian or Prof. Macalisang. Settle them before the numbers go in, since each one changes how a result is reported.

1. **Accuracy versus precision for Objective 1.** Section 1.2 and TC_UNIT_01 grade the model on precision, recall and mAP@0.5. Table 3, row 1, grades it on "percentage of correct garbage recognition" and the Accuracy formula uses True Negatives, which object detection doesn't really have. Decide which single number gets read against the Table 3 scale. Precision fits the 80% target in Section 1.3. Report recall and mAP@0.5 beside it either way.
2. **Which test set is the headline.** On the full test set v7 reaches 0.852 precision (Good). On creek images only it reaches 0.742 (Fair). Both are real and both belong in the chapter. Which one answers the objective is your call, but the other one should not be left out.
3. **What "latency" measures.** Chapter III defines it as frame capture time to detection completion. The code times the `model.track()` call, so it covers inference and tracking but not camera capture or drawing. Either describe exactly what was timed, or ask Brian to adjust the Chapter III wording. The code reports milliseconds and Table 3 is in seconds, so convert.
4. **The "Successful Trials / 10" column.** Tables 4 to 6 count successes out of ten trials, but TC_UNIT_01 is one evaluation run on the test set and TC_ACC_03 is a log review. The current placeholder also mentions "Pass/Fail columns" that the tables don't have. Fill in what makes sense for each case and tell Brian what column changes Chapter III needs.
5. **Camera stream settings.** Section 3.4 says detection reads the main stream at 1280 x 720, 15 fps, H.264, cropped to the monitored area, with a 960 px detection size by default and the sub-stream as a fallback. Report the settings actually used in the field, because latency and small-object recall both depend on them. If they differ from Section 3.4, tell Brian so the two chapters agree.
6. **Old limitation notes.** Section 10 of the Field Reference still describes an earlier model (recall about 0.70, food container weakest). For v7, bags are the weakest class and food containers are the strongest. Use the v7 figures.
7. **"Good" equals "Neutral" in Table 9.** The usability target is a weighted mean of 2.61, which is the Neutral band. A panel member may ask why a neutral answer counts as passing. That's Brian's and the adviser's decision, not yours, but it affects how you word the evaluation results.
8. **Roboflow, the V-Model and the model versions.** From v19, Chapter III covers Roboflow (Section 3.4 and Figures 47 and 48), a V-Model and Scrum hybrid (Figure 45: requirements and system design up front, sprints of software design, development and unit testing, then system and acceptance testing at the end), and an Assembly and Installation Procedure that includes the threshold calibration steps. It says nine training runs so far (v1 to v8, plus v6b), v9 in training, and that v7 stays the default model and the one whose scores are reported. If v8 or v9 becomes the reported model, tell Brian so Chapter III and Figure 46 change with it. Chapter IV can refer back to all of this without introducing it again.
9. **Chapter V depends on this one.** Section 5.1 and 5.2 are still placeholders. Whatever findings you report here become the conclusions there, so a short list of your main findings at the end of your draft would help whoever writes Chapter V.

## A starting point, if useful

This is one way to open each section. Rewrite freely.

- **4.1** One paragraph on what Garbage-Guard is and where it ran (the creek site under the barangay testing permit in Appendix C), then the external photo of the unit and camera, then the internal photo of the junction box with each part labelled (a), (b), (c).
- **4.2** How a frame travels: camera RTSP into the Pi, crop to the monitored area, YOLO26n NCNN with ByteTrack, smoothed count, alert, SQLite record and snapshot, dashboard on port 8080, Firebase sync, Android app. Then the screens that satisfy each requirement.
- **4.3** Capabilities and limitations as two short lists, each item backed by a number from 4.4 to 4.7 where one exists.
- **4.4 to 4.7** For each objective: the filled table, a figure if it helps, two or three paragraphs saying what the numbers mean against Table 3, and a comparison with a study from Chapter II.

## Before handing it back

- Every specific objective has a direct answer with a number and a Table 3 band.
- New figures continue from 62, tables at the next free number after Table 17, and each is mentioned in the text before it appears.
- Every test case ID from Tables 4 to 8 appears with a result, or the text says why it wasn't run.
- Every cited author is in VIII. REFERENCES in APA 7.
- Anything that contradicts Chapter I or III is flagged to Brian, not left in.

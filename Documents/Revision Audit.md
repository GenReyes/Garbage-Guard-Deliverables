# Garbage-Guard Manuscript Audit

Checked `GarbageGuard v1.docx` (381 paragraphs, 7 tables, 15 figures) against the
code in this repository, the model card in `models/README.md`, and the Pi field
reference. Prof. Macalisang's points are tracked separately in `Revision Plan.md`;
this file records what the manuscript itself gets wrong.

---

## 1. References: the years are fine, the accuracy is not

All 41 entries were checked. Every journal, conference and web source already
falls inside 2021 to 2026, so no literature needs replacing on recency grounds.
Six entries sit outside that window:

| Entry | Year | Assessment |
|---|---|---|
| Den Hartog & Voncken | 2017 | Patent |
| Kim et al. | 2019 | Patent |
| Wu et al. | 2019 | Patent |
| Chordiya et al. | 2020 | Patent |
| Li & Wang | 2020 | Patent |
| United Nations | n.d. | Web page, undated |

Five of the six are patents. Patents are prior art, and prior art carries no
recency requirement; replacing them would empty Section 2.2 and Table 2, which
Prof. Macalisang did not ask for. They are being kept. The undated United Nations
entry is being replaced with a dated source for the same material.

### Citation errors found

| Problem | Location |
|---|---|
| Ne≈ærka cited as 2024 in the body, listed as 2023 | 2.1, References |
| Wu et al. cited as 2020 in the body, 2019 in the caption and the list | 2.2, Figure 2 |
| "Campbel et al. 2023" in the body; the list has Campbell et al. (2022) | 2.2, Figure 6 |
| Figure 9's discussion opens "The patent at Figure 8..." | 2.2, Figure 9 |
| Stray tab inside the Gryech et al. entry ("I&#9;Internet") | References |
| "Li1, F. H." in the Al Noman et al. entry | References |
| Ella et al. title reads "solid waste management the economic growth"; the word "to" is missing. The Chapter 2 heading has it right, so only the list entry is wrong | References |
| Ella et al. entry drops the comma after each surname ("Ella D. M., Lim D. M., Lu B.") | References |

---

## 2. The figures do not match the prototype

| Figure | Problem |
|---|---|
| 11, System Architecture | The board pictured is a Raspberry Pi 3, not the Pi 5 that is actually used. The model is labelled "Yolov26". Storage is shown as SQLite alone. The buzzer, the Firebase sync, the Android application, the step-up converter and the UPS HAT are all absent. The creek photographs carry a stock-library watermark. |
| 12, Block Diagram | YOLO appears twice in sequence as two separate stages, which is not what the pipeline does. "Waste Categories & Accumulation Count" has an unclosed bracket. The ROI filter and the tracker are missing. |
| 13, Flowchart | Storage sits only on the "threshold not reached" branch, so on the reading of the diagram an alerting event is never written to the database. The ROI test, the tracker that stops an item being counted twice, the buzzer and the push to the app are all missing. |
| 15, Gantt Chart | Ran April to July. Replaced. |

Prof. Macalisang also asked for a block diagram of how the model was trained
(`Revision Plan.md`, item 1.2). No such figure exists yet.

---

## 3. The numbers in the text are not the numbers the system produced

| Manuscript | Repository |
|---|---|
| "YOLOv26", "YOLOv26n" | The model is YOLO26n; the Pi runs an NCNN export of it |
| "custom dataset of 2,000 images (1,500 for training and 500 for testing)" | `dataset_v7`, split 70/10/20, with a frozen 807-image test set |
| "a baseline accuracy of at least 80%" | Precision 0.852, recall 0.685, mAP50 0.745 on the full test set |
| TC_UNIT_01 tests "500 testing images" | 807 held-out images |
| Storage is SQLite | SQLite on the Pi, with Firebase sync to the Android application |

The creek-only subset is weaker still: precision 0.742, recall 0.534, mAP50
0.593, and bags are detected at mAP50 0.179. A claim of "at least 80% accuracy"
is not supportable, and "accuracy" is in any case not a standard object-detection
metric. Precision, recall and mAP@0.5 should be reported instead, which is also
what `Revision Plan.md` item 2.9 asks for.

### Components named in 1.2 System Design but missing from the manuscript

Buzzer, the 5 V to 12 V step-up converter that powers the camera, Android
application, Firebase. The vibration motor that
appears in older drafts was removed from the build on 17 October 2026 and should
not reappear.

---

## 4. Structure

- Table 1 sits in Chapter 1, where Prof. Macalisang said it does not belong. It
  moves to Chapter 3 beside the testing parameters.
- The front matter numbers References as IV, and gives both Appendices and the
  Bionote as V. Adding Chapters IV and V pushes References to VI, Appendices to
  VII and the Bionote to VIII.
- Chapter 2 has no framework at its start and no synthesis at its end.
- Chapters IV and V do not exist, so neither appears in the table of contents.

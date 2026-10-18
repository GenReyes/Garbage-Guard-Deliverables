# Chapter 2 Restructure

## Why it changes

Both samples in this folder name their second chapter **CONCEPTUAL FRAMEWORK**, not
Literature Review. HelmetGuard, which came out of the same department and uses a
comparable detector, runs its Chapter 2 from page 8 to page 51 and carries four
tables inside it. Garbage-Guard's Chapter 2 runs about twenty pages, carries one
table, has no framework, and ends on the last patent with nothing drawing the
review together. That gap is most of what is missing.

HelmetGuard's Chapter 2 holds, in order:

| Section | Device |
|---|---|
| Review of Related Literature and Studies | prose, grouped by theme |
| (inside the review) SWOT Matrix | Table 1 |
| (inside the review) Training Details in Different Models | Table 2 |
| (inside the review) Related Patents | Table 3 |
| (inside the review) Proposed Solution Matrix | Table 4 |
| Conceptual Model of the Study | figure and prose |
| Operational Definition of Terms | list, not adopted here |

## Proposed structure for Garbage-Guard

The chapter is renamed **II. CONCEPTUAL FRAMEWORK**. The framework goes at the
front, as you asked, so the reader meets the system before the review rather than
after it, and the synthesis closes the chapter. HelmetGuard puts its conceptual
model last instead; say the word if you would rather match it exactly.

| New section | Status | Pages |
|---|---|---|
| 2.1 Conceptual Framework of the Study | New. Figure 1 (IPO) plus prose | 2 |
| 2.2 Review of Related Literature and Studies | Existing prose, reorganised | 14 |
| 2.2.1 Garbage Pollution in Philippine Waterways | 6 existing studies | 3 |
| 2.2.2 Computer Vision and Object Detection | 8 existing studies | 4 |
| 2.2.3 Comparison of Object Detection Models | New. Table 1 | 2 |
| 2.2.4 Smart Environmental Monitoring Systems | 7 existing studies | 4 |
| 2.3 Review of Related Patents | Existing Table 2 and Figures 2 to 11 | 11 |
| 2.4 Synthesis of the Review | New. Prose plus Table 3 | 3 |

HelmetGuard closes its Chapter 2 with an Operational Definition of Terms. Brian
has ruled that out for this manuscript, so the chapter ends on the synthesis.
Terms that genuinely need defining are defined where they are first used, and the
existing List of Abbreviations in the front matter carries the rest.

Three things are worth flagging before I write them in.

**The headings are currently whole paper titles.** "Assessing optimal frequency
for image acquisition in computer vision systems developed to monitor feeding
behavior of group-housed Holstein heifers, (Bresolin et al., 2022)" is a heading
in the present draft. Twenty-one of those bloat the table of contents and tell a
reader nothing. They become topical headings, with the citation carried in the
prose where it belongs.

**Four of the studies are hard to defend as they stand.** Holstein heifer feeding
behaviour, warehouse inventory accuracy, lane detection for autonomous vehicles
and PM2.5 prediction are all real computer vision work, but none of them is about
waste or water, and the draft never says why they are present. Each one keeps its
place only if its paragraph ends on what Garbage-Guard took from it. The heifer
study, for instance, is genuinely useful on image acquisition frequency, which is
the same question as what frame rate the Pi should sample at. Written that way it
earns its place. Left as a summary it does not.

**There is no statement of the gap.** The review should end by saying what nobody
in it did, which is run a trained detector on an edge device at a creek, count
accumulation inside an operator-drawn region, and alert on a threshold. That
sentence is what the whole chapter is for.

---

# Draft: 2.1 Conceptual Framework of the Study

Figure 1 presents the conceptual framework of Garbage-Guard, arranged as an
input-process-output model with a feedback path. The process column is expanded
beyond a single detection block so that each point at which a design decision was
made is visible, since those decisions are what separate this system from a
detector that merely labels frames.

**Input.** Three inputs drive the system. The first is the live video feed from a
power-over-Ethernet closed-circuit camera fixed on the creek surface, which
supplies frames continuously rather than on a schedule. The second is the
annotated dataset, held in this study as dataset_v7 and split 70/10/20 into
training, validation and test partitions across three classes, namely bottles,
bags and food containers. The third is the set of operator-supplied settings: the
region of interest traced on the water, the accumulation threshold, which
defaults to twenty items, and the alert preferences.

**Process.** Processing runs in four stages on a Raspberry Pi 5. Detection is
performed by a YOLO26n convolutional neural network exported to NCNN, the format
the board runs fastest. Region filtering then discards any detection whose
position falls outside the traced water area, which is what prevents fixed objects
on the bank from entering the count. Tracking assigns and holds one identity per
object, so an item drifting across the frame is counted once rather than once per
frame. Accumulation counting tallies the tracked identities against the threshold.
Logging and alerting write each event and its snapshot to a local SQLite database
and synchronise them to Firebase.

The second and third stages deserve emphasis because they are the difference
between a detector and a monitoring system. A detector that reports twenty
bounding boxes in a frame has not established that twenty items are present, and
a system that cannot exclude the bank will report a concrete wall as garbage for
as long as it is in view.

**Output.** The system produces three outputs. The touchscreen dashboard shows the
annotated live feed with bounding boxes, class labels and the running count. When
the threshold is crossed, an accumulation alert is raised on screen, sounded on
the buzzer and pushed to the Android application. Every recorded event is retained
in a searchable history with a saved snapshot, which is what allows a reading to
be checked after the fact.

**Feedback.** Evaluation against ISO/IEC 25010:2023, covering functional
suitability, performance efficiency and usability, returns to the input and
process stages. Weak per-class results direct further annotation and retraining,
measured latency informs the frame sampling rate, and operator comments on the
interface feed the dashboard design. The feedback path is drawn because it
describes what happened in this study: the detection model passed through seven
training iterations, and the accumulation threshold is calibrated on site rather
than fixed in advance.

---

# Draft: 2.4 Synthesis of the Review

The literature reviewed in this chapter converges on four points.

First, the volume of plastic reaching the ocean through rivers is large, is
concentrated in a small number of waterways, and is worst where collection is
weakest. Meijer et al. (2022) place more than a thousand rivers behind eighty per
cent of riverine plastic emission, and Ng et al. (2023) and Corpuz (2025) locate
Southeast Asia and the Philippines specifically among the places where collection
infrastructure does not keep pace. The problem Garbage-Guard addresses is
therefore not a local inconvenience but a documented and measured one.

Second, manual inspection does not scale to it. The studies that quantify waste in
waterways rely on survey campaigns, satellite passes or modelled estimates, none
of which produce a reading an operator can act on this afternoon.

Third, convolutional object detection is mature enough to carry the task. Murat
and Kiran (2025) and Yaseen (2024) document a decade of accuracy and speed gains
across YOLO versions, and Nežerka et al. (2023) show convolutional classification
outperforming hand-selected feature extraction on waste fragments specifically.
Detection of floating waste is not an open research question.

Fourth, the supporting pieces are established independently. Bresolin et al.
(2022) address how often a vision system needs to sample to retain accuracy.
Kang et al. (2022) address detection in overhead imagery, which is the viewing
geometry of a camera above water. Narayana et al. (2024), Zeng et al. (2024) and
Te et al. (2024) establish that environmental sensing on constrained hardware with
real-time alerting is routine practice.

**What the literature does not contain.** No study reviewed here brings these
pieces together in the form this project requires. The waste-detection work
classifies images without counting accumulation over time. The accumulation work
counts items in containers rather than on moving water. The environmental
monitoring work alerts on scalar sensor readings rather than on a visual count.
None of the reviewed systems runs a trained detector on an edge device at a creek,
restricts counting to an operator-defined area of water, holds one identity per
item across frames so that drift does not inflate the count, and raises a
threshold alert on the result. Table 3 sets out that gap against what this study
proposes.

### Table 3: Proposed Solution Matrix

| Limitation in the reviewed literature | Representative source | How Garbage-Guard responds |
|---|---|---|
| Waste detection stops at classification, with no accumulation measure over time | Nežerka et al. (2023); Magyar et al. (2023) | Tracked identities are tallied against an operator-set threshold, so the output is a count that persists rather than a per-frame label |
| Monitoring of waterways depends on manual survey or satellite passes, so readings are periodic | Meijer et al. (2022); Victoriano et al. (2022) | Continuous processing of a fixed camera feed, with every event logged and timestamped on site |
| Counting systems assume a contained, static scene | Balachandran and Talavera (2023) | Region filtering confines counting to a traced area of moving water, and tracking holds one identity per drifting item |
| Environmental alerting is driven by scalar sensor thresholds, not by visual evidence | Te et al. (2024); Arante et al. (2025) | The alert is raised from a visual count and carries a saved snapshot, so the reading that triggered it can be reviewed |
| Detection models are evaluated on curated datasets rather than deployment conditions | Murat and Kiran (2025); Yaseen (2024) | The model is reported on a frozen held-out partition and again on a creek-only subset, so field performance is stated separately |

The gap the matrix describes is the one this study occupies. Chapter 3 sets out
the methodology by which the system was designed, built and evaluated against it.

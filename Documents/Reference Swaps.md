# Proposed Reference Swaps

Brian asked for free alternatives wherever a reference cannot be downloaded.
Chasing that turned out to fix a second problem at the same time.

Four of the studies in Chapter 2 are both paywalled and only loosely connected to
this project. They are real computer vision work, but none of them is about waste
or water, and the manuscript never says why they are present. The replacements
below are open access, downloadable, published 2023 to 2025, and about floating
waste detection on rivers. Several are close enough to Garbage-Guard that they
strengthen the review rather than merely filling the slot.

All five candidates are already downloaded to `References/candidates/` so you can
read them before deciding. Nothing has been swapped in the manuscript yet.

---

## The candidates

**Li et al. (2024).** *Detection of Floating Objects on Water Surface Using
YOLOv5s in an Edge Computing Environment.* Water, 16(1), 86.
https://doi.org/10.3390/w16010086

This is the closest paper to what you built: a YOLO detector for floating objects
running on edge hardware. It reports a 15.7% accuracy gain over baseline YOLOv5,
an 83% reduction in false detections on small targets, and 33 frames per second
at the edge. It gives Chapter 2 a direct precedent for the edge-deployment
decision and gives Chapter 4 a published figure to compare your latency against.

**Nunkhaw and Miyamoto (2024).** *An Image Analysis of River-Floating Waste
Materials by Using Deep Learning Techniques.* Water, 16(10), 1373.
https://doi.org/10.3390/w16101373

YOLOv5 with DeepSORT tracking on river-floating waste, reporting mean average
precision at or above 88% and about 80% accuracy across three scenarios. This is
the detector-plus-tracker combination Garbage-Guard uses, applied to the same
subject, which makes it the single most useful citation in this list.

**Nunkhaw et al. (2025).** *Enhancing River Waste Detection with Deep Learning
and Preprocessing: A Case Study in the Urban Canals of Thailand.* Water, 17(22),
3193. https://doi.org/10.3390/w17223193

A YOLO detector with DeepSORT in Southeast Asian urban canals, trained first on
laboratory images and then adapted to real river conditions. The setting is
closer to a Manila creek than anything currently cited, and the laboratory to
field adaptation mirrors your own path from collected images to creek deployment.

**Jia et al. (2023).** *Advancing deep learning-based detection of floating
litter using a novel open dataset.* Frontiers in Water, 5, 1298465.
https://doi.org/10.3389/frwa.2023.1298465

Useful for the dataset discussion, since it addresses the shortage of public
floating-litter data, which is the same problem that made you annotate your own.

**Kataoka et al. (2024).** *Comparison of instance segmentation models for
floating macroplastic in river surface images.* Frontiers in Earth Science, 12,
1427132. https://doi.org/10.3389/feart.2024.1427132

Supports the choice of bounding-box detection over segmentation, which is a
decision the manuscript currently makes without defending.

---

## Suggested mapping

| Remove | Why | Replace with |
|---|---|---|
| Bresolin et al. (2022), image acquisition frequency in dairy heifers | Paywalled; about cattle feeding behaviour | Li et al. (2024) |
| Ayoola et al. (2024), warehouse inventory accuracy | Only on ResearchGate; about stock control | Nunkhaw and Miyamoto (2024) |
| Yang et al. (2022), falling hazards from surveillance video | Paywalled; about construction safety | Nunkhaw et al. (2025) |
| Lee et al. (2024), urban form and seasonal PM2.5 | Paywalled; about air quality prediction | Jia et al. (2023) |

Keep **Al Noman et al. (2022)** on lane detection. It is free, and it genuinely
supports the point about detection under varying light.

Two paywalled references are worth keeping even though no free copy exists,
because they carry arguments nothing else does:

- **Nežerka et al. (2023)** is the only citation showing convolutional
  classification beating hand-selected features on waste specifically.
- **Gryech et al. (2024)** supports the machine learning and IoT framing in the
  environmental monitoring section.

For those two the entry in `References/index.md` carries the link, and a copy can
be pulled from a campus connection.

---

## What this costs

Each swap means rewriting one paragraph in Chapter 2 and one line in the
reference list. The replacements are closer to the topic, so the paragraphs get
easier to write, not harder: each one can end on what Garbage-Guard took from it
instead of straining for a connection.

Say which swaps you want and I will make them.

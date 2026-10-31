# Models

## garbageguard_v7_best.pt

Trained Garbage-Guard v7 detection model.

| Item | Value |
|---|---|
| Model | YOLO26n |
| Training | 100 epochs max, stopped early at epoch 93 (1.37 hours) |
| Image size | 640 |
| Batch | 16 |
| GPU | RTX 2070 |
| Classes (in order) | 0 bottle, 1 bag, 2 food_container |
| Dataset | dataset_v7, 70/10/20 train/val/test split, frozen 807-image test set |

### Results on the full test set (807 images)

| Precision | Recall | mAP50 | mAP50-95 |
|---|---|---|---|
| 0.852 | 0.685 | 0.745 | 0.535 |

### Results on the creek-only test set (180 real creek images)

| Precision | Recall | mAP50 | mAP50-95 |
|---|---|---|---|
| 0.742 | 0.534 | 0.593 | 0.436 |

Per class at the creek (mAP50): bottle 0.729, bag 0.179, food_container 0.871.

### Known weakness

- Floating bags are rarely detected at the creek (recall 0.217, only 20 test images).
- About 4 in 10 bottles are missed.

### Notes

- These scores are not comparable to the v5 or v6b scores, because the test set is different.
- Status: the NCNN export runs on the Raspberry Pi 5. v7 is the model the
  code falls back to when no other model is chosen, or when the chosen one
  fails to load. Which model the unit runs is picked on the dashboard's Model
  card. The latency results are in Chapter IV of the manuscript.
- v8 was run in the field session of 31 October 2026 through the dashboard's
  Model card. Its scores are below; its weights are not in this folder.
- `.gitignore` allows only this one `.pt` file. Other `.pt` files and the NCNN folder stay out of the repo.

## garbageguard_v8 (weights not in the repo)

Retrained from v7 with the creek set relabelled, new bag images and field
captures, at 960 px. Scored on the same frozen 807-image test set as v7, so
the two are directly comparable. Source: Tables 10 and 11 of the manuscript
(`Documents/Garbage-Guard v22.docx`).

| Test set | Precision | Recall | mAP50 |
|---|---|---|---|
| Full (807 images) | 0.829 | 0.674 | 0.728 |
| Creek only (180 images) | 0.567 | 0.499 | 0.497 |

Per class at the creek (mAP50): bottle 0.509, bag 0.174, food_container 0.807.

v8 scores a little below v7 on the full test set and clearly below it at the
creek. The field session of 31 October 2026 ran v8, and its latency on the Pi
is reported in Chapter IV.

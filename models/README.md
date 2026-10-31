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
- Status: the NCNN export runs on the Raspberry Pi 5 and is the Pi's default
  model. On the main stream cropped to the monitored area at 960 px, the Pi
  takes a median of about 190 ms per frame. The full latency results are in
  Chapter IV of the manuscript.
- A v8 model was run in the field session of 31 October 2026 through the
  dashboard's Model card. Its weights and scores are not in this folder yet.
- `.gitignore` allows only this one `.pt` file. Other `.pt` files and the NCNN folder stay out of the repo.

# v8 and v9 Insert for Chapter III and Chapter IV

Paste-ready text for Garbage-Guard v32. Each piece says where it goes. Text in [square brackets] is a number still missing. Nothing here rewrites an existing section except the sentences marked REPLACE.

---

## 1. Chapter III, Section 3.5, "Dataset Annotation and Model Training" (new paragraph at the end)

Models v8 and v9 were trained on the same laptop with the same settings as v7, except that the image size was raised from 640 to 960 pixels, the detection size used on the Raspberry Pi, and the batch size was lowered from 16 to 8 to fit the graphics memory. The v8 dataset had 3,667 training and 391 validation images. It added 157 frames from the prototype's camera taken at the creek on October 21, 325 bag images from public datasets, and a relabelled set of creek photographs that replaced 750 older ones. Training stopped at epoch 136 and the best epoch was 105. The v9 dataset had 3,742 training and 393 validation images. It added 57 frames from the prototype's camera (1280 x 720, October 28) and a few more creek photographs to the v8 data, and it trained for 120 epochs in 3.2 hours. The Roboflow exports list the classes as bag, bottle, food container, so the label numbers were remapped to the order bottle, bag, food container and the result was checked.

The frozen test set of 807 images was kept the same for every version and was never trained on, so the scores of v7, v8 and v9 can be compared. Two steps kept test images out of training. Images were matched to the frozen test list by their original file names, because Roboflow changes the file contents when it exports again and a check on file contents alone would miss them. Photographs taken within 60 seconds, or within two shots, of a test photograph were also removed from training (163 images for v8 and v9). Each merge script stopped if the test count was not 807 or if any test image appeared in the training or validation set.

A second test set was made from 59 frames of the prototype's own camera at 1280 x 720, with 2,621 labelled items (640 bottles, 998 bags and 983 food containers). These frames were kept out of the training of v7 and v8. v9 was trained on them, so they are not used to score v9.

---

## 2. Chapter IV, Section 4.3, "Training history"

REPLACE the first sentences of the paragraph under "Training history" with:

Nine training runs were carried out: v1, v3, v4, v5, v6, v6b, v7, v8 and v9. The dataset for v2 was assembled but not trained, because v3 replaced it. Table 10 lists every run with the change that defined it and its scores on its own test set.

Table 10, replace the "[Reserved]" row with:

| v9 | 57 more prototype camera frames (1280 x 720, October 28) and a few more creek photographs, trained at 960 px | 807 | [ ] | [ ] | 0.730 |

Table 10 note, REPLACE the second and third sentences with: v7, v8 and v9 use the same frozen test set of 807 images. v8 and v9 are shown at their training size of 960 pixels.

REPLACE the last sentence of the v8 paragraph ("The frozen test set is made of older phone photographs ... decide between the models.") with:

The frozen test set is made of older phone photographs and does not show the sharper 1280 x 720 picture the prototype now uses, so the models were also compared on frames from the prototype's own camera (Table 13).

REPLACE the "[Reserved paragraph for v9 ...]" with:

v9 added 57 frames from the prototype's camera and a few more creek photographs to the v8 data and was also trained at 960 pixels. On the frozen test set it scored a mAP@0.5 of 0.730 against 0.728 for v8, and on the creek-only photographs 0.530 against 0.496. A difference this small is within what one training run can vary by, and the creek-only set holds only 46 bag boxes, so the researchers did not report v9 as better than v8. The choice between the two will be made on new camera frames that neither model has seen.

---

## 3. Chapter IV, Section 4.3, new table after Table 11 (becomes Table 12; the later tables move up by one)

Text before the table:

Table 12 compares v7, v8 and v9 on the same frozen test set, measured on the same computer with the same script at both image sizes. Table 11 shows each model at its own training size, while Table 12 puts them on equal terms.

**Table 12**

*mAP@0.5 of v7, v8 and v9 on the Frozen Test Set and the Creek-Only Photographs*

| Test set | Class | Image size | v7 | v8 | v9 |
|---|---|---|---|---|---|
| Full (807) | All classes | 640 px | 0.745 | 0.724 | 0.725 |
| Full (807) | All classes | 960 px | 0.715 | 0.728 | 0.730 |
| Full (807) | Bottles | 960 px | 0.626 | 0.629 | 0.643 |
| Full (807) | Bags | 960 px | 0.782 | 0.816 | 0.804 |
| Full (807) | Food containers | 960 px | 0.737 | 0.739 | 0.742 |
| Creek (180) | All classes | 640 px | 0.593 | 0.539 | 0.554 |
| Creek (180) | All classes | 960 px | 0.563 | 0.496 | 0.530 |
| Creek (180) | Bags | 960 px | 0.232 | 0.174 | 0.204 |

*Note.* The creek-only bag row rests on 46 boxes. The values can differ by 0.001 from Table 11 because the models were scored again with one script.

On phone photographs the three models are within about 0.02 mAP@0.5 of each other at 960 pixels. v7 is ahead only at 640 pixels, the size it was trained at.

---

## 4. Chapter IV, Section 4.3, new table for the prototype's camera frames (becomes Table 13)

Text before the table:

Table 13 shows the scores on the 59 frames from the prototype's camera. These frames are crowded, with about 44 labelled items in each, and many of the items are very small.

**Table 13**

*mAP@0.5 on 59 Frames from the Prototype's Camera (1280 x 720)*

| Class | Image size | v7 | v8 |
|---|---|---|---|
| All classes | 640 px | 0.019 | 0.108 |
| All classes | 960 px | 0.055 | 0.170 |
| Bags | 960 px | 0.054 | 0.234 |

*Note.* v9 is not shown because it was trained on these frames. The test shrinks each whole frame to the image size, while the prototype crops the frame to the monitored area first, so the live system sees more detail than this test.

---

## 5. Chapter IV, Section 4.3, discussion (after the new tables)

On the prototype's own camera frames, v8 scored clearly higher than v7 (0.170 against 0.055 mAP@0.5 at 960 pixels), and this is the main reason v8 runs on the Raspberry Pi. v8 was trained on frames from the same creek spot on another day and v7 was not, which is fair for a camera fixed at that spot but means the result may not carry over to a new site. The scores on these frames are low for every model, because the frames are crowded and hold many tiny items, and even v9 reached only 0.695 on frames it had trained on. The models were scored on still images, so these results do not show how the system behaves live, such as flicker or false alarms from water and shadows.

The Raspberry Pi therefore runs v8 as its main model, with v9 as a second option and v7 as the fallback. All three can be selected on the Model card in the Settings tab. A new model is put on the unit only if it gains at least 0.03 mAP@0.5 on a set of camera frames it has not seen, or clearly improves bag recall, and runs well live.

---

## 6. Chapter IV, Section 4.6, Limitations (add these items)

- No set of camera frames that v9 has not seen has been scored yet. New frames are being collected on a different day and in different light.
- The results come from one site and one training run for each model, so differences of a few points may be ordinary run-to-run variation.
- The bag result on the creek-only photographs rests on 46 boxes.
- The scores were measured on still photographs, not on live behavior such as flicker and false alarms on water and shadows.

---

## 7. Chapter V

- Finding 1: say "nine training runs" and drop the CONFIRM about v9 (the run finished).
- Findings 2 and 3 and Conclusion 1: state which model each number comes from. Use v8 for the field work, and say that v7 is the reference on the frozen test set.
- Chapter III, Section 3.5, "The model went around this loop many times": change "eight times" to "nine times" and replace "with a v9 that adds more creek photographs in training at the time of writing" with "v9 was the ninth".

---
title: "Chapter IV Update Draft: v10"
subtitle: "Changes to Garbage-Guard v47, for review before they go into the main file"
---

# How to read this draft

Each block below says where it goes in v47 and whether it **replaces** text that is there now or is **added**. Everything that is not listed stays as it is in v47. Table and figure numbers do not change, so Tables 14 to 20 and the Field Kit stay correct.

Text in square brackets is a placeholder for a result that does not exist yet. There are two kinds:

- **[After the full test]**: numbers that will change once the remaining 11 session 3 test photographs are labelled and `eval_v10.py` is run again. The current figures are from 19 test photographs (361 boxes).
- **[After the live check]**: what happens when v10 runs on the Raspberry Pi at the creek.

# 1. Section 4.3, Training history, first paragraph

**Replaces** "Nine training runs were carried out: ..."

Ten training runs were carried out: v1, v3, v4, v5, v6, v6b, v7, v8, v9 and v10. The dataset for v2 was assembled but not trained, because v3 replaced it. Table 10 lists every run with the change that defined it and its scores on its own test set.

# 2. Table 10, new last row and note

**Adds** this row under v9:

| Run | Main change from the previous run | Test images | Precision | Recall | mAP@0.5 |
|---|---|---|---|---|---|
| v10 | 27 photographs and prototype camera frames from the third data session added to the v9 data, trained at 960 px | 807 | 0.846 | 0.672 | 0.744 |

**Replaces** the note under Table 10:

*Note.* The scores of v1 to v6b are not directly comparable with each other or with v7 and later, because each of those runs had a different test set. v7 to v10 use the same frozen test set of 807 images. v8, v9 and v10 are shown at their training size of 960 pixels. \*The v6 score is not valid: its data loader silently skipped all 1,092 styrofoam images in every split.

# 3. The v7 paragraph, last two sentences

**Replaces** "It is the model whose scores are reported in the rest of this objective, unless the reported-model paragraph below says otherwise."

It served as the reference for every run after it.

# 4. The v9 paragraph, last sentence

**Replaces** "The choice between the two will be made on new camera frames that neither model has seen."

To settle the question on pictures that neither model had seen, the researchers held a third data session and kept half of it out of training as a test set.

# 5. New paragraphs on v10

**Added** after the v9 paragraph, before "Results of the reported model".

The third data session took place at the same creek on November 4, 2026. It produced 58 pictures: 31 frames saved by the prototype and 27 photographs taken with a phone. Pictures taken within 30 seconds of each other were treated as one burst, because they show almost the same scene, and every burst was placed whole on one side. This gave 28 pictures for training and 30 for testing, with frames from the prototype and phone photographs on both sides. Any older image taken within 60 seconds of a test picture was also kept out of training. [After the full test: at the time of writing, 27 training pictures and 19 test pictures had been labelled; the other test pictures are being labelled.]

v10 is v9's data with the 27 labelled training pictures from the third session added. It was trained with the same settings as v9: 960 pixels, a batch of 16, up to 120 epochs and the same random seed. On the frozen test set it scored a precision of 0.846, a recall of 0.672 and a mAP@0.5 of 0.744. Its mAP@0.5 is close to v8 (0.728) and v9 (0.730), within what one training run can vary by. Its precision is clearly higher than v9's (0.846 against 0.793) and is back above the 80% target, while its recall is lower (0.672 against 0.702). In plain terms, v10 marks a few fewer items than v9, but it is right more often when it does.

On the test half of the third session, v10 reached a mAP@0.5 of 0.404 at 960 pixels, against 0.337 for v9, 0.204 for v8 and 0.149 for v7 (Table 13). Its precision there was 0.530 and its recall 0.367, against 0.330 and 0.266 for v8. This comparison favors v10 for a clear reason: v10 was trained on pictures from the same spot and the same day, and the other models were not. The test pictures themselves were never used in training, so the result shows how well v10 handles new moments at this creek, but it does not show how it would do at a different site. For a camera that stays fixed at one spot, this is the situation that matters. v10 is the reported model for the rest of this objective.

# 6. "Results of the reported model", first line

**Replaces** "Table 11 gives the per-class results of v7, v8 and v9 on the frozen test set of 807 images and on its 180 creek images."

Table 11 gives the per-class results of v8, v9 and v10 on the frozen test set of 807 images and on its 180 creek images. The v7 results stay in Tables 10 and 12.

# 7. Table 11, rebuilt

**Replaces** Table 11. The title stays: *Per-Class Detection Results on the Frozen Test Set and on the Creek Images*.

| Test set | Class | v8 P | v8 R | v8 mAP@0.5 | v9 P | v9 R | v9 mAP@0.5 | v10 P | v10 R | v10 mAP@0.5 |
|---|---|---|---|---|---|---|---|---|---|---|
| Full (807) | All classes | 0.829 | 0.674 | 0.728 | 0.793 | 0.702 | 0.730 | 0.846 | 0.672 | 0.744 |
| Full (807) | Bottles | 0.772 | 0.556 | 0.629 | 0.745 | 0.617 | 0.643 | 0.828 | 0.571 | 0.664 |
| Full (807) | Bags | 0.854 | 0.803 | 0.816 | 0.789 | 0.807 | 0.804 | 0.831 | 0.787 | 0.818 |
| Full (807) | Food containers | 0.861 | 0.662 | 0.739 | 0.845 | 0.682 | 0.742 | 0.881 | 0.657 | 0.750 |
| Creek (180) | All classes | 0.567 | 0.499 | 0.497 | 0.613 | 0.508 | 0.530 | 0.689 | 0.423 | 0.536 |
| Creek (180) | Bottles | 0.601 | 0.505 | 0.509 | 0.671 | 0.547 | 0.563 | 0.834 | 0.450 | 0.590 |
| Creek (180) | Bags | 0.248 | 0.261 | 0.174 | 0.287 | 0.261 | 0.204 | 0.312 | 0.152 | 0.194 |
| Creek (180) | Food containers | 0.851 | 0.732 | 0.807 | 0.881 | 0.716 | 0.823 | 0.921 | 0.666 | 0.825 |

*Note.* P is precision and R is recall. All three models were scored at 960 pixels, the size they were trained at, with the same script on the same computer. The creek-only bag rows rest on 46 boxes.

# 8. The three paragraphs after Table 11

**Replaces** the paragraphs starting "Precision is the measure read against the Table 3 scale", "Recall was lower than precision in every class" and "The precision of 85.2% on the full test set".

Precision is the measure read against the Table 3 scale, following the 80% target in Section 1.3, with recall and mAP@0.5 reported beside it. On the full test set v10 reached a precision of 84.6%, which falls in the Good band (80% to 89.99%) and meets the 80% target. v8 also reached the Good band (82.9%), and v9 fell just short of it (79.3%, Fair). On the creek images alone the precision of v10 was 68.9%, in the Poor band (below 70%), although it was still the highest of the three models there (56.7% for v8 and 61.3% for v9). The difference between the two sets is expected, because the creek images are harder: items are smaller, partly submerged and mixed with leaves and other debris. Nunkhaw et al. (2025) reported the same kind of gap between controlled and field conditions for floating waste in urban canals.

Recall was lower than precision in every class. In plain terms, when the model marks an item it is usually right, but it misses some items altogether. Since missed items are not counted, the system is more likely to count too few items than too many, so an alert may come later than it would with a perfect detector, but rarely too early. Bags in the older creek photographs were the weakest result, with a recall of 0.152 on 46 boxes, lower than the 0.261 of v8 and v9. On the newer pictures of the third session, however, v10 found bags far more often (recall of 0.427, Table 13), so the weakness is strongest on the older photographs. Food containers were the strongest class, with a mAP@0.5 of 0.825 on the creek images.

The precision of 84.6% on the full test set is close to the 85.9% overall accuracy that Nežerka et al. (2023) reported for convolutional recognition of waste fragments, although the two measures differ and their task was classification of single fragments rather than detection in a moving scene.

# 9. Figures 59 to 61, switched to v10

**Replaces** the paragraph "Figures 59 to 61 show the training curves ... of the v8 model, the model that ran in the field."

Figures 59 to 61 show the training curves, the precision-recall curve and the normalized confusion matrix of the v10 model. The precision-recall curve and the confusion matrix were made on the validation set, not on the frozen test set.

**Replaces** the three figure titles and images:

Figure 59

*Training and Validation Curves of the v10 Model*

![](/mnt/user-data/uploads/GarbageGuard/runs/detect/GarbageGuard/yolo26n_v10/results.png){width=6in}

Figure 60

*Precision-Recall Curve of the v10 Model on the Validation Set*

![](/mnt/user-data/uploads/GarbageGuard/runs/detect/GarbageGuard/yolo26n_v10/BoxPR_curve.png){width=5in}

Figure 61

*Normalized Confusion Matrix of the v10 Model on the Validation Set*

![](/mnt/user-data/uploads/GarbageGuard/runs/detect/GarbageGuard/yolo26n_v10/confusion_matrix_normalized.png){width=5in}

**Replaces** the paragraph after the figures, starting "The training curves in Figure 59 show the losses falling steadily":

The training curves in Figure 59 show the training losses falling steadily throughout the run. The validation mAP@0.5 rose quickly over the first 40 epochs and levelled off at about 0.76 from epoch 50 onward. The validation classification loss stopped falling at about epoch 70 and then rose slightly, a sign that longer training would not have helped. Training stopped early at epoch 109 of 120, after 30 epochs without improvement. The best epoch was 79, and training took about 2.9 hours on the same laptop used for v7 and v8. On the validation set the best epoch reached a precision of 0.854, a recall of 0.698 and a mAP@0.5 of 0.773. These are higher than the frozen test scores in Table 11 because the validation images come from the same sources as the training images. v10 also has a different validation set from v8, so the validation scores of the two runs cannot be compared directly. The confusion matrix in Figure 61 shows that 85% of bags, 80% of bottles and 80% of food containers were classified correctly. Bottles and food containers were the classes most often missed, with 14% of each predicted as background. Of the false detections on background, 72% were labelled as bottles and 20% as food containers, so a false alarm is most likely to appear as a bottle.

# 10. Table 12, v10 column added

**Replaces** the sentence before Table 12 and the table. The new title is *mAP@0.5 of v7 to v10 on the Frozen Test Set and the Creek-Only Photographs*.

Table 12 compares v7 to v10 on the same frozen test set, measured on the same computer with the same script at both image sizes. Table 11 shows the models at their training size of 960 pixels, while Table 12 also shows them at 640 pixels, the size v7 was trained at.

| Test set | Class | Image size | v7 | v8 | v9 | v10 |
|---|---|---|---|---|---|---|
| Full (807) | All classes | 640 px | 0.745 | 0.724 | 0.725 | 0.728 |
| Full (807) | All classes | 960 px | 0.715 | 0.728 | 0.730 | 0.744 |
| Full (807) | Bottles | 960 px | 0.626 | 0.629 | 0.643 | 0.664 |
| Full (807) | Bags | 960 px | 0.782 | 0.816 | 0.804 | 0.818 |
| Full (807) | Food containers | 960 px | 0.737 | 0.739 | 0.742 | 0.750 |
| Creek (180) | All classes | 640 px | 0.593 | 0.540 | 0.554 | 0.538 |
| Creek (180) | All classes | 960 px | 0.563 | 0.497 | 0.530 | 0.536 |
| Creek (180) | Bags | 960 px | 0.232 | 0.174 | 0.204 | 0.194 |

*Note.* The creek-only bag row rests on 46 boxes. All values come from the same evaluation script, run on the same computer.

**Replaces** "On phone photographs the three models are within about 0.02 mAP@0.5 of each other at 960 pixels. v7 is ahead only at 640 pixels, the size it was trained at."

On the older phone photographs the four models are close. At 960 pixels v10 has the highest mAP@0.5 on the full test set (0.744) and in every class, but its lead over v8 and v9 is about 0.02 or less, which is within ordinary run-to-run variation. On the creek-only photographs v7 is ahead only at 640 pixels, the size it was trained at.

# 11. Table 13, extended with the third session

**Replaces** the sentence before Table 13, the table and its note. This keeps the number at Table 13, so Tables 14 to 20 do not move. The new title is *mAP@0.5 on Pictures from the Prototype's Camera and on the Third-Session Test Half*.

Table 13 shows the scores on pictures from the creek that the models were not trained on. The first rows are the 59 frames from the prototype's camera taken on October 28. These frames are crowded, with about 44 labelled items in each, and many of the items are very small. The remaining rows are the test half of the third session.

| Test set | Class | Image size | v7 | v8 | v9 | v10 |
|---|---|---|---|---|---|---|
| Prototype frames (59) | All classes | 640 px | 0.019 | 0.108 | – | – |
| Prototype frames (59) | All classes | 960 px | 0.056 | 0.170 | – | – |
| Prototype frames (59) | Bags | 960 px | 0.054 | 0.234 | – | – |
| Session 3 test [19] | All classes | 640 px | 0.088 | 0.184 | 0.298 | 0.300 |
| Session 3 test [19] | All classes | 960 px | 0.149 | 0.204 | 0.337 | 0.404 |
| Session 3 test [19] | Bottles | 960 px | 0.242 | 0.287 | 0.356 | 0.397 |
| Session 3 test [19] | Bags | 960 px | 0.085 | 0.185 | 0.400 | 0.445 |
| Session 3 test [19] | Food containers | 960 px | 0.120 | 0.141 | 0.255 | 0.369 |

*Note.* v9 and v10 are not scored on the 59 prototype frames because both were trained on them. Only v10 was trained on pictures from the third session; the test pictures themselves were not used by any model. The session 3 test half holds [19] labelled pictures with [361] boxes. [After the full test: update to 30 pictures.] The test shrinks each whole picture to the image size, while the prototype crops the frame to the monitored area first, so the live system sees more detail than this test.

# 12. The paragraphs after Table 13

**Replaces** the paragraph starting "On the prototype's own camera frames, v8 scored clearly higher than v7" and the paragraph starting "The Raspberry Pi therefore runs v8 as its main model".

On the 59 frames from the prototype's camera, v8 scored clearly higher than v7 (0.170 against 0.056 mAP@0.5 at 960 pixels). v8 was trained on frames from the same creek spot on another day and v7 was not. The same pattern appears in the third session: the models that had seen more pictures from this spot scored higher, and v10, the only one trained on pictures from that day, scored highest in every class, with a mAP@0.5 of 0.404 against 0.204 for v8. This is fair for a camera fixed at one spot, but it means the result may not carry over to a new site. Every model scored lower at 640 pixels than at 960 pixels on these pictures, by 0.02 to 0.10, which supports keeping the detection size at 960 pixels on the Raspberry Pi. The scores are low for every model because the pictures are crowded and hold many small, partly submerged items. The models were scored on still images, so these results do not show how the system behaves live, such as flicker or false alarms from water and shadows.

A new model is installed from the Model card in the Settings tab, as a zip file or a folder under a version name. The Raspberry Pi checks it before use and keeps the running model if the check fails, and the card goes back to the previous model in one tap. Before v10 was trained, the researchers set a rule that a new model is put on the unit only if it gains at least 0.03 mAP@0.5 on camera pictures it has not seen, or clearly improves bag recall, and runs well live. On the third-session test half v10 met the first two conditions, with a mAP@0.5 0.200 higher than v8 and a bag recall of 0.427 against 0.267. [After the live check: v10 was installed on (date) and ran at (frame rate) with a median latency of (ms). It (did / did not) produce false detections on fixed objects such as the floating barrier.] [If it passes: The Raspberry Pi therefore runs v10 as its main model, with v8 kept as the fallback.] [After the full test: confirm the mAP@0.5 and bag recall figures above.]

# 13. Table 14, TC_UNIT_01 row

**Replaces** the Actual Result cell of TC_UNIT_01:

On the same 807 test images (Table 11): v10, the reported model, precision 0.846, recall 0.672, mAP@0.5 0.744, pass. For reference, v8 0.829, 0.674, 0.728, pass; v9 0.793, 0.702, 0.730, below the 0.80 precision target.

# 14. Table 16, model column

No change to the October 31 row, which correctly says v8. On the rows for the field days, write the model that actually ran, for example "v10, 960 px". [After the live check.]

# 15. Section 4.6, Capabilities, first item

**Replaces** "Detects bottles, bags and food containers with a precision of 85.2% (v7) and 82.9% (v8, the model that ran in the field) ..."

Detects bottles, bags and food containers with a precision of 84.6% (v10, the reported model) on the frozen test set of 807 images, meeting the 80% target (Section 4.3).

# 16. Section 4.6, Limitations

**Replaces** the first three items:

Bags in the older creek photographs are rarely detected: v10 has a recall of 0.152 on 46 bag boxes. On the third-session test pictures its bag recall is 0.427. Piles with many bags will still be undercounted.

Recall is lower than precision in every class, so the count tends to be too low rather than too high. More than half of the bottles in the creek photographs are missed (a recall of 0.450).

Precision on creek images is lower than on the full test set: 68.9% against 84.6% for v10, which is in the Poor band of Table 3.

**Replaces** "No set of camera frames that v9 has not seen has been scored yet. New frames are being collected on a different day and in different light."

The third-session test pictures come from the same spot and the same day as the 27 third-session pictures v10 was trained on, so they show how v10 does at this creek, not at a new site. The test half holds [19] labelled pictures.

**Replaces** "The results come from one site and one training run for each model, so differences of a few points may be ordinary run-to-run variation."

The results come from one site and one training run for each model, so differences of a few points, such as the 0.016 mAP@0.5 between v10 and v8 on the frozen test set, may be ordinary run-to-run variation.

All other items stay as they are.

# Things to confirm before this goes in

1. **Date of the third session.** The draft says November 4, 2026. The prototype's file names show November 3 at about 10:20 PM. That is probably the Raspberry Pi clock running on UTC (6:20 AM on November 4 in Philippine time). Please confirm.
2. **The 27 + 19 split.** These are the labelled counts from the last merge report. When the other 11 test photographs are labelled, rerun `merge_dataset_v10.py` and `eval_v10.py`, send the new `eval_v10_results.csv`, and every [After the full test] bracket gets filled from it. v10 does not need retraining, because the missing photographs are all on the test side, apart from one optional training frame.
3. **The live check.** After v10 runs at the creek, give me the frame rate, the median latency and any false detections on fixed objects.

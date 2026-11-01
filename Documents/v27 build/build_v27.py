"""v27: consistency fixes on top of v26.

Usage: python build_v27.py "Garbage-Guard v26.docx" "Garbage-Guard v27.docx"
Each fix replaces one exact phrase inside a single run, so formatting is kept.
"""
import sys
import docx

FIX = [
    # framework lists five stages (Figure 1 shows 1 to 5)
    ("Processing runs in four stages on a Raspberry Pi 5.", "Processing runs in five stages on a Raspberry Pi 5."),
    # the alert fires when the count reaches the threshold (smoothed >= threshold in gg_counter.py)
    ("When the threshold is exceeded, an accumulation alert appears",
     "When the threshold is reached, an accumulation alert appears"),
    ("When the threshold is exceeded, and there is no Accumulation Alert, the system alerts",
     "When the count reaches the threshold and no Accumulation Alert is active, the system alerts"),
    ("After it has dropped to the re-arm level (20-15), the alert is removed",
     "Once the count drops to the re-arm level (15 for a threshold of 20), the alert is removed"),
    ("when the accumulation threshold is exceeded, a repeated alarm is raised",
     "when the count reaches the accumulation threshold, a repeated alarm is raised"),
    ("to know that the threshold has been exceeded.", "to know that the threshold has been reached."),
    # Nezerka et al. (2023): CNN 85.9% overall accuracy; selected features up to 92.3% (abstract)
    ("It reached an overall 85.9%, offering a path to efficient and accurate sorting.",
     "Their convolutional network reached an overall accuracy of 85.9%, and classifiers built on selected texture "
     "features reached up to 92.3%, offering a path to efficient and accurate sorting."),
    ("while Nežerka et al. (2023) demonstrate that convolutional classification is superior to hand-selected feature "
     "extraction with waste fragments in particular.",
     "while Nežerka et al. (2023) show a convolutional classifier recognizing waste fragments with 85.9% accuracy, "
     "although selected texture features scored higher on their task."),
    ("is close to the 85.9% reported by Nežerka et al. (2023) for camera-based recognition of waste fragments, "
     "although their task",
     "is close to the 85.9% overall accuracy that Nežerka et al. (2023) reported for convolutional recognition of "
     "waste fragments, although the two measures differ and their task"),
    # APA 7 uses the % symbol with numerals
    ("by 15.7 per cent over the baseline", "by 15.7% over the baseline"),
    ("on small targets by 83 per cent,", "on small targets by 83%,"),
    ("at or above 88 per cent and roughly 80 per cent accuracy", "at or above 88% and roughly 80% accuracy"),
    ("the minimum of 5 per cent charge", "the minimum of 5% charge"),
    ("up to 2560×1440 at 20 FPS", "up to 2560 x 1440 at 20 FPS"),
    # the camera uses a plain Ethernet cable; power comes from the 12 V step-up cable
    ("and not from a power over Ethernet (PoE) cable, which is used solely for data transfer.",
     "and not through power over Ethernet (PoE); its Ethernet cable carries data only."),
    # the deployed model is YOLO26n
    ("the items the YOLO26 model identified as garbage", "the items the YOLO26n model identified as garbage"),
    ("successfully captured by the YOLO26 model.", "successfully captured by the YOLO26n model."),
    # latency as the code measures it (garbageguard_live.py: t0 = frame arrival, end = after model.track)
    ("It is the time from frame capture (Start Time) to completed detection (End Time).",
     "It is the time from the moment the Raspberry Pi receives a frame (Start Time) to the moment detection and "
     "tracking finish (End Time), so camera encoding and network delay are not included."),
    # Objective 1 is graded on precision; Table 3 lists several measures
    ("Classification accuracy is computed from the confusion matrix variables",
     "The classification measures are computed from the confusion matrix variables"),
]


def main(src, dst):
    d = docx.Document(src)
    runs = [r for p in d.paragraphs for r in p.runs]
    for t in d.tables:
        for row in t.rows:
            for c in row.cells:
                runs += [r for p in c.paragraphs for r in p.runs]
    for old, new in FIX:
        hit = [r for r in runs if old in r.text]
        if len(hit) != 1:
            sys.exit("%d hits for: %s" % (len(hit), old[:70]))
        hit[0].text = hit[0].text.replace(old, new)
    d.save(dst)
    print("applied %d fixes" % len(FIX))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

"""v26: wording pass on paragraphs that read as machine-written.

Usage: python build_v26.py "Garbage-Guard v25.docx" "Garbage-Guard v26.docx"
Every replacement is checked to be no longer than the paragraph it replaces.
A bold lead-in label (e.g. "Accuracy: ") is kept as its own run.
"""
import sys
import docx

R = {}  # paragraph prefix -> new text (without the bold label)

R["Urban waterways such as rivers and creeks have an essential role"] = (
    "Urban waterways such as rivers and creeks act as natural drainage, habitat for wildlife, and channels that carry water "
    "through cities. Rapid urban growth and rising populations, however, have sharply increased solid waste generation in "
    "many countries, the Philippines among them. Tons of solid waste are produced across the country each day, and part of "
    "it reaches waterways through poor disposal practices and inadequate collection services (Development Asia, 2024; "
    "UNEP, 2023; World Bank, 2022). Creeks and rivers are also major sinks and carriers of pollution from land to sea, "
    "which makes them key sites for pollution monitoring (UNEP, 2023; Acot et al., 2022; World Bank, 2022).")

R["A major issue related to the environment that impacts urban creeks"] = (
    "A major environmental problem for urban creeks is the steady dumping of solid waste, especially plastics and other "
    "non-biodegradable items. In many urban areas, discarded garbage builds up in waterways and blocks the natural flow of "
    "water. Rivers and riverbanks hold the highest concentration of litter among the environments studied, since they act "
    "as collection points for debris (UNEP, 2023; Acot et al., 2022; Meijer et al., 2021; World Bank, 2022). Creeks and "
    "rivers in the Philippines regularly receive waste from nearby communities, which pollutes the water and degrades the "
    "environment (Meijer et al., 2021; Robles & Monjardin, 2025; Victoriano et al., 2022). The buildup also slows the "
    "current and causes flooding when drainage channels are blocked.")

R["The amount of garbage in creeks is often caused by"] = (
    "Garbage in creeks usually comes from poor disposal habits and weak waste management. Studies link river and creek "
    "pollution to poorly performing solid waste programs and low public awareness (Development Asia, 2024; UNEP, 2023; "
    "World Bank, 2022). Households dump waste from their homes and daily activities straight into drains and waterways "
    "that empty into creeks and rivers. These conditions call for better monitoring and waste management.")

R["Various studies and methods have been implemented to monitor garbage pollution"] = (
    "Several methods have been used to monitor garbage pollution and locate where it accumulates in waterways. Traditional "
    "approaches rely on manual inspection, which is slow and labor-intensive, particularly over large areas (Robles & "
    "Monjardin, 2025; Magyar et al., 2023; Victoriano et al., 2022). Advances in artificial intelligence and computer "
    "vision in recent years have opened many uses for object detection, environmental monitoring among them. You Only Look "
    "Once (YOLO) is one example of a real-time detection algorithm (Murat & Kiran, 2025; Yaseen, 2024; Kang et al., 2022). "
    "Such technologies already appear in monitoring systems, but many current garbage detection systems depend on "
    "satellite imagery or very large datasets and are not optimized for small waterways such as creeks.")

R["In order to overcome these limitations, computer vision"] = (
    "Computer vision, deep learning, and real-time monitoring together offer a way past these limitations. YOLO can "
    "process camera video streams and detect garbage automatically with good accuracy. It can also be linked to platforms "
    "that let environmental managers track the amount of garbage and respond quickly to hazards. This study proposes "
    "Garbage-Guard, which applies object detection to a camera feed to identify floating garbage in creeks, count how much "
    "has built up, and alert the people responsible once it reaches a set level.")

R["The research aligns with three UN-SDG"] = (
    "Through computer vision for urban environmental sustainability, the research supports three United "
    "Nations Sustainable Development Goals: Goal 6 (Clean Water and Sanitation), Goal 9 (Industry, Innovation and "
    "Infrastructure), and Goal 11 (Sustainable Cities and Communities) (United Nations, 2025).")

R["Utilize a custom dataset split 70/10/20"] = (
    "Use a custom dataset split 70/10/20 into training, validation and test partitions, with a frozen held-out test set, "
    "to achieve a precision of at least 80% with recall and mAP@0.5 reported alongside it.")

R["TUP Academic Community."] = (
    " The study shows TUP and the students of the Computer Engineering Technology program how a trained object detection "
    "model, deployed on an edge device and linked to a mobile app, can be a low-cost alternative to other computer "
    "technologies. It can also be a reference for similar projects.")

R["The conceptual framework of Garbage-Guard is illustrated in Figure 1"] = (
    "Figure 1 shows the conceptual framework of Garbage-Guard as an Input-Process-Output (IPO) model with a feedback path. "
    "The process column is expanded to show every system block, since these blocks are what make the system a detector "
    "rather than a frame labeling system.")

R["There are three inputs to the system."] = (
    "The system has three inputs. The first is the live feed from the network camera mounted above the creek, which "
    "supplies frames continuously rather than on a schedule. The second is the annotated dataset, divided into three "
    "classes: bottles, bags, and food containers. The third is the set of operator settings: the region of interest drawn "
    "on the water, the number of items that triggers an alert (twenty by default), and the alert preferences.")

R["The processing is done in four stages on a Raspberry Pi 5."] = (
    "Processing runs in four stages on a Raspberry Pi 5. Detection uses a YOLO26n convolutional network exported to NCNN, "
    "the format the board runs fastest. Tracking keeps one identity per object from frame to frame, so each item holds a "
    "steady labeled box instead of flickering in and out of view. Region filtering then discards any detection outside the "
    "traced water area, which keeps fixed objects along the bank out of the count. Counting takes the number of objects "
    "inside the area in each frame, smooths it as the median of the last 15 frames so that one missed or false detection "
    "does not move it, and compares the result with the threshold. Logging writes each alert and its snapshot to a local "
    "database and syncs it to the cloud.")

R["The point to emphasize is the region filter and the smoothed count"] = (
    "The region filter and the smoothed count are what turn a detector into a monitoring system. Twenty bounding boxes in "
    "one frame do not prove that twenty objects are present, since a single frame can hold a false detection or miss an "
    "object. A system that cannot exclude the bank will also report a concrete wall as garbage for as long as it stays in "
    "frame.")

R["The system has three outputs."] = (
    "The system has three outputs. The touchscreen dashboard shows the live annotated feed with bounding boxes, class "
    "labels, and the running count. When the threshold is exceeded, an accumulation alert appears on screen, sounds on the "
    "buzzer, and is sent to the Android app. Every recorded event is kept in a searchable detection history with its own "
    "snapshot, so any reading can be checked afterward.")

R["Based on the UNEP (2023) or United Nations Environment Programme"] = (
    "According to the United Nations Environment Programme (UNEP, 2023), the World Bank (2022), and Acot et al. (2022), "
    "urban waterways such as rivers and creeks are central to the lives of people in urban centers.")

R["According to the study of Chen et al. (2024), computer vision serves"] = (
    "Chen et al. (2024) describe computer vision as a foundation of intelligent systems and AI technologies. It can "
    "replace traditional sensors as the main source of input data, acting as a pair of intelligent \"eyes\" that processes "
    "data to drive unmanned operations such as autonomous driving, obstacle avoidance, and detection tracking. The study "
    "is relevant because the researchers use computer vision to detect waste in creeks in real time.")

R["According to the study of Nežerka et al. (2023)"] = (
    "Nežerka et al. (2023) addressed the improper sorting of construction and demolition waste, which carries significant "
    "environmental and economic costs, by developing a machine-learning-assisted procedure that recognizes waste "
    "fragments with an RGB camera. It reached an overall 85.9%, offering a path to efficient and accurate sorting. The "
    "study relates to the present research through its use of computer vision to detect waste.")

R["According to Kang et al. (2022), computer vision technology has enhanced"] = (
    "According to Kang et al. (2022), computer vision has improved the processing of objects in high-resolution overhead "
    "images from drones, airplanes, and satellites. It has been applied to crop analysis, economic analysis, and disaster "
    "tracking. Object detection in overhead images remains difficult, however, because of image volume, resolution, small "
    "object size, and complex backgrounds.")

R["Similarly, Murat & Kiran (2025) described"] = (
    "Murat and Kiran (2025) described how deep learning transformed object detection algorithms. YOLO is among the most "
    "popular, known for fast and accurate real-time detection. The authors reviewed YOLO versions from YOLOv1 to YOLOv11 "
    "and described the changes in architecture and performance across them.")

R["Moreover, Yaseen (2024) has examined YOLOv8"] = (
    "Yaseen (2024) examined YOLOv8 and its advances over earlier versions. It uses architectures such as CSPNet and "
    "FPN+PAN together with an anchor-free design to improve feature extraction and multi-scale detection. Evaluations on "
    "several datasets, including Microsoft COCO, show its accuracy and real-time detection capability.")

R["Expanding on these capabilities, Garcia et al. (2025)"] = (
    "Garcia et al. (2025) show how Internet of Things (IoT) technologies have improved environmental monitoring, with "
    "networks of low-cost sensors producing large volumes of real-time air quality data. Pairing these IoT systems with "
    "machine learning and deep learning makes environmental assessment as accurate as possible. IoT-based systems can "
    "monitor air quality parameters such as PM, CO₂, NO₂, ozone, and VOCs, which makes them an efficient means of "
    "thorough environmental surveillance. Integrated IoT and artificial intelligence systems show how such tools can "
    "improve environmental management decisions.")

R["One of the significant environmental issues experienced in the Philippines"] = (
    "Flooding is a significant environmental problem in the Philippines, driven by monsoon rains, local topography, and "
    "inadequate drainage. It causes heavy loss of life and property, especially in urban areas. To reduce delays in flood "
    "monitoring, Te et al. (2024) proposed a smart IoT-based flood monitoring system for an urban area that combines "
    "efficient pressure sensors, LoRaWAN, and solar power. Their review of the background shows a need to apply IoT to "
    "disasters, since efficient monitoring would improve flood response (Te et al., 2024).")

R["This technology is relevant to Garbage-Guard since"] = (
    "This technology relates to Garbage-Guard because both monitor drainage systems. Instead of water levels, however, "
    "Garbage-Guard uses computer vision to detect the amount of garbage in creeks.")

R["Fourth, the supporting pieces are set up separately."] = (
    "Fourth, the supporting pieces exist separately. Li et al. (2024) present a ‘Very small YOLO’ (VsYOLO) that "
    "identifies floating objects at 33 fps on an edge device. Nunkhaw and Miyamoto (2024) combine detection with tracking "
    "so that an item passing through the frame is counted only once. Nunkhaw et al. (2025) extend this work to urban "
    "canals in Southeast Asia and report the gap between lab and field results. Jia et al. (2023) address the shortage of "
    "public floating-litter data, and Kataoka et al. (2024) compare segmentation with bounding-box detection. Narayana et "
    "al. (2024), Zeng et al. (2024), and Te et al. (2024) show that environmental sensing on limited hardware with "
    "real-time alerting is common.")

R["The proposed Garbage-Guard prototype should auto-detect"] = (
    "The proposed Garbage-Guard prototype detects floating garbage automatically while monitoring a creek. A "
    "high-definition CCTV camera supplies real-time video, and the YOLO26 object detection algorithm classifies the "
    "floating objects as bottles, bags, or food containers. All processing runs on the Raspberry Pi 5 edge computer, as "
    "seen in Figure 26.")

R["This will be a real-time system, which will not only support"] = (
    "As a real-time system, Garbage-Guard can give local authorities information for enforcing environmental regulations "
    "and help reduce severe pollution in local waterways. It reports garbage accumulation immediately, which helps "
    "communities stay aware of conditions in the creek and manage them. This early warning supports better water quality, "
    "lower environmental health risks, and closer adherence to garbage disposal regulations.")

R["Once it has been initialized, the camera takes a real-time video"] = (
    "Once initialized, the camera records real-time video of the target water surface. The video passes to the main "
    "processing unit, a Raspberry Pi 5, which uses the YOLO26 algorithm to classify floating solid waste, keep a "
    "consistent identity for each object, exclude objects outside the monitored water area, and count the rest. Users see "
    "the annotated results in real time on the live dashboard, along with accumulation alerts and stored data. The "
    "processing unit raises an alert when the count reaches the garbage threshold (20 by default). It also stores all "
    "garbage data in the local database, including category, time, and accumulation, for later analysis. Combining the "
    "hardware (Raspberry Pi 5 and camera) with the software (YOLO26) lets local communities monitor and manage "
    "environmental hazards safely and efficiently.")

R["Detections outside the region of interest are then discarded"] = (
    "Detections outside the region of interest are discarded, so objects on the bank or a bridge are not counted. The "
    "items left in the area are tallied, and the tally is smoothed as the median of the previous 15 frames. The count "
    "therefore reflects the current amount of garbage in the area: it rises as items accumulate and falls as they drift "
    "away.")

R["In each case the marked frame and the frame count are displayed"] = (
    "In each case the annotated frame and the count are shown on the dashboard, and the procedure repeats for the next "
    "frame. Frames that fire no alert are displayed but not stored. Besides alerts, the system stores manual captures, "
    "test alerts, and mutes, along with a latency summary every 100 frames. It keeps analyzing new video while the record "
    "stays small enough for a long deployment.")

R["It does not require a separate power supply, but is powered from the USB-A output"] = (
    "The camera needs no separate power supply. It draws from the battery-backed USB-A output of the UPS HAT through the "
    "hub, so an AC mains failure does not blind the system: the camera and the Pi stay up or go down together. The cable "
    "rating is also adequate, since the camera draws no more than 0.42 A at 12 V, just over half of the cable's 0.8 A "
    "rating at 12 V.")

R["The drive has the project folder, including the exported model"] = (
    "The drive holds the project folder, including the exported model, the database, and the snapshot store, while the "
    "operating system stays on the microSD card. The point of the arrangement is separation. Garbage-Guard writes a "
    "snapshot for every recorded event and keeps appending to the database, a steady stream of small writes that is the "
    "worst case for flash wear. Placing these on the drive puts the heavy writing on the storage best suited to absorb "
    "it, and if the drive fails, the operating system survives.")

R["ByteTrack is used to track objects from one frame to another"] = (
    "ByteTrack tracks objects from frame to frame, even when they are missed at some time steps, so each object keeps a "
    "consistent label for its whole appearance in the scene.")

R["At the end of each frame the counter will be updated"] = (
    "At the end of each frame the counter is updated with the number of items inside the region, smoothed as the median "
    "of the last 15 frames so that a single missed or false detection does not move it. When the smoothed count reaches "
    "the threshold (20 by default, adjustable in the Settings tab), one alert is raised, and it re-arms once the count "
    "drops to 15. A pile hovering around the threshold therefore does not trigger repeated alerts. A displayed count of "
    "twenty means roughly twenty items in the monitored area, and the threshold is calibrated on site rather than "
    "assumed.")

R["Accumulation alerts, brief alerts that reached the threshold"] = (
    "Accumulation alerts, brief alerts that reached the threshold but were not sustained, mutes, manual captures, and test "
    "alerts are stored in a local SQLite database. Each alert and capture has a snapshot with the boxes, monitored area, "
    "and count stamped onto the image. A processing latency summary is written every 100 frames. Frames that trigger no "
    "alert are shown on the dashboard but not stored, which keeps the database small over a long deployment.")

R["In this stage, the researchers planned the physical system architecture"] = (
    "In this stage, the researchers planned the physical system architecture and the components needed for portability "
    "and continuous operation. The hardware layout uses these components:")

R["(1) The first objective focuses on Functional Suitability"] = (
    "(1) The first objective covers Functional Suitability (Classification): how accurately the YOLO26n computer vision "
    "model identifies and classifies floating solid garbage such as bottles, bags, and food containers. Classification "
    "accuracy is computed from the confusion matrix variables True Positive (TP), True Negative (TN), False Negative (FN), "
    "and False Positive (FP), using the formulas below:")

R["Accuracy: The overall correctness of predictions"] = (
    "The overall correctness of predictions, or the ratio of correct predictions to all cases examined. For object "
    "detection it is reported for completeness, with mean average precision at an overlap threshold of 0.5 (mAP@0.5) "
    "reported alongside precision and recall as the standard detection measure. The model must reach a precision of at "
    "least 80%.")

R["Average Latency: Evaluates the mean inference time"] = (
    " The mean inference time (in seconds) over a test period, used to confirm that there is no severe hardware "
    "degradation. It is the time from frame capture (Start Time) to completed detection (End Time).")

R["In order to get an accurate assessment and define the clear testing limits"] = (
    "To make the assessment accurate and set clear testing limits, this phase will be split into two perspectives: the "
    "developer side (Internal Acceptance) and the end-user side (External Acceptance).")


def set_text(p, new):
    runs = p.runs
    keep = 1 if (runs and runs[0].bold and len(runs) > 1 and p.text.startswith(runs[0].text)
                 and runs[0].text.strip().endswith((":", "."))) else 0
    body = runs[keep]
    body.text = new
    for r in runs[keep + 1:]:
        r._r.getparent().remove(r._r)


def main(src, dst):
    d = docx.Document(src)
    hits = {k: [] for k in R}
    for p in d.paragraphs:
        t = p.text
        for k in R:
            if t.startswith(k):
                hits[k].append(p)
    bad = [k for k, v in hits.items() if len(v) != 1]
    if bad:
        sys.exit("prefix not unique or missing: %r" % bad)
    old_total = new_total = 0
    for k, (p,) in hits.items():
        old = p.text
        set_text(p, R[k])
        assert "—" not in p.text, k
        if len(p.text) > len(old):
            sys.exit("longer: %s (%d > %d)" % (k, len(p.text), len(old)))
        old_total += len(old); new_total += len(p.text)
    d.save(dst)
    print("rewrote %d paragraphs, %d -> %d characters" % (len(R), old_total, new_total))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

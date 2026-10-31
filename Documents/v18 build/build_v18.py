"""Build Garbage-Guard v18 from v17: Roboflow in Chapter 3, paired V-Model text,
new Figures 44/45 images, Figure 46, signed Testing Permit page 2.

python build_v18.py IN.docx OUT.docx ASSET_DIR
ASSET_DIR holds: vmodel.png, pipeline.png, permit_p2.jpg, enclosure.png,
                 roboflow_logo.png, roboflow_annotate.png
"""
import copy
import os
import re
import sys

import docx
from docx.oxml.ns import qn
from PIL import Image

SRC, OUT, ASSETS = sys.argv[1], sys.argv[2], sys.argv[3]
A = lambda n: os.path.join(ASSETS, n)

doc = docx.Document(SRC)
body = doc.element.body
W14 = "http://schemas.microsoft.com/office/word/2010/wordml"


def paras():
    return doc.paragraphs


def find(text, start=0, exact=False):
    for i, p in enumerate(paras()):
        if i < start:
            continue
        t = p.text.strip()
        if (t == text) if exact else t.startswith(text):
            return i
    raise SystemExit(f"not found: {text!r}")


def clone(p):
    e = copy.deepcopy(getattr(p, "_p", p))
    for el in e.iter():
        for k in list(el.attrib):
            if k in (f"{{{W14}}}paraId", f"{{{W14}}}textId"):
                del el.attrib[k]
    return e


def set_runs(pel, parts):
    """parts: list of (text, italic) ; keeps pPr, drops everything else."""
    for ch in list(pel):
        if ch.tag != qn("w:pPr"):
            pel.remove(ch)
    for text, italic in parts:
        r = pel.makeelement(qn("w:r"), {})
        if italic:
            rpr = r.makeelement(qn("w:rPr"), {})
            rpr.append(rpr.makeelement(qn("w:i"), {}))
            rpr.append(rpr.makeelement(qn("w:iCs"), {}))
            r.append(rpr)
        t = r.makeelement(qn("w:t"), {})
        t.text = text
        t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
        r.append(t)
        pel.append(r)
    return pel


def new_para(template_idx, parts):
    return set_runs(clone(paras()[template_idx]), parts)


def insert_after(anchor_el, els):
    for e in els:
        anchor_el.addnext(e)
        anchor_el = e
    return anchor_el


def blip_rid(pel):
    b = pel.find(".//" + qn("a:blip"))
    return b, b.get(qn("r:embed"))


def swap_image(pel, path, keep_width=True):
    """Point the picture in pel at a new image part; keep width, fix height."""
    blip, _ = blip_rid(pel)
    rid, _img = doc.part.get_or_add_image(path)
    blip.set(qn("r:embed"), rid)
    w, h = Image.open(path).size
    ext = pel.find(".//" + qn("wp:extent"))
    cx = int(ext.get("cx"))
    cy = int(cx * h / w)
    ext.set("cy", str(cy))
    for x in pel.iter(qn("a:ext")):
        if x.getparent().tag == qn("a:xfrm"):
            x.set("cx", str(cx))
            x.set("cy", str(cy))


def set_width(pel, cx):
    ext = pel.find(".//" + qn("wp:extent"))
    ratio = int(ext.get("cy")) / int(ext.get("cx"))
    ext.set("cx", str(int(cx)))
    ext.set("cy", str(int(cx * ratio)))
    for x in pel.iter(qn("a:ext")):
        if x.getparent().tag == qn("a:xfrm"):
            x.set("cx", ext.get("cx"))
            x.set("cy", ext.get("cy"))


def unique_docpr(pel, base):
    for i, d in enumerate(pel.iter(qn("wp:docPr"))):
        d.set("id", str(base + i))
        d.set("name", f"Picture {base + i}")


# ---------------------------------------------------------------- 3.4 Roboflow
i_ch3 = find("3.4 Component Materials", exact=True)
i_yolo = find("YOLO26n Detection Model", start=i_ch3, exact=True)
i_logo_tpl = i_yolo + 1  # Ultralytics logo paragraph
i_body_tpl = i_yolo + 2

h = clone(paras()[i_yolo])
set_runs(h, [("Roboflow", False)])
# heading runs keep heading style through pStyle
logo = clone(paras()[i_logo_tpl])
swap_image(logo, A("roboflow_logo.png"))
set_width(logo, int(914400 * 2.2))
unique_docpr(logo, 900100)
b1 = new_para(i_body_tpl, [(
    "Roboflow is a web-based platform for building computer vision datasets. "
    "It provides an editor for labeling images, keeps each version of a dataset, "
    "and exports labeled images in the formats that training libraries read "
    "(Roboflow, n.d.).", False)])
b2 = new_para(i_body_tpl, [(
    "In this study it served one purpose. The bounding boxes for the three garbage "
    "classes were drawn and checked in its editor, then exported in the YOLO format "
    "for training on the researchers' laptop. Nothing from Roboflow runs on the "
    "Raspberry Pi: it is part of how the model was made, not part of the deployed "
    "unit, and its place in development is described in Section 3.5.", False)])
paras()[i_yolo]._p.addprevious(h)
h.addnext(logo)
logo.addnext(b1)
b1.addnext(b2)

# ------------------------------------------------- Android Studio wording fix
i_and = find("The Android app built in Android Studio")
p = paras()[i_and]
txt = p.text.replace(
    "The Android app built in Android Studio is coded using Kotlin, .XML, and Jetpack Compose.",
    "The Android app, built in Android Studio, is written in Kotlin, and its screens are "
    "built with Jetpack Compose rather than XML layouts.")
set_runs(p._p, [(txt, False)])

# ------------------------------------------------------------ 3.5 V-Model text
i_35 = find("3.5 Project Development", exact=True)
i_f44 = find("Figure 44", start=i_35, exact=True)
swap_image(paras()[i_f44 + 2]._p, A("vmodel.png"))
i_adapt = find("The researchers adapted the traditional V-Model", start=i_35)
tpl = i_adapt
V1 = ("The researchers followed the V-Model of the Software Development Life Cycle, "
      "adapted to a project that joins hardware, a trained detection model and two user "
      "interfaces (Oppermann & Whitfield, 2025). Figure 44 shows the adapted model. The "
      "left arm holds the verification stages, in which the requirements and designs were "
      "set down. The right arm holds the validation stages, in which each of those was "
      "tested, and the vertex holds prototyping and development, where the design became "
      "a working unit.")
V2 = ("The figure should not be read as one line running from the top left to the top "
      "right, which would make it a waterfall model folded in half. Each stage on the left "
      "is paired with the stage directly across from it, as the dashed arrows show, and the "
      "test plan for the right-hand stage was written while its left-hand partner was still "
      "being worked on. The acceptance indicators in Tables 6 to 8 came out of requirement "
      "gathering, from the same ISO/IEC 25010:2023 characteristics agreed with the adviser. "
      "The system test cases in Table 5 were drafted once the hardware layout in system "
      "design was settled, and the unit test cases in Table 4 were drafted alongside the "
      "software design of the model, the counter and the latency log. In a waterfall model, "
      "testing is planned after development ends. Here, every design decision already had a "
      "test waiting for it before the part was built.")
V3 = ("In practice the V was travelled many times, not once. The detection model has been "
      "trained nine times so far (v1 to v8, plus a v6b run), with a v9 that adds more creek "
      "photographs in training at the time of writing, and each retraining sent the work "
      "back to the vertex and up the right arm again. The new weights were scored on the "
      "held-out test partition (TC_UNIT_01), exported to NCNN and loaded on the Raspberry Pi "
      "as the default model, and the system cases that depend on detection, TC_SYS_01, "
      "TC_SYS_06 and TC_SYS_07, were repeated with them. The test partition changed as the "
      "dataset was rebuilt between the early runs, so the scores of v5 and v6b cannot be "
      "compared with those of v7. With v7 its 807 images were frozen, and any later model "
      "is measured against the same set. The v8 model, trained with about 800 photographs "
      "taken at the creek, went through the same cycle, while v7 remains the default model "
      "on the unit and the one whose scores are reported in this paper. Changes outside the model followed the same loop. "
      "When the vibration motor was removed from the unit, the alert delivery case "
      "(TC_SYS_09) was repeated with the buzzer alone, and when detection moved from the "
      "camera's sub-stream to a crop of the main stream, the detection and region cases "
      "were run again because every item now covered more pixels. Acceptance testing at the "
      "creek is the one stage meant to run a single time, on the final build.")
V4 = ("Roboflow, the platform used to label the dataset, sits at the vertex of the V and not "
      "on either arm. Annotation does not define a requirement or a design, and it does not "
      "test one; it produces the material the model is built from, much as cutting the "
      "enclosure produces the housing. Its link to the right arm runs through the dataset. "
      "The frozen test partition used in unit testing is part of the same annotated set, so "
      "a weak score in unit testing sends the work back to annotation rather than forward to "
      "system testing.")
adapt = paras()[i_adapt]._p
new = [new_para(tpl, [(t, False)]) for t in (V1, V2, V3, V4)]
insert_after(adapt, new)
adapt.getparent().remove(adapt)

# Figure 45: picture before its explanation
i_f45exp = find("Figure 45 shows how the detection model", start=i_35)
i_f45 = find("Figure 45", start=i_35, exact=True)
exp = paras()[i_f45exp]._p
img45 = paras()[i_f45 + 2]._p
swap_image(img45, A("pipeline.png"))
F45 = ("Figure 45 shows how the detection model itself was produced inside the vertex. The "
       "sequence runs from collection, through annotation in Roboflow, the dataset split, "
       "training on the researchers' laptop and evaluation on the held-out partition, to "
       "export to NCNN and deployment on the Raspberry Pi. The dashed return path is not "
       "decorative. Weak per-class results sent the work back for further collection and "
       "annotation, and the model deployed at the creek, v7, came out of the eighth training "
       "run rather than the first.")
set_runs(exp, [(F45, False)])
exp.getparent().remove(exp)
img45.addnext(exp)

# Requirement gathering: names as in the Acknowledgment
i_req = find("The researchers consulted with researchers", start=i_35)
set_runs(paras()[i_req]._p, [(
    "The researchers consulted their thesis adviser, Dr. Jonel R. Macalisang, to set the "
    "scope and title of the Garbage-Guard project, and the requirements were defined during "
    "this stage. With the guidance of Prof. Ma Ian Delos Trinos, the professor of the "
    "Project Development subject, the acceptance criteria were based on the ISO/IEC "
    "25010:2023 standard. The assessment covered three software quality characteristics:",
    False)])

# ------------------------------------------- Phase 2 text, Figure 46
i_ph2 = find("Phase 2: Prototyping and Development", start=i_35)
i_rep = i_ph2 + 1
P2 = ("The vertex is the point where the design turned into a working unit. Development "
      "began with a custom dataset of video frames and still images of floating garbage, "
      "labeled in Roboflow, and continued with training YOLO26n on a laptop fitted with an "
      "NVIDIA GeForce RTX 2070 Max-Q graphics card. Each model that passed evaluation was "
      "exported to NCNN and tried on the Raspberry Pi 5 for compatibility and speed. The "
      "tracker, the region filter, the counter, the dashboard and the Android application "
      "were built around the model while it was still being retrained, which is why the same "
      "software was tested against several versions of the detector.")
rep = paras()[i_rep]._p
set_runs(rep, [(P2, False)])

i_h4tpl = find("Requirement Gathering", start=i_35, exact=True)
i_f45 = find("Figure 45", start=i_35, exact=True)
h46 = new_para(i_h4tpl, [("Dataset Annotation and Model Training", False)])
cap_num = clone(paras()[i_f45]._p)
for t in cap_num.iter(qn("w:t")):
    t.text = "Figure 46"
for bs in cap_num.iter(qn("w:bookmarkStart")):
    bs.set(qn("w:name"), "_FigMk46")
    bs.set(qn("w:id"), "946")
for be in cap_num.iter(qn("w:bookmarkEnd")):
    be.set(qn("w:id"), "946")
cap_title = clone(paras()[i_f45 + 1]._p)
for t in cap_title.iter(qn("w:t")):
    t.text = ""
list(cap_title.iter(qn("w:t")))[0].text = "Bounding-Box Annotation in the Roboflow Annotation Editor"
img46 = clone(paras()[i_f45 + 2]._p)
swap_image(img46, A("roboflow_annotate.png"))
unique_docpr(img46, 900200)
i_note = find("Note. Adapted from", start=i_35)
note46 = new_para(i_note, [
    ("Note.", True),
    (" From “YOLO Labeling Tools,” by M. Ibrahim, 2026, Roboflow Blog "
     "(https://roboflow.com/blog/yolo-labeling-tools). The sample image is not part of the "
     "Garbage-Guard dataset.", False)])
D1 = ("Figure 46 shows the annotation editor of Roboflow, the web platform the researchers "
      "used to label the dataset (Roboflow, n.d.). The image in the figure is a sample "
      "published by Roboflow, not one of the Garbage-Guard images, but the procedure is the "
      "same. An image is opened in the editor, a tight bounding box is drawn around each "
      "object, and each box is given a class name. For this study the class list was limited "
      "to bottle, bag and food_container, in that order, so that the label numbers in the "
      "exported files match the class order the detector reports.")
D2 = ("The labeled images were exported from Roboflow in the YOLO text format, one file per "
      "image with a line for each box, and divided into the 70/10/20 training, validation and "
      "test partitions. Training did not run on Roboflow's servers. It ran locally on the "
      "laptop's RTX 2070 Max-Q, where the v7 run stopped early at epoch 93 of a planned 100, "
      "at an image size of 640 pixels and a batch size of 16, after about 1.4 hours. A run of "
      "that length made it practical to retrain whenever the evaluation exposed a weak class, "
      "which is the loop drawn in Figures 44 and 45.")
d1 = new_para(i_adapt, [(D1, False)])
d2 = new_para(i_adapt, [(D2, False)])
insert_after(rep, [h46, cap_num, cap_title, img46, note46, d1, d2])

# ------------------------------------------- Phase 3 pairing sentences
i_ph3 = find("Phase 3: Validation", start=i_35)
set_runs(paras()[i_ph3 + 1]._p, [(
    "This phase climbs the right arm of the V. Each stage tests the left-arm stage paired "
    "with it, using the cases written during that stage, and the three stages run in order "
    "from unit to acceptance for every build that reaches the Raspberry Pi.", False)])


def append_sentence(idx, sentence):
    p = paras()[idx]._p
    runs = p.findall(qn("w:r"))
    last = runs[-1]
    r = copy.deepcopy(last)
    for c in list(r):
        if c.tag not in (qn("w:rPr"),):
            r.remove(c)
    t = r.makeelement(qn("w:t"), {})
    t.text = sentence
    t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
    r.append(t)
    last.addnext(r)


i_unit = find("The initial testing on the right side of the V-Model", start=i_ph3)
append_sentence(i_unit, " Its cases, listed in Table 4, were written during software design "
                        "and were run again after every retraining of the model.")
i_sys = find("After verification of the units, the System Testing is performed", start=i_ph3)
append_sentence(i_sys, " System testing is the partner of system design, and its cases in "
                       "Table 5 were written when the hardware layout was fixed.")
i_acc = find("The testing phase is equivalent to the requirement gathering phase", start=i_ph3)
p = paras()[i_acc]
set_runs(p._p, [(p.text.replace(
    "The testing phase is equivalent to the requirement gathering phase of the adapted V-Model.",
    "Acceptance testing is the partner of requirement gathering, and its indicators were set "
    "with the adviser during that stage."), False)])


# ------------------------------------------- camera stream paragraph (out of date)
i_cam = find("The camera operates from a 12V input power source")
set_runs(paras()[i_cam]._p, [(
    "The camera operates from a 12 V input power source and not from a power over Ethernet "
    "(PoE) cable, which is used solely for data transfer. Garbage-Guard reads the camera's main stream, set to 1280 x 720 at 15 frames per second "
    "in H.264, and crops each frame to the monitored area before it reaches the model, which "
    "runs at a detection size of 960 pixels by default. Cropping keeps the detail of the main "
    "stream inside the area that matters, so a floating item covers more pixels than it did on "
    "the 640 x 360 sub-stream that earlier builds used. At 1920 x 1080 the Pi could not decode "
    "the stream fast enough and the picture fell six to seven seconds behind; at 1280 x 720 it "
    "runs about one second behind on the touchscreen. The sub-stream remains available from "
    "the Settings tab as a fallback.", False)])

# ------------------------------------------- Figure 47 enclosure drawing and installation
i_35 = find("3.5 Project Development", exact=True)
i_port = find("The prototype was made portable", start=i_35)
i_f46 = find("Figure 46", start=i_35, exact=True)
port = paras()[i_port]._p


def figure(num, title, img_path, docpr):
    i_f = find("Figure 46", start=i_35, exact=True)
    c1 = clone(paras()[i_f]._p)
    for t in c1.iter(qn("w:t")):
        t.text = f"Figure {num}"
    for bs in c1.iter(qn("w:bookmarkStart")):
        bs.set(qn("w:name"), f"_FigMk{num}")
        bs.set(qn("w:id"), str(900 + num))
    for be in c1.iter(qn("w:bookmarkEnd")):
        be.set(qn("w:id"), str(900 + num))
    c2 = clone(paras()[i_f + 1]._p)
    ts = list(c2.iter(qn("w:t")))
    for t in ts:
        t.text = ""
    ts[0].text = title
    im = clone(paras()[i_f + 2]._p)
    swap_image(im, img_path)
    unique_docpr(im, docpr)
    return [c1, c2, im]


i_body = i_port
E47 = ("Figure 47 gives the outside dimensions of the enclosure in front and side view. The "
       "front door carries the 7-inch touchscreen behind a cut-out, so the dashboard can be "
       "read and touched without opening the box, and a key lock keeps the door shut at the "
       "site. A USB case fan is fitted on each side wall, and the cable entries for the mains "
       "lead and the camera cables sit low on the side and are sealed with rubber sealant. The "
       "camera is not drawn. It is mounted outside, apart from the box, and joined to it by its "
       "cables only.")
els = figure(47, "Front and Side Views of the Garbage-Guard Enclosure with Dimensions",
             A("enclosure.png"), 900300)
els.append(new_para(i_body, [(E47, False)]))

i_h4tpl = find("Requirement Gathering", start=i_35, exact=True)
i_h5tpl = find("1. Classification Evaluation", start=i_35)
els.append(new_para(i_h4tpl, [("Assembly and Installation Procedure", False)]))
els.append(new_para(i_body, [(
    "The unit was assembled and installed in the order below. The order matters at two points: "
    "the camera has to be in its final position before the monitored area is drawn, and the "
    "threshold is set only after the dashboard count has been compared with a hand count at the "
    "site.", False)]))
STEPS_A = [
    "The Waveshare UPS HAT (E) is fixed under the Raspberry Pi 5 so that its pogo pins meet the "
    "power pads on the underside of the board, and the four 21700 cells are loaded. The Active "
    "Cooler is attached to the Pi.",
    "The Waveshare PCIe adapter is connected to the Pi's PCIe connector by its ribbon cable and "
    "the KingSpec SSD is fitted to it. The microSD card holding Raspberry Pi OS goes into the "
    "Pi's card slot.",
    "The buzzer module is wired to the 40-pin header: VCC to pin 17 (3.3 V), IN to pin 32 "
    "(GPIO12) and GND to pin 34.",
    "The board stack is fixed inside the junction box, and the touchscreen is mounted behind the "
    "cut-out in the door and connected to the Pi by micro-HDMI for video and USB for touch.",
    "The two USB case fans are mounted on the side walls. The fans and the UPS charger are "
    "plugged into the extension lead, and the cable entries are sealed with rubber sealant once "
    "the cables are through.",
]
STEPS_B = [
    "The enclosure is placed under cover at the site. Only the camera is installed outside.",
    "The camera is mounted above the water and aimed along the creek surface. Its Ethernet "
    "cable runs to the Pi's Ethernet port, and its power plug to the 5 V to 12 V step-up cable "
    "on the UPS HAT's USB-A output.",
    "The USB-to-Ethernet adapter is cabled to the Wi-Fi repeater, and the repeater is set from "
    "its setup page to join the site's wireless network or a phone hotspot.",
    "The extension lead is plugged into the mains and the Pi powers up. The launcher checks the "
    "camera, opens the dashboard full screen and starts detection; if no network is in range, "
    "the Wi-Fi setup screen appears first.",
    "With the camera in its final position, the operator presses Draw on the Monitor tab, taps "
    "points along the waterline and presses Apply. The monitored area is saved and reloaded "
    "after every restart.",
    "The threshold is calibrated on site. Over several sessions the dashboard's item count is "
    "compared with a hand count of the items visible on the water at the same moment, with the "
    "time and lighting recorded, and the Accumulation Limit in the Settings tab is set so that "
    "the alert fires at the real count wanted. The default is 20 items, re-arming at 15.",
    "A test alert is raised from the Settings tab to confirm that the dashboard, the buzzer and "
    "the Android application all respond before the unit is left running.",
]


def step(n, text):
    e = new_para(i_body, [(f"{n}.\t{text}", False)])
    ppr = e.find(qn("w:pPr"))
    for old in ppr.findall(qn("w:ind")):
        ppr.remove(old)
    ind = ppr.makeelement(qn("w:ind"), {qn("w:left"): "720", qn("w:hanging"): "360"})
    ppr.append(ind)
    tabs = ppr.makeelement(qn("w:tabs"), {})
    tabs.append(tabs.makeelement(qn("w:tab"), {qn("w:val"): "left", qn("w:pos"): "720"}))
    ppr.insert(0, tabs)
    return e


els.append(new_para(i_h5tpl, [("Assembly of the Unit", False)]))
n = 1
for t in STEPS_A:
    els.append(step(n, t)); n += 1
els.append(new_para(i_h5tpl, [("Installation at the Creek", False)]))
for t in STEPS_B:
    els.append(step(n, t)); n += 1
insert_after(port, els)

# ------------------------------------------- number the Chapter III formulas
M = "{http://schemas.openxmlformats.org/officeDocument/2006/math}"
i_35 = find("3.5 Project Development", exact=True)
i_iv = find("IV. RESULTS AND DISCUSSION", start=i_35, exact=True)
eqs = [paras()[i]._p for i in range(i_35, i_iv)
       if paras()[i]._p.find(".//" + M + "oMath") is not None]


def number_eq(pel, num):
    ppr = pel.find(qn("w:pPr"))
    for j in ppr.findall(qn("w:jc")):
        ppr.remove(j)
    tabs = ppr.makeelement(qn("w:tabs"), {})
    tabs.append(tabs.makeelement(qn("w:tab"), {qn("w:val"): "center", qn("w:pos"): "4320"}))
    tabs.append(tabs.makeelement(qn("w:tab"), {qn("w:val"): "right", qn("w:pos"): "8640"}))
    ppr.insert(0, tabs)
    def tabrun(text=None):
        r = pel.makeelement(qn("w:r"), {})
        r.append(r.makeelement(qn("w:tab"), {}))
        if text:
            t = r.makeelement(qn("w:t"), {}); t.text = text; r.append(t)
        return r
    ppr.addnext(tabrun())
    pel.append(tabrun(f"({num})"))


first = eqs[0]
kids = [k for k in first if k.tag != qn("w:pPr")]
groups, cur = [], []
for k in kids:
    cur.append(k)
    if k.tag == M + "oMath":
        groups.append(cur); cur = []
assert len(groups) == 3, f"expected three formulas in first equation line, got {len(groups)}"
new_ps = []
for g in groups:
    e = copy.deepcopy(first)
    for k in [k for k in e if k.tag != qn("w:pPr")]:
        e.remove(k)
    for k in g:
        kc = copy.deepcopy(k)
        if kc.tag == qn("w:r"):
            t = kc.find(qn("w:t"))
            if t is not None and t.text and t.text.strip() in (",", ""):
                continue
            if t is not None and t.text:
                t.text = t.text.lstrip(" ,")
        e.append(kc)
    new_ps.append(e)
insert_after(first, new_ps)
first.getparent().remove(first)
order = new_ps + eqs[1:]
nums = [1, 2, 3, 4, 5, 1, 2, 3, 4, 5]
assert len(order) == len(nums), f"found {len(order)} formula lines, expected {len(nums)}"
for pel, num in zip(order, nums):
    number_eq(pel, num)

# ------------------------------------------- List of Figures row 46
xml_rows = list(body.iter(qn("w:tr")))
row45 = None
for tr in xml_rows:
    if tr.find(".//" + qn("w:hyperlink")) is not None and \
            tr.find(".//" + qn("w:hyperlink")).get(qn("w:anchor")) == "_FigMk45":
        row45 = tr
        break
assert row45 is not None, "LoF row 45 missing"
row46 = copy.deepcopy(row45)
ts = list(row46.iter(qn("w:t")))
ts[0].text = "46"
ts[1].text = "Bounding-Box Annotation in the Roboflow Annotation Editor"
row46.find(".//" + qn("w:hyperlink")).set(qn("w:anchor"), "_FigMk46")
for it in row46.iter(qn("w:instrText")):
    it.text = " PAGEREF _FigMk46 \\h "
row45.addnext(row46)
row47 = copy.deepcopy(row46)
ts = list(row47.iter(qn("w:t")))
ts[0].text = "47"
ts[1].text = "Front and Side Views of the Garbage-Guard Enclosure with Dimensions"
row47.find(".//" + qn("w:hyperlink")).set(qn("w:anchor"), "_FigMk47")
for it in row47.iter(qn("w:instrText")):
    it.text = " PAGEREF _FigMk47 \\h "
row46.addnext(row47)

# ------------------------------------------- References
i_refs = find("VIII. REFERENCES", exact=True)
refs = [(i, p) for i, p in enumerate(paras()) if i > i_refs][:400]
i_app = find("IX. APPENDICES", start=i_refs, exact=True)
refs = [(i, p) for i, p in refs if i < i_app and p.text.strip()]


def add_ref(parts, key):
    tpl_i = refs[0][0]
    for i, p in refs:
        if p.text.strip().lower() > key.lower():
            el = new_para(tpl_i, parts)
            p._p.addprevious(el)
            return
    raise SystemExit("ref position")


add_ref([("Ibrahim, M. (2026, July 1). ", False), ("YOLO labeling tools", True),
         (". Roboflow Blog. https://roboflow.com/blog/yolo-labeling-tools", False)], "Ibrahim")
refs = [(i, p) for i, p in enumerate(paras()) if i_refs < i < find("IX. APPENDICES", exact=True)
        and p.text.strip()]
add_ref([("Roboflow. (n.d.). ", False), ("Roboflow Annotate", True),
         (" [Computer software]. Retrieved October 31, 2026, from https://roboflow.com/annotate",
          False)], "Roboflow.")

# ------------------------------------------- Appendix C page 2
i_c = find("Testing Permit", start=find("IX. APPENDICES", exact=True), exact=True)
pics = []
for p in paras()[i_c:i_c + 12]:
    if p._p.find(".//" + qn("a:blip")) is not None:
        pics.append(p._p)
assert len(pics) >= 2, "permit pictures"
blip, rid = blip_rid(pics[1])
newrid, _ = doc.part.get_or_add_image(A("permit_p2.jpg"))
blip.set(qn("r:embed"), newrid)

doc.save(OUT)
print("saved", OUT)

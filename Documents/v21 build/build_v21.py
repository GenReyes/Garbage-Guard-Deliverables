"""Build Garbage-Guard v21 from v20.

- Enclosure drawing (three cable entries) moved from 3.4 to 3.1 as Figure 27
- 3.4: power extension (Figure 43) and USB case fans (Figure 44) added
- 3.5: V-Model replaced by a V-Model and Scrum hybrid (Figure 45), text rewritten,
  Oppermann & Whitfield (2025) replaced by Samolovac (2026)
- Roboflow sample replaced by the team's own screenshots (Figures 47 and 48),
  Ibrahim (2026) removed
- All regenerated diagrams swapped in (white background, arrowheads with tails)
- Chapter V written from the Chapter 5 draft
- Figures renumbered, figure bookmarks and List of Figures rebuilt

python build_v21.py IN.docx OUT.docx FIG_DIR ASSET_DIR CH5.md
"""
import copy
import os
import re
import sys

import docx
from docx.oxml.ns import qn
from PIL import Image

SRC, OUT, FIGS, ASSETS, CH5 = sys.argv[1:6]
F = lambda n: os.path.join(FIGS, n)
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


def P(i):
    return paras()[i]._p


def clone(p):
    e = copy.deepcopy(getattr(p, "_p", p))
    for el in e.iter():
        for k in list(el.attrib):
            if k in (f"{{{W14}}}paraId", f"{{{W14}}}textId"):
                del el.attrib[k]
    for tag in ("w:bookmarkStart", "w:bookmarkEnd"):
        for b in list(e.iter(qn(tag))):
            b.getparent().remove(b)
    return e


def run(pel, text, italic=False, bold=False, highlight=False):
    r = pel.makeelement(qn("w:r"), {})
    if italic or bold or highlight:
        rpr = r.makeelement(qn("w:rPr"), {})
        if bold:
            rpr.append(rpr.makeelement(qn("w:b"), {}))
            rpr.append(rpr.makeelement(qn("w:bCs"), {}))
        if italic:
            rpr.append(rpr.makeelement(qn("w:i"), {}))
            rpr.append(rpr.makeelement(qn("w:iCs"), {}))
        if highlight:
            rpr.append(rpr.makeelement(qn("w:highlight"), {qn("w:val"): "yellow"}))
        r.append(rpr)
    t = r.makeelement(qn("w:t"), {})
    t.text = text
    t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
    r.append(t)
    pel.append(r)


def set_runs(pel, parts):
    """parts: str, or list of (text, italic) / (text, italic, bold, highlight)."""
    if isinstance(parts, str):
        parts = [(parts, False)]
    for ch in list(pel):
        if ch.tag != qn("w:pPr"):
            pel.remove(ch)
    for part in parts:
        run(pel, *part)
    return pel


def new_para(tpl, parts):
    return set_runs(clone(tpl), parts)


def insert_after(anchor, els):
    for e in els:
        anchor.addnext(e)
        anchor = e
    return anchor


def has_pic(pel):
    return pel.find(".//" + qn("a:blip")) is not None


def swap_image(pel, path, width=None):
    blip = pel.find(".//" + qn("a:blip"))
    rid, _ = doc.part.get_or_add_image(path)
    blip.set(qn("r:embed"), rid)
    w, h = Image.open(path).size
    ext = pel.find(".//" + qn("wp:extent"))
    cx = int(width or ext.get("cx"))
    cy = int(cx * h / w)
    ext.set("cx", str(cx))
    ext.set("cy", str(cy))
    for x in pel.iter(qn("a:ext")):
        if x.getparent().tag == qn("a:xfrm"):
            x.set("cx", str(cx))
            x.set("cy", str(cy))


_pic_id = [9000]


def unique_docpr(pel):
    for d in pel.iter(qn("wp:docPr")):
        _pic_id[0] += 1
        d.set("id", str(_pic_id[0]))
        d.set("name", f"Picture {_pic_id[0]}")


def set_text(i, text):
    set_runs(P(i), text)


def replace_in(i, old, new):
    p = paras()[i]
    t = p.text
    assert old in t, (i, old)
    set_runs(p._p, t.replace(old, new))


def image_after(cap_idx):
    for k in range(cap_idx + 1, cap_idx + 5):
        if has_pic(P(k)):
            return k
    raise SystemExit(f"no picture after paragraph {cap_idx}")


def remove(el):
    el.getparent().remove(el)


i_app = find("IX. APPENDICES", exact=True)
stop = P(i_app)

# ================================================================ 1. renumber figures in the main text
def newnum(n):
    if n == 42:
        return 27
    if 27 <= n <= 41:
        return n + 1
    if 43 <= n <= 45:
        return n + 2
    if n >= 46:
        return n + 3
    return n


pat = re.compile(r"(Figures? )(\d+)((?: and | to |, | or )(\d+))?")


def sub(m):
    s = f"{m.group(1)}{newnum(int(m.group(2)))}"
    if m.group(3):
        s += m.group(3)[: -len(m.group(4))] + str(newnum(int(m.group(4))))
    return s


for el in body.iterchildren():
    if el is stop:
        break
    for t in el.iter(qn("w:t")):
        if t.text and "Figure" in t.text:
            t.text = pat.sub(sub, t.text)

# ================================================================ 2. enclosure drawing -> 3.1 as Figure 27
i27 = find("Figure 27", start=find("3.4 Component Materials", exact=True), exact=True)
assert paras()[i27 + 1].text.startswith("Front and Side Views")
blk = [P(i27 + k) for k in range(4)]
assert has_pic(blk[2]) and paras()[i27 + 3].text.startswith("Figure 27 gives")
F27 = ("Figure 27 shows the physical design of the prototype: the outside dimensions of the "
       "enclosure after it was modified, in front and side view. The 7-inch touchscreen sits in a "
       "cut-out in the door, so the dashboard can be read and touched without opening the box, and "
       "the door handle with its lock is below the screen. The door is hinged on its left edge. A USB "
       "case fan is fitted on each side wall. Three cable entries are cut low on the right side wall "
       "and sealed with rubber sealant. From front to back they carry the AC mains extension cable, "
       "the Ethernet cable to the Wi-Fi extender, and the 2-in-1 Ethernet and power cable to the "
       "camera. The camera is not drawn. It is mounted outside, apart from the box, and joined to it "
       "by its cable only.")
set_runs(blk[3], F27)
swap_image(blk[2], F("GarbageGuard_Enclosure_Drawing.png"), width=5486400)
anchor = P(find("This will be a real-time system", exact=False))
for e in blk:
    remove(e)
insert_after(anchor, blk)

# ================================================================ 3. 3.4: junction box, extension, fans
i_h = find("Metal Waterproof Junction Box, Extension Lead and Case Fans", exact=True)
set_text(i_h, "Metal Waterproof Junction Box")
i42 = find("Figure 42", start=i_h, exact=True)
i_note = find("Note. Image for Shopee", start=i42)
set_runs(P(i_note), [("Note. ", True), ("Image from Shopee (n.d.).", False)])
i_house = find("The electronics are housed in a metal waterproof junction box", start=i42)
replace_in(i_house, "with openings cut for the AC mains extension lead and the camera cabling and sealed with a rubberized water sealant",
           "with three openings cut low on the right side for the AC mains extension cable, the Ethernet cable to the Wi-Fi extender and the camera cable, sealed with a rubberized water sealant")
i_dist = find("The distribution point inside the enclosure", start=i42)
remove(P(i_dist))
i_weather = find("The enclosure is not exposed to the weather", start=i42)

tplH4, tplCap, tplTitle, tplImg, tplNote, tplBody = (P(i_h), P(i42), P(i42 + 1), P(image_after(i42)),
                                                    P(i_note), P(i42 + 4))
assert paras()[i42 + 4].text.startswith("Figure 42 shows")


def figure_block(heading, num, title, img, note_parts, texts, width=None):
    els = []
    if heading:
        els.append(new_para(tplH4, heading))
    els.append(new_para(tplCap, [(f"Figure {num}", False, True, False)]))
    els.append(new_para(tplTitle, [(title, True)]))
    im = clone(tplImg)
    swap_image(im, img, width)
    unique_docpr(im)
    els.append(im)
    if note_parts:
        els.append(new_para(tplNote, note_parts))
    for t in texts:
        els.append(new_para(tplBody, t))
    return els


EXT = [
    "Figure 43 shows the Mitsushi power extension, the point where AC mains power enters the "
    "enclosure. It is rated 2500 W maximum at 10 A, 220 V and 50/60 Hz, carries BPS certification, "
    "and has three universal outlets, six USB ports and a power switch (Shopee, n.d.). The UPS "
    "charger is plugged into one of its outlets and the two case fans into two of its USB ports, so "
    "the fans need no power adapter of their own.",
    "This split matters during a power cut. The UPS HAT, the Active Cooler and the camera keep "
    "running from the battery when mains power fails, but the case fans stop, because they draw "
    "from the extension and not from the HAT. On battery the processor keeps detecting inside a "
    "closed box with only its own cooler. This was deliberate: running the fans from the battery "
    "would shorten the time the unit can keep detecting.",
]
FAN = [
    "Figure 44 shows the FUGONGDA FGD9025H9BL case fan, two of which cool the inside of the "
    "enclosure, one on each side wall (Figure 27). Each runs on 5 V DC from a USB-A plug, so it "
    "needs no separate supply, and the seller lists it in the 12 x 12 size (Shopee, n.d.). Both are "
    "plugged into the USB ports of the power extension and move air through the box past the board "
    "stack, while the Active Cooler handles the processor itself. A wire grille covers each fan on "
    "the outside of the wall.",
]
note_shopee = [("Note. ", True), ("Image from Shopee (n.d.).", False)]
els = figure_block("Mitsushi Power Extension with USB Ports", 43,
                   "Mitsushi 2500 W Power Extension with Three Universal Outlets and Six USB Ports",
                   A("power_extension.png"), note_shopee, EXT, width=3200400)
els += figure_block("FUGONGDA 5 V USB Case Fans", 44, "FUGONGDA FGD9025H9BL 5 V USB Case Fan",
                    A("usb_fan.png"), note_shopee, FAN, width=3200400)
insert_after(P(i_weather), els)

# ================================================================ 4. 3.5 V-Model and Scrum hybrid
i45 = find("Figure 45", start=find("3.5 Project Development", exact=True), exact=True)
set_runs(P(i45 + 1), [("V-Model and Scrum Hybrid Development Model", True)])
swap_image(P(image_after(i45)), F("GarbageGuard_V_Model.png"))
i_n = find("Note. Adapted from", start=i45)
assert i_n <= i45 + 4
remove(P(i_n))
i_v1 = find("The researchers followed the V-Model", start=i45)
assert paras()[i_v1 + 3].text.startswith("Roboflow, the platform")
V = [
    "The researchers followed a hybrid of the V-Model and Scrum, shown in Figure 45. In this hybrid "
    "the V-Model is kept only at the two ends of the project, for requirements and planning at the "
    "start and for integration and system testing at the end, while the work in between is done in "
    "Scrum sprints with testing inside every iteration (Samolovac, 2026). A pure V-Model did not "
    "describe what happened in this study. It assumes each stage is finished before the next one "
    "starts, while the detection model here was trained, tested and retrained many times before the "
    "system as a whole was tested at the creek.",
    "The upper left of the figure is the V-Model up-front. Requirement gathering fixed the scope, "
    "the three garbage classes and the ISO/IEC 25010:2023 targets with the adviser, and system design "
    "fixed the hardware: the camera, the Raspberry Pi 5, the power path and the enclosure. Both were "
    "settled early and once, because the parts had to be bought and the tests depend on them. Each "
    "is paired with the stage directly across from it, as the dashed arrows show. The acceptance "
    "indicators in Tables 6 to 8 were written during requirement gathering, and the system test "
    "cases in Table 5 were drafted as soon as the hardware layout was settled, long before there was "
    "a system to test.",
    "The lower part of the figure is the Scrum stage, where most of the weeks on the Gantt chart in "
    "Appendix A were spent. Every sprint went around the same three steps. Software design chose "
    "what the sprint would change: a new dataset version, the counter logic, a dashboard tab or an "
    "application screen. Development built it, which for the model meant annotation in Roboflow, "
    "training on the researchers' laptop and export to NCNN. Unit testing closed the sprint with the "
    "cases in Table 4. Each new model was scored on the frozen test partition (TC_UNIT_01), and the "
    "counter and region logic were checked, before the build went onto the Raspberry Pi. A weak "
    "result did not move up to system testing. It became the reason for the next sprint.",
    "The model went around this loop many times. It has been trained eight times so far (v1 and v3 "
    "to v8, plus a v6b run; the v2 dataset was assembled but never trained), with a v9 that adds "
    "more creek photographs in training at the time of writing, and each retraining was a sprint of "
    "its own. The test partition changed as the dataset was rebuilt between the early runs, so the "
    "scores of v5 and v6b cannot be compared with those of v7. With v7 its 807 images were frozen, "
    "and any later model is measured against the same set. The v8 model, trained with about 800 "
    "photographs taken at the creek, went through the same cycle. Changes outside the model were "
    "sprints too. When the vibration motor was removed from the unit, the alert delivery case "
    "(TC_SYS_09) was repeated with the buzzer alone, and when detection moved from the camera's "
    "sub-stream to a crop of the main stream, the detection and region cases were run again because "
    "every item now covered more pixels.",
    "The upper right of the figure is the V-Model at-end. Once a build passed unit testing, the "
    "whole system was tested together, with the camera, the dashboard, the buzzer and the Android "
    "application (Table 5), and the cases that depend on detection, TC_SYS_01, TC_SYS_06 and "
    "TC_SYS_07, were repeated whenever a new model was loaded. Acceptance testing at the creek is "
    "the one stage meant to run a single time, on the final build. The two long arrows at the sides "
    "give the direction of the work: verification runs down from the requirements into the sprints, "
    "and validation runs up from unit testing to acceptance.",
    "Roboflow, the platform used to label the dataset, belongs to the development step of the "
    "sprint and not to either end of the V. Annotation does not define a requirement and does not "
    "test one; it produces the material the model is built from. Its link to testing runs through "
    "the dataset. The frozen test partition used in unit testing is part of the same annotated set, "
    "so a weak score sends the next sprint back to annotation.",
]
tplV = P(i_v1)
old = [P(i_v1 + k) for k in range(4)]
insert_after(old[-1], [new_para(tplV, t) for t in V])
for e in old:
    remove(e)

i46e = find("Figure 46 shows how the detection model itself was produced inside the vertex")
set_text(i46e, "Figure 46 shows how the detection model itself was produced inside the development step "
         "of each sprint. The sequence runs from collection, through annotation in Roboflow, the "
         "dataset split, training on the researchers' laptop and evaluation on the held-out partition, "
         "to export to NCNN and deployment on the Raspberry Pi. The dashed return path is not "
         "decorative. Weak per-class results sent the work back for further collection and "
         "annotation, and each pass around it was one sprint.")
swap_image(P(image_after(find("Figure 46", exact=True))), F("GarbageGuard_Training_Pipeline.png"))

# phase headings and Software Design moved into the sprints
set_text(find("Phase 1: Verification (Left Side of the V)", exact=True), "Phase 1: Verification (V-Model Up-Front)")
i_p2 = find("Phase 2: Prototyping and Development (Vertex of the V)", exact=True)
set_text(i_p2, "Phase 2: Development Sprints (Scrum)")
i_sd = find("Software Design", exact=True, start=find("Phase 1: Verification", exact=False))
sd = [P(k) for k in range(i_sd, i_p2)]
assert paras()[i_sd + 1].text.startswith("The software design was developed")
set_text(i_sd + 1, "Software design was revisited at the start of every sprint, and the design was "
         "organised in three layers:")
i_p2 = find("Phase 2: Development Sprints (Scrum)", exact=True)
set_text(i_p2 + 1,
         "The sprints are where the design turned into a working unit. Development began with a custom "
         "dataset of video frames and still images of floating garbage, labeled in Roboflow, and "
         "continued with training YOLO26n on a laptop fitted with an NVIDIA GeForce RTX 2070 Max-Q "
         "graphics card. Each model that passed unit testing was exported to NCNN and tried on the "
         "Raspberry Pi 5 for compatibility and speed. The tracker, the region filter, the counter, the "
         "dashboard and the Android application were built around the model while it was still being "
         "retrained, which is why the same software was tested against several versions of the "
         "detector.")
anchor = P(i_p2 + 1)
for e in sd:
    remove(e)
insert_after(anchor, sd)

# Roboflow figures 47 and 48
i47 = find("Figure 47", exact=True, start=i_p2)
set_runs(P(i47 + 1), [("Annotating a Food Container in the Roboflow Annotation Editor", True)])
swap_image(P(image_after(i47)), A("roboflow_editor.png"))
i_n47 = find("Note. From", start=i47)
set_runs(P(i_n47), [("Note. ", True), ("Screenshot of the researchers' own Roboflow project, image 16 of "
                                       "100 in a batch of creek frames captured on October 21, 2026.", False)])
i47e = find("Figure 47 shows the annotation editor", start=i47)
set_text(i47e, "Figure 47 shows the annotation editor of Roboflow (Roboflow, n.d.) open on one of the "
         "researchers' own creek frames. A box has just been drawn around a food container, and the "
         "editor asks for the class name before the box is saved. Every object in a frame is labelled "
         "this way: a tight bounding box, then a class. For this study the class list was limited to "
         "bottle, bag and food_container, in that order, so that the label numbers in the exported "
         "files match the class order the detector reports.")
F48 = ("Figure 48 shows the same frame with all of its boxes. One frame from the creek can hold dozens "
       "of labelled items, many of them small, partly submerged or touching one another, and each one "
       "was boxed by hand. This density is why annotation took most of the time in each sprint, and why "
       "the creek photographs changed the model more than the public images did.")
tplImg47 = P(image_after(i47))
note48 = [("Note. ", True), ("Screenshot of the researchers' own Roboflow project. The box colours "
                             "distinguish the three classes.", False)]
els = [new_para(tplCap, [("Figure 48", False, True, False)]),
       new_para(tplTitle, [("A Fully Annotated Creek Frame in the Garbage-Guard Dataset", True)])]
im = clone(tplImg47)
swap_image(im, A("roboflow_full.png"))
unique_docpr(im)
els += [im, new_para(tplNote, note48), new_para(P(i47e), F48)]
insert_after(P(i47e), els)

i_lab = find("The labeled images were exported from Roboflow")
replace_in(i_lab, "which is the loop drawn in Figures 45 and 46", "which is the sprint cycle in Figure 45 and the return path in Figure 46")

# system design bullet, assembly step 5, Phase 3 text
i_b = find("Extension lead, UPS charger and two USB case fans")
set_text(i_b, "Power extension, UPS charger and two USB case fans - AC mains power reaches the UPS charger "
         "through an outlet of the Mitsushi power extension and the case fans through its USB ports; "
         "the fans stop when AC mains are lost.")
i_s5 = find("5.\tThe two USB case fans are mounted")
set_text(i_s5, "5.\tThe two USB case fans are mounted on the side walls and plugged into the USB ports of the "
         "power extension, and the UPS charger into one of its outlets. The three cable entries on the "
         "right side wall are sealed with rubber sealant once the cables are through.")
set_text(find("Phase 3: Validation (Right Side of the V)", exact=True), "Phase 3: Testing and Validation (V-Model At-End)")
set_text(find("This phase climbs the right arm of the V."),
         "This phase sets out the tests. Unit testing is described first because its cases are defined "
         "here, but in practice it closed every sprint of Phase 2. System testing and acceptance testing "
         "form the V-Model at-end and were run on the integrated unit.")
replace_in(find("The initial testing on the right side of the V-Model is the unit testing phase."),
           "The initial testing on the right side of the V-Model is the unit testing phase.",
           "Unit testing is the last step of every sprint.")

# ================================================================ 5. regenerated diagrams
for cap, png in (("Figure 1", "GarbageGuard_Conceptual_Framework.png"),
                 ("Figure 26", "GarbageGuard_System_Architecture.png"),
                 ("Figure 28", "GarbageGuard_Block_Diagram.png"),
                 ("Figure 29", "GarbageGuard_System_Flowchart.png")):
    i = find(cap, exact=True, start=find("I. INTRODUCTION", exact=True))
    swap_image(P(image_after(i)), F(png))
i_ga = find("Gantt Chart", exact=True, start=find("IX. APPENDICES", exact=True))
swap_image(P(image_after(i_ga)), F("GarbageGuard_Gantt_Chart.png"))

# ================================================================ 6. Chapter IV placeholder on fans
i_f = find("[Fans on battery: CONFIRM")
set_text(i_f, "The two case fans run from the USB ports of the power extension, so they stop when mains "
         "power fails and the unit runs on its battery.")

# ================================================================ 7. Chapter V
i_v = find("V. CONCLUSIONS", exact=True, start=find("4.6 Project Capabilities", start=find("IV. RESULTS AND DISCUSSION", exact=True)))
i_vi = find("VI. ACKNOWLEDGMENT", exact=True, start=i_v)
tplH2 = P(find("4.1 Project Description", exact=True))
tplPar = P(find("Garbage-Guard is a fixed monitoring unit"))
tplNum = P(find("1.\tThe Waveshare UPS HAT (E)"))
for k in range(i_vi - 1, i_v, -1):
    remove(P(k))

md = open(CH5, encoding="utf-8").read()
md = md[md.index("## V. CONCLUSIONS"):]
md = md.replace("following the retrain loop in the V-Model", "as a new sprint of the development model in Figure 45")
md = md.replace("The two USB case fans draw power from the extension cable", "The two USB case fans draw power from the USB ports of the power extension")


def inline(text):
    parts = []
    for tok in re.split(r"(\*\*[^*]+\*\*|\[CONFIRM:[^\]]*\])", text):
        if not tok:
            continue
        if tok.startswith("**"):
            parts.append((tok[2:-2], False, True, False))
        elif tok.startswith("[CONFIRM"):
            parts.append((tok, False, False, True))
        else:
            parts.append((tok, False))
    return parts


els = []
for line in md.splitlines()[1:]:
    line = line.strip()
    if not line:
        continue
    if line.startswith("### "):
        els.append(new_para(tplH2, line[4:]))
        continue
    m = re.match(r"(\d+)\.\s+(.*)", line)
    if m:
        els.append(new_para(tplNum, [(f"{m.group(1)}.\t", False)] + inline(m.group(2))))
    else:
        els.append(new_para(tplPar, inline(line)))
insert_after(P(i_v), els)

# ================================================================ 8. references
i_ref = find("VIII. REFERENCES", exact=True)
for key in ("Oppermann, A., & Whitfield, B. (2025", "Ibrahim, M. (2026"):
    remove(P(find(key, start=i_ref)))
i_ref = find("VIII. REFERENCES", exact=True)
i_app = find("IX. APPENDICES", exact=True)
tplRef = P(find("Roboflow. (n.d.)", start=i_ref))
sam = new_para(tplRef, [("Samolovac, J. (2026, July 10). ", False),
                        ("What is hybrid project management?", True),
                        (" Plaky. https://plaky.com/learn/project-management/hybrid-project-management/", False)])
for k in range(i_ref + 1, i_app):
    t = paras()[k].text.strip()
    if t and t.lower() > "samolovac":
        P(k).addprevious(sam)
        break

# ================================================================ 9. figure bookmarks and List of Figures
i_app = find("IX. APPENDICES", exact=True)
stop = P(i_app)
for tag in ("w:bookmarkStart",):
    for b in list(body.iter(qn(tag))):
        if re.fullmatch(r"_FigMk\d+", b.get(qn("w:name")) or ""):
            bid = b.get(qn("w:id"))
            for e in list(body.iter(qn("w:bookmarkEnd"))):
                if e.get(qn("w:id")) == bid:
                    remove(e)
            remove(b)
caps = []
for k, p in enumerate(paras()):
    if p._p is stop:
        break
    m = re.fullmatch(r"Figure (\d+)", p.text.strip())
    if m:
        caps.append((int(m.group(1)), p._p, paras()[k + 1].text.strip()))
nums = [c[0] for c in caps]
assert nums == list(range(1, len(nums) + 1)), nums
bid = 7000
for n, pel, _ in caps:
    bid += 1
    s = pel.makeelement(qn("w:bookmarkStart"), {qn("w:id"): str(bid), qn("w:name"): f"_FigMk{n}"})
    e = pel.makeelement(qn("w:bookmarkEnd"), {qn("w:id"): str(bid)})
    first_r = pel.find(qn("w:r"))
    first_r.addprevious(s)
    pel.append(e)

rows = []
for tr in body.iter(qn("w:tr")):
    h = tr.find(".//" + qn("w:hyperlink"))
    if h is not None and re.fullmatch(r"_FigMk\d+", h.get(qn("w:anchor")) or ""):
        rows.append(tr)
tpl_row = copy.deepcopy(rows[0])
after = rows[-1].getnext()
parent = rows[-1].getparent()
for r in rows:
    remove(r)
new_rows = []
for n, _, title in caps:
    tr = copy.deepcopy(tpl_row)
    tcs = tr.findall(qn("w:tc"))
    tcs[0].find(".//" + qn("w:t")).text = str(n)
    h = tr.find(".//" + qn("w:hyperlink"))
    h.set(qn("w:anchor"), f"_FigMk{n}")
    ts = list(h.iter(qn("w:t")))
    ts[0].text = title
    for t in ts[1:]:
        t.text = ""
    for it in tr.iter(qn("w:instrText")):
        it.text = f" PAGEREF _FigMk{n} \\h "
    new_rows.append(tr)
for tr in new_rows:
    after.addprevious(tr)

doc.save(OUT)
print("saved", OUT, "figures:", len(caps))

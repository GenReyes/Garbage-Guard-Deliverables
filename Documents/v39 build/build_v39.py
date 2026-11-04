"""v39: Appendix D gains Figures D9 (third independent test and image collection for model v10) and D10
(measuring the camera cable from the barangay building to the bridge), both November 4, 2026.

Usage (from the repo root):
  python "Documents/v39 build/build_v39.py" "Documents/Garbage-Guard v38.docx" "Documents/Garbage-Guard v39.docx"
"""
import copy
import os
import sys

import docx
from docx.oxml.ns import qn
from docx.shared import Inches
from docx.text.paragraph import Paragraph
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
WT = qn("w:t")
MAX_W, MAX_H = Inches(6.0), Inches(4.3)

NEW = [
    ("D9", "Third Independent Test and Image Collection for Model v10", "D9.png",
     "Figure D9 shows the fourth run, an independent test held back at the creek in Barangay 870, Pandacan, "
     "Manila. Panel (a) shows the team arriving at the bridge, (b) the team at the railing setting up, and (c) a "
     "member on the ledge beside the bridge holding the camera over the water while two others work at the "
     "railing. The session was also used to take new pictures of the creek, to be annotated for model v10. "
     "Taken on November 4, 2026."),
    ("D10", "Measuring the Camera Cable from the Barangay Building to the Bridge", "D10.png",
     "Figure D10 shows the camera cable being measured on the same day, from the barangay building to the "
     "bridge where the camera is to be mounted. Panels (a) and (b) show the cable laid out along the street. "
     "The 40 m 2-in-1 Ethernet and power cable covered the distance with about 15 m left over, so one cable is "
     "enough to link the unit in the barangay building to the camera on the bridge. Taken on November 4, 2026."),
]
INTRO_OLD = ("to three tests at creeks. Two of them, on October 14 and October 27, were independent runs at other "
             "creeks to gather pictures for annotation, and the October 21 test was held at a creek in Barangay "
             "870, Pandacan, Manila, under the permit in Appendix C.")
INTRO_NEW = ("to four tests at creeks and the measurement of the camera cable. The October 21 test was held at a "
             "creek in Barangay 870, Pandacan, Manila, under the permit in Appendix C. The other three were "
             "independent runs to gather pictures for annotation: on October 14 and October 27 at other creeks, "
             "and on November 4 back at the Barangay 870 creek.")


def full(p):
    return "".join(t.text or "" for t in p._p.iter(WT))


def one_run(p, text):
    for r in p.runs[1:]:
        r._r.getparent().remove(r._r)
    p.runs[0].text = text


def main(src, dst):
    d = docx.Document(src)
    body = d.element.body
    P = d.paragraphs
    intro = next(p for p in P if full(p).startswith("This appendix records the development and testing"))
    assert INTRO_OLD in full(intro), full(intro)
    one_run(intro, full(intro).replace(INTRO_OLD, INTRO_NEW))

    c8 = next(i for i, p in enumerate(P) if full(p).strip() == "Figure D8" and not p.style.name.lower().startswith("toc"))
    assert full(P[c8 + 4]).startswith("Appendix E"), full(P[c8 + 4])
    src_block = [P[c8 + k]._p for k in range(4)]
    anchor = src_block[3]
    nid = max(int(b.get(qn("w:id"))) for b in body.iter(qn("w:bookmarkStart"))) + 1
    did = max(int(x.get("id")) for x in body.iter(qn("wp:docPr"))) + 1
    lof = d.tables[1]
    r8 = next(r for r in lof.rows if r.cells[0].text.strip() == "D8")._tr
    lof_anchor = r8
    for num, title, img, text in NEW:
        block = [copy.deepcopy(e) for e in src_block]
        for e in block:
            anchor.addnext(e); anchor = e
        pc, pt, pi, px = (Paragraph(e, P[c8]._parent) for e in block)
        for t in block[0].iter(WT):
            t.text = ""
        pc.runs[0].text = "Figure " + num
        bs = block[0].find(qn("w:bookmarkStart")); be = block[0].find(qn("w:bookmarkEnd"))
        bs.set(qn("w:name"), "_FigMk" + num); bs.set(qn("w:id"), str(nid)); be.set(qn("w:id"), str(nid)); nid += 1
        one_run(pt, title)
        path = os.path.join(HERE, img)
        rid, _ = d.part.get_or_add_image(path)
        block[2].find(".//" + qn("a:blip")).set(qn("r:embed"), rid)
        w, h = Image.open(path).size
        cx = MAX_W; cy = MAX_W * h / w
        if cy > MAX_H:
            cy = MAX_H; cx = MAX_H * w / h
        for e in (block[2].find(".//" + qn("wp:extent")), block[2].find(".//" + qn("a:ext"))):
            e.set("cx", str(int(cx))); e.set("cy", str(int(cy)))
        dp = block[2].find(".//" + qn("wp:docPr")); dp.set("id", str(did)); dp.set("name", "Figure " + num); did += 1
        one_run(px, text)
        tr = copy.deepcopy(r8); lof_anchor.addnext(tr); lof_anchor = tr
        ts = list(tr.iter(WT)); ts[0].text = num; ts[1].text = title
        tr.find(".//" + qn("w:hyperlink")).set(qn("w:anchor"), "_FigMk" + num)
        tr.find(".//" + qn("w:instrText")).text = " PAGEREF _FigMk%s \\h " % num
    d.save(dst)
    print("added Figures D9 and D10, saved", dst)


if __name__ == "__main__":
    main(*sys.argv[1:3])

"""Build Garbage-Guard v19 from v18: Figures 41-43 replaced by the unmodified casing photo
and the enclosure drawing, old Figure 47 removed, Figures 44-46 renumbered 43-45,
straightened Testing Permit pages.

python build_v19.py IN.docx OUT.docx ASSET_DIR
ASSET_DIR holds: junction_box.png, enclosure.png, permit_p1.jpg, permit_p2.jpg
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



# ------------------------------------------- Figures 41 to 43 become casing photo + drawing
i_iv = find("IV. RESULTS AND DISCUSSION", exact=True)
i41 = find("Figure 41", exact=True)
assert paras()[i41 + 3].text.strip() == "Figure 42" and paras()[i41 + 6].text.strip() == "Figure 43"


def set_title(idx, title):
    ts = list(paras()[idx]._p.iter(qn("w:t")))
    for t in ts:
        t.text = ""
    ts[0].text = title


set_title(i41 + 1, "Metal Waterproof Junction Box Used for the Enclosure, Before Modification")
swap_image(paras()[i41 + 2]._p, A("junction_box.png"))
set_title(i41 + 4, "Front and Side Views of the Garbage-Guard Enclosure with Dimensions")
swap_image(paras()[i41 + 5]._p, A("enclosure.png"))
set_width(paras()[i41 + 5]._p, 5400000)
img41 = paras()[i41 + 2]._p
img42 = paras()[i41 + 5]._p
cap43 = [paras()[i41 + 6]._p, paras()[i41 + 7]._p, paras()[i41 + 8]._p]
i_note_tpl = find("Note. Image from The Pi Hut (n.d.-b).")
i_body_tpl = i41 + 9  # "The electronics are housed ..."
assert paras()[i_body_tpl].text.startswith("The electronics are housed")
note41 = new_para(i_note_tpl, [("Note.", True), (" Product image from the seller's listing. Source to be confirmed.", False)])
for r in note41.findall(qn("w:r")):
    rpr = r.find(qn("w:rPr"))
    if rpr is None:
        rpr = r.makeelement(qn("w:rPr"), {}); r.insert(0, rpr)
    rpr.append(rpr.makeelement(qn("w:highlight"), {qn("w:val"): "yellow"}))
F41 = ("Figure 41 shows the junction box as it was bought, before it was cut for the touchscreen, "
       "the fans and the cable entries. It is a metal box with a hinged door, a rubber seal around "
       "the door, a grounding post and a handle lock. The seller lists it as IP65. That rating "
       "applies to the unmodified box only: once the cut-outs for the screen, the fans and the "
       "cables were made, the enclosure no longer meets it, which is why the unit is kept under "
       "cover.")
F42 = ("Figure 42 gives the outside dimensions of the enclosure after it was modified, in front "
       "and side view. The 7-inch touchscreen sits in a cut-out in the door, so the dashboard can "
       "be read and touched without opening the box, and the door handle with its lock is below "
       "the screen. The door is hinged on its left edge. A USB case fan is fitted on each side "
       "wall, and the cable entries for the mains lead and the camera cables sit low on the right "
       "side and are sealed with rubber sealant. The camera is not drawn. It is mounted outside, "
       "apart from the box, and joined to it by its cables only.")
p41 = new_para(i_body_tpl + 1, [(F41, False)])
p42 = new_para(i_body_tpl + 1, [(F42, False)])
insert_after(img41, [note41, p41])
img42.addnext(p42)
for e in cap43:
    e.getparent().remove(e)

# ------------------------------------------- drop old Figure 47 from 3.5
i47 = find("Figure 47", exact=True)
i47e = find("Figure 47 gives the outside dimensions", start=i47)
assert i47e == i47 + 3, "Figure 47 block layout changed"
for e in [paras()[i47 + k]._p for k in range(4)]:
    e.getparent().remove(e)

# ------------------------------------------- renumber 44-46 -> 43-45 in the main text
MAP = {"44": "43", "45": "44", "46": "45"}
i_app = find("IX. APPENDICES", exact=True)
stop = paras()[i_app]._p
pat = re.compile(r"(Figures? )(\d+)((?: and )(\d+))?")


def sub(m):
    a = MAP.get(m.group(2), m.group(2))
    if m.group(3):
        b = MAP.get(m.group(4), m.group(4))
        return f"{m.group(1)}{a} and {b}"
    return f"{m.group(1)}{a}"


for el in body.iterchildren():
    if el is stop:
        break
    if el.tag != qn("w:p"):
        continue
    for t in el.iter(qn("w:t")):
        if t.text and "Figure" in t.text:
            t.text = pat.sub(sub, t.text)

for bs in body.iter(qn("w:bookmarkStart")):
    n = bs.get(qn("w:name"))
    if n in ("_FigMk44", "_FigMk45", "_FigMk46"):
        bs.set(qn("w:name"), "_FigMk" + MAP[n[-2:]])

# List of Figures rows
rows = {}
for tr in body.iter(qn("w:tr")):
    h = tr.find(".//" + qn("w:hyperlink"))
    if h is not None and (h.get(qn("w:anchor")) or "").startswith("_FigMk"):
        rows[h.get(qn("w:anchor"))] = tr
for k in ("_FigMk43", "_FigMk47"):
    rows[k].getparent().remove(rows[k])
for old in ("44", "45", "46"):
    tr = rows["_FigMk" + old]
    new = MAP[old]
    ts = list(tr.iter(qn("w:t")))
    ts[0].text = new
    tr.find(".//" + qn("w:hyperlink")).set(qn("w:anchor"), "_FigMk" + new)
    for it in tr.iter(qn("w:instrText")):
        it.text = it.text.replace("_FigMk" + old, "_FigMk" + new)
for k, title in (("_FigMk41", "Metal Waterproof Junction Box Used for the Enclosure, Before Modification"),
                 ("_FigMk42", "Front and Side Views of the Garbage-Guard Enclosure with Dimensions")):
    ts = list(rows[k].iter(qn("w:t")))
    ts[1].text = title
    for t in ts[2:-1]:
        if t.getparent().getparent().tag == qn("w:hyperlink"):
            t.text = ""

# ------------------------------------------- Appendix C: straightened permit pages
i_c = find("Testing Permit", start=i_app, exact=True)
pics = [p._p for p in paras()[i_c:i_c + 12] if p._p.find(".//" + qn("a:blip")) is not None]
assert len(pics) >= 2
for pel, name in ((pics[0], "permit_p1.jpg"), (pics[1], "permit_p2.jpg")):
    blip, _ = blip_rid(pel)
    rid, _ = doc.part.get_or_add_image(A(name))
    blip.set(qn("r:embed"), rid)

doc.save(OUT)
print("saved", OUT)

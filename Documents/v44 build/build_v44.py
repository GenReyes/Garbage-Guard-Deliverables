"""v44: new Appendix D, Deployment Permit, after the Testing Permit (Appendix C): pages 1 and 2 of the November 6,
2026 pilot deployment letter and, as its third page, page 2 of the Testing Permit (the signed noted-by and
approved-by page). Old Appendices D to H become E to I, with their figures (E1-E10, H1-H21) and listings (I1-I10).
The Chapter IV permit note names both permits.
Appendix H: garbageguard_live.py, gg_web.py, dashboard.html and GgRepository.kt are rebuilt from the source of
November 5, 2026 (model upload, five-column Settings), keeping the explanatory comments (listings.json, merged
with git merge-file onto the new source) and the syntax colouring (colors.json, from v38 build/make_colors.py).

Usage (from the repo root):
  python "Documents/v44 build/build_v44.py" "Documents/Garbage-Guard v43.docx" "Documents/Garbage-Guard v44.docx"
"""
import copy
import io
import json
import os
import re
import sys

import docx
import docx.text.paragraph
from docx.enum.text import WD_ALIGN_PARAGRAPH as AL
from docx.shared import Inches, Pt
from PIL import Image
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
WT = qn("w:t")


def full(p):
    return "".join(t.text or "" for t in p._p.iter(WT))


SHIFT = {"D": "E", "E": "F", "F": "G", "G": "H", "H": "I"}
FSHIFT = {"D": "E", "G": "H", "H": "I"}


def _edit(p, edits):
    """edits: {character index in the paragraph text: new character}. Same length, so run formatting stays."""
    i = 0
    for t in p._p.iter(WT):
        s = t.text or ""
        if any(i <= k < i + len(s) for k in edits):
            t.text = "".join(edits.get(i + j, ch) for j, ch in enumerate(s))
        i += len(s)


def reletter(d):
    """A new Appendix D (Deployment Permit) follows Appendix C, so old D to H become E to I, with their figure
    numbers (D1-D10 to E1-E10, G1-G21 to H1-H21) and listings (H1-H10 to I1-I10)."""
    pat_app = re.compile(r"(?<![A-Za-z0-9_.])Appendix ([D-H])(?![A-Za-z0-9])")
    pat_tok = re.compile(r"(?<![A-Za-z0-9_.])([DGH])(\d{1,2})(?![A-Za-z0-9])")
    paras = list(d.element.body.iter(qn("w:p")))
    n = 0
    for el in paras:
        p = docx.text.paragraph.Paragraph(el, None)
        ts = [t.text or "" for t in el.iter(WT)]
        if len(ts) >= 2 and re.match(r"^ *\d+ $", ts[0]):
            continue                               # a numbered code line of Appendix H
        text = "".join(ts)
        edits = {}
        for m in pat_app.finditer(text):
            edits[m.start(1)] = SHIFT[m.group(1)]
        for m in pat_tok.finditer(text):
            edits[m.start(1)] = FSHIFT[m.group(1)]
        if edits:
            _edit(p, edits); n += 1
    for e in d.element.body.iter(qn("w:instrText")):
        m = re.match(r"^Appendix ([D-H])\. ", e.text or "")
        if m:
            e.text = "Appendix " + SHIFT[m.group(1)] + e.text[len("Appendix X"):]
    print("relettered", n, "paragraphs")


def permits(d):
    P = d.paragraphs
    c = next(i for i, p in enumerate(P) if full(p) == "Appendix C" and not p.style.name.lower().startswith("toc"))
    assert full(P[c + 1]) == "Testing Permit" and full(P[c + 5]) == "Appendix E", [full(P[c + k]) for k in range(6)]
    head, title, blank, page1, page2 = (P[c + k]._p for k in range(5))
    page2_blip = page2.find(".//" + qn("a:blip")).get(qn("r:embed"))
    did = max(int(x.get("id")) for x in d.element.body.iter(qn("wp:docPr"))) + 1
    nid = max(int(b.get(qn("w:id"))) for b in d.element.body.iter(qn("w:bookmarkStart"))) + 1
    h2, t2, b2 = copy.deepcopy(head), copy.deepcopy(title), copy.deepcopy(blank)
    for e in h2.iter(qn("w:instrText")):
        if e.text == "Appendix C. Testing Permit":
            e.text = "Appendix D. Deployment Permit"
    bs = h2.find(qn("w:bookmarkStart")); be = h2.find(qn("w:bookmarkEnd"))
    bs.set(qn("w:name"), "_Toc244756299"); bs.set(qn("w:id"), str(nid)); be.set(qn("w:id"), str(nid))
    for t in h2.iter(WT):
        if t.text == "Appendix C":
            t.text = "Appendix D"
    for t in t2.iter(WT):
        if t.text == "Testing Permit":
            t.text = "Deployment Permit"
    anchor = page2
    for el in (h2, t2, b2):
        anchor.addnext(el); anchor = el
    for img, maxh in (("deployment_permit_p1.jpg", 6981109), ("deployment_permit_p2.jpg", 7218959), (None, None)):
        el = copy.deepcopy(page2 if img else page2)
        if img == "deployment_permit_p1.jpg":
            el = copy.deepcopy(page1)             # same paragraph as the Testing Permit page under its heading
        for br in el.findall(".//" + qn("w:br")):
            if img:
                br.getparent().remove(br)
        blip = el.find(".//" + qn("a:blip"))
        if img:
            path = os.path.join(HERE, img)
            rid, _ = d.part.get_or_add_image(path)
            blip.set(qn("r:embed"), rid)
            w, h = Image.open(path).size
            cy = maxh; cx = int(cy * w / h)
            if cx > 5651169:
                cx = 5651169; cy = int(cx * h / w)
            for e in (el.find(".//" + qn("wp:extent")), el.find(".//" + qn("a:ext"))):
                e.set("cx", str(cx)); e.set("cy", str(cy))
        else:
            blip.set(qn("r:embed"), page2_blip)   # page 2 of the Testing Permit as the third page
        dp = el.find(".//" + qn("wp:docPr")); dp.set("id", str(did)); dp.set("name", "Deployment Permit"); did += 1
        anchor.addnext(el); anchor = el


def permit_note(d):
    p = next(p for p in d.paragraphs if "CONFIRM: the permit in Appendix C covers October 21" in full(p))
    old = ("CONFIRM: the permit in Appendix C covers October 21, 2026, 1:00 to 3:00 PM only. The session reported "
           "below took place on October 31, 2026, and the field week will be on later dates, so the permit or a "
           "second letter has to cover them.")
    new = ("CONFIRM: Appendix C holds the testing permit for October 21, 2026, 1:00 to 3:00 PM, and Appendix D the "
           "deployment permit for November 6, 7 and 9, 2026, 10:00 AM to 4:00 PM. The October 31 session reported "
           "below falls outside both.")
    assert len(p.runs) == 1 and old in p.runs[0].text
    p.runs[0].text = p.runs[0].text.replace(old, new)


def listings(d):
    code = json.load(open(os.path.join(HERE, "listings.json"), encoding="utf-8"))
    colors = json.load(open(os.path.join(HERE, "colors.json"), encoding="utf-8"))
    intro = next(p for p in d.paragraphs if full(p).startswith("Listings in this appendix are from"))
    assert len(intro.runs) == 1 and "as of November 4, 2026" in intro.runs[0].text
    intro.runs[0].text = intro.runs[0].text.replace("as of November 4, 2026", "as of November 5, 2026")
    P = d.paragraphs
    heads = [i for i, p in enumerate(P) if re.match(r"Listing H\d+\. ", full(p)) and i > 1000]
    assert len(heads) == 10, heads
    for h in reversed(heads):
        P = d.paragraphs
        name = full(P[h]).split(". ", 1)[1].split(":")[0]
        if name not in code:
            continue
        lines, rows = code[name], colors[name]
        old = []
        for p in P[h + 1:]:
            if not re.match(r"^ *\d+( |$)", full(p)) or re.match(r"Listing H\d+\. ", full(p)):
                break
            old.append(p)
        tmpl = next(p for p in old if len(p._p.findall(qn("w:r"))) >= 2)._p
        width = len(list(tmpl.iter(WT))[0].text) - 1
        anchor = old[-1]._p
        for n, (line, runs) in enumerate(zip(lines, rows), 1):
            el = copy.deepcopy(tmpl)
            rs = el.findall(qn("w:r"))
            for r in rs[2:]:
                el.remove(r)
            num, base = rs[0], rs[1]
            t = num.find(WT); t.text = str(n).rjust(width) + " "
            t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
            prev = base
            for text, col, it in runs:
                r = copy.deepcopy(base)
                rpr = r.find(qn("w:rPr"))
                if rpr is None:
                    rpr = OxmlElement("w:rPr"); r.insert(0, rpr)
                for tag in ("w:color", "w:i"):
                    for e in rpr.findall(qn(tag)):
                        rpr.remove(e)
                if it:
                    rpr.append(OxmlElement("w:i"))
                if col:
                    c = OxmlElement("w:color"); c.set(qn("w:val"), col); rpr.append(c)
                tt = r.find(WT); tt.text = text
                tt.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
                prev.addnext(r); prev = r
            el.remove(base)
            anchor.addprevious(el)
        for p in old:
            p._p.getparent().remove(p._p)
        print("Listing", name, len(old), "->", len(lines), "lines")

TITLE = ("GARBAGE-GUARD: DEVELOPMENT OF COMPUTER VISION FOR CREEKS GARBAGE DETECTION USING "
         "CONVOLUTIONAL NEURAL NETWORK")
STUDENTS = "Brian Jay Celeste, Genesis Walter D. Reyes II, Jade C. Gunayan, King Perth G. Pepito and Rodel S. Banogbanog"
DEGREE = "Bachelor of Engineering Technology major in Computer Engineering Technology"


def _par(cell, text="", bold=False, size=11, align=None, first=False, before=0, after=0):
    p = cell.paragraphs[0] if first else cell.add_paragraph()
    p.paragraph_format.space_before = Pt(before); p.paragraph_format.space_after = Pt(after)
    p.paragraph_format.line_spacing = 1.0; p.paragraph_format.first_line_indent = 0
    if align is not None:
        p.alignment = align
    if text:
        r = p.add_run(text); r.bold = bold; r.font.size = Pt(size)
    return p


def approval(d):
    """Approval sheet from the CIT template: heading with both logos, the statement, and blank signature lines."""
    body = d.element.body
    P = d.paragraphs
    i = next(k for k, p in enumerate(P) if full(p) == "APPROVAL SHEET")
    head, ph = P[i], P[i + 1]
    if not full(ph).startswith("[Insert the scanned, signed approval sheet"):
        print("approval sheet already filled in; kept as it is")
        return
    # the heading paragraph keeps the page break and the table-of-contents entry; its text moves into the frame
    for t in head._p.iter(WT):
        t.text = ""
    head.paragraph_format.space_after = Pt(0); head.paragraph_format.line_spacing = 1.0
    for r in head.runs:
        r.font.size = Pt(2)
    t = d.add_table(rows=8, cols=6)
    t.autofit = False
    tblPr = t._tbl.tblPr
    b = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right"):
        e = OxmlElement("w:" + edge); e.set(qn("w:val"), "single"); e.set(qn("w:sz"), "6"); e.set(qn("w:color"), "000000"); b.append(e)
    for edge in ("insideH", "insideV"):
        e = OxmlElement("w:" + edge); e.set(qn("w:val"), "nil"); b.append(e)
    tblPr.append(b)
    for row in t.rows:
        for c in row.cells:
            c.width = Inches(1.0)
        row._tr.get_or_add_trPr().append(OxmlElement("w:cantSplit"))
    R = t.rows
    # header: logos and college
    R[0].cells[0].merge(R[0].cells[0])
    left, mid, right = R[0].cells[0], R[0].cells[1].merge(R[0].cells[4]), R[0].cells[5]
    for cell, img in ((left, "logo_tup.png"), (right, "logo_cit.png")):
        p = _par(cell, first=True, align=AL.CENTER, before=8)
        p.add_run().add_picture(os.path.join(HERE, img), width=Inches(0.8))
    _par(mid, "Technological University of the Philippines", size=11, align=AL.CENTER, first=True, before=10)
    _par(mid, "COLLEGE OF INDUSTRIAL TECHNOLOGY", bold=True, size=12, align=AL.CENTER)
    _par(mid, "Ayala Boulevard, Ermita, Manila 1000", size=10, align=AL.CENTER)
    _par(mid, "cit@tup.edu.ph", size=10, align=AL.CENTER)
    full_row = lambda r: r.cells[0].merge(r.cells[5])
    c = full_row(R[1]); _par(c, "APPROVAL SHEET", bold=True, size=12, align=AL.CENTER, first=True, before=14, after=8)
    c = full_row(R[2])
    p = _par(c, first=True, align=AL.LEFT, before=4, after=10)
    p.paragraph_format.first_line_indent = Inches(0.5); p.paragraph_format.line_spacing = 1.5
    for txt, bold in (("The capstone study titled ", False), ("\u201c" + TITLE + "\u201d", True), (", prepared and submitted by ", False),
                      (STUDENTS, True), (", in partial fulfillment of the requirements for the degree ", False),
                      (DEGREE, True), (", is hereby approved and accepted.", False)):
        r = p.add_run(txt); r.bold = bold; r.font.size = Pt(11)

    def sig(cell, role, first=True, date=False):
        _par(cell, "______________________________", size=11, align=AL.CENTER, first=first, before=18)
        _par(cell, role, size=11, align=AL.CENTER)
        if date:
            _par(cell, "Date: ____________________", size=11, align=AL.CENTER, before=2)

    sig(R[3].cells[3].merge(R[3].cells[5]), "Adviser")
    sig(R[4].cells[1].merge(R[4].cells[4]), "Chairperson")
    sig(R[5].cells[0].merge(R[5].cells[2]), "Member"); sig(R[5].cells[3].merge(R[5].cells[5]), "Member")
    sig(R[6].cells[0].merge(R[6].cells[2]), "Member"); sig(R[6].cells[3].merge(R[6].cells[5]), "Member")
    sig(R[7].cells[3].merge(R[7].cells[5]), "Dean, College of Industrial Technology", date=True)
    _par(R[7].cells[0], "", first=True, after=14)
    ph._p.addprevious(t._tbl)
    ph._p.getparent().remove(ph._p)

def replace_text(p, old, new):
    t = full(p)
    assert old in t, (old, t[:120])
    for r in p.runs[1:]:
        r._r.getparent().remove(r._r)
    p.runs[0].text = t.replace(old, new, 1)


def swap_blip(d, par, path):
    rid, _ = d.part.get_or_add_image(path)
    par._p.find(".//" + qn("a:blip")).set(qn("r:embed"), rid)


def settings(d):
    """Figure G9 and Chapter IV Figure 57: the Settings tab at 1024 x 600 with five columns and the Model card."""
    P = d.paragraphs
    for cap, k0, img in (("Figure G9", 1000, "G9_settings.jpg"), ("Figure 57", 0, "C57_settings.jpg")):
        i = next(k for k, p in enumerate(P) if full(p) == cap and k > k0 and not p.style.name.lower().startswith("toc"))
        par = next(P[j] for j in range(i, i + 4) if P[j]._p.find(".//" + qn("a:blip")) is not None)
        swap_blip(d, par, os.path.join(HERE, img))
    P = d.paragraphs
    i = next(k for k, p in enumerate(P) if full(p).startswith("Figure G9 shows the Settings tab."))
    set_all(P[i], "Figure G9 shows the Settings tab, laid out in five columns on the touchscreen. The Screen saver setting "
            "determines when the touchscreen turns off or dims. Power sets the battery shutdown level and shows the voltage "
            "of each battery cell. The Buzzer setting determines the volume and the events that sound. The Camera card "
            "selects the camera stream and the detection size, and its Box labels row chooses what is written beside each "
            "detection box: the track ID, the class name, the confidence, or any mix of the three. With only the confidence "
            "selected, a box reads \u201c50%.\u201d Quiet hours, a time window during which the buzzer is disabled, sit below "
            "the Camera card, with the times shown greyed out while the setting is off. The Model card shows the model that "
            "is running, goes back to the previous one with a single button, and installs a new one from a zip file or a "
            "folder under a version name.")
    p = next(p for p in P if full(p).startswith("The Raspberry Pi therefore runs v8 as its main model"))
    replace_text(p, "All three can be selected on the Model card in the Settings tab.",
                 "A new model is installed from the Model card in the Settings tab, as a zip file or a folder under a version "
                 "name. The Pi checks it before use, keeps the running model if the check fails, and the card goes back to the "
                 "previous model in one tap.")
    p = next(p for p in P if full(p).startswith("The dashboard is a local web page served by the Raspberry Pi"))
    replace_text(p, "camera stream, box labels and model options.", "camera stream, box labels and the model upload.")


def set_all(p, text):
    for r in p.runs[1:]:
        r._r.getparent().remove(r._r)
    p.runs[0].text = text


def app_settings(d):
    """Figure G17 from the App 15 emulator shots, when the folder 'app shots' holds them."""
    folder = os.path.join(HERE, "app shots")
    files = [os.path.join(folder, f) for f in ("g17a.png", "g17b.png", "g17c.png")]
    if not all(os.path.exists(f) for f in files):
        print("app shots missing: Figure G17 keeps its App 14 screenshots")
        return
    P = d.paragraphs
    i = next(k for k, p in enumerate(P) if full(p) == "Figure G17" and k > 1000)
    tbl = P[i + 1]._p.getnext()
    assert tbl.tag == qn("w:tbl")
    blips = list(tbl.iter(qn("a:blip")))
    assert len(blips) == 3
    for blip, path in zip(blips, files):
        im = Image.open(path).convert("RGB")
        buf = io.BytesIO(); im.save(buf, "JPEG", quality=90); buf.seek(0)
        rid, _ = d.part.get_or_add_image(buf)
        blip.set(qn("r:embed"), rid)
        w, h = im.size
        drawing = blip
        while drawing.tag != qn("w:drawing"):
            drawing = drawing.getparent()
        ext = drawing.find(".//" + qn("wp:extent")); a = drawing.find(".//" + qn("a:ext"))
        cx = int(ext.get("cx"))
        for e in (ext, a):
            e.set("cx", str(cx)); e.set("cy", str(int(cx * h / w)))
    p = next(p for p in d.paragraphs if full(p).startswith("Figure G17 shows the first part of the Settings"))
    set_all(p, "Figure G17 shows the first part of the Settings screen. In (a) the user adjusts the detection confidence and "
            "the accumulation limit, sends a test alert, chooses the box labels the Pi draws (track ID, class name and "
            "confidence), and, on the Model card, sees the running model, goes back to the previous one or uploads a new "
            "model as a zip file under a version name. In (b) the user draws the monitored area and sets the screen saver of "
            "the Pi. In (c) the user sets the automatic shutdown on low battery, the quiet hours and the buzzer of the Pi, "
            "including the events that sound and the repeating alarm.")


def main(src, dst):
    d = docx.Document(src)
    approval(d)
    listings(d)
    settings(d)
    app_settings(d)
    reletter(d)
    permits(d)
    permit_note(d)
    d.save(dst)
    print("saved", dst)


if __name__ == "__main__":
    main(*sys.argv[1:3])

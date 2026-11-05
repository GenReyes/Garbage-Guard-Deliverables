"""v47: approval sheet (page ii) completed and evened out.

Adviser and Dean names added, the empty fourth Member removed, and the signature area rebuilt
as one borderless table so the blocks sit at even vertical steps: Adviser on the right,
Chairperson centred, two Members side by side, the third Member centred, then the Dean on the
right with a blank Date line. Names in bold capitals above each role, as in the CIT template.

Usage: python build_v47.py "Garbage-Guard v46.docx" "Garbage-Guard v47.docx" [step_pt]
"""
import sys
import docx
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt, Inches
from docx.text.paragraph import Paragraph

src, dst = sys.argv[1:3]
STEP = Pt(float(sys.argv[3]) if len(sys.argv) > 3 else 30)
FONT = "Times New Roman"

ADVISER = "JONEL R. MACALISANG"
CHAIR = "TRISTAN R. MOPAS"
MEMBERS = ["MARC ARDIE V. ARDIENTE", "MA. IAN P. DE LOS TRINOS", "MINABELLE D. VILLAFUERTE"]
DEAN = "GHAZALI R. SISON"

d = docx.Document(src)
body = d.element.body
P = d.paragraphs
i_stmt = next(i for i, p in enumerate(P) if p.text.strip().startswith("The capstone study titled"))
i_toc = next(i for i, p in enumerate(P) if p.text.strip() == "TABLE OF CONTENTS")
kids = list(body)
lo, hi = kids.index(P[i_stmt]._p), kids.index(P[i_toc]._p)
old = kids[lo + 1:hi]
names_seen = "".join("".join(t.text or "" for t in e.iter(qn("w:t"))) for e in old)
assert CHAIR in names_seen and "Chairperson" in names_seen and "Dean, College" in names_seen, names_seen[:200]
for e in old:                       # signature tables and spacer paragraphs of v43 to v46
    body.remove(e)
anchor = P[i_toc]._p


def fmt(p, align, before=0):
    pf = p.paragraph_format
    pf.alignment = align
    pf.space_before, pf.space_after = before, Pt(0)
    pf.line_spacing_rule = WD_LINE_SPACING.SINGLE
    pf.first_line_indent = Inches(0)
    pf.keep_with_next = True


def run(p, text, bold=False):
    r = p.add_run(text)
    r.bold = bold
    r.font.size = Pt(12)
    r.font.name = FONT
    r._r.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), FONT)


def block(cell, name, role, align, extra=None):
    p = cell.paragraphs[0]
    fmt(p, align, STEP)
    run(p, name, bold=True)
    q = cell.add_paragraph(); fmt(q, align); run(q, role)
    if extra:
        r = cell.add_paragraph(); fmt(r, align, Pt(6)); run(r, extra)


t = d.add_table(rows=5, cols=2)
anchor.addprevious(t._tbl)
t.alignment = WD_TABLE_ALIGNMENT.CENTER
t.autofit = False
b = OxmlElement("w:tblBorders")
for side in ("top", "left", "bottom", "right", "insideH", "insideV"):
    e = OxmlElement("w:" + side); e.set(qn("w:val"), "nil"); b.append(e)
t._tbl.tblPr.append(b)
for row in t.rows:
    trPr = row._tr.get_or_add_trPr()
    cs = OxmlElement("w:cantSplit"); trPr.append(cs)
    for c in row.cells:
        c.width = Inches(3.0)
        fmt(c.paragraphs[0], WD_ALIGN_PARAGRAPH.CENTER)

C, L, R = WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER
block(t.rows[0].cells[1], ADVISER, "Adviser", C)
chair = t.rows[1].cells[0].merge(t.rows[1].cells[1]); block(chair, CHAIR, "Chairperson", C)
block(t.rows[2].cells[0], MEMBERS[0], "Member", C)
block(t.rows[2].cells[1], MEMBERS[1], "Member", C)
third = t.rows[3].cells[0].merge(t.rows[3].cells[1]); block(third, MEMBERS[2], "Member", C)
block(t.rows[4].cells[1], DEAN, "Dean, College of Industrial Technology", C, "Date: " + "_" * 22)
for row in (t.rows[0], t.rows[4]):          # empty left cells keep the step height
    fmt(row.cells[0].paragraphs[0], C, STEP)

d.save(dst)
print("approval sheet v47: adviser, dean, three members, step", STEP.pt, "pt | saved", dst)

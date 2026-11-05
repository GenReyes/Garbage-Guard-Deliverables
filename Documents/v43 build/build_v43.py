"""v43: the approval sheet laid out as in the CIT Capstone Study Guidelines (1.2 Approval Sheet).

Title, students and program come from the title page. The adviser, chairperson, members and
dean are left as blank signature lines for the names to be typed in later. The two logo
spots are marked until the TUP and CIT logo files are supplied.

Usage: python build_v43.py "Garbage-Guard v42.docx" "Garbage-Guard v43.docx"
"""
import copy
import sys
import docx
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt, Inches

src, dst = sys.argv[1:3]
d = docx.Document(src)
P = d.paragraphs

i_head = next(i for i, p in enumerate(P) if p.text.strip() == "APPROVAL SHEET")
ph = P[i_head + 1]
assert ph.text.strip().startswith("[Insert the scanned, signed approval sheet"), ph.text[:60]
assert P[i_head + 2].text.strip() == "TABLE OF CONTENTS"

title = (P[0].text.strip() + " " + P[1].text.strip()).strip()
assert title.startswith("GARBAGE-GUARD"), title
i_by = next(i for i, p in enumerate(P[:30]) if p.text.strip() == "by")
students = [p.text.strip() for p in P[i_by + 1:i_by + 12] if p.text.strip()][:5]
assert len(students) == 5 and students[0] == "BRIAN JAY CELESTE", students
program = next(p.text.strip() for p in P[:30] if p.text.strip().startswith("Bachelor of"))

FONT = "Times New Roman"
BLANK = "_" * 30


def fmt(p, align, before=0, after=0, line=1.0):
    pf = p.paragraph_format
    pf.alignment = align
    pf.space_before, pf.space_after = Pt(before), Pt(after)
    pf.line_spacing_rule = WD_LINE_SPACING.MULTIPLE
    pf.line_spacing = line
    pf.first_line_indent = Inches(0)


def run(p, text, bold=False, size=12):
    r = p.add_run(text)
    r.bold = bold
    r.font.size = Pt(size)
    r.font.name = FONT
    r._r.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), FONT)
    return r


def no_borders(t):
    tblPr = t._tbl.tblPr
    b = OxmlElement("w:tblBorders")
    for side in ("top", "left", "bottom", "right", "insideH", "insideV"):
        e = OxmlElement("w:" + side)
        e.set(qn("w:val"), "nil")
        b.append(e)
    tblPr.append(b)


def cell_par(cell, align):
    p = cell.paragraphs[0]
    fmt(p, align)
    return p


def signature(cell, role, align=WD_ALIGN_PARAGRAPH.CENTER):
    p = cell_par(cell, align)
    run(p, BLANK)
    p2 = cell.add_paragraph()
    fmt(p2, align)
    run(p2, role)


# everything is inserted before the placeholder, which is then removed
anchor = ph._p
new = []


def add_p(align, before=0, after=0, line=1.0):
    el = OxmlElement("w:p")
    anchor.addprevious(el)
    p = docx.text.paragraph.Paragraph(el, ph._parent)
    fmt(p, align, before, after, line)
    new.append(p)
    return p


def add_table(rows, cols, widths):
    t = d.add_table(rows=rows, cols=cols)
    anchor.addprevious(t._tbl)
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    no_borders(t)
    for row in t.rows:
        for c, w in zip(row.cells, widths):
            c.width = Inches(w)
    return t


# 1. college heading with the two logo spots, above the existing "APPROVAL SHEET" heading
#    (which carries the TOC entry); its page break moves to a 1 pt paragraph above the table
head = P[i_head]
hpr = head._p.find(qn("w:pPr"))
hpr.remove(hpr.find(qn("w:pageBreakBefore")))
fmt(head, WD_ALIGN_PARAGRAPH.CENTER, before=18, after=18)
anchor = head._p
top = add_p(WD_ALIGN_PARAGRAPH.CENTER)
top.paragraph_format.page_break_before = True
tr = top.add_run(""); tr.font.size = Pt(1)
top._p.get_or_add_pPr().append(OxmlElement("w:rPr"))
top._p.pPr.find(qn("w:rPr")).append(OxmlElement("w:sz")); top._p.pPr.find(qn("w:rPr"))[0].set(qn("w:val"), "2")
hdr = add_table(1, 3, (1.1, 4.0, 1.1))
lt, mid, rt = hdr.rows[0].cells
for c, label in ((lt, "[TUP logo]"), (rt, "[CIT logo]")):
    p = cell_par(c, WD_ALIGN_PARAGRAPH.CENTER)
    r = run(p, label, size=9)
    rpr = r._r.get_or_add_rPr()
    hl = OxmlElement("w:highlight")
    hl.set(qn("w:val"), "yellow")
    rpr.append(hl)
lines = [("Technological University of the Philippines", False),
         ("COLLEGE OF INDUSTRIAL TECHNOLOGY", True),
         ("Ayala Boulevard, Ermita, Manila 1000", False),
         ("cit@tup.edu.ph", False)]
p = cell_par(mid, WD_ALIGN_PARAGRAPH.CENTER)
run(p, lines[0][0])
for text, bold in lines[1:]:
    q = mid.add_paragraph()
    fmt(q, WD_ALIGN_PARAGRAPH.CENTER)
    run(q, text, bold=bold)

# 2. statement, after the heading
anchor = ph._p
p = add_p(WD_ALIGN_PARAGRAPH.JUSTIFY, after=12, line=2.0)
p.paragraph_format.first_line_indent = Inches(0.5)
run(p, "The capstone study titled ")
run(p, "“" + title + "”", bold=True)
run(p, ", prepared and submitted by ")
run(p, ", ".join(students[:-1]) + " and " + students[-1], bold=True)
run(p, ", in partial fulfillment of the requirements for the degree ")
run(p, program, bold=True)
run(p, ", is hereby approved and accepted.")

# 3. adviser (right), chairperson (centre), four members, dean (right)
t = add_table(1, 2, (3.0, 3.0))
signature(t.rows[0].cells[1], "Adviser")
add_p(WD_ALIGN_PARAGRAPH.CENTER, after=12)
t = add_table(1, 1, (6.0,))
signature(t.rows[0].cells[0], "Chairperson")
add_p(WD_ALIGN_PARAGRAPH.CENTER, after=12)
t = add_table(2, 2, (3.0, 3.0))
for r_ in range(2):
    for c_ in range(2):
        signature(t.rows[r_].cells[c_], "Member")
    if r_ == 0:
        t.rows[0].cells[0].add_paragraph()
        t.rows[0].cells[1].add_paragraph()
add_p(WD_ALIGN_PARAGRAPH.CENTER, after=24)
t = add_table(1, 2, (3.0, 3.0))
c = t.rows[0].cells[1]
p = cell_par(c, WD_ALIGN_PARAGRAPH.LEFT)
run(p, BLANK)
q = c.add_paragraph(); fmt(q, WD_ALIGN_PARAGRAPH.LEFT); run(q, "Dean, College of Industrial Technology")
q = c.add_paragraph(); fmt(q, WD_ALIGN_PARAGRAPH.LEFT, before=6); run(q, "Date: " + "_" * 22)

# 4. the placeholder paragraph goes; keep any page break it carried
brs = [b for b in ph._p.iter(qn("w:br")) if b.get(qn("w:type")) == "page"]
if brs:
    last = add_p(WD_ALIGN_PARAGRAPH.LEFT)
    last._p.append(copy.deepcopy(brs[0].getparent()))
ph._p.getparent().remove(ph._p)

d.save(dst)
print("approval sheet filled:", title[:40], "| students", len(students), "| saved", dst)

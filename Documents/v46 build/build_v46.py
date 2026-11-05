"""v46: names on the approval sheet (page ii), in the template's style: the name in bold
capitals on the line above the role. Adviser, Dean and the fourth Member stay blank.

Usage: python build_v46.py "Garbage-Guard v45.docx" "Garbage-Guard v46.docx"
"""
import sys
import docx

src, dst = sys.argv[1:3]
NAMES = {"Chairperson": ["TRISTAN R. MOPAS"],
         "Member": ["MARC ARDIE V. ARDIENTE", "MA. IAN P. DE LOS TRINOS", "MINABELLE D. VILLAFUERTE"]}

d = docx.Document(src)
i_appr = next(i for i, p in enumerate(d.paragraphs) if p.text.strip() == "APPROVAL SHEET")
i_toc = next(i for i, p in enumerate(d.paragraphs) if p.text.strip() == "TABLE OF CONTENTS")
body = d.element.body
lo = list(body).index(d.paragraphs[i_appr]._p)
hi = list(body).index(d.paragraphs[i_toc]._p)
tables = [t for t in d.tables if lo < list(body).index(t._tbl) < hi]

slots = []   # (role, name paragraph) in reading order
for t in tables:
    for row in t.rows:
        seen = set()
        for cell in row.cells:
            if id(cell._tc) in seen:
                continue
            seen.add(id(cell._tc))
            ps = cell.paragraphs
            for k in range(len(ps) - 1):
                if set(ps[k].text.strip()) == {"_"} and ps[k + 1].text.strip() in NAMES:
                    slots.append((ps[k + 1].text.strip(), ps[k]))

filled = 0
for role, names in NAMES.items():
    targets = [p for r, p in slots if r == role]
    assert len(targets) >= len(names), (role, len(targets))
    for p, name in zip(targets, names):
        r = p.runs[0]
        r.text = name
        r.bold = True
        for extra in p.runs[1:]:
            extra._r.getparent().remove(extra._r)
        filled += 1
assert filled == 4, filled
d.save(dst)
print("approval sheet names filled:", filled, "| saved", dst)

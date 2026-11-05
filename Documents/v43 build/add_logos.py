"""Puts the TUP seal and the CIT logo into the two marked spots of the approval sheet heading.

Usage: python add_logos.py "Garbage-Guard v43.docx" TUP.png CIT.png
"""
import sys
import docx
from docx.shared import Inches
from docx.oxml.ns import qn

path, tup, cit = sys.argv[1:4]
d = docx.Document(path)
done = 0
for t in d.tables[:12]:
    for cell in t._cells:
        for p in cell.paragraphs:
            label = p.text.strip()
            if label in ("[TUP logo]", "[CIT logo]"):
                for r in list(p.runs):
                    r._r.getparent().remove(r._r)
                p.add_run().add_picture(tup if label == "[TUP logo]" else cit, width=Inches(0.85))
                done += 1
assert done == 2, done
d.save(path)
print("logos placed:", done)

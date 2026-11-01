"""v28: Figure 45 redrawn (straight blue Scrum arrows, evenly spaced V-Model boxes) and
Table of Contents, List of Tables and List of Figures set to the body text spacing.

Usage: python build_v28.py "Garbage-Guard v27.docx" "Garbage-Guard v28.docx" GarbageGuard_V_Model.png
"""
import sys
import docx
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

BODY_LINE = "480"   # docDefaults: 24 pt exact, no space before or after


def body_spacing(pPr):
    sp = pPr.find(qn("w:spacing"))
    if sp is None:
        sp = OxmlElement("w:spacing")
        pPr.append(sp)
    for a in list(sp.attrib):
        del sp.attrib[a]
    sp.set(qn("w:before"), "0")
    sp.set(qn("w:after"), "0")
    sp.set(qn("w:line"), BODY_LINE)
    sp.set(qn("w:lineRule"), "exact")


def main(src, dst, png):
    d = docx.Document(src)
    P = d.paragraphs

    # 1. Figure 45 picture
    cap = [i for i, p in enumerate(P) if p.text.strip() == "Figure 45"]
    assert len(cap) == 1, cap
    rid = None
    for p in P[cap[0]:cap[0] + 5]:
        blips = p._p.findall(".//" + qn("a:blip"))
        if blips:
            rid = blips[0].get(qn("r:embed"))
            break
    assert rid, "no picture after Figure 45"
    part = d.part.related_parts[rid]
    part._blob = open(png, "rb").read()

    # 2. TOC styles and entries: body spacing (Word keeps the style when it updates the field)
    for sid in ("TOC1", "TOC2", "TOC3"):
        for st in d.styles.element.findall(qn("w:style")):
            if st.get(qn("w:styleId")) == sid:
                pPr = st.find(qn("w:pPr"))
                if pPr is None:
                    pPr = OxmlElement("w:pPr")
                    st.append(pPr)
                body_spacing(pPr)
    n_toc = 0
    start = next(i for i, p in enumerate(P) if p.text.strip() == "TABLE OF CONTENTS")
    stop = next(i for i, p in enumerate(P) if p.text.strip() == "LIST OF TABLES")
    for p in P[start + 1:stop]:
        body_spacing(p._p.get_or_add_pPr())
        n_toc += 1

    # 3. List of Tables and List of Figures (tables of entries between the two headings and the abbreviations)
    body = d.element.body
    kids = list(body)
    lo = kids.index(P[stop]._p)
    hi = kids.index(next(p for p in P if p.text.strip() == "LIST OF ABBREVIATIONS")._p)
    n_list = 0
    for el in kids[lo + 1:hi]:
        if el.tag == qn("w:tbl"):
            for p in el.iter(qn("w:p")):
                body_spacing(p.get_or_add_pPr())
                n_list += 1
    d.save(dst)
    print("Figure 45 replaced; TOC paragraphs %d, list paragraphs %d set to body spacing" % (n_toc, n_list))


if __name__ == "__main__":
    main(*sys.argv[1:4])

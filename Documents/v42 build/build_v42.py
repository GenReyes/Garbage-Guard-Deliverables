"""v42: Chapter III and IV consistency fixes from the v41 audit.
- Table 19 Acceptance Testing Results (TC_ACC_01 to 03) added to 4.5; the survey table becomes Table 20.
- The three paragraphs under Table 3 speak of metrics, not objectives, so they no longer clash with 1.2.
- 4.3 opens by naming where Objective 3 is reported and how the results follow Figure 45.
- TC_SYS_02, TC_ACC_02 and DEV-FS2 use the set threshold (20 by default) instead of a fixed 20 items.
- TC_UNIT_01 in Table 14 reports v7, v8 and v9.
- Latency start time worded as in 3.5 and 4.4; DEV-PE1 and TC_UNIT_02 wording fixed.

Usage (from the repo root):
  python "Documents/v42 build/build_v42.py" "Documents/Garbage-Guard v41.docx" "Documents/Garbage-Guard v42.docx"
"""
import copy
import sys

import docx
from docx.oxml.ns import qn
from docx.text.paragraph import Paragraph

WT = qn("w:t")


def full(p):
    return "".join(t.text or "" for t in p._p.iter(WT))


def rep(p, old, new):
    """Replace text inside one paragraph, keeping run formatting when the old text sits in one run."""
    for r in p.runs:
        if old in r.text:
            r.text = r.text.replace(old, new, 1)
            return
    t = full(p)
    assert old in t, (old, t[:200])
    for r in p.runs[1:]:
        r._r.getparent().remove(r._r)
    p.runs[0].text = t.replace(old, new, 1)
    print("  note: merged runs in:", t[:60])


def cell_rep(cell, old, new):
    p = next(p for p in cell.paragraphs if old in full(p))
    rep(p, old, new)


def find_p(d, start, exact=False):
    for p in d.paragraphs:
        t = full(p)
        if (t == start) if exact else t.startswith(start):
            if not p.style.name.lower().startswith("toc"):
                return p
    raise AssertionError(start)


def row_of(table, first):
    return next(r for r in table.rows if r.cells[0].text.strip() == first)


def table_after(d, caption):
    p = find_p(d, caption, exact=True)
    t = p._p.getnext().getnext()
    assert t.tag == qn("w:tbl"), caption
    return next(tb for tb in d.tables if tb._tbl is t)


def chapter3(d):
    # paragraphs under Table 3
    rep(find_p(d, "(1) The first objective covers"), "(1) The first objective covers Functional Suitability (Classification): how",
        "(1) The first metric, Functional Suitability (Classification), measures how")
    rep(find_p(d, "(2) The second objective defines"), "(2) The second objective defines an additional layer",
        "(2) The second metric adds a layer")
    p3 = find_p(d, "(3) Lastly, the third objective")
    rep(p3, "(3) Lastly, the third objective, Performance Efficiency measures",
        "(3) Lastly, the third metric, Performance Efficiency, measures")
    rep(p3, "the difference between the frame capture time (Start Time)",
        "the difference between the time the Raspberry Pi receives the frame (Start Time)")
    # Table 4 TC_UNIT_02 title
    t4 = table_after(d, "Table 4")
    cell_rep(row_of(t4, "TC_UNIT_02").cells[1], "Validated Accumulation", "Validate Accumulation")
    # Table 5 TC_SYS_02 procedure
    t5 = table_after(d, "Table 5")
    cell_rep(row_of(t5, "TC_SYS_02").cells[2],
             "until 20 valid targeted garbage items accumulate on the water surface.",
             "until the valid target garbage items on the water surface reach the set threshold (20 items by default).")
    # Table 6 TC_ACC_02 expected result
    t6 = table_after(d, "Table 6")
    cell_rep(row_of(t6, "TC_ACC_02").cells[3], "when reaching the 20-item threshold",
             "when the set threshold (20 items by default) is reached.")
    # Table 7 DEV-FS2 and DEV-PE1
    t7 = table_after(d, "Table 7")
    fs2 = next(r for r in t7.rows if "DEV-FS2" in [c.text.strip() for c in r.cells])
    cell_rep(fs2.cells[-1], "when the 20 item threshold is reached", "when the set threshold (20 items by default) is reached")
    pe1 = next(r for r in t7.rows if "DEV-PE1" in [c.text.strip() for c in r.cells])
    c = pe1.cells[-1]
    p = c.paragraphs[0]
    old = full(p)
    assert old.startswith("The video streaming is processed by Raspberry Pi 5 with minimum interference latency"), old
    for r in p.runs[1:]:
        r._r.getparent().remove(r._r)
    p.runs[0].text = "The video stream is processed by the Raspberry Pi 5 with an average inference latency below 3.0 seconds per frame."


def chapter4(d):
    rep(find_p(d, "The results are reported per specific objective, in the order of Section 1.2.", exact=True),
        "in the order of Section 1.2.",
        "in the order of Section 1.2. Objectives 1 and 2 are covered in this section, and Objective 3, the evaluation "
        "under ISO/IEC 25010:2023, in Sections 4.4 and 4.5. The results also follow the development model in Figure 45: "
        "the training runs in Table 10 are the development sprints, each closed by the unit tests in Table 14, while "
        "Tables 15 to 20 report the system and acceptance tests of the V-Model at-end.")
    t14 = table_after(d, "Table 14")
    c = row_of(t14, "TC_UNIT_01").cells[3]
    p = c.paragraphs[0]
    assert full(p).startswith("Precision 0.852, recall 0.685, mAP@0.5 0.745 on 807 test images"), full(p)
    for r in p.runs[1:]:
        r._r.getparent().remove(r._r)
    p.runs[0].text = ("On the same 807 test images (Table 11): v7 precision 0.852, recall 0.685, mAP@0.5 0.745, pass; "
                      "v8, the field model, 0.829, 0.674, 0.728, pass; v9 0.793, 0.702, 0.730, below the 0.80 precision target.")
    rep(find_p(d, "This section reports the acceptance test plan"), "the developer-side indicators first",
        "the developer-side test cases and indicators first")
    acceptance_table(d)
    p = next(x for x in d.paragraphs if "In the developer-side acceptance test, the system rated" in full(x))
    assert p.runs[-1].text == " trials.", p.runs[-1].text
    p.runs[-1].text = " trials (Table 19)."


def acceptance_table(d):
    body = d.element.body
    # survey table: 19 -> 20
    cap19 = find_p(d, "Table 19", exact=True)
    rep(cap19, "Table 19", "Table 20")
    bs = cap19._p.find(qn("w:bookmarkStart")); assert bs.get(qn("w:name")) == "_TabMk19"
    bs.set(qn("w:name"), "_TabMk20")
    lot = d.tables[0]
    r19 = row_of(lot, "19")._tr
    ts = list(r19.iter(WT)); ts[0].text = "20"
    r19.find(".//" + qn("w:hyperlink")).set(qn("w:anchor"), "_TabMk20")
    r19.find(".//" + qn("w:instrText")).text = " PAGEREF _TabMk20 \\h "
    # new LoT row 19 after row 18
    r18 = row_of(lot, "18")._tr
    nr = copy.deepcopy(r18); r18.addnext(nr)
    ts = [t for t in nr.iter(WT)]; ts[0].text = "19"; ts[1].text = "Acceptance Testing Results"
    nr.find(".//" + qn("w:hyperlink")).set(qn("w:anchor"), "_TabMk19")
    nr.find(".//" + qn("w:instrText")).text = " PAGEREF _TabMk19 \\h "
    # caption, title and table cloned from Table 18 (caption) and Table 15 (layout)
    cap18 = find_p(d, "Table 18", exact=True)
    title18 = Paragraph(cap18._p.getnext(), cap18._parent)
    t18 = cap18._p.getnext().getnext()
    after = t18.getnext()          # empty paragraph after Table 18
    assert after.tag == qn("w:p") and not full(Paragraph(after, cap18._parent)).strip()
    t15 = table_after(d, "Table 15")
    intro = copy.deepcopy(find_p(d, "This section reports the acceptance test plan")._p)
    ip = Paragraph(intro, cap18._parent)
    for r in ip.runs[1:]:
        r._r.getparent().remove(r._r)
    ip.runs[0].text = ("Table 19 gives the results of the acceptance test cases in Table 6, run on the final build during "
                       "the field deployment.")
    cap = copy.deepcopy(cap18._p); title = copy.deepcopy(title18._p); tbl = copy.deepcopy(t15._tbl)
    blank = copy.deepcopy(after)
    for e in (intro, cap, title, tbl, blank):
        after.addnext(e); after = e
    nid = max(int(b.get(qn("w:id"))) for b in body.iter(qn("w:bookmarkStart"))) + 1
    b = cap.find(qn("w:bookmarkStart")); b.set(qn("w:name"), "_TabMk19"); b.set(qn("w:id"), str(nid))
    cap.find(qn("w:bookmarkEnd")).set(qn("w:id"), str(nid))
    list(cap.iter(WT))[0].text = "Table 19"
    tts = list(title.iter(WT)); tts[0].text = "Acceptance Testing Results"
    for t in tts[1:]:
        t.text = ""
    rows = tbl.findall(qn("w:tr"))
    for tr in rows[4:]:
        tbl.remove(tr)
    data = [("TC_ACC_01", "Functional Suitability (classification)", "[ ]", "[ ]"),
            ("TC_ACC_02", "Functional Suitability (accumulation detection)", "[ ]", "[ ]"),
            ("TC_ACC_03", "Performance Efficiency (latency and stability)", "[ ]", "Not counted (computed from all logged frames)")]
    for tr, vals in zip(rows[1:4], data):
        for tc, v in zip(tr.findall(qn("w:tc")), vals):
            ts = list(tc.iter(WT)); ts[0].text = v
            for t in ts[1:]:
                t.text = ""
    # keep the new table on one page: every row but the last keeps with the next
    for tr in tbl.findall(qn("w:tr"))[:-1]:
        for p in tr.iter(qn("w:p")):
            Paragraph(p, cap18._parent).paragraph_format.keep_with_next = True


def main(src, dst):
    d = docx.Document(src)
    chapter3(d)
    chapter4(d)
    d.save(dst)
    print("saved", dst)


if __name__ == "__main__":
    main(*sys.argv[1:3])

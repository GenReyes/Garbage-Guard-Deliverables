"""Build Garbage-Guard v24 from v23: redrawn Figure 45 (V-Model and Scrum hybrid) and matching 3.5 text.

python build_v24.py IN.docx OUT.docx FIG_DIR
"""
import sys
import docx
from docx.oxml.ns import qn
from PIL import Image

SRC, OUT, FIGS = sys.argv[1:4]
doc = docx.Document(SRC)


def rewrite(old, new):
    hits = [p for p in doc.paragraphs if old in p.text]
    assert len(hits) == 1, (old, len(hits))
    p = hits[0]
    p.runs[0].text = p.text.replace(old, new)
    for r in p.runs[1:]:
        r._r.getparent().remove(r._r)


def swap(cap_text, png):
    ps = doc.paragraphs
    cap = [i for i, p in enumerate(ps) if p.text.strip() == cap_text][-1]
    for k in range(cap, cap + 5):
        pel = ps[k]._p
        blip = pel.find(".//" + qn("a:blip"))
        if blip is None:
            continue
        rid, _ = doc.part.get_or_add_image(png)
        blip.set(qn("r:embed"), rid)
        w, h = Image.open(png).size
        ext = pel.find(".//" + qn("wp:extent"))
        cx = int(ext.get("cx")); cy = int(cx * h / w)
        ext.set("cy", str(cy))
        for x in pel.iter(qn("a:ext")):
            if x.getparent().tag == qn("a:xfrm"):
                x.set("cx", str(cx)); x.set("cy", str(cy))
        return
    raise SystemExit(f"no picture after {cap_text}")


rewrite("Each is paired with the stage directly across from it, as the dashed arrows show. The acceptance "
        "indicators in Tables 6 to 8 were written during requirement gathering, and the system test cases in "
        "Table 5 were drafted as soon as the hardware layout was settled, long before there was a system to test.",
        "Each is paired with the stage directly across from it by a dashed test-plan link. The acceptance test "
        "plan, the indicators in Tables 6 to 8, was written during requirement gathering, and the system test "
        "plan, the cases in Table 5, was drafted as soon as the hardware layout was settled, long before there "
        "was a system to test.")
rewrite("The lower part of the figure is the Scrum stage, where most of the weeks on the Gantt chart in Appendix A "
        "were spent. Every sprint went around the same three steps.",
        "The lower part of the figure is the development phase, run in Scrum, where most of the weeks on the "
        "Gantt chart in Appendix A were spent. The dashed circle is one sprint, and every sprint went around the "
        "same three steps.")
rewrite("The two long arrows at the sides give the direction of the work: verification runs down from the "
        "requirements into the sprints, and validation runs up from unit testing to acceptance.",
        "The three long arrows around the figure give the direction of the work: the verification phase runs "
        "down from the requirements into the sprints, the development phase runs across the sprints from "
        "software design to unit testing, and the validation phase runs up from unit testing to acceptance.")
rewrite("Phase 2: Development Sprints (Scrum)", "Phase 2: Development Phase (Scrum)")
swap("Figure 45", FIGS + "/GarbageGuard_V_Model.png")
swap("Gantt Chart", FIGS + "/GarbageGuard_Gantt_Chart.png")
doc.save(OUT)
print("saved", OUT)

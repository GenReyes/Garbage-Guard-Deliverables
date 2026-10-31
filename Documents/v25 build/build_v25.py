"""Build Garbage-Guard v25 from v24: higher-contrast Figure 45, and the system and
acceptance test plans named in Chapters III and IV.

python build_v25.py IN.docx OUT.docx FIG_DIR
"""
import copy
import sys
import docx
from docx.oxml.ns import qn
from PIL import Image

SRC, OUT, FIGS = sys.argv[1:4]
doc = docx.Document(SRC)


def one(sub, exact=False):
    hits = [p for p in doc.paragraphs if (p.text.strip() == sub if exact else sub in p.text)]
    assert hits, sub
    return hits[-1]


def rewrite(old, new):
    hits = [p for p in doc.paragraphs if old in p.text]
    assert len(hits) == 1, (old, len(hits))
    p = hits[0]
    p.runs[0].text = p.text.replace(old, new)
    for r in p.runs[1:]:
        r._r.getparent().remove(r._r)


# Chapter III
rewrite("System testing is the partner of system design, and its cases in Table 5 were written when the hardware "
        "layout was fixed.",
        "System testing is the partner of system design. Table 5 is its system test plan, drafted when the hardware "
        "layout was fixed, as the dashed system test plan link in Figure 45 shows, and run in full on each build "
        "that passed unit testing.")
rewrite("Acceptance testing is the partner of requirement gathering, and its indicators were set with the adviser "
        "during that stage.",
        "Acceptance testing is the partner of requirement gathering. Its acceptance test plan, made up of the test "
        "cases in Table 6, the developer-side indicators in Table 7 and the end-user survey in Table 8, was set with "
        "the adviser during that stage, as the dashed acceptance test plan link in Figure 45 shows.")

# Chapter IV
rewrite("Table 13 gives the system test results from the field.",
        "Table 13 gives the results of the system test plan in Table 5, run on the unit in the field.")
h = one("4.5 Evaluation Results", exact=True)
tpl = one("Table 13 gives the results of the system test plan")._p
new = copy.deepcopy(tpl)
for r in new.findall(qn("w:r"))[1:]:
    new.remove(r)
new.find(".//" + qn("w:t")).text = ("This section reports the acceptance test plan set during requirement gathering "
                                    "(Tables 6 to 8 in Chapter III): the developer-side indicators first, then the "
                                    "end-user usability survey.")
h._p.addnext(new)

# Figure 45
ps = doc.paragraphs
cap = [i for i, q in enumerate(ps) if q.text.strip() == "Figure 45"][-1]
png = FIGS + "/GarbageGuard_V_Model.png"
for k in range(cap, cap + 5):
    pel = ps[k]._p
    blip = pel.find(".//" + qn("a:blip"))
    if blip is None:
        continue
    rid, _ = doc.part.get_or_add_image(png)
    blip.set(qn("r:embed"), rid)
    w, hh = Image.open(png).size
    ext = pel.find(".//" + qn("wp:extent"))
    cx = int(ext.get("cx")); cy = int(cx * hh / w)
    ext.set("cy", str(cy))
    for x in pel.iter(qn("a:ext")):
        if x.getparent().tag == qn("a:xfrm"):
            x.set("cx", str(cx)); x.set("cy", str(cy))
    break
else:
    raise SystemExit("Figure 45 picture not found")
doc.save(OUT)
print("saved", OUT)

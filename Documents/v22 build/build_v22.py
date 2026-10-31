"""Build Garbage-Guard v22 from v21: the USB hub inside the enclosure.

python build_v22.py IN.docx OUT.docx FIG_DIR
"""
import sys
import docx
from docx.oxml.ns import qn
from PIL import Image

SRC, OUT, FIGS = sys.argv[1:4]
doc = docx.Document(SRC)
ps = lambda: doc.paragraphs


def find(sub, start=0):
    for i, p in enumerate(ps()):
        if i >= start and sub in p.text:
            return i
    raise SystemExit(f"not found: {sub!r}")


def rewrite(i, old, new):
    p = ps()[i]
    t = p.text
    assert old in t, (i, old)
    t = t.replace(old, new)
    runs = p.runs
    runs[0].text = t
    for r in runs[1:]:
        r._r.getparent().remove(r._r)


i3 = [i for i, p in enumerate(ps()) if p.text.strip() == "III. METHODOLOGY"][-1]

# 3.4 step-up cable paragraph
import re
i = find("It is rated 12V at", i3)
m = re.search(r"It is rated 12V at [0-9.]+ ?A\.", ps()[i].text)
assert m, ps()[i].text
rewrite(i, m.group(0),
        m.group(0) + " The step-up cable is not plugged into the UPS HAT directly. Its USB-A "
        "plug cannot fit beside the HAT's USB-C charging cable, so a small USB hub inside the enclosure is "
        "plugged into the HAT's USB-A output, and the step-up cable runs from the hub.")
# system design bullet
i = find("its USB-A output also feeds the camera through a 5 V to 12 V step-up cable", i3)
rewrite(i, "its USB-A output also feeds the camera through a 5 V to 12 V step-up cable",
        "its USB-A output also feeds the camera through a USB hub and a 5 V to 12 V step-up cable")
# assembly step 7
i = find("its power plug to the 5 V to 12 V step-up cable on the UPS HAT's USB-A output", i3)
rewrite(i, "its power plug to the 5 V to 12 V step-up cable on the UPS HAT's USB-A output",
        "its power plug to the 5 V to 12 V step-up cable, which is connected through the USB hub inside "
        "the box to the UPS HAT's USB-A output")

# Figure 26 image
cap = [i for i, p in enumerate(ps()) if p.text.strip() == "Figure 26"][-1]
for k in range(cap, cap + 5):
    blip = ps()[k]._p.find(".//" + qn("a:blip"))
    if blip is not None:
        path = FIGS + "/GarbageGuard_System_Architecture.png"
        rid, _ = doc.part.get_or_add_image(path)
        blip.set(qn("r:embed"), rid)
        w, h = Image.open(path).size
        ext = ps()[k]._p.find(".//" + qn("wp:extent"))
        cx = int(ext.get("cx")); cy = int(cx * h / w)
        ext.set("cy", str(cy))
        for x in ps()[k]._p.iter(qn("a:ext")):
            if x.getparent().tag == qn("a:xfrm"):
                x.set("cx", str(cx)); x.set("cy", str(cy))
        break
else:
    raise SystemExit("Figure 26 picture not found")
doc.save(OUT)
print("saved", OUT)

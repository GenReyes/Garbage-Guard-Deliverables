"""Build Garbage-Guard v23 from v22: Chapter III names v8 as the unit's model and leaves room for v9.

python build_v23.py IN.docx OUT.docx
"""
import sys
import docx

SRC, OUT = sys.argv[1:3]
doc = docx.Document(SRC)


def rewrite(old, new):
    hits = [p for p in doc.paragraphs if old in p.text]
    assert len(hits) == 1, (old, len(hits))
    p = hits[0]
    t = p.text.replace(old, new)
    p.runs[0].text = t
    for r in p.runs[1:]:
        r._r.getparent().remove(r._r)


rewrite("The v7 model was trained at 640 pixels and scored higher on the frozen test set at 640 than at 960, "
        "so the detection size is the first setting to check when v7 is the active model.",
        "The unit currently runs v8, and the Model card in the Settings tab switches to another trained "
        "model, such as v9 once it is finished, without editing code. Each model works best at the size it "
        "was trained at, so the detection size is the first setting to check after a switch; v7, for "
        "example, was trained at 640 pixels and scored higher on the frozen test set at 640 than at 960.")
rewrite("The v8 model, trained with about 800 photographs taken at the creek, went through the same cycle.",
        "The v8 model, trained with about 800 photographs taken at the creek, went through the same cycle "
        "and is the model the unit runs now; v9 will be measured against the same frozen set before it "
        "replaces v8.")
doc.save(OUT)
print("saved", OUT)

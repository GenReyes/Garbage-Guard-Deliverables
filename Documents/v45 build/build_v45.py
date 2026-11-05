"""v45: the explanation of Figure H17 now matches the App 15 screenshots that v44 put in it:
(a) Model card, (b) Monitored area and Screen saver, (c) Pi buzzer card with Quiet hours.

Usage (from the repo root):
  python "Documents/v45 build/build_v45.py" "Documents/Garbage-Guard v44.docx" "Documents/Garbage-Guard v45.docx"
"""
import sys

import docx
from docx.oxml.ns import qn

WT = qn("w:t")


def full(p):
    return "".join(t.text or "" for t in p._p.iter(WT))


def main(src, dst):
    d = docx.Document(src)
    p = next(p for p in d.paragraphs if full(p).startswith("Figure H17 shows the first part of the Settings"))
    for r in p.runs[1:]:
        r._r.getparent().remove(r._r)
    p.runs[0].text = ("Figure H17 shows the middle part of the Settings screen. In (a) the Model card shows the model "
                      "that is running, goes back to the previous one, and uploads a new model as a zip file under a "
                      "version name. In (b) the user draws the monitored area and sets the screen saver of the Pi. In "
                      "(c) the user controls the buzzer of the Pi, including the events that sound and the repeating "
                      "alarm, and sets the quiet hours, whose times stay greyed out while the setting is off.")
    d.save(dst)
    print("saved", dst)


if __name__ == "__main__":
    main(*sys.argv[1:3])

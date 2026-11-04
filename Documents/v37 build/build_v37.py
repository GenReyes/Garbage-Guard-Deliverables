"""v37: App 14 screenshots for Figures G16 and G17 (taken on the Galaxy S22 Ultra emulator in demo mode)
and the Figure 49 to 51 explanations moved below their figures, so each figure comes first.

Usage (from the repo root):
  python "Documents/v37 build/build_v37.py" "Documents/Garbage-Guard v36.docx" "Documents/Garbage-Guard v37.docx" <shots folder>
The shots folder holds a_history.png, b_record.png, c_box_labels.png, d2_area_screen.png, d3_power_buzzer.png.
"""
import io
import os
import sys

import docx
from docx.oxml.ns import qn
from PIL import Image

WT = qn("w:t")


def full(p):
    return "".join(t.text or "" for t in p._p.iter(WT))


def set_text(p, text):
    for r in p.runs[1:]:
        r._r.getparent().remove(r._r)
    p.runs[0].text = text


def jpeg(path):
    im = Image.open(path).convert("RGB")
    buf = io.BytesIO(); im.save(buf, "JPEG", quality=90); buf.seek(0)
    return buf, im.size


def swap_table_images(d, tbl, paths):
    blips = list(tbl.iter(qn("a:blip")))
    assert len(blips) == len(paths), (len(blips), paths)
    for blip, path in zip(blips, paths):
        buf, (w, h) = jpeg(path)
        rid, _ = d.part.get_or_add_image(buf)
        blip.set(qn("r:embed"), rid)
        drawing = blip
        while drawing.tag != qn("w:drawing"):
            drawing = drawing.getparent()
        ext = drawing.find(".//" + qn("wp:extent")); a = drawing.find(".//" + qn("a:ext"))
        cx = int(ext.get("cx")); cy = int(cx * h / w)
        for e in (ext, a):
            e.set("cx", str(cx)); e.set("cy", str(cy))


def appendix_g(d, shots):
    P = d.paragraphs
    for cap, files in (("Figure G16", ["a_history.png", "b_record.png"]),
                       ("Figure G17", ["c_box_labels.png", "d2_area_screen.png", "d3_power_buzzer.png"])):
        i = next(k for k, p in enumerate(P) if full(p) == cap and k > 1000)
        tbl = P[i + 1]._p.getnext()
        assert tbl.tag == qn("w:tbl"), cap
        swap_table_images(d, tbl, [os.path.join(shots, f) for f in files])
    i = next(k for k, p in enumerate(P) if full(p).startswith("Figure G16 shows the History screen"))
    set_text(P[i], "Figure G16 shows the History screen. In (a) each event carries a record number, counted down "
             "from the newest, with its type, time and item count and a snapshot thumbnail with an eye mark; the "
             "list can be filtered, marked as read or deleted. In (b) a record is opened by tapping its thumbnail "
             "and shows its snapshot, annotated or clean, together with the counts, confidence and time of the "
             "event.")
    i = next(k for k, p in enumerate(P) if full(p).startswith("Figure G17 shows the first part of the Settings"))
    set_text(P[i], "Figure G17 shows the first part of the Settings screen. In (a) the user adjusts the detection "
             "confidence and the accumulation limit, sends a test alert and chooses the box labels the Pi draws "
             "(track ID, class name and confidence), with a preview of how a box will read. In (b) the user draws "
             "the monitored area and sets the screen saver of the Pi. In (c) the user sets the automatic shutdown "
             "on low battery and controls the buzzer of the Pi, including the events that sound and the "
             "repeating alarm.")


def figure_first(d):
    """Move each 'Figure N shows' paragraph of Figures 49-51 below its figure (after the note if there is one)."""
    for n in (49, 50, 51):
        P = d.paragraphs
        e = next(k for k, p in enumerate(P) if full(p).startswith("Figure %d shows" % n))
        assert full(P[e + 1]) == "Figure %d" % n, (n, full(P[e + 1]))
        last = e + 3  # caption, title, image
        if full(P[last + 1]).startswith("Note."):
            last += 1
        expl = P[e]._p
        P[last]._p.addnext(expl)
        for k in range(e + 3, last + 1):
            P[k].paragraph_format.keep_with_next = True
    P = d.paragraphs
    for n in (49, 50, 51):
        e = next(k for k, p in enumerate(P) if full(p).startswith("Figure %d shows" % n))
        assert full(P[e - 1]).startswith("Note.") or P[e - 1]._p.find(".//" + qn("a:blip")) is not None, n
        P[e].paragraph_format.keep_with_next = False


def main(src, dst, shots):
    d = docx.Document(src)
    appendix_g(d, shots)
    figure_first(d)
    d.save(dst)
    print("saved", dst)


if __name__ == "__main__":
    main(*sys.argv[1:4])

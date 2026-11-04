"""v36: the Erlvsoiis WF-ER1800M USB Wi-Fi adapter on a Vention USB 3.0 extension cable replaces the
USB-to-Ethernet adapter and the Wi-Fi repeater (text, Figures 26, 27, 34, 35, 49, 50, Bill of Materials,
references). Appendix G gets new History and Settings screenshots and text for record numbers,
thumbnails, infinite scroll, box labels and pinch zoom. Appendix H is rebuilt from the current source.

Usage (from the repo root):
  python "Documents/v36 build/build_v36.py" "Documents/Garbage-Guard v35.docx" "Documents/Garbage-Guard v36.docx"
"""
import copy
import json
import os
import re
import sys

import docx
from docx.oxml.ns import qn
from docx.shared import Inches
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
FIGS = os.path.join(HERE, "..", "Figures")
WT = qn("w:t")


def full(p):
    return "".join(t.text or "" for t in p._p.iter(WT))


def find(P, start, after=0):
    hits = [i for i, p in enumerate(P) if i >= after and full(p).startswith(start)]
    assert hits, "not found: " + start
    return hits[0]


def set_text(p, text, keep=1):
    """Keep the first `keep` runs' formatting: text goes into run `keep-1`, later runs are dropped."""
    runs = p.runs
    for r in runs[keep:]:
        r._r.getparent().remove(r._r)
    runs[keep - 1].text = text


def replace(p, old, new):
    t = full(p)
    assert old in t, (old, t[:120])
    set_text(p, t.replace(old, new))


def swap_image(d, p, path, width=None, height=None):
    blip = p._p.find(".//" + qn("a:blip"))
    assert blip is not None, full(p)
    rid, _ = d.part.get_or_add_image(path)
    blip.set(qn("r:embed"), rid)
    ext = p._p.find(".//" + qn("wp:extent")); a = p._p.find(".//" + qn("a:ext"))
    w, h = Image.open(path).size
    if width is None and height is None:
        cx = int(ext.get("cx")); cy = int(cx * h / w)
    elif width is not None:
        cx = int(width); cy = int(width * h / w)
        if height is not None and cy > height:
            cy = int(height); cx = int(height * w / h)
    else:
        cy = int(height); cx = int(height * w / h)
    for e in (ext, a):
        e.set("cx", str(cx)); e.set("cy", str(cy))


def wifi(d):
    P = d.paragraphs
    i = find(P, "Figure 27 shows the physical design")
    replace(P[i], "the Ethernet cable to the Wi-Fi extender, and the 2-in-1",
            "the USB 3.0 extension cable to the Wi-Fi adapter, and the 2-in-1")
    replace(P[i], "The camera is not drawn.",
            "The USB Wi-Fi adapter sits on top of the enclosure at the right, outside the metal walls. "
            "The camera is not drawn.")

    h = find(P, "USB-to-Ethernet Adapter and Wi-Fi Repeater")
    set_text(P[h], "USB Wi-Fi Adapter")
    assert full(P[h + 1]) == "Figure 34" and full(P[h + 5]) == "Figure 35"
    set_text(P[h + 2], "Erlvsoiis WF-ER1800M Dual-Band USB Wi-Fi Adapter")
    swap_image(d, P[h + 3], os.path.join(HERE, "fig34_wifi_adapter.jpg"), width=Inches(2.2))
    set_text(P[h + 6], "USB Wi-Fi Adapter Mounted on the Enclosure")
    swap_image(d, P[h + 7], os.path.join(HERE, "fig35_adapter_mounted.jpg"), width=Inches(3.6))
    note = P[h + 8]
    assert full(note).startswith("Note.")
    set_text(note, "Note. ")
    note.runs[0].italic = True
    r = note.add_run("The adapter is plugged into a Vention USB 3.0 Type A male-to-female extension cable that "
                     "runs to the Raspberry Pi inside the box.")
    r.italic = False
    assert full(P[h + 9]).startswith("The Raspberry Pi 5")
    set_text(P[h + 9],
             "The Raspberry Pi 5’s own Ethernet port is given over to the camera, on a direct link with fixed "
             "addresses at both ends, so the unit reaches the internet over Wi-Fi through a USB adapter. The "
             "Erlvsoiis WF-ER1800M is sold as an 1800M dual-band adapter for the 2.4GHz and 5.8GHz bands, with "
             "a USB 3.0 plug and two external antennas (Shopee, n.d.). Raspberry Pi OS recognized it without any "
             "driver being installed. It is plugged into a Vention USB 3.0 Type A male-to-female extension cable, "
             "which leaves the box through a cable entry, so the adapter sits on top of the enclosure at the "
             "right (Figure 35).")
    assert full(P[h + 10]).startswith("The repeater was adopted")
    set_text(P[h + 10],
             "Testing showed that the Pi’s built-in wireless adapter could not complete a connection to any "
             "network, an open one included, and the metal walls of the enclosure weaken any radio placed inside "
             "it. The first fix was a Wi-Fi repeater cabled to the Pi through a USB-to-Ethernet adapter. The USB "
             "adapter outside the box replaced both, which removed the repeater’s separate wall power and a "
             "second Ethernet cable. The camera link and the internet link still run on separate interfaces, so "
             "a fault on one does not affect the other.")

    i = find(P, "The electronics are housed in a metal waterproof junction box")
    replace(P[i], "the Ethernet cable to the Wi-Fi extender and the camera cable, sealed with a rubberized water "
            "sealant.", "the USB 3.0 extension cable to the Wi-Fi adapter and the camera cable, sealed with a "
            "rubberized water sealant. The USB Wi-Fi adapter is mounted on top of the box at the right.")

    i = find(P, "USB-to-Ethernet adapter and Wi-Fi repeater")
    p = P[i]
    for r in p.runs[1:]:
        r._r.getparent().remove(r._r)
    p.runs[0].text = "USB Wi-Fi adapter – "
    p.add_run("It joins the site’s wireless network and carries the “cloud link” to Firebase.")

    i = find(P, "8.The USB-to-Ethernet adapter")
    set_text(P[i], "\tThe USB Wi-Fi adapter is plugged into the Vention USB 3.0 extension cable, set on top of the "
             "enclosure at the right, and joined to the site’s wireless network or a phone hotspot from the "
             "Wi-Fi setup screen of the Pi.", keep=2)

    i = find(P, "Figure 49 shows the assembled unit")
    set_text(P[i], "Figure 49 shows the assembled unit from the front. The electronics are housed in a metal "
             "junction box with the 7-inch touchscreen set into the door and a locking handle below the screen. "
             "Stickers on the door carry the study title, the names of the researchers, the Garbage-Guard logo "
             "and a QR code that links to the project files. The Hikvision HiWatch HWI-B140H bullet camera is "
             "mounted separately on its own base and is joined to the box by its cable only.")
    assert full(P[i + 1]) == "Figure 49"
    swap_image(d, P[i + 3], os.path.join(HERE, "fig49_front_view.jpg"), width=Inches(5.2))
    j = i + 4
    assert full(P[j]).startswith("Figure 50 shows")
    set_text(P[j], "Figure 50 shows the right side of the enclosure with its case fan and the USB Wi-Fi adapter "
             "on top.")
    assert full(P[j + 1]) == "Figure 50"
    set_text(P[j + 2], "Side View of the Enclosure Showing the Case Fan and the USB Wi-Fi Adapter")
    swap_image(d, P[j + 3], os.path.join(HERE, "fig50_side_view.jpg"), height=Inches(5.0))
    note = P[j + 4]
    assert full(note).startswith("Note. The three cable entries")
    set_text(note, "The USB Wi-Fi adapter sits on top of the enclosure at the right, plugged into the Vention "
             "USB 3.0 extension cable that runs to the Raspberry Pi inside. The camera stands in the foreground "
             "on its base.", keep=2)

    i = find(P, "The Raspberry Pi 5 runs Raspberry Pi OS (64-bit)")
    replace(P[i], "comes through a USB-to-Ethernet adapter cabled to a Wi-Fi repeater,",
            "comes through a USB Wi-Fi adapter mounted outside the enclosure,")
    i = find(P, "The built-in Wi-Fi of the Raspberry Pi 5 did not work during testing")
    replace(P[i], "depends on a cabled Wi-Fi repeater.", "depends on a USB Wi-Fi adapter mounted outside the enclosure.")
    i = find(P, "The prototype has limits that the results make plain.")
    replace(P[i], "depends on a cabled Wi-Fi extender,", "depends on a USB Wi-Fi adapter mounted outside the box,")

    # References: the two TP-Link pages go; the remaining PC Express entry loses its letter.
    for start in ("PC Express. (n.d.-a). TP-Link", "PC Express. (n.d.-b). TP-Link"):
        p = P[find(P, start)]
        p._p.getparent().remove(p._p)
    P = d.paragraphs
    p = P[find(P, "PC Express. (n.d.-c).")]
    p.runs[0].text = p.runs[0].text.replace("(n.d.-c)", "(n.d.)")
    for t in d.element.body.iter(WT):
        if t.text and "PC Express, n.d.-" in t.text:
            raise SystemExit("unexpected PC Express citation: " + t.text)

    # Figures 26 and 27, redrawn
    P = d.paragraphs
    for cap, png in (("Figure 26", "GarbageGuard_System_Architecture.png"),
                     ("Figure 27", "GarbageGuard_Enclosure_Drawing.png")):
        k = next(i for i, p in enumerate(P) if full(p) == cap and not p.style.name.lower().startswith("toc"))
        swap_image(d, P[k + 2], os.path.join(FIGS, png))

    # List of Figures titles
    lof = d.tables[1]
    titles = {"34": "Erlvsoiis WF-ER1800M Dual-Band USB Wi-Fi Adapter",
              "35": "USB Wi-Fi Adapter Mounted on the Enclosure",
              "50": "Side View of the Enclosure Showing the Case Fan and the USB Wi-Fi Adapter"}
    done = set()
    for row in lof.rows:
        n = row.cells[0].text.strip()
        if n in titles:
            ts = [t for t in row.cells[1]._tc.iter(WT) if t.text]
            ts[0].text = titles[n]
            for t in ts[1:]:
                t.text = ""
            done.add(n)
    assert done == set(titles), done


def bom(d):
    t = next(t for t in d.tables if len(t.rows) > 8 and any("TP-Link UE300" in c.text for c in t.rows[7].cells))
    new = {7: ("Erlvsoiis WF-ER1800M dual-band USB Wi-Fi adapter", "₱489.00"),
           8: ("Vention USB 3.0 Type A male-to-female extension cable", "₱129.00")}
    for k, (name, price) in new.items():
        cells = t.rows[k].cells
        for c, val in ((cells[1], name), (cells[3], price), (cells[4], price)):
            p = c.paragraphs[0]
            for r in p.runs[1:]:
                r._r.getparent().remove(r._r)
            p.runs[0].text = val
    total = t.rows[-1].cells[-1].paragraphs[0]
    assert "33,185" in total.text, total.text
    for r in total.runs[1:]:
        r._r.getparent().remove(r._r)
    total.runs[0].text = "₱32,303.00"


def appendix_g(d):
    P = d.paragraphs
    for cap, img, w in (("Figure G6", "G6_history.jpg", None), ("Figure G9", "G9_settings.jpg", None)):
        k = next(i for i, p in enumerate(P) if full(p) == cap and i > 1000)
        swap_image(d, P[k + 2], os.path.join(HERE, img))
    i = find(P, "Figure G6 shows the History tab")
    set_text(P[i], "Figure G6 shows the History tab, which lists all events stored in the SQLite database on the "
             "Pi. Events include accumulation alerts, spikes, manual captures, test alerts, mutes and power "
             "events. Each row carries a record number, counted down from the newest, and a square thumbnail of "
             "its snapshot with an eye mark; tapping the thumbnail opens the snapshot. Older rows load as the "
             "list is scrolled, so the whole history is reached without changing pages. Events can be filtered "
             "by type and by a date range, marked as read and deleted. The Clean copies switch determines whether "
             "or not an unannotated copy of each snapshot is kept.")
    i = find(P, "Figure G9 shows the Settings tab")
    set_text(P[i], "Figure G9 shows the Settings tab. The Screen saver setting determines when the touchscreen "
             "turns off or dims. Power sets the battery shutdown level and shows the voltage of each battery "
             "cell. The Buzzer setting determines the volume and the events that sound. The Camera stream card "
             "selects the camera stream and the detection size, and its Box labels row chooses what is written "
             "beside each detection box: the track ID, the class name, the confidence, or any mix of the three. "
             "With only the confidence selected, a box reads “50%.” The Model card selects the trained "
             "model that the Pi runs. Quiet hours, a time window during which the buzzer is disabled, sit below "
             "the Model card.")
    i = find(P, "Figure G4 shows the Fullscreen view")
    t = full(P[i])
    set_text(P[i], t.rstrip() + " Two fingers pinch to zoom into the picture and drag to pan it, a Reset zoom "
             "key returns to the whole frame, and one finger still places the points of the area. Full-size "
             "snapshots opened from the history zoom the same way.")
    i = find(P, "Figure G15 shows the app in Fullscreen")
    t = full(P[i])
    set_text(P[i], t.rstrip() + " As on the dashboard, a two-finger pinch zooms and pans the picture and Reset "
             "zoom returns to the whole frame.")
    i = find(P, "The dashboard is a local web page served by the Raspberry Pi on port 8080")
    replace(P[i], "History lists the stored events with filters", "History lists the stored events, numbered and "
            "with a snapshot thumbnail, with filters")
    replace(P[i], "camera stream and model options.", "camera stream, box labels and model options.")


def appendix_h(d):
    code = json.load(open(os.path.join(HERE, "listings.json"), encoding="utf-8"))
    P = d.paragraphs
    i = find(P, "Listings in this appendix are from the Garbage-Guard project code repository.")
    replace(P[i], "Each listing is the entire source file.", "Each listing is the entire source file as of "
            "November 4, 2026, with short explanatory comments added for the reader.")
    P = d.paragraphs
    heads = [i for i, p in enumerate(P) if re.match(r"Listing H\d+\. ", full(p)) and i > 1000]
    assert len(heads) == 10, heads
    parent = None
    for h in reversed(heads):
        P = d.paragraphs
        name = full(P[h]).split(". ", 1)[1].split(":")[0]
        lines = code[name]
        old = []
        for p in P[h + 1:]:
            if not re.match(r"^ *\d+( |$)", full(p)) or re.match(r"Listing H\d+\. ", full(p)):
                break
            old.append(p)
        tmpl = next(p for p in old if len(p.runs) == 2)
        anchor = old[-1]._p
        width = max(4, len(str(len(lines))))
        new = []
        for n, line in enumerate(lines, 1):
            el = copy.deepcopy(tmpl._p)
            ts = list(el.iter(WT))
            ts[0].text = str(n).rjust(width) + " "
            ts[1].text = line
            for t in ts[:2]:
                t.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
            new.append(el)
        for el in new:
            anchor.addprevious(el)
        for p in old:
            p._p.getparent().remove(p._p)
        print("Listing", name, len(old), "->", len(lines), "lines")


def main(src, dst):
    d = docx.Document(src)
    wifi(d)
    bom(d)
    appendix_g(d)
    appendix_h(d)
    d.save(dst)
    print("saved", dst)


if __name__ == "__main__":
    main(*sys.argv[1:3])

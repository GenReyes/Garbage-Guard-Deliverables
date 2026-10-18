# What changed from v3 to v4

`GarbageGuard v4.docx` is v3 with the corrections below. Nothing else was
reworded. Four Word comments mark the points the group still has to decide.
After opening it, right-click the table of contents and the two lists and
choose **Update Field**, because the page numbers moved.

## 1. Counting now matches the code

v3 said ByteTrack gives each item one identity and the counter adds one per
item, so the total only grows. The Pi does not do that. It counts the items
inside the drawn area in each frame, takes the median of the last 15 frames,
raises the alert once at the threshold (20) and re-arms when the count falls to
15 (`Raspberry Pi 5/gg_counter.py`). ByteTrack keeps each box's ID steady on
screen. Rewritten to say so:

- Specific Objective 2 and the Scope bullet on tracking
- 2.1, the Process paragraph and the paragraph after it
- 2.4 Synthesis, last paragraph, and Table 2 rows 1 and 3
- 3.2 Block Diagram text, 3.3 Flowchart text (all five paragraphs)
- 3.4 ByteTrack, Region of Interest and SQLite entries
- System Testing intro, TC_UNIT_02, TC_SYS_04, TC_SYS_07 (now "Stable Tracking
  and Count") and TC_SYS_08

## 2. What gets stored

v3 said every detection is written to the database. Only alerts, brief spikes
that were ignored, mutes, manual captures and test alerts are stored, plus a
latency summary every 100 frames. TC_SYS_08 now uses an alert and a manual
capture, both of which really appear in History.

## 3. Figures

Edited in `Figures/*.html` and re-rendered:

- Figure 1: tracking and counting descriptions
- Figure 26: counter label, "USB Ethernet" instead of "Wi-Fi", note on the repeater
- Figure 27 and Figure 28: redrawn around the real counting and alert logic
- Figure 37: eight training runs (v1 to v7 with a v6b), not seven

## 4. Facts

- Eight training runs, as Gen confirmed (2.1 and 3.5 text).
- New hardware entry "USB-to-Ethernet Adapter and Wi-Fi Repeater", and a
  matching System Design bullet. The Pi's built-in Wi-Fi could not join any
  network, so internet runs through the adapter and the repeater.

## 5. Usability scale

Objective 3c had no scale. Added Table 9 (five-point Likert scale with
weighted-mean ranges) and the weighted-mean formula after Table 8, and a List
of Tables entry. The ranges are a common convention; change them if the
adviser prefers others.

## 6. References

Checked against the publishers' pages:

- Meijer et al. is 2021, *Science Advances* 7(18), eaaz5803. Fixed in the list,
  in all six in-text citations and in the Figure 2 note.
- Acot et al.: title and journal were swapped; "Rallo" is "Rallos".
- Kang et al.: journal is *IEEE Access*, with a proper DOI.
- Murat & Kiran: journal is *Engineering Science and Technology, an
  International Journal*, 70, 102161.
- Corpuz: author is J. C. G. Corpuz.
- Nežerka et al.: author names formatted; in-text spelling made consistent.
- Chordiya et al.: removed "& Individual"; Gryech: "IInternet"; Robles:
  "Water 2025"; Magyar: stray ", 18".
- United Nations (n.d.) replaced with the dated *Sustainable Development Goals
  Report 2025*.
- KingSpec moved into alphabetical order.

## 7. Clean-up

- Removed 22 copies of the Acot link that had been pasted after every Chapter
  IV and V placeholder and after twelve reference entries.
- Chapter IV and V placeholders now use body text formatting instead of the
  references' hanging indent.

## Still open (Word comments in the file)

1. Floating barrier in the Scope: keep or delete.
2. Cover date still says August 2026.
3. Accuracy formula with true negatives: confirm with Prof. Jonel.
4. Genesis's bionote.

Not done here: the Raspberry Pi logo for the software components, and photos
of the step-up cable, touchscreen, buzzer and junction box.

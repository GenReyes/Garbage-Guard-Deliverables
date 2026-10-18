# Chapter 3: Component Materials

Written to the Smart-Bin pattern: a numbered figure, a source note, then two
paragraphs per part, the first saying what the component is and the second saying
what it does in Garbage-Guard. Split into hardware and software as you asked.

Everything below is taken from the code and the field reference in this
repository, not from the old draft. Where I could not confirm a part from the
repository it is marked **[need from Brian]**, and those are listed again at the
end.

---

## 3.x.1 Hardware Components

> Opening paragraph: This section describes the physical parts of one
> Garbage-Guard unit. Figures N to N+7 show each part, and the bill of materials
> in Appendix B gives quantities and costs.

### Raspberry Pi 5 (8 GB)

The Raspberry Pi 5 is a single-board computer built on a quad-core Arm
Cortex-A76 processor running at 2.4 GHz, supplied here with 8 GB of memory. It
exposes a 40-pin general-purpose input/output header, two micro-HDMI outputs,
USB 3.0 ports and Gigabit Ethernet, and it boots from a microSD card.

It is the whole of the processing side of Garbage-Guard. It pulls the camera's
RTSP stream, runs the detection model, applies the region of interest, keeps the
tracker and the accumulation counter, writes to the local database, drives the
buzzer on GPIO12, serves the dashboard over HTTP, and synchronises events to
Firebase. The 8 GB variant was chosen because the model, the tracker and the
video decoder hold their working buffers in memory at the same time. Active
cooling is fitted because sustained inference keeps the processor near its
thermal limit.

### Hikvision HiWatch HWI-B140H Network Camera

The HWI-B140H is a 4 MP fixed bullet network camera from Hikvision's HiWatch
series, built on a 1/3 inch progressive scan CMOS sensor with a maximum
resolution of 2560 x 1440 at 20 fps. It carries a fixed-focus M12 lens at F2.0,
an 850 nm infrared illuminator reaching about 30 m, and a 120 dB wide dynamic
range, and it is rated IP67 with an operating range of -30 to 60 degrees Celsius.
It accepts either 802.3af power over Ethernet or 12 V DC on a 5.5 mm coaxial
plug, drawing at most 5 W on the DC input (Hikvision, n.d.).

It is mounted above the creek and aimed along the water surface, and it is the
only sensor in the system. Everything Garbage-Guard reports is derived from this
one feed, which is why the IP67 rating and the wide temperature range matter for
an outdoor deployment. Because the region of interest is traced against the
camera's exact view, the mounting is fixed first and the region drawn afterwards,
never the reverse.

The camera is run on its 12 V DC input rather than on power over Ethernet, so the
Ethernet cable carries data only. Garbage-Guard reads the camera's sub-stream at
`/Streaming/Channels/102` rather than the main stream. The sub-stream is delivered
at 640 x 480 or 640 x 360, which is a useful match: the detection model was
trained at an image size of 640, so the Pi decodes frames at roughly the size the
model expects instead of downscaling a 2560 x 1440 stream on every frame.

### 5 V to 12 V USB-A to DC Barrel Step-up Cable

A step-up, or boost, converter raises a lower input voltage to a higher one. The
part used here is the cable form of that circuit, with a USB-A plug at one end,
the converter moulded into the lead, and a 5.5 mm DC barrel plug at the other. It
is rated 12 V at 1 A.

Its rating covers the load with room to spare. Hikvision specifies the
HWI-B140H at a maximum of 5 W on its 12 V input, which is about 0.42 A, so the
cable is rated at roughly two and a half times what the camera can draw. That
margin is worth stating in the chapter, because a converter run near its limit is
a common cause of intermittent faults in field equipment, and an intermittent
camera would look like a detection problem rather than a power one.

Its 5 V comes from the USB-A output on the Waveshare UPS HAT, not from a separate
adapter. The consequence is worth stating plainly in the chapter: because the
camera is powered through the same battery-backed supply as the Pi, a mains
failure does not blind the system. The camera and the processor go down together
or stay up together, which is what makes the uninterruptible supply meaningful
for a monitoring task rather than merely protecting the filesystem.

### Waveshare UPS HAT (E)

The Waveshare UPS HAT (E) is an uninterruptible power supply board carrying four
21700 lithium-ion cells. It mounts beneath the Raspberry Pi rather than on top of
it, connecting through high-current pogo pins that press against the underside of
the board, and it delivers 5 V at up to 6 A. It passes mains power through while
charging, takes over when that supply fails, and reports its state over I2C,
exposing voltage, current, power, the four individual cell voltages and the
charger input. It also provides a USB-A output.

The pogo-pin connection is the detail worth naming, because it leaves the Pi's
40-pin GPIO header completely free (Waveshare, n.d.). A conventional UPS board
sitting on that header would have taken it, and the buzzer needs three pins on
it. The choice of this particular board is therefore what allows the audible
alert to exist at all without a pass-through or a ribbon extension.

In Garbage-Guard it is what makes an unattended deployment safe. `gg_power.py`
reads the charge level over I2C and begins a five-minute countdown toward a clean
shutdown when the battery runs low, with a floor at five per cent and a grace
period after boot, so the Pi is never cut off mid-write. The dashboard shows the
battery panel from the same readings. Its USB-A output additionally feeds the
step-up converter that powers the camera, so the HAT carries the whole system
rather than the processor alone.

### Passive Buzzer Module

A passive buzzer has no oscillator of its own, so it makes no sound unless the
controller drives it with a changing signal. That is a limitation on paper and an
advantage in practice, because the pitch is then under software control and one
device can produce several distinguishable sounds.

It is wired with VCC to physical pin 17 at 3.3 V, its signal line to physical pin
32 at GPIO12, and ground to physical pin 34. `gg_buzzer.py` gives each event its
own sound and raises a repeating alarm when the accumulation threshold is
crossed. It drives the pin by software PWM by default and switches to hardware
PWM when GPIO12 has been configured for it, which is louder and steadier. Quiet
hours and a mute panel on the dashboard suppress it when a reading is being taken
by hand.

### 7-inch IPS Touchscreen

The display is a 7 inch IPS panel at 1024 x 600, an unbranded module of the kind
sold for single-board computers. It takes video on HDMI, connected to the Pi's
HDMI-A-2 output, and reports touch over USB. IPS is worth naming because the
panel is read at an angle as often as straight on when the unit is mounted at the
waterside, and IPS holds its colour and contrast off-axis where a cheaper TN
panel would not.

It is the operator's only local interface. The dashboard runs full screen on it,
and the region of interest is drawn by tapping points directly on the video, so
the touch layer is not a convenience but the means of configuring the system on
site. The screen saver in `gg_screen.py` turns the HDMI output off after a timer,
sooner on battery, and `gg_touch.py` reads the touch device directly from
`/dev/input` so that a tap wakes the screen while the output is off.

### Raspberry Pi Active Cooler

The official Raspberry Pi Active Cooler is a combined heatsink and fan that
clips onto the Pi 5's mounting points and is driven from the board's dedicated
fan header, which lets the firmware vary its speed with the processor
temperature.

Sustained inference is the reason it is fitted rather than an optional extra.
Running detection on every frame holds the processor near its thermal limit
indefinitely, and a Pi 5 that reaches that limit reduces its clock speed, which
would show up as rising latency and a falling frame rate rather than as an
obvious fault. Active cooling keeps the measured latency in Chapter 4
representative of the hardware rather than of its thermal state.

### Extension Lead and Case Fans

Mains reaches the unit through an extension lead, which serves as the
distribution point inside the enclosure. Two things are plugged into it: the
UPS HAT's 5 V, 5 A charger, and a pair of USB fans mounted on the side of the
case.

The split matters, and it should be stated rather than glossed. The UPS HAT, and
therefore the Pi, its active cooler and the camera, all continue through a mains
failure. The two case fans do not, because they are fed from the extension lead
directly rather than through the HAT. On battery the enclosure fans stop while
the processor carries on running detection at full rate inside a closed metal
box.

That is a deliberate trade. Running the case fans from the battery would shorten
the time the system can keep detecting, and detection is the thing worth
preserving. It does, however, leave a limitation that belongs in Chapter 5 rather
than being left for a panellist to find: sustained operation on battery is
thermally untested, and the uptime figures gathered on mains will not predict it.
The Pi's own active cooler stays powered throughout, so the processor is not
unprotected, but the enclosure's air exchange is.

### SanDisk 64 GB High Endurance microSD Card

The card is a SanDisk High Endurance model. The High Endurance line is built for
devices that write continuously, such as dashboard cameras and surveillance
recorders, rather than for the occasional large transfers a standard card is
rated for.

The card carries Raspberry Pi OS and nothing else. The detection data lives on
the solid-state drive described below, so the card's workload is the operating
system's own: journal writes, logs and the ordinary housekeeping of a machine
that is never switched off. That is lighter than the detection workload but it is
still continuous, and it runs for the whole of a field deployment, which is why
an endurance-rated card rather than a standard one is the sensible choice for a
system intended to be left running unattended.

### KingSpec 256 GB M.2 SSD with Waveshare PCIe to 2-Channel M.2 Adapter

The solid-state drive is a KingSpec 256 GB module in the 2242 form factor,
carried on a Waveshare PCIe to 2-channel M.2 adapter, the same manufacturer as
the UPS board, which connects to the Raspberry Pi 5's PCIe interface. Being a
two-channel adapter it takes a second M.2 module alongside the first, so storage
can be extended for a longer deployment without changing the board or the
mounting. The Pi 5 is the first model in the line to
expose PCIe directly, which is what makes an attached solid-state drive practical
rather than a USB afterthought.

The drive carries the project folder, and with it the detection work: the
exported model, the SQLite database and the snapshot store. The operating system
stays on the microSD card, so the two write workloads are separated onto
different devices.

That separation is the point of the arrangement and should be stated as a design
decision rather than left as a parts-list entry. Garbage-Guard writes a snapshot
for every recorded event and appends to the database continuously for as long as
it is deployed, which is a sustained small-write workload of exactly the kind
that wears flash storage. Putting it on a solid-state drive rather than on the
boot card means the heavy writing happens on the device best able to absorb it,
and that a storage failure there would not take the operating system with it.
Solid-state storage is also faster for the two operations that matter most in
practice, saving annotated snapshots while detection continues and loading the
model at start-up.

### Metal Waterproof CCTV Junction Box

The electronics are housed in a metal waterproof junction box of the type sold
for closed-circuit television installations, measuring 25 by 30 by 15
centimetres. Boxes of this class are made for outdoor camera work, so they come
with a gasketed lid and are intended to be mounted and left in the weather.

It holds the Raspberry Pi 5 with its active cooler, the UPS HAT and the step-up
converter, with openings cut for the mains extension lead and the camera cabling
and two USB fans mounted on one side. Roughly eleven litres of internal volume is
generous for the contents, which matters less for fit than for thermal mass and
air movement around the board.

The boards are stacked in a fixed order: the Waveshare UPS HAT at the bottom, the
Raspberry Pi 5 above it with its active cooler, and the PCIe to M.2 adapter
carrying the solid-state drive on top. The order follows from how each board
connects. The UPS HAT reaches the Pi through pogo pins pressing against its
underside, so it has to sit below. The adapter reaches the PCIe connector by
ribbon, so it can sit above. Between them the Pi's 40-pin header stays clear,
which is where the buzzer's three wires go, to pins 17, 32 and 34.

The cut openings are sealed with a black rubberised water sealant. The enclosure
is not itself left in the weather: the unit is sited under shelter and only the
camera is mounted outdoors, wired back to the box. That division is worth stating
plainly in the installation description, because it is what makes the
arrangement sound. The camera is the part exposed to rain and sun and it is the
part carrying a manufacturer's IP67 rating and a minus 30 to 60 degree operating
range. The enclosure holds the parts that have no such rating, and it is kept out
of the weather accordingly rather than being asked to substitute for one.

---

## 3.x.2 Software Components

> Opening paragraph: This section describes the software of the system, grouped
> by where it runs: on the Raspberry Pi, in the cloud, and on the operator's
> phone.

### Raspberry Pi OS and the Python Environment

The unit runs Raspberry Pi OS with the detection program in a virtual environment
at `~/gg-env`, kept separate from the system packages. The separation is the
reason `lgpio` has to be linked into the environment by hand before the buzzer
will work, which is documented in the Pi README.

`install_autostart.sh` registers the launcher so that switching the Pi on is
enough to bring the system up: it checks the camera, raises the network profile
if needed, activates the environment, opens the dashboard full screen and starts
detection. If no network is in range it shows a Wi-Fi setup screen first.

### YOLO26n Detection Model, Exported to NCNN

The detector is a YOLO26n convolutional neural network, trained on dataset_v7 and
exported to NCNN, a neural network inference framework built for mobile and
embedded processors. The exported model is loaded through the Ultralytics API as
`garbageguard_v7_best_ncnn_model`. NCNN is used rather than the PyTorch weights
because it is markedly faster on the Pi's Arm processor.

The model classifies three categories, in order: bottle, bag and food container.
Its scores on the frozen test set are precision 0.852, recall 0.685 and mAP@0.5
0.745, and on the creek-only subset precision 0.742, recall 0.534 and mAP@0.5
0.593. These are the figures Chapter 4 reports against.

### ByteTrack Multi-Object Tracker

ByteTrack is a tracking-by-detection algorithm that associates detections across
consecutive frames, including low-confidence ones that a simpler tracker
discards, and assigns each object a stable identity for as long as it stays in
view.

It is invoked through the Ultralytics tracking interface with `persist=True` so
that identities survive between frames. It is what makes the accumulation count
meaningful: without it, one bottle drifting across the camera's view would be
counted once per frame rather than once. Each box on the dashboard carries its
identity number, and a number is released when its object leaves.

### OpenCV and NumPy

OpenCV reads the camera's RTSP stream, decodes the frames and draws the overlays,
and NumPy carries the frame data as arrays. Together they handle every step
between the camera and the model, and between the model's output and what appears
on the screen.

### Region of Interest and Accumulation Counter

The region of interest is a polygon traced on the video by the operator, stored
in `gg_settings.json` and reloaded at start-up. Detections whose position falls
outside it are discarded before counting, which is what prevents the bank and the
bridge wall from entering the tally.

The counter in `gg_counter.py` tallies tracked identities inside that region
against a threshold, twenty by default and adjustable from the Settings tab.
Because the model's recall is about 0.70, a displayed count of twenty does not
mean twenty items are present, so the threshold is calibrated on site rather than
assumed. That calibration procedure is in the field reference.

### SQLite Database and Snapshot Store

Detection events are written to a local SQLite database on the Pi, with a
snapshot saved for every history entry showing the boxes, the monitored area and
the count stamped onto the frame. Snapshots are kept in `~/gg/snapshots/`, named
by event and time.

Keeping the record locally means the system loses nothing when the network drops,
and the saved snapshot means any logged reading can be checked afterwards against
the image that produced it.

### Touchscreen Dashboard

The dashboard is a single HTML file served over HTTP from the Pi by Python's
built-in `http.server`, with no build step and no external dependencies. It has
three tabs: Monitor, History and Settings. **This corrects the Revision Plan,
which assumed a Flask server; the code uses the standard library instead.**

Monitor carries the live feed with bounding boxes, the running count, the frame
rate and the mute panel, and it is where the region of interest is drawn. History
lists recorded events with their snapshots and a filter bar. Settings holds the
detection threshold, the alert preferences, the screen saver and the power
options.

### Firebase Synchronisation

Firebase is used as the cloud link between the unit and the phone. `gg_cloud.py`
pushes events and state upward, and `firebase.rules.json` holds the access rules.

It exists so that an operator does not have to be standing at the creek to know
the threshold has been crossed. The Pi remains the system of record; Firebase
carries a copy.

### Android Application

The Android application is written in Kotlin with Jetpack Compose, under package
`com.garbageguard.app`, and is at version 12. It receives push alerts, shows the
detection history and allows the detection settings to be changed remotely.

It is the second half of the alerting path. The buzzer and the on-screen warning
serve whoever is present; the application serves whoever is not.

---

## Outstanding

Nothing. Every part is identified and every connection accounted for.

**The USB-A hub is out.** It appears in the old Chapter 3 component list but
nowhere in the code, and Brian has confirmed it is not in the build. It is
removed, as the vibration motor was.

The Smart-Bin pattern puts a product photograph with every component and cites it
as "Note. Image from <seller> (n.d.)", recorded in a `sources.txt` alongside. I
can do the same here, but fetching those images means downloading from vendor
sites, so tell me to go ahead and I will.

## Reference to add

Hikvision. (n.d.). *HWI-B140H: 4 MP fixed bullet network camera* [Product
specification]. Retrieved October 18, 2026, from
https://www.hikvision.com/ph/products/IP-Products/Network-Cameras/HiWatch-Series/HWI-B140H--M-

Waveshare. (n.d.). *UPS HAT (E)* [Product documentation]. Retrieved October 18,
2026, from https://www.waveshare.com/wiki/UPS_HAT_(E)

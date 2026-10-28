"""
Garbage-Guard live detection engine + touchscreen dashboard.

    source ~/gg-env/bin/activate
    cd ~/gg
    python garbageguard_live.py

Then open http://localhost:8080 on the Pi, or http://<pi-ip>:8080 from a
phone on the same network.
"""

import os
os.environ["OPENCV_FFMPEG_CAPTURE_OPTIONS"] = "rtsp_transport;tcp"

import argparse
import collections
import json
import sqlite3
import statistics
import threading
import time
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np
from ultralytics import YOLO

from gg_counter import AccumulationCounter, DisplayIds, scale_polygon, tally
import gg_buzzer
import gg_clean
import gg_clock
import gg_cloud
import gg_model
import gg_power
import gg_screen
import gg_touch
import gg_ups
import gg_view
import gg_web
from gg_snapcard import add_card

CAMERA_FILE = Path(__file__).resolve().parent / "camera.txt"
SOURCE = "rtsp://admin:CHANGE_THIS_PASSWORD@192.168.1.64:554/Streaming/Channels/102"
if CAMERA_FILE.is_file():
    line = CAMERA_FILE.read_text().strip()
    if line:
        SOURCE = line
MODEL_PATH = gg_model.DEFAULT

SMOOTH_WINDOW = 15
ALERT_COOLDOWN_SEC = 60
WATCHDOG_S = 12
PERF_FLUSH_EVERY = 100
WEB_PORT = 8080

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "garbageguard.db"
SNAPSHOT_DIR = BASE_DIR / "snapshots"
SETTINGS_PATH = BASE_DIR / "gg_settings.json"

DEFAULT_SETTINGS = {"conf": 0.40, "threshold": 20, "roi": [],
                    "auto_mute": False, "auto_mute_s": 900,
                    **gg_screen.DEFAULTS, **gg_power.DEFAULTS, **gg_buzzer.DEFAULTS,
                    **gg_clean.DEFAULTS, **gg_view.DEFAULTS, **gg_model.DEFAULTS}

CLASS_COLORS = {
    "bottle": (255, 128, 0),
    "bag": (255, 0, 200),
    "food_container": (0, 220, 0),
}
DEFAULT_COLOR = (200, 200, 200)


def open_db():
    conn = sqlite3.connect(str(DB_PATH), timeout=10)
    conn.row_factory = sqlite3.Row
    return conn


def init_database():
    conn = open_db()
    cur = conn.cursor()
    cur.execute("""
        CREATE TABLE IF NOT EXISTS alerts (
            id               INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp        TEXT    NOT NULL,
            smoothed_count   INTEGER NOT NULL,
            raw_count        INTEGER NOT NULL,
            n_bottle         INTEGER NOT NULL,
            n_bag            INTEGER NOT NULL,
            n_food_container INTEGER NOT NULL,
            peak_confidence  REAL,
            threshold_used   INTEGER NOT NULL,
            snapshot_path    TEXT
        )
    """)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS perf_samples (
            id                INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp         TEXT    NOT NULL,
            frames_in_window  INTEGER NOT NULL,
            mean_latency_ms   REAL    NOT NULL,
            median_latency_ms REAL    NOT NULL,
            max_latency_ms    REAL    NOT NULL
        )
    """)
    cols = {r["name"] for r in cur.execute("PRAGMA table_info(alerts)")}
    if "event" not in cols:
        cur.execute("ALTER TABLE alerts ADD COLUMN event TEXT DEFAULT 'accumulation'")
    if "read" not in cols:
        cur.execute("ALTER TABLE alerts ADD COLUMN read INTEGER DEFAULT 0")
    conn.commit()
    conn.close()


def load_settings():
    s = dict(DEFAULT_SETTINGS)
    if SETTINGS_PATH.is_file():
        try:
            saved = json.loads(SETTINGS_PATH.read_text())
            if isinstance(saved, dict):
                s.update({k: saved[k] for k in DEFAULT_SETTINGS if k in saved})
        except (json.JSONDecodeError, OSError):
            pass
    return s


def save_settings(s):
    try:
        SETTINGS_PATH.write_text(json.dumps(s, indent=2))
    except OSError:
        pass


def ink_scale(frame):
    return max(1.0, frame.shape[1] / 640.0)


def draw_roi(frame, roi_pixels):
    if roi_pixels is None:
        return
    overlay = frame.copy()
    cv2.fillPoly(overlay, [roi_pixels], (148, 158, 31))
    cv2.addWeighted(overlay, 0.15, frame, 0.85, 0, dst=frame)
    cv2.polylines(frame, [roi_pixels], True, (148, 158, 31),
                  max(2, round(2 * ink_scale(frame))))


def draw_detection(frame, box, label, color, inside):
    k = ink_scale(frame)
    x1, y1, x2, y2 = [int(v) for v in box]
    if not inside:
        cv2.rectangle(frame, (x1, y1), (x2, y2), (120, 120, 120), max(1, round(k)))
        return
    cv2.rectangle(frame, (x1, y1), (x2, y2), color, max(2, round(2 * k)))
    size, weight = 0.45 * k, max(1, round(k))
    (tw, th), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, size, weight)
    pad = round(3 * k)
    cv2.rectangle(frame, (x1, y1 - th - 2 * pad), (x1 + tw + 2 * pad, y1), color, -1)
    cv2.putText(frame, label, (x1 + pad, y1 - pad),
                cv2.FONT_HERSHEY_SIMPLEX, size, (0, 0, 0), weight, cv2.LINE_AA)


class FrameReader:
    """Keeps the RTSP queue drained so the engine always gets the newest frame."""

    STALE_S = 3.0

    def __init__(self, open_fn):
        self.open_fn = open_fn
        self.lock = threading.Lock()
        self.frame = None
        self.captured_at = 0.0
        self.last_ok = 0.0
        self.seq = 0
        self.stamps = collections.deque(maxlen=90)
        self.stop_flag = threading.Event()
        self.thread = None
        self.started = time.perf_counter()

    @property
    def alive(self):
        return time.perf_counter() - self.last_ok <= self.STALE_S

    def silent_for(self):
        ref = self.last_ok or self.started
        return time.perf_counter() - ref

    def start(self):
        self.thread = threading.Thread(target=self._loop, daemon=True)
        self.thread.start()
        return self

    def _loop(self):
        cap = self.open_fn()
        misses = 0
        while not self.stop_flag.is_set():
            ok, frame = cap.read()
            if self.stop_flag.is_set():
                break
            if not ok:
                misses += 1
                if misses > 50:
                    print("Stream lost. Reconnecting...")
                    cap.release()
                    time.sleep(2.0)
                    if self.stop_flag.is_set():
                        return
                    cap = self.open_fn()
                    misses = 0
                time.sleep(0.02)
                continue
            misses = 0
            now = time.perf_counter()
            with self.lock:
                self.frame = frame
                self.captured_at = now
                self.last_ok = now
                self.seq += 1
                self.stamps.append(now)
        cap.release()

    def latest(self, after_seq):
        with self.lock:
            if self.frame is None or self.seq == after_seq:
                return None, after_seq, 0.0
            return self.frame, self.seq, self.captured_at

    def fps(self):
        with self.lock:
            stamps = list(self.stamps)
        recent = [t for t in stamps if time.perf_counter() - t <= 2.0]
        if len(recent) < 2:
            return 0.0
        return (len(recent) - 1) / (recent[-1] - recent[0])

    def stop(self):
        self.stop_flag.set()


def jpg_names(folder):
    try:
        with os.scandir(folder) as it:
            return {e.name for e in it if e.name.lower().endswith(".jpg")}
    except OSError:
        return set()


def _short(e, n=120):
    text = str(e).strip().splitlines()[0] if str(e).strip() else type(e).__name__
    return text if len(text) <= n else text[:n - 1] + "…"


def reset_tracker(model):
    for t in getattr(getattr(model, "predictor", None), "trackers", None) or []:
        try:
            t.reset()
        except Exception:
            pass


class Engine:
    def __init__(self, source, model_path, settings):
        self.source = source
        self.view = gg_view.StreamChoice(source, settings.get("stream", "main"))
        self.frame_size = None
        self.det_box = None
        self.model_path = str(model_path)
        self.model_name = Path(self.model_path).name
        self.model_note = ""
        self.model_request = None
        self.model_loading = None
        self.model_flag = threading.Event()
        self._models = ([], 0.0)
        self.settings = settings
        self.lock = threading.Lock()
        self.stop_flag = threading.Event()

        self.jpeg = None
        self.raw_frame = None
        self.annotated = None
        self.snapshot_dir = SNAPSHOT_DIR
        self.clean = gg_clean.Clean(settings, SNAPSHOT_DIR, save=lambda: save_settings(settings))
        self.connected = False
        self.counts = {}
        self.raw_count = 0
        self.smoothed = 0
        self.peak_conf = 0.0
        self.latency_ms = 0.0
        self.alert_active = False
        self.mute_until = 0.0
        self.mute_total = 0
        self.auto_muted = False
        self.frames = 0
        self.reader = None
        self.reconnect_flag = threading.Event()
        self.reconnect_reason = ""
        self.reconnects = 0
        self.proc_stamps = collections.deque(maxlen=90)
        self.all_latencies = []
        self.started_at = time.time()
        self.on_alert = None
        self.on_event = None

        self.counter = AccumulationCounter(
            threshold=settings["threshold"],
            window=SMOOTH_WINDOW,
            cooldown=ALERT_COOLDOWN_SEC,
        )
        self.names = {}
        self.display_ids = DisplayIds()

    def snapshot_state(self):
        with self.lock:
            avg = (sum(self.all_latencies) / len(self.all_latencies)
                   if self.all_latencies else 0.0)
            recent = [t for t in self.proc_stamps if time.perf_counter() - t <= 2.0]
            proc_fps = ((len(recent) - 1) / (recent[-1] - recent[0])
                        if len(recent) >= 2 else 0.0)
            cam_fps = self.reader.fps() if self.reader is not None else proc_fps
            if not self.connected:
                cam_fps = proc_fps = 0.0
            return {
                "connected": self.connected,
                "counts": dict(self.counts),
                "raw": self.raw_count,
                "smoothed": self.smoothed,
                "threshold": self.counter.on,
                "rearm": self.counter.off,
                "conf": self.settings["conf"],
                "roi": list(self.settings["roi"]),
                "latency_ms": round(self.latency_ms, 1),
                "avg_latency_ms": round(avg, 1),
                "fps": round(proc_fps, 1),
                "cam_fps": round(cam_fps, 1),
                "alert_active": self.alert_active,
                "muted": self._muted_locked(),
                "mute_left": self._mute_left_locked(),
                "mute_total": self.mute_total,
                "auto_muted": self.auto_muted and self._muted_locked(),
                "auto_mute": bool(self.settings.get("auto_mute")),
                "auto_mute_s": int(self.settings.get("auto_mute_s", 0)),
                "frames": self.frames,
                **self.view.fields(),
                "frame_size": ("%dx%d" % self.frame_size) if self.frame_size else None,
                "detect_size": int(self.settings["detect_size"]),
                "crop": bool(self.settings["crop"]),
                "detect_box": list(self.det_box) if self.det_box else None,
                "model": self.model_name,
                "model_label": gg_model.label(self.model_name),
                "model_wanted": self.model_request or self.model_loading or self.settings.get("model"),
                "model_loading": self.model_request is not None or self.model_loading is not None,
                "models": self._installed_models(),
                "model_note": self.model_note,
                "reconnects": self.reconnects,
                "uptime_s": int(time.time() - self.started_at),
            }

    def _muted_locked(self):
        return self.mute_until == float("inf") or self.mute_until > time.time()

    def _mute_left_locked(self):
        if self.mute_until == float("inf"):
            return None
        left = self.mute_until - time.time()
        return int(left) if left > 0 else 0

    def is_muted(self):
        with self.lock:
            return self._muted_locked()

    def set_mute(self, seconds=None, cancel=False, auto=False):
        with self.lock:
            was = self._muted_locked()
            if cancel:
                self.mute_until = 0.0
                self.mute_total = 0
            elif seconds in (0, None):
                self.mute_until = float("inf")
                self.mute_total = 0
            else:
                self.mute_total = max(1, int(seconds))
                self.mute_until = time.time() + self.mute_total
            self.auto_muted = auto and not cancel
            now_muted = self._muted_locked()
            left = self._mute_left_locked()
        if now_muted != was:
            if now_muted:
                self._log_simple("auto_muted" if auto else "muted")
            else:
                self._log_simple("unmuted")
        return {"muted": now_muted, "mute_left": left}

    def set_auto_mute(self, on=None, seconds=None):
        with self.lock:
            if on is not None:
                self.settings["auto_mute"] = bool(on)
            if seconds is not None:
                self.settings["auto_mute_s"] = max(0, int(seconds))
            save_settings(self.settings)
            return {"auto_mute": self.settings["auto_mute"],
                    "auto_mute_s": self.settings["auto_mute_s"]}

    def clear_mute(self, log=True):
        with self.lock:
            was = self._muted_locked()
            self.mute_until = 0.0
            self.mute_total = 0
            self.auto_muted = False
        if was and log:
            self._log_simple("unmuted")
        return was

    def log_note(self, event):
        self._log_simple(event)

    def _log_simple(self, event):
        st = self.snapshot_state()
        self._log_event(event, st["counts"], st["raw"], st["smoothed"], None, None)

    def mark_read(self, ids=None, every=False, read=True):
        val = 1 if read else 0
        conn = open_db()
        if every:
            cur = conn.execute("UPDATE alerts SET read=?", (val,))
        elif ids:
            cur = conn.execute(
                "UPDATE alerts SET read=? WHERE id IN (%s)" % ",".join("?" * len(ids)),
                [val] + [int(i) for i in ids])
        else:
            conn.close()
            return {"changed": 0}
        n = cur.rowcount
        conn.commit()
        conn.close()
        return {"changed": max(0, n)}

    def get_jpeg(self):
        with self.lock:
            return self.jpeg

    def update_settings(self, conf=None, threshold=None):
        with self.lock:
            if conf is not None:
                self.settings["conf"] = max(0.05, min(0.95, float(conf)))
            if threshold is not None:
                t = max(1, int(threshold))
                self.settings["threshold"] = t
                self.counter.set_threshold(t)
            save_settings(self.settings)
            return dict(self.settings)

    def set_view(self, stream=None, detect_size=None, crop=None):
        with self.lock:
            if detect_size is not None:
                self.settings["detect_size"] = detect_size
            if crop is not None:
                self.settings["crop"] = crop
            changed = stream is not None and stream != self.settings.get("stream")
            if stream is not None:
                self.settings["stream"] = stream
            gg_view.clean_settings(self.settings)
            save_settings(self.settings)
            if changed:
                self.view.want(self.settings["stream"])
            out = {k: self.settings[k] for k in gg_view.DEFAULTS}
        if changed:
            self.reconnect("stream changed")
        return out

    def _installed_models(self):
        found, at = self._models
        if time.monotonic() - at > 5:
            found = gg_model.list_models(BASE_DIR)
            self._models = (found, time.monotonic())
        return [dict(m) for m in found]

    def set_model(self, name):
        if not gg_model.valid(name, BASE_DIR):
            return {"ok": False, "error": "That model is not installed on the Pi.",
                    "model": self.model_name}
        with self.lock:
            self.settings["model"] = name
            save_settings(self.settings)
            self.model_note = ""
            if name == self.model_name:
                self.model_request = None
                return {"ok": True, "model": self.model_name, "pending": None}
            self.model_request = name
        self.model_flag.set()
        return {"ok": True, "model": self.model_name, "pending": name}

    def _load_model(self, path, check=True):
        model = YOLO(str(path), task="detect")
        names = model.names
        problem = gg_model.class_problem(names)
        if check and problem:
            raise ValueError(problem)
        if check:
            model.track(np.zeros((360, 640, 3), np.uint8), persist=True,
                        tracker="bytetrack.yaml", imgsz=640, verbose=False)
            reset_tracker(model)
        return model

    def _first_model(self):
        default = BASE_DIR / gg_model.DEFAULT
        if Path(self.model_path).name == gg_model.DEFAULT or not gg_model.valid(gg_model.DEFAULT, BASE_DIR):
            return self._load_model(self.model_path, check=False)
        try:
            return self._load_model(self.model_path)
        except Exception as e:
            bad = gg_model.label(self.model_name)
            print(f"[MODEL] Could not load {bad} ({e}). Starting with v7.", flush=True)
            with self.lock:
                self.model_note = f"Could not load {bad} at start, using v7: {_short(e)}"
                self.model_path = str(default)
                self.model_name = default.name
                self.settings["model"] = default.name
                save_settings(self.settings)
            return self._load_model(default, check=False)

    def _swap_model(self, model):
        with self.lock:
            name, self.model_request = self.model_request, None
            self.model_loading = name
        try:
            return self._swap_to(model, name)
        finally:
            with self.lock:
                self.model_loading = None

    def _swap_to(self, model, name):
        if not name or name == self.model_name:
            return model
        new_label, old_label = gg_model.label(name), gg_model.label(self.model_name)
        print(f"[MODEL] Loading {new_label}...", flush=True)
        try:
            if not gg_model.valid(name, BASE_DIR):
                raise ValueError("its folder is missing or incomplete")
            new = self._load_model(BASE_DIR / name)
        except Exception as e:
            print(f"[MODEL] Could not load {new_label}, still using {old_label}: {e}", flush=True)
            with self.lock:
                self.model_note = f"Could not load {new_label}, still using {old_label}: {_short(e)}"
                if self.model_request is None:
                    self.settings["model"] = self.model_name
                    save_settings(self.settings)
            return model
        with self.lock:
            self.model_path = str(BASE_DIR / name)
            self.model_name = name
            self.names = new.names
            self.model_note = ""
            self.counts = {}
            self.raw_count = 0
            self.peak_conf = 0.0
            self.display_ids = DisplayIds()
        print(f"[MODEL] Now using {new_label}. Classes: {self.names}", flush=True)
        return new

    def set_roi(self, points):
        clean = []
        for p in points or []:
            x, y = float(p[0]), float(p[1])
            clean.append([max(0.0, min(1.0, x)), max(0.0, min(1.0, y))])
        if len(clean) < 3:
            clean = []
        with self.lock:
            self.settings["roi"] = clean
            save_settings(self.settings)
            return clean

    def alerts_sig(self):
        """Changes whenever a record is added, deleted or marked read or unread."""
        conn = open_db()
        n, top, unread = conn.execute(
            "SELECT COUNT(*), COALESCE(MAX(id),0), COALESCE(SUM(COALESCE(read,0)=0),0)"
            " FROM alerts").fetchone()
        conn.close()
        return f"{n}.{top}.{unread}.{len(jpg_names(SNAPSHOT_DIR))}"

    def recent_alerts(self, limit=50):
        """Newest records first. A limit of 0 or less returns every record."""
        limit = int(limit)
        conn = open_db()
        rows = conn.execute(
            "SELECT id, timestamp, event, smoothed_count, raw_count, n_bottle, n_bag,"
            " n_food_container, peak_confidence, threshold_used, snapshot_path,"
            " COALESCE(read,0) AS read FROM alerts ORDER BY id DESC LIMIT ?",
            (limit if limit > 0 else -1,)
        ).fetchall()
        conn.close()
        pics = jpg_names(SNAPSHOT_DIR)
        cleans = jpg_names(SNAPSHOT_DIR / gg_clean.FOLDER)
        out = []
        for r in rows:
            d = dict(r)
            name = Path(d["snapshot_path"]).name if d.get("snapshot_path") else None
            d["snapshot"] = name if name in pics else None
            d["clean"] = d["snapshot"] if d["snapshot"] in cleans else None
            out.append(d)
        return out

    def delete_alerts(self, ids=None, every=False, dfrom=None, dto=None):
        where, args = [], []
        if every:
            where.append("1=1")
        if ids:
            where.append("id IN (%s)" % ",".join("?" * len(ids)))
            args += [int(i) for i in ids]
        if dfrom:
            where.append("date(timestamp) >= date(?)")
            args.append(dfrom)
        if dto:
            where.append("date(timestamp) <= date(?)")
            args.append(dto)
        if not where:
            return {"deleted": 0, "files_removed": 0}

        clause = " AND ".join(where) if not every else "1=1"
        conn = open_db()
        rows = conn.execute(
            f"SELECT id, snapshot_path FROM alerts WHERE {clause}", args).fetchall()
        removed = 0
        folder = Path(SNAPSHOT_DIR).resolve()
        for r in rows:
            sp = r["snapshot_path"]
            if not sp:
                continue
            try:
                target = Path(sp).resolve()
                if target.parent == folder:
                    gg_clean.remove(target)
                    if target.is_file():
                        target.unlink()
                        removed += 1
            except OSError:
                pass
        conn.execute(f"DELETE FROM alerts WHERE {clause}", args)
        conn.commit()
        conn.close()
        self.clean.forget()
        return {"deleted": len(rows), "files_removed": removed}

    def _log_event(self, event, counts, raw, smoothed, peak, snap):
        conn = open_db()
        conn.execute(
            "INSERT INTO alerts (timestamp, event, smoothed_count, raw_count,"
            " n_bottle, n_bag, n_food_container, peak_confidence, threshold_used,"
            " snapshot_path) VALUES (?,?,?,?,?,?,?,?,?,?)",
            (datetime.now().isoformat(sep=" ", timespec="seconds"), event,
             int(smoothed), int(raw), int(counts.get("bottle", 0)),
             int(counts.get("bag", 0)), int(counts.get("food_container", 0)),
             float(peak) if peak else None, int(self.counter.on),
             str(snap) if snap else None))
        conn.commit()
        conn.close()
        if self.on_event:
            try:
                self.on_event(event)
            except Exception as e:
                print(f"[BUZZER] {e}", flush=True)

    def _save_snapshot(self, tag, state):
        with self.lock:
            src = self.annotated if self.annotated is not None else self.raw_frame
            frame = None if src is None else src.copy()
            raw = None if self.raw_frame is None else self.raw_frame.copy()
            peak = self.peak_conf
        if frame is None:
            return None
        frame = add_card(frame, tag, state, peak)
        path = SNAPSHOT_DIR / f"{tag}_{datetime.now():%Y%m%d_%H%M%S_%f}.jpg"
        cv2.imwrite(str(path), frame)
        if raw is not None and self.clean.wanted(tag):
            target = gg_clean.path_for(path)
            try:
                target.parent.mkdir(parents=True, exist_ok=True)
                cv2.imwrite(str(target), raw, [cv2.IMWRITE_JPEG_QUALITY, 95])
            except (OSError, cv2.error) as e:
                print(f"[CLEAN] {e}", flush=True)
            self.clean.forget()
        return path

    def fire_test_alert(self):
        return self._manual_event("test", "test")

    def capture_snapshot(self):
        return self._manual_event("manual", "capture")

    def _manual_event(self, event, tag):
        state = self.snapshot_state()
        state["timestamp"] = datetime.now().isoformat(sep=" ", timespec="seconds")
        snap = self._save_snapshot(tag, state)
        self._log_event(event, state["counts"], state["raw"],
                        state["smoothed"], self.peak_conf, snap)
        return snap is not None

    def _open(self):
        url = self.view.url
        if str(url).isdigit():
            return cv2.VideoCapture(int(url))
        params = []
        for name, ms in (("CAP_PROP_OPEN_TIMEOUT_MSEC", 5000),
                         ("CAP_PROP_READ_TIMEOUT_MSEC", 5000)):
            if hasattr(cv2, name):
                params += [getattr(cv2, name), ms]
        cap = cv2.VideoCapture(url, cv2.CAP_FFMPEG, params) if params \
            else cv2.VideoCapture(url)
        if str(url).lower().startswith("rtsp"):
            cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        return cap

    def reconnect(self, reason="manual"):
        self.reconnect_flag.set()
        self.reconnect_reason = reason
        return True

    def _swap_reader(self):
        old = self.reader
        print(f"Camera reconnect ({self.reconnect_reason}).")
        if old is not None:
            old.stop()
        reader = FrameReader(self._open).start()
        with self.lock:
            self.reader = reader
            self.connected = False
            self.jpeg = None
            self.reconnects += 1
            self.display_ids = DisplayIds()
        return reader

    def run(self, show_window=False):
        print(f"Loading model: {self.model_path}")
        model = self._first_model()
        self.names = model.names
        print(f"Classes: {self.names}")

        live = (str(self.source).lower().startswith("rtsp")
                or str(self.source).isdigit())

        cap = None
        reader = None
        if live:
            reader = FrameReader(self._open).start()
            self.reader = reader
        else:
            cap = self._open()
            if not cap.isOpened():
                print("ERROR: could not open the video source.")
                return

        seq = 0
        latency_window = []

        while not self.stop_flag.is_set():
            if self.model_flag.is_set():
                self.model_flag.clear()
                model = self._swap_model(model)
            if live:
                if self.reconnect_flag.is_set():
                    self.reconnect_flag.clear()
                    reader = self._swap_reader()
                    seq = 0
                elif reader.silent_for() > WATCHDOG_S:
                    self.reconnect_reason = "no frames"
                    if not reader.last_ok and self.view.main_gave_nothing():
                        print(f"[STREAM] {self.view.note}.")
                    reader = self._swap_reader()
                    seq = 0
                frame, seq, captured_at = reader.latest(seq)
                if frame is None:
                    with self.lock:
                        self.connected = reader.alive
                    time.sleep(0.005)
                    continue
                t0 = captured_at
            else:
                t0 = time.perf_counter()
                ok, frame = cap.read()
                if not ok:
                    print("End of video file.")
                    break

            with self.lock:
                conf = self.settings["conf"]
                roi_norm = list(self.settings["roi"])
                size = int(self.settings["detect_size"])
                use_crop = bool(self.settings["crop"])

            h, w = frame.shape[:2]
            roi_poly = scale_polygon(roi_norm, w, h)
            box = gg_view.crop_box(roi_poly, w, h) if use_crop else None
            if box != self.det_box or (w, h) != self.frame_size:
                reset_tracker(model)
                self.display_ids = DisplayIds()
            if box is None:
                part, dx, dy = frame, 0, 0
            else:
                part, dx, dy = frame[box[1]:box[3], box[0]:box[2]], box[0], box[1]

            try:
                results = model.track(np.ascontiguousarray(part), persist=True,
                                      tracker="bytetrack.yaml",
                                      conf=conf, imgsz=size, verbose=False)
            except Exception as e:
                if size == 640:
                    raise
                print(f"[DETECT] {size} px failed ({e}); going back to 640 px.", flush=True)
                with self.lock:
                    self.settings["detect_size"] = 640
                    self.view.note = f"The model could not run at {size} px, using 640 px"
                    save_settings(self.settings)
                reset_tracker(model)
                continue
            latency_ms = (time.perf_counter() - t0) * 1000.0

            dets = []
            boxes = results[0].boxes
            if boxes is not None and len(boxes) > 0:
                xyxy = boxes.xyxy.cpu().numpy()
                cls = boxes.cls.cpu().numpy().astype(int)
                cf = boxes.conf.cpu().numpy()
                ids = (boxes.id.cpu().numpy().astype(int)
                       if boxes.id is not None else np.full(len(cls), -1))
                for b, c, v, i in zip(xyxy, cls, cf, ids):
                    dets.append({"box": gg_view.shift(tuple(float(x) for x in b), dx, dy),
                                 "name": self.names.get(int(c), str(c)),
                                 "conf": float(v), "id": int(i)})

            flags, counts, raw_count, peak = tally(dets, roi_poly)
            numbers = self.display_ids.assign([d["id"] for d in dets])

            annotated, f = gg_view.for_display(frame)
            roi_show = scale_polygon(roi_norm, annotated.shape[1], annotated.shape[0])
            draw_roi(annotated, np.array(roi_show, dtype=np.int32)
                     if roi_show is not None else None)
            for d, inside, n in zip(dets, flags, numbers):
                tag = f"#{n} " if n > 0 else ""
                draw_detection(annotated, gg_view.scale_box(d["box"], f),
                               f"{tag}{d['name']} {d['conf']:.2f}",
                               CLASS_COLORS.get(d["name"], DEFAULT_COLOR), inside)

            step = self.counter.update(raw_count)

            with self.lock:
                self.raw_frame = frame
                self.annotated = annotated
                self.frame_size = (w, h)
                self.det_box = box
                self.connected = True
                self.counts = dict(counts)
                self.raw_count = raw_count
                self.smoothed = step.smoothed
                self.peak_conf = peak
                self.latency_ms = latency_ms
                self.alert_active = step.active
                self.frames += 1
                self.proc_stamps.append(time.perf_counter())
                self.all_latencies.append(latency_ms)
                ok_enc, buf = cv2.imencode(".jpg", annotated,
                                           [cv2.IMWRITE_JPEG_QUALITY, 75])
                if ok_enc:
                    self.jpeg = buf.tobytes()

            state = self.snapshot_state()
            state["timestamp"] = datetime.now().isoformat(sep=" ", timespec="seconds")

            if step.alert and self.is_muted():
                print(f"[MUTED] alert suppressed at count={step.smoothed}")

            elif step.alert:
                snap = self._save_snapshot("alert", state)
                self._log_event("accumulation", counts, raw_count,
                                step.smoothed, peak, snap)
                if self.on_alert:
                    self.on_alert()
                print(f"[ALERT] count={step.smoothed} "
                      f"bottle={counts.get('bottle',0)} "
                      f"bag={counts.get('bag',0)} "
                      f"food_container={counts.get('food_container',0)}")
                with self.lock:
                    auto = bool(self.settings.get("auto_mute"))
                    auto_s = int(self.settings.get("auto_mute_s", 0))
                if auto:
                    self.set_mute(seconds=auto_s, auto=True)
                    print("[AUTO-MUTE] " + (f"{auto_s}s" if auto_s else "until unmuted"))

            if step.suppressed:
                snap = self._save_snapshot("suppressed", state)
                self._log_event("suppressed", counts, step.suppressed["peak"],
                                step.smoothed, peak, snap)
                print(f"[SUPPRESSED] {step.suppressed['frames']} frames, "
                      f"peak {step.suppressed['peak']}")

            if step.cleared:
                if self.clear_mute():
                    print("[CLEAR] mute lifted with the alert")
                print(f"[CLEAR] count fell to {step.smoothed}")

            latency_window.append(latency_ms)
            if len(latency_window) >= PERF_FLUSH_EVERY:
                conn = open_db()
                conn.execute(
                    "INSERT INTO perf_samples (timestamp, frames_in_window,"
                    " mean_latency_ms, median_latency_ms, max_latency_ms)"
                    " VALUES (?,?,?,?,?)",
                    (datetime.now().isoformat(sep=" ", timespec="seconds"),
                     len(latency_window),
                     sum(latency_window) / len(latency_window),
                     statistics.median(latency_window), max(latency_window)))
                conn.commit()
                conn.close()
                latency_window = []

            if show_window:
                cv2.imshow("Garbage-Guard", annotated)
                if (cv2.waitKey(1) & 0xFF) == ord("q"):
                    break

        if reader is not None:
            reader.stop()
        if cap is not None:
            cap.release()
        if show_window:
            cv2.destroyAllWindows()
        self.summary()

    def summary(self):
        if not self.all_latencies:
            return
        mean = sum(self.all_latencies) / len(self.all_latencies)
        band = ("Excellent (<0.5s)" if mean < 500 else
                "Very Good (0.51-1.5s)" if mean < 1500 else
                "Good (1.51-3.0s)" if mean < 3000 else
                "Fair (3.1-5.0s)" if mean < 5000 else "Poor (>5.0s)")
        print("\n" + "=" * 64)
        print(" SESSION SUMMARY")
        print("=" * 64)
        print(f" frames processed : {len(self.all_latencies)}")
        print(f" mean latency     : {mean:.1f} ms  ({mean/1000:.3f} s)")
        print(f" median latency   : {statistics.median(self.all_latencies):.1f} ms")
        print(f" max latency      : {max(self.all_latencies):.1f} ms")
        print(f" effective FPS    : {1000.0/mean:.1f}")
        print(f" ISO 25010 band   : {band}")
        print("=" * 64)


def main():
    ap = argparse.ArgumentParser(description="Garbage-Guard live detection")
    ap.add_argument("--source", default=SOURCE)
    ap.add_argument("--model", default=None,
                    help="model folder to start with (default: the one picked in Settings, v7)")
    ap.add_argument("--conf", type=float, default=None)
    ap.add_argument("--threshold", type=int, default=None)
    ap.add_argument("--stream", choices=("main", "sub"), default=None,
                    help="camera stream to read (default: saved setting, main)")
    ap.add_argument("--detect-size", type=int, default=None,
                    help="pixels the model works at: 640, 960 or 1280")
    ap.add_argument("--no-crop", action="store_true",
                    help="give the model the whole frame, not just the ROI box")
    ap.add_argument("--port", type=int, default=WEB_PORT)
    ap.add_argument("--no-web", action="store_true")
    ap.add_argument("--display", action="store_true",
                    help="also open the old OpenCV debug window")
    args = ap.parse_args()

    SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)
    init_database()

    settings = load_settings()
    if args.conf is not None:
        settings["conf"] = args.conf
    if args.threshold is not None:
        settings["threshold"] = args.threshold
    if args.stream:
        settings["stream"] = args.stream
    if args.detect_size:
        settings["detect_size"] = args.detect_size
    if args.no_crop:
        settings["crop"] = False
    gg_view.clean_settings(settings)
    wanted = settings.get("model")
    gg_model.clean_settings(settings, BASE_DIR)
    model_path = args.model or str(BASE_DIR / settings["model"])

    engine = Engine(args.source, model_path, settings)
    if not args.model and wanted != settings["model"]:
        engine.model_note = (f"{gg_model.label(wanted)} is not installed, "
                             f"using {gg_model.label(settings['model'])}")
        save_settings(settings)

    print("=" * 64)
    print(" GARBAGE-GUARD")
    print("=" * 64)
    print(f" source     : {engine.view.url}  ({engine.view.active} stream)")
    print(f" detection  : {settings['detect_size']} px, "
          f"{'ROI box only' if settings['crop'] else 'whole frame'}")
    print(f" model      : {gg_model.label(engine.model_name)}  ({engine.model_path})")
    print(f" confidence : {settings['conf']}")
    print(f" threshold  : {settings['threshold']}  (re-arm {engine.counter.off})")
    print(f" roi points : {len(settings['roi'])}")
    if not args.no_web:
        print(f" dashboard  : http://localhost:{args.port}")
    print("=" * 64)

    if not args.no_web:
        ups = gg_ups.Ups()
        save = lambda: save_settings(settings)
        screen = gg_screen.Screen(settings, save=save, ups=ups)
        clock = gg_clock.Clock()
        buzzer = gg_buzzer.Buzzer(settings, save=save, backend=gg_buzzer.make_backend(),
                                  clock_synced=clock.synced,
                                  alert_on=lambda: engine.alert_active and not engine.is_muted())
        power = gg_power.Power(settings, save=save, ups=ups, screen=screen,
                               log=engine.log_note, flush=gg_cloud.flush, sound=buzzer.play)
        engine.on_alert = screen.alert
        engine.on_event = buzzer.event
        touch = gg_touch.Touch(screen.touch)
        touch.start()
        screen.wake_source = touch
        screen.start()
        power.start()
        buzzer.start()
        buzzer.play("ready")
        gg_web.serve(engine, args.port, ups=ups, screen=screen, power=power,
                     buzzer=buzzer, clock=clock, clean=engine.clean)
        gg_cloud.start(note=gg_web.note_viewer)

    try:
        engine.run(show_window=args.display)
    except KeyboardInterrupt:
        print("\nStopped by user.")
        engine.stop_flag.set()
        engine.summary()


if __name__ == "__main__":
    main()

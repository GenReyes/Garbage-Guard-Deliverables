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

from gg_counter import AccumulationCounter, scale_polygon, tally
import gg_web

CAMERA_FILE = Path(__file__).resolve().parent / "camera.txt"
SOURCE = "rtsp://admin:CHANGE_THIS_PASSWORD@192.168.1.64:554/Streaming/Channels/102"
if CAMERA_FILE.is_file():
    line = CAMERA_FILE.read_text().strip()
    if line:
        SOURCE = line
MODEL_PATH = "garbageguard_v5_best_ncnn_model"
IMGSZ = 640

SMOOTH_WINDOW = 15
ALERT_COOLDOWN_SEC = 60
PERF_FLUSH_EVERY = 100
WEB_PORT = 8080

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "garbageguard.db"
SNAPSHOT_DIR = BASE_DIR / "snapshots"
SETTINGS_PATH = BASE_DIR / "gg_settings.json"

DEFAULT_SETTINGS = {"conf": 0.40, "threshold": 20, "roi": []}

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


def draw_roi(frame, roi_pixels):
    if roi_pixels is None:
        return
    overlay = frame.copy()
    cv2.fillPoly(overlay, [roi_pixels], (148, 158, 31))
    cv2.addWeighted(overlay, 0.15, frame, 0.85, 0, dst=frame)
    cv2.polylines(frame, [roi_pixels], True, (148, 158, 31), 2)


def draw_detection(frame, box, label, color, inside):
    x1, y1, x2, y2 = [int(v) for v in box]
    if not inside:
        cv2.rectangle(frame, (x1, y1), (x2, y2), (120, 120, 120), 1)
        return
    cv2.rectangle(frame, (x1, y1), (x2, y2), color, 2)
    (tw, th), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.45, 1)
    cv2.rectangle(frame, (x1, y1 - th - 6), (x1 + tw + 4, y1), color, -1)
    cv2.putText(frame, label, (x1 + 2, y1 - 4),
                cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 0, 0), 1, cv2.LINE_AA)


def stamp_snapshot(frame, state):
    h, w = frame.shape[:2]
    cv2.rectangle(frame, (0, 0), (w, 86), (28, 28, 28), -1)
    c = state["counts"]
    cv2.putText(frame, f"{state['timestamp']}   {state['latency_ms']:.1f} ms",
                (10, 26), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 255, 255), 1, cv2.LINE_AA)
    cv2.putText(frame, f"ITEMS {state['smoothed']}   threshold {state['threshold']}",
                (10, 52), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 1, cv2.LINE_AA)
    cv2.putText(frame,
                f"bottle {c.get('bottle',0)}  bag {c.get('bag',0)}  "
                f"food_container {c.get('food_container',0)}",
                (10, 76), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1, cv2.LINE_AA)
    cv2.rectangle(frame, (0, 0), (w - 1, h - 1), (0, 0, 255), 6)


class FrameReader:
    """Keeps the RTSP queue drained so the engine always gets the newest frame."""

    def __init__(self, open_fn):
        self.open_fn = open_fn
        self.lock = threading.Lock()
        self.frame = None
        self.captured_at = 0.0
        self.seq = 0
        self.stop_flag = threading.Event()
        self.alive = False
        self.thread = None

    def start(self):
        self.thread = threading.Thread(target=self._loop, daemon=True)
        self.thread.start()
        return self

    def _loop(self):
        cap = self.open_fn()
        misses = 0
        while not self.stop_flag.is_set():
            ok, frame = cap.read()
            if not ok:
                misses += 1
                self.alive = False
                if misses > 50:
                    print("Stream lost. Reconnecting...")
                    cap.release()
                    time.sleep(2.0)
                    cap = self.open_fn()
                    misses = 0
                time.sleep(0.02)
                continue
            misses = 0
            with self.lock:
                self.frame = frame
                self.captured_at = time.perf_counter()
                self.seq += 1
                self.alive = True
        cap.release()

    def latest(self, after_seq):
        with self.lock:
            if self.frame is None or self.seq == after_seq:
                return None, after_seq, 0.0
            return self.frame, self.seq, self.captured_at

    def stop(self):
        self.stop_flag.set()


class Engine:
    def __init__(self, source, model_path, settings):
        self.source = source
        self.model_path = model_path
        self.settings = settings
        self.lock = threading.Lock()
        self.stop_flag = threading.Event()

        self.jpeg = None
        self.raw_frame = None
        self.annotated = None
        self.snapshot_dir = SNAPSHOT_DIR
        self.connected = False
        self.counts = {}
        self.raw_count = 0
        self.smoothed = 0
        self.peak_conf = 0.0
        self.latency_ms = 0.0
        self.alert_active = False
        self.mute_until = 0.0
        self.frames = 0
        self.all_latencies = []
        self.started_at = time.time()

        self.counter = AccumulationCounter(
            threshold=settings["threshold"],
            window=SMOOTH_WINDOW,
            cooldown=ALERT_COOLDOWN_SEC,
        )
        self.names = {}

    def snapshot_state(self):
        with self.lock:
            avg = (sum(self.all_latencies) / len(self.all_latencies)
                   if self.all_latencies else 0.0)
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
                "fps": round(1000.0 / self.latency_ms, 1) if self.latency_ms else 0.0,
                "alert_active": self.alert_active,
                "muted": self._muted_locked(),
                "mute_left": self._mute_left_locked(),
                "frames": self.frames,
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

    def set_mute(self, seconds=None, cancel=False):
        with self.lock:
            was = self._muted_locked()
            if cancel:
                self.mute_until = 0.0
            elif seconds in (0, None):
                self.mute_until = float("inf")
            else:
                self.mute_until = time.time() + max(1, int(seconds))
            now_muted = self._muted_locked()
            left = self._mute_left_locked()
        if now_muted != was:
            self._log_simple("muted" if now_muted else "unmuted")
        return {"muted": now_muted, "mute_left": left}

    def clear_mute(self, log=True):
        with self.lock:
            was = self._muted_locked()
            self.mute_until = 0.0
        if was and log:
            self._log_simple("unmuted")
        return was

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

    def recent_alerts(self, limit=50):
        conn = open_db()
        rows = conn.execute(
            "SELECT id, timestamp, event, smoothed_count, raw_count, n_bottle, n_bag,"
            " n_food_container, peak_confidence, threshold_used, snapshot_path,"
            " COALESCE(read,0) AS read FROM alerts ORDER BY id DESC LIMIT ?", (int(limit),)
        ).fetchall()
        conn.close()
        out = []
        for r in rows:
            d = dict(r)
            name = Path(d["snapshot_path"]).name if d.get("snapshot_path") else None
            d["snapshot"] = name if name and (SNAPSHOT_DIR / name).is_file() else None
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
                if target.parent == folder and target.is_file():
                    target.unlink()
                    removed += 1
            except OSError:
                pass
        conn.execute(f"DELETE FROM alerts WHERE {clause}", args)
        conn.commit()
        conn.close()
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

    def _save_snapshot(self, tag, state):
        with self.lock:
            src = self.annotated if self.annotated is not None else self.raw_frame
            frame = None if src is None else src.copy()
        if frame is None:
            return None
        stamp_snapshot(frame, state)
        path = SNAPSHOT_DIR / f"{tag}_{datetime.now():%Y%m%d_%H%M%S_%f}.jpg"
        cv2.imwrite(str(path), frame)
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
        cap = cv2.VideoCapture(int(self.source) if str(self.source).isdigit()
                               else self.source)
        if str(self.source).lower().startswith("rtsp"):
            cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        return cap

    def run(self, show_window=False):
        print(f"Loading model: {self.model_path}")
        model = YOLO(self.model_path, task="detect")
        self.names = model.names
        print(f"Classes: {self.names}")

        live = (str(self.source).lower().startswith("rtsp")
                or str(self.source).isdigit())

        cap = None
        reader = None
        if live:
            probe = self._open()
            if not probe.isOpened():
                print("ERROR: could not open the video source.")
                return
            probe.release()
            reader = FrameReader(self._open).start()
        else:
            cap = self._open()
            if not cap.isOpened():
                print("ERROR: could not open the video source.")
                return

        seq = 0
        latency_window = []

        while not self.stop_flag.is_set():
            if live:
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

            h, w = frame.shape[:2]
            roi_poly = scale_polygon(roi_norm, w, h)
            roi_px = (np.array(roi_poly, dtype=np.int32)
                      if roi_poly is not None else None)

            results = model.track(frame, persist=True, tracker="bytetrack.yaml",
                                  conf=conf, imgsz=IMGSZ, verbose=False)
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
                    dets.append({"box": tuple(float(x) for x in b),
                                 "name": self.names.get(int(c), str(c)),
                                 "conf": float(v), "id": int(i)})

            flags, counts, raw_count, peak = tally(dets, roi_poly)

            annotated = frame.copy()
            draw_roi(annotated, roi_px)
            for d, inside in zip(dets, flags):
                tag = f"#{d['id']} " if d["id"] >= 0 else ""
                draw_detection(annotated, d["box"],
                               f"{tag}{d['name']} {d['conf']:.2f}",
                               CLASS_COLORS.get(d["name"], DEFAULT_COLOR), inside)

            step = self.counter.update(raw_count)

            with self.lock:
                self.raw_frame = frame
                self.annotated = annotated
                self.connected = True
                self.counts = dict(counts)
                self.raw_count = raw_count
                self.smoothed = step.smoothed
                self.peak_conf = peak
                self.latency_ms = latency_ms
                self.alert_active = step.active
                self.frames += 1
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
                print(f"[ALERT] count={step.smoothed} "
                      f"bottle={counts.get('bottle',0)} "
                      f"bag={counts.get('bag',0)} "
                      f"food_container={counts.get('food_container',0)}")

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
    ap.add_argument("--model", default=MODEL_PATH)
    ap.add_argument("--conf", type=float, default=None)
    ap.add_argument("--threshold", type=int, default=None)
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

    engine = Engine(args.source, args.model, settings)

    print("=" * 64)
    print(" GARBAGE-GUARD")
    print("=" * 64)
    print(f" source     : {args.source}")
    print(f" confidence : {settings['conf']}")
    print(f" threshold  : {settings['threshold']}  (re-arm {engine.counter.off})")
    print(f" roi points : {len(settings['roi'])}")
    if not args.no_web:
        print(f" dashboard  : http://localhost:{args.port}")
    print("=" * 64)

    if not args.no_web:
        gg_web.serve(engine, args.port)

    try:
        engine.run(show_window=args.display)
    except KeyboardInterrupt:
        print("\nStopped by user.")
        engine.stop_flag.set()
        engine.summary()


if __name__ == "__main__":
    main()

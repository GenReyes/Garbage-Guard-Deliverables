"""Mirror of the Pi to a Firebase Realtime Database, for phones away from the Pi's network.

The Pi stays the source of truth. This thread only copies what the local API
already serves (state, history, snapshots, a slow preview) up to Firebase, and
carries phone commands back down to the same local API. If the internet or
Firebase is down, nothing else on the Pi notices.

The Pi signs in as its own Firebase user (email and password), so every
setting can be typed on the touchscreen. Settings live in gg_firebase.json;
the last set that passed a connection test is kept in gg_firebase.good.json
and can be restored. Off until enabled there. See pi/README.md.
"""

import base64
import http.client
import json
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

import gg_usage

BASE_DIR = Path(__file__).resolve().parent
CONFIG = BASE_DIR / "gg_firebase.json"
GOOD = BASE_DIR / "gg_firebase.good.json"
LOCAL = "http://127.0.0.1:8080"
SIGN_IN = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key="
DB_URL = re.compile(r"^https://[a-z0-9-]+(\.[a-z0-9-]+)*\.(firebaseio\.com|firebasedatabase\.app)$")
API_KEY = re.compile(r"^AIza[0-9A-Za-z_-]{35}$")
EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")

COMMANDS = {
    "mute": "/api/mute",
    "auto_mute": "/api/auto_mute",
    "settings": "/api/settings",
    "roi": "/api/roi",
    "test_alert": "/api/test_alert",
    "capture": "/api/capture",
    "alerts_read": "/api/alerts/read",
    "alerts_delete": "/api/alerts/delete",
    "camera_reconnect": "/api/camera/reconnect",
    "screen_saver": "/api/screen_saver",
    "screen": "/api/screen",
    "auto_shutdown": "/api/auto_shutdown",
    "shutdown": "/api/shutdown",
    "buzzer": "/api/buzzer",
    "buzzer_test": "/api/buzzer/test",
    "clean": "/api/clean",
    "view": "/api/view",
}

STATE_KEYS = ("connected", "counts", "smoothed", "threshold", "rearm", "conf", "roi", "latency_ms",
              "avg_latency_ms", "fps", "cam_fps", "alert_active", "muted", "mute_left", "mute_total",
              "auto_mute", "auto_mute_s", "auto_muted", "frames", "uptime_s", "reconnects",
              "screen", "screen_mode", "screen_timeout_s", "screen_timeout_battery_s",
              "screen_dim_pct", "screen_wake_on_alert", "screen_note", "clock_synced",
              "buzzer_on", "buzzer_volume", "buzzer_alerts", "buzzer_countdown", "buzzer_ui",
              "buzzer_repeat", "buzzer_repeat_s", "quiet_on", "quiet_from", "quiet_to",
              "buzzer_available", "buzzer_note", "quiet_now", "quiet_note",
              "clean_mode", "clean_count", "clean_bytes",
              "stream", "stream_note", "frame_size", "detect_size", "crop",
              "model", "models", "model_note")
ALERT_KEYS = ("id", "timestamp", "event", "smoothed_count", "raw_count", "n_bottle", "n_bag",
              "n_food_container", "peak_confidence", "threshold_used", "read")

STATUS = {"enabled": False, "ok": False, "error": None, "last_ok": None, "watchers": 0,
          "usage": None}

DEFAULTS = {"root": "gg", "enabled": True, "state_s": 3, "idle_state_s": 120,
            "alerts_s": 10, "commands_s": 2, "idle_commands_s": 10, "pi_s": 30, "idle_pi_s": 60,
            "preview_s": 3, "preview_width": 360, "preview_max_kb": 40,
            "snap_width": 960, "snap_max_kb": 300, "thumb_width": 160, "thumb_max_kb": 8, "history": 200, "watch_window_s": 30,
            "month_limit_gb": 10, "day_preview_mb": 300, "usage_s": 600, "countdown_s": 3}


# Firebase bills the TLS handshake of every new connection (about 3.5 KB), so
# the HTTPS connections are kept open and reused, one per thread and host.
HANDSHAKE_BYTES = 3500
NET = {"handshakes": 0}
_conns = threading.local()


def _https(host, timeout):
    pool = getattr(_conns, "pool", None)
    if pool is None:
        pool = _conns.pool = {}
    conn = pool.get(host)
    if conn is None:
        conn = pool[host] = http.client.HTTPSConnection(host, timeout=timeout)
        NET["handshakes"] += 1
    conn.timeout = timeout
    return conn


def _drop(host):
    conn = getattr(_conns, "pool", {}).pop(host, None)
    if conn:
        conn.close()


def http_call(method, url, body=None, timeout=15):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json"} if data else {}
    u = urllib.parse.urlsplit(url)
    if u.scheme != "https":
        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.status, r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.read()
    path = u.path + ("?" + u.query if u.query else "")
    for attempt in (0, 1):
        conn = _https(u.netloc, timeout)
        try:
            conn.request(method, path, body=data, headers=headers)
            r = conn.getresponse()
            raw = r.read()
            if r.will_close:
                _drop(u.netloc)
            return r.status, raw
        except (http.client.HTTPException, OSError):
            # A kept-alive connection the server already closed fails once; retry on a new one.
            _drop(u.netloc)
            if attempt:
                raise


def shrink_jpeg(raw, width, max_kb):
    import cv2
    import numpy as np
    img = cv2.imdecode(np.frombuffer(raw, np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        return None
    h, w = img.shape[:2]
    if w > width:
        img = cv2.resize(img, (width, int(h * width / w)), interpolation=cv2.INTER_AREA)
    for q in (78, 68, 58, 48, 38):
        ok, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, q])
        if ok and len(buf) <= max_kb * 1024:
            break
    h, w = img.shape[:2]
    return {"jpg": base64.b64encode(buf.tobytes()).decode(), "w": w, "h": h, "bytes": int(len(buf))}


def check_config(cfg):
    errors = {}
    url = (cfg.get("database_url") or "").strip().rstrip("/")
    if not DB_URL.match(url):
        errors["database_url"] = ("Copy it from Realtime Database in the Firebase console, e.g. "
                                  "https://your-project-default-rtdb.asia-southeast1.firebasedatabase.app")
    if not API_KEY.match((cfg.get("api_key") or "").strip()):
        errors["api_key"] = "The Web API key starts with AIza and is 39 characters (Project settings, General)."
    if not EMAIL.match((cfg.get("email") or "").strip()):
        errors["email"] = "Use the email of the Pi's own user in Firebase Authentication."
    if len(cfg.get("password") or "") < 6:
        errors["password"] = "Firebase passwords are at least 6 characters."
    return errors


def sign_in(cfg, http=http_call):
    status, raw = http("POST", SIGN_IN + cfg["api_key"].strip(),
                       {"email": cfg["email"].strip(), "password": cfg["password"], "returnSecureToken": True})
    data = json.loads(raw or b"{}")
    if status != 200:
        msg = (data.get("error") or {}).get("message", f"HTTP {status}")
        friendly = {"INVALID_LOGIN_CREDENTIALS": "Wrong email or password.",
                    "EMAIL_NOT_FOUND": "No such user. Add it under Authentication, Users.",
                    "INVALID_PASSWORD": "Wrong password.",
                    "USER_DISABLED": "This user is disabled in Firebase.",
                    "OPERATION_NOT_ALLOWED": "Turn on Email/Password under Authentication, Sign-in method."}
        for k, v in friendly.items():
            if msg.startswith(k):
                raise RuntimeError(v)
        if "API key not valid" in msg or "API_KEY_INVALID" in msg:
            raise RuntimeError("The Web API key was refused.")
        raise RuntimeError(msg)
    return data["idToken"], data["localId"], int(data.get("expiresIn", 3600))


def token_source(cfg, http=http_call, clock=time.time):
    held = {"token": None, "until": 0, "uid": None}

    def get():
        if held["token"] is None or clock() > held["until"]:
            token, uid, life = sign_in(cfg, http)
            held.update(token=token, uid=uid, until=clock() + life - 300)
        return held["token"]
    get.held = held
    return get


def test_config(cfg, http=http_call):
    errors = check_config(cfg)
    if errors:
        return {"ok": False, "error": "invalid", "fields": errors, "message": next(iter(errors.values()))}
    try:
        token, uid, _ = sign_in(cfg, http)
    except Exception as e:
        return {"ok": False, "error": "sign_in", "message": str(e)}
    url = f"{cfg['database_url'].strip().rstrip('/')}/gg_users/{uid}.json?auth={token}"
    try:
        status, raw = http("GET", url, None)
    except Exception as e:
        return {"ok": False, "error": "database", "message": f"Could not reach the database: {e}"}
    if status == 401 or status == 403:
        return {"ok": False, "error": "rules", "uid": uid,
                "message": "Signed in, but the database rules refused it. Add this Pi's UID under gg_users "
                           "with the value \"pi\" (see pi/README.md)."}
    if status == 404:
        return {"ok": False, "error": "database", "message": "No database at that address."}
    role = json.loads(raw or b"null")
    if role != "pi":
        return {"ok": False, "error": "role", "uid": uid,
                "message": f"Signed in as {uid}, but gg_users/{uid} is not set to \"pi\" yet."}
    return {"ok": True, "uid": uid, "message": "Connected. The Pi can write to Firebase."}


def load_config():
    try:
        return json.loads(CONFIG.read_text())
    except (OSError, ValueError):
        return None


def _write_private(path, cfg):
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(cfg, indent=2))
    os.chmod(tmp, 0o600)
    tmp.replace(path)


def save_config(cfg, good=False):
    keep = {k: cfg[k] for k in ("database_url", "api_key", "email", "password", "enabled") if k in cfg}
    keep["database_url"] = keep.get("database_url", "").strip().rstrip("/")
    _write_private(CONFIG, keep)
    if good:
        _write_private(GOOD, keep)
    return keep


def public_config():
    cfg = load_config() or {}
    good = None
    try:
        good = json.loads(GOOD.read_text())
    except (OSError, ValueError):
        pass
    return {"configured": bool(cfg), "enabled": bool(cfg.get("enabled", False)),
            "database_url": cfg.get("database_url", ""), "api_key": cfg.get("api_key", ""),
            "email": cfg.get("email", ""), "has_password": bool(cfg.get("password")),
            "has_good": good is not None, "good_url": (good or {}).get("database_url"),
            "status": dict(STATUS)}


# Rough size of Firebase's response headers, counted with each answer the Pi downloads.
RESPONSE_HEADERS = 400


def apply_event(tree, path, data, patch=False):
    """Apply one Firebase streaming event to a local copy of the node. Returns the new tree."""
    parts = [p for p in path.split("/") if p]
    if patch:
        for k, v in (data or {}).items():
            tree = apply_event(tree, "/".join(parts + [k]), v)
        return tree
    if not parts:
        return data
    tree = tree if isinstance(tree, dict) else {}
    cur = tree
    for p in parts[:-1]:
        if not isinstance(cur.get(p), dict):
            cur[p] = {}
        cur = cur[p]
    if data is None:
        cur.pop(parts[-1], None)
    else:
        cur[parts[-1]] = data
    return tree


class Stream:
    """Keeps one node of the database mirrored over Firebase's streaming REST API.

    Firebase sends the whole node once, then only what changes, so watching a
    quiet node costs a keep-alive line every 30 s instead of a full answer
    every poll. `live` is False until the first copy arrives and again after
    the connection drops; the caller polls in the meantime.
    """

    def __init__(self, url_for, stop_flag):
        self.url_for = url_for
        self.stop_flag = stop_flag
        self.data = None
        self.live = False
        self.bytes = 0
        self.lock = threading.Lock()
        self.conn = None

    def start(self):
        threading.Thread(target=self.run, daemon=True).start()
        return self

    def run(self):
        fails = 0
        while not self.stop_flag.is_set():
            try:
                self.listen(self.url_for())
                fails = 0
            except Exception:
                fails += 1
            self.live = False
            self.stop_flag.wait(min(60, 2 * fails) or 1)

    def listen(self, url):
        for _ in range(3):
            u = urllib.parse.urlsplit(url)
            conn = self.conn = http.client.HTTPSConnection(u.netloc, timeout=75)
            NET["handshakes"] += 1
            conn.request("GET", u.path + "?" + u.query, headers={"Accept": "text/event-stream"})
            r = conn.getresponse()
            if r.status in (301, 302, 307, 308) and r.getheader("Location"):
                url = r.getheader("Location")
                conn.close()
                continue
            if r.status != 200:
                conn.close()
                raise RuntimeError(f"stream HTTP {r.status}")
            try:
                self.read(r)
            finally:
                conn.close()
            return

    def read(self, r):
        event = None
        while not self.stop_flag.is_set():
            line = r.readline()
            if not line:
                return
            with self.lock:
                self.bytes += len(line)
            line = line.decode("utf-8", "replace").rstrip("\r\n")
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:") and event in ("put", "patch"):
                msg = json.loads(line[5:].strip() or "null") or {}
                with self.lock:
                    self.data = apply_event(self.data, msg.get("path", "/"), msg.get("data"), event == "patch")
                    self.live = True
            elif line.startswith("data:") and event in ("cancel", "auth_revoked"):
                return

    def take_bytes(self):
        with self.lock:
            n, self.bytes = self.bytes, 0
        return n

    def snapshot(self):
        with self.lock:
            return json.loads(json.dumps(self.data)) if self.data is not None else None


class Cloud:
    def __init__(self, cfg, token=None, http=http_call, local=LOCAL, clock=time.time, note=None,
                 usage=None, streams=None):
        self.cfg = dict(DEFAULTS, **cfg)
        self.url = self.cfg["database_url"].rstrip("/")
        self.root = self.cfg["root"].strip("/")
        self.token = token if token is not None else token_source(self.cfg, http, clock)
        self.http = http
        self.local = local
        self.clock = clock
        self.note = note
        self.stop_flag = threading.Event()
        self.next = {}
        self.sent_state = None
        self.sent_alerts = None
        self.sent_snaps = None
        self.sent_usage = None
        self.alerts_rev = int(self.clock() * 1000)
        self.handshakes = NET["handshakes"]
        self.watchers = {}
        self.busy = threading.RLock()
        self.counting = False
        # watchers and commands arrive by stream when possible; see start_streams().
        self.streams = streams or {}
        self.done_cmds = []
        self.thumbs_todo = None
        self.usage = usage if usage is not None else gg_usage.Usage(
            clock=clock, limit=int(self.cfg["month_limit_gb"] * gg_usage.GB),
            day_preview_mb=self.cfg["day_preview_mb"])
        STATUS["usage"] = self.usage.report()

    # ---------- plumbing ----------
    def _fb(self, method, path, body=None, query=""):
        url = f"{self.url}/{self.root}/{path}.json"
        params = [query] if query else []
        if method != "GET":
            # Without this Firebase sends every write straight back, and that download is billed.
            params.append("print=silent")
        if self.token:
            params.append("auth=" + self.token())
        if params:
            url += "?" + "&".join(params)
        status, raw = self.http(method, url, body)
        self.usage.add_down("pi", len(raw or b"") + RESPONSE_HEADERS)
        if status >= 300:
            raise RuntimeError(f"Firebase {method} {path}: HTTP {status} {raw[:120]!r}")
        return json.loads(raw or b"null")

    def _count(self, kind, body, per_watcher=True):
        size = len(json.dumps(body)) if not isinstance(body, (bytes, str)) else len(body)
        self.usage.add(kind, size, len(self.active_watchers()) if per_watcher else 0)

    def _get_local(self, path):
        status, raw = self.http("GET", self.local + path, None)
        if status != 200:
            raise RuntimeError(f"local {path}: HTTP {status}")
        return raw

    def _due(self, name, every):
        now = self.clock()
        if now >= self.next.get(name, 0):
            self.next[name] = now + every
            return True
        return False

    # ---------- what goes up ----------
    def watching(self):
        return any(self.watchers.values())

    def active_watchers(self):
        return {k: v for k, v in self.watchers.items() if v}

    def live_watchers(self):
        # Phones say which screen is open; one that does not say is treated as on the live screen.
        return [v for v in self.watchers.values() if v and v.get("screen", "live") == "live"]

    def streaming(self):
        return bool(self.streams) and all(s.live for s in self.streams.values())

    def start_streams(self):
        def url_for(node):
            return lambda: f"{self.url}/{self.root}/{node}.json?auth={self.token()}" if self.token \
                else f"{self.url}/{self.root}/{node}.json"
        self.streams = {n: Stream(url_for(n), self.stop_flag).start() for n in ("watchers", "commands")}

    def _rows(self, node):
        s = self.streams.get(node)
        if s is not None and s.live:
            return s.snapshot() or {}
        return self._fb("GET", node) or {}

    def push_state(self, force=False):
        st = json.loads(self._get_local("/api/state"))
        slim = {k: st.get(k) for k in STATE_KEYS}
        try:
            battery = json.loads(self._get_local("/api/battery"))
        except Exception:
            battery = None
        shutdown = (battery or {}).get("shutdown_state")
        self.counting = shutdown == "counting"
        # With nobody watching, a changing count is not worth a write; alert and mute changes still are.
        watched = self.watching()
        fields = ("counts", "smoothed") if watched else ()
        key = json.dumps({k: slim[k] for k in fields + ("alert_active", "muted", "connected",
                                                         "threshold", "auto_mute", "screen", "screen_mode", "screen_note")}
                         | {"shutdown": shutdown}, sort_keys=True)
        every = self.cfg["state_s"] if watched else self.cfg["idle_state_s"]
        if self.counting:
            every = min(every, self.cfg["countdown_s"])
        if not force and key == self.sent_state and not self._due("state_beat", every):
            return False
        slim["battery"] = battery
        slim["ts"] = int(self.clock() * 1000)
        self._fb("PUT", "state", slim)
        self._count("state", slim)
        self.sent_state = key
        self.next["state_beat"] = self.clock() + every
        return True

    def push_pi(self):
        urls = []
        try:
            det = json.loads(self._get_local("/api/net/details"))
            for d in det.get("interfaces", []):
                if d.get("role") != "camera" and d.get("state") == "connected":
                    urls += [f"http://{ip.split('/')[0]}:8080" for ip in d.get("ips", [])]
        except Exception:
            pass
        self._fb("PUT", "pi", {"online": True, "ts": int(self.clock() * 1000), "local_urls": urls,
                               "version": "2026.10.12", "alerts_rev": self.alerts_rev})
        self.next["pi"] = self.clock() + (self.cfg["pi_s"] if self.watching() else self.cfg["idle_pi_s"])

    def sync_alerts(self):
        rows = json.loads(self._get_local(f"/api/alerts?limit={self.cfg['history']}")).get("alerts", [])
        if self.sent_alerts is None:
            have = self._fb("GET", "alerts", query="shallow=true") or {}
            self.sent_alerts = {k: None for k in have}
        if self.sent_snaps is None:
            have = self._fb("GET", "snaps", query="shallow=true") or {}
            self.sent_snaps = set(have)
        now = {}
        for r in rows:
            row = {k: r.get(k) for k in ALERT_KEYS}
            row["has_snap"] = bool(r.get("snapshot"))
            row["has_clean"] = bool(r.get("clean"))
            row["snapshot"] = r.get("snapshot")
            now[str(r["id"])] = row
        patch = {}
        for k, row in now.items():
            if self.sent_alerts.get(k) != row:
                patch[k] = row
        for k in list(self.sent_alerts):
            if k not in now:
                patch[k] = None
        if patch:
            self._fb("PATCH", "alerts", patch)
            self._count("alerts", patch)
            for k, v in patch.items():
                if v is None:
                    self.sent_alerts.pop(k, None)
                else:
                    self.sent_alerts[k] = v
        gone = {k: None for k in self.sent_snaps if k not in now or not now[k]["has_snap"]}
        if gone:
            self._fb("PATCH", "snaps", gone)
            self.sent_snaps -= set(gone)
        if patch or gone:
            # Phones read /gg/pi often and fetch the whole history only when this number moves.
            self.alerts_rev = int(self.clock() * 1000)
            self.next["pi"] = 0
        uploaded = 0
        for k, row in now.items():
            if uploaded >= 2:
                break
            if row["has_snap"] and k not in self.sent_snaps:
                raw = self._get_local("/snapshots/" + row["snapshot"])
                pic = shrink_jpeg(raw, self.cfg["snap_width"], self.cfg["snap_max_kb"])
                if pic:
                    pic["thumb"] = self._thumb(raw)
                    self._fb("PUT", f"snaps/{k}", pic)
                    self._count("snaps", pic, per_watcher=False)
                    self.sent_snaps.add(k)
                    uploaded += 1
        self.backfill_thumbs(now)
        return len(patch), uploaded

    def _thumb(self, raw):
        t = shrink_jpeg(raw, self.cfg["thumb_width"], self.cfg["thumb_max_kb"])
        return {k: t[k] for k in ("jpg", "w", "h", "bytes")} if t else None

    def backfill_thumbs(self, now, per_call=5):
        """Snapshots uploaded before thumbnails existed get one, a few at a time."""
        if self.thumbs_todo is None:
            self.thumbs_todo = sorted(self.sent_snaps, key=lambda k: -int(k) if k.isdigit() else 0)
        done = 0
        while self.thumbs_todo and done < per_call:
            k = self.thumbs_todo.pop(0)
            row = now.get(k)
            if not row or not row["has_snap"] or k not in self.sent_snaps:
                continue
            done += 1
            if self._fb("GET", f"snaps/{k}/thumb", query="shallow=true") is not None:
                continue
            thumb = self._thumb(self._get_local("/snapshots/" + row["snapshot"]))
            if thumb:
                self._fb("PUT", f"snaps/{k}/thumb", thumb)
        return done

    def push_preview(self):
        try:
            raw = self._get_local("/frame.jpg")
        except RuntimeError:
            self._fb("PUT", "preview", {"ts": int(self.clock() * 1000), "jpg": None})
            return False
        pic = shrink_jpeg(raw, self.cfg["preview_width"], self.cfg["preview_max_kb"])
        if not pic:
            return False
        pic["ts"] = int(self.clock() * 1000)
        self._fb("PUT", "preview", pic)
        self._count("preview", pic)
        return True

    # ---------- what comes down ----------
    def read_watchers(self):
        rows = self._rows("watchers")
        now_ms = self.clock() * 1000
        win = self.cfg["watch_window_s"] * 1000
        self.watchers = {k: (v if isinstance(v, dict) and now_ms - float(v.get("ts", 0)) <= win else None)
                         for k, v in rows.items()}
        stale = {k: None for k, v in self.watchers.items() if v is None}
        if stale and (not self.streaming() or self._due("stale", 30)):
            self._fb("PATCH", "watchers", stale)
        active = {k: v for k, v in self.watchers.items() if v}
        STATUS["watchers"] = len(active)
        if self.note:
            for k, v in active.items():
                self.note("Firebase", "app", headers={
                    "X-GG-Client-Id": "fb-" + k, "X-GG-Device": v.get("device"), "X-GG-OS": v.get("os"),
                    "X-GG-App-Version": v.get("app_version"), "X-GG-Mode": v.get("mode")})
        return active

    def run_commands(self):
        rows = self._rows("commands")
        done = 0
        for cid, cmd in sorted(rows.items()):
            # A streamed copy can still list a command whose deletion has not come back yet.
            if cid in self.done_cmds:
                continue
            self.done_cmds = (self.done_cmds + [cid])[-100:]
            self._fb("DELETE", f"commands/{cid}")
            name = (cmd or {}).get("cmd")
            path = COMMANDS.get(name)
            if not path:
                result = {"ok": False, "error": "unknown_command"}
            else:
                body = (cmd or {}).get("args") or {}
                status, raw = self.http("POST", self.local + path, body)
                try:
                    result = json.loads(raw or b"{}")
                except ValueError:
                    result = {"ok": status == 200}
                result.setdefault("ok", status == 200)
            result.update(cmd=name, ts=int(self.clock() * 1000))
            self._fb("PUT", f"results/{cid}", result)
            done += 1
        if done or self._due("results_prune", 600):
            res = self._fb("GET", "results", query="shallow=true") or {}
            if len(res) > 50:
                self._fb("PATCH", "results", {k: None for k in sorted(res)[:-50]})
        return done

    def push_usage(self):
        rep = self.usage.report()
        STATUS["usage"] = rep
        self._fb("PUT", "usage", rep)
        self.sent_usage = rep["down_bytes"]
        return rep

    # ---------- loop ----------
    def tick(self):
        with self.busy:
            self._tick()

    def flush(self, timeout=8):
        """Send state, history and the heartbeat now. Used right before the Pi powers off."""
        if not self.busy.acquire(timeout=timeout):
            return False
        try:
            self.sync_alerts()
            self.push_state(force=True)
            self.push_pi()
            self.usage.save(force=True)
            return True
        finally:
            self.busy.release()

    def _tick(self):
        if self.streaming():
            # Streamed copies are local, so reading them often costs nothing.
            if self._due("commands", self.cfg["commands_s"]):
                self.read_watchers()
            if self.streams["commands"].snapshot():
                self.run_commands()
            for s in self.streams.values():
                self.usage.add_down("pi", s.take_bytes())
        else:
            every = self.cfg["commands_s"] if self.watching() or self.counting else self.cfg["idle_commands_s"]
            if self._due("commands", every):
                self.read_watchers()
                self.run_commands()
        self.push_state()
        if self._due("alerts", self.cfg["alerts_s"]):
            self.sync_alerts()
        if self.clock() >= self.next.get("pi", 0):
            self.push_pi()
        made = NET["handshakes"] - self.handshakes
        if made > 0:
            self.usage.add_down("pi", made * HANDSHAKE_BYTES)
            self.handshakes = NET["handshakes"]
        every = self.usage.preview_every(self.cfg["preview_s"])
        if self.live_watchers() and every and self._due("preview", every):
            self.push_preview()
        STATUS["usage"] = self.usage.report()
        if self._due("usage", self.cfg["usage_s"]) and self.usage.data["down"] != self.sent_usage:
            self.push_usage()
        self.usage.save()

    def run(self):
        STATUS.update(enabled=True)
        if self.http is http_call and not self.streams:
            self.start_streams()
        fails = 0
        while not self.stop_flag.is_set():
            try:
                self.tick()
                STATUS.update(ok=True, error=None, last_ok=self.clock())
                fails = 0
                self.stop_flag.wait(1.0)
            except Exception as e:
                fails += 1
                STATUS.update(ok=False, error=str(e)[:200])
                self.sent_state = None
                self.stop_flag.wait(min(60, 5 * fails))


_current = {"cloud": None, "note": None}


def stop():
    c = _current["cloud"]
    if c:
        c.stop_flag.set()
    _current["cloud"] = None
    STATUS.update(enabled=False, ok=False, error=None, watchers=0)


def flush(timeout=8):
    c = _current["cloud"]
    if not c or not STATUS.get("ok"):
        return False
    done = threading.Event()
    out = {"ok": False}

    def work():
        try:
            out["ok"] = c.flush(timeout)
        except Exception as e:
            print(f"[CLOUD] flush failed: {e}", flush=True)
        done.set()

    threading.Thread(target=work, daemon=True).start()
    done.wait(timeout)
    return out["ok"]


def start(note=None):
    if note is not None:
        _current["note"] = note
    stop()
    cfg = load_config()
    if not cfg or not cfg.get("enabled", False):
        return None
    if check_config(cfg):
        STATUS.update(enabled=True, ok=False, error="Firebase settings are incomplete.")
        return None
    cloud = Cloud(cfg, note=_current["note"])
    _current["cloud"] = cloud
    print(f"Firebase sync on: {cloud.url}/{cloud.root}")
    threading.Thread(target=cloud.run, daemon=True).start()
    return cloud

"""Hide and bring back the kiosk dashboard window on the Pi desktop.

Chromium in --kiosk mode has no title bar or taskbar button a finger can
reach, and a web page cannot minimize its own window. The Pi desktop runs the
labwc Wayland compositor, which lets a helper tool (wlrctl) minimize or focus
any window by its class. The dashboard page asks gg_web for that.

If wlrctl is missing the window is closed instead, so the desktop is still
reachable; detection keeps running either way and open_dashboard.sh (the
"Garbage-Guard" icon) brings the dashboard back.
"""
import shutil
import subprocess
import time
from pathlib import Path

# wlrctl matches the whole title ("Garbage-Guard - Chromium"), so match the
# window class instead. The kiosk is the only Chromium window on this Pi.
MATCH = "app_id:chromium"
PROFILE = "user-data-dir=/tmp/gg-chromium"
OPEN = Path(__file__).resolve().parent / "open_dashboard.sh"


def _run(cmd):
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        return r.returncode == 0, (r.stderr or r.stdout).strip()
    except (OSError, subprocess.TimeoutExpired) as e:
        return False, str(e)


def _hidden():
    time.sleep(0.5)
    ok, _ = _run(["wlrctl", "toplevel", "find", MATCH, "state:minimized"])
    return ok


def _shown():
    time.sleep(0.5)
    ok, _ = _run(["wlrctl", "toplevel", "find", MATCH, "state:-minimized"])
    return ok


def minimize():
    msg = "wlrctl not installed"
    if shutil.which("wlrctl"):
        ok, msg = _run(["wlrctl", "toplevel", "minimize", MATCH])
        if ok and _hidden():
            return {"ok": True, "how": "minimized"}
        msg = msg or "window did not minimize"
    # Could not hide it, so close the kiosk window instead. Detection keeps
    # running and the Garbage-Guard icon opens the dashboard again.
    ok, msg2 = _run(["pkill", "-f", PROFILE])
    if ok:
        return {"ok": True, "how": "closed", "note": msg}
    return {"ok": False, "error": msg2 or msg}


def restore():
    if shutil.which("wlrctl"):
        ok, _ = _run(["wlrctl", "toplevel", "focus", MATCH])
        if ok and _shown():
            return {"ok": True, "how": "focused"}
    try:
        subprocess.Popen([str(OPEN)], stdout=subprocess.DEVNULL,
                         stderr=subprocess.DEVNULL, start_new_session=True)
        return {"ok": True, "how": "launched"}
    except OSError as e:
        return {"ok": False, "error": str(e)}

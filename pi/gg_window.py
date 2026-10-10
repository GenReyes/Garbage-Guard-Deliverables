"""Hide and bring back the kiosk dashboard window on the Pi desktop.

Chromium in --kiosk mode has no title bar or taskbar button a finger can
reach, and a web page cannot minimize its own window. The Pi desktop runs the
labwc Wayland compositor, which lets a helper tool (wlrctl) minimize or focus
any window by its title. The dashboard page asks gg_web for that.

If wlrctl is missing the window is closed instead, so the desktop is still
reachable; detection keeps running either way and open_dashboard.sh (the
"Garbage-Guard" icon) brings the dashboard back.
"""
import shutil
import subprocess
from pathlib import Path

TITLE = "Garbage-Guard"
PROFILE = "user-data-dir=/tmp/gg-chromium"
OPEN = Path(__file__).resolve().parent / "open_dashboard.sh"


def _run(cmd):
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
        return r.returncode == 0, (r.stderr or r.stdout).strip()
    except (OSError, subprocess.TimeoutExpired) as e:
        return False, str(e)


def minimize():
    if shutil.which("wlrctl"):
        ok, msg = _run(["wlrctl", "toplevel", "minimize", f"title:{TITLE}"])
        if ok:
            return {"ok": True, "how": "minimized"}
    else:
        msg = "wlrctl not installed"
    # No way to minimize, so close the kiosk window; detection keeps running.
    ok, msg2 = _run(["pkill", "-f", PROFILE])
    if ok:
        return {"ok": True, "how": "closed", "note": msg}
    return {"ok": False, "error": msg2 or msg}


def restore():
    if shutil.which("wlrctl"):
        ok, _ = _run(["wlrctl", "toplevel", "focus", f"title:{TITLE}"])
        if ok:
            return {"ok": True, "how": "focused"}
    try:
        subprocess.Popen([str(OPEN)], stdout=subprocess.DEVNULL,
                         stderr=subprocess.DEVNULL, start_new_session=True)
        return {"ok": True, "how": "launched"}
    except OSError as e:
        return {"ok": False, "error": str(e)}

#!/bin/bash
# Garbage-Guard launcher. Starts detection and opens the dashboard.

cd "$(dirname "$0")" || exit 1

CAMERA_IP=192.168.1.64
PORT=8080

# GG_AUTOSTART=1 is set by install_autostart.sh. In that mode there is no
# terminal to press Enter in, so the script never waits for a key and instead
# restarts detection if it ever stops.
AUTO="${GG_AUTOSTART:-0}"

# Only one copy at a time. A second launch (double-clicked icon after boot)
# just exits instead of fighting over the camera and port 8080.
exec 9>/tmp/garbageguard.lock
if ! flock -n 9; then
    echo "Garbage-Guard is already running."
    [ "$AUTO" = "1" ] || read -r -p "Press Enter to close..."
    exit 0
fi

echo "======================================================"
echo " GARBAGE-GUARD"
echo "======================================================"

if ! ping -c 1 -W 2 "$CAMERA_IP" >/dev/null 2>&1; then
    echo "Camera at $CAMERA_IP did not answer."
    echo "Bringing up the camera-link network profile..."
    sudo nmcli con up camera-link >/dev/null 2>&1
    sleep 3
    if ping -c 1 -W 2 "$CAMERA_IP" >/dev/null 2>&1; then
        echo "Camera reachable."
    else
        echo "STILL UNREACHABLE. Check that:"
        echo "  - the PoE injector has power"
        echo "  - the Pi's ethernet cable is in the injector's LAN port"
        echo "  - the camera cable is in the injector's POE port"
        echo "Starting anyway; it will retry on its own."
    fi
else
    echo "Camera reachable at $CAMERA_IP"
fi

source ~/gg-env/bin/activate || { echo "Could not activate gg-env"; exit 1; }

( sleep 6
  for i in $(seq 1 20); do
      curl -s -o /dev/null "http://127.0.0.1:$PORT/api/state" && break
      sleep 1
  done
  # /start decides where to go: straight to the dashboard when Wi-Fi is up,
  # or to the Wi-Fi setup screen when it is not.
  ./open_dashboard.sh /start ) &

# Close the dashboard window when detection stops, so Ctrl+C leaves nothing
# stranded in fullscreen. Matches only this script's browser profile.
cleanup() { pkill -f "user-data-dir=/tmp/gg-chromium" >/dev/null 2>&1; }
trap cleanup EXIT INT TERM

echo "Dashboard will open shortly at http://127.0.0.1:$PORT (fullscreen)"
echo "Press Ctrl+C here to stop everything."
echo "To reach the desktop, tap the minimize button next to Wi-Fi on the"
echo "dashboard. The Garbage-Guard icon on the desktop brings it back."
echo "======================================================"

if [ "$AUTO" = "1" ]; then
    while true; do
        python garbageguard_live.py "$@"
        rc=$?
        echo "[$(date '+%F %T')] Detection stopped (exit $rc). Restarting in 5 s."
        sleep 5
    done
fi

python garbageguard_live.py "$@"

echo ""
echo "Detection stopped. Close this window, or run sudo shutdown -h now"
read -r -p "Press Enter to close..."

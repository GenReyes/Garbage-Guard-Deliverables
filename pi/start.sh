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

# The dashboard is laid out for a 1024 px wide viewport. If the Pi's screen is
# running at a higher resolution, 1 CSS pixel is still 1 screen pixel, so the
# whole UI shrinks. Scaling the browser by screen width / 1024 makes it render
# at the intended size whatever the panel is set to.
SCREEN_W=""
if command -v xdpyinfo >/dev/null 2>&1; then
    SCREEN_W=$(xdpyinfo 2>/dev/null | awk '/dimensions:/{print $2}' | cut -d x -f1)
fi
if [ -z "$SCREEN_W" ] && [ -r /sys/class/graphics/fb0/virtual_size ]; then
    SCREEN_W=$(cut -d , -f1 /sys/class/graphics/fb0/virtual_size 2>/dev/null)
fi
case "$SCREEN_W" in
    ''|*[!0-9]*) SCALE=1 ;;
    *) SCALE=$(awk -v w="$SCREEN_W" 'BEGIN{
                 s = w / 1024;
                 if (s < 1) s = 1;
                 if (s > 3) s = 3;
                 printf "%.2f", s }') ;;
esac
echo "Screen width ${SCREEN_W:-unknown}px, browser scale ${SCALE}x"

( sleep 6
  for i in $(seq 1 20); do
      curl -s -o /dev/null "http://127.0.0.1:$PORT/api/state" && break
      sleep 1
  done
  # --kiosk is the reliable way to get true fullscreen with no tabs or address
  # bar. --start-fullscreen is ignored by --app windows on some Chromium builds.
  # /start decides where to go: straight to the dashboard when Wi-Fi is up,
  # or to the Wi-Fi setup screen when it is not.
  chromium --no-proxy-server --no-first-run --no-default-browser-check \
           --noerrdialogs --disable-infobars --disable-session-crashed-bubble \
           --user-data-dir=/tmp/gg-chromium \
           --force-device-scale-factor="$SCALE" \
           --kiosk --disable-pinch --overscroll-history-navigation=0 \
           "http://127.0.0.1:$PORT/start" >/dev/null 2>&1 ) &

# Close the dashboard window when detection stops, so Ctrl+C leaves nothing
# stranded in fullscreen. Matches only this script's browser profile.
cleanup() { pkill -f "user-data-dir=/tmp/gg-chromium" >/dev/null 2>&1; }
trap cleanup EXIT INT TERM

echo "Dashboard will open shortly at http://127.0.0.1:$PORT (fullscreen)"
echo "Press Ctrl+C here to stop everything."
echo "To leave fullscreen without stopping: Alt+F4 closes the window,"
echo "or run  chromium --no-proxy-server http://127.0.0.1:$PORT  in a terminal."
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

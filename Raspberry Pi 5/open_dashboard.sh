#!/bin/bash
# Opens the Garbage-Guard dashboard fullscreen, or brings it back if it was
# minimized. start.sh uses this at boot; the "Garbage-Guard" desktop and menu
# icon runs it by hand after the dashboard was minimized or closed.
#
#   ./open_dashboard.sh          dashboard
#   ./open_dashboard.sh /start   Wi-Fi check first (used at boot)

PORT=8080
URL_PATH="${1:-/}"

# Already open but minimized: just bring it to the front.
if pgrep -f "user-data-dir=/tmp/gg-chromium" >/dev/null 2>&1; then
    if command -v wlrctl >/dev/null 2>&1 &&
       wlrctl toplevel focus "app_id:chromium" >/dev/null 2>&1; then
        sleep 0.5
        # Only trust the focus if the window really came back on screen.
        wlrctl toplevel find "app_id:chromium" "state:-minimized" >/dev/null 2>&1 && exit 0
    fi
    # Window exists but cannot be raised (no wlrctl): reopen it cleanly.
    pkill -f "user-data-dir=/tmp/gg-chromium" >/dev/null 2>&1
    sleep 1
fi

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

# --kiosk is the reliable way to get true fullscreen with no tabs or address
# bar. --start-fullscreen is ignored by --app windows on some Chromium builds.
exec chromium --no-proxy-server --no-first-run --no-default-browser-check \
       --noerrdialogs --disable-infobars --disable-session-crashed-bubble \
       --user-data-dir=/tmp/gg-chromium \
       --force-device-scale-factor="$SCALE" \
       --kiosk --disable-pinch --overscroll-history-navigation=0 \
       "http://127.0.0.1:$PORT$URL_PATH" >/dev/null 2>&1

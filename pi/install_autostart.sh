#!/bin/bash
# Makes Garbage-Guard start by itself when the Pi boots, open the dashboard
# fullscreen, and show the Wi-Fi setup screen only when there is no Wi-Fi.
#
#   ./install_autostart.sh            install
#   ./install_autostart.sh --remove   undo it
#
# Run it as the normal desktop user (not with sudo).

set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
CFG="$HOME/.config/labwc"
AUTOSTART="$CFG/autostart"
MARK="# garbage-guard"
LOG="$HERE/gg.log"

if [ "$(id -u)" = "0" ]; then
    echo "Run this as your normal user, not root or sudo."
    exit 1
fi

if [ "${1:-}" = "--remove" ]; then
    if [ -f "$AUTOSTART" ]; then
        grep -v "$MARK" "$AUTOSTART" > "$AUTOSTART.tmp"
        mv "$AUTOSTART.tmp" "$AUTOSTART"
    fi
    echo "Autostart removed. Garbage-Guard will no longer start at boot."
    exit 0
fi

chmod +x "$HERE/start.sh"
mkdir -p "$CFG"
touch "$AUTOSTART"

# A fresh labwc autostart does not exist, so on some installs the system one
# (/etc/xdg/labwc/autostart) is used for the panel and wallpaper. Creating our
# own file replaces it, so copy the system lines in first, once.
if [ ! -s "$AUTOSTART" ] && [ -f /etc/xdg/labwc/autostart ]; then
    cp /etc/xdg/labwc/autostart "$AUTOSTART"
fi

grep -v "$MARK" "$AUTOSTART" > "$AUTOSTART.tmp"
echo "GG_AUTOSTART=1 \"$HERE/start.sh\" >> \"$LOG\" 2>&1 & $MARK" >> "$AUTOSTART.tmp"
mv "$AUTOSTART.tmp" "$AUTOSTART"
echo "Added to $AUTOSTART:"
grep "$MARK" "$AUTOSTART"

echo
read -r -p "Stop the screen from going blank while Garbage-Guard runs? [Y/n] " a
if [ "${a:-Y}" != "n" ] && [ "${a:-Y}" != "N" ]; then
    sudo raspi-config nonint do_blanking 1 && echo "Screen blanking disabled."
fi

echo
if grep -qs "^autologin-user=" /etc/lightdm/lightdm.conf /etc/lightdm/lightdm.conf.d/* 2>/dev/null; then
    echo "Desktop auto login looks enabled."
else
    echo "IMPORTANT: turn on desktop auto login or nothing will start at boot:"
    echo "  sudo raspi-config  ->  System Options  ->  Boot / Auto Login  ->  Desktop Autologin"
fi

# Wi-Fi on the same 192.168.1.x range as the camera cable breaks the camera.
if nmcli -g ipv4.addresses connection show camera-link 2>/dev/null | grep -q "/24"; then
    echo
    echo "The camera cable profile uses 192.168.1.101/24. If your Wi-Fi router also"
    echo "hands out 192.168.1.x addresses, the camera feed can drop once Wi-Fi joins."
    read -r -p "Give the camera its own route now (safe, undo with: sudo nmcli con modify camera-link ipv4.addresses 192.168.1.101/24)? [y/N] " b
    if [ "${b:-N}" = "y" ] || [ "${b:-N}" = "Y" ]; then
        sudo nmcli connection modify camera-link ipv4.addresses 192.168.1.101/32 \
            ipv4.method manual ipv4.routes 192.168.1.64/32 ipv4.never-default yes \
            && sudo nmcli connection up camera-link && echo "Camera route set."
    fi
fi

echo
echo "Done. Reboot to test:  sudo reboot"
echo "Boot messages are saved in $LOG"

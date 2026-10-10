import ipaddress
import os
import re
import shutil
import socket
import subprocess
import threading
import time

ADDR = re.compile(r"\b\d{1,3}(?:\.\d{1,3}){3}/\d{1,2}\b")
CAMERA_PROFILE = "camera-link"
PROBES = (("1.1.1.1", 443), ("8.8.8.8", 53), ("9.9.9.9", 53))


def split_terse(line):
    fields, cur, i = [], [], 0
    while i < len(line):
        c = line[i]
        if c == "\\" and i + 1 < len(line):
            cur.append(line[i + 1])
            i += 2
            continue
        if c == ":":
            fields.append("".join(cur))
            cur = []
        else:
            cur.append(c)
        i += 1
    fields.append("".join(cur))
    return fields


def parse_devices(text):
    out = []
    for line in text.splitlines():
        f = split_terse(line)
        if len(f) >= 4 and f[0]:
            out.append({"device": f[0], "type": f[1], "state": f[2], "connection": f[3]})
    return out


def parse_wifi_list(text):
    best = {}
    for line in text.splitlines():
        f = split_terse(line)
        if len(f) < 4 or not f[1].strip():
            continue
        ssid = f[1]
        try:
            signal = int(f[2])
        except ValueError:
            signal = 0
        sec = f[3].strip()
        secure = sec not in ("", "--")
        row = {"ssid": ssid, "signal": signal, "security": "" if not secure else sec,
               "secure": secure, "in_use": f[0].strip() == "*",
               "enterprise": "802.1X" in sec}
        old = best.get(ssid)
        if old is None or row["in_use"] or (not old["in_use"] and signal > old["signal"]):
            best[ssid] = row
    return sorted(best.values(), key=lambda r: (not r["in_use"], -r["signal"], r["ssid"].lower()))


def parse_names(text, kind):
    names = []
    for line in text.splitlines():
        f = split_terse(line)
        if len(f) >= 2 and f[1] == kind:
            names.append(f[0])
    return names


def friendly_error(text):
    t = (text or "").lower()
    if "secrets were required" in t or "password" in t or "(7)" in t or "802-1x" in t:
        return "bad_password", "Wrong password, or the network refused the connection."
    if "no network with ssid" in t or "could not be found" in t:
        return "not_found", "That network is out of range or its name is wrong."
    if "no wi-fi device" in t or "no suitable device" in t:
        return "no_device", "No Wi-Fi adapter was found on this Pi."
    if "rfkill" in t or "wi-fi is disabled" in t or "wireless is disabled" in t:
        return "radio_off", "Wi-Fi is switched off. Set the Wi-Fi country in raspi-config, then try again."
    if "timeout" in t or "timed out" in t:
        return "timeout", "The network took too long to answer."
    if "not found" in t and "nmcli" in t:
        return "no_nmcli", "NetworkManager is not available on this system."
    one = " ".join((text or "").split())[:140]
    return "failed", one or "Could not connect."


def internet_ok(timeout=1.2, probes=PROBES):
    for host, port in probes:
        try:
            with socket.create_connection((host, port), timeout=timeout):
                return True
        except OSError:
            continue
    return False


def run_cmd(args, timeout=20):
    try:
        p = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
        return p.returncode, p.stdout, p.stderr
    except FileNotFoundError:
        return 127, "", "nmcli not found"
    except subprocess.TimeoutExpired:
        return 124, "", "timeout"


class Net:
    def __init__(self, camera_ip="192.168.1.64", runner=run_cmd, probe=internet_ok,
                 settle_s=25):
        self.camera_ip = camera_ip
        self.settle_s = settle_s
        self._run = runner
        self._probe = probe
        self._lock = threading.Lock()
        self._cache = (0.0, None)
        self.backend = "nmcli" if (runner is not run_cmd or shutil.which("nmcli")) else "none"

    def _nm(self, *args, timeout=20):
        return self._run(["nmcli", *args], timeout)

    def _wifi_device(self):
        rc, out, _ = self._nm("-t", "-f", "DEVICE,TYPE,STATE,CONNECTION", "device")
        devs = parse_devices(out) if rc == 0 else []
        wifi = [d for d in devs if d["type"] == "wifi"]
        return devs, (wifi[0] if wifi else None)

    def _ips(self, device):
        rc, out, _ = self._nm("-t", "-f", "IP4.ADDRESS", "device", "show", device)
        return ADDR.findall(out) if rc == 0 else []

    def _camera_link_prefix(self):
        rc, out, _ = self._nm("-g", "ipv4.addresses", "connection", "show", CAMERA_PROFILE)
        found = ADDR.findall(out) if rc == 0 else []
        if not found:
            return None
        return int(found[0].split("/")[1])

    def status(self, max_age=3.0):
        now = time.time()
        stamp, cached = self._cache
        if cached is not None and now - stamp < max_age:
            return cached
        s = self._status()
        self._cache = (time.time(), s)
        return s

    def _status(self):
        s = {"backend": self.backend, "has_wifi_hw": False, "radio_on": None,
             "wifi_connected": False, "wifi_connecting": False, "ssid": None,
             "wifi_ip": None, "wifi_device": None, "ethernet": [], "internet": False,
             "camera_ip": self.camera_ip, "camera_conflict": False,
             "settle_s": self.settle_s}
        if self.backend == "none":
            s["internet"] = self._probe()
            return s
        devs, wifi = self._wifi_device()
        for d in devs:
            if d["type"] == "ethernet" and d["state"] == "connected":
                ips = self._ips(d["device"])
                s["ethernet"].append({"device": d["device"], "connection": d["connection"],
                                      "ip": ips[0] if ips else None})
        if wifi:
            s["has_wifi_hw"] = True
            s["wifi_device"] = wifi["device"]
            st = wifi["state"]
            s["wifi_connecting"] = st.startswith("connecting")
            if st == "connected":
                s["wifi_connected"] = True
                s["ssid"] = wifi["connection"] or None
                ips = self._ips(wifi["device"])
                s["wifi_ip"] = ips[0] if ips else None
            rc, out, _ = self._nm("radio", "wifi")
            if rc == 0:
                s["radio_on"] = out.strip() == "enabled"
        s["internet"] = self._probe()
        s["camera_conflict"] = self._conflict(s["wifi_ip"])
        return s

    def _conflict(self, wifi_ip):
        if not wifi_ip:
            return False
        try:
            net = ipaddress.ip_interface(wifi_ip).network
            cam = ipaddress.ip_address(self.camera_ip)
        except ValueError:
            return False
        if cam not in net:
            return False
        prefix = self._camera_link_prefix()
        return prefix is not None and prefix < 32

    def scan(self, rescan=True):
        with self._lock:
            self._nm("radio", "wifi", "on", timeout=8)
            _, wifi = self._wifi_device()
            if not wifi:
                return {"ok": False, "error": "no_device",
                        "message": "No Wi-Fi adapter was found on this Pi.", "networks": []}
            args = ["-t", "-f", "IN-USE,SSID,SIGNAL,SECURITY", "device", "wifi", "list",
                    "ifname", wifi["device"], "--rescan", "yes" if rescan else "auto"]
            rc, out, err = self._nm(*args, timeout=25)
            if rc != 0:
                code, msg = friendly_error(err)
                return {"ok": False, "error": code, "message": msg, "networks": []}
            return {"ok": True, "networks": parse_wifi_list(out)}

    def connect(self, ssid, password=None, hidden=False):
        ssid = (ssid or "").strip()
        if not ssid:
            return {"ok": False, "error": "no_ssid", "message": "Pick a network first."}
        with self._lock:
            self._nm("radio", "wifi", "on", timeout=8)
            _, wifi = self._wifi_device()
            if not wifi:
                return {"ok": False, "error": "no_device",
                        "message": "No Wi-Fi adapter was found on this Pi."}
            rc, out, _ = self._nm("-t", "-f", "NAME,TYPE", "connection", "show")
            existed = ssid in parse_names(out, "802-11-wireless") if rc == 0 else False
            if password and existed:
                self._nm("connection", "delete", "id", ssid, timeout=15)
                existed = False
            args = ["--wait", "30", "device", "wifi", "connect", ssid]
            if password:
                args += ["password", password]
            if hidden:
                args += ["hidden", "yes"]
            args += ["ifname", wifi["device"]]
            rc, out, err = self._nm(*args, timeout=45)
            self._cache = (0.0, None)
            if rc == 0:
                return {"ok": True, "ssid": ssid}
            if not existed:
                self._nm("connection", "delete", "id", ssid, timeout=15)
            code, msg = friendly_error(err or out)
            return {"ok": False, "error": code, "message": msg}

    def fix_camera_route(self):
        with self._lock:
            rc, out, _ = self._nm("-g", "ipv4.addresses", "connection", "show", CAMERA_PROFILE)
            found = ADDR.findall(out) if rc == 0 else []
            if not found:
                return {"ok": False, "error": "no_profile",
                        "message": "The camera-link network profile was not found."}
            ip = found[0].split("/")[0]
            rc, _, err = self._nm("connection", "modify", CAMERA_PROFILE,
                                  "ipv4.addresses", f"{ip}/32", "ipv4.method", "manual",
                                  "ipv4.routes", f"{self.camera_ip}/32",
                                  "ipv4.never-default", "yes", timeout=15)
            if rc != 0:
                return {"ok": False, "error": "modify_failed", "message": " ".join(err.split())[:140]}
            rc, _, err = self._nm("connection", "up", CAMERA_PROFILE, timeout=25)
            self._cache = (0.0, None)
            if rc != 0:
                return {"ok": False, "error": "up_failed", "message": " ".join(err.split())[:140]}
            return {"ok": True}


class FakeNet(Net):
    NETS = [
        {"ssid": "HomeNet", "signal": 78, "security": "WPA2", "secure": True, "enterprise": False},
        {"ssid": "Neighbor:5G", "signal": 52, "security": "WPA2 WPA3", "secure": True, "enterprise": False},
        {"ssid": "OpenCafe", "signal": 41, "security": "", "secure": False, "enterprise": False},
        {"ssid": "CampusSecure", "signal": 63, "security": "WPA2 802.1X", "secure": True, "enterprise": True},
        {"ssid": "Far-Away", "signal": 12, "security": "WPA2", "secure": True, "enterprise": False},
    ]

    def __init__(self, scenario="offline", camera_ip="192.168.1.64", settle_s=2):
        super().__init__(camera_ip=camera_ip, runner=lambda a, t: (0, "", ""),
                         probe=lambda: False, settle_s=settle_s)
        self.backend = "fake"
        self.ssid = "HomeNet" if scenario in ("connected", "conflict") else None
        self.conflict = scenario == "conflict"
        self.no_hw = scenario == "nohw"

    def status(self, max_age=3.0):
        on = self.ssid is not None
        return {"backend": "fake", "has_wifi_hw": not self.no_hw, "radio_on": True,
                "wifi_connected": on, "wifi_connecting": False, "ssid": self.ssid,
                "wifi_ip": "192.168.1.20/24" if on else None, "wifi_device": "wlan0",
                "ethernet": [{"device": "eth0", "connection": "camera-link", "ip": "192.168.1.101/24"}],
                "internet": on, "camera_ip": self.camera_ip,
                "camera_conflict": self.conflict and on, "settle_s": self.settle_s}

    def scan(self, rescan=True):
        time.sleep(0.6)
        nets = [dict(n, in_use=(n["ssid"] == self.ssid)) for n in self.NETS]
        nets.sort(key=lambda r: (not r["in_use"], -r["signal"]))
        return {"ok": True, "networks": nets}

    def connect(self, ssid, password=None, hidden=False):
        time.sleep(1.2)
        row = next((n for n in self.NETS if n["ssid"] == ssid), None)
        if row is None and not hidden:
            return {"ok": False, "error": "not_found",
                    "message": "That network is out of range or its name is wrong."}
        if row and row["enterprise"]:
            return {"ok": False, "error": "failed", "message": "Needs a certificate."}
        if row and row["secure"] and password != "goodpass":
            return {"ok": False, "error": "bad_password",
                    "message": "Wrong password, or the network refused the connection."}
        self.ssid = ssid
        self.conflict = True
        return {"ok": True, "ssid": ssid}

    def fix_camera_route(self):
        time.sleep(0.8)
        self.conflict = False
        return {"ok": True}


def make_net(camera_ip="192.168.1.64"):
    fake = os.environ.get("GG_FAKE_NET")
    if fake:
        return FakeNet(scenario=fake if fake != "1" else "offline", camera_ip=camera_ip)
    return Net(camera_ip=camera_ip)

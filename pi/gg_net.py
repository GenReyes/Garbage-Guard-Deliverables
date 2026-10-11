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


def parse_device_show(text):
    devs, cur = [], None
    for line in text.splitlines():
        if not line.strip():
            continue
        key, _, val = line.partition(":")
        key = key.split("[")[0]
        if key == "GENERAL.DEVICE":
            cur = {"device": val, "ips": [], "dns": []}
            devs.append(cur)
            continue
        if cur is None:
            continue
        if key == "GENERAL.TYPE":
            cur["type"] = val
        elif key == "GENERAL.HWADDR":
            cur["mac"] = val.replace("\\", "") or None
        elif key == "GENERAL.STATE":
            m = re.search(r"\((.*)\)", val)
            cur["state"] = m.group(1) if m else val
        elif key == "GENERAL.CONNECTION":
            cur["connection"] = val or None
        elif key == "CAPABILITIES.SPEED":
            cur["speed"] = None if val in ("", "unknown") else val
        elif key == "IP4.ADDRESS" and val:
            cur["ips"].append(val)
        elif key == "IP4.GATEWAY":
            cur["gateway"] = val or None
        elif key == "IP4.DNS" and val:
            cur["dns"].append(val)
    return devs


def parse_wifi_link(text):
    for line in text.splitlines():
        f = split_terse(line)
        if len(f) >= 8 and f[0].strip() == "*":
            try:
                signal = int(f[6])
            except ValueError:
                signal = None
            return {"ssid": f[1], "bssid": f[2], "channel": f[3], "freq": f[4],
                    "rate": f[5], "signal": signal, "security": f[7] or "Open"}
    return None


def parse_default_route(text):
    best = None
    for line in text.splitlines():
        parts = line.split()
        if not parts or parts[0] != "default" or "dev" not in parts:
            continue
        dev = parts[parts.index("dev") + 1]
        metric = int(parts[parts.index("metric") + 1]) if "metric" in parts else 0
        if best is None or metric < best[1]:
            best = (dev, metric)
    return best[0] if best else None


def validate_ipv4(cfg, camera_ip="192.168.1.64", reserved=()):
    """Checks a static address before NetworkManager ever sees it.

    Returns (clean, errors). errors maps a field name to a sentence the
    touchscreen can show next to that field.
    """
    errors, clean = {}, {"method": cfg.get("method", "auto")}
    if clean["method"] not in ("auto", "manual"):
        return clean, {"method": "Pick Automatic or Manual."}
    if clean["method"] == "auto":
        return clean, {}

    def addr(field, text):
        text = (text or "").strip()
        if not re.fullmatch(r"\d{1,3}(\.\d{1,3}){3}", text):
            errors[field] = "Four numbers from 0 to 255, separated by dots."
            return None
        if any(len(p) > 1 and p.startswith("0") for p in text.split(".")):
            errors[field] = "Leave out leading zeros (write 1, not 001)."
            return None
        try:
            return ipaddress.IPv4Address(text)
        except ValueError:
            errors[field] = "Each number must be 0 to 255."
            return None

    ip = addr("address", cfg.get("address"))
    try:
        prefix = int(cfg.get("prefix", 24))
    except (TypeError, ValueError):
        prefix = -1
    if not 8 <= prefix <= 30:
        errors["prefix"] = "Use a subnet between /8 and /30. Home networks are almost always /24."
        prefix = None
    gw = addr("gateway", cfg.get("gateway"))
    dns = []
    for i, d in enumerate([x for x in (cfg.get("dns") or []) if str(x).strip()][:2]):
        v = addr(f"dns{i + 1}", d)
        if v is not None:
            dns.append(str(v))

    if ip is not None and prefix is not None:
        net = ipaddress.IPv4Network(f"{ip}/{prefix}", strict=False)
        if ip.is_loopback or ip.is_multicast or ip.is_unspecified or ip.is_link_local:
            errors["address"] = "That kind of address cannot be used on a network."
        elif ip == net.network_address:
            errors["address"] = f"{ip} is the network's own name, not a usable address."
        elif ip == net.broadcast_address:
            errors["address"] = f"{ip} is the broadcast address for this network."
        elif str(ip) == camera_ip:
            errors["address"] = "That is the camera's address."
        elif str(ip) in reserved:
            errors["address"] = "The camera cable already uses this address. Pick another."
        if gw is not None and "address" not in errors:
            if gw not in net:
                errors["gateway"] = f"The gateway must be inside {net}."
            elif gw == ip:
                errors["gateway"] = "The gateway is the router, not this Pi. Use a different address."
        clean.update(address=str(ip), prefix=prefix, network=str(net))
    if gw is not None:
        clean["gateway"] = str(gw)
    clean["dns"] = dns
    return clean, errors


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

    def details(self):
        out = {"backend": self.backend, "interfaces": [], "default_dev": None,
               "internet": False, "camera_ip": self.camera_ip}
        if self.backend == "none":
            out["internet"] = self._probe()
            return out
        fields = ("GENERAL.DEVICE,GENERAL.TYPE,GENERAL.HWADDR,GENERAL.STATE,GENERAL.CONNECTION,"
                  "CAPABILITIES.SPEED,IP4.ADDRESS,IP4.GATEWAY,IP4.DNS")
        rc, text, _ = self._nm("-t", "-f", fields, "device", "show")
        devs = parse_device_show(text) if rc == 0 else []
        rc, routes, _ = self._run(["ip", "-4", "route", "show", "default"], 5)
        out["default_dev"] = parse_default_route(routes) if rc == 0 else None
        for d in devs:
            if d.get("type") not in ("wifi", "ethernet"):
                continue
            d["role"] = ("camera" if d.get("connection") == CAMERA_PROFILE
                         else "wifi" if d["type"] == "wifi" else "wired")
            d["default"] = d["device"] == out["default_dev"]
            d["method"] = None
            if d.get("connection"):
                rc, m, _ = self._nm("-g", "ipv4.method", "connection", "show", d["connection"])
                d["method"] = (m.strip() or None) if rc == 0 else None
            if d["type"] == "wifi" and d.get("state") == "connected":
                rc, w, _ = self._nm("-t", "-f", "IN-USE,SSID,BSSID,CHAN,FREQ,RATE,SIGNAL,SECURITY",
                                    "device", "wifi", "list", "ifname", d["device"], "--rescan", "no")
                d["link"] = parse_wifi_link(w) if rc == 0 else None
            out["interfaces"].append(d)
        out["internet"] = self._probe()
        return out

    def _reserved(self):
        rc, out, _ = self._nm("-g", "ipv4.addresses", "connection", "show", CAMERA_PROFILE)
        return tuple(a.split("/")[0] for a in ADDR.findall(out)) if rc == 0 else ()

    def set_ipv4(self, connection, cfg):
        connection = (connection or "").strip()
        if not connection:
            return {"ok": False, "error": "no_connection", "message": "Pick a connection first."}
        if connection == CAMERA_PROFILE:
            return {"ok": False, "error": "camera_profile",
                    "message": "The camera cable's settings are fixed so the camera keeps working."}
        clean, errors = validate_ipv4(cfg, self.camera_ip, self._reserved())
        if errors:
            return {"ok": False, "error": "invalid", "fields": errors,
                    "message": next(iter(errors.values()))}
        if clean["method"] == "auto":
            args = ["ipv4.method", "auto", "ipv4.addresses", "", "ipv4.gateway", "",
                    "ipv4.dns", "", "ipv4.ignore-auto-dns", "no"]
        else:
            args = ["ipv4.method", "manual",
                    "ipv4.addresses", f"{clean['address']}/{clean['prefix']}",
                    "ipv4.gateway", clean.get("gateway", ""),
                    "ipv4.dns", " ".join(clean["dns"]),
                    "ipv4.ignore-auto-dns", "yes" if clean["dns"] else "no"]
        with self._lock:
            rc, _, err = self._nm("connection", "modify", connection, *args, timeout=15)
            if rc != 0:
                return {"ok": False, "error": "modify_failed", "message": " ".join(err.split())[:160]}
            rc, _, err = self._nm("connection", "up", connection, timeout=40)
            self._cache = (0.0, None)
            if rc != 0:
                return {"ok": False, "error": "up_failed", "applied": clean,
                        "message": "Saved, but the connection did not come back up. "
                                   "Switch it to Automatic to undo. " + " ".join(err.split())[:120]}
        return {"ok": True, "applied": clean}

    def camera_up(self):
        rc, _, _ = self._run(["ping", "-c", "1", "-W", "2", self.camera_ip], 5)
        if rc == 0:
            return {"ok": True, "reachable": True, "action": "none"}
        rc, _, err = self._nm("con", "up", CAMERA_PROFILE, timeout=20)
        rc2, _, _ = self._run(["ping", "-c", "1", "-W", "2", self.camera_ip], 5)
        return {"ok": rc2 == 0, "reachable": rc2 == 0, "action": "camera-link up",
                "message": "" if rc == 0 else friendly_error(err)}

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

    def details(self):
        on = self.ssid is not None
        ifs = [
            {"device": "eth0", "type": "ethernet", "state": "connected", "connection": "camera-link",
             "mac": "D8:3A:DD:00:00:01", "speed": "100 Mb/s", "ips": ["192.168.1.101/24"],
             "gateway": None, "dns": [], "role": "camera", "default": False, "method": "manual"},
            {"device": "eth1", "type": "ethernet", "state": "connected", "connection": "Wired connection 1",
             "mac": "00:E0:4C:00:00:02", "speed": "1000 Mb/s", "ips": ["192.168.1.15/24"],
             "gateway": "192.168.1.1", "dns": ["192.168.1.1"], "role": "wired", "default": True,
             "method": "auto"},
            {"device": "wlan0", "type": "wifi", "state": "connected" if on else "disconnected",
             "connection": self.ssid, "mac": "D8:3A:DD:00:00:03", "speed": None,
             "ips": ["192.168.1.20/24"] if on else [], "gateway": "192.168.1.1" if on else None,
             "dns": ["192.168.1.1"] if on else [], "role": "wifi", "default": False,
             "method": "auto" if on else None,
             "link": {"ssid": self.ssid, "bssid": "AA:BB:CC:DD:EE:FF", "channel": "6",
                      "freq": "2437 MHz", "rate": "65 Mbit/s", "signal": 62,
                      "security": "WPA2"} if on else None},
        ]
        return {"backend": "fake", "interfaces": ifs, "default_dev": "eth1",
                "internet": True, "camera_ip": self.camera_ip}

    def set_ipv4(self, connection, cfg):
        clean, errors = validate_ipv4(cfg, self.camera_ip, ("192.168.1.101",))
        if errors:
            return {"ok": False, "error": "invalid", "fields": errors,
                    "message": next(iter(errors.values()))}
        time.sleep(0.8)
        return {"ok": True, "applied": clean}

    def camera_up(self):
        return {"ok": True, "reachable": True, "action": "none"}

    def fix_camera_route(self):
        time.sleep(0.8)
        self.conflict = False
        return {"ok": True}


def make_net(camera_ip="192.168.1.64"):
    fake = os.environ.get("GG_FAKE_NET")
    if fake:
        return FakeNet(scenario=fake if fake != "1" else "offline", camera_ip=camera_ip)
    return Net(camera_ip=camera_ip)

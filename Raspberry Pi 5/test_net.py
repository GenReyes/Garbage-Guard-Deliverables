import unittest

import gg_net as g


def runner_for(table):
    calls = []

    def run(args, timeout=20):
        calls.append(args)
        key = " ".join(args[1:])
        for k, v in table.items():
            if key.startswith(k):
                return v
        return 0, "", ""
    run.calls = calls
    return run


class ParseTests(unittest.TestCase):
    def test_split_terse_escapes(self):
        self.assertEqual(g.split_terse(r"*:Neighbor\:5G:52:WPA2"), ["*", "Neighbor:5G", "52", "WPA2"])

    def test_devices(self):
        d = g.parse_devices("wlan0:wifi:connected:HomeNet\neth0:ethernet:connected:camera-link\nlo:loopback:unmanaged:")
        self.assertEqual(d[0]["type"], "wifi")
        self.assertEqual(d[1]["connection"], "camera-link")

    def test_wifi_list_dedupes_and_sorts(self):
        txt = " :HomeNet:60:WPA2\n*:HomeNet:75:WPA2\n :Open:40:\n ::90:WPA2\n :Weak:10:WPA2"
        n = g.parse_wifi_list(txt)
        self.assertEqual([x["ssid"] for x in n], ["HomeNet", "Open", "Weak"])
        self.assertTrue(n[0]["in_use"])
        self.assertFalse(n[1]["secure"])

    def test_enterprise_flag(self):
        n = g.parse_wifi_list(" :Campus:60:WPA2 802.1X")
        self.assertTrue(n[0]["enterprise"])

    def test_friendly_errors(self):
        self.assertEqual(g.friendly_error("Error: Connection activation failed: Secrets were required")[0], "bad_password")
        self.assertEqual(g.friendly_error("Error: No network with SSID 'x' found.")[0], "not_found")
        self.assertEqual(g.friendly_error("")[0], "failed")


class NetTests(unittest.TestCase):
    def test_status_connected(self):
        r = runner_for({
            "-t -f DEVICE,TYPE,STATE,CONNECTION device": (0, "wlan0:wifi:connected:HomeNet\neth0:ethernet:connected:camera-link\n", ""),
            "-t -f IP4.ADDRESS device show wlan0": (0, "IP4.ADDRESS[1]:192.168.1.20/24\n", ""),
            "-t -f IP4.ADDRESS device show eth0": (0, "IP4.ADDRESS[1]:192.168.1.101/24\n", ""),
            "radio wifi": (0, "enabled\n", ""),
            "-g ipv4.addresses": (0, "192.168.1.101/24\n", ""),
        })
        s = g.Net(runner=r, probe=lambda: True).status(max_age=0)
        self.assertTrue(s["wifi_connected"])
        self.assertEqual(s["ssid"], "HomeNet")
        self.assertTrue(s["camera_conflict"])
        self.assertTrue(s["internet"])

    def test_no_conflict_after_32(self):
        r = runner_for({
            "-t -f DEVICE,TYPE,STATE,CONNECTION device": (0, "wlan0:wifi:connected:HomeNet\n", ""),
            "-t -f IP4.ADDRESS device show wlan0": (0, "IP4.ADDRESS[1]:192.168.1.20/24\n", ""),
            "radio wifi": (0, "enabled\n", ""),
            "-g ipv4.addresses": (0, "192.168.1.101/32\n", ""),
        })
        s = g.Net(runner=r, probe=lambda: True).status(max_age=0)
        self.assertFalse(s["camera_conflict"])

    def test_no_conflict_other_subnet(self):
        r = runner_for({
            "-t -f DEVICE,TYPE,STATE,CONNECTION device": (0, "wlan0:wifi:connected:Home\n", ""),
            "-t -f IP4.ADDRESS device show wlan0": (0, "IP4.ADDRESS[1]:10.0.0.5/24\n", ""),
            "radio wifi": (0, "enabled\n", ""),
            "-g ipv4.addresses": (0, "192.168.1.101/24\n", ""),
        })
        self.assertFalse(g.Net(runner=r, probe=lambda: True).status(max_age=0)["camera_conflict"])

    def test_offline_status(self):
        r = runner_for({
            "-t -f DEVICE,TYPE,STATE,CONNECTION device": (0, "wlan0:wifi:disconnected:\n", ""),
            "radio wifi": (0, "enabled\n", ""),
        })
        s = g.Net(runner=r, probe=lambda: False).status(max_age=0)
        self.assertFalse(s["wifi_connected"])
        self.assertTrue(s["has_wifi_hw"])

    def test_connect_cleans_failed_profile(self):
        r = runner_for({
            "-t -f DEVICE,TYPE,STATE,CONNECTION device": (0, "wlan0:wifi:disconnected:\n", ""),
            "-t -f NAME,TYPE connection show": (0, "camera-link:802-3-ethernet\n", ""),
            "--wait 30 device wifi connect": (4, "", "Error: Connection activation failed: Secrets were required"),
        })
        res = g.Net(runner=r, probe=lambda: False).connect("HomeNet", "bad")
        self.assertFalse(res["ok"])
        self.assertEqual(res["error"], "bad_password")
        self.assertTrue(any(c[1:5] == ["connection", "delete", "id", "HomeNet"] for c in r.calls))

    def test_connect_replaces_stale_profile_when_password_given(self):
        r = runner_for({
            "-t -f DEVICE,TYPE,STATE,CONNECTION device": (0, "wlan0:wifi:disconnected:\n", ""),
            "-t -f NAME,TYPE connection show": (0, "HomeNet:802-11-wireless\n", ""),
        })
        res = g.Net(runner=r, probe=lambda: False).connect("HomeNet", "pw")
        self.assertTrue(res["ok"])
        self.assertTrue(any(c[1:5] == ["connection", "delete", "id", "HomeNet"] for c in r.calls))

    def test_connect_requires_ssid(self):
        self.assertEqual(g.Net(runner=runner_for({}), probe=lambda: False).connect("  ")["error"], "no_ssid")

    def test_fix_camera_route_commands(self):
        r = runner_for({"-g ipv4.addresses": (0, "192.168.1.101/24\n", "")})
        res = g.Net(runner=r).fix_camera_route()
        self.assertTrue(res["ok"])
        mod = [c for c in r.calls if c[1:3] == ["connection", "modify"]][0]
        self.assertIn("192.168.1.101/32", mod)
        self.assertIn("192.168.1.64/32", mod)

    def test_password_never_in_result(self):
        r = runner_for({
            "-t -f DEVICE,TYPE,STATE,CONNECTION device": (0, "wlan0:wifi:disconnected:\n", ""),
            "--wait 30 device wifi connect": (4, "", "Error: failed"),
        })
        res = g.Net(runner=r, probe=lambda: False).connect("X", "supersecret")
        self.assertNotIn("supersecret", str(res))


class FakeTests(unittest.TestCase):
    def test_camera_up_skips_when_reachable(self):
        run = runner_for({})
        res = g.Net(runner=run).camera_up()
        self.assertTrue(res["reachable"])
        self.assertFalse(any(c[0] == "nmcli" for c in run.calls))

    def test_camera_up_brings_profile_up(self):
        state = {"pings": 0}

        def run(args, timeout=20):
            run.calls.append(args)
            if args[0] == "ping":
                state["pings"] += 1
                return (1 if state["pings"] == 1 else 0), "", ""
            return 0, "", ""
        run.calls = []
        res = g.Net(runner=run).camera_up()
        self.assertIn(["nmcli", "con", "up", "camera-link"], run.calls)
        self.assertTrue(res["ok"])

    def test_fake_flow(self):
        f = g.FakeNet("offline", settle_s=0)
        self.assertFalse(f.status()["wifi_connected"])
        self.assertEqual(f.connect("HomeNet", "nope")["error"], "bad_password")
        self.assertTrue(f.connect("HomeNet", "goodpass")["ok"])
        self.assertTrue(f.status()["camera_conflict"])
        self.assertTrue(f.fix_camera_route()["ok"])
        self.assertFalse(f.status()["camera_conflict"])


class DetailTests(unittest.TestCase):
    SHOW = ("GENERAL.DEVICE:eth1\nGENERAL.TYPE:ethernet\nGENERAL.HWADDR:00\\:E0\\:4C\\:68\\:00\\:01\n"
            "GENERAL.STATE:100 (connected)\nGENERAL.CONNECTION:Wired connection 1\n"
            "CAPABILITIES.SPEED:1000 Mb/s\nIP4.ADDRESS[1]:192.168.1.15/24\nIP4.GATEWAY:192.168.1.1\n"
            "IP4.DNS[1]:192.168.1.1\n\nGENERAL.DEVICE:wlan0\nGENERAL.TYPE:wifi\n"
            "GENERAL.HWADDR:D8\\:3A\\:DD\\:00\\:00\\:03\nGENERAL.STATE:30 (disconnected)\n"
            "GENERAL.CONNECTION:\nCAPABILITIES.SPEED:unknown\nIP4.GATEWAY:\n")

    def test_device_show(self):
        d = g.parse_device_show(self.SHOW)
        self.assertEqual(len(d), 2)
        self.assertEqual(d[0]["mac"], "00:E0:4C:68:00:01")
        self.assertEqual(d[0]["state"], "connected")
        self.assertEqual(d[0]["ips"], ["192.168.1.15/24"])
        self.assertEqual(d[0]["dns"], ["192.168.1.1"])
        self.assertEqual(d[1]["state"], "disconnected")
        self.assertIsNone(d[1]["connection"])
        self.assertIsNone(d[1]["speed"])

    def test_wifi_link(self):
        txt = " :Other:AA\\:AA\\:AA\\:AA\\:AA\\:AA:1:2412 MHz:54 Mbit/s:30:WPA2\n" \
              "*:Home:BB\\:BB\\:BB\\:BB\\:BB\\:BB:6:2437 MHz:65 Mbit/s:72:WPA2"
        w = g.parse_wifi_link(txt)
        self.assertEqual(w["ssid"], "Home")
        self.assertEqual(w["bssid"], "BB:BB:BB:BB:BB:BB")
        self.assertEqual(w["signal"], 72)

    def test_default_route_lowest_metric(self):
        r = "default via 192.168.1.1 dev wlan0 proto dhcp metric 600\n" \
            "default via 192.168.1.1 dev eth1 proto dhcp metric 100\n"
        self.assertEqual(g.parse_default_route(r), "eth1")
        self.assertIsNone(g.parse_default_route(""))


class Ipv4Tests(unittest.TestCase):
    def ok(self, **kw):
        cfg = {"method": "manual", "address": "192.168.1.50", "prefix": 24, "gateway": "192.168.1.1"}
        cfg.update(kw)
        return g.validate_ipv4(cfg, "192.168.1.64", ("192.168.1.101",))

    def test_auto_needs_nothing(self):
        self.assertEqual(g.validate_ipv4({"method": "auto"})[1], {})

    def test_good_manual(self):
        clean, err = self.ok(dns=["8.8.8.8", ""])
        self.assertEqual(err, {})
        self.assertEqual(clean["network"], "192.168.1.0/24")
        self.assertEqual(clean["dns"], ["8.8.8.8"])

    def test_bad_shapes(self):
        self.assertIn("address", self.ok(address="192.168.1")[1])
        self.assertIn("address", self.ok(address="192.168.1.300")[1])
        self.assertIn("address", self.ok(address="192.168.001.5")[1])
        self.assertIn("prefix", self.ok(prefix=31)[1])

    def test_reserved_and_special(self):
        self.assertIn("address", self.ok(address="192.168.1.64")[1])
        self.assertIn("address", self.ok(address="192.168.1.101")[1])
        self.assertIn("address", self.ok(address="192.168.1.0")[1])
        self.assertIn("address", self.ok(address="192.168.1.255")[1])

    def test_gateway_rules(self):
        self.assertIn("gateway", self.ok(gateway="10.0.0.1")[1])
        self.assertIn("gateway", self.ok(gateway="192.168.1.50")[1])

    def test_camera_profile_refused(self):
        res = g.Net(runner=runner_for({})).set_ipv4("camera-link", {"method": "auto"})
        self.assertEqual(res["error"], "camera_profile")

    def test_set_manual_commands(self):
        run = runner_for({})
        res = g.Net(runner=run).set_ipv4("Home", {"method": "manual", "address": "192.168.1.50",
                                                  "prefix": 24, "gateway": "192.168.1.1"})
        self.assertTrue(res["ok"])
        mod = next(c for c in run.calls if c[:3] == ["nmcli", "connection", "modify"])
        self.assertIn("192.168.1.50/24", mod)
        self.assertIn(["nmcli", "connection", "up", "Home"], run.calls)


if __name__ == "__main__":
    unittest.main()

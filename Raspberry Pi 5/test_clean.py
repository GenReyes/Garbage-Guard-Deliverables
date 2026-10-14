import sys
import tempfile
import types
import unittest
from pathlib import Path

import gg_clean as c


class ModeTests(unittest.TestCase):
    def test_which_pictures_get_a_clean_copy(self):
        for tag in ("capture", "alert", "suppressed", "test"):
            self.assertTrue(c.wanted("all", tag))
            self.assertFalse(c.wanted("off", tag))
        self.assertTrue(c.wanted("manual", "capture"))
        for tag in ("alert", "suppressed", "test"):
            self.assertFalse(c.wanted("manual", tag))

    def test_clean_copy_sits_in_the_clean_folder_with_the_same_name(self):
        self.assertEqual(c.path_for("/x/snaps/capture_1.jpg"), Path("/x/snaps/clean/capture_1.jpg"))

    def test_configure_ignores_unknown_modes_and_saves(self):
        saved = []
        with tempfile.TemporaryDirectory() as d:
            k = c.Clean({}, d, save=lambda: saved.append(1))
            self.assertEqual(k.fields()["clean_mode"], "all")
            self.assertEqual(k.configure("manual")["clean_mode"], "manual")
            self.assertEqual(k.configure("everything")["clean_mode"], "manual")
            self.assertEqual(len(saved), 1)

    def test_stats_count_jpgs_and_are_cached_until_forgotten(self):
        t = [0.0]
        with tempfile.TemporaryDirectory() as d:
            k = c.Clean({}, d, mono=lambda: t[0])
            self.assertEqual(k.stats(), (0, 0))
            (Path(d) / "clean").mkdir()
            (Path(d) / "clean" / "a.jpg").write_bytes(b"x" * 100)
            (Path(d) / "clean" / "notes.txt").write_bytes(b"x")
            self.assertEqual(k.stats(), (0, 0))
            k.forget()
            self.assertEqual(k.stats(), (1, 100))
            (Path(d) / "clean" / "b.jpg").write_bytes(b"x" * 50)
            t[0] = c.CACHE_S + 1
            self.assertEqual(k.fields()["clean_count"], 2)

    def test_remove_and_lookup(self):
        with tempfile.TemporaryDirectory() as d:
            snap = Path(d) / "capture_1.jpg"
            self.assertIsNone(c.name_if_present(d, "capture_1.jpg"))
            (Path(d) / "clean").mkdir()
            c.path_for(snap).write_bytes(b"x")
            self.assertEqual(c.name_if_present(d, "capture_1.jpg"), "capture_1.jpg")
            self.assertIsNone(c.name_if_present(d, None))
            self.assertTrue(c.remove(snap))
            self.assertFalse(c.remove(snap))


def _live():
    try:
        import numpy  # noqa: F401
        import cv2  # noqa: F401
    except ImportError:
        return None
    if "ultralytics" not in sys.modules:
        try:
            import ultralytics  # noqa: F401
        except ImportError:
            sys.modules["ultralytics"] = types.SimpleNamespace(YOLO=None)
    import garbageguard_live
    return garbageguard_live


class EngineTests(unittest.TestCase):
    def setUp(self):
        self.g = _live()
        if self.g is None:
            self.skipTest("numpy and OpenCV are needed")
        import numpy as np
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.dir = Path(tmp.name)
        for name, value in (("SNAPSHOT_DIR", self.dir / "snaps"), ("DB_PATH", self.dir / "gg.db"),
                            ("SETTINGS_PATH", self.dir / "gg_settings.json")):
            old = getattr(self.g, name)
            setattr(self.g, name, value)
            self.addCleanup(setattr, self.g, name, old)
        self.g.SNAPSHOT_DIR.mkdir()
        self.g.init_database()
        self.e = self.g.Engine("0", "model", self.g.load_settings())
        self.e.raw_frame = np.full((120, 160, 3), 90, np.uint8)
        self.e.annotated = self.e.raw_frame.copy()
        self.e.annotated[10:30, 10:30] = (0, 0, 255)
        self.clean_dir = self.g.SNAPSHOT_DIR / "clean"

    def files(self):
        return sorted(p.name for p in self.clean_dir.glob("*.jpg")) if self.clean_dir.is_dir() else []

    def test_capture_saves_the_untouched_frame_with_the_same_name(self):
        import cv2
        self.assertTrue(self.e.capture_snapshot())
        rec = self.e.recent_alerts()[0]
        self.assertEqual(rec["clean"], rec["snapshot"])
        clean = cv2.imread(str(self.clean_dir / rec["clean"]))
        self.assertEqual(clean.shape, (120, 160, 3))
        self.assertLess(abs(int(clean[20, 20, 2]) - 90), 8)

    def test_modes(self):
        self.e.clean.configure("manual")
        self.e.fire_test_alert()
        self.assertIsNone(self.e.recent_alerts()[0]["clean"])
        self.e.capture_snapshot()
        self.assertEqual(len(self.files()), 1)
        self.e.clean.configure("off")
        self.e.capture_snapshot()
        self.assertEqual(len(self.files()), 1)
        self.e.clean.configure("all")
        self.e.fire_test_alert()
        self.assertEqual(len(self.files()), 2)
        self.assertEqual(self.e.clean.fields()["clean_count"], 2)

    def test_deleting_a_record_deletes_its_clean_copy(self):
        self.e.capture_snapshot()
        self.e.capture_snapshot()
        ids = [r["id"] for r in self.e.recent_alerts()]
        self.e.delete_alerts(ids=[ids[0]])
        self.assertEqual(len(self.files()), 1)
        self.e.delete_alerts(every=True)
        self.assertEqual(self.files(), [])
        self.assertEqual(self.e.clean.fields()["clean_count"], 0)


if __name__ == "__main__":
    unittest.main()

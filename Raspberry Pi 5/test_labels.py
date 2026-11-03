import tempfile
import unittest
from pathlib import Path

from test_model import _live


class BoxLabelText(unittest.TestCase):
    def setUp(self):
        self.g = _live()
        if self.g is None:
            self.skipTest("numpy and OpenCV are needed")

    def label(self, **on):
        return self.g.box_label(53, "food_container", 0.5, self.g.clean_labels(on))

    def test_default_is_confidence_only(self):
        self.assertEqual(self.g.box_label(53, "bag", 0.5, self.g.clean_labels(None)), "50%")

    def test_order_is_id_class_confidence(self):
        self.assertEqual(self.label(id=True, **{"class": True}, conf=True), "#53 food_container 50%")
        self.assertEqual(self.label(id=True, conf=False), "#53")
        self.assertEqual(self.label(**{"class": True}, conf=False), "food_container")

    def test_none_gives_outline_only(self):
        self.assertEqual(self.label(conf=False), "")

    def test_untracked_box_has_no_id(self):
        self.assertEqual(self.g.box_label(0, "bag", 0.44, self.g.clean_labels({"id": True})), "44%")


class EngineLabelsAndTotal(unittest.TestCase):
    def setUp(self):
        self.g = _live()
        if self.g is None:
            self.skipTest("numpy and OpenCV are needed")
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        d = Path(tmp.name)
        for name, value in (("SNAPSHOT_DIR", d / "snaps"), ("DB_PATH", d / "gg.db"),
                            ("SETTINGS_PATH", d / "gg_settings.json")):
            old = getattr(self.g, name)
            setattr(self.g, name, value)
            self.addCleanup(setattr, self.g, name, old)
        self.g.SNAPSHOT_DIR.mkdir()
        self.g.init_database()
        self.e = self.g.Engine("0", str(d / "model"), self.g.load_settings())

    def test_labels_saved_and_partial_update_keeps_the_rest(self):
        self.assertEqual(self.e.snapshot_state()["labels"], {"id": False, "class": False, "conf": True})
        self.assertEqual(self.e.set_labels({"id": True, "junk": 1}),
                         {"id": True, "class": False, "conf": True})
        self.assertEqual(self.e.set_labels({"conf": 0}), {"id": True, "class": False, "conf": False})
        self.assertEqual(self.g.load_settings()["labels"], {"id": True, "class": False, "conf": False})

    def test_total_counts_every_record(self):
        for _ in range(3):
            self.e.log_note("muted")
        self.assertEqual(self.e.alerts_total(), 3)
        self.assertEqual(len(self.e.recent_alerts(2)), 2)


if __name__ == "__main__":
    unittest.main()

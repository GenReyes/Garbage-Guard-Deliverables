import tempfile
import unittest
from pathlib import Path

import gg_model


def make(base, name, files=(".param", ".bin")):
    d = Path(base) / name
    d.mkdir()
    for ext in files:
        (d / ("model" + ext)).write_text("x")
    return d


class ListModels(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = self.tmp.name

    def tearDown(self):
        self.tmp.cleanup()

    def test_finds_complete_folders_in_version_order(self):
        make(self.base, "garbageguard_v8_best_ncnn_model")
        make(self.base, "garbageguard_v7_best_ncnn_model")
        make(self.base, "garbageguard_v10_best_ncnn_model")
        got = gg_model.list_models(self.base)
        self.assertEqual([m["label"] for m in got], ["v7", "v8", "v10"])
        self.assertEqual(got[0]["name"], "garbageguard_v7_best_ncnn_model")

    def test_skips_incomplete_and_unrelated(self):
        make(self.base, "garbageguard_v7_best_ncnn_model")
        make(self.base, "garbageguard_v9_best_ncnn_model", files=())
        make(self.base, "garbageguard_v6_best_ncnn_model", files=(".param",))
        make(self.base, "other_ncnn_model")
        (Path(self.base) / "garbageguard_v5_best_ncnn_model").write_text("a file")
        self.assertEqual(gg_model.names(self.base), ["garbageguard_v7_best_ncnn_model"])

    def test_label_without_best(self):
        make(self.base, "garbageguard_v6b_ncnn_model")
        self.assertEqual(gg_model.list_models(self.base)[0]["label"], "v6b")

    def test_missing_folder(self):
        self.assertEqual(gg_model.list_models(Path(self.base) / "nope"), [])


class Validation(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = self.tmp.name
        make(self.base, "garbageguard_v7_best_ncnn_model")
        make(self.base, "garbageguard_v8_best_ncnn_model")

    def tearDown(self):
        self.tmp.cleanup()

    def test_valid_names(self):
        self.assertTrue(gg_model.valid("garbageguard_v8_best_ncnn_model", self.base))

    def test_rejects_unknown_and_path_like(self):
        outside = Path(self.base).parent / "garbageguard_v9_best_ncnn_model"
        for bad in ("garbageguard_v9_best_ncnn_model", "../x", "..", "/etc/passwd",
                    "./garbageguard_v8_best_ncnn_model",
                    "garbageguard_v8_best_ncnn_model/../garbageguard_v7_best_ncnn_model",
                    str(Path(self.base) / "garbageguard_v8_best_ncnn_model"),
                    str(outside), "", None, 8, ["garbageguard_v8_best_ncnn_model"]):
            self.assertFalse(gg_model.valid(bad, self.base), bad)


class CleanSettings(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = self.tmp.name

    def tearDown(self):
        self.tmp.cleanup()

    def test_keeps_installed_choice(self):
        make(self.base, "garbageguard_v7_best_ncnn_model")
        make(self.base, "garbageguard_v8_best_ncnn_model")
        s = gg_model.clean_settings({"model": "garbageguard_v8_best_ncnn_model"}, self.base)
        self.assertEqual(s["model"], "garbageguard_v8_best_ncnn_model")

    def test_missing_choice_falls_back_to_default(self):
        make(self.base, "garbageguard_v7_best_ncnn_model")
        s = gg_model.clean_settings({"model": "garbageguard_v8_best_ncnn_model"}, self.base)
        self.assertEqual(s["model"], gg_model.DEFAULT)

    def test_path_like_choice_falls_back(self):
        make(self.base, "garbageguard_v7_best_ncnn_model")
        s = gg_model.clean_settings({"model": "../x"}, self.base)
        self.assertEqual(s["model"], gg_model.DEFAULT)

    def test_default_missing_uses_first_installed(self):
        make(self.base, "garbageguard_v8_best_ncnn_model")
        s = gg_model.clean_settings({"model": "nope"}, self.base)
        self.assertEqual(s["model"], "garbageguard_v8_best_ncnn_model")

    def test_nothing_installed_keeps_default(self):
        s = gg_model.clean_settings({}, self.base)
        self.assertEqual(s["model"], gg_model.DEFAULT)


class Classes(unittest.TestCase):
    def test_right_order(self):
        self.assertIsNone(gg_model.class_problem({0: "bottle", 1: "bag", 2: "food_container"}))

    def test_wrong_order_or_names(self):
        self.assertIn("instead of", gg_model.class_problem({0: "bag", 1: "bottle", 2: "food_container"}))
        self.assertIn("instead of", gg_model.class_problem({0: "person", 1: "car"}))
        self.assertIsNotNone(gg_model.class_problem(None))



class FakeYOLO:
    """Stands in for ultralytics.YOLO: folders named *broken* fail, *wrong* have other classes."""
    loaded = []

    def __init__(self, path, task=None):
        name = Path(str(path)).name
        if "broken" in name or not Path(str(path)).is_dir():
            raise RuntimeError(f"cannot open {name}")
        self.path = name
        self.names = ({0: "bag", 1: "bottle", 2: "food_container"} if "wrong" in name
                      else {0: "bottle", 1: "bag", 2: "food_container"})
        self.predictor = None
        FakeYOLO.loaded.append(name)

    def track(self, *a, **k):
        return []


def _live():
    import sys
    import types
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


class EngineSwitch(unittest.TestCase):
    V7, V8 = "garbageguard_v7_best_ncnn_model", "garbageguard_v8_best_ncnn_model"

    def setUp(self):
        self.g = _live()
        if self.g is None:
            self.skipTest("numpy and OpenCV are needed")
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.dir = Path(tmp.name)
        for name, value in (("BASE_DIR", self.dir), ("SNAPSHOT_DIR", self.dir / "snaps"),
                            ("DB_PATH", self.dir / "gg.db"),
                            ("SETTINGS_PATH", self.dir / "gg_settings.json"), ("YOLO", FakeYOLO)):
            old = getattr(self.g, name)
            setattr(self.g, name, value)
            self.addCleanup(setattr, self.g, name, old)
        make(self.dir, self.V7)
        make(self.dir, self.V8)
        self.g.SNAPSHOT_DIR.mkdir()
        self.g.init_database()
        self.settings = self.g.load_settings()
        self.e = self.g.Engine("0", str(self.dir / self.V7), self.settings)
        self.model = self.e._first_model()

    def saved(self):
        return self.g.load_settings()["model"]

    def test_default_is_v7(self):
        self.assertEqual(self.settings["model"], self.V7)
        self.assertEqual(self.e.snapshot_state()["model"], self.V7)

    def test_switch_to_v8_and_back(self):
        self.assertTrue(self.e.set_model(self.V8)["ok"])
        self.assertTrue(self.e.model_flag.is_set())
        self.assertTrue(self.e.snapshot_state()["model_loading"])
        m = self.e._swap_model(self.model)
        self.assertEqual(m.path, self.V8)
        st = self.e.snapshot_state()
        self.assertEqual((st["model"], st["model_note"], st["model_loading"]), (self.V8, "", False))
        self.assertEqual(self.saved(), self.V8)
        self.e.set_model(self.V7)
        self.assertEqual(self.e._swap_model(m).path, self.V7)
        self.assertEqual(self.saved(), self.V7)

    def test_unknown_or_path_like_name_is_refused(self):
        for bad in ("garbageguard_v9_best_ncnn_model", "../x", str(self.dir / self.V8)):
            res = self.e.set_model(bad)
            self.assertFalse(res["ok"])
            self.assertFalse(self.e.model_flag.is_set())
        self.assertEqual(self.saved(), self.V7)

    def test_folder_removed_after_choosing_keeps_old_model(self):
        self.e.set_model(self.V8)
        for f in (self.dir / self.V8).iterdir():
            f.unlink()
        m = self.e._swap_model(self.model)
        self.assertIs(m, self.model)
        st = self.e.snapshot_state()
        self.assertEqual(st["model"], self.V7)
        self.assertIn("still using v7", st["model_note"])
        self.assertEqual(self.saved(), self.V7)

    def test_broken_model_keeps_old_model(self):
        broken = "garbageguard_v9broken_best_ncnn_model"
        make(self.dir, broken)
        self.e._models = ([], 0.0)
        self.assertTrue(self.e.set_model(broken)["ok"])
        m = self.e._swap_model(self.model)
        self.assertIs(m, self.model)
        self.assertIn("Could not load v9broken, still using v7", self.e.model_note)
        self.assertEqual(self.saved(), self.V7)

    def test_wrong_classes_refused(self):
        wrong = "garbageguard_v9wrong_best_ncnn_model"
        make(self.dir, wrong)
        self.e.set_model(wrong)
        m = self.e._swap_model(self.model)
        self.assertIs(m, self.model)
        self.assertIn("instead of bottle, bag, food_container", self.e.model_note)

    def test_saved_v8_that_fails_at_start_falls_back_to_v7(self):
        bad = "garbageguard_v9broken_best_ncnn_model"
        make(self.dir, bad)
        e = self.g.Engine("0", str(self.dir / bad), self.g.load_settings())
        m = e._first_model()
        self.assertEqual(m.path, self.V7)
        self.assertEqual(e.model_name, self.V7)
        self.assertIn("using v7", e.model_note)


if __name__ == "__main__":
    unittest.main()

import io
import tempfile
import unittest
import zipfile
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
        make(self.base, "garbageguard_v8_best_ncnn_model")
        make(self.base, "garbageguard_v9_best_ncnn_model")
        s = gg_model.clean_settings({"model": "garbageguard_v9_best_ncnn_model"}, self.base)
        self.assertEqual(s["model"], "garbageguard_v9_best_ncnn_model")

    def test_missing_choice_falls_back_to_default(self):
        make(self.base, "garbageguard_v8_best_ncnn_model")
        s = gg_model.clean_settings({"model": "garbageguard_v9_best_ncnn_model"}, self.base)
        self.assertEqual(s["model"], gg_model.DEFAULT)

    def test_path_like_choice_falls_back(self):
        make(self.base, "garbageguard_v8_best_ncnn_model")
        s = gg_model.clean_settings({"model": "../x"}, self.base)
        self.assertEqual(s["model"], gg_model.DEFAULT)

    def test_default_missing_uses_first_installed(self):
        make(self.base, "garbageguard_v9_best_ncnn_model")
        s = gg_model.clean_settings({"model": "nope"}, self.base)
        self.assertEqual(s["model"], "garbageguard_v9_best_ncnn_model")

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



META = b"names:\n  0: bottle\n  1: bag\n  2: food_container\n"


def zipped(entries):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for name, data in entries.items():
            z.writestr(name, data)
    return buf.getvalue()


def export(prefix=""):
    return {prefix + "model.ncnn.param": b"p", prefix + "model.ncnn.bin": b"b" * 10,
            prefix + "metadata.yaml": META, prefix + "model_ncnn.py": b"print(1)"}


class Upload(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def send(self, entries_or_bytes, version="v12", **kw):
        data = entries_or_bytes if isinstance(entries_or_bytes, bytes) else zipped(entries_or_bytes)
        return gg_model.install(io.BytesIO(data), kw.pop("length", len(data)), version, self.base, **kw)

    def refused(self, code, *a, **kw):
        with self.assertRaises(gg_model.UploadError) as cm:
            self.send(*a, **kw)
        self.assertEqual(cm.exception.code, code, cm.exception.message)
        work = self.base / gg_model.UPLOAD_DIR
        self.assertEqual(list(work.iterdir()) if work.exists() else [], [])
        return cm.exception

    def test_files_at_zip_root(self):
        name = self.send(export())
        self.assertEqual(name, "garbageguard_v12_best_ncnn_model")
        kept = sorted(p.name for p in (self.base / name).iterdir())
        self.assertEqual(kept, ["metadata.yaml", "model.ncnn.bin", "model.ncnn.param"])
        self.assertEqual((self.base / name / "metadata.yaml").read_bytes(), META)
        self.assertEqual(gg_model.names(self.base), [name])
        self.assertEqual(list((self.base / gg_model.UPLOAD_DIR).iterdir()), [])

    def test_zipped_export_folder_and_other_file_names(self):
        entries = {"best_ncnn_model/": b"", "best_ncnn_model/best.ncnn.param": b"p",
                   "best_ncnn_model/best.ncnn.bin": b"b", "best_ncnn_model/metadata.yaml": META,
                   "best_ncnn_model/model_ncnn.py": b"x", "__MACOSX/best_ncnn_model/._best.ncnn.bin": b"junk"}
        name = self.send(entries, version="12b")
        self.assertEqual(name, "garbageguard_v12b_best_ncnn_model")
        self.assertEqual(sorted(p.name for p in (self.base / name).iterdir()),
                         ["metadata.yaml", "model.ncnn.bin", "model.ncnn.param"])

    def test_missing_metadata(self):
        entries = export()
        del entries["metadata.yaml"]
        self.assertIn("metadata.yaml", self.refused("no_metadata", entries).message)
        self.assertFalse((self.base / "garbageguard_v12_best_ncnn_model").exists())

    def test_two_bin_files(self):
        entries = export()
        entries["other.bin"] = b"b"
        self.assertIn("exactly one .bin", self.refused("not_a_model", entries).message)

    def test_files_in_two_folders(self):
        entries = export("a/")
        entries["b/metadata.yaml"] = META
        self.refused("not_one_model", entries)

    def test_zip_slip_entries_are_skipped(self):
        entries = export()
        entries["../evil.param"] = b"evil"
        entries["/abs/evil.bin"] = b"evil"
        entries["x/../../evil.yaml"] = b"evil"
        name = self.send(entries)
        self.assertEqual(sorted(p.name for p in (self.base / name).iterdir()),
                         ["metadata.yaml", "model.ncnn.bin", "model.ncnn.param"])
        self.assertFalse((self.base.parent / "evil.param").exists())
        self.refused("not_a_model", {"../model.ncnn.param": b"p", "../model.ncnn.bin": b"b",
                                     "metadata.yaml": META}, version="v13")

    def test_unpacked_size_cap(self):
        entries = export()
        entries["model.ncnn.bin"] = b"b" * 5000
        self.refused("too_large", entries, max_bytes=1000)

    def test_not_a_zip_and_cut_short(self):
        self.refused("bad_zip", b"this is not a zip")
        data = zipped(export())
        self.refused("cut_short", data, length=len(data) + 100)

    def test_damaged_file_inside_the_zip(self):
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", zipfile.ZIP_STORED) as z:
            for name, data in export().items():
                z.writestr(name, data)
        data = bytearray(buf.getvalue())
        at = data.index(b"b" * 10)
        data[at] = ord("X")
        self.refused("bad_zip", bytes(data))

    def test_version_names(self):
        for raw, want in ((" v12 ", "v12"), ("12", "v12"), ("2025.11-a_b", "v2025.11-a_b"), ("best", "best")):
            self.assertEqual(gg_model.version_name(raw), want)
        for bad in ("", "  ", "v 12", "a/b", "..", ".", "x" * 25, "v12?", None):
            with self.assertRaises(gg_model.UploadError, msg=repr(bad)):
                gg_model.version_name(bad)
        self.refused("bad_version", export(), version="../v12")

    def test_duplicate_version_is_refused(self):
        make(self.base, "garbageguard_v12_best_ncnn_model")
        e = self.refused("exists", export(), version="12")
        self.assertEqual(e.status, 409)
        self.assertEqual(sorted(p.name for p in (self.base / "garbageguard_v12_best_ncnn_model").iterdir()),
                         ["model.bin", "model.param"])

    def test_stale_work_folders_are_cleaned(self):
        (self.base / gg_model.UPLOAD_DIR / "abc").mkdir(parents=True)
        gg_model.clean_uploads(self.base)
        self.assertFalse((self.base / gg_model.UPLOAD_DIR).exists())

    def test_remove_only_touches_model_folders(self):
        keep = self.base / "notes"
        keep.mkdir()
        self.assertFalse(gg_model.remove("notes", self.base))
        self.assertFalse(gg_model.remove("../garbageguard_v1_best_ncnn_model", self.base))
        self.assertTrue(keep.exists())
        make(self.base, "garbageguard_v9_best_ncnn_model")
        self.assertTrue(gg_model.remove("garbageguard_v9_best_ncnn_model", self.base))


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
    DEF, OTHER = "garbageguard_v8_best_ncnn_model", "garbageguard_v9_best_ncnn_model"

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
        make(self.dir, self.DEF)
        make(self.dir, self.OTHER)
        self.g.SNAPSHOT_DIR.mkdir()
        self.g.init_database()
        self.settings = self.g.load_settings()
        self.e = self.g.Engine("0", str(self.dir / self.DEF), self.settings)
        self.model = self.e._first_model()

    def saved(self):
        return self.g.load_settings()["model"]

    def test_default_is_v8(self):
        self.assertEqual(self.settings["model"], self.DEF)
        self.assertEqual(self.e.snapshot_state()["model"], self.DEF)

    def test_switch_to_v9_and_back(self):
        self.assertTrue(self.e.set_model(self.OTHER)["ok"])
        self.assertTrue(self.e.model_flag.is_set())
        self.assertTrue(self.e.snapshot_state()["model_loading"])
        m = self.e._swap_model(self.model)
        self.assertEqual(m.path, self.OTHER)
        st = self.e.snapshot_state()
        self.assertEqual((st["model"], st["model_note"], st["model_loading"]), (self.OTHER, "", False))
        self.assertEqual(self.saved(), self.OTHER)
        self.e.set_model(self.DEF)
        self.assertEqual(self.e._swap_model(m).path, self.DEF)
        self.assertEqual(self.saved(), self.DEF)

    def test_unknown_or_path_like_name_is_refused(self):
        for bad in ("garbageguard_v10_best_ncnn_model", "../x", str(self.dir / self.OTHER)):
            res = self.e.set_model(bad)
            self.assertFalse(res["ok"])
            self.assertFalse(self.e.model_flag.is_set())
        self.assertEqual(self.saved(), self.DEF)

    def test_folder_removed_after_choosing_keeps_old_model(self):
        self.e.set_model(self.OTHER)
        for f in (self.dir / self.OTHER).iterdir():
            f.unlink()
        m = self.e._swap_model(self.model)
        self.assertIs(m, self.model)
        st = self.e.snapshot_state()
        self.assertEqual(st["model"], self.DEF)
        self.assertIn("still using v8", st["model_note"])
        self.assertEqual(self.saved(), self.DEF)

    def test_broken_model_keeps_old_model(self):
        broken = "garbageguard_v10broken_best_ncnn_model"
        make(self.dir, broken)
        self.e._models = ([], 0.0)
        self.assertTrue(self.e.set_model(broken)["ok"])
        m = self.e._swap_model(self.model)
        self.assertIs(m, self.model)
        self.assertIn("Could not load v10broken, still using v8", self.e.model_note)
        self.assertEqual(self.saved(), self.DEF)

    def test_wrong_classes_refused(self):
        wrong = "garbageguard_v10wrong_best_ncnn_model"
        make(self.dir, wrong)
        self.e.set_model(wrong)
        m = self.e._swap_model(self.model)
        self.assertIs(m, self.model)
        self.assertIn("instead of bottle, bag, food_container", self.e.model_note)

    def upload(self, version, entries=None):
        data = zipped(entries or export())
        return self.e.upload_model(version, io.BytesIO(data), len(data))

    def test_upload_switches_and_remembers_the_previous_model(self):
        code, res = self.upload("v12")
        self.assertEqual((code, res["ok"], res["pending"]), (200, True, "garbageguard_v12_best_ncnn_model"))
        st = self.e.snapshot_state()
        self.assertTrue(st["model_loading"])
        self.assertIsNone(st["model_prev"])
        m = self.e._swap_model(self.model)
        self.assertEqual(m.path, "garbageguard_v12_best_ncnn_model")
        st = self.e.snapshot_state()
        self.assertEqual((st["model_label"], st["model_prev"], st["model_prev_label"]),
                         ("v12", self.DEF, "v8"))
        self.assertEqual(self.g.load_settings()["model_prev"], self.DEF)
        self.e.set_model(st["model_prev"])
        self.e._swap_model(m)
        st = self.e.snapshot_state()
        self.assertEqual((st["model"], st["model_prev_label"]), (self.DEF, "v12"))
        self.assertTrue((self.dir / "garbageguard_v12_best_ncnn_model").is_dir())

    def test_failed_swap_of_an_upload_deletes_it(self):
        code, res = self.upload("v12broken")
        self.assertEqual(code, 200)
        m = self.e._swap_model(self.model)
        self.assertIs(m, self.model)
        self.assertFalse((self.dir / "garbageguard_v12broken_best_ncnn_model").exists())
        st = self.e.snapshot_state()
        self.assertIn("Could not load v12broken, still using v8", st["model_note"])
        self.assertIn("The uploaded v12broken was removed.", st["model_note"])
        self.assertNotIn("garbageguard_v12broken_best_ncnn_model", [x["name"] for x in st["models"]])
        self.assertEqual(self.saved(), self.DEF)

    def test_upload_with_wrong_classes_is_deleted_too(self):
        self.upload("v12wrong")
        self.e._swap_model(self.model)
        self.assertFalse((self.dir / "garbageguard_v12wrong_best_ncnn_model").exists())
        self.assertIn("instead of bottle, bag, food_container", self.e.model_note)

    def test_broken_model_already_on_disk_is_kept(self):
        broken = "garbageguard_v10broken_best_ncnn_model"
        make(self.dir, broken)
        self.e._models = ([], 0.0)
        self.e.set_model(broken)
        self.e._swap_model(self.model)
        self.assertTrue((self.dir / broken).is_dir())

    def test_refused_upload_changes_nothing(self):
        code, res = self.upload("v9")
        self.assertEqual((code, res["error"]), (409, "exists"))
        code, res = self.upload("bad name")
        self.assertEqual((code, res["error"]), (400, "bad_version"))
        self.assertFalse(self.e.model_flag.is_set())
        self.assertEqual(self.saved(), self.DEF)

    def test_previous_model_hidden_when_gone(self):
        self.e.set_model(self.OTHER)
        self.e._swap_model(self.model)
        self.assertEqual(self.e.snapshot_state()["model_prev"], self.DEF)
        for f in (self.dir / self.DEF).iterdir():
            f.unlink()
        self.e._models = ([], 0.0)
        st = self.e.snapshot_state()
        self.assertEqual((st["model_prev"], st["model_prev_label"]), (None, None))

    def test_saved_model_that_fails_at_start_falls_back_to_v8(self):
        bad = "garbageguard_v10broken_best_ncnn_model"
        make(self.dir, bad)
        e = self.g.Engine("0", str(self.dir / bad), self.g.load_settings())
        m = e._first_model()
        self.assertEqual(m.path, self.DEF)
        self.assertEqual(e.model_name, self.DEF)
        self.assertIn("using v8", e.model_note)


if __name__ == "__main__":
    unittest.main()

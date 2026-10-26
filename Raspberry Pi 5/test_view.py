import unittest

import numpy as np

import gg_view


class StreamUrls(unittest.TestCase):
    def test_sub_to_main(self):
        url = "rtsp://admin:pw@192.168.1.64:554/Streaming/Channels/102"
        main, sub = gg_view.stream_urls(url)
        self.assertEqual(main, "rtsp://admin:pw@192.168.1.64:554/Streaming/Channels/101")
        self.assertEqual(sub, url)

    def test_main_given(self):
        main, sub = gg_view.stream_urls("rtsp://a@h/Streaming/Channels/101")
        self.assertTrue(main.endswith("/101"))
        self.assertTrue(sub.endswith("/102"))

    def test_second_camera(self):
        main, sub = gg_view.stream_urls("rtsp://a@h/Streaming/Channels/202")
        self.assertTrue(main.endswith("/201"))

    def test_other_sources(self):
        for src in ("0", "creek.mp4", "rtsp://cam/live"):
            self.assertEqual(gg_view.stream_urls(src), (src, src))


class Choice(unittest.TestCase):
    URL = "rtsp://a@h/Streaming/Channels/102"

    def test_main_by_default(self):
        c = gg_view.StreamChoice(self.URL)
        self.assertEqual(c.active, "main")
        self.assertTrue(c.url.endswith("/101"))

    def test_falls_back_after_two_empty_tries(self):
        c = gg_view.StreamChoice(self.URL)
        self.assertFalse(c.main_gave_nothing())
        self.assertEqual(c.active, "main")
        self.assertTrue(c.main_gave_nothing())
        self.assertEqual(c.active, "sub")
        self.assertIn("sub-stream", c.note)
        self.assertFalse(c.main_gave_nothing())

    def test_want_resets(self):
        c = gg_view.StreamChoice(self.URL)
        c.main_gave_nothing()
        c.main_gave_nothing()
        c.want("main")
        self.assertEqual(c.active, "main")
        self.assertEqual(c.note, "")

    def test_sub_wanted(self):
        c = gg_view.StreamChoice(self.URL, "sub")
        self.assertEqual(c.active, "sub")
        self.assertFalse(c.main_gave_nothing())

    def test_file_source(self):
        c = gg_view.StreamChoice("creek.mp4")
        self.assertEqual(c.url, "creek.mp4")


class CropBox(unittest.TestCase):
    def test_no_roi(self):
        self.assertIsNone(gg_view.crop_box(None, 1920, 1080))
        self.assertIsNone(gg_view.crop_box([(0, 0), (1, 1)], 1920, 1080))

    def test_padded_box(self):
        poly = [(800, 500), (1200, 500), (1200, 800), (800, 800)]
        x1, y1, x2, y2 = gg_view.crop_box(poly, 1920, 1080)
        self.assertLess(x1, 800)
        self.assertGreater(x2, 1200)
        self.assertLess(y1, 500)
        self.assertGreater(y2, 800)

    def test_inside_frame(self):
        poly = [(0, 900), (300, 900), (300, 1080), (0, 1080)]
        x1, y1, x2, y2 = gg_view.crop_box(poly, 1920, 1080)
        self.assertGreaterEqual(x1, 0)
        self.assertGreaterEqual(y1, 0)
        self.assertLessEqual(x2, 1920)
        self.assertLessEqual(y2, 1080)

    def test_small_roi_grows_to_minimum(self):
        poly = [(1900, 1070), (1910, 1070), (1910, 1075)]
        x1, y1, x2, y2 = gg_view.crop_box(poly, 1920, 1080)
        self.assertGreaterEqual(x2 - x1, gg_view.MIN_CROP - 1)
        self.assertGreaterEqual(y2 - y1, gg_view.MIN_CROP - 1)
        self.assertLessEqual(x2, 1920)
        self.assertLessEqual(y2, 1080)

    def test_whole_frame_roi_means_no_crop(self):
        poly = [(0, 0), (1920, 0), (1920, 1080), (0, 1080)]
        self.assertIsNone(gg_view.crop_box(poly, 1920, 1080))


class Display(unittest.TestCase):
    def test_scales_down(self):
        frame = np.zeros((1080, 1920, 3), np.uint8)
        out, f = gg_view.for_display(frame)
        self.assertEqual(out.shape[1], gg_view.DISPLAY_W)
        self.assertEqual(out.shape[0], 540)
        self.assertAlmostEqual(f, 0.5)

    def test_small_frame_unchanged(self):
        frame = np.zeros((360, 640, 3), np.uint8)
        out, f = gg_view.for_display(frame)
        self.assertEqual(out.shape, frame.shape)
        self.assertEqual(f, 1.0)

    def test_box_helpers(self):
        self.assertEqual(gg_view.shift((1, 2, 3, 4), 10, 20), (11, 22, 13, 24))
        self.assertEqual(gg_view.scale_box((10, 20, 30, 40), 0.5), (5, 10, 15, 20))


class Settings(unittest.TestCase):
    def test_clean(self):
        s = gg_view.clean_settings({"stream": "odd", "detect_size": 1000, "crop": 0})
        self.assertEqual(s, {"stream": "main", "detect_size": 960, "crop": False})
        s = gg_view.clean_settings({"stream": "sub", "detect_size": "bad"})
        self.assertEqual(s["detect_size"], 960)
        self.assertTrue(s["crop"])


if __name__ == "__main__":
    unittest.main()

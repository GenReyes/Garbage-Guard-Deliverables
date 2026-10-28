"""Which detection model the Pi runs.

Models are NCNN export folders next to this file, named
garbageguard_<version>_best_ncnn_model (for example garbageguard_v8_best_ncnn_model),
each holding a .param and a .bin file. The browser only ever sends a folder name
from list_models(), never a path, so nothing outside this folder can be loaded.
"""

import re
from pathlib import Path

DEFAULT = "garbageguard_v7_best_ncnn_model"
DEFAULTS = {"model": DEFAULT}
CLASSES = ("bottle", "bag", "food_container")

_NAME = re.compile(r"^garbageguard_(.+?)(?:_best)?_ncnn_model$")


def label(name):
    m = _NAME.match(str(name))
    return m.group(1) if m else str(name)


def _order(label_text):
    m = re.match(r"^v(\d+)(.*)$", label_text)
    return (0, int(m.group(1)), m.group(2)) if m else (1, 0, label_text)


def _complete(folder):
    try:
        files = [p.suffix for p in folder.iterdir() if p.is_file()]
    except OSError:
        return False
    return ".param" in files and ".bin" in files


def list_models(base_dir):
    """Installed model folders, oldest version first: [{"name", "label"}]."""
    out = []
    try:
        entries = list(Path(base_dir).iterdir())
    except OSError:
        return out
    for p in entries:
        if p.is_dir() and _NAME.match(p.name) and _complete(p):
            out.append({"name": p.name, "label": label(p.name)})
    out.sort(key=lambda m: _order(m["label"]))
    return out


def names(base_dir):
    return [m["name"] for m in list_models(base_dir)]


def valid(name, base_dir):
    return isinstance(name, str) and name in names(base_dir)


def clean_settings(s, base_dir):
    have = names(base_dir)
    if s.get("model") not in have:
        s["model"] = DEFAULT if DEFAULT in have or not have else have[0]
    return s


def class_problem(model_names):
    """None when the model's classes are bottle, bag, food_container in that order."""
    try:
        got = [model_names[i] for i in sorted(model_names)] if isinstance(model_names, dict) \
            else list(model_names)
    except (TypeError, KeyError):
        return "its class list could not be read"
    if tuple(got) != CLASSES:
        return "its classes are " + ", ".join(map(str, got)) + \
               " instead of bottle, bag, food_container"
    return None

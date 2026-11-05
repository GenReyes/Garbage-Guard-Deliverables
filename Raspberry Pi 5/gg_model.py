"""Which detection model the Pi runs.

Models are NCNN export folders next to this file, named
garbageguard_<version>_best_ncnn_model (for example garbageguard_v8_best_ncnn_model),
each holding a .param and a .bin file. The browser only ever sends a folder name
from list_models(), never a path, so nothing outside this folder can be loaded.

New models arrive as a .zip of an Ultralytics NCNN export (POST /api/model/upload).
install() streams the zip to a work folder under .model_upload/, keeps only the
.param, the .bin and metadata.yaml, and moves the result into place under a
folder name built from the version the user typed.
"""

import os
import re
import secrets
import shutil
import zipfile
import zlib
from pathlib import Path, PurePosixPath

DEFAULT = "garbageguard_v8_best_ncnn_model"
DEFAULTS = {"model": DEFAULT, "model_prev": None}
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


UPLOAD_DIR = ".model_upload"
MAX_UPLOAD = 64 * 1024 * 1024
MAX_UNPACKED = 200 * 1024 * 1024
CHUNK = 1024 * 1024
_VERSION = re.compile(r"^[A-Za-z0-9._-]{1,24}$")


class UploadError(Exception):
    """A refused upload: `status` is the HTTP code, `code` a short word, the text is for people."""

    def __init__(self, status, code, message):
        super().__init__(message)
        self.status, self.code, self.message = status, code, message


def folder_for(version):
    return f"garbageguard_{version}_best_ncnn_model"


def version_name(raw):
    """The version label from what the user typed: v12, v12b, 2025-11 becomes v2025-11."""
    text = str(raw or "").strip()
    if not _VERSION.match(text) or text in (".", ".."):
        raise UploadError(400, "bad_version",
                          "The version name may use letters, digits, dot, dash and underscore, "
                          "up to 24 characters, for example v12.")
    return "v" + text if text[0].isdigit() else text


def save_stream(stream, length, work):
    """Copies exactly `length` bytes from `stream` into work/upload.zip, a chunk at a time."""
    path = Path(work) / "upload.zip"
    left = length
    with open(path, "wb") as f:
        while left > 0:
            chunk = stream.read(min(CHUNK, left))
            if not chunk:
                raise UploadError(400, "cut_short", "The upload stopped before the whole file arrived.")
            f.write(chunk)
            left -= len(chunk)
    return path


def _safe_parts(name):
    """Path parts of a zip entry, or None for absolute paths, drive letters and '..' parts."""
    name = name.replace("\\", "/")
    if name.startswith("/") or re.match(r"^[A-Za-z]:", name):
        return None
    parts = [p for p in PurePosixPath(name).parts if p not in ("", ".")]
    if not parts or ".." in parts:
        return None
    return parts


def _kind(filename):
    if filename == "metadata.yaml":
        return "meta"
    if filename.endswith(".param"):
        return "param"
    if filename.endswith(".bin"):
        return "bin"
    return None


def unpack(zip_path, dest, max_bytes=MAX_UNPACKED):
    """Writes model.ncnn.param, model.ncnn.bin and metadata.yaml from the zip into dest.

    The files may sit at the root of the zip or inside one folder (the zipped
    *_ncnn_model export folder). Everything else in the zip is ignored, so
    model_ncnn.py and similar files are never kept.
    """
    try:
        zf = zipfile.ZipFile(zip_path)
    except (zipfile.BadZipFile, OSError):
        raise UploadError(400, "bad_zip", "That file is not a readable .zip.")
    with zf:
        found = {"param": [], "bin": [], "meta": []}
        folders = set()
        for info in zf.infolist():
            if info.is_dir():
                continue
            parts = _safe_parts(info.filename)
            if parts is None or len(parts) > 2 or parts[0] == "__MACOSX":
                continue
            kind = _kind(parts[-1])
            if kind is None:
                continue
            found[kind].append(info)
            folders.add(parts[0] if len(parts) == 2 else "")
        if len(folders) > 1:
            raise UploadError(400, "not_one_model",
                              "The zip holds model files in more than one place. Zip one *_ncnn_model folder.")
        if not found["param"] and not found["bin"]:
            raise UploadError(400, "not_a_model",
                              "The zip has no .param and .bin files. Zip the *_ncnn_model folder from yolo export.")
        for kind, what in (("param", ".param"), ("bin", ".bin")):
            if len(found[kind]) != 1:
                raise UploadError(400, "not_a_model",
                                  f"The zip must hold exactly one {what} file, it holds {len(found[kind])}.")
        if len(found["meta"]) != 1:
            raise UploadError(400, "no_metadata",
                              "The zip has no metadata.yaml. It holds the class names, so the Pi needs it.")
        picked = (("model.ncnn.param", found["param"][0]), ("model.ncnn.bin", found["bin"][0]),
                  ("metadata.yaml", found["meta"][0]))
        if sum(info.file_size for _, info in picked) > max_bytes:
            raise UploadError(413, "too_large", f"The model files are over {max_bytes // (1024 * 1024)} MB unpacked.")
        Path(dest).mkdir(parents=True)
        total = 0
        for out_name, info in picked:
            try:
                with zf.open(info) as src, open(Path(dest) / out_name, "wb") as out:
                    while True:
                        chunk = src.read(CHUNK)
                        if not chunk:
                            break
                        total += len(chunk)
                        if total > max_bytes:
                            raise UploadError(413, "too_large",
                                              f"The model files are over {max_bytes // (1024 * 1024)} MB unpacked.")
                        out.write(chunk)
            except (zipfile.BadZipFile, zlib.error, EOFError, NotImplementedError, RuntimeError) as e:
                raise UploadError(400, "bad_zip", f"{info.filename} in the zip could not be read ({e}).")
    return Path(dest)


def install(stream, length, version, base_dir, max_bytes=MAX_UNPACKED):
    """Receives a zipped model and puts it next to the others. Returns the new folder name."""
    label_text = version_name(version)
    name = folder_for(label_text)
    base = Path(base_dir)
    if (base / name).exists():
        raise UploadError(409, "exists", f"{label_text} is already on the Pi. Pick another version name.")
    work = base / UPLOAD_DIR / secrets.token_hex(8)
    work.mkdir(parents=True)
    try:
        zip_path = save_stream(stream, length, work)
        out = unpack(zip_path, work / "model", max_bytes)
        if (base / name).exists():
            raise UploadError(409, "exists", f"{label_text} is already on the Pi. Pick another version name.")
        os.replace(out, base / name)
    finally:
        shutil.rmtree(work, ignore_errors=True)
    return name


def remove(name, base_dir):
    """Deletes a model folder, only ever one named like a model and directly in base_dir."""
    if not _NAME.match(str(name)) or Path(str(name)).name != name:
        return False
    target = Path(base_dir) / name
    if not target.is_dir():
        return False
    shutil.rmtree(target, ignore_errors=True)
    return not target.exists()


def clean_uploads(base_dir):
    """Removes work folders left by an upload that was cut off by a restart."""
    shutil.rmtree(Path(base_dir) / UPLOAD_DIR, ignore_errors=True)

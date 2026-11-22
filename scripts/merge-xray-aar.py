import argparse
import io
import zipfile
from pathlib import Path

from android_elf import verify_elf

ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64", "x86")


def canonical_zip(files, output):
    with zipfile.ZipFile(output, "w") as archive:
        for name in sorted(files):
            entry = zipfile.ZipInfo(name)
            entry.create_system = 3
            entry.external_attr = (0o40755 if name.endswith("/") else 0o100644) << 16
            archive.writestr(entry, files[name], compress_type=zipfile.ZIP_STORED)


def normalized_jar(data):
    buf = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        canonical_zip({n: jar.read(n) for n in jar.namelist()}, buf)
    return buf.getvalue()


def merge(inputs, output):
    common = None
    libraries = {}
    for abi, path in zip(ABIS, inputs, strict=True):
        with zipfile.ZipFile(path) as aar:
            name = f"jni/{abi}/libgojni.so"
            entries = aar.namelist()
            if len(entries) != len(set(entries)) or [n for n in entries if n.startswith("jni/")] != [name]:
                raise ValueError(f"Unexpected JNI contents for {abi}")
            libraries[name] = aar.read(name)
            verify_elf(libraries[name], abi)
            files = {n: aar.read(n) for n in entries if n != name}
            files["classes.jar"] = normalized_jar(files["classes.jar"])
            if common is not None and common != files:
                raise ValueError(f"Non-native contents differ for {abi}")
            common = files
    canonical_zip(common | libraries, output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    parser.add_argument("inputs", nargs=4, type=Path)
    args = parser.parse_args()
    merge(args.inputs, args.output)

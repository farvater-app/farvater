#!/usr/bin/env python3
import argparse
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
FILES = ("app/libs/libv2ray.aar",) + tuple(
    f"app/src/main/jniLibs/{abi}/libhev-socks5-tunnel.so" for abi in ABIS
)


def pinned(root):
    return json.loads((root / "scripts/native-versions.json").read_text())


def verify_aar(root=ROOT):
    archive = root / "app/libs/libv2ray.aar"
    pin = pinned(root)
    with archive.open("rb") as stream:
        actual = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual != pin["aarSha256"]:
        raise ValueError("Xray AAR does not match the pinned release")
    module = f"dep\tgithub.com/xtls/xray-core\t{pin['xrayModuleVersion']}\t".encode()
    with zipfile.ZipFile(archive) as aar:
        names = aar.namelist()
        for abi in ABIS:
            name = f"jni/{abi}/libgojni.so"
            if names.count(name) != 1:
                raise ValueError(f"Xray AAR must contain exactly one library for {abi}")
            library = aar.read(name)
            if not library.startswith(b"\x7fELF") or module not in library:
                raise ValueError(f"Xray core revision does not match the pin for {abi}")


def verify(root=ROOT, record=False):
    verify_aar(root)
    manifest = root / "app/libs/natives-sha256.json"
    actual = {}
    for name in FILES:
        digest = hashlib.sha256()
        with (root / name).open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        actual[name] = digest.hexdigest()
    if record:
        manifest.write_text(json.dumps(actual, sort_keys=True, indent=2) + "\n")
    else:
        if manifest.stat().st_size > 4096:
            raise ValueError("Native digest manifest is too large")
        expected = json.loads(manifest.read_text())
        if expected != actual:
            raise ValueError("Native file digests do not match the build manifest")


def verify_hev_source(directory, root=ROOT):
    pin = pinned(root)
    def git(*args):
        return subprocess.check_output(["git", "-C", str(directory), *args], text=True).strip()
    if git("rev-parse", "HEAD") != pin["hevCommit"]:
        raise ValueError("HEV source revision does not match the pin")
    modules = {}
    for line in git("submodule", "status", "--recursive").splitlines():
        revision, path, *_ = line.split()
        modules[path] = revision
    if modules != pin["hevSubmodules"]:
        raise ValueError("HEV submodule revisions do not match the pins")
    if git("status", "--porcelain", "--untracked-files=all", "--ignore-submodules=none"):
        raise ValueError("HEV source contains local changes before applying the project patch")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--record", action="store_true")
    parser.add_argument("--aar-only", action="store_true")
    parser.add_argument("--xray-version", action="store_true")
    parser.add_argument("--hev-version", action="store_true")
    parser.add_argument("--verify-hev-source", type=Path)
    args = parser.parse_args()
    if args.xray_version:
        print(pinned(ROOT)["xrayLibrary"])
    elif args.hev_version:
        print(pinned(ROOT)["hevVersion"])
    elif args.verify_hev_source:
        verify_hev_source(args.verify_hev_source)
    elif args.aar_only:
        verify_aar()
    else:
        verify(record=args.record)
        print("Native digests verified")

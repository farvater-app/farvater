#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
FILES = ("app/libs/libv2ray.aar",) + tuple(
    f"app/src/main/jniLibs/{abi}/libhev-socks5-tunnel.so" for abi in ABIS
)


def pinned(root):
    return json.loads((root / "scripts/native-versions.json").read_text())


def verify_aar(root=ROOT):
    with (root / "app/libs/libv2ray.aar").open("rb") as stream:
        actual = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual != pinned(root)["aarSha256"]:
        raise ValueError("Xray AAR does not match the pinned release")


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


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--record", action="store_true")
    parser.add_argument("--aar-only", action="store_true")
    parser.add_argument("--xray-version", action="store_true")
    args = parser.parse_args()
    if args.xray_version:
        print(pinned(ROOT)["xrayLibrary"])
    elif args.aar_only:
        verify_aar()
    else:
        verify(record=args.record)
        print("Native digests verified")

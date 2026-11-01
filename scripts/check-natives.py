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


def verify(root=ROOT, record=False):
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
    args = parser.parse_args()
    verify(record=args.record)
    print("Native digests verified")

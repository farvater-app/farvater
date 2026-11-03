#!/usr/bin/env python3
import argparse
import hashlib
import io
import json
import subprocess
import urllib.request
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument("output", type=Path)
args = parser.parse_args()
pin = json.loads((root / "scripts/native-versions.json").read_text())
url = f"https://github.com/XTLS/Xray-core/releases/download/v{pin['xrayCore']}/Xray-linux-64.zip"
with urllib.request.urlopen(url, timeout=60) as response:
    data = response.read()
if hashlib.sha256(data).hexdigest() != pin["testCoreSha256"]:
    raise ValueError("Test core does not match the pinned release")
args.output.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(io.BytesIO(data)) as archive:
    binary = args.output / "xray"
    binary.write_bytes(archive.read("xray"))
binary.chmod(0o700)
version = subprocess.check_output([str(binary), "version"], text=True)
if not version.startswith(f"Xray {pin['xrayCore']} "):
    raise ValueError("Unexpected test core version")
print(version)

import argparse
import hashlib
import pathlib
import re
import subprocess
import tempfile
import zipfile


def dex_classes(dump):
    classes = {}
    for block in re.split(r"(?m)^Class #\d+", dump)[1:]:
        descriptor = re.search(r"Class descriptor\s*:\s*'([^']+)'", block)
        if descriptor:
            classes[descriptor.group(1)] = re.findall(
                r"name\s*:\s*'([^']+)'\s+type\s*:\s*'([^']+)'\s+access\s*:\s*[^\n]*\b(PUBLIC)\b", block)
    return classes


def check(apk, build_tools):
    classes = {}
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as temp:
        names = archive.namelist()
        for abi in ("arm64-v8a", "armeabi-v7a", "x86_64", "x86"):
            for library in ("libgojni.so", "libhev-socks5-tunnel.so"):
                if f"lib/{abi}/{library}" not in names:
                    raise ValueError(f"Missing native library: {abi}/{library}")
        dex_files = [name for name in names if re.fullmatch(r"classes\d*\.dex", name)]
        if not dex_files:
            raise ValueError("APK has no DEX files")
        dex_hashes = {name: hashlib.sha256(archive.read(name)).digest() for name in dex_files}
        for abi in ("arm64-v8a", "armeabi-v7a", "x86_64", "x86"):
            split = apk.with_name(apk.name.replace("universal", abi))
            if split == apk:
                raise ValueError("Pass the universal APK to check all five APKs")
            with zipfile.ZipFile(split) as variant:
                split_dex = {name: hashlib.sha256(variant.read(name)).digest() for name in variant.namelist()
                             if re.fullmatch(r"classes\d*\.dex", name)}
                if split_dex != dex_hashes:
                    raise ValueError(f"Different R8 bytecode in {split.name}")
                libraries = {name for name in variant.namelist() if name.startswith("lib/") and name.endswith(".so")}
                expected = {name for name in names if name.startswith(f"lib/{abi}/") and name.endswith(".so")}
                if libraries != expected:
                    raise ValueError(f"Wrong native libraries in {split.name}")
                for name in expected:
                    if variant.read(name) != archive.read(name):
                        raise ValueError(f"Native library mismatch in {split.name}: {name}")
        for name in dex_files:
            path = pathlib.Path(temp) / name
            path.write_bytes(archive.read(name))
            dump = subprocess.check_output([str(build_tools / "dexdump"), str(path)], text=True)
            classes.update(dex_classes(dump))
    contracts = {
        "androidx/work/impl/WorkDatabase_Impl": [("<init>", "()V")],
        "app/farvater/data/UpdateWorker": [("<init>", "(Landroid/content/Context;Landroidx/work/WorkerParameters;)V")],
        "hev/sockstun/TProxyService": [("TProxyStartService", "(Ljava/lang/String;I)Z"),
            ("TProxyStopService", "()Z"), ("TProxyGetStats", "()[J")],
    }
    for component in ("App", "FarvaterActivity", "vpn/ToggleActivity", "vpn/FarvaterVpnService",
                      "vpn/QsTileService", "vpn/NodeCheckService", "vpn/VpnWidget", "vpn/BootReceiver", "data/ApkProvider"):
        contracts["app/farvater/" + component] = [("<init>", "()V")]
    for name, methods in contracts.items():
        present = classes.get(f"L{name};", [])
        for method, signature in methods:
            if (method, signature, "PUBLIC") not in present:
                raise ValueError(f"R8 contract missing: {name}.{method}{signature} public")
    for name, methods in {
        "libv2ray/Libv2ray": ("newCoreController", "measureOutboundDelay", "checkVersionX", "initCoreEnv"),
        "libv2ray/CoreController": ("startLoop", "stopLoop"),
    }.items():
        present = {method for method, _, _ in classes.get(f"L{name};", [])}
        if not set(methods) <= present:
            raise ValueError(f"R8 reflection contract missing: {name}: {set(methods) - present}")
    if "Llibv2ray/CoreCallbackHandler;" not in classes:
        raise ValueError("R8 removed CoreCallbackHandler")
    print(f"Release contracts verified: {apk.name}, {len(contracts)} constructors/JNI classes, Xray reflection, four ABIs")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=pathlib.Path)
    parser.add_argument("build_tools", type=pathlib.Path)
    args = parser.parse_args()
    check(args.apk, args.build_tools)

#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ABIS="arm64-v8a armeabi-v7a x86_64 x86"
XRAY_LIB_VERSION="$(python3 "$ROOT/scripts/check-natives.py" --xray-version)"
PINNED_HEV_VERSION="$(python3 "$ROOT/scripts/check-natives.py" --hev-version)"
HEV_VERSION="${HEV_VERSION:-$PINNED_HEV_VERSION}"

echo "Ядро Xray ($XRAY_LIB_VERSION)"
mkdir -p "$ROOT/app/libs"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
"$ROOT/scripts/build-xray.sh" "$WORK/libv2ray.aar"
EXPECTED=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["aarSha256"])' "$ROOT/scripts/native-versions.json")
printf '%s  %s\n' "$EXPECTED" "$WORK/libv2ray.aar" | sha256sum -c -
mv "$WORK/libv2ray.aar" "$ROOT/app/libs/libv2ray.aar"
python3 "$ROOT/scripts/check-natives.py" --aar-only

echo "TUN-мост (hev-socks5-tunnel $HEV_VERSION)"
git clone --depth 1 --branch "$HEV_VERSION" --recursive --shallow-submodules \
  https://github.com/heiher/hev-socks5-tunnel "$WORK/jni"
if [ "$HEV_VERSION" = "$PINNED_HEV_VERSION" ]; then
    python3 "$ROOT/scripts/check-natives.py" --verify-hev-source "$WORK/jni"
else
    echo "Custom HEV_VERSION requires independent source and compatibility verification" >&2
fi
patch --fuzz=0 -p1 -d "$WORK/jni" < "$ROOT/scripts/hev-udp-bind.patch"
patch --fuzz=0 -p1 -d "$WORK/jni" < "$ROOT/scripts/hev-jni-session.patch"
if [[ "${FARVATER_TEST_NATIVE:-0}" == 1 ]]; then
    "$ROOT/native/hev/test/run.sh" "$WORK/jni"
fi
(
  cd "$WORK"
  "$ANDROID_NDK_HOME/ndk-build" \
    APP_ABI="$ABIS" \
    APP_PLATFORM=android-26 \
    APP_CFLAGS="-O3 -DPKGNAME=hev/sockstun -DCLSNAME=TProxyService" \
    APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true
)
mkdir -p "$ROOT/app/src/main/jniLibs"
for abi in $ABIS; do
    mkdir -p "$ROOT/app/src/main/jniLibs/$abi"
    cp "$WORK/libs/$abi/libhev-socks5-tunnel.so" "$ROOT/app/src/main/jniLibs/$abi/"
done
find "$ROOT/app/src/main/jniLibs" -name '*.so'
python3 "$ROOT/scripts/check-natives.py" --record
echo "Готово"

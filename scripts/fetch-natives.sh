#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ABIS="arm64-v8a armeabi-v7a x86_64 x86"
XRAY_LIB_VERSION="${XRAY_LIB_VERSION:-v26.10.21}"
HEV_VERSION="${HEV_VERSION:-2.18.0}"

echo "Ядро Xray ($XRAY_LIB_VERSION)"
mkdir -p "$ROOT/app/libs"
gh release download "$XRAY_LIB_VERSION" -R 2dust/AndroidLibXrayLite -p 'libv2ray.aar' -D "$ROOT/app/libs" --clobber

echo "TUN-мост (hev-socks5-tunnel $HEV_VERSION)"
WORK="$(mktemp -d)"
git clone --depth 1 --branch "$HEV_VERSION" --recursive --shallow-submodules \
  https://github.com/heiher/hev-socks5-tunnel "$WORK/jni"
patch -p1 -d "$WORK/jni" < "$ROOT/scripts/hev-udp-bind.patch"
(
  cd "$WORK"
  "$ANDROID_NDK_HOME/ndk-build" \
    APP_ABI="$ABIS" \
    APP_PLATFORM=android-26 \
    APP_CFLAGS="-O3 -DPKGNAME=hev/sockstun -DCLSNAME=TProxyService" \
    APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true
)
mkdir -p "$ROOT/app/src/main/jniLibs"
cp -r "$WORK/libs/"* "$ROOT/app/src/main/jniLibs/"
find "$ROOT/app/src/main/jniLibs" -name '*.so'
echo "Готово"

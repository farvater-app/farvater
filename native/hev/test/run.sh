#!/usr/bin/env bash
set -euo pipefail
src=$(realpath "$1")/src
testdir=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$testdir/../../.." && pwd)
jdk=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
javac -d "$tmp" "$repo/app/src/main/java/hev/sockstun/TProxyService.java" "$testdir/BridgeTest.java"
cc -pthread -shared -fPIC -DANDROID -DPKGNAME=hev/sockstun -I"$jdk/include" -I"$jdk/include/linux" \
    "$src/hev-jni.c" "$testdir/worker.c" -o "$tmp/libbridge.so"
timeout 20s java --add-opens java.base/java.io=ALL-UNNAMED -cp "$tmp" BridgeTest "$tmp/libbridge.so"

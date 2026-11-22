#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
output=$(realpath -m "${1:?Specify the output AAR path}")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
pin() { python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$root/scripts/native-versions.json" "$1"; }
commit=$(pin xrayLibraryCommit)
version=$(pin xrayLibrary)
export GOTOOLCHAIN="$(pin goToolchain)" GOFLAGS="-buildvcs=false" GOBIN="$work/bin"
export PATH="$GOBIN:${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
javac -version 2>&1 | grep -Eq '^javac 17([.]|$)'
test "$(go env GOVERSION)" = "$GOTOOLCHAIN"
test -x "${ANDROID_NDK_HOME:?}"/ndk-build
grep -Eq '^Pkg.Revision *= *27\.2\.12479018$' "$ANDROID_NDK_HOME/source.properties"
platform="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?}}/platforms/android-36/android.jar"
test -s "$platform"
git -C "$work" init -q
git -C "$work" fetch -q --depth 1 https://github.com/2dust/AndroidLibXrayLite.git "$commit"
git -C "$work" checkout -q --detach FETCH_HEAD
test "$(git -C "$work" rev-parse HEAD)" = "$commit"
curl --proto '=https' --proto-redir '=https' -fsSL --retry 3 "https://github.com/2dust/AndroidLibXrayLite/releases/download/$version/libv2ray.aar" -o "$work/upstream.aar"
printf '%s  %s\n' "$(pin upstreamAarSha256)" "$work/upstream.aar" | sha256sum -c -
cd "$work"
mkdir -p assets
for asset in geoip.dat geosite.dat geoip-only-cn-private.dat; do unzip -p upstream.aar "assets/$asset" >"assets/$asset"; done
cp "$root/native/xray/runtime_policy.go" "$root/native/xray/mobile_log.go" .
patch --fuzz=0 -p1 <"$root/native/xray/console-output.patch"
mobile=$(pin goMobileVersion)
test "$(go list -mod=readonly -m -f '{{.Version}}' golang.org/x/mobile)" = "$mobile"
test "$(go list -mod=readonly -m -f '{{.Version}}' github.com/xtls/xray-core)" = "$(pin xrayModuleVersion)"
go mod download
go mod verify
mobile_src=$(go list -mod=readonly -m -f '{{.Dir}}' golang.org/x/mobile)
cp -R "$mobile_src" "$work/mobile-tools"
chmod -R u+w "$work/mobile-tools"
(
    cd "$work/mobile-tools"
    patch --fuzz=0 -p1 <"$root/native/xray/gomobile-module-path.patch"
    go install ./cmd/gomobile ./cmd/gobind
)
export GOCACHE="$work/go-build"
if [[ "${FARVATER_TEST_NATIVE:-0}" == 1 ]]; then
    mkdir "$work/host-tests"
    cp "$work"/go.mod "$work"/go.sum "$work"/*.go "$root"/native/xray/*_test.go "$work/host-tests/"
    (cd "$work/host-tests" && go test -mod=readonly -tags=xrayintegration \
        -run 'TestMobile(CoreCycles|RuntimePolicy|LogBoundsAndRedactsBursts)$' -v .)
    go clean -cache
fi
for abi in arm64 arm amd64 386; do
    gomobile bind -target="android/$abi" -androidapi 26 -bootclasspath "$platform" -trimpath \
        -ldflags='-s -w -buildid= -checklinkname=0 -extldflags=-Wl,-z,max-page-size=16384' -o "$work/$abi.aar" .
    go clean -cache
done
python3 "$root/scripts/merge-xray-aar.py" "$work/libv2ray.aar" "$work"/{arm64,arm,amd64,386}.aar
mkdir -p "$(dirname "$output")"
sha256sum "$work/libv2ray.aar"
mv "$work/libv2ray.aar" "$output"

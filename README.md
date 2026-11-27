# Farvater

Farvater is an open-source Android VPN client that uses Xray-core to connect to a server you choose.

## What Farvater provides

Farvater runs on your device. It does not sell VPN access, operate a VPN
service, or provide its own server infrastructure. To connect, you need a
profile for your own server or a subscription from a provider.

## Features

- Connect with VLESS, VMess, Trojan, Shadowsocks, or Hysteria2.
- Import subscription URLs, individual node links, and supported Xray JSON configurations.
- Test nodes individually or by subscription. Use Gals to select a node by latency or download speed.
- Split traffic by app or destination. Send matching destinations through the VPN, connect directly, or block them.
- Control the connection from a Quick Settings tile, a widget, Tasker, or MacroDroid.

## Installation

Download an APK from [GitHub Releases](https://github.com/farvater-app/farvater/releases/latest).
Farvater requires Android 8.0 (API 26) or later. Root access is unnecessary.

| APK variant | Device |
| --- | --- |
| `arm64-v8a` | Most current ARM phones and tablets with 64-bit Android |
| `armeabi-v7a` | Devices running 32-bit ARM Android |
| `x86_64` or `x86` | Intel-based devices or emulators with the corresponding ABI |
| `universal` | Any supported ABI, if you are unsure which to choose |

### Verify the APK

Download `SHA256SUMS.txt` from the same release. In a directory containing
only that file and the APK you selected, run this command with GNU Coreutils:

```sh
sha256sum --check --ignore-missing SHA256SUMS.txt
```

Check that the output names your APK and says `OK`. This compares the file
with the published release. To verify the APK signature and identify its
signer, use `apksigner` from Android SDK Build Tools. For example, for the
ARM64 APK from [v1.0.33](https://github.com/farvater-app/farvater/releases/tag/v1.0.33):

```sh
apksigner verify --print-certs Farvater-1.0.33-arm64-v8a.apk
```

Verification must succeed. Compare `Signer #1 certificate SHA-256 digest`
with the project's signing certificate fingerprint, ignoring case and colons:

```text
D8:80:FC:FE:51:D4:A3:06:A5:0F:B8:67:40:83:9D:74:75:37:53:D4:88:06:6B:27:E1:54:00:B5:42:BC:68:0F
```

Open the verified APK on Android. If prompted, allow the app opening the
file to install it. You can revoke that permission after installation.

## Quick start

1. Open Farvater and review the first-run documents. Choose **Only my subscriptions**
   to use your own provider and leave the public catalog disabled.
2. In **Sources**, choose **Add subscription** for a subscription URL or
   **Paste from clipboard** for a node link. You can also scan a QR code or open a QR image.
3. Open **Nodes** and select a server. On the home screen, tap **Connect**.
4. Approve the Android VPN request. Once connected, open the site or app
   you intend to use. Tap **Disconnect** on the home screen to end the session.

For backups, startup behavior, and automation, see the [user guide](docs/user-guide.md).

## Subscriptions and nodes

A subscription URL retrieves a list of nodes. Refresh it when the provider
changes a server or its credentials. A node imported separately remains a
manual entry. The optional public catalog links to third-party subscriptions;
Farvater does not control their contents or availability.

Use an HTTPS node test to check whether a request reaches the test site
through the node. A TCP test only establishes whether the server port accepts
a connection. To search across candidates, choose **Find a working node**.
Gals can check access to a particular service before ranking candidates.

[Subscription formats, refresh intervals, and node selection](docs/subscriptions.md)

## Routing

Farvater uses Android `VpnService` to receive traffic from participating apps.
An app you explicitly exclude bypasses the tunnel and its destination rules.

For traffic in the tunnel, add rules for domains, IP addresses, or CIDR
subnets. Choose VPN, direct, or block. The first matching user rule wins,
so place a narrow exception above a broader rule.

With automatic routing enabled, local networks use a direct connection by
default. Destinations on the built-in Russian services list also go directly
when that option is enabled.

[Rule examples and Android always-on VPN](docs/routing.md)

## Security and limitations

A server operator can observe connection metadata and read unencrypted
traffic leaving the server. HTTPS protects the contents of the connection
to the destination. A VPN does not guarantee anonymity: signing in to a
site still identifies your account to that site.

The public catalog's security filter checks for known configuration problems.
A passing check says nothing about an operator's logging practices.
Network-mode detection makes direct requests. Update checks can also fall
back to a direct connection.

[Security settings and trust boundaries](docs/security.md) ·
[Connection troubleshooting](docs/troubleshooting.md)

## Privacy

The official app has no registration, advertising, analytics, or automatic
crash reporting. Settings and saved profiles stay on the device. Exported
backups are plaintext and can contain subscription URLs and node credentials.

By default, personal subscription requests include a random installation ID
(HWID), the device model, and the Android version. You can disable this in
subscription settings. Public catalog sources do not receive these headers.

The [privacy policy](legal/privacy.txt) describes what subscription
providers, DNS resolvers, test sites, and update hosts receive. The policy
and [terms of use](legal/terms.txt) are in Russian.

## Building from source

Use a Linux environment with JDK 17, Android SDK Platform 36, Android NDK
`27.2.12479018`, and the Go `1.27.3` toolchain. The native build scripts use
Bash, Git, Python 3, curl, unzip, patch, and GNU Coreutils.

Set `ANDROID_SDK_ROOT` to your installed SDK, then run:

```sh
git clone https://github.com/farvater-app/farvater.git
cd farvater
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/27.2.12479018"
./scripts/fetch-natives.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug APKs are written to `app/build/outputs/apk/debug/`. The debug signing
key cannot update an installed official APK. Native component versions and
checksums are in [native-versions.json](scripts/native-versions.json).
The [CI workflow](.github/workflows/build.yml) includes the host Xray compatibility test setup.

## License

Farvater is licensed under [GPL-3.0](LICENSE).
[Third-party notices](legal/licenses.txt) cover bundled libraries and assets.

## Reporting vulnerabilities

Email [injexor@proton.me](mailto:injexor@proton.me) privately. Include the
Farvater and Android versions, the affected device, the impact, and steps to
reproduce. Remove credentials and private subscription URLs from examples.
The [security policy](SECURITY.md) lists supported versions and the disclosure process.

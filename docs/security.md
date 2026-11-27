# Security settings and trust boundaries

## Profiles and operators

A public catalog entry identifies a subscription publisher. It does not establish who runs each node or what they log. A private subscription is also a trust decision: credentials, additional outbounds, and probe destinations can all come from the imported profile.

The secure-node filter, enabled by default for public sources, screens for known problems such as unencrypted configurations and requests to skip certificate verification without a pin. Its treatment of public sources does not extend to every manually added profile. Farvater does not pass an imported `allowInsecure` flag through as permission to ignore an invalid TLS certificate. If a provider uses certificate pinning, obtain the expected fingerprint from the provider through a trusted channel.

## Which connections leave the device

| Operation | Who can receive a connection |
| --- | --- |
| VPN session | The node operator sees your connecting IP and can observe destinations and timing. End-to-end encryption such as HTTPS protects application contents in transit to the destination. |
| Subscription refresh | The provider receives a request through the selected node when the VPN is active, or directly otherwise. Personal subscriptions can also receive HWID headers. |
| Network-mode detection | Probe hosts receive direct requests and can see the device's ordinary external IP. Gals also checks the network before searching. |
| Node or service test | Candidate nodes and test destinations receive the test traffic. A success measures reachability at that time. |
| Version check | Update hosts can receive a direct request if the check through the node fails. |
| DNS resolution | The selected resolver receives queries sent to it. App exclusions and independent resolvers affect coverage. |

## Local data and exports

Settings and saved connection data use encryption backed by Android Keystore. Test results and traffic totals use app-private storage. Android automatic backup is disabled. A manually exported backup is a plaintext file: protect it like the provider credentials it contains.

Personal subscription requests can send an installation-specific random ID, phone model, and Android version. The ID is independent of the IMEI. Disabling HWID sharing stops future transmission of these headers. It cannot revoke information the provider has received.

## Updates and the tunnel

Before offering an in-app APK for installation, Farvater checks the file hash, package, version, and signing identity against the installed app. Android performs the installation. The [installation guide](../README.md#verify-the-apk) gives the release fingerprint for checking a fresh download.

Android's TUN interface feeds a native bridge and an authenticated local SOCKS listener used by Xray-core. The listener binds to loopback. For traffic outside the tunnel and Android's lockdown setting, see [routing](routing.md).

Consult the [privacy policy](../legal/privacy.txt) for the full data inventory and [SECURITY.md](../SECURITY.md) to report a vulnerability privately.

# Troubleshooting

## Android refuses to install or update the APK

For the checksum command, use GNU Coreutils in a directory containing just the APK and `SHA256SUMS.txt` from its release. Use `apksigner` from Android SDK Build Tools and compare the `Signer #1 certificate SHA-256 digest` only after verification succeeds.

Check that the phone runs Android 8.0 or later and that the APK matches the Android ABI. Try the universal APK if you are unsure. Follow the [APK verification procedure](../README.md#установка). A debug build or a build from another signer cannot update an official installation in place. Before removing an installation, [export a backup](user-guide.md#back-up-or-restore) if you need its data.

## A subscription is empty or shows fewer nodes than expected

Refresh just that source and read its status. Check whether the URL has expired with the provider. Clear the node search and favorites filter. Check the source's network mode, duplicate suppression, and the option to hide unresponsive nodes. Public lists also have a secure-node filter. If the data uses an unsupported format, ask for a compatible link.

## A node has a latency result, but a service does not load

Switch from TCP to an HTTPS test through the node, then check the affected service. If the test succeeds, check whether the app is excluded, whether a destination rule matches it, and whether the DNS setting is appropriate. Try a second trusted node. For problems affecting only some apps or transfers, test a smaller tunnel MTU.

## A certificate check fails

Check the phone's date and time. Confirm the hostname and TLS settings with the provider. If the profile uses a pin, compare it with the provider's verified fingerprint. Copying the value from the error itself would defeat the purpose of pinning.

## The active server changed

Check whether a Gals search is still running, whether failover is enabled, and whether separate nodes are saved for different network modes. Changing the selected node while the VPN is running also reconnects the tunnel.

## VPN stops when the phone locks

Check background activity and battery restrictions for Farvater in Android. Some vendors add a separate autostart control. Turning the screen off pauses ordinary active-node checks. It does not request that the VPN disconnect. Also check for a Wi-Fi/mobile network change or loss of contact with the node.

## Report a reproducible problem

For an ordinary bug, give the Farvater version, Android version, device model, steps, expected result, and observed result. For a UI problem, include the language, style, and font scale. Remove subscription URLs, credentials, HWID, and private addresses from logs or screenshots. See [CONTRIBUTING.md](../CONTRIBUTING.md) for the issue process and [SECURITY.md](../SECURITY.md) for private reports.

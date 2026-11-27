# Subscriptions and node selection

## Choose an input

| Input | How Farvater uses it |
| --- | --- |
| HTTP or HTTPS subscription URL | Fetches the provider's current list. Prefer HTTPS, especially if the URL includes an access token. |
| Node link | Adds a manual node. Supported schemes are `vless://`, `vmess://`, `trojan://`, `ss://`, `hysteria2://`, and `hy2://`. |
| Plain text or Base64 subscription | Parses the supported node links in the list. Base64 is an encoding and does not conceal credentials. |
| Xray JSON | Imports supported configurations or arrays, including supported outbound chains and balancers. Other clients' JSON formats may use a different schema. |
| QR code | Scan with the camera or choose an image. Review the node or subscription before confirming. |
| Import deep link | A website can offer a subscription with `farvater://import?url=` followed by the percent-encoded URL. The user confirms the import. |

## Refresh a source

Open its entry under **Sources** to refresh it or set an interval. Automatic scheduling uses the provider's supported refresh interval, or six hours if none is supplied. You can also choose a fixed interval or manual updates. Android may delay scheduled work.

Usage, quota, and expiry fields come from the provider when supplied. If a field is absent, ask the provider for the account details. A manual node is independent of subscription refreshes.

The public catalog starts disabled. Enabling it lets you choose lists published by third parties. Each source can be toggled separately. A subscription can be assigned to either network mode or both. A source excluded by the current mode does not participate in the normal candidate search.

## Test nodes

A manual HTTPS check sends a request to the selected test site through the node. Choose TCP when you only want to test the server port. Test one node from its actions or use the test control next to a subscription to test its nodes together. Reduce parallelism if a large batch saturates your network.

## Configure Gals

Use **Find a working node** on the home screen. In the Gals settings, choose the subscriptions and transports to include, select latency or download speed for ranking, and optionally select a service or custom HTTPS target. Exclude individual nodes from automatic selection when you want to keep them for manual use.

Periodic searches for a better node and switching after a failure are separate settings. Failover needs a reachable alternative. Searching and throughput checks transfer real data over the current network.

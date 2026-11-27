# Routing

## Start with the app

Android passes participating apps' traffic to Farvater's VPN interface. Use **Apps outside VPN** for an app that should bypass the VPN entirely. Xray cannot apply a destination rule to traffic that Android never passes to it.

## Then match the destination

In **Routing**, enter a domain, an IPv4 or IPv6 address, or a CIDR subnet. Domain entries match the named domain and its subdomains. Enter the hostname without a URL scheme, path, or port.

User rules are ordered. For example, these two rules send a single host through the VPN and let the rest of its subnet go directly:

| Order | Destination | Action |
| --- | --- | --- |
| 1 | `192.0.2.44` | VPN |
| 2 | `192.0.2.0/24` | Direct |

These are documentation addresses. Use addresses from your actual routing policy. Reversing the two rules would send the host directly. A block rule rejects matching connections.

## Automatic routes

User destination rules take precedence over the built-in local-network and Russian-service destination rules. Automatic routing sends configured local networks directly. The Russian services option also sends its built-in destinations directly.

There is a separate shortcut for known Russian apps. It applies when automatic routing and the Russian services option are enabled and the user rule list is empty. Adding a user rule brings these apps back under destination routing. Apps explicitly excluded by the user still bypass it.

## When connectivity must stop with the VPN

In Android's VPN settings, select Farvater, enable **Always-on VPN**, then **Block connections without VPN**. Android enforces this setting. Explicitly excluded apps may lose connectivity. While the tunnel is connected, direct destination rules still apply inside Farvater. Test the configuration with the apps you depend on.

Encrypted DNS applies to supported queries handled by the tunnel. An excluded app or an app with its own resolver can follow another DNS path.

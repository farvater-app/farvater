# Using Farvater on Android

For installation and the first connection, start with the [README](../README.md#быстрый-старт).

The first-run screen links to the [terms of use](../legal/terms.txt) and [privacy policy](../legal/privacy.txt). Choose **Only my subscriptions** to start with your own profiles.

## Change the active node

Open **Nodes** and tap a server. If the VPN is running, selecting a different server reconnects the tunnel. To keep a frequently used node easy to find, tap its star. The favorites filter and the search field apply together.

The Quick Settings tile and the home screen widget control the connection without reopening the app. Grant the Android VPN permission from the app before using these shortcuts.

## Start after reboot

Enable **Auto-connect** to reuse the last selected node at startup. After an app update, Farvater can restore a VPN that was previously running. Android's **Always-on VPN** is a separate system setting. If the service stops when the screen is locked, check the phone's background activity restrictions.

## Use Tasker or MacroDroid

Connect manually once, then create an automation key in Farvater's connection settings. Configure a broadcast in the automation app using the package, receiver class, action, and string extra shown by Farvater. Use the connect or disconnect action according to the macro.

A macro containing the key can control this installation. Revoke the key if you share a macro by mistake. Generating a replacement invalidates the previous one.

## Back up or restore

1. Open **Settings → Backup → Save to file**. Choose a private location. The export contains subscription URLs and node keys in plaintext.
2. To restore, disconnect the VPN and wait for running subscription updates and node checks to finish.
3. In **Backup**, choose the file and review what will be replaced. Security-sensitive changes, including app exclusions and HWID sharing, require separate consent. The selected node is cleared.
4. Refresh subscriptions after restoring and select the node you want.

**Share app** sends the installed APK through Android's share sheet. To move your own settings and subscriptions, use the backup procedure. Sharing a node link gives the recipient that node's connection details.

## Read the traffic history

The chart shows daily traffic volume recorded for the VPN, with 7-day and 30-day views. Apps outside the tunnel are outside those totals. Service checks can consume traffic even if no browser is open.

## Change appearance or language

Use **Settings → Appearance** to change the style, theme, or launcher icon. A style change leaves connection settings in place. The interface supports Russian, English, and Persian. On Android 13 and later it uses the system's per-app language setting. [Screenshots](screens/) show the screens in several styles.

package app.farvater.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import app.farvater.App

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = App.prefs
        if (!prefs.settings.autoStart) return
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> Unit
            Intent.ACTION_MY_PACKAGE_REPLACED -> if (!prefs.vpnWasOn) return
            else -> return
        }
        if (VpnBus.state.value is VpnState.Connected || VpnBus.state.value is VpnState.Connecting) return
        val node = prefs.lastNode ?: return
        if (VpnService.prepare(context) != null) return
        runCatching { FarvaterVpnService.start(context, node, checkMode = true) }
    }
}

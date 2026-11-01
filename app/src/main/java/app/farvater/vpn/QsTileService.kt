package app.farvater.vpn

import app.farvater.data.localizedString
import app.farvater.data.AppLanguages
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.farvater.App
import app.farvater.FarvaterActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class QsTileService : TileService() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguages.wrap(base))
    }

    private var job: Job? = null

    override fun onStartListening() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            combine(VpnBus.state, VpnBus.request) { state, _ -> state }.collect { render(it) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
    }

    private fun render(state: VpnState) {
        val tile = qsTile ?: return
        val requested = VpnBus.request.value
        tile.state = if (isActive(state)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = android.graphics.drawable.Icon.createWithResource(this, app.farvater.data.AppIcons.current(this).monochrome)
        tile.label = localizedString(app.farvater.R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                requested?.active == true -> localizedString(app.farvater.R.string.settings_connection)
                requested?.active == false -> localizedString(app.farvater.R.string.vpn_state_off)
                state is VpnState.Connected -> state.node.name
                state is VpnState.Connecting -> localizedString(app.farvater.R.string.settings_connection)
                else -> localizedString(app.farvater.R.string.vpn_state_off)
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        if (isActive(VpnBus.state.value)) {
            FarvaterVpnService.stop(this)
        } else {
            val node = App.prefs.lastNode
            if (node != null && VpnService.prepare(this) == null) {
                FarvaterVpnService.start(this, node)
            } else {
                if (node == null) {
                    android.widget.Toast.makeText(this, localizedString(app.farvater.R.string.choose_node_first), android.widget.Toast.LENGTH_LONG).show()
                }
                openApp()
            }
        }
        render(VpnBus.state.value)
    }

    private fun isActive(state: VpnState): Boolean =
        VpnBus.request.value?.active ?: (state is VpnState.Connected || state is VpnState.Connecting)

    private fun openApp() {
        val intent = Intent(this, FarvaterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

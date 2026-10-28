package app.farvater

import android.app.Application
import app.farvater.core.model.NetModeChoice
import app.farvater.data.Prefs
import app.farvater.data.SubscriptionRepository
import app.farvater.data.UpdateRepository
import app.farvater.ui.MainViewModel
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.flow.MutableStateFlow

internal fun prepareRuntime(app: Application) {
    app.getSharedPreferences("farvater", 0).edit().clear().commit()
    app.getSharedPreferences("farvater_health", 0).edit().clear().commit()
    val prefs = Prefs(app)
    prefs.settings = prefs.settings.copy(netMode = NetModeChoice.BLACK, autoUpdates = false, modeNodes = false)
    mapOf("prefs" to prefs, "repo" to SubscriptionRepository(app), "updates" to UpdateRepository(app)).forEach { (name, value) ->
        App::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, value)
    }
    MainViewModel::class.java.getDeclaredField("cachedState").apply { isAccessible = true }.set(null, null)
    for (name in listOf("cachedSnapshots", "cachedResults")) {
        @Suppress("UNCHECKED_CAST")
        val flow = MainViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }.get(null) as MutableStateFlow<Any>
        flow.value = emptyMap<String, Any>()
    }
    VpnBus.state.value = VpnState.Idle
    VpnBus.failedChecks.value = 0
}

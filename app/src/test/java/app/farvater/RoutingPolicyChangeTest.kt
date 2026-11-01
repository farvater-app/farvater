package app.farvater

import android.app.Application
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.AppSettings
import app.farvater.core.parser.LinkParser
import app.farvater.ui.MainViewModel
import app.farvater.vpn.FarvaterVpnService
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVpnService
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoutingPolicyChangeTest {
    private val changes: List<(AppSettings) -> AppSettings> = listOf(
        { it.copy(bypassApps = it.bypassApps + "example.bypass") },
        { it.copy(directRuServices = !it.directRuServices) },
        { it.copy(encryptedDns = !it.encryptedDns) },
        { it.copy(mtu = if (it.mtu == 1280) 1400 else 1280) },
    )

    @Test fun everyRoutingChangeRestartsConnectingAndConnectedTunnel() {
        for (connecting in listOf(true, false)) for (change in changes) {
            exercise(connecting, change, stopBeforeRestart = false)
        }
    }

    @Test fun stopBeforeDelayedRestartDoesNotStartTunnelAgain() {
        for (change in changes) exercise(true, change, stopBeforeRestart = true)
    }

    @Test fun pendingStopWinsBeforeConnectingOrConnectedStateBecomesIdle() {
        for (connecting in listOf(true, false)) for (change in changes) {
            exercise(connecting, change, stopBeforeRestart = false, pendingStop = true)
        }
    }

    private fun exercise(connecting: Boolean, change: (AppSettings) -> AppSettings,
        stopBeforeRestart: Boolean, pendingStop: Boolean = false) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        prepareRuntime(app)
        ShadowVpnService.setPrepareResult(null)
        val vm = MainViewModel(app)
        try {
            val node = LinkParser.parse("vless://test@policy.example.test:443?security=tls")!!
            VpnBus.state.value = if (connecting) VpnState.Connecting(node) else VpnState.Connected(node, 1)
            vm.updateSettings(change)
            if (stopBeforeRestart) VpnBus.state.value = VpnState.Idle
            if (pendingStop) {
                FarvaterVpnService.stop(app)
                assertEquals(FarvaterVpnService.ACTION_STOP, shadowOf(app).nextStartedService?.action)
                assertEquals(false, VpnBus.request.value?.active)
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            val intent = shadowOf(app).nextStartedService
            if (stopBeforeRestart || pendingStop) assertNull(intent) else {
                assertNotNull(intent)
                assertEquals(FarvaterVpnService.ACTION_START, intent!!.action)
                assertTrue(intent.getBooleanExtra(FarvaterVpnService.EXTRA_RESTART, false))
            }
        } finally {
            vm.viewModelScope.cancel()
            VpnBus.state.value = VpnState.Idle
        }
    }
}

package app.farvater

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.model.NetMode
import app.farvater.core.model.NodeProfile
import app.farvater.core.model.ProxyNode
import app.farvater.core.parser.LinkParser
import app.farvater.data.GalsGroup
import app.farvater.data.GalsPolicy
import app.farvater.data.ServiceKind
import app.farvater.data.SourceSnapshot
import app.farvater.engine.ProbeNetwork
import app.farvater.engine.AutomaticContext
import app.farvater.engine.AutomaticReason
import app.farvater.vpn.VpnBus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AutomaticContextTest {
    private val node = LinkParser.parse("vless://test@candidate.example.test:443?security=tls", ProxyNode.MANUAL_SOURCE)!!
    private val group = GalsGroup("selected", "Selected")

    @Before fun setup() {
        prepareRuntime(ApplicationProvider.getApplicationContext<Application>())
        App.prefs.settings = App.prefs.settings.copy(galsGroups = listOf(group), activeGalsGroupId = group.id)
        App.prefs.selectNode(node)
        App.repo.snapshots.value = mapOf(node.sourceId to SourceSnapshot(node.sourceId, null, null, emptyList(), listOf(node), 1))
    }

    @Test fun unchangedRequestRetainsTheExactVerifiedNodeAndProfile() {
        val profile = NodeProfile(fragment = true)
        App.prefs.rememberProfile(node.id, profile)
        val captured = capture()
        assertTrue(captured.valid(App.prefs, App.repo))
        assertEquals(node, captured.node)
        assertEquals(profile, captured.profile)
    }

    @Test fun changedProfileRejectsTheQueuedRequest() {
        val captured = capture()
        App.prefs.rememberProfile(node.id, NodeProfile(fingerprint = "firefox"))
        assertFalse(captured.valid(App.prefs, App.repo))
    }

    @Test fun changedTargetRejectsTheQueuedRequest() {
        val captured = capture()
        App.prefs.settings = App.prefs.settings.copy(galsGroups = listOf(group.copy(target = ServiceKind.YOUTUBE)))
        assertFalse(captured.valid(App.prefs, App.repo))
    }

    @Test fun missingNodeRejectsTheQueuedRequest() {
        val captured = capture()
        App.repo.snapshots.value = emptyMap()
        assertFalse(captured.valid(App.prefs, App.repo))
    }

    @Test fun anotherSelectionWithTheSameIdButDifferentSourceRejectsTheRequest() {
        val captured = capture()
        App.prefs.selectNode(node.copy(sourceId = "different-source"))
        assertFalse(captured.valid(App.prefs, App.repo))
    }

    @Test fun cancellationStopAndNetworkChangeEachInvalidateRequests() {
        val cancelled = capture()
        VpnBus.automaticEpoch.incrementAndGet()
        assertFalse(cancelled.valid(App.prefs, App.repo))
        val stopped = capture()
        VpnBus.stopEpoch.incrementAndGet()
        assertFalse(stopped.valid(App.prefs, App.repo))
        val network = capture()
        VpnBus.networkEpoch.incrementAndGet()
        assertFalse(network.valid(App.prefs, App.repo))
    }

    @Suppress("DEPRECATION")
    @Test fun switchingFromWifiToMobileBeforeTheServiceIsStartedRevokesTheCandidate() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val cm = app.getSystemService(ConnectivityManager::class.java)
        fun connect(type: Int) {
            shadowOf(cm).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED, type, 0, true, NetworkInfo.State.CONNECTED))
        }
        connect(ConnectivityManager.TYPE_WIFI)
        val wifi = cm.activeNetwork
        assertNotNull(wifi)
        val checked = capture()
        connect(ConnectivityManager.TYPE_MOBILE)
        assertNotEquals(wifi, cm.activeNetwork)
        assertFalse(checked.valid(App.prefs, App.repo))
    }

    @Test fun disablingBackgroundSwitchesDoesNotCancelAnExplicitForegroundSearch() {
        App.prefs.settings = App.prefs.settings.copy(modeNodes = true, autoSwitch = true)
        App.prefs.rememberForMode(NetMode.BLACK, node)
        val foreground = capture()
        val recovery = capture(AutomaticReason.FAILOVER)
        val mode = capture(AutomaticReason.MODE_SWITCH)
        App.prefs.settings = App.prefs.settings.copy(autoSwitch = false, modeNodes = false)
        assertTrue(foreground.valid(App.prefs, App.repo))
        assertFalse(recovery.valid(App.prefs, App.repo))
        assertFalse(mode.valid(App.prefs, App.repo))
    }

    @Test fun changingModeAssignmentRevokesTheQueuedModeSwitch() {
        App.prefs.settings = App.prefs.settings.copy(modeNodes = true)
        App.prefs.rememberForMode(NetMode.BLACK, node)
        val checked = capture(AutomaticReason.MODE_SWITCH)
        App.prefs.rememberForMode(NetMode.BLACK, node.copy(name = "Other config"))
        assertFalse(checked.valid(App.prefs, App.repo))
    }

    @Test fun detectingAnotherModeOnTheSameNetworkRevokesQueuedRequest() {
        App.prefs.settings = App.prefs.settings.copy(netMode = app.farvater.core.model.NetModeChoice.AUTO)
        App.prefs.lastNetMode = NetMode.BLACK.name
        val checked = capture()
        App.prefs.lastNetMode = NetMode.WHITE.name
        assertFalse(checked.valid(App.prefs, App.repo))
    }

    private fun capture(reason: AutomaticReason = AutomaticReason.FOREGROUND): AutomaticContext =
        AutomaticContext.capture(ProbeNetwork(ApplicationProvider.getApplicationContext<Application>()), node, App.prefs, App.repo, NetMode.BLACK, GalsPolicy.from(App.prefs.settings), reason)!!
}

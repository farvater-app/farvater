package app.farvater

import android.app.Application
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.catalog.BuiltInCatalog
import app.farvater.core.model.NetModeChoice
import app.farvater.core.model.ProxyNode
import app.farvater.core.model.SourceMode
import app.farvater.core.parser.LinkParser
import app.farvater.data.FullNodeCheck
import app.farvater.data.Prefs
import app.farvater.data.SourceConfig
import app.farvater.data.UserSource
import app.farvater.ui.MainViewModel
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FullNodeCheckStateTest {
    private lateinit var app: Application
    private lateinit var vm: MainViewModel
    private val source get() = BuiltInCatalog.sources.first { it.mode == SourceMode.BLACK }.id

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        prepareRuntime(app)
        vm = MainViewModel(app)
        val initialization = vm.viewModelScope.coroutineContext[Job]!!.children.toList()
        initialization.forEach { it.cancel() }
        val deadline = System.nanoTime() + 5_000_000_000
        while (initialization.any { !it.isCompleted } && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue(initialization.all { it.isCompleted })
        App.prefs.settings = App.prefs.settings.copy(communityEnabled = true, safeMode = false)
        App.prefs.enabledSources = setOf(source)
    }

    @After fun finish() {
        vm.viewModelScope.cancel()
        VpnBus.state.value = VpnState.Idle
    }

    @Test fun completedSnapshotsPersistByModeWithCompatibleEmptyDefault() {
        assertTrue(Prefs(app).fullNodeChecks.isEmpty())
        val snapshots = mapOf("BLACK" to FullNodeCheck(setOf("one"), 100),
            "WHITE" to FullNodeCheck(setOf("two"), 200))
        App.prefs.fullNodeChecks = snapshots
        assertEquals(snapshots, Prefs(app).fullNodeChecks)
        assertTrue(Prefs(app).nodeHealth.isEmpty())
    }

    @Test fun disabledSourceSafeModeAndSourceNetworkModeExcludeStaleFailures() {
        val secure = node("secure")
        val insecure = node("insecure", security = "none")
        val nodes = listOf(secure, insecure)
        snapshot(nodes)
        assertEquals(nodes.map { it.id }.toSet(), grouped(nodes))
        App.prefs.enabledSources = emptySet()
        assertTrue(grouped(nodes).isEmpty())
        App.prefs.enabledSources = setOf(source)
        App.prefs.settings = App.prefs.settings.copy(communityEnabled = false)
        assertTrue(grouped(nodes).isEmpty())
        App.prefs.settings = App.prefs.settings.copy(communityEnabled = true, safeMode = true)
        assertEquals(setOf(secure.id), grouped(nodes))
        App.prefs.sourceConfigs = mapOf(source to SourceConfig(mode = SourceMode.WHITE))
        assertTrue(grouped(nodes).isEmpty())
    }

    @Test fun currentModeSelectsItsOwnCompletedSnapshotAndRemovedNodesStayAbsent() {
        val one = node("one", ProxyNode.MANUAL_SOURCE)
        val two = node("two", ProxyNode.MANUAL_SOURCE)
        val now = System.currentTimeMillis()
        App.prefs.fullNodeChecks = mapOf("BLACK" to FullNodeCheck(setOf(one.id), now),
            "WHITE" to FullNodeCheck(setOf(two.id), now))
        assertEquals(setOf(one.id), grouped(listOf(one, two)))
        App.prefs.settings = App.prefs.settings.copy(netMode = NetModeChoice.WHITE)
        assertEquals(setOf(two.id), grouped(listOf(one, two)))
        assertTrue(grouped(listOf(one)).isEmpty())
        App.prefs.settings = App.prefs.settings.copy(groupDead = false)
        assertTrue(grouped(listOf(one, two)).isEmpty())
    }

    @Test fun connectedAndConnectingOwnAccountNodesAreProtected() {
        val current = node("current", "user-dummy")
        val sameAccount = node("same-account", current.sourceId)
        val public = node("public")
        val nodes = listOf(current, sameAccount, public)
        App.prefs.userSources = listOf(UserSource(current.sourceId, "https://source.example.test/sub", "Dummy"))
        snapshot(nodes)
        for (state in listOf(VpnState.Connecting(current), VpnState.Connected(current, 1))) {
            VpnBus.state.value = state
            assertEquals(setOf(public.id), grouped(nodes))
        }
        VpnBus.state.value = VpnState.Idle
        assertEquals(nodes.map { it.id }.toSet(), grouped(nodes))
        App.prefs.userSources = emptyList()
        assertEquals(setOf(public.id), grouped(nodes))
    }

    @Test fun connectedPublicNodeDoesNotExemptTheEntirePublicSource() {
        val current = node("connected-public")
        val other = node("other-public")
        val nodes = listOf(current, other)
        snapshot(nodes)
        VpnBus.state.value = VpnState.Connected(current, 1)
        assertEquals(setOf(other.id), grouped(nodes))
    }

    private fun snapshot(nodes: List<ProxyNode>) {
        App.prefs.fullNodeChecks = mapOf("BLACK" to FullNodeCheck(nodes.map { it.id }.toSet(), System.currentTimeMillis()))
    }

    private fun node(name: String, sourceId: String = source, security: String = "tls") =
        LinkParser.parse("vless://test@$name.example.test:443?security=$security#$name")!!.copy(sourceId = sourceId)

    @Suppress("UNCHECKED_CAST")
    private fun grouped(nodes: List<ProxyNode>): Set<String> = MainViewModel::class.java
        .getDeclaredMethod("groupedDeadFor", List::class.java).apply { isAccessible = true }.invoke(vm, nodes) as Set<String>
}

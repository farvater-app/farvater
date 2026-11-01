package app.farvater

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.catalog.BuiltInCatalog
import app.farvater.core.model.NetMode
import app.farvater.core.model.NodeProfile
import app.farvater.core.model.ProxyNode
import app.farvater.core.model.SourceMode
import app.farvater.core.parser.LinkParser
import app.farvater.data.SourceSnapshot
import app.farvater.data.UserSource
import app.farvater.engine.NodeTester
import app.farvater.engine.PathResult
import app.farvater.engine.Pathfinder
import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import app.farvater.ui.MainViewModel
import app.farvater.ui.UiState
import app.farvater.vpn.FarvaterVpnService
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowVpnService
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class,
    shadows = [GalsEligibilityTest.PathProbe::class, GalsEligibilityTest.DelayProbe::class],
    instrumentedPackages = ["app.farvater.engine.Pathfinder", "app.farvater.engine.NodeTester"])
class GalsEligibilityTest {
    private lateinit var app: Application
    private lateinit var vm: MainViewModel
    private lateinit var state: MutableStateFlow<UiState>
    private lateinit var snapshots: MutableStateFlow<Map<String, SourceSnapshot>>
    private val source get() = BuiltInCatalog.sources.first { it.mode == SourceMode.BLACK }.id

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        prepareRuntime(app)
        vm = MainViewModel(app)
        val initialization = vm.viewModelScope.coroutineContext[Job]!!.children.toList()
        initialization.forEach { it.cancel() }
        await { initialization.all { it.isCompleted } }
        @Suppress("UNCHECKED_CAST")
        state = field("_state").get(vm) as MutableStateFlow<UiState>
        @Suppress("UNCHECKED_CAST")
        snapshots = field("snapshots").get(vm) as MutableStateFlow<Map<String, SourceSnapshot>>
        App.prefs.settings = App.prefs.settings.copy(communityEnabled = true, safeMode = false)
        App.prefs.enabledSources = setOf(source)
        ShadowVpnService.setPrepareResult(null)
        PathProbe.nodes.clear()
        DelayProbe.onResult = {}
        PathProbe.onResult = { PathResult(NodeProfile(), speedBps = 100) }
    }

    @After fun finish() {
        vm.viewModelScope.cancel()
        DelayProbe.onResult = {}
        PathProbe.onResult = { PathResult(null) }
        VpnBus.state.value = VpnState.Idle
    }

    @Test fun disabledSourceWhileProbeRunsCannotBeSelected() {
        rejectsResult { App.prefs.enabledSources = emptySet() }
    }

    @Test fun enablingSafeModeWhileProbeRunsRejectsInsecurePublicNode() {
        rejectsResult(insecure = true) { App.prefs.settings = App.prefs.settings.copy(safeMode = true) }
    }

    @Test fun removedNodeIsRejectedEvenBeforeUiListRebuilds() {
        rejectsResult { snapshots.value = emptyMap() }
    }

    @Test fun normalProbeResultAfterCancellationCannotSelectOrConnect() {
        rejectsResult { vm.cancelTest() }
    }

    @Test fun removedOwnSourceRejectsResultDespiteRetainedSnapshot() {
        val node = node("own", sourceId = "user-dummy")
        App.prefs.userSources = listOf(UserSource(node.sourceId, "https://source.example.test/sub", "Dummy"))
        install(listOf(node))
        PathProbe.onResult = {
            App.prefs.userSources = emptyList()
            PathResult(NodeProfile(), speedBps = 100)
        }
        search()
        assertEquals(listOf(node), PathProbe.nodes)
        assertNull(App.prefs.lastNode)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun refinementExcludesEarlierVerifiedNodeRemovedByLaterProbe() {
        val first = node("first")
        val faster = node("faster")
        val last = node("last")
        install(listOf(first, faster, last))
        PathProbe.onResult = { candidate ->
            if (candidate == faster) VpnBus.state.value = VpnState.Connected(first, 1)
            if (candidate == last) snapshots.value = snapshots.value.mapValues { (_, snap) ->
                snap.copy(nodes = snap.nodes.filterNot { it.id == faster.id })
            }
            PathResult(NodeProfile(), speedBps = if (candidate == faster) 500 else 100)
        }
        search()
        assertEquals(listOf(first, faster, last), PathProbe.nodes)
        assertEquals(first, App.prefs.lastNode)
        assertNotNull(shadowOf(app).nextStartedService)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun permissionGrantRevalidatesAutomaticSelection() {
        val node = node("permission")
        install(listOf(node))
        ShadowVpnService.setPrepareResult(Intent("dummy.permission"))
        search()
        assertEquals(node, App.prefs.lastNode)
        assertNull(shadowOf(app).nextStartedService)
        App.prefs.enabledSources = emptySet()
        vm.onVpnPermissionResult(true)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun permissionGrantStillStartsEligibleAutomaticSelection() {
        val node = node("permission-ok")
        install(listOf(node))
        ShadowVpnService.setPrepareResult(Intent("dummy.permission"))
        search()
        assertNull(shadowOf(app).nextStartedService)
        vm.onVpnPermissionResult(true)
        assertNotNull(shadowOf(app).nextStartedService)
    }

    @Test fun stopBeforeFirstResultRejectsItEvenAfterIdleClearsRequest() {
        for (finished in listOf(false, true)) {
            val candidate = node("stopped-first")
            install(listOf(candidate))
            VpnBus.state.value = VpnState.Connected(node("previous"), 1)
            PathProbe.onResult = {
                stopAndMaybeFinish(finished)
                PathResult(NodeProfile(), speedBps = 100)
            }
            search()
            assertNull(App.prefs.lastNode)
            assertNull(App.prefs.profileFor(candidate.id))
            assertNull(shadowOf(app).nextStartedService)
        }
    }

    @Test fun stopDuringRefinementRejectsTheFasterResult() {
        val first = node("first-stop")
        val faster = node("faster-stop")
        install(listOf(first, faster))
        PathProbe.onResult = { candidate ->
            if (candidate == faster) {
                assertNotNull(shadowOf(app).nextStartedService)
                VpnBus.state.value = VpnState.Connected(first, 1)
                stopAndMaybeFinish(false)
            }
            PathResult(NodeProfile(), speedBps = if (candidate == first) 100 else 500)
        }
        search()
        assertEquals(first, App.prefs.lastNode)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun stopRevokesPendingPermissionEvenWhenNodeRemainsEligible() {
        val candidate = node("permission-stopped")
        install(listOf(candidate))
        ShadowVpnService.setPrepareResult(Intent("dummy.permission"))
        search()
        stopAndMaybeFinish(true)
        vm.onVpnPermissionResult(true)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun stopRevokesLatePreflightSuccessAfterIdle() {
        val candidate = node("preflight-stopped")
        install(listOf(candidate))
        VpnBus.state.value = VpnState.Idle
        DelayProbe.onResult = { stopAndMaybeFinish(true) }
        MainViewModel::class.java.getDeclaredMethod("preflight", ProxyNode::class.java)
            .apply { isAccessible = true }.invoke(vm, candidate)
        await { (field("preflightJob").get(vm) as? Job)?.isCompleted == true }
        assertNull(shadowOf(app).nextStartedService)
        assertNull(state.value.deadPrompt)
    }

    private fun stopAndMaybeFinish(finished: Boolean) {
        FarvaterVpnService.stop(app)
        assertEquals(FarvaterVpnService.ACTION_STOP, shadowOf(app).nextStartedService?.action)
        if (finished) {
            VpnBus.state.value = VpnState.Idle
            VpnBus.request.value = null
        }
    }

    private fun rejectsResult(insecure: Boolean = false, mutation: () -> Unit) {
        val node = node("stale", insecure)
        install(listOf(node))
        PathProbe.onResult = {
            mutation()
            assertTrue(state.value.nodes.contains(node))
            PathResult(NodeProfile(), speedBps = 100)
        }
        search()
        assertEquals(listOf(node), PathProbe.nodes)
        assertNull(App.prefs.lastNode)
        assertNull(App.prefs.profileFor(node.id))
        assertNull(shadowOf(app).nextStartedService)
    }

    private fun install(nodes: List<ProxyNode>) {
        snapshots.value = nodes.groupBy { it.sourceId }.mapValues { (id, list) ->
            SourceSnapshot(id, null, null, emptyList(), list, 1)
        }
        state.value = state.value.copy(nodes = nodes, gals = true, galsNet = NetMode.BLACK)
    }

    private fun node(name: String, insecure: Boolean = false, sourceId: String = source) =
        LinkParser.parse("vless://test@$name.example.test:443?security=${if (insecure) "none" else "tls"}", sourceId)!!

    private fun search() {
        MainViewModel::class.java.getDeclaredMethod("findWorkingNow", Long::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(vm, VpnBus.stopEpoch.get())
        await { (field("pathJob").get(vm) as? Job)?.isCompleted == true }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(5)
        }
        assertTrue(condition())
    }

    private fun field(name: String) = MainViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Implements(value = NodeTester::class, isInAndroidSdk = false)
    class DelayProbe {
        @Implementation fun testAll(nodes: List<ProxyNode>, url: String, concurrency: Int): Flow<TestResult> = flow {
            kotlinx.coroutines.delay(1)
            nodes.forEachIndexed { index, node ->
                onResult()
                emit(TestResult(node.id, index + 1L, TestMethod.REAL, Long.MAX_VALUE))
            }
        }
        companion object {
            var onResult: () -> Unit = {}
        }
    }

    @Implements(value = Pathfinder::class, isInAndroidSdk = false)
    class PathProbe {
        @Implementation fun find(node: ProxyNode, saved: NodeProfile?, ordinaryBlocking: Boolean,
            checkDns: Boolean, limit: Int, onTry: (NodeProfile) -> Unit, continuation: Continuation<PathResult>): Any {
            nodes += node
            return onResult(node)
        }
        companion object {
            val nodes = mutableListOf<ProxyNode>()
            var onResult: (ProxyNode) -> PathResult = { PathResult(null) }
        }
    }
}

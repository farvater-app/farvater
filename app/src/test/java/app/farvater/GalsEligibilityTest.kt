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
import app.farvater.data.GalsGroup
import app.farvater.data.GalsStrategy
import app.farvater.data.ServiceCheck
import app.farvater.data.ServiceReason
import app.farvater.data.ServiceVerdict
import app.farvater.engine.GalsProbe
import app.farvater.data.ServiceKind
import app.farvater.engine.NodeTester
import app.farvater.engine.NodeCheckCoordinator
import app.farvater.engine.NodeCheckWork
import app.farvater.engine.PathResult
import app.farvater.engine.Pathfinder
import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import app.farvater.ui.MainViewModel
import app.farvater.ui.UiState
import app.farvater.vpn.FarvaterVpnService
import app.farvater.vpn.NodeCheckService
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
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
    shadows = [GalsEligibilityTest.PathProbe::class, GalsEligibilityTest.DelayProbe::class, GalsEligibilityTest.ConfiguredProbe::class],
    instrumentedPackages = ["app.farvater.engine.Pathfinder", "app.farvater.engine.NodeTester", "app.farvater.engine.GalsProbe"])
class GalsEligibilityTest {
    private lateinit var app: Application
    private lateinit var vm: MainViewModel
    private lateinit var state: MutableStateFlow<UiState>
    private lateinit var snapshots: MutableStateFlow<Map<String, SourceSnapshot>>
    private lateinit var results: MutableStateFlow<Map<String, TestResult>>
    private var checkService: NodeCheckService? = null
    private var checkStartId = 0
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
        @Suppress("UNCHECKED_CAST")
        results = field("results").get(vm) as MutableStateFlow<Map<String, TestResult>>
        vm.viewModelScope.launch(Dispatchers.Unconfined) {
            NodeCheckCoordinator.state.collect { publishCheckResults(it) }
        }
        App.prefs.settings = App.prefs.settings.copy(communityEnabled = true, safeMode = false)
        App.prefs.enabledSources = setOf(source)
        ShadowVpnService.setPrepareResult(null)
        PathProbe.nodes.clear()
        ConfiguredProbe.calls = 0
        DelayProbe.onResult = {}
        PathProbe.onResult = { PathResult(NodeProfile(), speedBps = 100, delayMs = 100,
            targetCheck = ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, 1)) }
    }

    @After fun finish() {
        NodeCheckCoordinator.cancel()
        checkService?.onDestroy()
        vm.viewModelScope.cancel()
        await {
            (NodeCheckCoordinator::class.java.getDeclaredField("jobs").apply { isAccessible = true }.get(null) as Map<*, *>).isEmpty()
        }
        @Suppress("UNCHECKED_CAST")
        val work = NodeCheckCoordinator::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(null) as MutableStateFlow<NodeCheckWork>
        work.value = NodeCheckWork()
        DelayProbe.onResult = {}
        PathProbe.onResult = { PathResult(null) }
        VpnBus.state.value = VpnState.Idle
    }

    @Test fun checkingOneSubscriptionFindsItsNodesEvenWhenTheUiCollapsesDuplicates() {
        val allowed = node("same-address")
        val duplicate = allowed.copy(sourceId = ProxyNode.MANUAL_SOURCE)
        val unrelated = node("unrelated", sourceId = ProxyNode.MANUAL_SOURCE)
        install(listOf(duplicate, unrelated, allowed))
        state.value = state.value.copy(nodes = listOf(duplicate, unrelated), gals = false)
        vm.testSource(source)
        await { (field("testJob").get(vm) as? Job)?.isCompleted == true }
        assertEquals(setOf(allowed.id), results.value.keys)
        assertTrue(App.prefs.fullNodeChecks.isEmpty())
    }

    @Test fun disabledSourceWhileProbeRunsCannotBeSelected() {
        rejectsResult { App.prefs.enabledSources = emptySet() }
    }

    @Test fun configuredTargetDoesNotRequireAnUnrelatedGenericLatencyCheck() {
        configure()
        val candidate = node("direct-target")
        install(listOf(candidate))
        DelayProbe.onResult = { fail("Configured Gals must check its target directly") }
        search()
        assertEquals(1, ConfiguredProbe.calls)
        assertEquals(candidate, App.prefs.lastNode)
    }

    @Test fun configuredGroupFindsAnAllowedDuplicateMissingFromTheUiList() {
        configure(sourceIds = setOf(source))
        val allowed = node("duplicate")
        val excluded = allowed.copy(sourceId = ProxyNode.MANUAL_SOURCE)
        install(listOf(excluded, allowed))
        state.value = state.value.copy(nodes = listOf(excluded))
        search()
        assertEquals(listOf(allowed), PathProbe.nodes)
        assertEquals(allowed, App.prefs.lastNode)
    }

    @Test fun configuredTargetFailureCannotUseTheLegacyPartialFallback() {
        configure()
        install(listOf(node("target-rejected")))
        PathProbe.onResult = { PathResult(null, telegramBlocked = true, partial = NodeProfile(), speedBps = 100,
            targetCheck = ServiceCheck(ServiceVerdict.REJECTED, ServiceReason.REGION, 1)) }
        search()
        assertNull(App.prefs.lastNode)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun configuredLatencyPrefersShorterDelayEvenWhenThroughputIsLower() {
        configure()
        val first = node("slow-latency")
        val best = node("low-latency")
        install(listOf(first, best))
        PathProbe.onResult = { candidate ->
            if (candidate == best) VpnBus.state.value = VpnState.Connected(first, 1)
            PathResult(NodeProfile(), delayMs = if (candidate == best) 40 else 200,
                speedBps = if (candidate == best) 100 else 1000, targetCheck = ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, 1))
        }
        search()
        assertEquals(best, App.prefs.lastNode)
    }

    @Test fun configuredThroughputPrefersSpeedWhenBothTargetsRespond() {
        configure(strategy = GalsStrategy.THROUGHPUT)
        val first = node("low-throughput")
        val best = node("high-throughput")
        install(listOf(first, best))
        PathProbe.onResult = { candidate ->
            if (candidate == best) VpnBus.state.value = VpnState.Connected(first, 1)
            PathResult(NodeProfile(), delayMs = if (candidate == best) 200 else 40,
                speedBps = if (candidate == best) 1000 else 100, targetCheck = ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, 1))
        }
        search()
        assertEquals(2, ConfiguredProbe.calls)
        assertEquals(best, App.prefs.lastNode)
    }

    @Test fun changedSavedProfileRejectsAConfiguredResult() {
        configure()
        val candidate = node("profile-changed")
        install(listOf(candidate))
        val chosen = NodeProfile(fingerprint = "firefox")
        PathProbe.onResult = {
            App.prefs.rememberProfile(candidate.id, chosen)
            PathResult(NodeProfile(), delayMs = 50, targetCheck = ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, 1))
        }
        search()
        assertEquals(chosen, App.prefs.profileFor(candidate.id))
        assertNull(App.prefs.lastNode)
    }

    @Test fun repeatedConfiguredSearchReachesNodesOutsideTheFirstBatch() {
        configure()
        val nodes = (1..10).map { node("batch-$it") }
        install(nodes)
        PathProbe.onResult = { candidate ->
            if (candidate == nodes.last()) PathResult(NodeProfile(), delayMs = 50,
                targetCheck = ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, 1)) else PathResult(null)
        }
        search()
        assertNull(App.prefs.lastNode)
        search()
        assertTrue(PathProbe.nodes.contains(nodes.last()))
        assertEquals(nodes.last(), App.prefs.lastNode)
    }

    @Test fun emptyConfiguredGroupFinishesTheSearchState() {
        configure(sourceIds = emptySet())
        install(listOf(node("outside-empty-scope")))
        search()
        assertFalse(state.value.gals)
        assertFalse(state.value.refining)
        assertNull(state.value.pathfinding)
        assertNull(App.prefs.lastNode)
    }

    private fun configure(strategy: GalsStrategy = GalsStrategy.LATENCY, sourceIds: Set<String>? = null) {
        val group = GalsGroup("configured", "Configured", allSources = sourceIds == null,
            sourceIds = sourceIds.orEmpty(), strategy = strategy, target = ServiceKind.YOUTUBE)
        App.prefs.settings = App.prefs.settings.copy(galsGroups = listOf(group), activeGalsGroupId = group.id)
    }

    @Test fun selectedGroupRestrictsAutomaticCandidatesWithoutChangingTheNodeList() {
        val included = node("included")
        val excluded = node("excluded", sourceId = ProxyNode.MANUAL_SOURCE)
        val group = GalsGroup("selected", "Selected", allSources = false, sourceIds = setOf(source))
        App.prefs.settings = App.prefs.settings.copy(galsGroups = listOf(group), activeGalsGroupId = group.id)
        install(listOf(included, excluded))
        search()
        assertEquals(listOf(included), PathProbe.nodes)
        assertEquals(included, App.prefs.lastNode)
        assertEquals(listOf(included, excluded), state.value.nodes)
    }

    @Test fun changedTargetRejectsAResultEvenWhenTheNodeRemainsInTheGroup() {
        val group = GalsGroup("selected", "Selected")
        App.prefs.settings = App.prefs.settings.copy(galsGroups = listOf(group), activeGalsGroupId = group.id)
        rejectsResult {
            App.prefs.settings = App.prefs.settings.copy(galsGroups = listOf(group.copy(target = ServiceKind.YOUTUBE)))
        }
    }

    @Test fun manualSelectionCancelsLateAutomaticSelection() {
        val candidate = node("automatic")
        val manual = node("manual", sourceId = ProxyNode.MANUAL_SOURCE)
        install(listOf(candidate, manual))
        PathProbe.onResult = {
            vm.select(manual)
            PathResult(NodeProfile(), speedBps = 100)
        }
        search()
        assertEquals(manual, App.prefs.lastNode)
        assertNull(App.prefs.profileFor(candidate.id))
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun manualSelectionReplacesAConnectionStillStarting() {
        val automatic = node("starting")
        val manual = node("selected", sourceId = ProxyNode.MANUAL_SOURCE)
        install(listOf(automatic, manual))
        VpnBus.state.value = VpnState.Connecting(automatic)
        vm.select(manual)
        assertEquals(manual, App.prefs.lastNode)
        assertEquals(FarvaterVpnService.ACTION_START, shadowOf(app).nextStartedService?.action)
    }

    @Test fun manualSelectionRevokesAnOlderPendingPermissionRequest() {
        val previous = node("pending")
        val selected = node("selected", sourceId = ProxyNode.MANUAL_SOURCE)
        install(listOf(previous, selected))
        state.value = state.value.copy(gals = false)
        ShadowVpnService.setPrepareResult(Intent("dummy.permission"))
        vm.select(previous)
        vm.toggleConnection()
        assertNull(shadowOf(app).nextStartedService)
        vm.select(selected)
        vm.onVpnPermissionResult(true)
        assertEquals(selected, App.prefs.lastNode)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun manualSelectionCancelsAnOlderPreflightProbe() {
        val previous = node("preflight")
        val selected = node("selected", sourceId = ProxyNode.MANUAL_SOURCE)
        install(listOf(previous, selected))
        DelayProbe.onResult = { vm.select(selected) }
        MainViewModel::class.java.getDeclaredMethod("preflight", ProxyNode::class.java)
            .apply { isAccessible = true }.invoke(vm, previous)
        await { (field("preflightJob").get(vm) as? Job)?.isCompleted == true }
        assertEquals(selected, App.prefs.lastNode)
        assertNull(shadowOf(app).nextStartedService)
        assertNull(state.value.deadPrompt)
    }

    @Test fun changedGroupBeforePermissionGrantCannotStartAnOldTargetResult() {
        val group = GalsGroup("selected", "Selected")
        App.prefs.settings = App.prefs.settings.copy(galsGroups = listOf(group), activeGalsGroupId = group.id)
        val candidate = node("permission-policy")
        install(listOf(candidate))
        ShadowVpnService.setPrepareResult(Intent("dummy.permission"))
        search()
        assertNull(shadowOf(app).nextStartedService)
        vm.updateSettings { it.copy(galsGroups = listOf(group.copy(target = ServiceKind.CUSTOM,
            customUrl = "https://probe.example.test/"))) }
        vm.onVpnPermissionResult(true)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun changedProfileBeforePermissionGrantCannotStartAnUnverifiedProfile() {
        configure()
        val candidate = node("permission-profile")
        install(listOf(candidate))
        ShadowVpnService.setPrepareResult(Intent("dummy.permission"))
        search()
        assertNull(shadowOf(app).nextStartedService)
        App.prefs.rememberProfile(candidate.id, NodeProfile(fingerprint = "firefox"))
        vm.onVpnPermissionResult(true)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun removingASourceKeepsAnEmptySelectedScopeInsteadOfUsingEverySource() {
        val own = "user-dummy"
        App.prefs.userSources = listOf(UserSource(own, "https://source.example.test/sub", "Dummy"))
        val group = GalsGroup("selected", "Selected", allSources = false, sourceIds = setOf(own))
        App.prefs.settings = App.prefs.settings.copy(galsGroups = listOf(group), activeGalsGroupId = group.id)
        vm.removeUserSource(own)
        val saved = App.prefs.settings.galsGroups.single()
        assertFalse(saved.allSources)
        assertTrue(saved.sourceIds.isEmpty())
        assertEquals(group.id, App.prefs.settings.activeGalsGroupId)
    }

    @Test fun deferredCandidateIsRetriedAfterScanningInsteadOfPenalized() {
        val candidate = node("deferred")
        install(listOf(candidate))
        var calls = 0
        PathProbe.onResult = {
            if (++calls == 1) PathResult(null, deferred = true) else PathResult(NodeProfile(), speedBps = 100)
        }
        search()
        assertEquals(listOf(candidate, candidate), PathProbe.nodes)
        assertEquals(candidate.id, App.prefs.lastNode?.id)
    }

    @Test fun persistentLocalContentionDoesNotSelectAnUnverifiedFallback() {
        val candidate = node("busy")
        install(listOf(candidate))
        PathProbe.onResult = { PathResult(null, deferred = true) }
        search()
        assertEquals(listOf(candidate, candidate), PathProbe.nodes)
        assertNull(App.prefs.lastNode)
        assertNull(shadowOf(app).nextStartedService)
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
        dispatchChecking()
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
        dispatchChecking()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            dispatchChecking()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(5)
        }
        assertTrue(condition())
    }

    private fun dispatchChecking() {
        val application = shadowOf(app)
        while (application.peekNextStartedService()?.component?.className == NodeCheckService::class.java.name) {
            val intent = application.nextStartedService
            if (checkService == null || shadowOf(checkService!!).isStoppedBySelf) {
                checkService?.onDestroy()
                checkService = Robolectric.buildService(NodeCheckService::class.java).create().get()
            }
            checkService!!.onStartCommand(intent, 0, ++checkStartId)
        }
    }

    private fun publishCheckResults(work: NodeCheckWork) {
        if (!::results.isInitialized || !::state.isInitialized) return
        results.update { current -> current + work.results.filter { (id, result) -> (current[id]?.at ?: Long.MIN_VALUE) <= result.at } }
        state.update { it.copy(results = results.value) }
    }

    private fun field(name: String) = MainViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Implements(value = NodeTester::class, isInAndroidSdk = false)
    class DelayProbe {
        @Implementation fun testAll(nodes: List<ProxyNode>, url: String, concurrency: Int, profiles: Map<String, NodeProfile>): Flow<TestResult> =
            testAll(nodes, url, concurrency)
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

    @Implements(value = GalsProbe::class, isInAndroidSdk = false)
    class ConfiguredProbe {
        @Implementation fun find(node: ProxyNode, saved: NodeProfile?, ordinaryBlocking: Boolean,
            checkDns: Boolean, group: GalsGroup, probeUrl: String, limit: Int, onTry: (NodeProfile) -> Unit,
            continuation: Continuation<PathResult>): Any {
            calls++
            PathProbe.nodes += node
            return PathProbe.onResult(node)
        }
        companion object { var calls = 0 }
    }
}

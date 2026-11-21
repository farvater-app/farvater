package app.farvater

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import android.os.PowerManager
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.model.NetModeChoice
import app.farvater.core.model.NodeProfile
import app.farvater.core.model.ProxyNode
import app.farvater.core.parser.LinkParser
import app.farvater.data.NodeHealth
import app.farvater.data.ServiceCheck
import app.farvater.data.ServiceChecks
import app.farvater.data.ServiceKind
import app.farvater.data.ServiceReason
import app.farvater.data.ServiceTarget
import app.farvater.data.ServiceVerdict
import app.farvater.engine.NodeCheckCoordinator
import app.farvater.engine.NodeCheckFailure
import app.farvater.engine.NodeCheckWork
import app.farvater.engine.NodeTester
import app.farvater.engine.ServiceProbe
import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import app.farvater.engine.TestFailure
import app.farvater.data.UserSource
import app.farvater.data.ProbeMethod
import app.farvater.data.FullNodeCheck
import app.farvater.ui.MainViewModel
import app.farvater.ui.UiState
import app.farvater.vpn.NodeCheckService
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru",
    shadows = [NodeCheckServiceTest.Nodes::class, NodeCheckServiceTest.Services::class],
    instrumentedPackages = ["app.farvater.engine.NodeTester", "app.farvater.engine.ServiceProbe"])
class NodeCheckServiceTest {
    private lateinit var app: Application
    private val models = mutableListOf<MainViewModel>()
    private var service: NodeCheckService? = null
    private val nodes = (1..2).map { LinkParser.parse("vless://test@check$it.example.test:443?security=tls#Node$it")!! }

    @Before
    fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        prepareRuntime(app)
        Nodes.permits = Channel(Channel.UNLIMITED)
        Nodes.method = TestMethod.TCP
        Nodes.delayMs = 25
        Nodes.delays = emptyMap()
        Nodes.nativeIds.clear()
        Nodes.liveResult = null
        Nodes.reason = TestFailure.UNKNOWN
        Nodes.onLive = {}
        Nodes.onBatch = {}
        Nodes.seenProfiles = emptyMap()
        Nodes.cancelled = 0
        Services.permits = Channel(Channel.UNLIMITED)
        Services.entered = 0
        Services.closed = 0
        Services.fail = false
        Services.gate = Semaphore(1)
        Services.cleanupGate = null
        Services.cleanupGates.clear()
        Services.lateResultAfterCleanup = false
    }

    @After
    fun finish() {
        Services.cleanupGates.forEach { it.complete(Unit) }
        Services.cleanupGate = null
        NodeCheckCoordinator.cancel()
        service?.onDestroy()
        models.forEach { it.viewModelScope.cancel() }
        await {
            (NodeCheckCoordinator::class.java.getDeclaredField("jobs").apply { isAccessible = true }.get(null) as Map<*, *>).isEmpty()
        }
        @Suppress("UNCHECKED_CAST")
        val state = NodeCheckCoordinator::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(null) as MutableStateFlow<NodeCheckWork>
        state.value = NodeCheckWork()
    }

    @Test
    fun completedCheckSortsOnceWhenLiveSortingIsDisabled() {
        App.prefs.settings = App.prefs.settings.copy(liveSort = false)
        Nodes.delays = mapOf(nodes.first().id to 250L, nodes.last().id to 25L)
        App.repo.appendManual(nodes)
        val vm = MainViewModel(app).also(models::add)
        await { vm.state.value.nodes.map { it.id } == nodes.map { it.id } }
        vm.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { vm.state.value.progress?.done == 1 }
        assertEquals(nodes.map { it.id }, vm.state.value.nodes.map { it.id })
        Nodes.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active && vm.state.value.progress == null }
        assertEquals(nodes.reversed().map { it.id }, vm.state.value.nodes.map { it.id })
    }

    @Test
    fun failedLiveTunnelDoesNotFallBackToAnotherCoreDuringSingleCheck() {
        val node = nodes.first()
        VpnBus.state.value = VpnState.Connected(node, 1)
        val vm = model()
        vm.testNode(node)
        startService()
        Nodes.permits.trySend(Unit)
        await { vm.state.value.results[node.id] != null && !NodeCheckCoordinator.state.value.active }
        assertFalse(vm.state.value.results.getValue(node.id).alive)
        assertFalse(node.id in Nodes.nativeIds)
    }

    @Test
    fun failedLiveTunnelDoesNotFallBackToAnotherCoreDuringFullCheck() {
        val node = nodes.first()
        VpnBus.state.value = VpnState.Connected(node, 1)
        val vm = model()
        vm.testAll()
        startService()
        repeat(2) { Nodes.permits.trySend(Unit) }
        await { vm.state.value.results.size == 2 && !NodeCheckCoordinator.state.value.active }
        assertFalse(vm.state.value.results.getValue(node.id).alive)
        assertEquals(listOf(nodes.last().id), Nodes.nativeIds)
    }

    @Test
    fun failedActiveNodeMeasurementStillChecksOtherNodes() {
        val active = nodes.first()
        VpnBus.state.value = VpnState.Connected(active, 1)
        Nodes.onLive = { throw java.io.IOException("live tunnel closed") }
        val vm = model()
        vm.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active }
        val work = NodeCheckCoordinator.state.value
        assertNull(work.failure)
        assertEquals(false, work.results[active.id]?.alive)
        assertEquals(true, work.results[nodes.last().id]?.alive)
    }

    @Test
    fun repeatedPartialHttpsChecksReachTheEndOfTheSubscription() {
        val list = nodes + LinkParser.parse("vless://test@third.example.test:443?security=tls#Third")!!
        fun begin(settings: app.farvater.data.AppSettings) = NodeCheckCoordinator.testAll(
            app, list, settings, "BLACK", connected = null, exempt = emptySet(), allowServices = false, fullScope = false,
        )!!.also { NodeCheckCoordinator.ready(NodeCheckCoordinator.state.value.session) }
        val settings = App.prefs.settings.copy(probeMethod = ProbeMethod.HTTPS)
        for (expected in list + list.first()) {
            val job = begin(settings)
            Nodes.permits.trySend(Unit)
            await { NodeCheckCoordinator.state.value.results.isNotEmpty() }
            assertEquals(setOf(expected.id), NodeCheckCoordinator.state.value.results.keys)
            NodeCheckCoordinator.fail(NodeCheckCoordinator.state.value.session, NodeCheckFailure.TIMEOUT)
            await { job.isCompleted }
        }
        val newTarget = begin(settings.copy(testUrl = "https://different.example.test/"))
        Nodes.permits.trySend(Unit)
        await { NodeCheckCoordinator.state.value.results.isNotEmpty() }
        assertEquals(setOf(list.first().id), NodeCheckCoordinator.state.value.results.keys)
        NodeCheckCoordinator.cancel()
        await { newTarget.isCompleted }
    }

    @Test
    fun interruptedBatchDoesNotTellAnOpenAppToStartAgain() {
        Nodes.onBatch = { throw java.io.IOException("interrupted batch") }
        val vm = model()
        vm.testAll()
        startService()
        await { !NodeCheckCoordinator.state.value.active && vm.state.value.message != null }
        assertEquals(app.getString(R.string.node_test_incomplete), vm.state.value.message)
    }

    @Test
    fun busyCheckIsVisibleWithoutReplacingHealthOrCompleteSnapshot() {
        val node = nodes.first()
        val before = TestResult(node.id, 40, TestMethod.REAL)
        App.prefs.nodeHealth = NodeHealth.record(emptyMap(), "BLACK", listOf(before))
        val savedHealth = App.prefs.nodeHealth
        val savedFull = FullNodeCheck(setOf(nodes.last().id), before.at)
        App.prefs.fullNodeChecks = mapOf("BLACK" to savedFull)
        Nodes.reason = TestFailure.BUSY
        Nodes.delayMs = -1
        App.repo.appendManual(listOf(node))
        val vm = MainViewModel(app).also(models::add)
        await { vm.state.value.nodes.singleOrNull()?.id == node.id }
        vm.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active }
        assertEquals(TestFailure.BUSY, vm.state.value.results[node.id]?.reason)
        val best = MainViewModel::class.java.getDeclaredMethod("bestNode").apply { isAccessible = true }
        assertEquals(node.id, (best.invoke(vm) as ProxyNode).id)
        assertEquals(savedHealth, App.prefs.nodeHealth)
        assertEquals(savedFull, App.prefs.fullNodeChecks["BLACK"])
    }

    @Test
    fun legacySubscriptionTitleUsesOnlyTheEndpointAndKeepsOneSource() {
        val source = UserSource("user-old", "https://example.test:8443/private/profile", "kotlin.Unit")
        App.prefs.userSources = listOf(source)
        val cache = java.io.File(app.filesDir, "subs/${source.id}.txt")
        cache.writeText("#profile-title: kotlin.Unit")
        App.repo.loadSnapshot(source.id)
        val vm = MainViewModel(app).also(models::add)
        await { vm.state.value.sources.any { it.id == source.id } }
        val owned = vm.state.value.sources.filterNot { it.community }
        assertEquals(listOf("example.test:8443"), owned.map { it.title })
        cache.writeText("#profile-title: My subscription")
        App.repo.loadSnapshot(source.id)
        await { vm.state.value.sources.single { it.id == source.id }.title == "My subscription" }
        assertEquals(listOf(source), App.prefs.userSources)
    }

    @Test
    fun singleBusyCheckReportsIncompleteInsteadOfADeadNode() {
        val node = nodes.first()
        Nodes.reason = TestFailure.BUSY
        Nodes.delayMs = -1
        val vm = model()
        vm.testNode(node)
        startService()
        Nodes.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active && vm.state.value.message != null }
        assertEquals(app.getString(R.string.node_test_incomplete), vm.state.value.message)
    }

    @Test
    fun liveFailureIsPublishedForExemptAccountWithoutMarkingItDead() {
        val node = nodes.first()
        VpnBus.state.value = VpnState.Connected(node, 1)
        App.prefs.nodeHealth = NodeHealth.record(emptyMap(), "BLACK", listOf(TestResult(node.id, 40, TestMethod.REAL)))
        val vm = model()
        NodeCheckCoordinator.testAll(app, nodes, App.prefs.settings, "BLACK", node, setOf(node.id), false)
        startService()
        Nodes.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active }
        assertEquals(-1L, vm.state.value.results[node.id]?.delayMs)
        assertEquals(0, App.prefs.nodeHealth["BLACK/${node.id}"]?.fails)
        assertFalse(node.id in NodeHealth.dead(App.prefs.nodeHealth, "BLACK"))
    }

    @Test
    fun changedLiveSessionCannotOverwriteStoredHealth() {
        val node = nodes.first()
        val before = TestResult(node.id, 40, TestMethod.REAL)
        App.prefs.nodeHealth = NodeHealth.record(emptyMap(), "BLACK", listOf(before))
        val health = App.prefs.nodeHealth
        VpnBus.state.value = VpnState.Connected(node, 1)
        Nodes.liveResult = TestResult(node.id, 10, TestMethod.REAL)
        Nodes.onLive = { VpnBus.state.value = VpnState.Connected(node, 2) }
        val vm = model()
        vm.testNode(node)
        startService()
        await { !NodeCheckCoordinator.state.value.active }
        assertEquals(TestFailure.STALE, vm.state.value.results[node.id]?.reason)
        assertEquals(health, App.prefs.nodeHealth)
        assertTrue(Nodes.nativeIds.isEmpty())
        await { vm.state.value.message != null }
        assertEquals(app.getString(R.string.node_test_incomplete), vm.state.value.message)
    }

    @Test
    fun partialCheckPreservesOtherNodesAndTheFullSnapshotTimestamp() {
        val before = TestResult(nodes.last().id, 40, TestMethod.REAL)
        App.prefs.nodeHealth = NodeHealth.record(emptyMap(), "BLACK", listOf(before))
        val oldHealth = App.prefs.nodeHealth.getValue("BLACK/${nodes.last().id}")
        val full = FullNodeCheck(setOf(nodes.first().id), before.at)
        App.prefs.fullNodeChecks = mapOf("BLACK" to full)
        NodeCheckCoordinator.testAll(app, nodes.take(1), App.prefs.settings, "BLACK", null, emptySet(), false, fullScope = false)
        startService()
        Nodes.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active }
        assertEquals(full, App.prefs.fullNodeChecks["BLACK"])
        assertEquals(oldHealth, App.prefs.nodeHealth["BLACK/${nodes.last().id}"])
        assertTrue(App.prefs.nodeHealth.containsKey("BLACK/${nodes.first().id}"))
    }

    @Test
    fun queuedCheckKeepsTheProfileSnapshotFromItsRequest() {
        val node = nodes.first()
        val saved = NodeProfile(fingerprint = "firefox")
        App.prefs.nodeProfiles = mapOf(node.id to saved)
        val vm = model()
        vm.testNode(node)
        App.prefs.nodeProfiles = mapOf(node.id to NodeProfile(fingerprint = "chrome"))
        startService()
        Nodes.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active }
        assertEquals(saved, Nodes.seenProfiles[node.id])
    }

    @Test
    fun ordinaryCheckContinuesAfterViewModelClearsAndNewModelObservesProgress() {
        val first = model()
        first.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { first.state.value.progress?.done == 1 }
        val lock = wakeLock()
        assertTrue(lock.isHeld)
        first.viewModelScope.cancel()
        val restored = model()
        await { restored.state.value.progress?.done == 1 }
        Nodes.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active }
        await { restored.state.value.progress == null && restored.state.value.results.size == 2 }
        assertEquals(2, NodeHealth.results(App.prefs.nodeHealth, "BLACK").size)
        assertFalse(lock.isHeld)
        assertEquals(1, shadowOf(service!!).stopSelfResultId)
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
    }

    @Test
    fun serviceCheckContinuesWithoutActivityAndPersistsItsResult() {
        enableServices()
        val first = model(nodes.take(1))
        first.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { Services.entered == 1 }
        first.viewModelScope.cancel()
        val restored = model(nodes.take(1))
        await { restored.state.value.serviceChecking }
        Services.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active }
        await { restored.state.value.serviceChecks.values.singleOrNull()?.verdict == ServiceVerdict.RESPONDED }
        assertEquals(1, Services.closed)
        assertFalse(restored.state.value.serviceChecking)
    }

    @Test
    fun notificationStopCancelsNodeProbeAndRejectsLateServiceContinuation() {
        enableServices()
        val vm = model(nodes.take(1))
        vm.testAll()
        startService()
        await { NodeCheckCoordinator.state.value.progress != null }
        val session = NodeCheckCoordinator.state.value.session
        service!!.onStartCommand(Intent(app, NodeCheckService::class.java).setAction(NodeCheckService.ACTION_STOP)
            .putExtra(NodeCheckService.EXTRA_SESSION, session), 0, 2)
        Nodes.permits.trySend(Unit)
        await { Nodes.cancelled == 1 }
        assertFalse(NodeCheckCoordinator.state.value.active)
        assertNull(NodeCheckCoordinator.state.value.progress)
        assertEquals(0, Services.entered)
        assertTrue(App.prefs.serviceChecks.isEmpty())
        assertTrue(shadowOf(service!!).isStoppedBySelf)
    }

    @Test
    fun stoppingServiceProbeClosesItAndDoesNotRestartAfterModelRecreation() {
        enableServices()
        val vm = model(nodes.take(1))
        vm.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { Services.entered == 1 }
        vm.cancelTest()
        await { Services.closed == 1 }
        vm.viewModelScope.cancel()
        val restored = model(nodes.take(1))
        Services.permits.trySend(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, Services.entered)
        assertFalse(restored.state.value.serviceChecking)
        assertEquals(ServiceVerdict.UNKNOWN, App.prefs.serviceChecks.values.single().verdict)
        assertEquals(ServiceReason.INCOMPLETE, App.prefs.serviceChecks.values.single().reason)
        assertFalse(ServiceChecks.due(App.prefs.serviceChecks.values.single()))
    }

    @Test
    fun serviceProgressCountsOnlyAddressesDueForChecking() {
        enableServices()
        App.prefs.settings = App.prefs.settings.copy(checkedServices = ServiceChecks.builtIn.map { it.kind }.toSet())
        val node = nodes.first()
        val cached = ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, System.currentTimeMillis())
        App.prefs.serviceChecks = ServiceChecks.builtIn.drop(1).associate {
            ServiceChecks.key("BLACK", node.id, it) to cached
        }
        val vm = model(listOf(node))
        vm.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { Services.entered == 1 }
        assertEquals(1, NodeCheckCoordinator.state.value.serviceTotal)
        Services.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active }
        assertEquals(1, NodeCheckCoordinator.state.value.serviceDone)
        assertEquals(1, NodeCheckCoordinator.state.value.serviceTotal)
        assertEquals(ServiceChecks.builtIn.map { ServiceChecks.key("BLACK", node.id, it) }.toSet(), App.prefs.serviceChecks.keys)
        ServiceChecks.builtIn.drop(1).forEach {
            assertEquals(cached, App.prefs.serviceChecks[ServiceChecks.key("BLACK", node.id, it)])
        }
    }

    @Test
    fun cancellationKeepsCompletedResultsAndMarksRemainingChecksIncomplete() {
        enableServices()
        App.prefs.settings = App.prefs.settings.copy(checkedServices = ServiceChecks.builtIn.map { it.kind }.toSet())
        val node = nodes.first()
        val vm = model(listOf(node))
        vm.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { Services.entered == 1 }
        Services.permits.trySend(Unit)
        await { NodeCheckCoordinator.state.value.serviceDone == 1 }
        vm.cancelTest()
        await { Services.closed == 1 }
        val results = ServiceChecks.builtIn.map { App.prefs.serviceChecks.getValue(ServiceChecks.key("BLACK", node.id, it)) }
        assertEquals(ServiceVerdict.RESPONDED, results.first().verdict)
        assertEquals(ServiceReason.PAGE, results.first().reason)
        assertTrue(results.drop(1).all { it.verdict == ServiceVerdict.UNKNOWN && it.reason == ServiceReason.INCOMPLETE })
        assertTrue(results.none { ServiceChecks.due(it) })
    }

    @Test
    fun cancelledServiceCleanupCannotClearNewProgressOrPublishItsLateResult() {
        enableServices()
        val cleanup = CompletableDeferred<Unit>()
        Services.cleanupGate = cleanup
        Services.cleanupGates += cleanup
        Services.lateResultAfterCleanup = true
        val vm = model(nodes.take(1))
        vm.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { Services.entered == 1 }
        NodeCheckCoordinator.cancelServices()
        assertFalse(NodeCheckCoordinator.state.value.services)
        Services.cleanupGate = null
        Services.lateResultAfterCleanup = false
        NodeCheckCoordinator.testNode(app, nodes.last(), App.prefs.settings, "BLACK", false, emptySet())
        Nodes.permits.trySend(Unit)
        await { NodeCheckCoordinator.state.value.services }
        assertEquals(1, Services.entered)
        cleanup.complete(Unit)
        await { Services.closed == 1 && Services.entered == 2 }
        assertTrue(NodeCheckCoordinator.state.value.services)
        assertEquals(0, NodeCheckCoordinator.state.value.serviceDone)
        val target = ServiceChecks.builtIn.first { it.kind == ServiceKind.CHATGPT }
        assertEquals(ServiceVerdict.UNKNOWN, App.prefs.serviceChecks[ServiceChecks.key("BLACK", nodes.first().id, target)]?.verdict)
        Services.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active }
        assertEquals(2, Services.closed)
        assertEquals(ServiceVerdict.RESPONDED, App.prefs.serviceChecks[ServiceChecks.key("BLACK", nodes.last().id, target)]?.verdict)
    }

    @Test
    fun failedServiceProbeReplacesOldPositiveResultForNewAndExistingModels() {
        enableServices()
        Services.fail = true
        val target = ServiceChecks.builtIn.first { it.kind == ServiceKind.CHATGPT }
        val key = ServiceChecks.key("BLACK", nodes.first().id, target)
        App.prefs.serviceChecks = mapOf(key to ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE,
            System.currentTimeMillis() - 3_600_000))
        val vm = model(nodes.take(1))
        vm.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { Services.closed == 1 && !NodeCheckCoordinator.state.value.active }
        await { vm.state.value.serviceChecks[key]?.verdict == ServiceVerdict.UNKNOWN }
        val restored = model(nodes.take(1))
        await { restored.state.value.serviceChecks[key]?.verdict == ServiceVerdict.UNKNOWN }
    }

    @Test
    fun androidTimeoutCancelsWorkReleasesWakeLockAndStopsService() {
        val vm = model()
        vm.testAll()
        startService()
        await { NodeCheckCoordinator.state.value.progress != null }
        val lock = wakeLock()
        service!!.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        await { Nodes.cancelled == 1 }
        assertEquals(NodeCheckFailure.TIMEOUT, NodeCheckCoordinator.state.value.failure)
        assertNull(NodeCheckCoordinator.state.value.progress)
        assertFalse(lock.isHeld)
        assertTrue(shadowOf(service!!).isStoppedBySelf)
        assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
    }

    @Test
    fun pendingNextSessionIsNotStoppedByThePreviousSessionObserver() {
        val observer = ObserverQueue()
        val first = NodeCheckCoordinator.testAll(app, nodes.take(1), App.prefs.settings, "BLACK", null, emptySet(), false)!!
        startService(observer)
        observer.drain()
        val firstSession = NodeCheckCoordinator.state.value.session
        val firstLock = wakeLock()
        Nodes.permits.trySend(Unit)
        await { first.isCompleted }
        assertFalse(NodeCheckCoordinator.state.value.active)
        val second = NodeCheckCoordinator.testAll(app, nodes.takeLast(1), App.prefs.settings, "BLACK", null, emptySet(), false)!!
        val secondSession = NodeCheckCoordinator.state.value.session
        assertNotEquals(firstSession, secondSession)
        observer.drain()
        assertFalse(shadowOf(service!!).isStoppedBySelf)
        assertTrue(firstLock.isHeld)
        val next = shadowOf(app).nextStartedService
        assertNotNull(next)
        assertEquals(secondSession, next!!.getLongExtra(NodeCheckService.EXTRA_SESSION, 0))
        service!!.onStartCommand(next, 0, 2)
        observer.drain()
        assertFalse(firstLock.isHeld)
        assertTrue(wakeLock().isHeld)
        assertTrue(second.isActive)
        assertEquals(secondSession, NodeCheckCoordinator.state.value.session)
        Nodes.permits.trySend(Unit)
        await { second.isCompleted }
        observer.drain()
        assertTrue(shadowOf(service!!).isStoppedBySelf)
        assertEquals(2, shadowOf(service!!).stopSelfResultId)
        assertFalse(NodeCheckCoordinator.state.value.active)
    }

    @Test
    fun deniedForegroundStartDoesNotRunProbesOrLeaveProgress() {
        val denied = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun startForegroundService(service: Intent): ComponentName? = throw SecurityException("denied")
        }
        assertNull(NodeCheckCoordinator.testAll(denied, nodes, App.prefs.settings, "BLACK", null, emptySet(), true))
        assertEquals(NodeCheckFailure.START, NodeCheckCoordinator.state.value.failure)
        assertFalse(NodeCheckCoordinator.state.value.active)
        assertNull(NodeCheckCoordinator.state.value.progress)
        assertEquals(0, Services.entered)
    }

    @Test
    fun completedFullCheckPublishesFirstFailuresWithoutChangingAutomaticDeadThreshold() {
        Nodes.delayMs = -1
        val vm = model()
        vm.testAll()
        startService()
        repeat(nodes.size) { Nodes.permits.trySend(Unit) }
        await { !NodeCheckCoordinator.state.value.active }
        assertEquals(nodes.map { it.id }.toSet(), App.prefs.fullNodeChecks["BLACK"]?.failed)
        assertTrue(NodeHealth.dead(App.prefs.nodeHealth, "BLACK").isEmpty())
    }

    @Test
    fun completedCheckWithProtectedFailurePublishesUnprotectedFailure() {
        App.prefs.fullNodeChecks = mapOf("BLACK" to FullNodeCheck(setOf("previous"), System.currentTimeMillis()))
        Nodes.delayMs = -1
        assertNotNull(NodeCheckCoordinator.testAll(app, nodes, App.prefs.settings, "BLACK", null,
            setOf(nodes.first().id), false))
        startService()
        repeat(nodes.size) { Nodes.permits.trySend(Unit) }
        await { !NodeCheckCoordinator.state.value.active }
        assertEquals(setOf(nodes.last().id), App.prefs.fullNodeChecks["BLACK"]?.failed)
        assertFalse(NodeHealth.results(App.prefs.nodeHealth, "BLACK").containsKey(nodes.first().id))
    }

    @Test
    fun cancellingPartialCheckPreservesPreviousCompletedGroup() {
        val previous = FullNodeCheck(setOf("previous"), System.currentTimeMillis())
        App.prefs.fullNodeChecks = mapOf("BLACK" to previous)
        Nodes.delayMs = -1
        val vm = model()
        vm.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { NodeCheckCoordinator.state.value.progress?.done == 1 }
        vm.cancelTest()
        await { Nodes.cancelled == 1 }
        assertEquals(previous, App.prefs.fullNodeChecks["BLACK"])
    }

    @Test
    fun changingNetworkModeDuringFullCheckDoesNotPublishMixedNetworkFailures() {
        Nodes.delayMs = -1
        val vm = model()
        vm.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { NodeCheckCoordinator.state.value.progress?.done == 1 }
        App.prefs.settings = App.prefs.settings.copy(netMode = NetModeChoice.WHITE)
        Nodes.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active }
        assertTrue(App.prefs.fullNodeChecks.isEmpty())
    }

    @Test
    fun manifestKeepsCheckingPrivateAndDeclaresDataSyncPermission() {
        val info = app.packageManager.getServiceInfo(ComponentName(app, NodeCheckService::class.java), 0)
        assertFalse(info.exported)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
        val permissions = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue("android.permission.FOREGROUND_SERVICE_DATA_SYNC" in permissions)
        assertTrue("android.permission.WAKE_LOCK" in permissions)
    }

    @Test
    fun networkModeChangeCancelsOldWorkAndIsolatesResultsAcrossRecreation() {
        val vm = model()
        vm.testAll()
        startService()
        Nodes.permits.trySend(Unit)
        await { vm.state.value.progress?.done == 1 }
        assertNull(NodeCheckCoordinator.testNode(app, nodes.last(), App.prefs.settings, "WHITE", false, emptySet()))
        assertNull(NodeCheckCoordinator.testAll(app, nodes, App.prefs.settings, "WHITE", null, emptySet(), false))
        vm.setNetMode(NetModeChoice.WHITE)
        assertNull(vm.state.value.progress)
        assertTrue(vm.state.value.results.isEmpty())
        await { Nodes.cancelled == 1 && coordinatorJobs().isEmpty() }
        assertTrue(App.prefs.fullNodeChecks.isEmpty())
        assertEquals(setOf(nodes.first().id), NodeHealth.results(App.prefs.nodeHealth, "BLACK").keys)
        assertTrue(NodeHealth.results(App.prefs.nodeHealth, "WHITE").isEmpty())
        val oldService = service!!
        vm.testNode(nodes.last())
        val next = shadowOf(app).nextStartedService
        assertNotNull(next)
        assertEquals("WHITE", NodeCheckCoordinator.state.value.mode)
        assertTrue(NodeCheckCoordinator.state.value.results.isEmpty())
        oldService.onStartCommand(next, 0, 2)
        Nodes.permits.trySend(Unit)
        await { !NodeCheckCoordinator.state.value.active }
        await { vm.state.value.results.keys == setOf(nodes.last().id) }
        vm.viewModelScope.cancel()
        val restored = model()
        assertEquals(setOf(nodes.last().id), restored.state.value.results.keys)
        restored.setNetMode(NetModeChoice.BLACK)
        assertEquals(setOf(nodes.first().id), restored.state.value.results.keys)
        assertNull(restored.state.value.progress)
        assertTrue(restored.state.value.checking.isEmpty())
        assertFalse(restored.state.value.serviceChecking)
        val again = model()
        assertEquals(setOf(nodes.first().id), again.state.value.results.keys)
        assertEquals(setOf(nodes.last().id), NodeHealth.results(App.prefs.nodeHealth, "WHITE").keys)
    }

    private fun coordinatorJobs(): Map<*, *> = NodeCheckCoordinator::class.java.getDeclaredField("jobs")
        .apply { isAccessible = true }.get(null) as Map<*, *>

    private fun enableServices() {
        Nodes.method = TestMethod.REAL
        App.prefs.settings = App.prefs.settings.copy(serviceChecksEnabled = true, checkedServices = setOf(ServiceKind.CHATGPT))
    }

    private fun model(selected: List<ProxyNode> = nodes): MainViewModel {
        val vm = MainViewModel(app)
        models += vm
        @Suppress("UNCHECKED_CAST")
        val state = MainViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(vm) as MutableStateFlow<UiState>
        state.value = state.value.copy(nodes = selected)
        return vm
    }

    private fun startService(observer: CoroutineDispatcher? = null) {
        var intent: Intent?
        do { intent = shadowOf(app).nextStartedService } while (intent != null && intent.component?.className != NodeCheckService::class.java.name)
        assertNotNull(intent)
        val controller = Robolectric.buildService(NodeCheckService::class.java)
        if (observer != null) {
            val field = NodeCheckService::class.java.getDeclaredField("scope").apply { isAccessible = true }
            (field.get(controller.get()) as CoroutineScope).cancel()
            field.set(controller.get(), CoroutineScope(SupervisorJob() + observer))
        }
        service = controller.create().get()
        assertEquals(android.app.Service.START_NOT_STICKY, service!!.onStartCommand(intent, 0, 1))
    }

    private fun wakeLock(): PowerManager.WakeLock = NodeCheckService::class.java.getDeclaredField("wakeLock")
        .apply { isAccessible = true }.get(service) as PowerManager.WakeLock

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("Condition did not become true", condition())
    }

    private class ObserverQueue : CoroutineDispatcher() {
        private val pending = java.util.ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { pending.addLast(block) }
        fun drain() { while (pending.isNotEmpty()) pending.removeFirst().run() }
    }

    @Implements(value = NodeTester::class, isInAndroidSdk = false)
    class Nodes {
        @Implementation
        fun userCheck(nodes: List<ProxyNode>, url: String, concurrency: Int, profiles: Map<String, NodeProfile>,
            method: ProbeMethod): Flow<TestResult> = testAll(nodes, url, concurrency, profiles)

        @Implementation
        fun testAll(nodes: List<ProxyNode>, url: String, concurrency: Int, profiles: Map<String, NodeProfile>): Flow<TestResult> {
            seenProfiles = profiles
            return testAll(nodes, url, concurrency)
        }

        @Implementation
        fun testAll(nodes: List<ProxyNode>, url: String, concurrency: Int): Flow<TestResult> = flow {
            try {
                onBatch()
                nodes.forEach {
                    nativeIds += it.id
                    permits.receive()
                    emit(TestResult(it.id, delays[it.id] ?: delayMs, method, reason = reason))
                }
            } finally {
                cancelled++
            }
        }

        @Implementation
        fun throughTunnel(node: ProxyNode, url: String, continuation: Continuation<TestResult?>): Any? = liveResult

        @Implementation
        fun measureLiveLatency(node: ProxyNode, url: String, continuation: Continuation<TestResult>): Any {
            onLive()
            return liveResult ?: TestResult(node.id, -1, TestMethod.REAL)
        }

        companion object {
            lateinit var permits: Channel<Unit>
            var method = TestMethod.TCP
            var delayMs = 25L
            var delays = emptyMap<String, Long>()
            var cancelled = 0
            val nativeIds = mutableListOf<String>()
            var liveResult: TestResult? = null
            var reason = TestFailure.UNKNOWN
            var onLive: () -> Unit = {}
            var onBatch: () -> Unit = {}
            var seenProfiles: Map<String, NodeProfile> = emptyMap()
        }
    }

    @Implements(value = ServiceProbe::class, isInAndroidSdk = false)
    class Services {
        @Implementation
        fun check(node: ProxyNode, profile: NodeProfile?, targets: List<ServiceTarget>,
            onResult: suspend (ServiceTarget, ServiceCheck) -> Unit, continuation: Continuation<Unit>): Any? {
            val suspended: suspend () -> Unit = {
                gate.withPermit {
                    entered++
                    val cleanup = cleanupGate
                    val lateResult = lateResultAfterCleanup
                    try {
                        if (!fail) for (target in targets) {
                            permits.receive()
                            onResult(target, ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, System.currentTimeMillis()))
                        }
                    } finally {
                        if (cleanup != null) withContext(NonCancellable) {
                            cleanup.await()
                            if (lateResult) onResult(targets.first(), ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, System.currentTimeMillis()))
                        }
                        closed++
                    }
                }
            }
            return suspended.startCoroutineUninterceptedOrReturn(continuation)
        }

        companion object {
            lateinit var permits: Channel<Unit>
            var entered = 0
            var closed = 0
            var fail = false
            var gate = Semaphore(1)
            var cleanupGate: CompletableDeferred<Unit>? = null
            val cleanupGates = mutableListOf<CompletableDeferred<Unit>>()
            var lateResultAfterCleanup = false
        }
    }
}

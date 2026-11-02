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
import app.farvater.data.FullNodeCheck
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
import app.farvater.ui.MainViewModel
import app.farvater.ui.UiState
import app.farvater.vpn.NodeCheckService
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
        assertEquals(4, App.prefs.serviceChecks.size)
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
        fun testAll(nodes: List<ProxyNode>, url: String, concurrency: Int): Flow<TestResult> = flow {
            try {
                nodes.forEach {
                    permits.receive()
                    emit(TestResult(it.id, delayMs, method))
                }
            } finally {
                cancelled++
            }
        }

        companion object {
            lateinit var permits: Channel<Unit>
            var method = TestMethod.TCP
            var delayMs = 25L
            var cancelled = 0
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

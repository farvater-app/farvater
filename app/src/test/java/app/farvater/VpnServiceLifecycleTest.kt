package app.farvater

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.parser.LinkParser
import app.farvater.core.model.ProxyNode
import app.farvater.engine.NodeTester
import app.farvater.engine.TestResult
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import app.farvater.engine.TunBridge
import app.farvater.engine.XrayEngine
import app.farvater.vpn.FarvaterVpnService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [VpnServiceLifecycleTest.Core::class, VpnServiceLifecycleTest.Bridge::class, VpnServiceLifecycleTest.Probe::class],
    instrumentedPackages = ["app.farvater.engine.XrayEngine", "app.farvater.engine.TunBridge", "app.farvater.engine.NodeTester"] , qualifiers = "ru")
class VpnServiceLifecycleTest {
    private lateinit var service: FarvaterVpnService
    private lateinit var queue: QueueDispatcher
    private lateinit var scope: CoroutineScope

    @Before
    fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        prepareRuntime(app)
        App.prefs.selectNode(LinkParser.parse("vless://test@node.example.test:443?security=tls")!!)
        service = Robolectric.buildService(FarvaterVpnService::class.java).create().get()
        queue = QueueDispatcher()
        scope = CoroutineScope(SupervisorJob() + queue)
        val field = FarvaterVpnService::class.java.getDeclaredField("scope").apply { isAccessible = true }
        (field.get(service) as CoroutineScope).cancel()
        field.set(service, scope)
        FarvaterVpnService::class.java.getDeclaredField("lifecycleDispatcher").apply { isAccessible = true }.set(service, queue)
        Core.starts = 0
        Core.stops = 0
        Core.onStart = null
        Core.onStop = null
    }

    @After
    fun finish() {
        Core.onStart = null
        Core.onStop = null
        service.onDestroy()
        queue.drain()
    }

    @Test
    fun stopPreventsQueuedStartBeforeDestroy() {
        service.onStartCommand(Intent().setAction(FarvaterVpnService.ACTION_START), 0, 1)
        service.onStartCommand(Intent().setAction(FarvaterVpnService.ACTION_STOP), 0, 2)
        queue.drain()
        assertEquals(0, Core.starts)
    }

    @Test
    fun newStartAfterStopRunsWithoutRevivingOldStart() {
        command(FarvaterVpnService.ACTION_START, 1)
        command(FarvaterVpnService.ACTION_STOP, 2)
        command(FarvaterVpnService.ACTION_START, 3)
        queue.drain()
        assertEquals(1, Core.starts)
    }

    @Test
    fun stopStartStopCancelsEveryQueuedGeneration() {
        command(FarvaterVpnService.ACTION_START, 1)
        command(FarvaterVpnService.ACTION_STOP, 2)
        command(FarvaterVpnService.ACTION_START, 3)
        command(FarvaterVpnService.ACTION_STOP, 4)
        queue.drain()
        assertEquals(0, Core.starts)
        command(FarvaterVpnService.ACTION_START, 5)
        queue.drain()
        assertEquals(1, Core.starts)
    }

    @Test
    fun stopAlsoCancelsQueuedSystemRestartAndItsModeCheck() {
        service.onStartCommand(null, 0, 1)
        command(FarvaterVpnService.ACTION_STOP, 2)
        queue.drain()
        assertEquals(0, Core.starts)
        val modeJob = FarvaterVpnService::class.java.getDeclaredField("modeJob").apply { isAccessible = true }
        assertNull(modeJob.get(service))
    }

    @Test
    fun acceptingStartDoesNotWaitForRunningConnectionLock() {
        val locked = CountDownLatch(1)
        val accepted = CountDownLatch(1)
        val acceptedWhileLocked = AtomicBoolean(false)
        val worker = thread {
            synchronized(service) {
                locked.countDown()
                acceptedWhileLocked.set(accepted.await(5, TimeUnit.SECONDS))
            }
        }
        try {
            assertTrue(locked.await(5, TimeUnit.SECONDS))
            command(FarvaterVpnService.ACTION_START, 1)
        } finally {
            accepted.countDown()
            worker.join(6000)
        }
        assertFalse(worker.isAlive)
        assertTrue(acceptedWhileLocked.get())
    }

    @Test
    fun acceptingStopDoesNotWaitForRunningConnectionLock() {
        val locked = CountDownLatch(1)
        val accepted = CountDownLatch(1)
        val acceptedWhileLocked = AtomicBoolean(false)
        val worker = thread {
            synchronized(service) {
                locked.countDown()
                acceptedWhileLocked.set(accepted.await(2, TimeUnit.SECONDS))
            }
        }
        try {
            assertTrue(locked.await(5, TimeUnit.SECONDS))
            command(FarvaterVpnService.ACTION_STOP, 1)
        } finally {
            accepted.countDown()
            worker.join(6000)
        }
        assertFalse(worker.isAlive)
        assertTrue(acceptedWhileLocked.get())
    }

    @Test
    fun staleAutomaticSwitchCannotReplaceCurrentConnection() {
        val old = App.prefs.lastNode!!
        val manual = old.copy(id = "manual", name = "manual")
        val candidate = old.copy(id = "candidate", name = "candidate")
        App.prefs.selectNode(manual)
        VpnBus.state.value = VpnState.Connected(manual, 123)
        val method = FarvaterVpnService::class.java.declaredMethods.single { it.name == "startVpn" }
        method.isAccessible = true
        val args = if (method.parameterCount == 3) arrayOf(candidate, true, old) else arrayOf(candidate, true, old, 0L)
        method.invoke(service, *args)
        assertEquals(0, Core.starts)
        assertEquals(manual, App.prefs.lastNode)
        assertEquals(manual, (VpnBus.state.value as VpnState.Connected).node)
    }

    @Test
    fun cancellingMonitorAlsoCancelsInFlightProbe() = runBlocking {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        FarvaterVpnService::class.java.getDeclaredField("scope").apply { isAccessible = true }.set(service, scope)
        Probe.entered = CountDownLatch(1)
        Probe.cancelled = CountDownLatch(1)
        val method = FarvaterVpnService::class.java.getDeclaredMethod("monitorLoop", ProxyNode::class.java, Continuation::class.java)
            .apply { isAccessible = true }
        val monitor = scope.launch {
            suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
                method.invoke(service, App.prefs.lastNode!!, continuation)
            }
        }
        try {
            assertTrue(Probe.entered.await(8, TimeUnit.SECONDS))
            monitor.cancel()
            assertTrue("Проба пережила отмену монитора", Probe.cancelled.await(2, TimeUnit.SECONDS))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun staleRevocationCleanupDoesNotStopNewerCore() {
        command(FarvaterVpnService.ACTION_START, 1)
        queue.drain()
        service.onRevoke()
        command(FarvaterVpnService.ACTION_START, 2)
        queue.runLast()
        val stops = Core.stops
        queue.drain()
        assertEquals(stops, Core.stops)
    }

    @Test
    fun failedStartCleanupCannotStopNewRequest() {
        val cleaning = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stoppedBeforeNewStart = AtomicBoolean(true)
        Core.onStop = {
            if (Core.stops == 2) {
                cleaning.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        Core.onStart = {
            if (Core.starts == 2) stoppedBeforeNewStart.set(org.robolectric.Shadows.shadowOf(service).isStoppedBySelf)
        }
        command(FarvaterVpnService.ACTION_START, 1)
        val worker = thread { queue.drain() }
        try {
            assertTrue(cleaning.await(5, TimeUnit.SECONDS))
            command(FarvaterVpnService.ACTION_START, 2)
        } finally {
            release.countDown()
            worker.join(6000)
        }
        assertFalse(worker.isAlive)
        assertEquals(2, Core.starts)
        assertFalse(stoppedBeforeNewStart.get())
    }

    @Test fun stopReturnsWhileNativeStartIsBlocked() = blockedNativeStart { command(FarvaterVpnService.ACTION_STOP, 2) }
    @Test fun revokeReturnsWhileNativeStartIsBlocked() = blockedNativeStart { service.onRevoke() }
    @Test fun destroyReturnsWhileNativeStartIsBlocked() = blockedNativeStart { service.onDestroy() }

    private fun blockedNativeStart(cancel: () -> Unit) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val accepted = CountDownLatch(1)
        val returnedBeforeCore = AtomicBoolean(false)
        Core.onStart = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        command(FarvaterVpnService.ACTION_START, 1)
        val worker = thread { queue.drain() }
        val timer = thread {
            check(entered.await(5, TimeUnit.SECONDS))
            returnedBeforeCore.set(accepted.await(2, TimeUnit.SECONDS))
            release.countDown()
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            cancel()
            accepted.countDown()
        } finally {
            timer.join(6000)
            release.countDown()
            worker.join(6000)
        }
        assertTrue(returnedBeforeCore.get())
        assertFalse(worker.isAlive)
        assertEquals(VpnState.Idle, VpnBus.state.value)
        assertFalse(App.prefs.vpnWasOn)
    }

    private fun command(action: String, id: Int) = service.onStartCommand(Intent().setAction(action), 0, id)

    @Test
    fun destroyCancelsQueuedStart() {
        service.onStartCommand(Intent().setAction(FarvaterVpnService.ACTION_START), 0, 1)
        service.onDestroy()
        queue.drain()
        assertEquals(0, Core.starts)
        assertFalse(scope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
    }

    private class QueueDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { synchronized(tasks) { tasks.add(block) } }
        fun drain() { while (true) (synchronized(tasks) { tasks.pollFirst() } ?: return).run() }
        fun runLast() { synchronized(tasks) { tasks.pollLast() }?.run() }
    }

    @Implements(value = XrayEngine::class, isInAndroidSdk = false)
    class Core {
        @Implementation(methodName = "start-IoAF18A")
        fun start(config: String): Result<Unit> {
            starts++
            onStart?.invoke()
            return Result.failure(IllegalStateException("test core must not start"))
        }

        @Implementation
        fun stop() {
            stops++
            onStop?.invoke()
        }

        companion object {
            var starts = 0
            var stops = 0
            var onStart: (() -> Unit)? = null
            var onStop: (() -> Unit)? = null
        }
    }

    @Implements(value = TunBridge::class, isInAndroidSdk = false)
    class Bridge {
        @Implementation
        fun stop() = Unit

        @Implementation
        fun stats(): LongArray? = null
    }

    @Implements(value = NodeTester::class, isInAndroidSdk = false)
    class Probe {
        @Implementation
        fun throughTunnel(node: ProxyNode, url: String, continuation: Continuation<TestResult?>): Any? {
            val suspended: suspend () -> TestResult? = {
                entered.countDown()
                try {
                    awaitCancellation()
                } finally {
                    cancelled.countDown()
                }
            }
            return suspended.startCoroutineUninterceptedOrReturn(continuation)
        }

        companion object {
            lateinit var entered: CountDownLatch
            lateinit var cancelled: CountDownLatch
        }
    }
}

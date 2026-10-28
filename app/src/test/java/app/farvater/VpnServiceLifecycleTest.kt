package app.farvater

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.parser.LinkParser
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
@Config(sdk = [35], application = Application::class, shadows = [VpnServiceLifecycleTest.Core::class, VpnServiceLifecycleTest.Bridge::class],
    instrumentedPackages = ["app.farvater.engine.XrayEngine", "app.farvater.engine.TunBridge"])
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
        Core.starts = 0
    }

    @After
    fun finish() {
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
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    @Implements(value = XrayEngine::class, isInAndroidSdk = false)
    class Core {
        @Implementation(methodName = "start-IoAF18A")
        fun start(config: String): Result<Unit> {
            starts++
            return Result.failure(IllegalStateException("test core must not start"))
        }

        @Implementation
        fun stop() = Unit

        companion object { var starts = 0 }
    }

    @Implements(value = TunBridge::class, isInAndroidSdk = false)
    class Bridge {
        @Implementation
        fun stop() = Unit
    }
}

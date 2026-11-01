package app.farvater

import android.app.Application
import app.farvater.engine.TunBridge
import hev.sockstun.TProxyService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class,
    shadows = [TunBridgeConcurrencyTest.Native::class], instrumentedPackages = ["hev.sockstun"])
class TunBridgeConcurrencyTest {
    @Test fun stopWaitsForStatsAndLaterReadsNeverReachStoppedNativeState() {
        Native.entered = CountDownLatch(1)
        Native.release = CountDownLatch(1)
        Native.stopped = CountDownLatch(1)
        Native.reads = 0
        TunBridge::class.java.getDeclaredField("running").apply { isAccessible = true }.setBoolean(null, true)
        val reader = thread { TunBridge.stats() }
        var stopper: Thread? = null
        try {
            assertTrue(Native.entered.await(5, TimeUnit.SECONDS))
            stopper = thread { TunBridge.stop() }
            assertFalse(Native.stopped.await(200, TimeUnit.MILLISECONDS))
            Native.release.countDown()
            reader.join(5000)
            stopper.join(5000)
            assertFalse(reader.isAlive)
            assertFalse(stopper.isAlive)
            assertEquals(0L, Native.stopped.count)
            assertNull(TunBridge.stats())
            assertEquals(1, Native.reads)
        } finally {
            Native.release.countDown()
            reader.join(5000)
            stopper?.join(5000)
            TunBridge.stop()
        }
    }

    @Implements(value = TProxyService::class, isInAndroidSdk = false)
    class Native {
        companion object {
            lateinit var entered: CountDownLatch
            lateinit var release: CountDownLatch
            lateinit var stopped: CountDownLatch
            var reads = 0

            @JvmStatic @Implementation fun TProxyGetStats(): LongArray {
                reads++
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return longArrayOf(0, 1, 0, 2)
            }

            @JvmStatic @Implementation fun TProxyStopService(): Boolean {
                stopped.countDown()
                return true
            }
        }
    }
}

package app.farvater

import android.app.Application
import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import app.farvater.engine.NodeTester
import app.farvater.engine.XrayEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [NodeTesterConcurrencyTest.Core::class],
    instrumentedPackages = ["app.farvater.engine.XrayEngine"])
class NodeTesterConcurrencyTest {
    @Test
    fun configuredConcurrencyIsClampedToOneThroughThirtyTwo() {
        exercise(concurrency = 0, sameSni = false, sameHost = false, expected = 1)
        exercise(concurrency = 100, sameSni = false, sameHost = false, expected = 32)
    }

    @Test
    fun sameSniLimitsCoreChecksToSix() {
        exercise(concurrency = 16, sameSni = true, sameHost = false, expected = 6)
    }

    @Test
    fun sameHostLimitsCoreChecksToThree() {
        exercise(concurrency = 16, sameSni = false, sameHost = true, expected = 3)
    }

    @Test
    fun cancellationInterruptsActiveChecksAndDoesNotPublishResults() = runBlocking {
        Core.reset(4)
        val result = async(Dispatchers.Default) { NodeTester.testAll(nodes(false, false), "https://example.test/", 4).toList() }
        try {
            assertTrue(Core.entered.await(5, TimeUnit.SECONDS))
            result.cancelAndJoin()
            assertTrue(result.isCancelled)
            assertEquals(0, Core.active.get())
        } finally {
            Core.release.countDown()
            result.cancelAndJoin()
        }
    }

    @Test
    fun emptyListCompletesWithoutCoreCalls() = runBlocking {
        Core.reset(1)
        assertTrue(NodeTester.testAll(emptyList(), "https://example.test/", 16).toList().isEmpty())
        assertEquals(0, Core.peak.get())
    }

    private fun exercise(concurrency: Int, sameSni: Boolean, sameHost: Boolean, expected: Int, maximum: Int = expected) = runBlocking {
        Core.reset(expected)
        val result = async(Dispatchers.Default) { NodeTester.testAll(nodes(sameSni, sameHost), "https://example.test/", concurrency).toList() }
        try {
            assertTrue(Core.entered.await(5, TimeUnit.SECONDS))
            if (sameHost) assertFalse(Core.overLimit.await(200, TimeUnit.MILLISECONDS))
            Core.release.countDown()
            val completed = result.await()
            assertEquals(40, completed.size)
            assertEquals(40, completed.map { it.nodeId }.toSet().size)
            assertTrue(completed.all { it.alive })
            assertTrue("peak=${Core.peak.get()}, maximum=$maximum", Core.peak.get() <= maximum)
            assertEquals(0, Core.active.get())
        } finally {
            Core.release.countDown()
            result.cancelAndJoin()
        }
    }

    private fun nodes(sameSni: Boolean, sameHost: Boolean) = List(40) { index ->
        ProxyNode("n$index", Protocol.HYSTERIA2, "n$index", if (sameHost) "node.example.test" else "node$index.example.test", 443,
            "test", params = mapOf("sni" to if (sameSni) "sni.example.test" else "sni$index.example.test"), raw = "hy2://test")
    }

    @Implements(value = XrayEngine::class, isInAndroidSdk = false)
    class Core {
        @Implementation
        fun isAvailable() = true

        @Implementation
        fun measureDelay(config: String, url: String): Long {
            val count = active.incrementAndGet()
            peak.accumulateAndGet(count, ::maxOf)
            if (count > limit) overLimit.countDown()
            entered.countDown()
            try {
                check(release.await(10, TimeUnit.SECONDS))
                return 10
            } finally {
                active.decrementAndGet()
            }
        }

        companion object {
            val active = AtomicInteger()
            val peak = AtomicInteger()
            lateinit var entered: CountDownLatch
            lateinit var release: CountDownLatch
            lateinit var overLimit: CountDownLatch
            var limit = 0

            fun reset(count: Int) {
                active.set(0)
                peak.set(0)
                entered = CountDownLatch(count)
                release = CountDownLatch(1)
                overLimit = CountDownLatch(1)
                limit = count
            }
        }
    }
}

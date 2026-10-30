package app.farvater

import app.farvater.engine.ProbeLaunch
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ProbeLaunchTest {
    @Test
    fun lateNativeStartupIsClosedAfterCallerTimeout() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val resource = Any()
        val acquiring = async(Dispatchers.Default) {
            ProbeLaunch.await({
                started.countDown()
                while (release.count > 0) runCatching { release.await() }
                resource
            }, { if (it === resource) closed.countDown() }, timeoutMs = 100)
        }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertNull(withTimeout(2_000) { acquiring.await() })
        } finally { release.countDown() }
        assertTrue(closed.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun cancellationClosesAbandonedNativeStartup() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val acquiring = launch(Dispatchers.Default) {
            ProbeLaunch.await({
                started.countDown()
                while (release.count > 0) runCatching { release.await() }
                Any()
            }, { if (it != null) closed.countDown() })
        }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            withTimeout(2_000) { acquiring.cancelAndJoin() }
        } finally { release.countDown() }
        assertTrue(closed.await(2, TimeUnit.SECONDS))
    }
}

package app.farvater

import android.app.Application
import android.system.ErrnoException
import android.system.OsConstants
import app.farvater.data.NodeCheck
import app.farvater.data.NodeHealth
import app.farvater.engine.TestFailure
import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import app.farvater.engine.XrayEngine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLHandshakeException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NodeFailureTest {
    @Test
    fun typedErrorsSurviveReflectionAndFutureWrappers() {
        val refused = ConnectException("details are not classified").apply {
            initCause(ErrnoException("connect", OsConstants.ECONNREFUSED))
        }
        for ((error, expected) in listOf(
            SocketTimeoutException() to TestFailure.TIMEOUT,
            TimeoutException() to TestFailure.TIMEOUT,
            refused to TestFailure.REFUSED,
            SSLHandshakeException("details are not classified") to TestFailure.TLS,
            ConnectException("refused is not proof without errno") to TestFailure.CONNECTION,
            IOException("TLS timeout refused") to TestFailure.UNKNOWN,
        )) {
            assertEquals(expected, TestFailure.from(error))
            val task = CompletableFuture<Long>().apply { completeExceptionally(InvocationTargetException(error)) }
            val result = XrayEngine.awaitDelay(task)
            assertEquals(-1L, result.delayMs)
            assertEquals(expected, result.reason)
        }
    }

    @Test
    fun externalCoreTimeoutCancelsTheTaskAndKeepsItsReason() {
        var interrupted = false
        val task = object : CompletableFuture<Long>() {
            override fun get(timeout: Long, unit: TimeUnit): Long = throw TimeoutException()
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                interrupted = mayInterruptIfRunning
                return super.cancel(mayInterruptIfRunning)
            }
        }
        val result = XrayEngine.awaitDelay(task)
        assertTrue(task.isCancelled)
        assertTrue(interrupted)
        assertEquals(TestFailure.TIMEOUT, result.reason)
    }

    @Test
    fun negativeCoreResultStaysUnknownAndSuccessKeepsItsDelay() {
        assertEquals(TestFailure.UNKNOWN, XrayEngine.awaitDelay(CompletableFuture.completedFuture(-1L)).reason)
        val positive = XrayEngine.awaitDelay(CompletableFuture.completedFuture(120L))
        assertEquals(120L, positive.delayMs)
        assertEquals(TestFailure.UNKNOWN, positive.reason)
    }

    @Test
    fun legacyJsonWithoutReasonRemainsReadable() {
        val check = Json.decodeFromString<NodeCheck>("""{"delayMs":-1,"method":"TCP","at":1000,"fails":1}""")
        val result = Json.decodeFromString<TestResult>("""{"nodeId":"node","delayMs":-1,"method":"REAL","at":1000}""")
        assertEquals(TestFailure.UNKNOWN, check.reason)
        assertEquals(TestFailure.UNKNOWN, result.reason)
    }

    @Test
    fun reasonPersistsInHealthAndSuccessClearsPreviousFailure() {
        val failed = TestResult("node", -1, TestMethod.TCP, 1000, TestFailure.REFUSED)
        val history = NodeHealth.record(emptyMap(), "BLACK", listOf(failed), now = 1000)
        val restored = Json.decodeFromString<Map<String, NodeCheck>>(Json.encodeToString(history))
        assertEquals(TestFailure.REFUSED, NodeHealth.results(restored, "BLACK", now = 1000)["node"]?.reason)
        val recovered = NodeHealth.record(restored, "BLACK", listOf(TestResult("node", 80, TestMethod.REAL, 2000)), now = 2000)
        val result = NodeHealth.results(recovered, "BLACK", now = 2000).getValue("node")
        assertTrue(result.alive)
        assertEquals(TestFailure.UNKNOWN, result.reason)
        assertTrue(NodeHealth.dead(recovered, "BLACK", now = 2000).isEmpty())
    }

    @Test
    fun interruptionRemainsCancellationRatherThanATimeout() {
        val task = object : CompletableFuture<Long>() {
            override fun get(timeout: Long, unit: TimeUnit): Long = throw InterruptedException()
        }
        try {
            assertEquals(TestFailure.UNKNOWN, XrayEngine.awaitDelay(task).reason)
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(task.isCancelled)
        } finally {
            Thread.interrupted()
        }
    }
}

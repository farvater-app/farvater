package app.farvater

import app.farvater.engine.LeasedTask
import app.farvater.engine.NativeMeasurements
import app.farvater.engine.NativeProbeBudget
import app.farvater.engine.NativeProbeHandle
import app.farvater.engine.ProbeLane
import app.farvater.engine.TestFailure
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class NativeProbeBudgetTest {
    @Test
    fun bulkMeasurementsLeaveCapacityForATemporaryProbe() {
        val budget = NativeProbeBudget(2)
        val runner = NativeMeasurements(budget, timeoutMs = 50, admissionMs = 10)
        val release = CountDownLatch(1)
        try {
            assertEquals(TestFailure.TIMEOUT, runner.measure {
                while (release.count > 0) runCatching { release.await() }
                10L
            }.reason)
            assertEquals(TestFailure.BUSY, runner.measure { 20L }.reason)
            val probe = budget.acquire(ProbeLane.NORMAL, 0)
            assertNotNull(probe)
            probe!!.close()
        } finally {
            release.countDown()
            runner.close()
        }
    }

    @Test
    fun timedOutNativeCallKeepsItsSlotAndRecoveryHasIndependentCapacity() {
        val budget = NativeProbeBudget(1)
        val runner = NativeMeasurements(budget, timeoutMs = 100, admissionMs = 20)
        val caller = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        try {
            val first = caller.submit<app.farvater.engine.DelayMeasurement> {
                runner.measure {
                    entered.countDown()
                    while (release.count > 0) runCatching { release.await() }
                    returned.countDown()
                    20L
                }
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertEquals(TestFailure.TIMEOUT, first.get(2, TimeUnit.SECONDS).reason)
            val calls = AtomicInteger()
            assertEquals(TestFailure.BUSY, runner.measure { calls.incrementAndGet(); 10L }.reason)
            assertEquals(0, calls.get())
            assertEquals(15L, runner.measure(ProbeLane.RECOVERY) { 15L }.delayMs)
            release.countDown()
            assertTrue(returned.await(2, TimeUnit.SECONDS))
            val lease = budget.acquire(ProbeLane.NORMAL, 2_000)
            assertNotNull(lease)
            lease!!.close()
            assertEquals(10L, runner.measure { 10L }.delayMs)
        } finally {
            release.countDown()
            runner.close()
            caller.shutdownNow()
        }
    }

    @Test
    fun cancellationBeforeStartReleasesOnceAndNeverInvokesNativeCode() {
        val budget = NativeProbeBudget(1)
        val lease = budget.acquire(ProbeLane.NORMAL, 0)!!
        val calls = AtomicInteger()
        val task = LeasedTask(lease) { calls.incrementAndGet() }
        assertTrue(task.cancel(true))
        val replacement = budget.acquire(ProbeLane.NORMAL, 0)
        assertNotNull(replacement)
        task.run()
        lease.close()
        assertEquals(0, calls.get())
        assertNull(budget.acquire(ProbeLane.NORMAL, 0))
        replacement!!.close()
    }

    @Test
    fun probeSlotIsHeldUntilStopReturnsAndCloseIsIdempotent() {
        val budget = NativeProbeBudget(1)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val handle = NativeProbeHandle(Any(), budget.acquire(ProbeLane.NORMAL, 0)!!) {
            calls.incrementAndGet()
            entered.countDown()
            release.await()
        }
        val closing = thread { handle.close() }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertNull(budget.acquire(ProbeLane.NORMAL, 0))
            handle.close()
            assertEquals(1, calls.get())
        } finally {
            release.countDown()
            closing.join(2_000)
        }
        val next = budget.acquire(ProbeLane.NORMAL, 0)
        assertNotNull(next)
        next!!.close()
    }

    @Test
    fun failedStopDoesNotPretendTheNativeResourceIsGone() {
        val budget = NativeProbeBudget(1)
        val handle = NativeProbeHandle(Any(), budget.acquire(ProbeLane.NORMAL, 0)!!) { error("synthetic stop failure") }
        handle.close()
        handle.close()
        assertNull(budget.acquire(ProbeLane.NORMAL, 0))
        budget.acquire(ProbeLane.RECOVERY, 0)!!.close()
    }
}

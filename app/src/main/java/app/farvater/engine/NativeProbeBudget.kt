package app.farvater.engine

import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal enum class ProbeLane { NORMAL, RECOVERY }
internal class ProbeDeferredException : Exception()

internal class NativeProbeBudget(val normalLimit: Int) {
    init { require(normalLimit in 1..8) }
    private val normal = Semaphore(normalLimit, true)
    private val recovery = Semaphore(1, true)
    val measurementLimit = (normalLimit - 1).coerceAtLeast(1)
    private val measurements = Semaphore(measurementLimit, true)

    fun acquireMeasurement(lane: ProbeLane, waitMs: Long): Lease? {
        if (lane == ProbeLane.RECOVERY) return acquire(lane, waitMs)
        val started = System.nanoTime()
        if (!measurements.tryAcquire(waitMs, TimeUnit.MILLISECONDS)) return null
        var transferred = false
        try {
            val remaining = (waitMs - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).coerceAtLeast(0)
            val slot = acquire(lane, remaining) ?: return null
            transferred = true
            return Lease { slot.close(); measurements.release() }
        } finally {
            if (!transferred) measurements.release()
        }
    }

    fun acquire(lane: ProbeLane, waitMs: Long): Lease? {
        val gate = if (lane == ProbeLane.RECOVERY) recovery else normal
        if (!gate.tryAcquire(waitMs, TimeUnit.MILLISECONDS)) return null
        return Lease { gate.release() }
    }

    class Lease internal constructor(private val release: () -> Unit) : AutoCloseable {
        private val released = AtomicBoolean()
        override fun close() {
            if (released.compareAndSet(false, true)) release()
        }
    }
}

internal class LeasedTask<T>(private val lease: NativeProbeBudget.Lease, action: () -> T) : FutureTask<T>(Callable(action)) {
    private val entered = AtomicBoolean()
    override fun run() {
        if (!entered.compareAndSet(false, true)) return
        try { super.run() } finally { lease.close() }
    }
    override fun done() {
        if (!entered.get()) lease.close()
    }
}

internal class NativeProbeHandle<T>(val value: T, private val lease: NativeProbeBudget.Lease, private val stop: (T) -> Unit) {
    private val closing = AtomicBoolean()
    @Volatile private var closed = false

    fun release(schedule: (() -> Unit) -> Unit) {
        if (!close()) schedule { release(schedule) }
    }

    fun close(): Boolean {
        if (closed || !closing.compareAndSet(false, true)) return closed
        return try {
            if (closed) return true
            stop(value)
            lease.close()
            closed = true
            true
        } catch (_: Exception) {
            false
        } finally {
            closing.set(false)
        }
    }
}

internal class NativeMeasurements(private val budget: NativeProbeBudget, private val timeoutMs: Long = 8_000,
    private val admissionMs: Long = 8_000) : AutoCloseable {
    private fun executor(size: Int, name: String) = ThreadPoolExecutor(size, size, 0, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(size), { task -> Thread(task, name).apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private val normal = executor(budget.normalLimit, "farvater-measure")
    private val recovery = executor(1, "farvater-recovery-measure")

    fun measure(lane: ProbeLane = ProbeLane.NORMAL, action: () -> Long): DelayMeasurement {
        val lease = try { budget.acquireMeasurement(lane, admissionMs) } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return DelayMeasurement(-1)
        } ?: return DelayMeasurement(-1, TestFailure.BUSY)
        val executor = if (lane == ProbeLane.RECOVERY) recovery else normal
        val task = LeasedTask(lease, action)
        return try {
            executor.execute(task)
            DelayMeasurement(task.get(timeoutMs, TimeUnit.MILLISECONDS))
        } catch (error: Exception) {
            task.cancel(true)
            executor.remove(task)
            if (error is InterruptedException) Thread.currentThread().interrupt()
            DelayMeasurement(-1, if (error is java.util.concurrent.RejectedExecutionException) TestFailure.BUSY else TestFailure.from(error))
        }
    }

    override fun close() {
        listOf(normal, recovery).forEach { executor ->
            executor.shutdownNow().filterIsInstance<FutureTask<*>>().forEach { it.cancel(true) }
        }
    }
}

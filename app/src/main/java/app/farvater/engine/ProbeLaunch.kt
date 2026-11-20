package app.farvater.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException

internal object ProbeLaunch {
    private data class Started<T>(val value: T?)
    private fun executor(name: String) = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(8), { Thread(it, name).apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private val starts = executor("farvater-probe-start")
    private val recoveryStarts = executor("farvater-recovery-start")
    private val recoveryCleanup = executor("farvater-recovery-close")
    private val cleanup = executor("farvater-probe-close")

    suspend fun <T> await(start: () -> T?, close: (T?) -> Unit, timeoutMs: Long = 4_000, deferOnTimeout: Boolean = false, recovery: Boolean = false): T? {
        val startPool = if (recovery) recoveryStarts else starts
        val closePool = if (recovery) recoveryCleanup else cleanup
        val resource = AtomicReference<T?>()
        var transferred = false
        fun discard() {
            val abandoned = resource.getAndSet(null) ?: return
            closePool.execute { close(abandoned) }
        }
        try {
            val acquired = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Started<T>> { continuation ->
                    try {
                        val task = startPool.submit {
                            val outcome = runCatching(start)
                            val failure = outcome.exceptionOrNull()
                            if (failure is ProbeDeferredException) {
                                continuation.resumeWithException(failure)
                                return@submit
                            }
                            val started = outcome.getOrNull()
                            resource.set(started)
                            continuation.resume(Started(started)) { _, _, _ -> discard() }
                        }
                        continuation.invokeOnCancellation {
                            task.cancel(true)
                            startPool.remove(task as Runnable)
                        }
                    } catch (_: RejectedExecutionException) {
                        if (deferOnTimeout) continuation.resumeWithException(ProbeDeferredException())
                        else continuation.resume(Started(null)) { _, _, _ -> }
                    }
                }
            }
            if (acquired == null && deferOnTimeout) throw ProbeDeferredException()
            transferred = acquired?.value != null
            return acquired?.value
        } finally {
            if (!transferred) discard()
        }
    }
}

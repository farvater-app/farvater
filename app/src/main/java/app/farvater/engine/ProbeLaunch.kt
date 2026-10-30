package app.farvater.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

internal object ProbeLaunch {
    private val starts = Executors.newSingleThreadExecutor { Thread(it, "farvater-probe-start").apply { isDaemon = true } }
    private val cleanup = Executors.newSingleThreadExecutor { Thread(it, "farvater-probe-close").apply { isDaemon = true } }

    suspend fun <T> await(start: () -> T?, close: (T?) -> Unit, timeoutMs: Long = 4_000): T? {
        val resource = AtomicReference<T?>()
        var transferred = false
        fun discard() { cleanup.execute { resource.getAndSet(null)?.let(close) } }
        try {
            val acquired = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val task = starts.submit {
                        val started = runCatching(start).getOrNull()
                        resource.set(started)
                        continuation.resume(started) { _, _, _ -> discard() }
                    }
                    continuation.invokeOnCancellation { task.cancel(true) }
                }
            }
            transferred = acquired != null
            return acquired
        } finally {
            if (!transferred) discard()
        }
    }
}

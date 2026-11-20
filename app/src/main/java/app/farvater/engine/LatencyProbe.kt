package app.farvater.engine

import app.farvater.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal object LatencyProbe {
    private const val MAX_BODY = 256 * 1024

    suspend fun measure(base: OkHttpClient, url: String, timeoutMs: Long = 8_000): DelayMeasurement {
        val pool = ConnectionPool(1, 1, TimeUnit.MINUTES)
        val client = base.newBuilder().connectionPool(pool).followSslRedirects(false)
            .retryOnConnectionFailure(false).build()
        var best: Long? = null
        var failure = TestFailure.UNKNOWN
        try {
            val completed = withTimeoutOrNull(timeoutMs) {
                repeat(2) {
                    try {
                        val start = System.nanoTime()
                        val ok = client.newCall(Request.Builder().url(url).build()).await { response ->
                            if (response.code != 200 && response.code != 204) return@await false
                            val body = response.body ?: return@await true
                            if (body.contentLength() > MAX_BODY) return@await false
                            val stream = body.byteStream()
                            val buffer = ByteArray(8 * 1024)
                            var total = 0
                            while (total <= MAX_BODY) {
                                val count = stream.read(buffer, 0, minOf(buffer.size, MAX_BODY + 1 - total))
                                if (count < 0) break
                                total += count
                            }
                            total <= MAX_BODY
                        }
                        if (ok) {
                            val elapsed = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
                            best = minOf(best ?: Long.MAX_VALUE, elapsed)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        failure = TestFailure.from(error)
                    }
                }
                true
            }
            return best?.let { DelayMeasurement(it) }
                ?: DelayMeasurement(-1, if (completed == null) TestFailure.TIMEOUT else failure)
        } finally { pool.evictAll() }
    }
}

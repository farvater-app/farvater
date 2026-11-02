package app.farvater.engine

import android.system.ErrnoException
import android.system.OsConstants
import kotlinx.serialization.Serializable
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

@Serializable
enum class TestFailure {
    UNKNOWN, TIMEOUT, REFUSED, TLS, CONNECTION;

    companion object {
        fun from(error: Throwable): TestFailure {
            val causes = ArrayList<Throwable>(8)
            var current: Throwable? = error
            repeat(8) {
                val next = current ?: return@repeat
                causes += next
                current = next.cause
            }
            return when {
                causes.any { it is SocketTimeoutException || it is TimeoutException } -> TIMEOUT
                causes.any { it is SSLException } -> TLS
                causes.any { it is ErrnoException && it.errno == OsConstants.ECONNREFUSED } -> REFUSED
                causes.any { it is ConnectException } -> CONNECTION
                else -> UNKNOWN
            }
        }
    }
}

data class DelayMeasurement(val delayMs: Long, val reason: TestFailure = TestFailure.UNKNOWN)

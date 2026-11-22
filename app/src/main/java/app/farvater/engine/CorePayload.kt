package app.farvater.engine

import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object CorePayload {
    private const val INLINE_CHARS = 8192
    private val writers = ThreadPoolExecutor(0, 12, 30, TimeUnit.SECONDS,
        java.util.concurrent.SynchronousQueue(), { task -> Thread(task, "farvater-config").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())

    fun <T> send(text: String, call: (Bundle) -> T): T {
        val data = Bundle()
        if (text.length <= INLINE_CHARS) {
            data.putString("text", text)
            return call(data)
        }
        val (read, write) = ParcelFileDescriptor.createReliablePipe()
        return read.use {
            data.putInt("chars", text.length)
            data.putParcelable("pipe", read)
            try {
                writers.execute {
                    try {
                        ParcelFileDescriptor.AutoCloseOutputStream(write).bufferedWriter().use { it.write(text) }
                    } catch (_: IOException) {
                        runCatching { write.closeWithError("Configuration transfer interrupted") }
                    }
                }
            } catch (error: java.util.concurrent.RejectedExecutionException) {
                write.close()
                throw IOException("Configuration transfer busy", error)
            }
            call(data)
        }
    }

    @Suppress("DEPRECATION")
    fun receive(data: Bundle): String {
        data.getString("text")?.let { return it }
        val fd = requireNotNull(data.getParcelable<ParcelFileDescriptor>("pipe"))
        return ParcelFileDescriptor.AutoCloseInputStream(fd).use { stream ->
            val expected = data.getInt("chars", -1)
            require(expected > INLINE_CHARS)
            val reader = stream.bufferedReader()
            val text = StringBuilder(minOf(expected, 65536))
            val buffer = CharArray(8192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (count > expected - text.length) throw IOException("Invalid configuration length")
                text.append(buffer, 0, count)
            }
            fd.checkError()
            if (text.length != expected) throw IOException("Incomplete configuration")
            text.toString()
        }
    }
}

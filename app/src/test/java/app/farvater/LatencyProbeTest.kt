package app.farvater

import app.farvater.engine.LatencyProbe
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class LatencyProbeTest {
    @Test
    fun bothRequestsReadTheBodyAndReuseTheSameConnection() = runBlocking {
        val ports = CopyOnWriteArrayList<Int>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/probe") { exchange ->
            ports += exchange.remoteAddress.port
            exchange.sendResponseHeaders(200, 16)
            exchange.responseBody.use {
                it.write(ByteArray(8))
                it.flush()
                Thread.sleep(150)
                it.write(ByteArray(8))
            }
        }
        server.start()
        try {
            val result = LatencyProbe.measure(OkHttpClient(), "http://127.0.0.1:${server.address.port}/probe")
            assertTrue("delay=${result.delayMs}", result.delayMs >= 100)
            assertEquals(2, ports.size)
            assertEquals(1, ports.distinct().size)
        } finally { server.stop(0) }
    }

    @Test
    fun aFailedFirstRequestDoesNotDiscardTheSecondSuccess() = runBlocking {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val code = if (calls.incrementAndGet() == 1) 503 else 204
            exchange.sendResponseHeaders(code, -1)
            exchange.close()
        }
        server.start()
        try {
            assertTrue(LatencyProbe.measure(OkHttpClient(), "http://127.0.0.1:${server.address.port}/").delayMs > 0)
            assertEquals(2, calls.get())
        } finally { server.stop(0) }
    }

    @Test
    fun anOversizedBodyIsNotReportedAsSuccessfulLatency() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(200, 300_000)
            runCatching { exchange.responseBody.use { it.write(ByteArray(300_000)) } }
            exchange.close()
        }
        server.start()
        try {
            assertEquals(-1L, LatencyProbe.measure(OkHttpClient(), "http://127.0.0.1:${server.address.port}/").delayMs)
        } finally { server.stop(0) }
    }
}

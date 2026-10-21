package app.farvater.engine

import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.net.await
import app.farvater.net.orDefault
import android.os.Process
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Serializable
enum class TestMethod { REAL, TCP }

@Serializable
data class TestResult(
    val nodeId: String,
    val delayMs: Long,
    val method: TestMethod,
    val at: Long = System.currentTimeMillis(),
) {
    val alive: Boolean get() = delayMs > 0
}

object NodeTester {
    private val workers = Executors.newFixedThreadPool(48) { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "farvater-test").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private const val TCP_CONCURRENCY = 48
    private const val TCP_TIMEOUT_MS = 2500

    private const val PER_HOST = 3
    private const val PER_SNI = 6

    fun testAll(nodes: List<ProxyNode>, url: String, concurrency: Int): Flow<TestResult> = channelFlow {
        val real = XrayEngine.isAvailable
        val tcpGate = Semaphore(TCP_CONCURRENCY)
        val realGate = Semaphore(concurrency.coerceIn(1, 32))
        val hostGates = ConcurrentHashMap<String, Semaphore>()
        val sniGates = ConcurrentHashMap<String, Semaphore>()
        nodes.forEach { node ->
            launch(workers) {
                val coreOnly = node.protocol == Protocol.HYSTERIA2 || node.isJson
                val host = hostGates.getOrPut(node.address.lowercase()) { Semaphore(PER_HOST) }
                val tcp = if (coreOnly) 0L else host.withPermit { tcpGate.withPermit { tcping(node) } }
                if (tcp < 0 || !real) {
                    send(TestResult(node.id, if (coreOnly) -1L else tcp, TestMethod.TCP))
                    return@launch
                }
                val sni = sniGates.getOrPut(handshakeKey(node)) { Semaphore(PER_SNI) }
                send(sni.withPermit { realGate.withPermit { runInterruptible { test(node, url) } } })
            }
        }
    }

    private val tunnelClient by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayConfigBuilder.SOCKS_PORT)))
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun throughTunnel(node: ProxyNode, url: String): TestResult? = orDefault(null) {
        val start = System.nanoTime()
        val ok = tunnelClient.newCall(Request.Builder().url(url).build()).await { it.code == 204 || it.code == 200 }
        if (ok) TestResult(node.id, ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1), TestMethod.REAL) else null
    }

    private fun handshakeKey(node: ProxyNode): String {
        val p = node.params
        return (p["sni"] ?: p["peer"] ?: p["host"])?.takeIf { it.isNotBlank() }?.lowercase() ?: node.address.lowercase()
    }

    fun test(node: ProxyNode, url: String): TestResult = when {
        XrayEngine.isAvailable -> TestResult(
            node.id,
            XrayEngine.measureDelay(XrayConfigBuilder.build(node, directRuServices = false, forTest = true), url),
            TestMethod.REAL,
        )
        node.protocol == Protocol.HYSTERIA2 -> TestResult(node.id, -1, TestMethod.TCP)
        else -> TestResult(node.id, tcping(node), TestMethod.TCP)
    }

    private fun tcping(node: ProxyNode): Long = runCatching {
        val start = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(node.address, node.port), TCP_TIMEOUT_MS) }
        ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
    }.getOrDefault(-1L)
}

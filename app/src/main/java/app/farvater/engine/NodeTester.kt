package app.farvater.engine

import app.farvater.core.model.Protocol
import app.farvater.core.model.NodeProfile
import app.farvater.core.model.ProxyNode
import app.farvater.core.xray.LocalProxyAuth
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.net.await
import app.farvater.net.orDefault
import app.farvater.data.ProbeMethod
import android.os.Process
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Serializable
enum class TestMethod { REAL, TCP }

@Serializable
data class TestResult(
    val nodeId: String,
    val delayMs: Long,
    val method: TestMethod,
    val at: Long = System.currentTimeMillis(),
    val reason: TestFailure = TestFailure.UNKNOWN,
    val endpointOnly: Boolean = false,
) {
    val alive: Boolean get() = delayMs > 0
    val evaluated: Boolean get() = reason != TestFailure.BUSY && reason != TestFailure.STALE && reason != TestFailure.UNSUPPORTED
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

    fun testAll(nodes: List<ProxyNode>, url: String, concurrency: Int): Flow<TestResult> =
        testAll(nodes, url, concurrency, emptyMap())

    fun testAll(nodes: List<ProxyNode>, url: String, concurrency: Int, profiles: Map<String, NodeProfile>): Flow<TestResult> =
        measureNodes(nodes, url, concurrency, profiles, ProbeMethod.AUTO)

    fun userCheck(nodes: List<ProxyNode>, url: String, concurrency: Int, profiles: Map<String, NodeProfile>, method: ProbeMethod): Flow<TestResult> =
        if (method == ProbeMethod.AUTO) testAll(nodes, url, concurrency, profiles)
        else measureNodes(nodes, url, concurrency, profiles, method)

    private fun measureNodes(nodes: List<ProxyNode>, url: String, concurrency: Int, profiles: Map<String, NodeProfile>,
        method: ProbeMethod): Flow<TestResult> = channelFlow {
        val real = XrayEngine.isAvailable
        val tcpGate = Semaphore(TCP_CONCURRENCY)
        val realGate = Semaphore(concurrency.coerceIn(1, 32))
        val hostGates = ConcurrentHashMap<String, Semaphore>()
        val sniGates = ConcurrentHashMap<String, Semaphore>()
        val cursor = AtomicInteger()
        repeat(minOf(nodes.size, TCP_CONCURRENCY)) {
            launch(workers) {
                while (true) {
                    val node = nodes.getOrNull(cursor.getAndIncrement()) ?: break
                    val coreOnly = node.protocol == Protocol.HYSTERIA2 || node.isJson
                    val host = hostGates.getOrPut(node.address.lowercase()) { Semaphore(PER_HOST) }
                    if (method == ProbeMethod.TCP) {
                        val result = if (coreOnly) DelayMeasurement(-1, TestFailure.UNSUPPORTED)
                            else host.withPermit { runInterruptible { tcping(node) } }
                        send(TestResult(node.id, result.delayMs, TestMethod.TCP, reason = result.reason, endpointOnly = true))
                        continue
                    }
                    if (method == ProbeMethod.HTTPS && !real) {
                        send(TestResult(node.id, -1, TestMethod.REAL, reason = TestFailure.UNSUPPORTED))
                        continue
                    }
                    val tcp = if (coreOnly || method == ProbeMethod.HTTPS) DelayMeasurement(0) else host.withPermit { tcpGate.withPermit { tcping(node) } }
                    if (tcp.delayMs < 0 || !real) {
                        send(TestResult(node.id, if (coreOnly) -1L else tcp.delayMs, TestMethod.TCP, reason = tcp.reason))
                        continue
                    }
                    val sni = sniGates.getOrPut(handshakeKey(node)) { Semaphore(PER_SNI) }
                    send(host.withPermit { sni.withPermit { realGate.withPermit {
                        runInterruptible { test(node, url, profiles[node.id]) }
                    } } })
                }
            }
        }
    }

    val tunnelClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", LocalProxyAuth.probePort)))
            .followRedirects(false).followSslRedirects(false)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun throughTunnel(node: ProxyNode, url: String): TestResult? = orDefault(null) {
        val start = System.nanoTime()
        val ok = tunnelClient.newCall(Request.Builder().url(url).build()).await { it.code == 204 || it.code == 200 }
        if (ok) TestResult(node.id, ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1), TestMethod.REAL) else null
    }

    suspend fun measureLiveLatency(node: ProxyNode, url: String): TestResult =
        LatencyProbe.measure(tunnelClient, url).let { TestResult(node.id, it.delayMs, TestMethod.REAL, reason = it.reason) }

    private fun handshakeKey(node: ProxyNode): String {
        val p = node.params
        return (p["sni"] ?: p["peer"] ?: p["host"])?.takeIf { it.isNotBlank() }?.lowercase() ?: node.address.lowercase()
    }

    fun test(node: ProxyNode, url: String): TestResult = when {
        XrayEngine.isAvailable -> XrayEngine.measureDelayDetailed(
            XrayConfigBuilder.build(node, directRuServices = false, forTest = true), url,
        ).let { TestResult(node.id, it.delayMs, TestMethod.REAL, reason = it.reason) }
        node.protocol == Protocol.HYSTERIA2 -> TestResult(node.id, -1, TestMethod.TCP)
        else -> tcping(node).let { TestResult(node.id, it.delayMs, TestMethod.TCP, reason = it.reason) }
    }

    fun test(node: ProxyNode, url: String, profile: NodeProfile?): TestResult =
        if (profile == null || !XrayEngine.isAvailable) test(node, url) else
            XrayEngine.measureDelayDetailed(XrayConfigBuilder.build(node, directRuServices = false, forTest = true, profile = profile), url)
                .let { TestResult(node.id, it.delayMs, TestMethod.REAL, reason = it.reason) }

    fun testRecovery(node: ProxyNode, url: String, profile: NodeProfile?): TestResult =
        if (!XrayEngine.isAvailable) test(node, url) else
            XrayEngine.measureRecovery(XrayConfigBuilder.build(node, directRuServices = false, forTest = true, profile = profile), url)
                .let { TestResult(node.id, it.delayMs, TestMethod.REAL, reason = it.reason) }

    private fun tcping(node: ProxyNode): DelayMeasurement = try {
        val start = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(node.address, node.port), TCP_TIMEOUT_MS) }
        DelayMeasurement(((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1))
    } catch (error: Exception) {
        DelayMeasurement(-1, TestFailure.from(error))
    }
}

package app.farvater.engine

import app.farvater.core.model.NodeProfile
import app.farvater.core.model.ProxyNode
import app.farvater.core.xray.LocalProxyAuth
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.data.GalsGroup
import app.farvater.data.GalsGroups
import app.farvater.data.GalsStrategy
import app.farvater.data.ProbeUrls
import app.farvater.data.ServiceCheck
import app.farvater.data.ServiceChecks
import app.farvater.data.ServiceKind
import app.farvater.data.ServiceTarget
import app.farvater.data.ServiceVerdict
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

internal data class GalsMetrics(
    val delayMs: Long = -1,
    val speedBps: Long = 0,
    val target: ServiceCheck? = null,
    val dns: String? = null,
    val dnsBlocked: Boolean = false,
) {
    val usable: Boolean get() = delayMs > 0 && !dnsBlocked &&
        (target == null || target.verdict == ServiceVerdict.RESPONDED)
}

object GalsProbe {
    private val base = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    suspend fun live(node: ProxyNode, saved: NodeProfile?, group: GalsGroup, probeUrl: String,
        checkDns: Boolean): PathResult = liveWithClient(node, saved, group, probeUrl, NodeTester.tunnelClient, checkDns)

    internal suspend fun liveWithClient(node: ProxyNode, saved: NodeProfile?, group: GalsGroup, probeUrl: String,
        client: OkHttpClient, checkDns: Boolean = true): PathResult {
        val session = (VpnBus.state.value as? VpnState.Connected)?.takeIf { it.node == node }
            ?: return PathResult(null, deferred = true)
        val epoch = VpnBus.stopEpoch.get()
        val pool = ConnectionPool()
        val metrics = try {
            val dns = if (checkDns) XrayConfigBuilder.dnsOrder(saved?.dns) else null
            withTimeoutOrNull(20_000) { measure(client.newBuilder().connectionPool(pool).build(), group, probeUrl, dns) }
                ?: GalsMetrics()
        } finally { pool.evictAll() }
        if (VpnBus.state.value != session || VpnBus.stopEpoch.get() != epoch) return PathResult(null, deferred = true)
        return PathResult((saved ?: NodeProfile()).takeIf { metrics.usable }, dnsBlocked = metrics.dnsBlocked, speedBps = metrics.speedBps,
            delayMs = metrics.delayMs, targetCheck = metrics.target)
    }

    suspend fun find(node: ProxyNode, saved: NodeProfile?, ordinaryBlocking: Boolean, checkDns: Boolean,
        group: GalsGroup, probeUrl: String, limit: Int = 2, onTry: (NodeProfile) -> Unit = {}): PathResult {
        return findInLane(node, saved, ordinaryBlocking, checkDns, group, probeUrl, limit, ProbeLane.NORMAL, onTry)
    }

    internal suspend fun findInLane(node: ProxyNode, saved: NodeProfile?, ordinaryBlocking: Boolean, checkDns: Boolean,
        group: GalsGroup, probeUrl: String, limit: Int, lane: ProbeLane, onTry: (NodeProfile) -> Unit = {}): PathResult {
        if (!GalsGroups.valid(group)) return PathResult(null)
        val dns = if (checkDns) XrayConfigBuilder.dnsOrder(saved?.dns) else null
        var last = PathResult(null)
        for (profile in Pathfinder.variants(node, saved, ordinaryBlocking).take(limit.coerceIn(1, 4))) {
            onTry(profile)
            val result = check(node, profile, group, probeUrl, dns, lane)
            if (result.profile != null || result.deferred || result.dnsBlocked ||
                result.targetCheck?.verdict == ServiceVerdict.REJECTED) return result
            last = result
        }
        return last
    }

    private suspend fun check(node: ProxyNode, profile: NodeProfile, group: GalsGroup, probeUrl: String,
        dns: List<String>?, lane: ProbeLane): PathResult = withContext(Dispatchers.IO) {
        val port = runCatching { ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort } }
            .getOrNull() ?: return@withContext PathResult(null)
        val account = LocalProxyAuth.Account()
        val config = ServiceProbe.configuration(node, profile, port, account = account)
        val probe = try {
            ProbeLaunch.await({ ProbeClient.startProbe(config, lane) }, ProbeClient::stopProbe, deferOnTimeout = true,
                recovery = lane == ProbeLane.RECOVERY)
        } catch (_: ProbeDeferredException) {
            return@withContext PathResult(null, deferred = true)
        } ?: return@withContext PathResult(null)
        val pool = ConnectionPool()
        try {
            coroutineContext.ensureActive()
            LocalProxyAuth.open(port, account) { ProbeClient.alive(probe) }
            val client = base.newBuilder().connectionPool(pool)
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))).build()
            val metrics = withTimeoutOrNull(20_000) { measure(client, group, probeUrl, dns) } ?: GalsMetrics()
            PathResult(profile.takeIf { metrics.usable }?.copy(dns = metrics.dns), dnsBlocked = metrics.dnsBlocked,
                speedBps = metrics.speedBps, delayMs = metrics.delayMs, targetCheck = metrics.target)
        } finally {
            pool.evictAll()
            LocalProxyAuth.close(port, account)
            ProbeClient.stopProbe(probe)
        }
    }

    internal suspend fun measure(client: OkHttpClient, group: GalsGroup, probeUrl: String, dns: List<String>?): GalsMetrics {
        if (!GalsGroups.valid(group)) return GalsMetrics()
        val target = when (group.target) {
            null -> null
            ServiceKind.CUSTOM -> ServiceChecks.customUrl(group.customUrl)?.let {
                ServiceTarget(ServiceKind.CUSTOM, it.toString(), group.expectedCode)
            } ?: return GalsMetrics()
            else -> ServiceChecks.builtIn.firstOrNull { it.kind == group.target } ?: return GalsMetrics()
        }
        val bounded = client.newBuilder().connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
        var checked: ServiceCheck? = null
        val latency = if (target == null) {
            if (probeUrl !in ProbeUrls.all) return GalsMetrics()
            LatencyProbe.measure(bounded, probeUrl).delayMs
        } else {
            var best = Long.MAX_VALUE
            repeat(2) {
                val start = System.nanoTime()
                val response = ServiceProbe.request(bounded, target)
                checked = response
                if (response.verdict != ServiceVerdict.RESPONDED) return GalsMetrics(target = response)
                best = minOf(best, ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1))
            }
            best
        }
        if (latency <= 0) return GalsMetrics(target = checked)
        val resolved = dns?.let {
            val host = (target?.url ?: probeUrl).toHttpUrl().host
            val dnsName = if (':' in host || host.all { it.isDigit() || it == '.' }) {
                if (probeUrl !in ProbeUrls.all) return GalsMetrics(target = checked, dnsBlocked = true)
                probeUrl.toHttpUrl().host
            } else host
            DohProbe.firstAnswering(bounded, it, dnsName)
        }
        if (dns != null && resolved == null) return GalsMetrics(target = checked, dnsBlocked = true)
        val speed = if (group.strategy == GalsStrategy.THROUGHPUT) {
            Pathfinder.measureSpeed(bounded) ?: return GalsMetrics(target = checked)
        } else 0
        return GalsMetrics(latency, speed, checked, resolved)
    }
}

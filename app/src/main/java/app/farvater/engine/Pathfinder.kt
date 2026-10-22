package app.farvater.engine

import app.farvater.core.model.NodeProfile
import app.farvater.core.model.ProxyNode
import app.farvater.core.model.canFragment
import app.farvater.core.model.canMux
import app.farvater.core.model.usesFingerprint
import app.farvater.core.xray.LocalProxyAuth
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.net.await
import app.farvater.net.orDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

data class PathResult(
    val profile: NodeProfile?,
    val telegramBlocked: Boolean = false,
    val dnsBlocked: Boolean = false,
    val speedBps: Long = 0,
    val partial: NodeProfile? = null,
)

object Pathfinder {
    private enum class Verdict { OK, NO_DATA, NO_TELEGRAM, NO_DNS }

    private class Check(val verdict: Verdict, val dns: String? = null, val speedBps: Long = 0)

    private const val PAYLOAD = 256 * 1024
    private const val ENOUGH = 200 * 1024
    private const val TELEGRAM = "https://api.telegram.org/"
    private val urls = listOf(
        "https://speed.cloudflare.com/__down?bytes=$PAYLOAD",
        "https://proof.ovh.net/files/1Mb.dat",
    )

    private val base by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun variants(node: ProxyNode, saved: NodeProfile?, ordinaryBlocking: Boolean): List<NodeProfile> = buildList {
        saved?.copy(dns = null)?.let(::add)
        add(NodeProfile())
        if (ordinaryBlocking && node.canFragment) add(NodeProfile(fragment = true))
        if (node.usesFingerprint) {
            add(NodeProfile(fingerprint = "firefox"))
            add(NodeProfile(fingerprint = "edge"))
        }
        if (node.canMux) add(NodeProfile(mux = true))
    }.distinct()

    suspend fun find(
        node: ProxyNode,
        saved: NodeProfile?,
        ordinaryBlocking: Boolean,
        checkDns: Boolean = true,
        limit: Int = 4,
        onTry: (NodeProfile) -> Unit = {},
    ): PathResult {
        val dns = if (checkDns) XrayConfigBuilder.dnsOrder(saved?.dns) else null
        for (profile in variants(node, saved, ordinaryBlocking).take(limit)) {
            onTry(profile)
            val result = check(node, profile, dns)
            when (result.verdict) {
                Verdict.OK -> return PathResult(profile.copy(dns = result.dns), speedBps = result.speedBps)
                Verdict.NO_TELEGRAM -> return PathResult(null, telegramBlocked = true, speedBps = result.speedBps, partial = profile)
                Verdict.NO_DNS -> return PathResult(null, dnsBlocked = true, speedBps = result.speedBps, partial = profile)
                Verdict.NO_DATA -> Unit
            }
        }
        return PathResult(null)
    }

    private suspend fun check(node: ProxyNode, profile: NodeProfile, dns: List<String>?): Check = withContext(Dispatchers.IO) {
        val port = freePort() ?: return@withContext Check(Verdict.NO_DATA)
        val config = XrayConfigBuilder.build(node, directRuServices = false, encryptedDns = false, profile = profile, socksPort = port)
        val probe = XrayEngine.startProbe(config) ?: return@withContext Check(Verdict.NO_DATA)
        LocalProxyAuth.open(port)
        try {
            val client = base.newBuilder().proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))).build()
            var speed: Long? = null
            for (url in urls) {
                val start = System.nanoTime()
                val bytes = download(client, url)
                if (bytes >= ENOUGH) {
                    speed = bytes * 1_000_000_000L / (System.nanoTime() - start).coerceAtLeast(1)
                    break
                }
            }
            when {
                speed == null -> Check(Verdict.NO_DATA)
                !reachable(client, TELEGRAM) -> Check(Verdict.NO_TELEGRAM, speedBps = speed)
                dns == null -> Check(Verdict.OK, speedBps = speed)
                else -> DohProbe.firstAnswering(client, dns)?.let { Check(Verdict.OK, it, speed) } ?: Check(Verdict.NO_DNS, speedBps = speed)
            }
        } finally {
            LocalProxyAuth.close(port)
            XrayEngine.stopProbe(probe)
        }
    }

    private suspend fun reachable(client: OkHttpClient, url: String): Boolean = orDefault(false) {
        client.newBuilder().followRedirects(false).build()
            .newCall(Request.Builder().url(url).head().build()).await { true }
    }

    private suspend fun download(client: OkHttpClient, url: String): Int = orDefault(0) {
        client.newCall(Request.Builder().url(url).build()).await { response ->
            if (!response.isSuccessful) return@await 0
            val source = response.body?.byteStream() ?: return@await 0
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (total < PAYLOAD) {
                val n = source.read(buffer)
                if (n < 0) break
                total += n
            }
            total
        }
    }

    private fun freePort(): Int? = runCatching {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    }.getOrNull()
}

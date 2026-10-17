package app.farvater.engine

import app.farvater.core.model.NodeProfile
import app.farvater.core.model.ProxyNode
import app.farvater.core.model.canFragment
import app.farvater.core.model.canMux
import app.farvater.core.model.usesFingerprint
import app.farvater.core.xray.XrayConfigBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

data class PathResult(val profile: NodeProfile?, val telegramBlocked: Boolean = false)

object Pathfinder {
    private enum class Verdict { OK, NO_DATA, NO_TELEGRAM }

    private const val PAYLOAD = 256 * 1024
    private const val ENOUGH = 200 * 1024
    private const val TELEGRAM = "https://api.telegram.org/"
    private val urls = listOf(
        "https://speed.cloudflare.com/__down?bytes=$PAYLOAD",
        "https://proof.ovh.net/files/1Mb.dat",
    )

    fun variants(node: ProxyNode, saved: NodeProfile?, ordinaryBlocking: Boolean): List<NodeProfile> = buildList {
        saved?.let(::add)
        add(NodeProfile())
        if (node.usesFingerprint) {
            add(NodeProfile(fingerprint = "firefox"))
            add(NodeProfile(fingerprint = "edge"))
        }
        if (node.canMux) add(NodeProfile(mux = true))
        if (ordinaryBlocking && node.canFragment) add(NodeProfile(fragment = true))
    }.distinct()

    suspend fun find(
        node: ProxyNode,
        saved: NodeProfile?,
        ordinaryBlocking: Boolean,
        limit: Int = 4,
        onTry: (NodeProfile) -> Unit = {},
    ): PathResult {
        for (profile in variants(node, saved, ordinaryBlocking).take(limit)) {
            onTry(profile)
            when (check(node, profile)) {
                Verdict.OK -> return PathResult(profile)
                Verdict.NO_TELEGRAM -> return PathResult(null, telegramBlocked = true)
                Verdict.NO_DATA -> Unit
            }
        }
        return PathResult(null)
    }

    private suspend fun check(node: ProxyNode, profile: NodeProfile): Verdict = withContext(Dispatchers.IO) {
        val port = freePort() ?: return@withContext Verdict.NO_DATA
        val config = XrayConfigBuilder.build(node, directRuServices = false, encryptedDns = false, profile = profile, socksPort = port)
        val probe = XrayEngine.startProbe(config) ?: return@withContext Verdict.NO_DATA
        try {
            val client = OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
            when {
                urls.none { url -> download(client, url) >= ENOUGH } -> Verdict.NO_DATA
                !reachable(client, TELEGRAM) -> Verdict.NO_TELEGRAM
                else -> Verdict.OK
            }
        } finally {
            XrayEngine.stopProbe(probe)
        }
    }

    private fun reachable(client: OkHttpClient, url: String): Boolean = runCatching {
        client.newBuilder().followRedirects(false).build()
            .newCall(Request.Builder().url(url).head().build()).execute().use { true }
    }.getOrDefault(false)

    private fun download(client: OkHttpClient, url: String): Int = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@use 0
            val source = response.body?.byteStream() ?: return@use 0
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (total < PAYLOAD) {
                val n = source.read(buffer)
                if (n < 0) break
                total += n
            }
            total
        }
    }.getOrDefault(0)

    private fun freePort(): Int? = runCatching {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    }.getOrNull()
}

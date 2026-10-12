package app.farvater.data

import android.content.Context
import app.farvater.BuildConfig
import app.farvater.core.model.ProxyNode
import app.farvater.core.parser.SubscriptionParser
import app.farvater.core.parser.Usage
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.net.SafeHttp
import app.farvater.net.SafeHttp.hardened
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class SourceSnapshot(
    val sourceId: String,
    val title: String?,
    val announce: String?,
    val notices: List<String>,
    val nodes: List<ProxyNode>,
    val updatedAt: Long,
    val error: String? = null,
    val skipped: Map<String, Int> = emptyMap(),
    val intervalHours: Int? = null,
    val usage: Usage? = null,
)

class SubscriptionRepository(context: Context) {
    private val dir = File(context.filesDir, "subs").apply { mkdirs() }
    private val manualFile = File(dir, "${ProxyNode.MANUAL_SOURCE}.txt")

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .hardened()
        .build()

    private val tunnelClient by lazy {
        baseClient.newBuilder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayConfigBuilder.SOCKS_PORT)))
            .build()
    }

    suspend fun fetch(
        sourceId: String,
        urls: List<String>,
        viaTunnel: Boolean,
        extraHeaders: Map<String, String> = emptyMap(),
        json: Boolean = false,
    ): SourceSnapshot =
        withContext(Dispatchers.IO) {
            val client = if (viaTunnel) tunnelClient else baseClient
            var lastError: String? = null
            for (url in urls) for (asJson in if (json) listOf(true, false) else listOf(false)) {
                try {
                    val base = SafeHttp.parse(url) ?: throw IOException("недопустимый адрес подписки")
                    val target = if (asJson) base.jsonVariant() ?: continue else base
                    SafeHttp.execute(
                        client, target,
                        headers = mapOf("User-Agent" to "Farvater/${BuildConfig.VERSION_NAME}"),
                        privateHeaders = extraHeaders,
                    ).use { response ->
                        if (!response.isSuccessful) {
                            lastError = "HTTP ${response.code}"
                            return@use
                        }
                        val body = response.body?.let(::readLimited).orEmpty()
                        val headers = response.headers.toMultimap().mapValues { it.value.firstOrNull().orEmpty() }
                        val parsed = SubscriptionParser.parse(body, sourceId, headers)
                        if (parsed.nodes.isEmpty() && parsed.notices.isEmpty()) {
                            lastError = if (body.trimStart().startsWith("<")) {
                                "вместо подписки пришла веб-страница"
                            } else {
                                "в подписке сейчас нет серверов"
                            }
                            return@use
                        }
                        File(dir, "$sourceId.txt").writeText(body)
                        File(dir, "$sourceId.headers").writeText(
                            headers.filterKeys { it.lowercase() in KEPT_HEADERS }.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                        )
                        val interval = File(dir, "$sourceId.interval")
                        parsed.updateIntervalHours?.let { interval.writeText(it.toString()) } ?: interval.delete()
                        return@withContext SourceSnapshot(
                            sourceId, parsed.title, parsed.announce, parsed.notices, parsed.nodes,
                            updatedAt = System.currentTimeMillis(),
                            skipped = parsed.skipped,
                            intervalHours = parsed.updateIntervalHours,
                            usage = parsed.usage,
                        )
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                }
            }
            val cached = loadCached(sourceId)
            cached?.copy(error = lastError)
                ?: SourceSnapshot(sourceId, null, null, emptyList(), emptyList(), 0, error = lastError)
        }

    private fun readLimited(body: ResponseBody): String {
        if (body.contentLength() > MAX_BODY) throw IOException("подписка больше ${MAX_BODY / 1024 / 1024} МБ")
        val source = body.source()
        if (source.request(MAX_BODY + 1)) throw IOException("подписка больше ${MAX_BODY / 1024 / 1024} МБ")
        return source.buffer.readUtf8()
    }

    fun loadCached(sourceId: String): SourceSnapshot? {
        val file = File(dir, "$sourceId.txt")
        if (!file.exists()) return null
        val headers = File(dir, "$sourceId.headers").takeIf { it.exists() }?.readLines().orEmpty()
            .filter { ':' in it }.associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        val parsed = SubscriptionParser.parse(file.readText(), sourceId, headers)
        val interval = File(dir, "$sourceId.interval").takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        return SourceSnapshot(
            sourceId, parsed.title, parsed.announce, parsed.notices, parsed.nodes, file.lastModified(),
            skipped = parsed.skipped,
            intervalHours = interval ?: parsed.updateIntervalHours,
            usage = parsed.usage,
        )
    }

    fun loadManual(): SourceSnapshot =
        loadCached(ProxyNode.MANUAL_SOURCE)
            ?: SourceSnapshot(ProxyNode.MANUAL_SOURCE, null, null, emptyList(), emptyList(), 0)

    @Synchronized
    fun appendManual(nodes: List<ProxyNode>): Int {
        val existing = loadManual().nodes.map { it.id }.toSet()
        val fresh = nodes.filter { it.id !in existing }
        if (fresh.isNotEmpty()) manualFile.appendText(fresh.joinToString("\n", postfix = "\n") { it.raw })
        return fresh.size
    }

    fun removeManual(nodeId: String) {
        val keep = loadManual().nodes.filter { it.id != nodeId }
        manualFile.writeText(keep.joinToString("\n", postfix = "\n") { it.raw })
    }

    fun deleteCache(sourceId: String) {
        File(dir, "$sourceId.txt").delete()
        File(dir, "$sourceId.interval").delete()
        File(dir, "$sourceId.headers").delete()
    }

    private fun HttpUrl.jsonVariant(): HttpUrl? {
        val last = pathSegments.lastOrNull().orEmpty()
        if (query != null || last.isEmpty() || '.' in last || last == JSON_FORMAT) return null
        return newBuilder().addPathSegment(JSON_FORMAT).build()
    }

    companion object {
        const val MAX_BODY = 8L * 1024 * 1024
        private const val JSON_FORMAT = "v2ray-json"
        private val KEPT_HEADERS = setOf("profile-title", "announce", "subscription-userinfo", "profile-update-interval")
    }
}

package app.farvater.data

import app.farvater.R
import android.content.Context
import app.farvater.BuildConfig
import app.farvater.core.model.ProxyNode
import app.farvater.core.parser.SubscriptionParser
import app.farvater.core.parser.Usage
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.net.SafeHttp
import app.farvater.net.SafeHttp.hardened
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
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
    val networkUnavailable: Boolean = false,
)

class SubscriptionRepository(context: Context, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    val snapshots = MutableStateFlow<Map<String, SourceSnapshot>>(emptyMap())
    private val fetchTokens = HashMap<String, Any>()
    private val preferences = context.getSharedPreferences("farvater", Context.MODE_PRIVATE)
    private val application = context.applicationContext as android.app.Application
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
        isCurrent: () -> Boolean,
    ): SourceSnapshot {
        currentCoroutineContext().ensureActive()
        val empty = SourceSnapshot(sourceId, null, null, emptyList(), emptyList(), 0)
        if (!isCurrent()) return empty
        val token = synchronized(this) { Any().also { fetchTokens[sourceId] = it } }
        return withContext(ioDispatcher) {
            val client = if (viaTunnel) tunnelClient else baseClient
            var lastError: String? = null
            var receivedContent = false
            for (url in urls) for (asJson in if (json) listOf(true, false) else listOf(false)) {
                try {
                    if (!requestCurrent(sourceId, token, isCurrent)) return@withContext empty
                    val base = SafeHttp.parse(url) ?: throw IOException(application.localizedString(R.string.error_subscription_address))
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
                        receivedContent = true
                        val body = response.body?.let(::readLimited).orEmpty()
                        val headers = response.headers.toMultimap().mapValues { it.value.firstOrNull().orEmpty() }
                        val parsed = SubscriptionParser.parse(body, sourceId, headers)
                        if (parsed.nodes.isEmpty() && parsed.notices.isEmpty()) {
                            lastError = if (body.trimStart().startsWith("<")) {
                                application.localizedString(R.string.error_subscription_html)
                            } else if (parsed.skipped.isNotEmpty()) {
                                application.localizedString(R.string.import_rejected, application.importReasons(parsed.skipped))
                            } else {
                                application.localizedString(R.string.error_subscription_empty)
                            }
                            return@use
                        }
                        val snapshot = SourceSnapshot(
                            sourceId, parsed.title, parsed.announce, parsed.notices, parsed.nodes,
                            updatedAt = System.currentTimeMillis(),
                            skipped = parsed.skipped,
                            intervalHours = parsed.updateIntervalHours,
                            usage = parsed.usage,
                        )
                        val context = currentCoroutineContext()
                        synchronized(this@SubscriptionRepository) {
                            context.ensureActive()
                            if (fetchTokens[sourceId] === token && isCurrent()) {
                                ProfileStorage.write(File(dir, "$sourceId.txt"), body)
                                ProfileStorage.write(File(dir, "$sourceId.headers"),
                                    headers.filterKeys { it.lowercase() in KEPT_HEADERS }.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                                )
                                val interval = File(dir, "$sourceId.interval")
                                parsed.updateIntervalHours?.let { interval.writeText(it.toString()) } ?: interval.delete()
                                publish(sourceId, snapshot)
                            }
                        }
                        return@withContext snapshot
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.userMessage(application)
                }
            }
            val context = currentCoroutineContext()
            synchronized(this@SubscriptionRepository) {
                context.ensureActive()
                val cached = loadCached(sourceId)
                val unavailable = urls.isNotEmpty() && !receivedContent
                val snapshot = cached?.copy(error = lastError, networkUnavailable = unavailable)
                    ?: SourceSnapshot(sourceId, null, null, emptyList(), emptyList(), 0, error = lastError, networkUnavailable = unavailable)
                if (fetchTokens[sourceId] === token && isCurrent()) publish(sourceId, snapshot)
                snapshot
            }
        }
    }

    @Synchronized
    private fun requestCurrent(id: String, token: Any, isCurrent: () -> Boolean): Boolean =
        fetchTokens[id] === token && isCurrent()

    private fun publish(id: String, snapshot: SourceSnapshot?): Unit = synchronized(Prefs.selectionLock) {
        snapshots.value = if (snapshot == null) snapshots.value - id else snapshots.value + (id to snapshot)
    }

    @Synchronized
    fun loadSnapshot(sourceId: String): SourceSnapshot? = loadCached(sourceId).also { publish(sourceId, it) }

    @Synchronized
    internal fun migrateStoredProfiles() {
        for (key in ProfileStorage.sensitive) {
            if (preferences.contains(key)) ProfileStorage.get(preferences, key)
        }
        for (file in dir.listFiles().orEmpty()) {
            if (file.extension != "txt" && file.extension != "headers") continue
            if (file == manualFile && preferences.contains(ProfileStorage.sealedKey(Prefs.MANUAL_NODES_KEY))) {
                ProfileStorage.delete(file)
            } else {
                ProfileStorage.read(file)
            }
        }
    }

    @Synchronized
    fun resetInventory() {
        fetchTokens.clear()
        synchronized(Prefs.selectionLock) { snapshots.value = emptyMap() }
    }

    private fun readLimited(body: ResponseBody): String {
        if (body.contentLength() > MAX_BODY) throw IOException(application.localizedString(R.string.error_subscription_size, MAX_BODY / 1024 / 1024))
        val source = body.source()
        if (source.request(MAX_BODY + 1)) throw IOException(application.localizedString(R.string.error_subscription_size, MAX_BODY / 1024 / 1024))
        return source.buffer.readUtf8()
    }

    @Synchronized
    fun loadCached(sourceId: String): SourceSnapshot? = try {
        readCached(sourceId)
    } catch (_: java.security.GeneralSecurityException) {
        unavailableProfile(sourceId)
    } catch (_: IOException) {
        unavailableProfile(sourceId)
    }

    private fun unavailableProfile(sourceId: String) = SourceSnapshot(sourceId, null, null, emptyList(), emptyList(), 0,
        error = application.localizedString(R.string.profile_storage_unavailable))

    private fun readCached(sourceId: String): SourceSnapshot? {
        if (sourceId == ProxyNode.MANUAL_SOURCE) {
            ProfileStorage.get(preferences, Prefs.MANUAL_NODES_KEY)?.let { raw ->
                ProfileStorage.delete(manualFile)
                val parsed = SubscriptionParser.parse(raw, sourceId)
                return SourceSnapshot(sourceId, null, null, parsed.notices, parsed.nodes, 0, skipped = parsed.skipped)
            }
        }
        val file = File(dir, "$sourceId.txt")
        val body = ProfileStorage.read(file) ?: return null
        val headers = ProfileStorage.read(File(dir, "$sourceId.headers"))?.lines().orEmpty()
            .filter { ':' in it }.associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        val parsed = SubscriptionParser.parse(body, sourceId, headers)
        val interval = File(dir, "$sourceId.interval").takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        return SourceSnapshot(
            sourceId, parsed.title, parsed.announce, parsed.notices, parsed.nodes, ProfileStorage.modified(file),
            skipped = parsed.skipped,
            intervalHours = interval ?: parsed.updateIntervalHours,
            usage = parsed.usage,
        )
    }

    fun loadManual(): SourceSnapshot =
        loadCached(ProxyNode.MANUAL_SOURCE)
            ?: SourceSnapshot(ProxyNode.MANUAL_SOURCE, null, null, emptyList(), emptyList(), 0)

    @Synchronized
    internal fun manualForBackup(): List<ProxyNode> = readCached(ProxyNode.MANUAL_SOURCE)?.nodes.orEmpty()

    @Synchronized
    fun appendManual(nodes: List<ProxyNode>): Int {
        val existing = readCached(ProxyNode.MANUAL_SOURCE)?.nodes.orEmpty().map { it.id }.toSet()
        val fresh = nodes.filter { it.id !in existing }
        if (fresh.isNotEmpty()) {
            val previous = ProfileStorage.get(preferences, Prefs.MANUAL_NODES_KEY)
            val addition = fresh.joinToString("\n", postfix = "\n") { it.raw }
            if (previous == null) ProfileStorage.write(manualFile, ProfileStorage.read(manualFile).orEmpty() + addition)
            else writeRestoredManual(previous + addition)
        }
        publish(ProxyNode.MANUAL_SOURCE, loadManual())
        return fresh.size
    }

    @Synchronized
    fun removeManual(nodeId: String) {
        val keep = readCached(ProxyNode.MANUAL_SOURCE)?.nodes.orEmpty().filter { it.id != nodeId }
        val raw = keep.joinToString("\n", postfix = "\n") { it.raw }
        if (!preferences.contains(ProfileStorage.sealedKey(Prefs.MANUAL_NODES_KEY))) ProfileStorage.write(manualFile, raw)
        else writeRestoredManual(raw)
        publish(ProxyNode.MANUAL_SOURCE, loadManual())
    }

    private fun writeRestoredManual(raw: String): Unit = synchronized(Prefs.selectionLock) {
        val key = ProfileStorage.sealedKey(Prefs.MANUAL_NODES_KEY)
        val previous = preferences.getString(key, null)
        if (!ProfileStorage.put(preferences.edit(), Prefs.MANUAL_NODES_KEY, raw).commit()) {
            preferences.edit().putString(key, previous).commit()
            throw IOException()
        }
    }

    @Synchronized
    fun deleteCache(sourceId: String) {
        fetchTokens.remove(sourceId)
        ProfileStorage.delete(File(dir, "$sourceId.txt"))
        File(dir, "$sourceId.interval").delete()
        ProfileStorage.delete(File(dir, "$sourceId.headers"))
        publish(sourceId, null)
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

package app.farvater.data

import app.farvater.BuildConfig
import app.farvater.core.catalog.BuiltInCatalog
import app.farvater.core.model.ProxyNode
import app.farvater.core.parser.SubscriptionParser
import app.farvater.core.parser.LinkParser
import app.farvater.net.SafeHttp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.InputStream
import java.io.ByteArrayOutputStream

@Serializable
data class BackupArchive(
    val schema: Int = 1,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val settings: JsonObject,
    val subscriptions: List<UserSource> = emptyList(),
    val enabledSources: Set<String> = emptySet(),
    val sourceConfigs: Map<String, SourceConfig> = emptyMap(),
    val manualNodes: List<String> = emptyList(),
    val language: String = "",
) {
    fun decodedSettings(): AppSettings = settingsJson.decodeFromJsonElement(AppSettings.serializer(), settings).let {
        it.copy(testUrl = ProbeUrls.migrate(it.testUrl))
    }

    fun securityChanges(current: AppSettings): BackupSecurityChanges {
        val restored = decodedSettings()
        return BackupSecurityChanges(
            addedBypassApps = restored.bypassApps - current.bypassApps,
            removedBypassApps = current.bypassApps - restored.bypassApps,
            safeMode = restored.safeMode.takeIf { it != current.safeMode },
            encryptedDns = restored.encryptedDns.takeIf { it != current.encryptedDns },
            autoUpdates = restored.autoUpdates.takeIf { it != current.autoUpdates },
            sendHwid = restored.sendHwid.takeIf { it != current.sendHwid },
            directRuServices = restored.directRuServices.takeIf { it != current.directRuServices },
        )
    }

    fun validated(): BackupArchive {
        require(schema == 1)
        require(appVersion.length <= 100)
        require(language.isEmpty() || language in AppLanguages.supported)
        require(settings.keys.none { it in excludedSettings })
        val decoded = decodedSettings()
        require(decoded.mtu in 1280..9000 && decoded.concurrency in 1..32)
        require(decoded.testUrl in ProbeUrls.all)
        require(decoded.serviceExpectedCode in 200..599)
        require(decoded.serviceCustomUrl.isEmpty() || ServiceChecks.customUrl(decoded.serviceCustomUrl) != null)
        require(decoded.bypassApps.size <= 1000 && decoded.bypassApps.all { it.length <= 255 && packageName.matches(it) })
        require(decoded.favoriteNodeIds.size <= 5000 && decoded.favoriteNodeIds.all { nodeId.matches(it) })
        require(subscriptions.size <= 100 && subscriptions.map { it.id }.distinct().size == subscriptions.size)
        require(subscriptions.map { it.url }.distinct().size == subscriptions.size)
        require(subscriptions.all {
            sourceId.matches(it.id) && SafeHttp.parse(it.url) != null && it.title.length <= 2000 &&
                it.url.none(Char::isISOControl) && it.url == it.url.trim() &&
                it.id == "user-" + LinkParser.nodeId(it.url).take(10)
        })
        val catalog = BuiltInCatalog.sources.map { it.id }.toSet()
        require(enabledSources.all { it in catalog })
        require(sourceConfigs.size <= 200 && sourceConfigs.keys.all { it in catalog || it in subscriptions.map { s -> s.id } })
        require(sourceConfigs.values.all { it.intervalHours == null || it.intervalHours in setOf(0, 1, 3, 6, 12, 24) })
        require(manualNodes.size <= 1000)
        val ids = manualNodes.map { raw ->
            require(raw.length <= 256 * 1024)
            val parsed = SubscriptionParser.parse(raw, ProxyNode.MANUAL_SOURCE)
            require(parsed.nodes.size == 1 && parsed.skipped.isEmpty() && parsed.notices.isEmpty())
            parsed.nodes.single().id
        }
        require(ids.distinct().size == ids.size)
        return this
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
        private val json = Json { encodeDefaults = true }
        private val settingsJson = Json(json) { ignoreUnknownKeys = true }
        private val excludedSettings = setOf("acceptedLegalVersion", "onboardingDone")
        private val sourceId = Regex("user-[0-9a-f]{10}")
        private val nodeId = Regex("[0-9a-f]{16}")
        private val packageName = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

        fun create(prefs: Prefs, manual: List<ProxyNode>, language: String): BackupArchive = BackupArchive(
            settings = JsonObject(json.encodeToJsonElement(AppSettings.serializer(), prefs.settings).jsonObject - excludedSettings),
            subscriptions = prefs.userSources,
            enabledSources = prefs.enabledSources,
            sourceConfigs = prefs.sourceConfigs.filterKeys { key ->
                BuiltInCatalog.byId(key) != null || prefs.userSources.any { it.id == key }
            },
            manualNodes = manual.map { it.raw },
            language = language,
        ).validated()

        fun read(input: InputStream): BackupArchive {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= MAX_BYTES) {
                val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
                if (count < 0) break
                if (count == 0) throw java.io.IOException()
                output.write(buffer, 0, count)
            }
            val bytes = output.toByteArray()
            require(bytes.size <= MAX_BYTES)
            val text = bytes.toString(Charsets.UTF_8)
            var depth = 0
            var quoted = false
            var escaped = false
            for (char in text) {
                if (quoted) {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> quoted = false
                    }
                } else when (char) {
                    '"' -> quoted = true
                    '{', '[' -> { depth++; require(depth <= 64) }
                    '}', ']' -> { depth--; require(depth >= 0) }
                }
            }
            require(depth == 0 && !quoted)
            return json.decodeFromString(serializer(), text).validated()
        }
    }
}

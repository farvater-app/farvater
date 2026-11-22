package app.farvater.data

import android.content.Context
import androidx.core.content.edit
import app.farvater.core.catalog.BuiltInCatalog
import app.farvater.core.model.NetMode
import app.farvater.core.model.NetModeChoice
import app.farvater.core.model.NodeProfile
import app.farvater.core.model.ProxyNode
import app.farvater.core.model.SourceMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

const val LEGAL_VERSION = 8

@Serializable
enum class UiStyle { IOS, MATERIAL, ROSKOMPOZOR, SUMIE, NOTHING, ORIGIN, HYPER, HARMONY, ONEUI, OXYGEN, WITCH, WINTER, COLOROS, FLYME, OBSERVATORY }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class ProbeMethod { AUTO, TCP, HTTPS }

@Serializable
enum class Accent { THEME, OCEAN, FOREST, BERRY }

@Serializable
enum class AppFont { THEME, SANS, SERIF, MONO }

@Serializable
data class AppSettings(
    val directRuServices: Boolean = true,
    val hideDead: Boolean = false,
    val liveSort: Boolean = false,
    val probeMethod: ProbeMethod = ProbeMethod.HTTPS,
    val flatNodes: Boolean = false,
    val neutralNotification: Boolean = false,
    val accent: Accent = Accent.THEME,
    val font: AppFont = AppFont.THEME,
    val dropDuplicates: Boolean = false,
    val duplicatesAsked: Boolean = false,
    val testUrl: String = ProbeUrls.GOOGLE,
    val concurrency: Int = 16,
    val onboardingDone: Boolean = false,
    val communityEnabled: Boolean = false,
    val acceptedLegalVersion: Int = 0,
    val safeMode: Boolean = true,
    val encryptedDns: Boolean = true,
    val sendHwid: Boolean = true,
    val autoUpdates: Boolean = true,
    val bypassApps: Set<String> = emptySet(),
    val mtu: Int = 8500,
    val netMode: NetModeChoice = NetModeChoice.AUTO,
    val uiStyle: UiStyle = UiStyle.IOS,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val modeNodes: Boolean = true,
    val autoStart: Boolean = false,
    val groupDead: Boolean = true,
    val autoSwitch: Boolean = true,
    val witchSeen: Boolean = false,
    val favoriteNodeIds: Set<String> = emptySet(),
    val serviceChecksEnabled: Boolean = false,
    val checkedServices: Set<ServiceKind> = setOf(ServiceKind.TELEGRAM),
    val serviceCustomUrl: String = "",
    val serviceExpectedCode: Int = 200,
    val galsGroups: List<GalsGroup> = emptyList(),
    val activeGalsGroupId: String? = null,
    val nodeNames: Map<String, String> = emptyMap(),
    val routeRules: List<RouteRule> = emptyList(),
    val automaticRoutes: Boolean = true,
    val excludedAutoNodeIds: Set<String> = emptySet(),
) {
    internal fun migrated(): AppSettings = copy(
        testUrl = ProbeUrls.migrate(testUrl),
        probeMethod = if (probeMethod == ProbeMethod.AUTO) ProbeMethod.HTTPS else probeMethod,
    )

    fun nodeLabel(node: ProxyNode): String = nodeNames["${node.sourceId}/${node.id}"] ?: node.name

    fun rename(node: ProxyNode, name: String): AppSettings {
        val key = "${node.sourceId}/${node.id}"
        val label = name.trim()
        if (label.length > 100 || label.any(Char::isISOControl)) return this
        if (label.isEmpty() || label == node.name) return copy(nodeNames = nodeNames - key)
        if (key !in nodeNames && nodeNames.size >= 5000) return this
        return copy(nodeNames = nodeNames + (key to label))
    }
}

internal val AppSettings.automaticBypassApps: Set<String>
    get() = if (directRuServices && automaticRoutes && routeRules.isEmpty()) app.farvater.core.catalog.RuApps.packages else emptySet()

internal val AppSettings.effectiveBypassApps: Set<String>
    get() = bypassApps + automaticBypassApps

internal val AppSettings.notificationIcon: Int get() =
    if (neutralNotification) app.farvater.R.drawable.ic_notification_arrow else app.farvater.R.drawable.ic_logo

@Serializable
data class SourceConfig(val mode: SourceMode? = null, val intervalHours: Int? = null)

@Serializable
data class UserSource(val id: String, val url: String, val title: String) {
    fun displayTitle(profileTitle: String?): String? =
        listOfNotNull(profileTitle, title).firstOrNull { it.isNotBlank() && it.trim() != "kotlin.Unit" }
}

@Serializable
data class DayTraffic(val day: Long = 0, val bytes: Long = 0)

@Serializable
data class BackupNodes(val mode: String = "", val nodes: List<ProxyNode> = emptyList())

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("farvater", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val unreadable = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var prepared = false
    internal val protectedDataUnavailable get() = unreadable.isNotEmpty()

    internal fun prepareProtectedData(): Boolean = synchronized(selectionLock) {
        if (prepared && unreadable.isEmpty()) return@synchronized true
        for (key in ProfileStorage.sensitive) {
            if (sp.contains(key) || sp.contains(ProfileStorage.sealedKey(key))) readString(key) else unreadable.remove(key)
        }
        if (unreadable.isEmpty() && !sp.contains(ProfileStorage.sealedKey("settings"))) {
            withStorage("settings") {
                if (!ProfileStorage.put(sp.edit(), "settings", json.encodeToString(AppSettings())).commit()) {
                    throw java.io.IOException("Cannot initialize protected settings")
                }
            }
        }
        if (unreadable.isEmpty()) {
            settings
            lastNode
            userSources
            modeNodes
        }
        prepared = unreadable.isEmpty()
        prepared
    }

    private fun readString(key: String): String? = withStorage(key) { ProfileStorage.get(sp, key) }

    private inline fun <T> withStorage(key: String, operation: () -> T): T? {
        return try {
            operation().also { unreadable.remove(key) }
        } catch (_: java.security.GeneralSecurityException) {
            unreadable.add(key)
            null
        } catch (_: java.io.IOException) {
            unreadable.add(key)
            null
        }
    }

    @Volatile private var cachedSettings: AppSettings? = null
    @Volatile private var cachedEnabled: Set<String>? = null
    @Volatile private var cachedUserSources: List<UserSource>? = null
    @Volatile private var cachedConfigs: Map<String, SourceConfig>? = null

    internal val loadedSettings get() = cachedSettings

    var settings: AppSettings
        get() = cachedSettings ?: run {
            val stored = read<AppSettings>("settings") ?: AppSettings()
            val current = stored.migrated()
            if (current != stored) write("settings", current)
            current
        }.also { if ("settings" !in unreadable) cachedSettings = it }
        set(value) { saveSettings(value) }

    internal fun saveSettings(value: AppSettings): Boolean = synchronized(selectionLock) {
        write("settings", value).also { if (it) cachedSettings = value }
    }

    var enabledSources: Set<String>
        get() = cachedEnabled ?: run {
            val all = BuiltInCatalog.sources.map { it.id }.toSet()
            val stored = sp.getStringSet("enabled_sources", null)?.toSet() ?: all
            val seen = sp.getStringSet("catalog_seen", null)?.toSet() ?: OLD_CATALOG
            val result = (stored + (all - seen)) intersect all
            sp.edit().putStringSet("enabled_sources", result).putStringSet("catalog_seen", all).apply()
            result
        }.also { cachedEnabled = it }
        set(value) = synchronized(selectionLock) {
            cachedEnabled = value
            sp.edit().putStringSet("enabled_sources", value)
                .putStringSet("catalog_seen", BuiltInCatalog.sources.map { it.id }.toSet()).apply()
        }

    var userSources: List<UserSource>
        get() = cachedUserSources ?: (read<List<UserSource>>("user_sources") ?: emptyList()).also { if ("user_sources" !in unreadable) cachedUserSources = it }
        set(value) { saveUserSources(value) }

    internal fun saveUserSources(value: List<UserSource>): Boolean = synchronized(selectionLock) {
        write("user_sources", value).also { if (it) cachedUserSources = value }
    }

    var sourceConfigs: Map<String, SourceConfig>
        get() = cachedConfigs ?: (read<Map<String, SourceConfig>>("source_configs") ?: emptyMap()).also { cachedConfigs = it }
        set(value) = synchronized(selectionLock) {
            cachedConfigs = value
            write("source_configs", value)
        }

    var collapsedGroups: Set<String>
        get() = sp.getStringSet("collapsed_groups", null)?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet("collapsed_groups", value).apply()

    var lastNetMode: String?
        get() = sp.getString("last_net_mode", null)
        set(value) = sp.edit().putString("last_net_mode", value).apply()

    val hwid: String
        get() = synchronized(selectionLock) {
            readString("hwid")?.let { return@synchronized it }
            if ("hwid" in unreadable) return@synchronized ""
            withStorage("hwid") {
                val id = java.util.UUID.randomUUID().toString().replace("-", "")
                if (!ProfileStorage.put(sp.edit(), "hwid", id).commit()) throw java.io.IOException("Cannot save installation ID")
                id
            }.orEmpty()
        }

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0)
        set(value) = sp.edit().putLong("last_update_check", value).apply()

    var notifiedVersion: Int
        get() = sp.getInt("notified_version", 0)
        set(value) = sp.edit().putInt("notified_version", value).apply()

    var shownUpdateVersion: Int
        get() = sp.getInt("shown_update_version", 0)
        set(value) = sp.edit().putInt("shown_update_version", value).apply()

    var selectedNodeId: String?
        get() = sp.getString("selected_node", null)
        set(value) = sp.edit().putString("selected_node", value).apply()

    @Volatile private var cachedLastNode: ProxyNode? = null

    internal val loadedLastNode get() = cachedLastNode

    var lastNode: ProxyNode?
        get() = cachedLastNode ?: read<ProxyNode>("last_node")?.also { cachedLastNode = it }
        set(value) = synchronized(selectionLock) {
            if (value != null && value == cachedLastNode) return@synchronized
            if (write("last_node", value)) cachedLastNode = value
        }

    fun selectNode(node: ProxyNode): Boolean = synchronized(selectionLock) {
        if (cachedLastNode == null && sp.contains(ProfileStorage.sealedKey("last_node"))) lastNode
        if ("last_node" in unreadable) return@synchronized false
        if (cachedLastNode == node && selectedNodeId == node.id) return@synchronized true
        try {
            val editor = sp.edit().putString("selected_node", node.id)
            ProfileStorage.put(editor, "last_node", json.encodeToString(node)).apply()
            cachedLastNode = node
            true
        } catch (_: java.security.GeneralSecurityException) {
            false
        }
    }

    fun replaceSelection(expectedId: String, node: ProxyNode): Boolean = synchronized(selectionLock) {
        if (lastNode?.id != expectedId) return@synchronized false
        selectNode(node)
    }

    @Volatile private var cachedModeNodes: Map<String, ProxyNode>? = null

    var modeNodes: Map<String, ProxyNode>
        get() = cachedModeNodes ?: (read<Map<String, ProxyNode>>("mode_nodes") ?: emptyMap()).also { if ("mode_nodes" !in unreadable) cachedModeNodes = it }
        set(value) = synchronized(selectionLock) {
            if (write("mode_nodes", value)) cachedModeNodes = value
        }

    var backupNodes: BackupNodes
        get() = read<BackupNodes>("backup_nodes") ?: BackupNodes()
        set(value) { write("backup_nodes", value) }

    fun nodeForMode(mode: NetMode): ProxyNode? = modeNodes[mode.name]

    fun rememberForMode(mode: NetMode, node: ProxyNode): Unit = synchronized(selectionLock) {
        if (modeNodes[mode.name] != node) modeNodes = modeNodes + (mode.name to node)
    }

    fun forgetModeNodes(predicate: (ProxyNode) -> Boolean): Unit = synchronized(selectionLock) {
        val kept = modeNodes.filterValues { !predicate(it) }
        if (kept.size != modeNodes.size) modeNodes = kept
    }

    fun allowedForAuto(node: ProxyNode): Boolean = node.id !in settings.excludedAutoNodeIds && eligibleSource(node)

    fun eligibleSource(node: ProxyNode): Boolean {
        val s = settings
        return when {
            node.sourceId == ProxyNode.MANUAL_SOURCE -> true
            userSources.any { it.id == node.sourceId } -> true
            BuiltInCatalog.byId(node.sourceId) != null ->
                s.communityEnabled && node.sourceId in enabledSources && !(s.safeMode && node.isInsecure)
            else -> false
        }
    }

    internal fun flushWrites(): Boolean = sp.edit().commit()

    var vpnWasOn: Boolean
        get() = sp.getBoolean("vpn_was_on", false)
        set(value) = sp.edit().putBoolean("vpn_was_on", value).apply()

    @Volatile private var cachedProfiles: Map<String, NodeProfile>? = null

    var nodeProfiles: Map<String, NodeProfile>
        get() = cachedProfiles ?: (read<Map<String, NodeProfile>>("node_profiles") ?: emptyMap()).also { cachedProfiles = it }
        set(value) = synchronized(selectionLock) {
            val trimmed = if (value.size > MAX_PROFILES) value.entries.toList().takeLast(MAX_PROFILES).associate { it.key to it.value } else value
            cachedProfiles = trimmed
            write("node_profiles", trimmed)
        }

    fun profileFor(nodeId: String): NodeProfile? = nodeProfiles[nodeId]

    fun rememberProfile(nodeId: String, profile: NodeProfile): Unit = synchronized(selectionLock) {
        nodeProfiles = (nodeProfiles - nodeId) + (nodeId to profile)
    }

    @Volatile private var cachedHealth: Map<String, NodeCheck>? = null

    private val health = context.getSharedPreferences("farvater_health", Context.MODE_PRIVATE)

    var nodeHealth: Map<String, NodeCheck>
        get() = cachedHealth ?: loadHealth().also { cachedHealth = it }
        set(value) {
            cachedHealth = value
            health.edit().putString(HEALTH_KEY, json.encodeToString(serializer<Map<String, NodeCheck>>(), value)).apply()
        }

    @Volatile private var cachedFullNodeChecks: Map<String, FullNodeCheck>? = null

    var fullNodeChecks: Map<String, FullNodeCheck>
        get() = cachedFullNodeChecks ?: health.getString("full_node_checks", null)?.let {
            runCatching { json.decodeFromString<Map<String, FullNodeCheck>>(it) }.getOrNull()
        }.orEmpty().also { cachedFullNodeChecks = it }
        set(value) {
            cachedFullNodeChecks = value
            health.edit().putString("full_node_checks", json.encodeToString(value)).apply()
        }

    var serviceChecks: Map<String, ServiceCheck>
        get() = synchronized(serviceHistoryLock) {
            val stored = health.getString("service_checks", null)?.let {
                runCatching { json.decodeFromString<Map<String, ServiceCheck>>(it) }.getOrNull()
            } ?: emptyMap()
            val fresh = ServiceChecks.prune(stored)
            if (stored != fresh) health.edit().putString("service_checks", json.encodeToString(fresh)).apply()
            fresh
        }
        set(value) = synchronized(serviceHistoryLock) {
            health.edit().putString("service_checks", json.encodeToString(ServiceChecks.prune(value))).apply()
        }

    private fun loadHealth(): Map<String, NodeCheck> {
        health.getString(HEALTH_KEY, null)?.let { raw ->
            return runCatching { json.decodeFromString(serializer<Map<String, NodeCheck>>(), raw) }.getOrDefault(emptyMap())
        }
        val old = read<Map<String, NodeCheck>>(HEALTH_KEY) ?: return emptyMap()
        health.edit().putString(HEALTH_KEY, json.encodeToString(serializer<Map<String, NodeCheck>>(), old)).apply()
        sp.edit().remove(HEALTH_KEY).apply()
        return old
    }

    var dayTraffic: DayTraffic
        get() = read<DayTraffic>("day_traffic") ?: DayTraffic()
        set(value) = synchronized(trafficLock) {
            val old = read<DayTraffic>("day_traffic") ?: DayTraffic()
            val reading = if (old.day == value.day) value.copy(bytes = maxOf(old.bytes, value.bytes)) else value
            val today = java.time.LocalDate.now().toEpochDay()
            val history = TrafficHistory.merge(read<List<DayTraffic>>("traffic_history") ?: emptyList(), old, today)
            sp.edit {
                if (reading.day < today - TrafficHistory.RETENTION_DAYS + 1) remove("day_traffic")
                else putString("day_traffic", json.encodeToString(reading))
                putString("traffic_history", json.encodeToString(TrafficHistory.merge(history, reading, today)))
            }
        }

    fun trafficForDay(day: Long): DayTraffic = trafficHistory.firstOrNull { it.day == day } ?: DayTraffic(day, 0)

    val trafficHistory: List<DayTraffic>
        get() = synchronized(trafficLock) {
            val stored = read<List<DayTraffic>>("traffic_history") ?: emptyList()
            val today = java.time.LocalDate.now().toEpochDay()
            val counter = dayTraffic
            val recent = TrafficHistory.merge(stored, counter, today)
            val expired = counter.day < today - TrafficHistory.RETENTION_DAYS + 1
            if (recent != stored || expired) sp.edit {
                putString("traffic_history", json.encodeToString(recent))
                if (expired) remove("day_traffic")
            }
            recent
        }

    fun clearSelection(): Unit = synchronized(selectionLock) {
        if ("last_node" !in unreadable) {
            ProfileStorage.put(sp.edit().remove("selected_node"), "last_node", null).apply()
            cachedLastNode = null
        }
    }

    fun restoreBackup(archive: BackupArchive, includeSecuritySettings: Boolean = false,
        reviewedChanges: BackupSecurityChanges? = null): Unit = synchronized(selectionLock) {
        archive.validated()
        val old = settings
        val incoming = archive.decodedSettings()
        if (includeSecuritySettings) require(reviewedChanges != null && reviewedChanges == archive.securityChanges(old))
        val restored = incoming.copy(
            excludedAutoNodeIds = (old.excludedAutoNodeIds + incoming.excludedAutoNodeIds).also { require(it.size <= 5000) },
            routeRules = if (includeSecuritySettings && "routeRules" in archive.settings) incoming.routeRules else old.routeRules,
            automaticRoutes = if (includeSecuritySettings && "automaticRoutes" in archive.settings) incoming.automaticRoutes else old.automaticRoutes,
            bypassApps = if (includeSecuritySettings) incoming.bypassApps else old.bypassApps,
            safeMode = if (includeSecuritySettings) incoming.safeMode else old.safeMode,
            encryptedDns = if (includeSecuritySettings) incoming.encryptedDns else old.encryptedDns,
            autoUpdates = if (includeSecuritySettings) incoming.autoUpdates else old.autoUpdates,
            sendHwid = if (includeSecuritySettings) incoming.sendHwid else old.sendHwid,
            directRuServices = if (includeSecuritySettings) incoming.directRuServices else old.directRuServices,
            acceptedLegalVersion = old.acceptedLegalVersion,
            onboardingDone = old.onboardingDone,
            activeGalsGroupId = null,
        )
        val manual = archive.manualNodes.map {
            app.farvater.core.parser.SubscriptionParser.parse(it, ProxyNode.MANUAL_SOURCE).nodes.single().raw
        }.joinToString("\n", postfix = "\n")
        val before = sp.all
        val editor = sp.edit()
            .also { ProfileStorage.put(it, "settings", json.encodeToString(restored)) }
            .also { ProfileStorage.put(it, "user_sources", json.encodeToString(archive.subscriptions)) }
            .putStringSet("enabled_sources", archive.enabledSources)
            .putStringSet("catalog_seen", BuiltInCatalog.sources.map { it.id }.toSet())
            .putString("source_configs", json.encodeToString(archive.sourceConfigs))
            .also { ProfileStorage.put(it, MANUAL_NODES_KEY, manual) }
            .putString("backup_language", archive.language)
            .remove("selected_node").also { editor ->
                for (key in listOf("last_node", "mode_nodes", "backup_nodes")) ProfileStorage.put(editor, key, null)
            }
            .remove("node_profiles").remove("collapsed_groups")
        if (!editor.commit()) {
            val rollback = sp.edit()
            val keys = setOf("settings", "user_sources", "enabled_sources", "catalog_seen", "source_configs",
                MANUAL_NODES_KEY, "backup_language", "selected_node", "last_node", "mode_nodes", "backup_nodes",
                "node_profiles", "collapsed_groups").let { raw -> raw + raw.intersect(ProfileStorage.sensitive).map(ProfileStorage::sealedKey) }
            keys.forEach { rollback.remove(it) }
            before.filterKeys { it in keys }.forEach { (key, value) ->
                when (value) {
                    is String -> rollback.putString(key, value)
                    is Boolean -> rollback.putBoolean(key, value)
                    is Long -> rollback.putLong(key, value)
                    is Int -> rollback.putInt(key, value)
                    is Float -> rollback.putFloat(key, value)
                    is Set<*> -> rollback.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            rollback.commit()
            throw java.io.IOException()
        }
        cachedSettings = restored
        cachedUserSources = archive.subscriptions
        cachedEnabled = archive.enabledSources
        cachedConfigs = archive.sourceConfigs
        cachedLastNode = null
        cachedModeNodes = emptyMap()
        cachedProfiles = emptyMap()
    }

    companion object {
        internal const val MANUAL_NODES_KEY = "manual_nodes"
        internal val selectionLock = Any()
        private val trafficLock = Any()
        private val serviceHistoryLock = Any()
        private const val MAX_PROFILES = 300
        private const val HEALTH_KEY = "node_health"
        private val OLD_CATALOG = setOf(
            "zieng2-universal", "igareck-mobile", "igareck-cidr", "rjsxrd-bypass", "rkp-whitelist", "byewhitelists2",
        )
    }

    private inline fun <reified T> read(key: String): T? {
        val text = readString(key) ?: return null
        return try {
            json.decodeFromString(serializer<T>(), text)
        } catch (_: IllegalArgumentException) {
            if (key in ProfileStorage.sensitive) unreadable.add(key)
            null
        }
    }

    private inline fun <reified T> write(key: String, value: T?): Boolean {
        if (key in unreadable) {
            read<T>(key)
            if (key in unreadable) return false
        }
        return withStorage(key) {
            ProfileStorage.put(sp.edit(), key, value?.let { json.encodeToString(serializer<T>(), it) }).apply()
            true
        } == true
    }
}

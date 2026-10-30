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

const val LEGAL_VERSION = 5

@Serializable
enum class UiStyle { IOS, MATERIAL, ROSKOMPOZOR, SUMIE, NOTHING, ORIGIN, HYPER, HARMONY, ONEUI, OXYGEN, WITCH, WINTER, COLOROS }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppSettings(
    val directRuServices: Boolean = true,
    val hideDead: Boolean = false,
    val liveSort: Boolean = false,
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
    val checkedServices: Set<ServiceKind> = emptySet(),
    val serviceCustomUrl: String = "",
    val serviceExpectedCode: Int = 200,
)

@Serializable
data class SourceConfig(val mode: SourceMode? = null, val intervalHours: Int? = null)

@Serializable
data class UserSource(val id: String, val url: String, val title: String)

@Serializable
data class DayTraffic(val day: Long = 0, val bytes: Long = 0)

@Serializable
data class BackupNodes(val mode: String = "", val nodes: List<ProxyNode> = emptyList())

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("farvater", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile private var cachedSettings: AppSettings? = null
    @Volatile private var cachedEnabled: Set<String>? = null
    @Volatile private var cachedUserSources: List<UserSource>? = null
    @Volatile private var cachedConfigs: Map<String, SourceConfig>? = null

    var settings: AppSettings
        get() = cachedSettings ?: (read<AppSettings>("settings") ?: AppSettings()).also { cachedSettings = it }
        set(value) = synchronized(selectionLock) {
            cachedSettings = value
            write("settings", value)
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
        get() = cachedUserSources ?: (read<List<UserSource>>("user_sources") ?: emptyList()).also { cachedUserSources = it }
        set(value) = synchronized(selectionLock) {
            cachedUserSources = value
            write("user_sources", value)
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
        get() = sp.getString("hwid", null) ?: java.util.UUID.randomUUID().toString().replace("-", "")
            .also { sp.edit().putString("hwid", it).apply() }

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

    var lastNode: ProxyNode?
        get() = cachedLastNode ?: read<ProxyNode>("last_node")?.also { cachedLastNode = it }
        set(value) {
            if (value != null && value == cachedLastNode) return
            cachedLastNode = value
            write("last_node", value)
        }

    fun selectNode(node: ProxyNode): Unit = synchronized(selectionLock) {
        if (selectedNodeId != node.id) selectedNodeId = node.id
        lastNode = node
    }

    fun replaceSelection(expectedId: String, node: ProxyNode): Boolean = synchronized(selectionLock) {
        if (lastNode?.id != expectedId) return@synchronized false
        selectNode(node)
        true
    }

    @Volatile private var cachedModeNodes: Map<String, ProxyNode>? = null

    var modeNodes: Map<String, ProxyNode>
        get() = cachedModeNodes ?: (read<Map<String, ProxyNode>>("mode_nodes") ?: emptyMap()).also { cachedModeNodes = it }
        set(value) {
            cachedModeNodes = value
            write("mode_nodes", value)
        }

    var backupNodes: BackupNodes
        get() = read<BackupNodes>("backup_nodes") ?: BackupNodes()
        set(value) = write("backup_nodes", value)

    fun nodeForMode(mode: NetMode): ProxyNode? = modeNodes[mode.name]

    fun rememberForMode(mode: NetMode, node: ProxyNode) {
        if (modeNodes[mode.name] != node) modeNodes = modeNodes + (mode.name to node)
    }

    fun forgetModeNodes(predicate: (ProxyNode) -> Boolean) {
        val kept = modeNodes.filterValues { !predicate(it) }
        if (kept.size != modeNodes.size) modeNodes = kept
    }

    fun allowedForAuto(node: ProxyNode): Boolean {
        val s = settings
        return when {
            node.sourceId == ProxyNode.MANUAL_SOURCE -> true
            userSources.any { it.id == node.sourceId } -> true
            BuiltInCatalog.byId(node.sourceId) != null ->
                s.communityEnabled && node.sourceId in enabledSources && !(s.safeMode && node.isInsecure)
            else -> false
        }
    }

    var vpnWasOn: Boolean
        get() = sp.getBoolean("vpn_was_on", false)
        set(value) = sp.edit().putBoolean("vpn_was_on", value).apply()

    @Volatile private var cachedProfiles: Map<String, NodeProfile>? = null

    var nodeProfiles: Map<String, NodeProfile>
        get() = cachedProfiles ?: (read<Map<String, NodeProfile>>("node_profiles") ?: emptyMap()).also { cachedProfiles = it }
        set(value) {
            val trimmed = if (value.size > MAX_PROFILES) value.entries.toList().takeLast(MAX_PROFILES).associate { it.key to it.value } else value
            cachedProfiles = trimmed
            write("node_profiles", trimmed)
        }

    fun profileFor(nodeId: String): NodeProfile? = nodeProfiles[nodeId]

    fun rememberProfile(nodeId: String, profile: NodeProfile) {
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
        selectedNodeId = null
        lastNode = null
    }

    fun restoreBackup(archive: BackupArchive, includeSecuritySettings: Boolean = false,
        reviewedChanges: BackupSecurityChanges? = null): Unit = synchronized(selectionLock) {
        archive.validated()
        val old = settings
        val incoming = archive.decodedSettings()
        if (includeSecuritySettings) require(reviewedChanges != null && reviewedChanges == archive.securityChanges(old))
        val restored = incoming.copy(
            bypassApps = if (includeSecuritySettings) incoming.bypassApps else old.bypassApps,
            safeMode = if (includeSecuritySettings) incoming.safeMode else old.safeMode,
            encryptedDns = if (includeSecuritySettings) incoming.encryptedDns else old.encryptedDns,
            autoUpdates = if (includeSecuritySettings) incoming.autoUpdates else old.autoUpdates,
            sendHwid = if (includeSecuritySettings) incoming.sendHwid else old.sendHwid,
            directRuServices = if (includeSecuritySettings) incoming.directRuServices else old.directRuServices,
            acceptedLegalVersion = old.acceptedLegalVersion,
            onboardingDone = old.onboardingDone,
        )
        val manual = archive.manualNodes.map {
            app.farvater.core.parser.SubscriptionParser.parse(it, ProxyNode.MANUAL_SOURCE).nodes.single().raw
        }.joinToString("\n", postfix = "\n")
        val before = sp.all
        val editor = sp.edit()
            .putString("settings", json.encodeToString(restored))
            .putString("user_sources", json.encodeToString(archive.subscriptions))
            .putStringSet("enabled_sources", archive.enabledSources)
            .putStringSet("catalog_seen", BuiltInCatalog.sources.map { it.id }.toSet())
            .putString("source_configs", json.encodeToString(archive.sourceConfigs))
            .putString(MANUAL_NODES_KEY, manual)
            .putString("backup_language", archive.language)
            .remove("selected_node").remove("last_node").remove("mode_nodes").remove("backup_nodes")
            .remove("node_profiles").remove("collapsed_groups")
        if (!editor.commit()) {
            val rollback = sp.edit()
            val keys = setOf("settings", "user_sources", "enabled_sources", "catalog_seen", "source_configs",
                MANUAL_NODES_KEY, "backup_language", "selected_node", "last_node", "mode_nodes", "backup_nodes",
                "node_profiles", "collapsed_groups")
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

    private inline fun <reified T> read(key: String): T? =
        sp.getString(key, null)?.let { runCatching { json.decodeFromString(serializer<T>(), it) }.getOrNull() }

    private inline fun <reified T> write(key: String, value: T?) {
        sp.edit().putString(key, value?.let { json.encodeToString(serializer<T>(), it) }).apply()
    }
}

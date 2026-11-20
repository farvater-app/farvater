package app.farvater.ui

import app.farvater.data.userMessage
import app.farvater.R
import app.farvater.data.localizedString
import app.farvater.data.importReason
import app.farvater.data.importReasons
import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.farvater.App
import app.farvater.core.catalog.BuiltInCatalog
import app.farvater.core.model.NetMode
import app.farvater.core.model.NetModeChoice
import app.farvater.core.model.NodeProfile
import app.farvater.core.model.ProxyNode
import app.farvater.core.model.SourceMode
import app.farvater.core.model.onePerEndpoint
import app.farvater.core.parser.SubscriptionParser
import app.farvater.core.parser.Usage
import app.farvater.data.ProbeMethod
import app.farvater.data.AppSettings
import app.farvater.data.ServiceChecks
import app.farvater.data.ServiceTarget
import app.farvater.data.ServiceKind
import app.farvater.data.BackupNodes
import app.farvater.data.GalsPolicy
import app.farvater.data.LEGAL_VERSION
import app.farvater.data.NodeCheck
import app.farvater.data.NodeHealth
import app.farvater.data.SourceConfig
import app.farvater.data.SourceSnapshot
import app.farvater.data.UpdateInfo
import app.farvater.data.UserSource
import app.farvater.engine.Failover
import app.farvater.engine.ProbeNetwork
import app.farvater.engine.AutomaticContext
import app.farvater.engine.AutomaticNodes
import app.farvater.engine.GalsProbe
import app.farvater.engine.PathResult
import app.farvater.data.GalsStrategy
import app.farvater.data.ServiceVerdict
import app.farvater.engine.GalsPlan
import app.farvater.engine.NetModeDetector
import app.farvater.engine.NodeTester
import app.farvater.engine.NodeCheckCoordinator
import app.farvater.engine.NodeCheckFailure
import app.farvater.engine.Pathfinder
import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import app.farvater.net.SafeHttp
import app.farvater.vpn.FarvaterVpnService
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import java.security.MessageDigest

data class SourceUi(
    val id: String,
    val title: String,
    val author: String,
    val license: String,
    val homepage: String,
    val forum: String? = null,
    val description: String,
    val community: Boolean,
    val enabled: Boolean,
    val nodeCount: Int,
    val updatedAt: Long,
    val error: String?,
    val loading: Boolean,
    val hidden: String? = null,
    val mode: SourceMode = SourceMode.ANY,
    val intervalHours: Int? = null,
    val ownIntervalHours: Int? = null,
    val inMode: Boolean = true,
    val usage: Usage? = null,
    val announce: String? = null,
    val networkUnavailable: Boolean = false,
)

data class TestProgress(val done: Int, val total: Int, val alive: Int)

data class GalsStep(val node: String, val profile: NodeProfile)

data class UiState(
    val settings: AppSettings = AppSettings(),
    val sources: List<SourceUi> = emptyList(),
    val nodes: List<ProxyNode> = emptyList(),
    val results: Map<String, TestResult> = emptyMap(),
    val selectedId: String? = null,
    val progress: TestProgress? = null,
    val refreshing: Boolean = false,
    val message: String? = null,
    val pendingImport: String? = null,
    val hiddenInsecure: Int = 0,
    val duplicates: Int = 0,
    val update: UpdateInfo? = null,
    val updateStage: UpdateStage = UpdateStage.Idle,
    val showUpdate: Boolean = false,
    val netMode: NetMode? = null,
    val detectedMode: NetMode? = null,
    val detecting: Boolean = false,
    val savedNode: ProxyNode? = null,
    val pathfinding: GalsStep? = null,
    val gals: Boolean = false,
    val sensing: Boolean = false,
    val refining: Boolean = false,
    val galsNet: NetMode? = null,
    val dead: Set<String> = emptySet(),
    val groupedDead: Set<String> = dead,
    val preflight: ProxyNode? = null,
    val deadPrompt: ProxyNode? = null,
    val checking: Set<String> = emptySet(),
    val collapsedGroups: Set<String> = emptySet(),
    val trafficHistory: List<app.farvater.data.DayTraffic> = emptyList(),
    val backupBusy: Boolean = false,
    val backupPreview: Pair<Int, Int>? = null,
    val backupSecurityChanges: app.farvater.data.BackupSecurityChanges = app.farvater.data.BackupSecurityChanges(),
    val serviceChecks: Map<String, app.farvater.data.ServiceCheck> = emptyMap(),
    val serviceChecking: Boolean = false,
) {
    val selectedNode: ProxyNode?
        get() = nodes.firstOrNull { it.id == selectedId } ?: savedNode?.takeIf { it.id == selectedId }
    fun sourceTitle(id: String, manualTitle: String): String =
        if (id == ProxyNode.MANUAL_SOURCE) manualTitle else sources.firstOrNull { it.id == id }?.title ?: id
}

sealed interface UiEvent {
    data object ApplyBackupLanguage : UiEvent
    data class RequestVpnPermission(val intent: Intent) : UiEvent
    data class StartActivity(val intent: Intent) : UiEvent
}

sealed interface UpdateStage {
    data object Idle : UpdateStage
    data object Checking : UpdateStage
    data class Downloading(val progress: Float) : UpdateStage
    data class Failed(val message: String) : UpdateStage
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private fun messageText(id: Int, vararg args: Any): String = getApplication<Application>().localizedString(id, *args)

    private val prefs = App.prefs
    private val repo = App.repo
    private val updates = App.updates

    private val snapshots = repo.snapshots
    private val loading = MutableStateFlow<Set<String>>(emptySet())
    private val loadingCounts = HashMap<String, Int>()
    private val results = cachedResults
    private val displayOnlyResults = MutableStateFlow<Map<String, TestResult>>(emptyMap())

    private fun displayResults(): Map<String, TestResult> {
        val paths = results.value
        val transient = displayOnlyResults.value
        if (transient.isEmpty()) return paths
        val showTcp = prefs.settings.probeMethod == ProbeMethod.TCP
        return paths + transient.filter { (id, result) ->
            (!result.endpointOnly || showTcp) && (paths[id]?.at ?: Long.MIN_VALUE) <= result.at
        }
    }


    private val _state = MutableStateFlow(
        cachedState?.copy(
            settings = prefs.settings,
            selectedId = prefs.selectedNodeId,
            savedNode = savedSelection(),
            progress = null,
            pathfinding = null,
            gals = false,
            sensing = false,
            refining = false,
            galsNet = null,
            preflight = null,
            deadPrompt = null,
            checking = emptySet(),
            collapsedGroups = prefs.collapsedGroups,
            refreshing = false,
            message = null,
            pendingImport = null,
            backupBusy = false,
            backupPreview = null,
            serviceChecking = false,
            updateStage = UpdateStage.Idle,
            showUpdate = false,
        ) ?: UiState(
            settings = prefs.settings,
            selectedId = prefs.selectedNodeId,
            savedNode = savedSelection(),
            collapsedGroups = prefs.collapsedGroups,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    val vpnState = VpnBus.state.asStateFlow()
    val traffic = VpnBus.traffic.asStateFlow()
    val speedHistory = VpnBus.speedHistory.asStateFlow()
    val failedChecks = VpnBus.failedChecks.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var pendingNode: ProxyNode? = null
    private var pendingAutomatic = false
    private var pendingStopEpoch = 0L
    private var pendingContext: AutomaticContext? = null
    private var pendingRestart = false
    private val automaticRevision: Long get() = VpnBus.automaticEpoch.get()
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var testJob: Job? = null
    @Volatile private var testing = false

    private val rebuildRequests = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var detected: NetMode? = prefs.lastNetMode?.let { runCatching { NetMode.valueOf(it) }.getOrNull() }
    private var detectJob: Job? = null
    private val attempts = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val healthLock = NodeCheckCoordinator.healthLock

    init {
        NodeCheckCoordinator.cancelIfModeChanged(modeKey())
        if (results.value.isEmpty() || cachedResultsMode != modeKey()) results.value = NodeHealth.results(prefs.nodeHealth, modeKey())
        cachedResultsMode = modeKey()
        _state.update { it.copy(results = results.value, dead = deadFor(prefs.nodeHealth, modeKey()), groupedDead = groupedDeadFor(it.nodes)) }
        viewModelScope.launch(Dispatchers.Default) {
            healthLock.withLock {
                val history = prefs.nodeHealth
                val fresh = NodeHealth.prune(history)
                if (fresh.size != history.size) prefs.nodeHealth = fresh
                val services = prefs.serviceChecks
                if (prefs.settings.serviceChecksEnabled) _state.update { it.copy(serviceChecks = services) }
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            for (request in rebuildRequests) {
                rebuildLocked()
                delay(REBUILD_GAP_MS)
            }
        }
        viewModelScope.launch { rebuildVersion.collect { rebuildRequests.trySend(Unit) } }
        viewModelScope.launch { snapshots.collect { rebuildRequests.trySend(Unit) } }
        viewModelScope.launch { _state.collect { cachedState = it } }
        viewModelScope.launch {
            var failureSession = -1L
            NodeCheckCoordinator.state.collect { work ->
                val mode = modeKey()
                val sameMode = work.mode == mode
                if (cachedResultsMode != mode) {
                    displayOnlyResults.value = emptyMap()
                    results.value = NodeHealth.results(prefs.nodeHealth, mode)
                    cachedResultsMode = mode
                }
                testing = sameMode && work.progress != null
                if (sameMode) {
                    results.update { current ->
                        current + work.results.filter { (id, r) -> r.evaluated && !r.endpointOnly && (current[id]?.at ?: Long.MIN_VALUE) <= r.at }
                    }
                    displayOnlyResults.update { current ->
                        current + work.results.filter { (id, r) ->
                            (r.endpointOnly || !r.evaluated) && (current[id]?.at ?: Long.MIN_VALUE) <= r.at
                        }
                    }
                }
                val display = displayResults()
                _state.update { old -> old.copy(
                    settings = prefs.settings,
                    results = display,
                    progress = work.progress?.takeIf { sameMode }?.let { TestProgress(it.done, it.total, it.alive) },
                    checking = if (sameMode) work.checking else emptySet(),
                    serviceChecking = sameMode && work.services,
                    serviceChecks = if (prefs.settings.serviceChecksEnabled) prefs.serviceChecks else emptyMap(),
                    dead = deadFor(prefs.nodeHealth, modeKey()),
                    groupedDead = groupedDeadFor(old.nodes),
                    nodes = if (prefs.settings.liveSort || !testing) sortNodes(old.nodes, display) else old.nodes,
                ) }
                if (sameMode && work.failure != null && failureSession != work.session) {
                    failureSession = work.session
                    toast(messageText(if (work.failure == NodeCheckFailure.TIMEOUT) R.string.checks_timed_out else R.string.checks_start_failed))
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ids = fetchTargets().map { it.first } + ProxyNode.MANUAL_SOURCE
            ids.filter { it !in snapshots.value }.forEach { repo.loadSnapshot(it) }
            rebuild()
            refreshDue()
        }
        viewModelScope.launch {
            while (true) {
                delay(DUE_CHECK_MS)
                refreshDue()
            }
        }
        detectNetMode()
        viewModelScope.launch {
            VpnBus.failedChecks.collect { failures ->
                if (failures == VpnBus.STALL_CHECKS) reportDeadNode()
            }
        }
        viewModelScope.launch {
            VpnBus.state.collect { vpn ->
                if (vpn is VpnState.Failed) toast(vpn.message)
                val node = (vpn as? VpnState.Connected)?.node ?: (vpn as? VpnState.Connecting)?.node
                _state.update { it.copy(groupedDead = groupedDeadFor(it.nodes)) }
                if (node != null && node.id != _state.value.selectedId) {
                    _state.update { it.copy(selectedId = node.id, savedNode = node) }
                }
            }
        }
        viewModelScope.launch {
            VpnBus.netMode.collect { mode ->
                if (prefs.settings.netMode != NetModeChoice.AUTO || mode == detected) return@collect
                val before = modeKey()
                detected = mode
                lastDetect = System.currentTimeMillis()
                if (modeKey() != before) reloadHealth()
                rebuildNow()
            }
        }
    }

    fun onForeground() {
        rebuild()
        detectNetMode()
        if (prefs.settings.autoUpdates && System.currentTimeMillis() - prefs.lastUpdateCheck > UPDATE_CHECK_GAP_MS) {
            checkUpdates(manual = false)
        }
    }

    fun checkUpdates(manual: Boolean) {
        if (updateCheckJob?.isActive == true || updateDownloadJob?.isActive == true) return
        if (_state.value.updateStage is UpdateStage.Checking || _state.value.updateStage is UpdateStage.Downloading) return
        updateCheckJob = viewModelScope.launch {
            if (manual) _state.update { it.copy(updateStage = UpdateStage.Checking) }
            val result = updates.check(viaTunnel = VpnBus.state.value is VpnState.Connected)
            result.onSuccess { prefs.lastUpdateCheck = System.currentTimeMillis() }
            val info = result.getOrNull()
            val autoShow = !manual && info != null && info.versionCode > prefs.shownUpdateVersion
            if (autoShow) {
                prefs.shownUpdateVersion = info.versionCode
                prefs.notifiedVersion = maxOf(prefs.notifiedVersion, info.versionCode)
            }
            _state.update {
                it.copy(update = info, updateStage = UpdateStage.Idle, showUpdate = it.showUpdate || autoShow || (manual && info != null))
            }
            when {
                !manual -> Unit
                result.isFailure -> toast(messageText(R.string.update_check_failed))
                info == null -> toast(messageText(R.string.update_latest))
            }
        }
    }

    fun openUpdate() {
        if (_state.value.update != null) _state.update { it.copy(showUpdate = true) } else checkUpdates(manual = true)
    }

    fun dismissUpdate() = _state.update {
        it.copy(
            showUpdate = false,
            updateStage = when (it.updateStage) {
                is UpdateStage.Downloading, UpdateStage.Checking -> it.updateStage
                else -> UpdateStage.Idle
            },
        )
    }

    fun installUpdate() {
        val info = _state.value.update ?: return
        if (updateDownloadJob?.isActive == true || _state.value.updateStage is UpdateStage.Downloading) return
        if (!updates.canInstall()) {
            toast(messageText(R.string.update_permission))
            _events.tryEmit(UiEvent.StartActivity(updates.installPermissionIntent()))
            return
        }
        updateCheckJob?.cancel()
        updateDownloadJob = viewModelScope.launch {
            _state.update { it.copy(updateStage = UpdateStage.Downloading(0f)) }
            var last = 0f
            val file = updates.download(info, viaTunnel = VpnBus.state.value is VpnState.Connected) { p ->
                if (p < last || p - last >= 0.01f || p >= 1f) {
                    last = p
                    _state.update { it.copy(updateStage = UpdateStage.Downloading(p)) }
                }
            }
            file.onSuccess { apk ->
                _state.update { it.copy(updateStage = UpdateStage.Idle) }
                _events.tryEmit(UiEvent.StartActivity(updates.installIntent(apk)))
            }.onFailure { e ->
                _state.update { it.copy(updateStage = UpdateStage.Failed(e.userMessage(getApplication()))) }
            }
        }
    }

    private fun effectiveMode(settings: AppSettings): NetMode? = when (settings.netMode) {
        NetModeChoice.WHITE -> NetMode.WHITE
        NetModeChoice.BLACK -> NetMode.BLACK
        NetModeChoice.AUTO -> detected
    }

    fun detectNetMode(force: Boolean = false): Job? {
        if (prefs.settings.netMode != NetModeChoice.AUTO) return null
        detectJob?.takeIf { it.isActive }?.let { return it }
        val now = System.currentTimeMillis()
        if (!force && now - lastDetect < DETECT_GAP_MS) return null
        lastDetect = now
        return viewModelScope.launch {
            _state.update { it.copy(detecting = true) }
            val mode = NetModeDetector.detect()
            var changed = false
            if (mode != null) {
                val before = modeKey()
                detected = mode
                prefs.lastNetMode = mode.name
                changed = modeKey() != before
                if (changed) reloadHealth()
            }
            _state.update { it.copy(detecting = false) }
            rebuildNow()
            if (changed) switchForMode(mode)
        }.also { detectJob = it }
    }

    fun setNetMode(choice: NetModeChoice) {
        val before = modeKey()
        updateSettings { it.copy(netMode = choice) }
        if (modeKey() != before) {
            reloadHealth()
            if (choice != NetModeChoice.AUTO) {
                viewModelScope.launch {
                    rebuildNow()
                    switchForMode(effectiveMode(prefs.settings))
                }
            }
        }
        if (choice == NetModeChoice.AUTO) detectNetMode(force = true)
    }

    private fun switchForMode(mode: NetMode?) {
        if (mode == null || !prefs.settings.modeNodes || _state.value.gals || testing) return
        val vpn = VpnBus.state.value
        if (vpn is VpnState.Connected || vpn is VpnState.Connecting) {
            FarvaterVpnService.recheckMode(getApplication(), mode)
            return
        }
        val target = prefs.nodeForMode(mode) ?: return
        if (target.id == _state.value.selectedId || _state.value.nodes.none { it.id == target.id }) return
        setSelected(target)
    }

    private fun sourceMode(id: String): SourceMode =
        prefs.sourceConfigs[id]?.mode ?: BuiltInCatalog.byId(id)?.mode ?: SourceMode.ANY

    fun setSourceMode(id: String, mode: SourceMode) {
        val current = prefs.sourceConfigs[id] ?: SourceConfig()
        prefs.sourceConfigs = prefs.sourceConfigs + (id to current.copy(mode = mode))
        rebuild()
    }

    fun setSourceInterval(id: String, hours: Int?) {
        val current = prefs.sourceConfigs[id] ?: SourceConfig()
        prefs.sourceConfigs = prefs.sourceConfigs + (id to current.copy(intervalHours = hours))
        rebuild()
        refreshDue()
    }

    private fun intervalMs(id: String): Long? {
        val chosen = prefs.sourceConfigs[id]?.intervalHours
        if (chosen == 0) return null
        val hours = chosen ?: snapshots.value[id]?.intervalHours?.takeIf { it > 0 } ?: DEFAULT_INTERVAL_H
        return hours * HOUR_MS
    }

    private fun refreshDue() {
        val now = System.currentTimeMillis()
        val due = fetchTargets().filter { (id, _) ->
            if (id in loading.value) return@filter false
            if (now - (attempts[id] ?: 0L) < RETRY_MS) return@filter false
            val snap = snapshots.value[id]
            if (snap == null || snap.updatedAt == 0L) return@filter true
            val interval = intervalMs(id) ?: return@filter false
            now - snap.updatedAt >= interval
        }
        if (due.isEmpty()) return
        due.forEach { attempts[it.first] = now }
        val viaTunnel = VpnBus.state.value is VpnState.Connected
        viewModelScope.launch {
            due.map { (id, urls) -> async(Dispatchers.IO) { refreshOne(id, urls, viaTunnel) } }.awaitAll()
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val viaTunnel = VpnBus.state.value is VpnState.Connected
            fetchTargets().map { (id, urls) -> async(Dispatchers.IO) { refreshOne(id, urls, viaTunnel) } }.awaitAll()
            _state.update { it.copy(refreshing = false) }
            val loaded = fetchTargets().count { snapshots.value[it.first]?.error == null }
            if (loaded == 0 && fetchTargets().isNotEmpty() && !viaTunnel) {
                toast(messageText(R.string.sources_unreachable))
            }
        }
    }

    private suspend fun refreshOne(id: String, urls: List<String>, viaTunnel: Boolean, manual: Boolean = false,
        communityConsent: Boolean = prefs.settings.communityEnabled) {
        synchronized(loadingCounts) {
            loadingCounts[id] = (loadingCounts[id] ?: 0) + 1
            loading.value = loadingCounts.keys.toSet()
        }
        rebuild()
        val headers = deviceHeaders(id)
        try {
            repo.fetch(id, urls, viaTunnel, headers, json = id !in communityIds, isCurrent = {
                synchronized(selectionLock) {
                    val current = if (manual) {
                        val catalog = BuiltInCatalog.byId(id)
                        if (catalog != null) catalog.mirrors == urls && prefs.settings.communityEnabled == communityConsent
                        else prefs.userSources.any { it.id == id && listOf(it.url) == urls }
                    } else fetchTargets().any { it.first == id && it.second == urls }
                    current && deviceHeaders(id) == headers
                }
            })
        } finally {
            synchronized(loadingCounts) {
                val remaining = (loadingCounts[id] ?: 1) - 1
                if (remaining == 0) loadingCounts.remove(id) else loadingCounts[id] = remaining
                loading.value = loadingCounts.keys.toSet()
            }
            rebuild()
        }
    }

    private fun deviceHeaders(sourceId: String): Map<String, String> {
        if (sourceId in communityIds || !prefs.settings.sendHwid) return emptyMap()
        fun ascii(v: String?) = v.orEmpty().filter { it in ' '..'~' }.trim().ifEmpty { "unknown" }
        return mapOf(
            "x-hwid" to prefs.hwid,
            "x-device-os" to "Android",
            "x-ver-os" to ascii(android.os.Build.VERSION.RELEASE),
            "x-device-model" to ascii(android.os.Build.MODEL),
        )
    }

    val hwid: String get() = prefs.hwid

    fun refreshSource(id: String) {
        val urls = BuiltInCatalog.byId(id)?.mirrors ?: prefs.userSources.firstOrNull { it.id == id }?.let { listOf(it.url) } ?: return
        val consent = prefs.settings.communityEnabled
        viewModelScope.launch { refreshOne(id, urls, VpnBus.state.value is VpnState.Connected, manual = true, communityConsent = consent) }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        prefs.enabledSources = if (enabled) prefs.enabledSources + id else prefs.enabledSources - id
        rebuild()
        if (enabled && snapshots.value[id] == null) {
            BuiltInCatalog.byId(id)?.let { src ->
                viewModelScope.launch(Dispatchers.IO) {
                    val cached = repo.loadSnapshot(id)
                    if (cached == null) {
                        refreshOne(id, src.mirrors, VpnBus.state.value is VpnState.Connected)
                    } else {
                        rebuild()
                        refreshDue()
                    }
                }
            }
        }
    }

    fun addSubscription(url: String) {
        val clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            toast(messageText(R.string.subscription_url_invalid))
            return
        }
        if (SafeHttp.parse(clean) == null) {
            toast(messageText(R.string.subscription_url_blocked))
            return
        }
        if (prefs.userSources.any { it.url == clean }) {
            toast(messageText(R.string.subscription_exists))
            return
        }
        val source = UserSource(id = "user-" + shortHash(clean), url = clean, title = hostOf(clean))
        prefs.userSources = prefs.userSources + source
        rebuild()
        viewModelScope.launch { refreshOne(source.id, listOf(clean), VpnBus.state.value is VpnState.Connected) }
    }

    fun removeUserSource(id: String) {
        prefs.userSources = prefs.userSources.filterNot { it.id == id }
        updateSettings { settings -> settings.copy(
            nodeNames = settings.nodeNames.filterKeys { !it.startsWith("$id/") },
            galsGroups = settings.galsGroups.map { group -> group.copy(sourceIds = group.sourceIds - id) },
        ) }
        repo.deleteCache(id)
        dropSelectionIf { it.sourceId == id }
        rebuild()
    }

    fun importText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            toast(messageText(R.string.clipboard_empty))
            return
        }
        if (trimmed.length > MAX_IMPORT) {
            toast(messageText(R.string.import_too_large))
            return
        }
        if (trimmed.lines().size == 1 && (trimmed.startsWith("https://") || trimmed.startsWith("http://"))) {
            addSubscription(trimmed)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val parsed = SubscriptionParser.parse(trimmed, ProxyNode.MANUAL_SOURCE)
            if (parsed.nodes.isEmpty()) {
                toast(if (parsed.skipped.isEmpty()) messageText(R.string.import_no_links)
                    else messageText(R.string.import_rejected, getApplication<Application>().importReasons(parsed.skipped)))
                return@launch
            }
            val added = synchronized(repo) {
                val count = repo.appendManual(parsed.nodes)
                count
            }
            rebuild()
            toast(if (added > 0) messageText(R.string.nodes_added, added) else messageText(R.string.nodes_exist))
        }
    }

    private var backupArchive: app.farvater.data.BackupArchive? = null

    fun writeBackup(uri: android.net.Uri) {
        if (_state.value.backupBusy) return
        _state.update { it.copy(backupBusy = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val application = getApplication<Application>()
                    val encoded = synchronized(repo) {
                        synchronized(selectionLock) {
                            app.farvater.data.BackupArchive.create(prefs, repo.loadManual().nodes,
                                app.farvater.data.AppLanguages.selected(application)).encode().toByteArray(Charsets.UTF_8)
                        }
                    }
                    require(encoded.size <= app.farvater.data.BackupArchive.MAX_BYTES)
                    application.contentResolver.openOutputStream(uri, "wt")?.use { it.write(encoded) } ?: throw java.io.IOException()
                }
                toast(messageText(R.string.backup_exported))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                toast(messageText(R.string.backup_write_failed))
            } finally {
                _state.update { it.copy(backupBusy = false) }
            }
        }
    }

    fun readBackup(uri: android.net.Uri) {
        if (_state.value.backupBusy) return
        dismissBackup()
        _state.update { it.copy(backupBusy = true) }
        viewModelScope.launch {
            try {
                val archive = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        app.farvater.data.BackupArchive.read(it)
                    } ?: throw java.io.IOException()
                }
                backupArchive = archive
                _state.update { it.copy(backupPreview = archive.subscriptions.size to archive.manualNodes.size,
                    backupSecurityChanges = archive.securityChanges(prefs.settings)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                toast(messageText(R.string.backup_read_failed))
            } finally {
                _state.update { it.copy(backupBusy = false) }
            }
        }
    }

    fun dismissBackup() {
        backupArchive = null
        _state.update { it.copy(backupPreview = null, backupSecurityChanges = app.farvater.data.BackupSecurityChanges()) }
    }

    fun restoreBackup(includeSecuritySettings: Boolean = false) {
        val archive = backupArchive ?: return
        val current = _state.value
        if (current.backupBusy) return
        if (VpnBus.state.value is VpnState.Connected || VpnBus.state.value is VpnState.Connecting ||
            current.progress != null || current.gals || current.preflight != null || current.detecting ||
            current.checking.isNotEmpty() || loading.value.isNotEmpty() || current.refreshing || current.serviceChecking) {
            toast(messageText(R.string.backup_wait_idle))
            return
        }
        _state.update { it.copy(backupBusy = true) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) {
                        synchronized(repo) {
                            synchronized(selectionLock) {
                                check(VpnBus.state.value !is VpnState.Connected && VpnBus.state.value !is VpnState.Connecting)
                                prefs.restoreBackup(archive, includeSecuritySettings, current.backupSecurityChanges)
                                repo.resetInventory()
                                repo.loadSnapshot(ProxyNode.MANUAL_SOURCE)
                                results.value = emptyMap()
                                displayOnlyResults.value = emptyMap()
                            }
                        }
                    }
                    dismissBackup()
                    pendingNode = null
                    _state.update { it.copy(settings = prefs.settings, selectedId = null, savedNode = null,
                        results = emptyMap(), collapsedGroups = emptySet()) }
                    rebuild()
                    app.farvater.data.UpdateWorker.schedule(getApplication())
                    _events.tryEmit(UiEvent.ApplyBackupLanguage)
                    toast(messageText(R.string.backup_restored))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                toast(messageText(R.string.backup_restore_failed))
            } finally {
                _state.update { it.copy(backupBusy = false) }
            }
        }
    }

    fun requestImport(text: String) = _state.update { it.copy(pendingImport = text) }
    fun rejectImport() = toast(messageText(R.string.import_invalid))
    fun confirmImport(expected: String) {
        if (consumeImport(expected)) importText(expected)
    }
    fun dismissImport(expected: String) {
        consumeImport(expected)
    }
    private fun consumeImport(expected: String): Boolean {
        while (true) {
            val current = _state.value
            if (current.pendingImport != expected) return false
            if (_state.compareAndSet(current, current.copy(pendingImport = null))) return true
        }
    }

    fun renameNode(node: ProxyNode, name: String) {
        if (node.sourceId != ProxyNode.MANUAL_SOURCE && prefs.userSources.none { it.id == node.sourceId }) return
        if (_state.value.nodes.none { it.id == node.id && it.sourceId == node.sourceId }) return
        updateSettings { it.rename(node, name) }
    }

    fun deleteManualNode(node: ProxyNode) {
        if (node.sourceId != ProxyNode.MANUAL_SOURCE) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                synchronized(repo) {
                    repo.removeManual(node.id)
                }
                withContext(Dispatchers.Main) {
                    updateSettings { it.rename(node, "") }
                    dropSelectionIf { it.id == node.id }
                }
                rebuild()
            }
        }
    }

    fun testSource(sourceId: String) {
        if (pathJob?.isActive == true) return
        val mode = effectiveMode(prefs.settings)
        val nodes = snapshots.value[sourceId]?.nodes.orEmpty().filter { node ->
            node.sourceId == sourceId && prefs.allowedForAuto(node) && sourceMode(sourceId).fits(mode)
        }.distinctBy { it.id }
        checkNodes(nodes, fullScope = false)
    }

    fun testAll() = checkNodes(_state.value.nodes, fullScope = true)

    fun testForServices() = checkNodes(_state.value.nodes, fullScope = true, method = ProbeMethod.HTTPS)

    private fun checkNodes(nodes: List<ProxyNode>, fullScope: Boolean, method: ProbeMethod = prefs.settings.probeMethod) {
        if (testJob?.isActive == true) return
        if (nodes.isEmpty()) {
            toast(messageText(R.string.nodes_list_empty))
            return
        }
        val connected = (VpnBus.state.value as? VpnState.Connected)?.node?.takeIf { node -> nodes.any { it.id == node.id } }
        testJob = NodeCheckCoordinator.testAll(getApplication(), nodes, prefs.settings.copy(probeMethod = method), modeKey(), connected,
            sameAccount(nodes), !_state.value.gals && pathJob?.isActive != true, fullScope = fullScope)
    }

    fun toggleGroup(sourceId: String) {
        val groups = _state.value.collapsedGroups
        val next = if (sourceId in groups) groups - sourceId else groups + sourceId
        prefs.collapsedGroups = next
        _state.update { it.copy(collapsedGroups = next) }
    }

    fun testNode(node: ProxyNode) {
        val started = System.currentTimeMillis()
        val connected = (VpnBus.state.value as? VpnState.Connected)?.node?.id == node.id
        val job = NodeCheckCoordinator.testNode(getApplication(), node, prefs.settings, modeKey(), connected, sameAccount(listOf(node))) ?: return
        viewModelScope.launch {
            job.join()
            if (job.isCancelled) return@launch
            val result = NodeCheckCoordinator.state.value.results[node.id]?.takeIf { it.at >= started } ?: return@launch
            val name = splitFlag(prefs.settings.nodeLabel(node)).second
            toast(when {
                result.reason == app.farvater.engine.TestFailure.UNSUPPORTED -> messageText(R.string.probe_unsupported)
                result.method == TestMethod.TCP && result.alive -> messageText(R.string.latency_tcp, result.delayMs)
                !result.evaluated -> messageText(R.string.node_test_incomplete)
                result.alive -> messageText(R.string.node_test_alive, name, result.delayMs)
                else -> messageText(R.string.dead_node_title, name)
            })
        }
    }

    fun cancelTest() {
        NodeCheckCoordinator.cancel()
        testJob?.cancel()
        cancelGals()
        _state.update { it.copy(progress = null) }
    }

    private fun cancelGals(): Unit = synchronized(selectionLock) {
        VpnBus.automaticEpoch.incrementAndGet()
        pathJob?.cancel()
        if (pendingAutomatic) {
            pendingNode = null
            pendingAutomatic = false
            pendingContext = null
        }
        _state.update { it.copy(pathfinding = null, gals = false, sensing = false, galsNet = null, refining = false) }
    }

    private var pathJob: Job? = null

    fun cancelServiceChecks() = NodeCheckCoordinator.cancelServices()

    fun findWorking() {
        if (pathJob?.isActive == true || testJob?.isActive == true) return
        cancelServiceChecks()
        val stopEpoch = VpnBus.stopEpoch.get()
        pathJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            val me = coroutineContext.job
            _state.update { it.copy(gals = true, sensing = true) }
            val net = try {
                senseNetwork()
            } finally {
                if (pathJob === me) _state.update { it.copy(sensing = false) }
            }
            coroutineContext.ensureActive()
            if (stopEpoch != VpnBus.stopEpoch.get()) {
                cancelTest()
                return@launch
            }
            _state.update { it.copy(galsNet = net) }
            findWorkingNow(stopEpoch)
        }
        pathJob?.start()
    }

    private suspend fun senseNetwork(): NetMode? {
        val network = ProbeNetwork(getApplication<Application>())
        val net = NetModeDetector.detect() ?: return null
        if (!network.current()) return null
        val choice = prefs.settings.netMode
        if (choice == NetModeChoice.AUTO) {
            val before = modeKey()
            detected = net
            prefs.lastNetMode = net.name
            if (modeKey() != before) reloadHealth()
            lastDetect = System.currentTimeMillis()
            rebuildNow()
        } else if (choice.name != net.name) {
            toast(
                if (net == NetMode.WHITE) messageText(R.string.gals_white_mismatch)
                else messageText(R.string.gals_black_mismatch)
            )
        }
        return net
    }

    private fun findWorkingNow(stopEpoch: Long = VpnBus.stopEpoch.get()) {
        val network = ProbeNetwork(getApplication<Application>())
        val policy = GalsPolicy.from(prefs.settings)
        val revision = automaticRevision
        fun eligible(node: ProxyNode) = revision == automaticRevision && eligibleAutomaticNode(node, policy)
        if (!policy.legacy) {
            findConfigured(policy, revision, stopEpoch, network)
            return
        }
        if (_state.value.nodes.isEmpty()) {
            _state.update { it.copy(gals = false, galsNet = null) }
            checkNodes(_state.value.nodes, fullScope = true, method = ProbeMethod.AUTO)
            return
        }
        pathJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            val me = coroutineContext.job
            val settings = prefs.settings
            val mode = _state.value.galsNet ?: effectiveMode(settings)
            val ordinaryBlocking = mode == NetMode.BLACK
            val tried = HashSet<String>()
            val deferred = HashSet<String>()
            val failedKinds = HashSet<String>()
            val failedHosts = HashSet<String>()
            var dnsBlocked = false
            val verified = LinkedHashMap<String, Pair<ProxyNode, Long>>()
            val partial = HashMap<String, Long>()
            val partialProfiles = HashMap<String, NodeProfile>()
            var connected: ProxyNode? = null

            suspend fun attempt(node: ProxyNode): Boolean {
                if (!network.current() || stopEpoch != VpnBus.stopEpoch.get() || !eligible(node)) return false
                val found = try {
                    Pathfinder.find(node, prefs.profileFor(node.id), ordinaryBlocking, checkDns = settings.encryptedDns) { t ->
                        _state.update { it.copy(pathfinding = GalsStep(prefs.settings.nodeLabel(node), t)) }
                    }
                } finally {
                    if (pathJob === me) _state.update { it.copy(pathfinding = null) }
                }
                coroutineContext.ensureActive()
                if (!network.current() || stopEpoch != VpnBus.stopEpoch.get() || !eligible(node)) return false
                if (found.deferred) {
                    deferred += node.id
                    return false
                }
                deferred -= node.id
                tried += node.id
                if (found.dnsBlocked) dnsBlocked = true
                val profile = found.profile
                if (profile == null) {
                    val half = found.partial
                    if (half != null && found.telegramBlocked) {
                        partial[node.id] = found.speedBps
                        partialProfiles[node.id] = half
                    } else if (!found.telegramBlocked && !found.dnsBlocked) {
                        failedKinds += GalsPlan.kind(node)
                        failedHosts += node.address.lowercase()
                    }
                    return true
                }
                prefs.rememberProfile(node.id, profile)
                verified[node.id] = node to found.speedBps
                if (connected == null && selectAutomatic(node, mode, stopEpoch, policy, revision, network)) {
                    connected = node
                    _state.update { it.copy(gals = false, galsNet = null, refining = true) }
                }
                return true
            }

            try {
                val since = System.currentTimeMillis()
                checkNodes(_state.value.nodes, fullScope = true, method = ProbeMethod.AUTO)
                var scanned = 0
                while (scanned + deferred.size < PATH_CANDIDATES && network.current() && stopEpoch == VpnBus.stopEpoch.get()) {
                    val running = testJob?.isActive == true
                    val node = GalsPlan.next(_state.value.nodes.filter(::eligible), results.value,
                        _state.value.dead, tried + deferred, failedKinds, failedHosts, since)
                    if (node == null) {
                        if (!running) break
                        delay(SCOUT_MS)
                        continue
                    }
                    if (attempt(node)) scanned++
                }
                testJob?.join()
                for (node in _state.value.nodes.filter { it.id in deferred && eligible(it) }.take(PATH_CANDIDATES - scanned)) {
                    if (attempt(node)) scanned++
                }
            } finally {
                if (pathJob === me) _state.update { it.copy(gals = false, galsNet = null, refining = false) }
            }

            if (!network.current() || stopEpoch != VpnBus.stopEpoch.get() || revision != automaticRevision ||
                !policy.sameBehavior(GalsPolicy.from(prefs.settings))) return@launch
            val current = connected
            if (current != null) {
                val available = verified.filterValues { eligible(it.first) }
                val bestId = GalsPlan.fastest(available.mapValues { it.value.second }) ?: return@launch
                val (best, bestSpeed) = verified.getValue(bestId)
                val currentSpeed = verified[current.id]?.second ?: 0L
                val vpn = VpnBus.state.value
                val stillOnCurrent = (vpn as? VpnState.Connected)?.node?.id == current.id || (vpn as? VpnState.Connecting)?.node?.id == current.id
                if (best.id != current.id && stillOnCurrent && GalsPlan.worthSwitching(currentSpeed, bestSpeed) &&
                    selectAutomatic(best, mode, stopEpoch, policy, revision, network)) {
                    toast(messageText(R.string.gals_faster_node, splitFlag(prefs.settings.nodeLabel(best)).second))
                }
                return@launch
            }

            if (deferred.isNotEmpty()) {
                toast(messageText(R.string.node_test_incomplete))
                return@launch
            }
            val target = GalsPlan.fallback(_state.value.nodes.filter(::eligible), results.value,
                _state.value.dead, tried, partial)
            if (target == null) {
                toast(
                    when {
                        tried.isEmpty() -> messageText(R.string.gals_no_nodes)
                        dnsBlocked -> messageText(R.string.gals_no_dns)
                        else -> messageText(R.string.gals_no_data)
                    },
                )
                return@launch
            }
            partialProfiles[target.id]?.let { prefs.rememberProfile(target.id, it) }
            toast(
                when {
                    target.id in partial -> messageText(R.string.gals_partial)
                    dnsBlocked -> messageText(R.string.gals_dns_fallback)
                    else -> messageText(R.string.gals_data_fallback)
                },
            )
            selectAutomatic(target, null, stopEpoch, policy, revision, network)
        }
        pathJob?.start()
    }

    private var configuredCursor = 0

    private fun findConfigured(policy: GalsPolicy, revision: Long, stopEpoch: Long, network: ProbeNetwork) {
        pathJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            val me = coroutineContext.job
            val settings = prefs.settings
            val mode = effectiveMode(settings)
            val group = policy.group
            fun eligible(node: ProxyNode) = stopEpoch == VpnBus.stopEpoch.get() && revision == automaticRevision &&
                eligibleAutomaticNode(node, policy)
            val availableNodes = AutomaticNodes.candidates(prefs, repo, mode, policy)
            val live = (VpnBus.state.value as? VpnState.Connected)?.node?.takeIf { it in availableNodes }
            val remaining = availableNodes.filter { it != live }
                .sortedBy { results.value[it.id]?.delayMs?.takeIf { delay -> delay > 0 } ?: Long.MAX_VALUE }
            val count = minOf(remaining.size, PATH_CANDIDATES - if (live == null) 0 else 1)
            val candidates = listOfNotNull(live) + if (remaining.isEmpty()) emptyList() else {
                val start = configuredCursor.mod(remaining.size)
                List(count) { remaining[(start + it) % remaining.size] }.also {
                    configuredCursor = (start + count) % remaining.size
                }
            }
            val verified = LinkedHashMap<ProxyNode, PathResult>()
            val deferred = LinkedHashSet<ProxyNode>()
            var connected: ProxyNode? = null
            var dnsBlocked = false

            fun fresh(node: ProxyNode, result: PathResult) = network.current() && eligible(node) && prefs.profileFor(node.id) == result.profile

            fun selectVerified(node: ProxyNode, result: PathResult): Boolean = synchronized(selectionLock) {
                if (!fresh(node, result)) return@synchronized false
                if ((VpnBus.state.value as? VpnState.Connected)?.node == node) true
                else selectAutomatic(node, mode, stopEpoch, policy, revision, network)
            }

            suspend fun attempt(node: ProxyNode) {
                if (group == null || !network.current() || !eligible(node)) return
                val saved = prefs.profileFor(node.id)
                val result = try {
                    if ((VpnBus.state.value as? VpnState.Connected)?.node == node) {
                        GalsProbe.live(node, saved, group, settings.testUrl, settings.encryptedDns)
                    } else GalsProbe.find(node, saved, (_state.value.galsNet ?: mode) == NetMode.BLACK,
                        settings.encryptedDns, group, settings.testUrl) { profile ->
                        if (pathJob === me && eligible(node)) _state.update { it.copy(pathfinding = GalsStep(prefs.settings.nodeLabel(node), profile)) }
                    }
                } finally {
                    if (pathJob === me) _state.update { it.copy(pathfinding = null) }
                }
                coroutineContext.ensureActive()
                val accepted = synchronized(selectionLock) {
                    if (!network.current() || !eligible(node) || prefs.profileFor(node.id) != saved) return@synchronized false
                    if (result.deferred) {
                        deferred += node
                        return@synchronized false
                    }
                    deferred -= node
                    dnsBlocked = dnsBlocked || result.dnsBlocked
                    val profile = result.profile ?: return@synchronized false
                    if (result.delayMs <= 0 || result.dnsBlocked ||
                        group.target != null && result.targetCheck?.verdict != ServiceVerdict.RESPONDED ||
                        group.strategy == GalsStrategy.THROUGHPUT && result.speedBps <= 0) return@synchronized false
                    prefs.rememberProfile(node.id, profile)
                    verified[node] = result
                    true
                }
                if (accepted && connected == null && selectVerified(node, result)) {
                    connected = node
                    _state.update { it.copy(gals = false, refining = true) }
                }
            }

            try {
                candidates.forEach { attempt(it) }
                deferred.toList().forEach { attempt(it) }
                coroutineContext.ensureActive()
                if (!network.current() || revision != automaticRevision || stopEpoch != VpnBus.stopEpoch.get() ||
                    !policy.sameBehavior(GalsPolicy.from(prefs.settings))) return@launch
                val available = verified.filter { (node, result) -> fresh(node, result) }
                val best = if (group?.strategy == GalsStrategy.THROUGHPUT) available.maxByOrNull { it.value.speedBps }
                    else available.minByOrNull { it.value.delayMs }
                val current = connected
                if (best == null) {
                    toast(messageText(when {
                        deferred.isNotEmpty() -> R.string.node_test_incomplete
                        candidates.isEmpty() -> R.string.gals_no_nodes
                        dnsBlocked -> R.string.gals_no_dns
                        else -> R.string.gals_no_data
                    }))
                    return@launch
                }
                if (current == null) {
                    selectVerified(best.key, best.value)
                    return@launch
                }
                val before = available[current]
                val improve = before == null || if (group?.strategy == GalsStrategy.THROUGHPUT)
                    GalsPlan.worthSwitching(before.speedBps, best.value.speedBps)
                else before.delayMs - best.value.delayMs >= 20 && best.value.delayMs < before.delayMs * 0.75
                val vpn = VpnBus.state.value
                val stillOnCurrent = (vpn as? VpnState.Connected)?.node == current || (vpn as? VpnState.Connecting)?.node == current
                if (best.key != current && stillOnCurrent && improve && selectVerified(best.key, best.value))
                    toast(messageText(R.string.gals_faster_node, splitFlag(prefs.settings.nodeLabel(best.key)).second))
            } finally {
                if (pathJob === me) _state.update { it.copy(gals = false, galsNet = null, pathfinding = null, refining = false) }
            }
        }
        pathJob?.start()
    }

    private fun eligibleAutomaticNode(node: ProxyNode, expected: GalsPolicy? = null): Boolean =
        AutomaticNodes.resolve(node, prefs, repo, effectiveMode(prefs.settings), expected) != null

    private fun selectAutomatic(node: ProxyNode, mode: NetMode?, stopEpoch: Long, policy: GalsPolicy,
        revision: Long, network: ProbeNetwork): Boolean = synchronized(selectionLock) {
        if (!network.current() || stopEpoch != VpnBus.stopEpoch.get() || revision != automaticRevision || !eligibleAutomaticNode(node, policy)) return@synchronized false
        setSelected(node, rememberFor = mode)
        connect(node, restart = true, automatic = true, expectedStopEpoch = stopEpoch,
            automaticPolicy = policy, expectedRevision = revision, network = network)
        true
    }

    private fun bestNode(): ProxyNode? =
        _state.value.nodes.firstOrNull { results.value[it.id]?.alive == true && it.id !in _state.value.dead }

    private fun modeKey(): String = effectiveMode(prefs.settings)?.name ?: "ANY"

    private fun deadFor(history: Map<String, NodeCheck>, mode: String): Set<String> =
        if (prefs.settings.groupDead) NodeHealth.dead(history, mode) else emptySet()

    private fun groupedDeadFor(nodes: List<ProxyNode>): Set<String> {
        if (!prefs.settings.groupDead) return emptySet()
        val mode = effectiveMode(prefs.settings)
        val eligible = nodes.filter { node ->
            val sourceMode = prefs.sourceConfigs[node.sourceId]?.mode ?: BuiltInCatalog.byId(node.sourceId)?.mode ?: SourceMode.ANY
            prefs.allowedForAuto(node) && sourceMode.fits(mode)
        }.mapTo(HashSet()) { it.id }
        val current = when (val vpn = VpnBus.state.value) {
            is VpnState.Connected -> vpn.node.id
            is VpnState.Connecting -> vpn.node.id
            else -> null
        }
        return NodeHealth.groupedDead(prefs.nodeHealth, prefs.fullNodeChecks[modeKey()], modeKey(),
            eligible, sameAccount(nodes) + setOfNotNull(current))
    }

    private fun sameAccount(nodes: List<ProxyNode>): Set<String> {
        val current = when (val vpn = VpnBus.state.value) {
            is VpnState.Connected -> vpn.node
            is VpnState.Connecting -> vpn.node
            else -> return emptySet()
        }
        if (prefs.userSources.none { it.id == current.sourceId }) return emptySet()
        return nodes.filter { it.sourceId == current.sourceId }.mapTo(HashSet()) { it.id }
    }

    private suspend fun testOne(node: ProxyNode): TestResult {
        val url = prefs.settings.testUrl
        val vpn = VpnBus.state.value
        return if (vpn is VpnState.Connected && vpn.node.id == node.id) app.farvater.engine.LiveNodeCheck.measure(node, url)
            else NodeTester.testAll(listOf(node), url, 1, prefs.nodeProfiles).first()
    }

    private suspend fun recordHealth(checked: Collection<TestResult>, exempt: Set<String> = emptySet()) {
        if (checked.isEmpty()) return
        val mode = modeKey()
        val batch = checked.toList()
        val dead = withContext(NonCancellable + Dispatchers.Default) {
            healthLock.withLock {
                val history = NodeHealth.record(prefs.nodeHealth, mode, batch, exempt = exempt)
                prefs.nodeHealth = history
                val dead = deadFor(history, mode)
                prefs.backupNodes = BackupNodes(mode, Failover.pick(_state.value.nodes, results.value, dead))
                dead
            }
        }
        _state.update { it.copy(dead = dead, groupedDead = groupedDeadFor(it.nodes)) }
        rebuild()
    }

    private fun reloadHealth() {
        displayOnlyResults.value = emptyMap()
        val mode = modeKey()
        NodeCheckCoordinator.cancelIfModeChanged(mode)
        val work = NodeCheckCoordinator.state.value
        testing = work.mode == mode && work.progress != null
        if (testing) return
        results.value = NodeHealth.results(prefs.nodeHealth, mode)
        cachedResultsMode = mode
        _state.update { it.copy(results = results.value, dead = deadFor(prefs.nodeHealth, mode), groupedDead = groupedDeadFor(it.nodes),
            progress = null, checking = if (work.mode == mode) work.checking else emptySet(), serviceChecking = work.mode == mode && work.services) }
        rebuild()
    }

    private var preflightJob: Job? = null

    private fun preflight(node: ProxyNode) {
        preflightJob?.cancel()
        val stopEpoch = VpnBus.stopEpoch.get()
        preflightJob = viewModelScope.launch {
            val me = coroutineContext.job
            _state.update { it.copy(preflight = node) }
            val r = try {
                testOne(node)
            } finally {
                if (preflightJob === me) _state.update { it.copy(preflight = null) }
            }
            if (!r.evaluated) {
                toast(messageText(R.string.node_test_incomplete))
                return@launch
            }
            results.update { it + (r.nodeId to r) }
            _state.update { it.copy(results = displayResults()) }
            recordHealth(listOf(r))
            coroutineContext.ensureActive()
            if (stopEpoch != VpnBus.stopEpoch.get()) return@launch
            if (r.alive) connect(node, expectedStopEpoch = stopEpoch) else _state.update { it.copy(deadPrompt = node) }
        }
    }

    fun dismissDeadPrompt() = _state.update { it.copy(deadPrompt = null) }

    fun connectAnyway() {
        val node = _state.value.deadPrompt ?: return
        dismissDeadPrompt()
        connect(node)
    }

    fun findWorkingInstead() {
        dismissDeadPrompt()
        findWorking()
    }

    private fun reportDeadNode() {
        val current = (VpnBus.state.value as? VpnState.Connected)?.node ?: return
        toast(
            if (prefs.settings.autoSwitch && prefs.backupNodes.nodes.any { it.id != current.id }) messageText(R.string.node_failed_switch, prefs.settings.nodeLabel(current))
            else messageText(R.string.node_failed_choose, prefs.settings.nodeLabel(current)),
        )
    }

    fun toggleConnection() {
        if (_state.value.gals || (_state.value.progress != null && !_state.value.refining)) {
            cancelTest()
            return
        }
        if (_state.value.refining) cancelTest()
        when (VpnBus.state.value) {
            is VpnState.Connected, is VpnState.Connecting -> FarvaterVpnService.stop(getApplication())
            else -> {
                if (preflightJob?.isActive == true) {
                    preflightJob?.cancel()
                    _state.update { it.copy(preflight = null) }
                    return
                }
                val dead = _state.value.dead
                val node = _state.value.selectedNode
                    ?: (bestNode() ?: _state.value.nodes.firstOrNull { it.id !in dead } ?: _state.value.nodes.firstOrNull())
                        ?.also(::setSelected)
                when {
                    node == null -> toast(messageText(R.string.no_nodes_connect))
                    node.id in dead -> preflight(node)
                    else -> connect(node)
                }
            }
        }
    }

    fun select(node: ProxyNode) {
        cancelGals()
        preflightJob?.cancel()
        pendingNode = null
        pendingAutomatic = false
        pendingContext = null
        _state.update { it.copy(preflight = null, deadPrompt = null) }
        setSelected(node, rememberFor = effectiveMode(prefs.settings))
        val current = when (val vpn = VpnBus.state.value) {
            is VpnState.Connected -> vpn.node
            is VpnState.Connecting -> vpn.node
            else -> null
        }
        if (current != null && current.id != node.id) connect(node)
    }

    private fun setSelected(node: ProxyNode, rememberFor: NetMode? = null): Unit = synchronized(selectionLock) {
        prefs.selectNode(node)
        if (rememberFor != null && prefs.settings.modeNodes) prefs.rememberForMode(rememberFor, node)
        _state.update { it.copy(selectedId = node.id, savedNode = node) }
    }

    private fun reconcileModeNodes(nodes: List<ProxyNode>) {
        val remembered = prefs.modeNodes
        if (remembered.isEmpty()) return
        val updated = remembered.mapValues { (_, saved) ->
            if (nodes.any { it.id == saved.id }) return@mapValues saved
            nodes.firstOrNull {
                it.sourceId == saved.sourceId && it.protocol == saved.protocol &&
                    it.address == saved.address && it.port == saved.port && it.name == saved.name
            } ?: saved
        }
        if (updated != remembered) prefs.modeNodes = updated
    }

    private fun dropSelectionIf(predicate: (ProxyNode) -> Boolean): Unit = synchronized(selectionLock) {
        prefs.forgetModeNodes(predicate)
        val backups = prefs.backupNodes
        if (backups.nodes.any(predicate)) prefs.backupNodes = backups.copy(nodes = backups.nodes.filterNot(predicate))
        val current = prefs.lastNode ?: return
        if (!predicate(current)) return
        prefs.clearSelection()
        _state.update { it.copy(selectedId = null, savedNode = null) }
    }

    private fun savedSelection(): ProxyNode? = prefs.lastNode?.takeIf { it.id == prefs.selectedNodeId }

    private fun reconcileSelection(nodes: List<ProxyNode>): ProxyNode? {
        val id = prefs.selectedNodeId
        nodes.firstOrNull { it.id == id }?.let { listed ->
            prefs.selectNode(listed)
            return listed
        }
        val saved = prefs.lastNode ?: return null
        val chosen = nodes.firstOrNull {
            it.sourceId == saved.sourceId && it.protocol == saved.protocol &&
                it.address == saved.address && it.port == saved.port && it.name == saved.name
        } ?: saved
        prefs.selectNode(chosen)
        return chosen
    }

    private fun connect(node: ProxyNode, restart: Boolean = false, automatic: Boolean = false,
        expectedStopEpoch: Long = VpnBus.stopEpoch.get(), automaticPolicy: GalsPolicy? = null,
        expectedRevision: Long = automaticRevision, network: ProbeNetwork? = null) {
        if (expectedStopEpoch != VpnBus.stopEpoch.get() || (automatic &&
                (expectedRevision != automaticRevision || !eligibleAutomaticNode(node, automaticPolicy)))) return
        val checked = if (automatic) AutomaticContext.capture(network ?: return, node, prefs, repo, effectiveMode(prefs.settings),
            automaticPolicy ?: return) ?: return else null
        val permission = VpnService.prepare(getApplication())
        if (permission != null) {
            pendingNode = node
            pendingAutomatic = automatic
            pendingStopEpoch = expectedStopEpoch
            pendingContext = checked
            pendingRestart = restart
            _events.tryEmit(UiEvent.RequestVpnPermission(permission))
        } else if (checked != null) {
            FarvaterVpnService.startAutomatic(getApplication(), checked, restart)
        } else {
            FarvaterVpnService.start(getApplication(), node, restart)
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        val node = pendingNode
        val automatic = pendingAutomatic
        val stopEpoch = pendingStopEpoch
        val checked = pendingContext
        val restart = pendingRestart
        pendingRestart = false
        pendingNode = null
        pendingAutomatic = false
        pendingContext = null
        if (granted && node != null && stopEpoch == VpnBus.stopEpoch.get()) {
            if (automatic) checked?.let { FarvaterVpnService.startAutomatic(getApplication(), it, restart) }
            else FarvaterVpnService.start(getApplication(), node, restart)
        } else if (!granted) toast(messageText(R.string.vpn_permission_denied))
    }

    fun refreshTrafficHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = prefs.trafficHistory
            _state.update { it.copy(trafficHistory = history) }
        }
    }

    fun toggleFavorite(nodeId: String) = updateSettings {
        it.copy(favoriteNodeIds = if (nodeId in it.favoriteNodeIds) it.favoriteNodeIds - nodeId else it.favoriteNodeIds + nodeId)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = prefs.settings
        val new = transform(old)
        prefs.settings = new
        if (old.probeMethod != new.probeMethod) displayOnlyResults.value = emptyMap()
        if (!GalsPolicy.from(old).sameBehavior(GalsPolicy.from(new))) cancelGals()
        if (old.serviceChecksEnabled != new.serviceChecksEnabled || old.checkedServices != new.checkedServices ||
            old.serviceCustomUrl != new.serviceCustomUrl || old.serviceExpectedCode != new.serviceExpectedCode) cancelServiceChecks()
        if (old.serviceCustomUrl != new.serviceCustomUrl || old.serviceExpectedCode != new.serviceExpectedCode) {
            ServiceChecks.customUrl(old.serviceCustomUrl)?.let { url ->
                val previous = ServiceTarget(ServiceKind.CUSTOM, url.toString(), old.serviceExpectedCode)
                prefs.serviceChecks = prefs.serviceChecks.filterKeys { !it.endsWith("/${previous.key}") }
            }
        }
        if (old.groupDead != new.groupDead) _state.update { it.copy(dead = deadFor(prefs.nodeHealth, modeKey()), groupedDead = groupedDeadFor(it.nodes)) }
        rebuild()
        val vpn = VpnBus.state.value
        val tunnelChanged = old.bypassApps != new.bypassApps ||
            old.directRuServices != new.directRuServices ||
            old.encryptedDns != new.encryptedDns ||
            old.mtu != new.mtu
        if (tunnelChanged && (vpn is VpnState.Connected || vpn is VpnState.Connecting)) reconnectSoon()
    }

    private var reconnectJob: Job? = null
    private fun reconnectSoon() {
        reconnectJob?.cancel()
        val stopEpoch = VpnBus.stopEpoch.get()
        reconnectJob = viewModelScope.launch {
            delay(RECONNECT_DELAY_MS)
            if (stopEpoch != VpnBus.stopEpoch.get() || VpnBus.request.value?.active == false) return@launch
            val vpn = VpnBus.state.value
            val node = when (vpn) {
                is VpnState.Connected -> vpn.node
                is VpnState.Connecting -> vpn.node
                else -> null
            }
            if (node != null) connect(node, restart = true, expectedStopEpoch = stopEpoch)
        }
    }

    fun finishOnboarding(enableCommunity: Boolean) {
        updateSettings {
            it.copy(onboardingDone = true, communityEnabled = enableCommunity, acceptedLegalVersion = LEGAL_VERSION)
        }
        if (enableCommunity) refreshAll()
    }

    fun acceptLegal() = updateSettings { it.copy(acceptedLegalVersion = LEGAL_VERSION) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun showMessage(text: String) = toast(text)

    private fun toast(text: String) = _state.update { it.copy(message = text) }

    private fun fetchTargets(): List<Pair<String, List<String>>> = buildList {
        val settings = prefs.settings
        val enabled = prefs.enabledSources
        BuiltInCatalog.sources
            .filter { settings.communityEnabled && it.id in enabled }
            .forEach { add(it.id to it.mirrors) }
        prefs.userSources.forEach { add(it.id to listOf(it.url)) }
    }

    private fun rebuild() {
        rebuildVersion.update { it + 1 }
    }

    private suspend fun rebuildNow() = withContext(Dispatchers.Default) { rebuildLocked() }

    private fun rebuildLocked(): Unit = synchronized(selectionLock) {
        val settings = prefs.settings
        val enabled = prefs.enabledSources
        val snaps = snapshots.value
        val busy = loading.value
        val configs = prefs.sourceConfigs
        val mode = effectiveMode(settings)

        fun hideInsecure(node: ProxyNode) = settings.safeMode && node.isInsecure && node.sourceId in communityIds
        fun shown(snap: SourceSnapshot?) = snap?.nodes?.count { !hideInsecure(it) } ?: 0
        fun hiddenNote(snap: SourceSnapshot?): String? {
            snap ?: return null
            val parts = buildList {
                snap.nodes.count { hideInsecure(it) }.takeIf { it > 0 }?.let { add(app.farvater.data.AppLanguages.wrap(getApplication<Application>()).resources.getQuantityString(R.plurals.unsafe_nodes_count, it, it)) }
                snap.skipped.forEach { (reason, n) -> add("$n ${getApplication<Application>().importReason(reason)}") }
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
        }
        fun message(snap: SourceSnapshot?) = snap?.let { (listOfNotNull(it.announce) + it.notices).distinct().joinToString("\n") }?.ifBlank { null }
        val community = BuiltInCatalog.sources.map { s ->
            val snap = snaps[s.id]
            SourceUi(
                id = s.id, title = messageText(s.titleRes), author = s.author, license = messageText(s.licenseRes), homepage = s.homepage, forum = s.forum,
                description = messageText(s.descriptionRes), community = true,
                enabled = settings.communityEnabled && s.id in enabled,
                nodeCount = shown(snap), updatedAt = snap?.updatedAt ?: 0,
                error = snap?.error, loading = s.id in busy, hidden = hiddenNote(snap),
                mode = configs[s.id]?.mode ?: s.mode,
                intervalHours = configs[s.id]?.intervalHours,
                ownIntervalHours = snap?.intervalHours,
                announce = message(snap),
                networkUnavailable = snap?.networkUnavailable == true,
            )
        }
        val own = prefs.userSources.map { u ->
            val snap = snaps[u.id]
            SourceUi(
                id = u.id, title = snap?.title ?: u.title, author = messageText(R.string.source_own_author), license = "",
                homepage = u.url, description = u.url, community = false, enabled = true,
                nodeCount = shown(snap), updatedAt = snap?.updatedAt ?: 0,
                error = snap?.error, loading = u.id in busy, hidden = hiddenNote(snap),
                mode = configs[u.id]?.mode ?: SourceMode.ANY,
                intervalHours = configs[u.id]?.intervalHours,
                ownIntervalHours = snap?.intervalHours,
                usage = snap?.usage,
                announce = message(snap),
                networkUnavailable = snap?.networkUnavailable == true,
            )
        }
        val sources = (community + own).map { it.copy(inMode = it.mode.fits(mode)) }
        val active = sources.filter { it.enabled && it.inMode }.map { it.id }.toSet() + ProxyNode.MANUAL_SOURCE
        val activeSnaps = (sources.map { it.id } + ProxyNode.MANUAL_SOURCE).filter { it in active }.mapNotNull { snaps[it] }
        val all = activeSnaps.flatMap { it.nodes }.distinctBy { it.id }
        val secure = all.filterNot(::hideInsecure)
        val single = secure.onePerEndpoint(prefs.selectedNodeId)
        val nodes = if (settings.dropDuplicates) single else secure
        val selected = reconcileSelection(nodes)
        reconcileModeNodes(nodes)
        val res = displayResults()

        _state.update {
            it.copy(
                settings = settings,
                sources = sources,
                nodes = if (testing && !settings.liveSort) keepOrder(it.nodes, nodes) else sortNodes(nodes, res),
                hiddenInsecure = all.size - secure.size,
                groupedDead = groupedDeadFor(nodes),
                duplicates = secure.size - single.size,
                results = res,
                netMode = mode,
                detectedMode = detected,
                selectedId = selected?.id,
                savedNode = selected,
                serviceChecks = if (settings.serviceChecksEnabled) prefs.serviceChecks else emptyMap(),
            )
        }
    }

    private fun keepOrder(previous: List<ProxyNode>, nodes: List<ProxyNode>): List<ProxyNode> {
        val index = HashMap<String, Int>(previous.size * 2)
        previous.forEachIndexed { i, n -> index[n.id] = i }
        return nodes.sortedBy { index[it.id] ?: Int.MAX_VALUE }
    }

    private fun sortNodes(nodes: List<ProxyNode>, res: Map<String, TestResult>): List<ProxyNode> =
        nodes.sortedWith(
            compareBy<ProxyNode>(
                { node ->
                    val r = res[node.id]
                    when {
                        r == null || !r.evaluated -> 2
                        !r.alive -> 3
                        r.method == TestMethod.REAL -> 0
                        else -> 1
                    }
                },
                { res[it.id]?.delayMs ?: Long.MAX_VALUE },
            ),
        )

    private fun shortHash(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).take(5).joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }

    private fun hostOf(url: String) = url.substringAfter("://").substringBefore('/').ifBlank { messageText(R.string.subscription_title) }

    companion object {
        @Volatile private var cachedResultsMode: String? = null
        private val communityIds = BuiltInCatalog.sources.map { it.id }.toSet()
        private const val HOUR_MS = 60 * 60 * 1000L
        private const val DEFAULT_INTERVAL_H = 6
        private const val DUE_CHECK_MS = 5 * 60 * 1000L
        private const val RETRY_MS = 15 * 60 * 1000L
        private const val MAX_IMPORT = 2 * 1024 * 1024
        private const val DETECT_GAP_MS = 60 * 1000L
        @Volatile private var lastDetect = 0L
        private const val UPDATE_CHECK_GAP_MS = 15 * 60 * 1000L
        private const val REBUILD_GAP_MS = 150L
        private const val RECONNECT_DELAY_MS = 1200L
        private const val PATH_CANDIDATES = 8
        private const val SCOUT_MS = 300L

        private val selectionLock = app.farvater.data.Prefs.selectionLock
        private val rebuildVersion = MutableStateFlow(0L)
        private val cachedResults = MutableStateFlow<Map<String, TestResult>>(emptyMap())
        @Volatile private var cachedState: UiState? = null
    }
}

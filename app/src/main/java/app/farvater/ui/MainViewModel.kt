package app.farvater.ui

import app.farvater.data.userMessage
import app.farvater.R
import app.farvater.data.localizedString
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
import app.farvater.data.AppSettings
import app.farvater.data.BackupNodes
import app.farvater.data.LEGAL_VERSION
import app.farvater.data.NodeCheck
import app.farvater.data.NodeHealth
import app.farvater.data.SourceConfig
import app.farvater.data.SourceSnapshot
import app.farvater.data.UpdateInfo
import app.farvater.data.UserSource
import app.farvater.engine.Failover
import app.farvater.engine.GalsPlan
import app.farvater.engine.NetModeDetector
import app.farvater.engine.NodeTester
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
    val preflight: ProxyNode? = null,
    val deadPrompt: ProxyNode? = null,
    val checking: Set<String> = emptySet(),
    val collapsedGroups: Set<String> = emptySet(),
    val trafficHistory: List<app.farvater.data.DayTraffic> = emptyList(),
) {
    val selectedNode: ProxyNode?
        get() = nodes.firstOrNull { it.id == selectedId } ?: savedNode?.takeIf { it.id == selectedId }
    fun sourceTitle(id: String, manualTitle: String): String =
        if (id == ProxyNode.MANUAL_SOURCE) manualTitle else sources.firstOrNull { it.id == id }?.title ?: id
}

sealed interface UiEvent {
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

    private val snapshots = cachedSnapshots
    private val loading = MutableStateFlow<Set<String>>(emptySet())
    private val results = cachedResults

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
    private var testJob: Job? = null
    @Volatile private var testing = false

    private val rebuildRequests = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var detected: NetMode? = prefs.lastNetMode?.let { runCatching { NetMode.valueOf(it) }.getOrNull() }
    private var detectJob: Job? = null
    private val attempts = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val healthLock = Mutex()

    init {
        if (results.value.isEmpty()) results.value = NodeHealth.results(prefs.nodeHealth, modeKey())
        _state.update { it.copy(results = results.value, dead = deadFor(prefs.nodeHealth, modeKey())) }
        viewModelScope.launch(Dispatchers.Default) {
            healthLock.withLock {
                val history = prefs.nodeHealth
                val fresh = NodeHealth.prune(history)
                if (fresh.size != history.size) prefs.nodeHealth = fresh
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            for (request in rebuildRequests) {
                rebuildLocked()
                delay(REBUILD_GAP_MS)
            }
        }
        viewModelScope.launch { rebuildVersion.collect { rebuildRequests.trySend(Unit) } }
        viewModelScope.launch { _state.collect { cachedState = it } }
        viewModelScope.launch(Dispatchers.IO) {
            if (snapshots.value.isEmpty()) {
                val ids = fetchTargets().map { it.first } + ProxyNode.MANUAL_SOURCE
                snapshots.value = ids.mapNotNull { id -> repo.loadCached(id)?.let { id to it } }.toMap()
            }
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
        if (_state.value.updateStage is UpdateStage.Checking || _state.value.updateStage is UpdateStage.Downloading) return
        viewModelScope.launch {
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
        if (_state.value.updateStage is UpdateStage.Downloading) return
        if (!updates.canInstall()) {
            toast(messageText(R.string.update_permission))
            _events.tryEmit(UiEvent.StartActivity(updates.installPermissionIntent()))
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(updateStage = UpdateStage.Downloading(0f)) }
            var last = 0f
            val file = updates.download(info, viaTunnel = VpnBus.state.value is VpnState.Connected) { p ->
                if (p - last >= 0.01f || p >= 1f) {
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

    private suspend fun refreshOne(id: String, urls: List<String>, viaTunnel: Boolean) {
        loading.update { it + id }
        rebuild()
        val snapshot = repo.fetch(id, urls, viaTunnel, deviceHeaders(id), json = id !in communityIds)
        snapshots.update { it + (id to snapshot) }
        loading.update { it - id }
        rebuild()
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
        viewModelScope.launch { refreshOne(id, urls, VpnBus.state.value is VpnState.Connected) }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        prefs.enabledSources = if (enabled) prefs.enabledSources + id else prefs.enabledSources - id
        rebuild()
        if (enabled && snapshots.value[id] == null) {
            BuiltInCatalog.byId(id)?.let { src ->
                viewModelScope.launch(Dispatchers.IO) {
                    val cached = repo.loadCached(id)
                    if (cached == null) {
                        refreshOne(id, src.mirrors, VpnBus.state.value is VpnState.Connected)
                    } else {
                        snapshots.update { it + (id to cached) }
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
        repo.deleteCache(id)
        snapshots.update { it - id }
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
                toast(messageText(R.string.import_no_links))
                return@launch
            }
            val added = synchronized(repo) {
                val count = repo.appendManual(parsed.nodes)
                snapshots.update { it + (ProxyNode.MANUAL_SOURCE to repo.loadManual()) }
                count
            }
            rebuild()
            toast(if (added > 0) messageText(R.string.nodes_added, added) else messageText(R.string.nodes_exist))
        }
    }

    fun requestImport(text: String) = _state.update { it.copy(pendingImport = text) }
    fun rejectImport() = toast(messageText(R.string.import_invalid))
    fun confirmImport() {
        val text = _state.value.pendingImport ?: return
        _state.update { it.copy(pendingImport = null) }
        importText(text)
    }
    fun dismissImport() = _state.update { it.copy(pendingImport = null) }

    fun deleteManualNode(node: ProxyNode) {
        if (node.sourceId != ProxyNode.MANUAL_SOURCE) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                synchronized(repo) {
                    repo.removeManual(node.id)
                    snapshots.update { it + (ProxyNode.MANUAL_SOURCE to repo.loadManual()) }
                }
                withContext(Dispatchers.Main) { dropSelectionIf { it.id == node.id } }
                rebuild()
            }
        }
    }

    fun testAll(onFinished: (() -> Unit)? = null) {
        if (testJob?.isActive == true) return
        val nodes = _state.value.nodes
        if (nodes.isEmpty()) {
            toast(messageText(R.string.nodes_list_empty))
            return
        }
        val settings = prefs.settings
        val vpn = VpnBus.state.value
        val connected = (vpn as? VpnState.Connected)?.node?.takeIf { node -> nodes.any { it.id == node.id } }
        val exempt = sameAccount(nodes)
        testing = true
        testJob = viewModelScope.launch {
            val me = coroutineContext.job
            var done = 0
            var alive = 0
            var lastFlush = 0L
            val pending = HashMap<String, TestResult>()
            val finished = ArrayList<TestResult>()
            fun flush(finished: Boolean = false) {
                if (pending.isNotEmpty()) {
                    val batch = HashMap(pending)
                    pending.clear()
                    results.update { it + batch }
                }
                _state.update {
                    it.copy(
                        results = results.value,
                        progress = if (finished) null else TestProgress(done, nodes.size, alive),
                        nodes = if (settings.liveSort) sortNodes(it.nodes, results.value) else it.nodes,
                    )
                }
            }
            fun take(r: TestResult) {
                done++
                if (!r.alive && r.nodeId in exempt) return
                if (r.alive) alive++
                pending[r.nodeId] = r
                finished += r
                val now = System.nanoTime() / 1_000_000
                if (now - lastFlush >= FLUSH_MS) {
                    lastFlush = now
                    flush()
                }
            }
            _state.update { it.copy(progress = TestProgress(0, nodes.size, 0)) }
            try {
                val rest = if (connected == null) {
                    nodes
                } else {
                    val tunnel = NodeTester.throughTunnel(connected, settings.testUrl)
                    if (tunnel == null) {
                        nodes
                    } else {
                        take(tunnel)
                        nodes.filter { it.id != connected.id }
                    }
                }
                NodeTester.testAll(rest, settings.testUrl, settings.concurrency).collect { take(it) }
            } finally {
                if (testJob === me) {
                    testing = false
                    flush(finished = true)
                }
                recordHealth(finished, exempt)
            }
            rebuildNow()
            _state.update { it.copy(progress = null) }
            onFinished?.invoke()
        }
    }

    fun toggleGroup(sourceId: String) {
        val groups = _state.value.collapsedGroups
        val next = if (sourceId in groups) groups - sourceId else groups + sourceId
        prefs.collapsedGroups = next
        _state.update { it.copy(collapsedGroups = next) }
    }

    fun testNode(node: ProxyNode) {
        if (node.id in _state.value.checking) return
        _state.update { it.copy(checking = it.checking + node.id) }
        viewModelScope.launch {
            try {
                val r = testOne(node)
                results.update { it + (r.nodeId to r) }
                _state.update { it.copy(results = results.value) }
                recordHealth(listOf(r), sameAccount(listOf(node)))
                val name = splitFlag(node.name).second
                toast(if (r.alive) messageText(R.string.node_test_alive, name, r.delayMs) else messageText(R.string.dead_node_title, name))
            } finally {
                _state.update { it.copy(checking = it.checking - node.id) }
            }
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        pathJob?.cancel()
        _state.update { it.copy(progress = null, pathfinding = null, gals = false, sensing = false, galsNet = null, refining = false) }
    }

    private var pathJob: Job? = null

    fun findWorking() {
        if (pathJob?.isActive == true || testJob?.isActive == true) return
        pathJob = viewModelScope.launch {
            val me = coroutineContext.job
            _state.update { it.copy(gals = true, sensing = true) }
            val net = try {
                senseNetwork()
            } finally {
                if (pathJob === me) _state.update { it.copy(sensing = false) }
            }
            _state.update { it.copy(galsNet = net) }
            findWorkingNow()
        }
    }

    private suspend fun senseNetwork(): NetMode? {
        val net = NetModeDetector.detect() ?: return null
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

    private fun findWorkingNow() {
        if (_state.value.nodes.isEmpty()) {
            _state.update { it.copy(gals = false, galsNet = null) }
            testAll()
            return
        }
        pathJob = viewModelScope.launch {
            val me = coroutineContext.job
            val settings = prefs.settings
            val mode = _state.value.galsNet ?: effectiveMode(settings)
            val ordinaryBlocking = mode == NetMode.BLACK
            val tried = HashSet<String>()
            val failedKinds = HashSet<String>()
            val failedHosts = HashSet<String>()
            var dnsBlocked = false
            val verified = LinkedHashMap<String, Pair<ProxyNode, Long>>()
            val partial = HashMap<String, Long>()
            val partialProfiles = HashMap<String, NodeProfile>()
            var connected: ProxyNode? = null

            suspend fun attempt(node: ProxyNode) {
                tried += node.id
                val found = try {
                    Pathfinder.find(node, prefs.profileFor(node.id), ordinaryBlocking, checkDns = settings.encryptedDns) { t ->
                        _state.update { it.copy(pathfinding = GalsStep(node.name, t)) }
                    }
                } finally {
                    if (pathJob === me) _state.update { it.copy(pathfinding = null) }
                }
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
                    return
                }
                prefs.rememberProfile(node.id, profile)
                verified[node.id] = node to found.speedBps
                if (connected == null) {
                    connected = node
                    _state.update { it.copy(gals = false, galsNet = null, refining = true) }
                    setSelected(node, rememberFor = mode)
                    connect(node, restart = true)
                }
            }

            try {
                val since = System.currentTimeMillis()
                testAll()
                var scanned = 0
                while (scanned < PATH_CANDIDATES) {
                    val running = testJob?.isActive == true
                    val node = GalsPlan.next(_state.value.nodes, results.value, _state.value.dead, tried, failedKinds, failedHosts, since)
                    if (node == null) {
                        if (!running) break
                        delay(SCOUT_MS)
                        continue
                    }
                    scanned++
                    attempt(node)
                }
                testJob?.join()
            } finally {
                if (pathJob === me) _state.update { it.copy(gals = false, galsNet = null, refining = false) }
            }

            val current = connected
            if (current != null) {
                val bestId = GalsPlan.fastest(verified.mapValues { it.value.second }) ?: return@launch
                val (best, bestSpeed) = verified.getValue(bestId)
                val currentSpeed = verified[current.id]?.second ?: 0L
                val vpn = VpnBus.state.value
                val stillOnCurrent = (vpn as? VpnState.Connected)?.node?.id == current.id || (vpn as? VpnState.Connecting)?.node?.id == current.id
                if (best.id != current.id && stillOnCurrent && GalsPlan.worthSwitching(currentSpeed, bestSpeed)) {
                    setSelected(best, rememberFor = mode)
                    connect(best, restart = true)
                    toast(messageText(R.string.gals_faster_node, splitFlag(best.name).second))
                }
                return@launch
            }

            val target = GalsPlan.fallback(_state.value.nodes, results.value, _state.value.dead, tried, partial)
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
            setSelected(target)
            connect(target, restart = true)
        }
    }

    private fun bestNode(): ProxyNode? =
        _state.value.nodes.firstOrNull { results.value[it.id]?.alive == true && it.id !in _state.value.dead }

    private fun modeKey(): String = effectiveMode(prefs.settings)?.name ?: "ANY"

    private fun deadFor(history: Map<String, NodeCheck>, mode: String): Set<String> =
        if (prefs.settings.groupDead) NodeHealth.dead(history, mode) else emptySet()

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
        val tunnel = if (vpn is VpnState.Connected && vpn.node.id == node.id) NodeTester.throughTunnel(node, url) else null
        return tunnel ?: NodeTester.testAll(listOf(node), url, 1).first()
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
        _state.update { it.copy(dead = dead) }
        rebuild()
    }

    private fun reloadHealth() {
        if (testing) return
        results.value = NodeHealth.results(prefs.nodeHealth, modeKey())
        _state.update { it.copy(results = results.value, dead = deadFor(prefs.nodeHealth, modeKey())) }
        rebuild()
    }

    private var preflightJob: Job? = null

    private fun preflight(node: ProxyNode) {
        preflightJob?.cancel()
        preflightJob = viewModelScope.launch {
            val me = coroutineContext.job
            _state.update { it.copy(preflight = node) }
            val r = try {
                testOne(node)
            } finally {
                if (preflightJob === me) _state.update { it.copy(preflight = null) }
            }
            results.update { it + (r.nodeId to r) }
            _state.update { it.copy(results = results.value) }
            recordHealth(listOf(r))
            if (r.alive) connect(node) else _state.update { it.copy(deadPrompt = node) }
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
            if (prefs.settings.autoSwitch && prefs.backupNodes.nodes.any { it.id != current.id }) messageText(R.string.node_failed_switch, current.name)
            else messageText(R.string.node_failed_choose, current.name),
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
        setSelected(node, rememberFor = effectiveMode(prefs.settings))
        val vpn = VpnBus.state.value
        if (vpn is VpnState.Connected && vpn.node.id != node.id) connect(node)
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

    private fun connect(node: ProxyNode, restart: Boolean = false) {
        val permission = VpnService.prepare(getApplication())
        if (permission != null) {
            pendingNode = node
            _events.tryEmit(UiEvent.RequestVpnPermission(permission))
        } else {
            FarvaterVpnService.start(getApplication(), node, restart)
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        val node = pendingNode
        pendingNode = null
        if (granted && node != null) FarvaterVpnService.start(getApplication(), node)
        else if (!granted) toast(messageText(R.string.vpn_permission_denied))
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
        if (old.groupDead != new.groupDead) _state.update { it.copy(dead = deadFor(prefs.nodeHealth, modeKey())) }
        rebuild()
        val vpn = VpnBus.state.value
        val tunnelChanged = old.bypassApps != new.bypassApps ||
            old.directRuServices != new.directRuServices ||
            old.encryptedDns != new.encryptedDns ||
            old.mtu != new.mtu
        if (tunnelChanged && vpn is VpnState.Connected) reconnectSoon()
    }

    private var reconnectJob: Job? = null
    private fun reconnectSoon() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(RECONNECT_DELAY_MS)
            val vpn = VpnBus.state.value
            if (vpn is VpnState.Connected) connect(_state.value.selectedNode ?: vpn.node, restart = true)
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
                snap.skipped.forEach { (scheme, n) -> add("$n $scheme") }
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
        val res = results.value

        _state.update {
            it.copy(
                settings = settings,
                sources = sources,
                nodes = if (testing && !settings.liveSort) keepOrder(it.nodes, nodes) else sortNodes(nodes, res),
                hiddenInsecure = all.size - secure.size,
                duplicates = secure.size - single.size,
                results = res,
                netMode = mode,
                detectedMode = detected,
                selectedId = selected?.id,
                savedNode = selected,
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
                        r == null -> 2
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
        private const val FLUSH_MS = 300L
        private const val RECONNECT_DELAY_MS = 1200L
        private const val PATH_CANDIDATES = 8
        private const val SCOUT_MS = 300L

        private val selectionLock = app.farvater.data.Prefs.selectionLock
        private val rebuildVersion = MutableStateFlow(0L)
        private val cachedSnapshots = MutableStateFlow<Map<String, SourceSnapshot>>(emptyMap())
        private val cachedResults = MutableStateFlow<Map<String, TestResult>>(emptyMap())
        @Volatile private var cachedState: UiState? = null
    }
}

package app.farvater.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.farvater.App
import app.farvater.core.catalog.BuiltInCatalog
import app.farvater.core.model.NetMode
import app.farvater.core.model.NetModeChoice
import app.farvater.core.model.ProxyNode
import app.farvater.core.model.SourceMode
import app.farvater.core.model.onePerEndpoint
import app.farvater.core.parser.SubscriptionParser
import app.farvater.core.parser.Usage
import app.farvater.data.AppSettings
import app.farvater.data.LEGAL_VERSION
import app.farvater.data.SourceConfig
import app.farvater.data.SourceSnapshot
import app.farvater.data.UpdateInfo
import app.farvater.data.UserSource
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val pathfinding: String? = null,
    val checking: Set<String> = emptySet(),
) {
    val selectedNode: ProxyNode?
        get() = nodes.firstOrNull { it.id == selectedId } ?: savedNode?.takeIf { it.id == selectedId }
    fun sourceTitle(id: String): String =
        if (id == ProxyNode.MANUAL_SOURCE) "вручную" else sources.firstOrNull { it.id == id }?.title ?: id
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
            checking = emptySet(),
            refreshing = false,
            message = null,
            pendingImport = null,
            updateStage = UpdateStage.Idle,
            showUpdate = false,
        ) ?: UiState(settings = prefs.settings, selectedId = prefs.selectedNodeId, savedNode = savedSelection()),
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

    init {
        viewModelScope.launch(Dispatchers.Default) {
            for (request in rebuildRequests) {
                rebuildLocked()
                delay(REBUILD_GAP_MS)
            }
        }
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
            VpnBus.state.collect { if (it is VpnState.Failed) toast(it.message) }
        }
    }

    fun onForeground() {
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
            if (autoShow && info != null) {
                prefs.shownUpdateVersion = info.versionCode
                prefs.notifiedVersion = maxOf(prefs.notifiedVersion, info.versionCode)
            }
            _state.update {
                it.copy(update = info, updateStage = UpdateStage.Idle, showUpdate = it.showUpdate || autoShow || (manual && info != null))
            }
            when {
                !manual -> Unit
                result.isFailure -> toast("Не удалось проверить обновления: сервер недоступен")
                info == null -> toast("У вас последняя версия")
            }
        }
    }

    fun openUpdate() {
        if (_state.value.update != null) _state.update { it.copy(showUpdate = true) } else checkUpdates(manual = true)
    }

    fun dismissUpdate() = _state.update { it.copy(showUpdate = false, updateStage = UpdateStage.Idle) }

    fun installUpdate() {
        val info = _state.value.update ?: return
        if (_state.value.updateStage is UpdateStage.Downloading) return
        if (!updates.canInstall()) {
            toast("Разрешите Фарватеру устанавливать обновления и нажмите «Обновить» ещё раз")
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
                _state.update { it.copy(updateStage = UpdateStage.Failed(e.message ?: "ошибка загрузки")) }
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
            if (mode != null) {
                detected = mode
                prefs.lastNetMode = mode.name
            }
            _state.update { it.copy(detecting = false) }
            rebuildNow()
        }.also { detectJob = it }
    }

    fun setNetMode(choice: NetModeChoice) {
        updateSettings { it.copy(netMode = choice) }
        if (choice == NetModeChoice.AUTO) detectNetMode(force = true)
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
                toast("Источники недоступны напрямую. Подключитесь к узлу из кэша — обновление пойдёт через туннель.")
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
            toast("Это не ссылка на подписку: нужен адрес, начинающийся с https://")
            return
        }
        if (SafeHttp.parse(clean) == null) {
            toast("Такой адрес подписки нельзя добавить")
            return
        }
        if (prefs.userSources.any { it.url == clean }) {
            toast("Эта подписка уже добавлена")
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
            toast("Буфер обмена пуст")
            return
        }
        if (trimmed.length > MAX_IMPORT) {
            toast("Слишком много текста для импорта")
            return
        }
        if (trimmed.lines().size == 1 && (trimmed.startsWith("https://") || trimmed.startsWith("http://"))) {
            addSubscription(trimmed)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val parsed = SubscriptionParser.parse(trimmed, ProxyNode.MANUAL_SOURCE)
            if (parsed.nodes.isEmpty()) {
                toast("Не нашёл ссылок vless://, vmess://, trojan://, ss:// или hysteria2://")
                return@launch
            }
            val added = repo.appendManual(parsed.nodes)
            snapshots.update { it + (ProxyNode.MANUAL_SOURCE to repo.loadManual()) }
            rebuild()
            toast(if (added > 0) "Добавлено узлов: $added" else "Эти узлы уже есть")
        }
    }

    fun requestImport(text: String) = _state.update { it.copy(pendingImport = text) }
    fun rejectImport() = toast("Ссылка для импорта повреждена или не поддерживается")
    fun confirmImport() {
        val text = _state.value.pendingImport ?: return
        _state.update { it.copy(pendingImport = null) }
        importText(text)
    }
    fun dismissImport() = _state.update { it.copy(pendingImport = null) }

    fun deleteManualNode(node: ProxyNode) {
        if (node.sourceId != ProxyNode.MANUAL_SOURCE) return
        repo.removeManual(node.id)
        snapshots.update { it + (ProxyNode.MANUAL_SOURCE to repo.loadManual()) }
        dropSelectionIf { it.id == node.id }
        rebuild()
    }

    fun testAll(onFinished: (() -> Unit)? = null) {
        if (testJob?.isActive == true) return
        val nodes = _state.value.nodes
        if (nodes.isEmpty()) {
            toast("Список пуст — включите источники или добавьте подписку")
            return
        }
        val settings = prefs.settings
        testing = true
        testJob = viewModelScope.launch {
            var done = 0
            var alive = 0
            var lastFlush = 0L
            val pending = HashMap<String, TestResult>()
            fun flush() {
                if (pending.isNotEmpty()) {
                    val batch = HashMap(pending)
                    pending.clear()
                    results.update { it + batch }
                }
                _state.update {
                    it.copy(
                        results = results.value,
                        progress = TestProgress(done, nodes.size, alive),
                        nodes = if (settings.liveSort) sortNodes(it.nodes, results.value) else it.nodes,
                    )
                }
            }
            _state.update { it.copy(progress = TestProgress(0, nodes.size, 0)) }
            try {
                NodeTester.testAll(nodes, settings.testUrl, settings.concurrency).collect { r ->
                    done++
                    if (r.alive) alive++
                    pending[r.nodeId] = r
                    val now = System.nanoTime() / 1_000_000
                    if (now - lastFlush >= FLUSH_MS) {
                        lastFlush = now
                        flush()
                    }
                }
            } finally {
                testing = false
                flush()
            }
            rebuildNow()
            _state.update { it.copy(progress = null) }
            onFinished?.invoke()
        }
    }

    fun testNode(node: ProxyNode) {
        if (node.id in _state.value.checking) return
        _state.update { it.copy(checking = it.checking + node.id) }
        viewModelScope.launch {
            try {
                val r = NodeTester.testAll(listOf(node), prefs.settings.testUrl, 1).first()
                results.update { it + (r.nodeId to r) }
                _state.update { it.copy(results = results.value) }
                val name = splitFlag(node.name).second
                toast(if (r.alive) "«$name» отвечает: ${r.delayMs} мс" else "«$name» не отвечает")
            } finally {
                _state.update { it.copy(checking = it.checking - node.id) }
            }
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        pathJob?.cancel()
        _state.update { it.copy(progress = null, pathfinding = null) }
    }

    private var pathJob: Job? = null

    fun findWorking() {
        viewModelScope.launch {
            detectNetMode(force = true)?.join()
            findWorkingNow()
        }
    }

    private fun findWorkingNow() {
        testAll {
            val candidates = _state.value.nodes.filter { results.value[it.id]?.alive == true }.take(PATH_CANDIDATES)
            if (candidates.isEmpty()) {
                toast("Рабочих узлов не нашлось. Обновите источники через Wi-Fi или добавьте свою подписку.")
                return@testAll
            }
            pathJob = viewModelScope.launch {
                val ordinaryBlocking = effectiveMode(prefs.settings) == NetMode.BLACK
                var chosen: ProxyNode? = null
                try {
                    for (node in candidates) {
                        _state.update { it.copy(pathfinding = node.name) }
                        val profile = Pathfinder.find(node, prefs.profileFor(node.id), ordinaryBlocking) ?: continue
                        prefs.rememberProfile(node.id, profile)
                        chosen = node
                        break
                    }
                } finally {
                    _state.update { it.copy(pathfinding = null) }
                }
                val target = chosen ?: candidates.first()
                if (chosen == null) {
                    toast("Галс не нашёл узла, через который идут данные: похоже, провайдер замораживает соединения. Подключаюсь к самому быстрому.")
                }
                setSelected(target)
                connect(target)
            }
        }
    }

    private fun bestNode(): ProxyNode? =
        _state.value.nodes.firstOrNull { results.value[it.id]?.alive == true }

    private fun reportDeadNode() {
        val current = (VpnBus.state.value as? VpnState.Connected)?.node ?: return
        toast("Узел «${current.name}» не отвечает. Проверьте интернет или выберите другой узел.")
    }

    fun toggleConnection() {
        when (VpnBus.state.value) {
            is VpnState.Connected, is VpnState.Connecting -> FarvaterVpnService.stop(getApplication())
            else -> {
                val node = _state.value.selectedNode
                    ?: (bestNode() ?: _state.value.nodes.firstOrNull())?.also(::setSelected)
                if (node == null) toast("Нет узлов. Включите источники или добавьте подписку.") else connect(node)
            }
        }
    }

    fun select(node: ProxyNode) {
        setSelected(node)
        val vpn = VpnBus.state.value
        if (vpn is VpnState.Connected && vpn.node.id != node.id) connect(node)
    }

    @Synchronized
    private fun setSelected(node: ProxyNode) {
        prefs.selectNode(node)
        _state.update { it.copy(selectedId = node.id, savedNode = node) }
    }

    @Synchronized
    private fun dropSelectionIf(predicate: (ProxyNode) -> Boolean) {
        val current = _state.value.selectedNode ?: return
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
        else if (!granted) toast("Без разрешения на VPN подключиться нельзя")
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = prefs.settings
        val new = transform(old)
        prefs.settings = new
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
        rebuildRequests.trySend(Unit)
    }

    private suspend fun rebuildNow() = withContext(Dispatchers.Default) { rebuildLocked() }

    @Synchronized
    private fun rebuildLocked() {
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
                snap.nodes.count { hideInsecure(it) }.takeIf { it > 0 }?.let { add("$it небезопасных") }
                snap.skipped.forEach { (scheme, n) -> add("$n $scheme") }
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
        }
        fun message(snap: SourceSnapshot?) = snap?.let { (listOfNotNull(it.announce) + it.notices).distinct().joinToString("\n") }?.ifBlank { null }
        val community = BuiltInCatalog.sources.map { s ->
            val snap = snaps[s.id]
            SourceUi(
                id = s.id, title = s.title, author = s.author, license = s.license, homepage = s.homepage,
                description = s.description, community = true,
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
                id = u.id, title = snap?.title ?: u.title, author = "ваша подписка", license = "",
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
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).take(5).joinToString("") { "%02x".format(it) }

    private fun hostOf(url: String) = url.substringAfter("://").substringBefore('/').ifBlank { "Подписка" }

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
        private const val PATH_CANDIDATES = 5

        private val cachedSnapshots = MutableStateFlow<Map<String, SourceSnapshot>>(emptyMap())
        private val cachedResults = MutableStateFlow<Map<String, TestResult>>(emptyMap())
        @Volatile private var cachedState: UiState? = null
    }
}

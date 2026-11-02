package app.farvater.engine

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.farvater.App
import app.farvater.core.model.NetModeChoice
import app.farvater.core.model.ProxyNode
import app.farvater.data.AppSettings
import app.farvater.data.BackupNodes
import app.farvater.data.NodeHealth
import app.farvater.data.ServiceCheck
import app.farvater.data.ServiceChecks
import app.farvater.data.ServiceReason
import app.farvater.data.ServiceVerdict
import app.farvater.vpn.NodeCheckService
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

data class NodeCheckProgress(val done: Int, val total: Int, val alive: Int)

enum class NodeCheckFailure { START, TIMEOUT }

data class NodeCheckWork(
    val session: Long = 0,
    val active: Boolean = false,
    val mode: String = "",
    val progress: NodeCheckProgress? = null,
    val checking: Set<String> = emptySet(),
    val services: Boolean = false,
    val serviceDone: Int = 0,
    val serviceTotal: Int = 0,
    val results: Map<String, TestResult> = emptyMap(),
    val revision: Long = 0,
    val failure: NodeCheckFailure? = null,
)

object NodeCheckCoordinator {
    const val MAX_DURATION_MS = 30 * 60 * 1000L
    internal val healthLock = Mutex()
    private val serviceLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(NodeCheckWork())
    val state = _state.asStateFlow()
    private var ready = CompletableDeferred<Unit>()
    private var startedAt = 0L
    private var nextJob = 0L
    private val jobs = mutableMapOf<Long, Job>()
    private var allJob: Job? = null
    private var serviceJob: Job? = null
    private var serviceGeneration = 0L
    private val singleJobs = mutableMapOf<String, Job>()

    fun testAll(context: Context, nodes: List<ProxyNode>, settings: AppSettings, mode: String,
        connected: ProxyNode?, exempt: Set<String>, allowServices: Boolean): Job? {
        if (jobs.isNotEmpty() && _state.value.mode != mode) return null
        if (allJob?.isActive == true || nodes.isEmpty()) return allJob
        cancelServices()
        val generation = serviceGeneration
        val job = launch(context, mode) {
            var done = 0
            var alive = 0
            var lastFlush = 0L
            val pending = LinkedHashMap<String, TestResult>()
            val finished = LinkedHashMap<String, TestResult>()
            val observed = LinkedHashMap<String, TestResult>()
            suspend fun flush() {
                if (pending.isNotEmpty()) {
                    val batch = pending.values.toList()
                    pending.clear()
                    record(mode, nodes, batch, exempt)
                    _state.update { it.copy(results = it.results + batch.associateBy(TestResult::nodeId), revision = it.revision + 1) }
                }
                _state.update { it.copy(progress = NodeCheckProgress(done, nodes.size, alive).takeIf { _ -> it.active }) }
            }
            suspend fun take(result: TestResult) {
                observed[result.nodeId] = result
                done++
                if (result.alive) alive++
                if (result.alive || result.nodeId !in exempt) {
                    pending[result.nodeId] = result
                    finished[result.nodeId] = result
                }
                if (done == 1 || SystemClock.elapsedRealtime() - lastFlush >= 300) {
                    lastFlush = SystemClock.elapsedRealtime()
                    flush()
                }
            }
            _state.update { it.copy(progress = NodeCheckProgress(0, nodes.size, 0)) }
            try {
                val tunnel = connected?.let { NodeTester.throughTunnel(it, settings.testUrl) }
                if (tunnel != null) take(tunnel)
                val remaining = if (tunnel == null) nodes else nodes.filter { it.id != tunnel.nodeId }
                NodeTester.testAll(remaining, settings.testUrl, settings.concurrency).collect { take(it) }
            } finally {
                withContext(NonCancellable) { flush() }
                _state.update { it.copy(progress = null) }
            }
            healthLock.withLock {
                currentCoroutineContext().ensureActive()
                if (currentMode() == mode) {
                    val current = when (val vpn = VpnBus.state.value) {
                        is VpnState.Connected -> vpn.node
                        is VpnState.Connecting -> vpn.node
                        else -> null
                    }
                    val protected = exempt + setOfNotNull(connected?.id, current?.id) +
                        if (current != null && App.prefs.userSources.any { it.id == current.sourceId }) {
                            nodes.filter { it.sourceId == current.sourceId }.map { it.id }
                        } else emptyList()
                    NodeHealth.completedCheck(nodes.mapTo(HashSet()) { it.id }, observed.values, protected)?.let { full ->
                        App.prefs.fullNodeChecks = App.prefs.fullNodeChecks + (mode to full)
                        _state.update { it.copy(revision = it.revision + 1) }
                    }
                }
            }
            if (allowServices) checkServices(context, nodes.filter {
                finished[it.id]?.let { result -> result.alive && result.method == TestMethod.REAL } == true
            }.sortedBy { finished[it.id]?.delayMs }, mode, generation)
        }
        allJob = job
        job?.start()
        return job
    }

    fun testNode(context: Context, node: ProxyNode, settings: AppSettings, mode: String,
        connected: Boolean, exempt: Set<String>): Job? {
        if (singleJobs[node.id]?.isActive == true) return null
        val generation = serviceGeneration
        val job = launch(context, mode) {
            _state.update { it.copy(checking = it.checking + node.id) }
            try {
                val tunnel = if (connected) NodeTester.throughTunnel(node, settings.testUrl) else null
                val result = tunnel ?: NodeTester.testAll(listOf(node), settings.testUrl, 1).first()
                record(mode, listOf(node), listOf(result), exempt)
                _state.update { it.copy(results = it.results + (node.id to result), revision = it.revision + 1) }
                if (result.alive && result.method == TestMethod.REAL) checkServices(context, listOf(node), mode, generation)
            } finally {
                _state.update { it.copy(checking = it.checking - node.id) }
            }
        }
        if (job != null) {
            singleJobs[node.id] = job
            job.invokeOnCompletion { if (singleJobs[node.id] === job) singleJobs.remove(node.id) }
            job.start()
        }
        return job
    }

    private suspend fun record(mode: String, nodes: List<ProxyNode>, batch: List<TestResult>, exempt: Set<String>) {
        withContext(NonCancellable + Dispatchers.Default) {
            healthLock.withLock {
                val prefs = App.prefs
                val history = NodeHealth.record(prefs.nodeHealth, mode, batch, exempt = exempt)
                prefs.nodeHealth = history
                val results = NodeHealth.results(history, mode)
                val previous = prefs.backupNodes.takeIf { it.mode == mode }?.nodes.orEmpty()
                prefs.backupNodes = BackupNodes(mode, Failover.pick((nodes + previous).distinctBy { it.id }, results, NodeHealth.dead(history, mode)))
            }
        }
    }

    private fun checkServices(context: Context, nodes: List<ProxyNode>, mode: String, generation: Long) {
        val prefs = App.prefs
        val targets = ServiceChecks.targets(prefs.settings)
        if (generation != serviceGeneration || targets.isEmpty() || serviceJob?.isActive == true) return
        val candidates = nodes.filter { node -> targets.any { target ->
            ServiceChecks.due(prefs.serviceChecks[ServiceChecks.key(mode, node.id, target)])
        } }.take(ServiceChecks.MAX_NODES)
        if (candidates.isEmpty()) return
        val job = launch(context, mode) {
            _state.update { it.copy(services = true, serviceDone = 0, serviceTotal = candidates.size * targets.size) }
            try {
                withTimeoutOrNull(90_000) {
                    for (node in candidates) {
                        if (generation != serviceGeneration || mode != currentMode() || !prefs.settings.serviceChecksEnabled) break
                        val due = serviceLock.withLock {
                            val current = prefs.serviceChecks
                            val wanted = targets.filter { ServiceChecks.due(current[ServiceChecks.key(mode, node.id, it)]) }
                            prefs.serviceChecks = current + wanted.associate { target ->
                                ServiceChecks.key(mode, node.id, target) to ServiceCheck(ServiceVerdict.UNKNOWN,
                                    ServiceReason.NETWORK, System.currentTimeMillis())
                            }
                            wanted
                        }
                        _state.update { it.copy(revision = it.revision + 1) }
                        app.farvater.net.orDefault(Unit) {
                            withTimeoutOrNull(25_000) {
                                ServiceProbe.check(node, prefs.profileFor(node.id), due) { target, result ->
                                    withContext(Dispatchers.Main.immediate) {
                                        serviceLock.withLock {
                                            if (generation == serviceGeneration) {
                                                prefs.serviceChecks = prefs.serviceChecks + (ServiceChecks.key(mode, node.id, target) to result)
                                                _state.update { it.copy(serviceDone = it.serviceDone + 1, revision = it.revision + 1) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                if (generation == serviceGeneration) _state.update { it.copy(services = false) }
            }
        }
        serviceJob = job
        job?.start()
    }

    private fun currentMode(): String = when (val mode = App.prefs.settings.netMode) {
        NetModeChoice.AUTO -> App.prefs.lastNetMode ?: "ANY"
        else -> mode.name
    }

    private fun launch(context: Context, mode: String, block: suspend CoroutineScope.() -> Unit): Job? {
        if (jobs.isNotEmpty() && _state.value.mode != mode) return null
        if (!_state.value.active && jobs.isNotEmpty()) return null
        if (jobs.isEmpty()) {
            ready = CompletableDeferred()
            startedAt = SystemClock.elapsedRealtime()
            val session = _state.value.session + 1
            _state.value = NodeCheckWork(session = session, active = true, mode = mode)
            try {
                ContextCompat.startForegroundService(context.applicationContext,
                    Intent(context, NodeCheckService::class.java).putExtra(NodeCheckService.EXTRA_SESSION, session))
            } catch (_: RuntimeException) {
                _state.update { it.copy(active = false, failure = NodeCheckFailure.START) }
                return null
            }
        }
        val session = _state.value.session
        val id = ++nextJob
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                withTimeout(5_000) { ready.await() }
                val remaining = MAX_DURATION_MS - (SystemClock.elapsedRealtime() - startedAt)
                withTimeout(remaining.coerceAtLeast(1)) { block() }
            } catch (_: TimeoutCancellationException) {
                if (isCurrent(session)) _state.update { it.copy(failure = NodeCheckFailure.TIMEOUT) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (isCurrent(session)) _state.update { it.copy(failure = NodeCheckFailure.START) }
            } finally {
                jobs.remove(id)
                if (isCurrent(session) && jobs.isEmpty()) _state.update {
                    it.copy(active = false, progress = null, checking = emptySet(), services = false)
                }
            }
        }
        jobs[id] = job
        return job
    }

    fun isCurrent(session: Long): Boolean = _state.value.active && session == _state.value.session

    fun ready(session: Long) {
        if (isCurrent(session)) ready.complete(Unit)
    }

    fun fail(session: Long, failure: NodeCheckFailure) {
        if (!isCurrent(session)) return
        _state.update { it.copy(failure = failure) }
        cancel()
    }

    fun cancelServices() {
        serviceGeneration++
        serviceJob?.cancel()
        _state.update { it.copy(services = false) }
    }

    fun cancelIfModeChanged(mode: String) {
        if (_state.value.mode != mode && jobs.isNotEmpty()) cancel()
    }

    fun cancel(session: Long = _state.value.session) {
        if (session != _state.value.session) return
        serviceGeneration++
        jobs.values.toList().forEach { it.cancel() }
        _state.update { it.copy(active = false, progress = null, checking = emptySet(), services = false) }
    }
}

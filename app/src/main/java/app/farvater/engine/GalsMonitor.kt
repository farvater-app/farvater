package app.farvater.engine

import android.content.Context
import android.os.SystemClock
import app.farvater.core.model.NetMode
import app.farvater.core.model.NetModeChoice
import app.farvater.core.model.NodeProfile
import app.farvater.core.model.ProxyNode
import app.farvater.data.GalsGroup
import app.farvater.data.GalsPolicy
import app.farvater.data.GalsStrategy
import app.farvater.data.Prefs
import app.farvater.data.SubscriptionRepository
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class GalsMonitor(
    private val context: Context,
    private val prefs: Prefs,
    private val repository: SubscriptionRepository,
    private val liveCheck: suspend (ProxyNode, NodeProfile?, GalsGroup, String, Boolean) -> PathResult = GalsProbe::live,
    private val candidateCheck: suspend (AutomaticContext, GalsGroup, String, Boolean, ProbeLane) -> PathResult =
        { checked, group, url, dns, lane ->
            GalsProbe.findInLane(checked.node, checked.profile, checked.mode == NetMode.BLACK, dns, group, url, 2, lane)
        },
) {
    private data class Reserve(val context: AutomaticContext, val profile: NodeProfile, val observedAt: Long)
    private var reserve: Reserve? = null
    private var cursor = 0

    suspend fun check(session: VpnState.Connected, activate: (AutomaticContext) -> Unit): Boolean? {
        val settings = prefs.settings
        val policy = GalsPolicy.from(settings)
        val group = policy.group ?: return null
        val mode = when (settings.netMode) {
            NetModeChoice.AUTO -> NetMode.entries.firstOrNull { it.name == prefs.lastNetMode }
            else -> NetMode.valueOf(settings.netMode.name)
        }
        val epoch = VpnBus.automaticEpoch.get()
        val network = ProbeNetwork(context)
        val stop = VpnBus.stopEpoch.get()
        val saved = prefs.profileFor(session.node.id)
        fun current() = session == VpnBus.state.value && epoch == VpnBus.automaticEpoch.get() &&
            network.current() && stop == VpnBus.stopEpoch.get() &&
            policy.sameBehavior(GalsPolicy.from(prefs.settings)) && prefs.profileFor(session.node.id) == saved &&
            prefs.lastNode == session.node && VpnBus.request.value == null
        if (!current()) return null
        val live = liveCheck(session.node, saved, group, settings.testUrl, settings.encryptedDns)
        if (!current() || live.deferred) return null
        val healthy = live.profile != null
        if (healthy && !group.periodicOptimization || !healthy && !settings.autoSwitch) return healthy
        val reason = if (healthy) AutomaticReason.OPTIMIZATION else AutomaticReason.FAILOVER

        fun select(candidate: Reserve): Boolean = synchronized(Prefs.selectionLock) {
            if (!current() || !candidate.context.valid(prefs, repository)) return@synchronized false
            if (!healthy && !prefs.settings.autoSwitch) return@synchronized false
            val command = candidate.context.withProbeResult(candidate.profile, reason)
            if (!command.valid(prefs, repository)) return@synchronized false
            activate(command)
            true
        }

        val cached = reserve
        if (!healthy && cached != null &&
            SystemClock.elapsedRealtime() - cached.observedAt in 0..group.intervalSeconds * 2_000L && select(cached)) {
            reserve = null
            return false
        }
        val sources = buildSet {
            add(ProxyNode.MANUAL_SOURCE)
            addAll(prefs.userSources.map { it.id })
            if (settings.communityEnabled) addAll(prefs.enabledSources)
        }
        sources.filter { (group.allSources || it in group.sourceIds) && it !in repository.snapshots.value }
            .forEach { repository.loadSnapshot(it) }
        val nodes = AutomaticNodes.candidates(prefs, repository, mode, policy).filter { it.id != session.node.id }
        if (nodes.isEmpty()) return healthy
        val attempts = if (healthy) 1 else minOf(8, nodes.size)
        repeat(attempts) {
            currentCoroutineContext().ensureActive()
            if (!current()) return null
            val index = cursor.mod(nodes.size)
            cursor = (index + 1) % nodes.size
            val node = nodes[index]
            val checked = AutomaticContext.capture(network, node, prefs, repository, mode, policy, reason) ?: return@repeat
            val lane = if (healthy) ProbeLane.NORMAL else ProbeLane.RECOVERY
            val result = candidateCheck(checked, group, settings.testUrl, settings.encryptedDns, lane)
            currentCoroutineContext().ensureActive()
            if (result.deferred || !current()) return null
            val profile = result.profile ?: return@repeat
            if (!checked.valid(prefs, repository)) return@repeat
            val next = Reserve(checked, profile, SystemClock.elapsedRealtime())
            reserve = next
            val improved = if (group.strategy == GalsStrategy.THROUGHPUT) GalsPlan.worthSwitching(live.speedBps, result.speedBps)
                else live.delayMs - result.delayMs >= 20 && result.delayMs < live.delayMs * 0.75
            if ((!healthy || improved) && select(next)) {
                reserve = null
                return healthy
            }
        }
        return healthy
    }
}

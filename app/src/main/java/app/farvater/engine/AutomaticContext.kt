package app.farvater.engine

import android.content.Context
import android.net.ConnectivityManager
import app.farvater.core.model.NetMode
import app.farvater.core.model.NodeProfile
import app.farvater.core.model.ProxyNode
import app.farvater.data.GalsPolicy
import app.farvater.data.Prefs
import app.farvater.data.SubscriptionRepository
import app.farvater.vpn.VpnBus

internal class ProbeNetwork(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val network = connectivity.activeNetwork
    private val epoch = VpnBus.networkEpoch.get()

    fun current(): Boolean = epoch == VpnBus.networkEpoch.get() && network == connectivity.activeNetwork
}

enum class AutomaticReason { FOREGROUND, FAILOVER, MODE_SWITCH, OPTIMIZATION }

class AutomaticContext private constructor(
    val node: ProxyNode,
    val profile: NodeProfile?,
    val policy: GalsPolicy,
    val mode: NetMode?,
    val reason: AutomaticReason,
    private val selection: ProxyNode?,
    private val automaticEpoch: Long,
    private val stopEpoch: Long,
    private val network: ProbeNetwork,
    private val detectedMode: String?,
    val connectionProfile: NodeProfile? = profile,
) {
    internal fun sameCommandGeneration() = automaticEpoch == VpnBus.automaticEpoch.get() && stopEpoch == VpnBus.stopEpoch.get()

    fun valid(prefs: Prefs, repository: SubscriptionRepository): Boolean = synchronized(Prefs.selectionLock) {
        sameCommandGeneration() && network.current() && prefs.lastNode == selection &&
            (prefs.settings.netMode != app.farvater.core.model.NetModeChoice.AUTO || prefs.lastNetMode == detectedMode) &&
            prefs.profileFor(node.id) == profile &&
            (reason != AutomaticReason.FAILOVER || prefs.settings.autoSwitch) &&
            (reason != AutomaticReason.MODE_SWITCH || (prefs.settings.modeNodes && mode != null && prefs.nodeForMode(mode) == node)) &&
            (reason != AutomaticReason.OPTIMIZATION || policy.group?.periodicOptimization == true) &&
            AutomaticNodes.resolve(node, prefs, repository, mode, policy) != null
    }

    fun withProbeResult(verified: NodeProfile, purpose: AutomaticReason = reason) =
        AutomaticContext(node, profile, policy, mode, purpose, selection, automaticEpoch, stopEpoch, network,
            detectedMode, verified)

    fun commit(prefs: Prefs, repository: SubscriptionRepository): Boolean = synchronized(Prefs.selectionLock) {
        if (!valid(prefs, repository)) return@synchronized false
        connectionProfile?.let { prefs.rememberProfile(node.id, it) }
        prefs.selectNode(node)
        true
    }

    companion object {
        internal fun capture(network: ProbeNetwork, node: ProxyNode, prefs: Prefs, repository: SubscriptionRepository, mode: NetMode?,
            policy: GalsPolicy, reason: AutomaticReason = AutomaticReason.FOREGROUND): AutomaticContext? =
            synchronized(Prefs.selectionLock) {
                val context = AutomaticContext(node, prefs.profileFor(node.id), policy, mode, reason, prefs.lastNode,
                    VpnBus.automaticEpoch.get(), VpnBus.stopEpoch.get(), network, prefs.lastNetMode)
                context.takeIf { it.valid(prefs, repository) }
            }
    }
}

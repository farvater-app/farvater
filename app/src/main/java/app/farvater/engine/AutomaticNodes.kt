package app.farvater.engine

import app.farvater.core.catalog.BuiltInCatalog
import app.farvater.core.model.NetMode
import app.farvater.core.model.ProxyNode
import app.farvater.core.model.SourceMode
import app.farvater.data.GalsPolicy
import app.farvater.data.Prefs
import app.farvater.data.SubscriptionRepository

object AutomaticNodes {
    fun candidates(prefs: Prefs, repository: SubscriptionRepository, mode: NetMode?,
        expected: GalsPolicy? = null): List<ProxyNode> = synchronized(Prefs.selectionLock) {
        val policy = GalsPolicy.from(prefs.settings)
        if (expected != null && !expected.sameBehavior(policy)) return@synchronized emptyList()
        repository.snapshots.value.values.asSequence().flatMap { it.nodes }
            .filter { allowed(it, prefs, mode, policy) }.distinctBy { it.id }.toList()
    }

    fun resolve(node: ProxyNode, prefs: Prefs, repository: SubscriptionRepository, mode: NetMode?,
        expected: GalsPolicy? = null): ProxyNode? = synchronized(Prefs.selectionLock) {
        val policy = GalsPolicy.from(prefs.settings)
        if (expected != null && !expected.sameBehavior(policy) || !allowed(node, prefs, mode, policy)) return@synchronized null
        repository.snapshots.value[node.sourceId]?.nodes?.firstOrNull { it == node }
    }

    private fun allowed(node: ProxyNode, prefs: Prefs, mode: NetMode?, policy: GalsPolicy): Boolean {
        if (!policy.accepts(node) || !prefs.allowedForAuto(node)) return false
        val sourceMode = prefs.sourceConfigs[node.sourceId]?.mode
            ?: BuiltInCatalog.byId(node.sourceId)?.mode ?: SourceMode.ANY
        return sourceMode.fits(mode)
    }
}

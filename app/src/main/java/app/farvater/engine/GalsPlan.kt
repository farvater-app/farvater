package app.farvater.engine

import app.farvater.core.model.ProxyNode

object GalsPlan {
    const val QUICK = 3

    fun kind(node: ProxyNode): String = "${node.protocol}/${node.security}/${node.transport}"

    fun quick(
        nodes: List<ProxyNode>,
        results: Map<String, TestResult>,
        dead: Set<String>,
        preferred: List<ProxyNode?>,
    ): List<ProxyNode> {
        val known = nodes
            .filter { results[it.id]?.alive == true && it.id !in dead }
            .sortedWith(compareBy({ results.getValue(it.id).method != TestMethod.REAL }, { results.getValue(it.id).delayMs }))
        val listed = nodes.mapTo(HashSet()) { it.id }
        return (preferred.filterNotNull().filter { it.id !in dead && it.id in listed } + known).distinctBy { it.id }.take(QUICK)
    }

    fun next(
        nodes: List<ProxyNode>,
        results: Map<String, TestResult>,
        dead: Set<String>,
        tried: Set<String>,
        failedKinds: Set<String>,
        failedHosts: Set<String>,
        since: Long,
    ): ProxyNode? = nodes
        .filter { it.id !in tried && it.id !in dead }
        .mapNotNull { node -> results[node.id]?.takeIf { it.alive && it.at >= since }?.let { node to it } }
        .sortedWith(
            compareBy(
                { (node, _) -> kind(node) in failedKinds || node.address.lowercase() in failedHosts },
                { (_, r) -> r.method != TestMethod.REAL },
                { (_, r) -> r.delayMs },
            ),
        )
        .firstOrNull()?.first
}

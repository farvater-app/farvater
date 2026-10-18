package app.farvater.engine

import app.farvater.core.model.ProxyNode

object GalsPlan {
    private const val UPGRADE_RATIO = 1.5

    fun kind(node: ProxyNode): String = "${node.protocol}/${node.security}/${node.transport}"

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

    fun fastest(verified: Map<String, Long>): String? = verified.maxByOrNull { it.value }?.key

    fun worthSwitching(currentSpeed: Long, bestSpeed: Long): Boolean = bestSpeed > currentSpeed * UPGRADE_RATIO
}

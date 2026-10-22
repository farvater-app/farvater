package app.farvater.engine

import app.farvater.core.model.ProxyNode

object Failover {
    const val GAP_MS = 2 * 60 * 1000L
    const val FAST_PROBE_S = 10
    const val BACKUPS = 8
    const val TRIES = 5

    fun pick(nodes: List<ProxyNode>, results: Map<String, TestResult>, dead: Set<String>, limit: Int = BACKUPS): List<ProxyNode> =
        nodes.filter { it.id !in dead && results[it.id]?.alive == true }
            .sortedWith(compareBy({ results.getValue(it.id).method != TestMethod.REAL }, { results.getValue(it.id).delayMs }))
            .take(limit)

    const val SAME_FIRST = 2

    fun order(backups: List<ProxyNode>, current: ProxyNode, skip: Set<String> = emptySet()): List<ProxyNode> {
        val left = backups.filter { it.id != current.id && it.id !in skip }
        val (same, others) = left.partition { it.sourceId == current.sourceId }
        return same.take(SAME_FIRST) + others + same.drop(SAME_FIRST)
    }

    fun probeDue(tick: Int, lastProbe: Int, failures: Int, every: Int, early: Set<Int>, stall: Int): Boolean =
        tick in early || tick % every == 0 || (failures in 1 until stall && tick - lastProbe >= FAST_PROBE_S)
}

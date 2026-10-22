package app.farvater.data

import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import kotlinx.serialization.Serializable

@Serializable
data class NodeCheck(val delayMs: Long, val method: TestMethod, val at: Long, val fails: Int = 0)

object NodeHealth {
    const val TTL_MS = 24 * 60 * 60 * 1000L
    const val DEAD_AFTER = 2
    private const val LIMIT = 3000

    fun record(
        history: Map<String, NodeCheck>,
        mode: String,
        results: Collection<TestResult>,
        now: Long = System.currentTimeMillis(),
        exempt: Set<String> = emptySet(),
    ): Map<String, NodeCheck> {
        val out = prune(history, now).toMutableMap()
        results.forEach { r ->
            val key = key(mode, r.nodeId)
            val before = out[key]?.fails ?: 0
            val fails = when {
                r.alive -> 0
                r.nodeId in exempt -> before
                else -> before + 1
            }
            out[key] = NodeCheck(r.delayMs, r.method, r.at, fails)
        }
        if (out.size <= LIMIT) return out
        return out.entries.sortedBy { it.value.at }.takeLast(LIMIT).associate { it.key to it.value }
    }

    fun prune(history: Map<String, NodeCheck>, now: Long = System.currentTimeMillis()): Map<String, NodeCheck> =
        history.filterValues { now - it.at < TTL_MS }

    fun results(history: Map<String, NodeCheck>, mode: String, now: Long = System.currentTimeMillis()): Map<String, TestResult> =
        fresh(history, mode, now).map { (id, c) -> id to TestResult(id, c.delayMs, c.method, c.at) }.toMap()

    fun dead(history: Map<String, NodeCheck>, mode: String, now: Long = System.currentTimeMillis()): Set<String> =
        fresh(history, mode, now).filter { it.second.fails >= DEAD_AFTER }.map { it.first }.toSet()

    private fun fresh(history: Map<String, NodeCheck>, mode: String, now: Long): List<Pair<String, NodeCheck>> {
        val prefix = "$mode/"
        return history.filter { it.key.startsWith(prefix) && now - it.value.at < TTL_MS }
            .map { it.key.removePrefix(prefix) to it.value }
    }

    private fun key(mode: String, nodeId: String) = "$mode/$nodeId"
}

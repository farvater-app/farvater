package app.farvater.data

import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import app.farvater.engine.TestFailure
import kotlinx.serialization.Serializable

@Serializable
data class NodeCheck(val delayMs: Long, val method: TestMethod, val at: Long, val fails: Int = 0,
    val reason: TestFailure = TestFailure.UNKNOWN)

@Serializable
data class FullNodeCheck(val failed: Set<String> = emptySet(), val at: Long = 0)

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
            out[key] = NodeCheck(r.delayMs, r.method, r.at, fails, reason = r.reason)
        }
        if (out.size <= LIMIT) return out
        return out.entries.sortedBy { it.value.at }.takeLast(LIMIT).associate { it.key to it.value }
    }

    fun prune(history: Map<String, NodeCheck>, now: Long = System.currentTimeMillis()): Map<String, NodeCheck> =
        history.filterValues { now - it.at < TTL_MS }

    fun results(history: Map<String, NodeCheck>, mode: String, now: Long = System.currentTimeMillis()): Map<String, TestResult> =
        fresh(history, mode, now).map { (id, c) -> id to TestResult(id, c.delayMs, c.method, c.at, reason = c.reason) }.toMap()

    fun dead(history: Map<String, NodeCheck>, mode: String, now: Long = System.currentTimeMillis()): Set<String> =
        fresh(history, mode, now).filter { it.second.fails >= DEAD_AFTER }.map { it.first }.toSet()

    fun completedCheck(
        expected: Set<String>,
        results: Collection<TestResult>,
        exempt: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis(),
    ): FullNodeCheck? {
        val checked = results.associateBy { it.nodeId }
        if (expected.isEmpty() || checked.keys != expected) return null
        return FullNodeCheck(checked.values.filter { !it.alive && it.nodeId !in exempt }
            .take(LIMIT).mapTo(LinkedHashSet()) { it.nodeId }, now)
    }

    fun groupedDead(
        history: Map<String, NodeCheck>,
        full: FullNodeCheck?,
        mode: String,
        eligible: Set<String>,
        exempt: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis(),
    ): Set<String> {
        val failed = when {
            full == null -> dead(history, mode, now)
            now - full.at >= TTL_MS -> emptySet()
            else -> full.failed.filterTo(HashSet()) { id ->
                val latest = history[key(mode, id)]
                latest == null || latest.delayMs <= 0 || now - latest.at >= TTL_MS
            }
        }
        return (failed intersect eligible) - exempt
    }

    private fun fresh(history: Map<String, NodeCheck>, mode: String, now: Long): List<Pair<String, NodeCheck>> {
        val prefix = "$mode/"
        return history.filter { it.key.startsWith(prefix) && now - it.value.at < TTL_MS }
            .map { it.key.removePrefix(prefix) to it.value }
    }

    private fun key(mode: String, nodeId: String) = "$mode/$nodeId"
}

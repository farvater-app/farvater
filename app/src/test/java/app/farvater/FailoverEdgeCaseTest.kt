package app.farvater

import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import app.farvater.engine.Failover
import app.farvater.engine.BackupCooldowns
import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import org.junit.Assert.*
import org.junit.Test

class FailoverEdgeCaseTest {
    @Test
    fun failedBackupsBecomeEligibleAgainWithBoundedBackoff() {
        val cooldowns = BackupCooldowns()
        cooldowns.failed("a", 1_000)
        assertEquals(setOf("a"), cooldowns.excluded(30_999))
        assertTrue(cooldowns.excluded(31_000).isEmpty())
        cooldowns.failed("a", 31_000)
        assertEquals(setOf("a"), cooldowns.excluded(90_999))
        assertTrue(cooldowns.excluded(91_000).isEmpty())
        cooldowns.failed("a", 91_000)
        assertTrue(cooldowns.excluded(211_000).isEmpty())
        cooldowns.failed("a", 211_000)
        assertTrue(cooldowns.excluded(331_000).isEmpty())
    }

    @Test
    fun backupFailuresAreIndependentAndResetAfterRecovery() {
        val cooldowns = BackupCooldowns()
        cooldowns.failed("a", 1_000)
        cooldowns.failed("b", 20_000)
        assertEquals(setOf("b"), cooldowns.excluded(31_000))
        cooldowns.clear()
        assertTrue(cooldowns.excluded(31_000).isEmpty())
        cooldowns.failed("a", 31_000)
        assertTrue(cooldowns.excluded(61_000).isEmpty())
    }

    @Test
    fun emptyCandidatesAndZeroLimitReturnNothing() {
        assertTrue(Failover.order(emptyList(), node("current")).isEmpty())
        assertTrue(Failover.pick(emptyList(), emptyMap(), emptySet()).isEmpty())
        assertTrue(Failover.pick(listOf(node("a")), mapOf(alive("a", 1)), emptySet(), limit = 0).isEmpty())
    }

    @Test
    fun currentAndSkippedIdsRemoveEveryDuplicate() {
        val current = node("current")
        val skipped = node("skip")
        val keep = node("keep")
        assertEquals(listOf(keep, keep), Failover.order(listOf(current, skipped, keep, current, skipped, keep), current, setOf("skip")))
    }

    @Test
    fun repeatedSourcesKeepStableOrderAroundSameSourceQuota() {
        val same = (1..4).map { node("same$it") }
        val other = node("other", "other-source")
        assertEquals(listOf(same[0], same[1], other, other, same[2], same[3]),
            Failover.order(same + listOf(other, other), node("current")))
    }

    @Test
    fun pickExcludesDeadMissingAndFailedAndPrefersRealBeforeTcp() {
        val nodes = listOf("missing", "dead", "failed", "tcp", "real", "tie").map { node(it) }
        val results = mapOf(alive("dead", 1), alive("failed", -1), alive("tcp", 1, TestMethod.TCP), alive("real", 50), alive("tie", 50))
        assertEquals(listOf("real", "tie", "tcp"), Failover.pick(nodes, results, setOf("dead")).map { it.id })
    }

    private fun node(id: String, source: String = "same") = ProxyNode(id, Protocol.VLESS, id, "$id.example.test", 443, "test", raw = "vless://test", sourceId = source)
    private fun alive(id: String, delay: Long, method: TestMethod = TestMethod.REAL) = id to TestResult(id, delay, method, 1)
}

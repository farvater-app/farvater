package app.farvater

import app.farvater.data.FullNodeCheck
import app.farvater.data.NodeHealth
import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import org.junit.Assert.*
import org.junit.Test

class FullNodeCheckTest {
    private val now = 1_000_000L
    private val ids = setOf("one", "two")
    private fun result(id: String, alive: Boolean) = TestResult(id, if (alive) 25 else -1, TestMethod.REAL, now)

    @Test fun firstCompletedFailureGroupsImmediatelyButAutomaticPolicyStillNeedsTwo() {
        val checks = listOf(result("one", false), result("two", true))
        var history = NodeHealth.record(emptyMap(), "BLACK", checks, now)
        val full = NodeHealth.completedCheck(ids, checks, now = now)!!
        assertEquals(setOf("one"), NodeHealth.groupedDead(history, full, "BLACK", ids, now = now))
        assertTrue(NodeHealth.dead(history, "BLACK", now).isEmpty())
        history = NodeHealth.record(history, "BLACK", checks, now)
        assertEquals(setOf("one"), NodeHealth.dead(history, "BLACK", now))
    }

    @Test fun completedSuccessClearsThePreviousGroup() {
        val failed = listOf(result("one", false), result("two", false))
        val success = listOf(result("one", true), result("two", true))
        val history = NodeHealth.record(NodeHealth.record(emptyMap(), "BLACK", failed, now), "BLACK", success, now)
        val full = NodeHealth.completedCheck(ids, success, now = now)!!
        assertTrue(full.failed.isEmpty())
        assertTrue(NodeHealth.groupedDead(history, full, "BLACK", ids, now = now).isEmpty())
    }

    @Test fun partialCheckCannotReplaceCompletedSnapshot() {
        val previous = FullNodeCheck(setOf("two"), now - 10)
        val partial = listOf(result("one", false))
        val next = NodeHealth.completedCheck(ids, partial, now = now) ?: previous
        assertEquals(previous, next)
        assertNull(NodeHealth.completedCheck(emptySet(), emptyList(), now = now))
    }

    @Test fun protectedConnectedAndSameAccountNodesAreNotGrouped() {
        val checks = ids.map { result(it, false) }
        val full = NodeHealth.completedCheck(ids, checks, exempt = setOf("one"), now = now)!!
        assertEquals(setOf("two"), full.failed)
        assertTrue(NodeHealth.groupedDead(emptyMap(), full, "BLACK", ids, exempt = setOf("two"), now = now).isEmpty())
    }

    @Test fun onlyCurrentEligibleNodesAreVisibleAndOldCompleteCheckExpires() {
        val full = FullNodeCheck(ids, now)
        assertEquals(setOf("one"), NodeHealth.groupedDead(emptyMap(), full, "BLACK", setOf("one"), now = now))
        assertTrue(NodeHealth.groupedDead(emptyMap(), full, "BLACK", ids, now = now + NodeHealth.TTL_MS).isEmpty())
    }

    @Test fun successfulIndividualRecheckReturnsNodeWithoutAddingOtherIndividualFailures() {
        val full = FullNodeCheck(setOf("one"), now)
        val history = NodeHealth.record(emptyMap(), "BLACK", listOf(result("one", true), result("two", false)), now)
        assertTrue(NodeHealth.groupedDead(history, full, "BLACK", ids, now = now).isEmpty())
        assertEquals(setOf("one"), full.failed)
    }

    @Test fun existingHealthRemainsVisibleUntilFirstCompleteSnapshot() {
        val checks = listOf(result("one", false))
        val history = NodeHealth.record(NodeHealth.record(emptyMap(), "BLACK", checks, now), "BLACK", checks, now)
        assertEquals(setOf("one"), NodeHealth.groupedDead(history, null, "BLACK", ids, now = now))
        assertTrue(NodeHealth.groupedDead(history, null, "WHITE", ids, now = now).isEmpty())
    }
}

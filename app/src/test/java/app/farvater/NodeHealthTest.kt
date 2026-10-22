package app.farvater

import app.farvater.data.NodeHealth
import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeHealthTest {
    private val now = 1_000_000_000L
    private fun dead(id: String) = TestResult(id, -1, TestMethod.REAL, now)
    private fun alive(id: String) = TestResult(id, 120, TestMethod.REAL, now)

    @Test fun nodeIsDeadOnlyAfterTwoFailuresInARow() {
        var h = NodeHealth.record(emptyMap(), "WHITE", listOf(dead("a")), now)
        assertTrue(NodeHealth.dead(h, "WHITE", now).isEmpty())
        h = NodeHealth.record(h, "WHITE", listOf(dead("a")), now)
        assertEquals(setOf("a"), NodeHealth.dead(h, "WHITE", now))
    }

    @Test fun oneSuccessBringsNodeBack() {
        var h = NodeHealth.record(emptyMap(), "WHITE", listOf(dead("a")), now)
        h = NodeHealth.record(h, "WHITE", listOf(dead("a")), now)
        h = NodeHealth.record(h, "WHITE", listOf(alive("a")), now)
        assertTrue(NodeHealth.dead(h, "WHITE", now).isEmpty())
    }

    @Test fun networkModesAreSeparate() {
        var h = NodeHealth.record(emptyMap(), "WHITE", listOf(dead("a")), now)
        h = NodeHealth.record(h, "WHITE", listOf(dead("a")), now)
        assertTrue(NodeHealth.dead(h, "BLACK", now).isEmpty())
        assertTrue(NodeHealth.results(h, "BLACK", now).isEmpty())
    }

    @Test fun oldChecksAreForgotten() {
        var h = NodeHealth.record(emptyMap(), "WHITE", listOf(dead("a")), now)
        h = NodeHealth.record(h, "WHITE", listOf(dead("a")), now)
        val later = now + NodeHealth.TTL_MS + 1
        assertTrue(NodeHealth.dead(h, "WHITE", later).isEmpty())
        assertTrue(NodeHealth.results(h, "WHITE", later).isEmpty())
    }

    @Test fun resultsSurviveForTheList() {
        val h = NodeHealth.record(emptyMap(), "BLACK", listOf(alive("b")), now)
        assertEquals(120L, NodeHealth.results(h, "BLACK", now)["b"]?.delayMs)
    }

    @Test fun exemptFailuresDoNotPileUpButOthersDo() {
        var h = NodeHealth.record(emptyMap(), "WHITE", listOf(dead("paid"), dead("public")), now, exempt = setOf("paid"))
        h = NodeHealth.record(h, "WHITE", listOf(dead("paid"), dead("public")), now, exempt = setOf("paid"))
        assertEquals(setOf("public"), NodeHealth.dead(h, "WHITE", now))
        assertEquals(-1L, NodeHealth.results(h, "WHITE", now)["paid"]?.delayMs)
    }

    @Test fun pruneDropsChecksOlderThanADay() {
        val h = NodeHealth.record(emptyMap(), "WHITE", listOf(alive("a")), now)
        assertEquals(1, NodeHealth.prune(h, now).size)
        assertTrue(NodeHealth.prune(h, now + NodeHealth.TTL_MS + 1).isEmpty())
    }
}

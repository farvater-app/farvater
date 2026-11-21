package app.farvater

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.model.NodeProfile
import app.farvater.core.parser.LinkParser
import app.farvater.data.GalsGroup
import app.farvater.data.GalsStrategy
import app.farvater.data.SourceSnapshot
import app.farvater.engine.AutomaticContext
import app.farvater.engine.NodeCheckCoordinator
import app.farvater.engine.GalsMonitor
import app.farvater.engine.PathResult
import app.farvater.engine.ProbeLane
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GalsMonitorTest {
    private val live = LinkParser.parse("vless://test@first.example.test:443?security=tls")!!
    private val spare = LinkParser.parse("vless://test@second.example.test:443?security=tls")!!
    private val session = VpnState.Connected(live, 1)
    private val working = PathResult(NodeProfile(), delayMs = 40, speedBps = 1000)
    private var current = working
    private var alternative = working
    private var probeHook: () -> Unit = {}
    private val lanes = mutableListOf<ProbeLane>()
    private var selected: AutomaticContext? = null
    private lateinit var monitor: GalsMonitor

    @Before fun setup() {
        prepareRuntime(ApplicationProvider.getApplicationContext<Application>())
        val group = GalsGroup("group", "Group", periodicOptimization = true)
        App.prefs.settings = App.prefs.settings.copy(galsGroups = listOf(group), activeGalsGroupId = group.id)
        App.prefs.selectNode(live)
        App.repo.snapshots.value = mapOf(live.sourceId to SourceSnapshot(live.sourceId, null, null, emptyList(), listOf(live, spare), 1))
        VpnBus.state.value = session
        monitor = GalsMonitor(ApplicationProvider.getApplicationContext<Application>(), App.prefs, App.repo, { _, _, _, _, _ -> current },
            { _, _, _, _, lane -> lanes += lane; probeHook(); alternative })
    }

    private suspend fun tick() = monitor.check(session) { selected = it }

    @Test fun smallImprovementKeepsTheTunnelAndSavesAReadyReserve() = runBlocking {
        alternative = working.copy(delayMs = 38)
        assertEquals(true, tick())
        assertNull(selected)
        current = PathResult(null)
        assertEquals(false, tick())
        assertEquals(listOf(ProbeLane.NORMAL), lanes)
        assertEquals(spare, selected?.node)
        assertEquals(live, App.prefs.lastNode)
        assertNull(App.prefs.profileFor(spare.id))
        assertTrue(selected!!.commit(App.prefs, App.repo))
        assertEquals(spare, App.prefs.lastNode)
        assertEquals(alternative.profile, App.prefs.profileFor(spare.id))
    }

    @Test fun removedReserveCannotBeRevivedWhenTheTunnelFails() = runBlocking {
        tick()
        App.repo.snapshots.value = App.repo.snapshots.value.mapValues { it.value.copy(nodes = listOf(live)) }
        current = PathResult(null)
        tick()
        assertNull(selected)
        assertEquals(live, App.prefs.lastNode)
    }

    @Test fun optOutStopsOptimizationAndFailoverIndependently() = runBlocking {
        App.prefs.settings = App.prefs.settings.copy(autoSwitch = false, galsGroups = App.prefs.settings.galsGroups.map {
            it.copy(periodicOptimization = false)
        })
        tick()
        current = PathResult(null)
        tick()
        assertTrue(lanes.isEmpty())
        assertNull(selected)
    }

    @Test fun failedTargetUsesRecoveryLaneAndCannotUseUnverifiedAlternative() = runBlocking {
        current = PathResult(null)
        alternative = PathResult(null)
        tick()
        assertEquals(listOf(ProbeLane.RECOVERY), lanes)
        assertNull(selected)
    }

    @Test fun speedStrategyCanUpgradeDespiteHigherLatency() = runBlocking {
        App.prefs.settings = App.prefs.settings.copy(galsGroups = App.prefs.settings.galsGroups.map {
            it.copy(strategy = GalsStrategy.THROUGHPUT)
        })
        alternative = working.copy(delayMs = 90, speedBps = 2000)
        tick()
        assertEquals(spare, selected?.node)
    }

    private fun startManualCheck() = NodeCheckCoordinator.testAll(ApplicationProvider.getApplicationContext<Application>(),
        listOf(spare), App.prefs.settings, "BLACK", null, emptySet(), false)

    @Test fun activeManualCheckDefersOptimizationButAllowsFailover() = runBlocking {
        val job = requireNotNull(startManualCheck())
        try {
            alternative = working.copy(delayMs = 5)
            assertEquals(true, tick())
            assertTrue(lanes.isEmpty())
            assertNull(selected)
            current = PathResult(null)
            assertEquals(false, tick())
            assertEquals(listOf(ProbeLane.RECOVERY), lanes)
            assertEquals(spare, selected?.node)
        } finally {
            NodeCheckCoordinator.cancel()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            job.join()
        }
    }

    @Test fun manualCheckStartingDuringOptimizationPreservesTheLiveNode() = runBlocking {
        alternative = working.copy(delayMs = 5)
        var job: kotlinx.coroutines.Job? = null
        probeHook = { job = startManualCheck() }
        try {
            assertEquals(true, tick())
            assertNotNull(job)
            assertNull(selected)
        } finally {
            NodeCheckCoordinator.cancel()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            job?.join()
        }
        Unit
    }

    @Test fun manualChoiceDuringProbeWins() = runBlocking {
        current = PathResult(null)
        probeHook = { VpnBus.automaticEpoch.incrementAndGet() }
        tick()
        assertNull(selected)
        assertEquals(live, App.prefs.lastNode)
    }
}

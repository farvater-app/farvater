package app.farvater.engine

import app.farvater.core.model.ProxyNode
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object LiveNodeCheck {
    suspend fun measure(node: ProxyNode, url: String): TestResult {
        val session = (VpnBus.state.value as? VpnState.Connected)?.takeIf { it.node.id == node.id }
            ?: return TestResult(node.id, -1, TestMethod.REAL, reason = TestFailure.STALE)
        val epoch = VpnBus.stopEpoch.get()
        val result = try {
            withContext(Dispatchers.IO) { NodeTester.measureLiveLatency(node, url) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            TestResult(node.id, -1, TestMethod.REAL, reason = TestFailure.from(error))
        }
        return if (VpnBus.state.value == session && VpnBus.stopEpoch.get() == epoch) result
            else TestResult(node.id, -1, TestMethod.REAL, reason = TestFailure.STALE)
    }
}

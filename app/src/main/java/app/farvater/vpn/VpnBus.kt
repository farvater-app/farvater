package app.farvater.vpn

import app.farvater.core.model.NetMode
import app.farvater.core.model.ProxyNode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface VpnState {
    data object Idle : VpnState
    data class Connecting(val node: ProxyNode) : VpnState
    data class Connected(val node: ProxyNode, val since: Long) : VpnState
    data class Failed(val message: String, val node: ProxyNode?) : VpnState
}

data class Traffic(
    val upBps: Long = 0,
    val downBps: Long = 0,
    val upTotal: Long = 0,
    val downTotal: Long = 0,
    val today: Long = 0,
)

object VpnBus {
    const val STALL_CHECKS = 3
    val state = MutableStateFlow<VpnState>(VpnState.Idle)
    val traffic = MutableStateFlow(Traffic())
    val speedHistory = MutableStateFlow<List<Long>>(emptyList())
    val failedChecks = MutableStateFlow(0)
    val netMode = MutableSharedFlow<NetMode>(extraBufferCapacity = 4)
}

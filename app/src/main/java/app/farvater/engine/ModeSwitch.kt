package app.farvater.engine

import app.farvater.core.model.NetMode

sealed interface ModeStep {
    data object Done : ModeStep
    data class Wait(val ms: Long) : ModeStep
    data object Switch : ModeStep
}

object ModeSwitch {
    const val GAP_MS = 2 * 60 * 1000L
    const val RETRY_MS = 15_000L
    const val ATTEMPTS = 6

    fun step(mode: NetMode?, targetId: String?, currentId: String?, now: Long, lastSwitch: Long): ModeStep = when {
        mode == null -> ModeStep.Wait(RETRY_MS)
        targetId == null || currentId == null || targetId == currentId -> ModeStep.Done
        now - lastSwitch < GAP_MS -> ModeStep.Wait(GAP_MS - (now - lastSwitch))
        else -> ModeStep.Switch
    }

    fun recheckAfter(failures: Int, stallChecks: Int): Boolean = failures >= stallChecks && failures % stallChecks == 0
}

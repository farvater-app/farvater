package app.farvater.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.farvater.R
import app.farvater.data.ServiceCheck
import app.farvater.data.ServiceChecks
import app.farvater.data.ServiceTarget
import app.farvater.data.ServiceVerdict
import app.farvater.ui.screens.serviceName
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType

@Composable
fun ServiceBadges(nodeId: String, mode: String, targets: List<ServiceTarget>, checks: Map<String, ServiceCheck>, now: Long) {
    val fresh = targets.mapNotNull { target ->
        checks[ServiceChecks.key(mode, nodeId, target)]?.takeIf { ServiceChecks.fresh(it, now) }?.let { target to it }
    }
    if (fresh.isEmpty()) return
    FlowRow(Modifier.padding(start = 68.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        fresh.forEach { (target, check) ->
            val name = serviceName(target.kind)
            val symbol = when (check.verdict) {
                ServiceVerdict.RESPONDED -> "✓"
                ServiceVerdict.REJECTED -> "✗"
                ServiceVerdict.UNKNOWN -> "?"
            }
            val description = stringResource(when (check.verdict) {
                ServiceVerdict.RESPONDED -> R.string.services_responded
                ServiceVerdict.REJECTED -> R.string.services_rejected
                ServiceVerdict.UNKNOWN -> R.string.services_unknown
            }, name)
            Text("$name $symbol", style = IosType.caption1, color = Ios.colors.label,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description })
        }
    }
}

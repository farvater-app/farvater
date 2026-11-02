package app.farvater.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.farvater.data.ServiceReason
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
            val description = serviceDescription(target, check)
            Text("$name $symbol", style = IosType.caption1, color = Ios.colors.label,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description })
        }
    }
}

@Composable
fun ServiceCheckDetails(nodeId: String, mode: String, targets: List<ServiceTarget>, checks: Map<String, ServiceCheck>, now: Long) {
    val fresh = targets.mapNotNull { target ->
        checks[ServiceChecks.key(mode, nodeId, target)]?.takeIf { ServiceChecks.fresh(it, now) }?.let { target to it }
    }
    if (fresh.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.services_details), style = IosType.headline, color = Ios.colors.label, modifier = Modifier.fillMaxWidth())
        fresh.forEach { (target, check) ->
            Text(serviceDescription(target, check), style = IosType.subheadline, color = Ios.colors.label, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun serviceDescription(target: ServiceTarget, check: ServiceCheck): String {
    val resource = if (check.verdict == ServiceVerdict.RESPONDED) R.string.services_responded else when (check.reason) {
        ServiceReason.REGION -> if (check.verdict == ServiceVerdict.REJECTED) R.string.service_reason_region else R.string.services_unknown
        ServiceReason.CHALLENGE -> R.string.service_reason_challenge
        ServiceReason.HTTP_ERROR -> R.string.service_reason_http
        ServiceReason.UNEXPECTED -> R.string.service_reason_unexpected
        ServiceReason.NETWORK -> R.string.service_reason_network
        ServiceReason.INCOMPLETE -> R.string.service_reason_incomplete
        ServiceReason.CORE -> R.string.service_reason_core
        ServiceReason.TIMEOUT -> R.string.service_reason_timeout
        ServiceReason.TLS -> R.string.service_reason_tls
        else -> if (check.verdict == ServiceVerdict.REJECTED) R.string.services_rejected else R.string.services_unknown
    }
    return stringResource(resource, serviceName(target.kind))
}

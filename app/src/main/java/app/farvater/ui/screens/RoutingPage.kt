package app.farvater.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.farvater.R
import app.farvater.data.AppSettings
import app.farvater.data.RouteAction
import app.farvater.data.RouteRule
import app.farvater.ui.ios.*

internal fun routeLabel(action: RouteAction): Int = when (action) {
    RouteAction.VPN -> R.string.route_vpn
    RouteAction.DIRECT -> R.string.route_direct
    RouteAction.BLOCK -> R.string.route_block
}

@Composable
internal fun RoutingPage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val rules = settings.routeRules
    IosSection(footer = stringResource(R.string.routes_help)) {
        IosRow(stringResource(R.string.routes_auto), checked = settings.automaticRoutes,
            onCheckedChange = { enabled -> onChange { it.copy(automaticRoutes = enabled) } })
    }
    IosSection(footer = stringResource(R.string.route_hint)) {
        if (rules.isEmpty()) IosRow(stringResource(R.string.route_empty))
        rules.forEachIndexed { index, rule ->
            if (index > 0) IosDivider()
            IosRow(app.farvater.ui.ltrText(rule.target), subtitle = stringResource(routeLabel(rule.action)), chevron = true,
                titleLines = 3, onClick = { editing = rule.target })
        }
    }
    IosButton(stringResource(R.string.route_add), onClick = { editing = "" },
        enabled = rules.size < RouteRule.LIMIT, modifier = Modifier.fillMaxWidth())
    editing?.let { original ->
        val existing = rules.find { it.target == original }
        key(original) {
            var target by rememberSaveable { mutableStateOf(original) }
            var action by rememberSaveable { mutableStateOf(existing?.action ?: RouteAction.VPN) }
            val candidate = RouteRule.parse(target, action)
            val valid = candidate != null && (original.isEmpty() || existing != null) &&
                rules.none { it != existing && it.target == candidate.target }
            val index = rules.indexOf(existing)
            fun change(transform: (MutableList<RouteRule>, Int) -> Unit) {
                onChange { current ->
                    val updated = current.routeRules.toMutableList()
                    val position = updated.indexOfFirst { it.target == original }
                    if (original.isNotEmpty() && position < 0) current else {
                        transform(updated, position)
                        if (RouteRule.valid(updated)) current.copy(routeRules = updated) else current
                    }
                }
                editing = null
            }
            IosSheet(stringResource(if (existing == null) R.string.route_add else R.string.routes_title), { editing = null }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val label = stringResource(R.string.route_target)
                    val invalid = stringResource(R.string.route_invalid)
                    IosTextField(target, { target = it }, label, Modifier.fillMaxWidth().semantics {
                        if (target.isNotEmpty() && !valid) error(invalid)
                    }, label = label)
                    if (target.isNotEmpty() && !valid) IosRow(stringResource(R.string.route_invalid), titleLines = Int.MAX_VALUE)
                    RouteAction.entries.forEach { option ->
                        IosChoiceRow(stringResource(routeLabel(option)), option == action, { action = option })
                    }
                    IosButton(stringResource(R.string.action_save), onClick = {
                        candidate?.let { rule -> change { list, at -> if (at < 0) list.add(rule) else list[at] = rule } }
                    }, enabled = valid, modifier = Modifier.fillMaxWidth())
                    if (index >= 0) {
                        for ((delta, label) in listOf(-1 to R.string.route_up, 1 to R.string.route_down)) {
                            IosButton(stringResource(label), onClick = { change { list, at ->
                                if (at + delta in list.indices) java.util.Collections.swap(list, at, at + delta)
                            } }, enabled = index + delta in rules.indices, modifier = Modifier.fillMaxWidth(), style = IosButtonStyle.Tinted)
                        }
                        IosButton(stringResource(R.string.action_delete), onClick = { change { list, at -> list.removeAt(at) } },
                            modifier = Modifier.fillMaxWidth(), style = IosButtonStyle.Plain)
                    }
                }
            }
        }
    }
}

package app.farvater.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.farvater.R
import app.farvater.core.model.ProxyNode
import app.farvater.data.AppSettings
import app.farvater.data.GalsGroup
import app.farvater.data.GalsGroups
import app.farvater.data.GalsStrategy
import app.farvater.data.ServiceChecks
import app.farvater.data.ServiceKind
import app.farvater.ui.SourceUi
import app.farvater.ui.ios.IosButton
import app.farvater.ui.ios.IosChoiceRow
import app.farvater.ui.ios.IosDivider
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSection
import app.farvater.ui.ios.IosSheet
import app.farvater.ui.ios.IosTextField
import java.util.UUID

@Composable
internal fun GalsSettingsPage(settings: AppSettings, sources: List<SourceUi>, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    var editing by remember { mutableStateOf<GalsGroup?>(null) }
    IosSection(header = stringResource(R.string.gals_active_group), footer = stringResource(R.string.gals_group_hint)) {
        IosChoiceRow(stringResource(R.string.gals_standard), settings.activeGalsGroupId == null,
            { onChange { it.copy(activeGalsGroupId = null) } })
        settings.galsGroups.forEach { group ->
            IosDivider()
            IosChoiceRow(group.name, settings.activeGalsGroupId == group.id, {
                onChange { current ->
                    if (current.galsGroups.any { it.id == group.id }) current.copy(activeGalsGroupId = group.id) else current
                }
            })
        }
    }
    IosSection(header = stringResource(R.string.gals_edit_groups)) {
        settings.galsGroups.forEach { group ->
            IosRow(title = group.name, chevron = true, onClick = { editing = group })
            IosDivider()
        }
        if (settings.galsGroups.size < GalsGroups.MAX_GROUPS)
            IosRow(title = stringResource(R.string.action_add), onClick = { editing = GalsGroup(UUID.randomUUID().toString(), "") })
    }
    editing?.let { original ->
        val exists = settings.galsGroups.any { it.id == original.id }
        val sourceNames = sources.associate { it.id to it.title } + (ProxyNode.MANUAL_SOURCE to stringResource(R.string.interval_manual))
        IosSheet(stringResource(R.string.gals_group), { editing = null }) {
            var draft by remember(original) { mutableStateOf(original) }
            var code by remember(original) { mutableStateOf(original.expectedCode.toString()) }
            val nameLabel = stringResource(R.string.gals_group_name)
            val urlLabel = stringResource(R.string.services_url)
            val statusLabel = stringResource(R.string.services_status)
            Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState())) {
                IosSection(header = nameLabel) {
                    IosTextField(draft.name, { draft = draft.copy(name = it.take(80)) }, nameLabel, label = nameLabel)
                }
                IosSection(header = stringResource(R.string.tab_sources)) {
                    IosRow(title = stringResource(R.string.gals_all_sources), checked = draft.allSources,
                        onCheckedChange = { draft = draft.copy(allSources = it) })
                    if (!draft.allSources) sourceNames.forEach { (id, title) ->
                        IosDivider()
                        IosRow(title = title, checked = id in draft.sourceIds, onCheckedChange = { included ->
                            draft = draft.copy(sourceIds = if (included) draft.sourceIds + id else draft.sourceIds - id)
                        })
                    }
                }
                IosSection(header = stringResource(R.string.gals_metric)) {
                    GalsStrategy.entries.forEach { strategy ->
                        IosChoiceRow(stringResource(if (strategy == GalsStrategy.LATENCY) R.string.gals_latency else R.string.gals_throughput),
                            draft.strategy == strategy, { draft = draft.copy(strategy = strategy) })
                    }
                }
                IosSection(header = stringResource(R.string.gals_target)) {
                    IosChoiceRow(stringResource(R.string.gals_standard), draft.target == null, { draft = draft.copy(target = null) })
                    ServiceKind.entries.sortedBy { it == ServiceKind.CUSTOM }.forEach { target ->
                        IosDivider()
                        IosChoiceRow(serviceName(target), draft.target == target, { draft = draft.copy(target = target) })
                    }
                    if (draft.target == ServiceKind.CUSTOM) {
                        IosTextField(draft.customUrl, { draft = draft.copy(customUrl = it.take(2048)) }, urlLabel, label = urlLabel)
                        IosTextField(code, { code = it.filter(Char::isDigit).take(3) }, statusLabel, label = statusLabel)
                    }
                }
                IosSection(header = stringResource(R.string.gals_transport), footer = stringResource(R.string.gals_transport_hint)) {
                    GalsGroups.transports.forEach { transport ->
                        IosRow(title = transport.uppercase(java.util.Locale.ROOT), checked = transport in draft.transports,
                            onCheckedChange = { included ->
                                draft = draft.copy(transports = if (included) draft.transports + transport else draft.transports - transport)
                            })
                    }
                }
                IosSection(header = stringResource(R.string.gals_interval), footer = stringResource(R.string.gals_period_hint)) {
                    IosRow(title = stringResource(R.string.gals_optimize), titleLines = 2, checked = draft.periodicOptimization,
                        onCheckedChange = { draft = draft.copy(periodicOptimization = it) })
                    IosDivider()
                    GalsGroups.intervals.forEach { seconds ->
                        IosChoiceRow(stringResource(R.string.gals_seconds, seconds), draft.intervalSeconds == seconds,
                            { draft = draft.copy(intervalSeconds = seconds) })
                    }
                }
                val custom = draft.target == ServiceKind.CUSTOM
                val saved = draft.copy(name = draft.name.trim(), sourceIds = draft.sourceIds.intersect(sourceNames.keys),
                    customUrl = if (custom) ServiceChecks.customUrl(draft.customUrl)?.toString().orEmpty() else "",
                    expectedCode = if (custom) code.toIntOrNull() ?: 0 else 200)
                IosButton(stringResource(R.string.action_save), onClick = {
                    onChange { old ->
                        val groups = if (exists) old.galsGroups.map { if (it.id == original.id) saved else it }
                            else (old.galsGroups + saved).distinctBy { it.id }.take(GalsGroups.MAX_GROUPS)
                        old.copy(galsGroups = groups)
                    }
                    editing = null
                }, enabled = GalsGroups.valid(saved), modifier = Modifier.fillMaxWidth().padding(16.dp))
                if (exists) IosButton(stringResource(R.string.action_delete), onClick = {
                    onChange { it.copy(galsGroups = it.galsGroups.filterNot { g -> g.id == original.id },
                        activeGalsGroupId = it.activeGalsGroupId?.takeUnless { id -> id == original.id }) }
                    editing = null
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Spacer(Modifier.height(24.dp).navigationBarsPadding())
            }
        }
    }
}

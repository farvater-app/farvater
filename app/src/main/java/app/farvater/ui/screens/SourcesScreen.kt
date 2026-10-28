package app.farvater.ui.screens

import androidx.compose.ui.platform.LocalContext
import app.farvater.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import app.farvater.core.model.NetMode
import app.farvater.core.model.NetModeChoice
import app.farvater.core.model.SourceMode
import app.farvater.core.parser.Usage
import app.farvater.ui.LocalBottomInset
import app.farvater.ui.SourceUi
import app.farvater.ui.UiState
import app.farvater.ui.formatAgo
import app.farvater.ui.formatBytes
import app.farvater.ui.ios.EdgeHaptics
import app.farvater.ui.ios.AlertAction
import app.farvater.ui.ios.AlertRole
import app.farvater.ui.ios.IosAlert
import app.farvater.ui.ios.IosCircleButton
import app.farvater.ui.ios.IosCompactBar
import app.farvater.ui.ios.glassSource
import app.farvater.ui.ios.rememberGlassBackdrop
import app.farvater.ui.ios.IosDivider
import app.farvater.ui.ios.IosLargeTitle
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSection
import app.farvater.ui.ios.IosSegmented
import app.farvater.ui.ios.IosSpinner
import app.farvater.ui.ios.IosSwitch
import app.farvater.ui.ios.IosTextField
import app.farvater.ui.theme.Ios
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun SourcesScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onAddSubscription: (String) -> Unit,
    onPaste: (String) -> Unit,
    onRemoveUserSource: (String) -> Unit,
    onEnableCommunity: () -> Unit,
    onRefreshSource: (String) -> Unit = {},
    onNetMode: (NetModeChoice) -> Unit = {},
    onSourceMode: (String, SourceMode) -> Unit = { _, _ -> },
    onSourceInterval: (String, Int?) -> Unit = { _, _ -> },
) {
    val c = Ios.colors
    var showAdd by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<SourceUi?>(null) }
    var menuFor by remember { mutableStateOf<SourceUi?>(null) }
    val clipboard = LocalClipboardManager.current
    val community = state.sources.filter { it.community }
    val own = state.sources.filterNot { it.community }
    val list = rememberLazyListState()
    EdgeHaptics(list)
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 70 } }

    val backdrop = rememberGlassBackdrop()
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().glassSource(backdrop),
            contentPadding = PaddingValues(bottom = 20.dp + LocalBottomInset.current),
        ) {
            item(key = "title") {
                val total = state.sources.filter { it.enabled && it.inMode }.sumOf { it.nodeCount }
                IosLargeTitle(stringResource(R.string.tab_sources), subtitle = stringResource(R.string.nodes_active_count, total)) {
                    if (state.refreshing) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { IosSpinner() }
                    } else {
                        IosCircleButton(Icons.Rounded.Refresh, stringResource(R.string.refresh_all), onRefresh)
                    }
                }
            }

            item(key = "add") {
                IosSection {
                    IosRow(
                        title = stringResource(R.string.subscription_add),
                        titleColor = c.tint,
                        icon = Icons.Rounded.Add,
                        iconTint = c.tint,
                        onClick = { showAdd = true },
                    )
                    IosDivider(start = 59.dp)
                    IosRow(
                        title = stringResource(R.string.paste_clipboard),
                        titleColor = c.tint,
                        icon = Icons.Rounded.ContentPaste,
                        iconTint = c.blue,
                        onClick = { onPaste(clipboard.getText()?.text.orEmpty()) },
                    )
                }
            }

            item(key = "mode") {
                val choices = NetModeChoice.entries
                IosSection(header = stringResource(R.string.network_mode_title), footer = modeFooter(state.settings.netMode, state.detectedMode, state.detecting)) {
                    IosSegmented(
                        options = listOf(stringResource(R.string.automatic), stringResource(R.string.mode_white_short), stringResource(R.string.mode_black_short)),
                        selected = choices.indexOf(state.settings.netMode),
                        onSelect = { onNetMode(choices[it]) },
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (own.isNotEmpty()) {
                item(key = "own") {
                    IosSection(header = stringResource(R.string.my_subscriptions)) {
                        own.forEachIndexed { i, source ->
                            if (i > 0) IosDivider(start = 59.dp)
                            val alpha by animateFloatAsState(if (source.inMode) 1f else 0.55f, tween(220), label = "dim")
                            IosRow(
                                title = source.title,
                                subtitle = statusText(source),
                                subtitleColor = statusColor(source),
                                subtitleLines = 5,
                                icon = Icons.Rounded.Link,
                                iconTint = c.indigo,
                                modifier = Modifier.graphicsLayer { this.alpha = alpha },
                                chevron = !source.loading,
                                onClick = { menuFor = source },
                                trailing = { if (source.loading) IosSpinner() },
                            )
                            source.announce?.let { AnnounceRow(it, alpha) }
                        }
                    }
                }
            }

            item(key = "community") {
                if (!state.settings.communityEnabled) {
                    IosSection(
                        header = stringResource(R.string.public_subscriptions),
                        footer = stringResource(R.string.public_warning),
                    ) {
                        IosRow(
                            title = stringResource(R.string.public_enable),
                            titleColor = c.tint,
                            icon = Icons.Rounded.Public,
                            iconTint = c.tint,
                            onClick = onEnableCommunity,
                        )
                    }
                } else {
                    IosSection(
                        header = stringResource(R.string.public_subscriptions),
                        footer = stringResource(R.string.public_ownership),
                    ) {
                        community.forEachIndexed { i, source ->
                            if (i > 0) IosDivider(start = 59.dp)
                            CommunityRow(source, onToggle) { menuFor = source }
                        }
                    }
                }
            }
        }
        IosCompactBar(stringResource(R.string.tab_sources), visible = collapsed, backdrop = backdrop)
    }

    menuFor?.let { opened ->
        val source = state.sources.firstOrNull { it.id == opened.id } ?: opened
        SourceSheet(
            source = source,
            status = statusText(source),
            onMode = { onSourceMode(source.id, it) },
            onInterval = { onSourceInterval(source.id, it) },
            onRefresh = { onRefreshSource(source.id) },
            onRemove = {
                removing = source
                menuFor = null
            },
            onClose = { menuFor = null },
        )
    }

    if (showAdd) {
        var url by remember { mutableStateOf("") }
        IosAlert(
            title = stringResource(R.string.subscription_add),
            message = stringResource(R.string.subscription_paste_hint),
            onDismiss = { showAdd = false },
            content = {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    IosTextField(url, { url = it }, "https://…")
                }
            },
            actions = listOf(
                AlertAction(stringResource(R.string.action_cancel), AlertRole.Default) { showAdd = false },
                AlertAction(stringResource(R.string.action_add), AlertRole.Preferred) {
                    if (url.isNotBlank()) onAddSubscription(url)
                    showAdd = false
                },
            ),
        )
    }

    removing?.let { source ->
        IosAlert(
            title = stringResource(R.string.subscription_delete_question),
            message = source.homepage,
            onDismiss = { removing = null },
            actions = listOf(
                AlertAction(stringResource(R.string.action_cancel), AlertRole.Cancel) { removing = null },
                AlertAction(stringResource(R.string.action_delete), AlertRole.Destructive) {
                    onRemoveUserSource(source.id)
                    removing = null
                },
            ),
        )
    }
}

@Composable
private fun CommunityRow(source: SourceUi, onToggle: (String, Boolean) -> Unit, onOpen: () -> Unit) {
    val c = Ios.colors
    val alpha by animateFloatAsState(if (source.enabled && source.inMode) 1f else 0.55f, tween(220), label = "dim")
    IosRow(
        title = source.title,
        subtitle = "${source.author} · ${source.license}\n" + statusText(source),
        subtitleColor = if (source.error != null && source.nodeCount == 0 && source.enabled) c.red else null,
        subtitleLines = 4,
        icon = Icons.Rounded.Public,
        iconTint = if (source.enabled) c.tint else c.gray,
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
        onClick = onOpen,
        trailing = {
            if (source.loading) {
                Box(Modifier.size(51.dp, 31.dp), contentAlignment = Alignment.Center) { IosSpinner() }
            } else {
                IosSwitch(checked = source.enabled, onCheckedChange = { onToggle(source.id, it) })
            }
        },
    )
    if (source.enabled) source.announce?.let { AnnounceRow(it, alpha) }
}

@Composable
private fun AnnounceRow(text: String, alpha: Float) {
    IosDivider(start = 59.dp)
    IosRow(
        title = stringResource(R.string.subscription_announcement),
        subtitle = text,
        subtitleLines = 8,
        icon = Icons.Rounded.Info,
        iconTint = Ios.colors.gray,
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
    )
}

@Composable
private fun modeFooter(choice: NetModeChoice, detected: NetMode?, detecting: Boolean): String = when (choice) {
    NetModeChoice.WHITE -> WhiteInfo.replaceFirstChar { it.uppercase() }
    NetModeChoice.BLACK -> BlackInfo.replaceFirstChar { it.uppercase() }
    NetModeChoice.AUTO -> when {
        detecting && detected == null -> stringResource(R.string.network_detecting)
        detected == NetMode.WHITE -> stringResource(R.string.network_detected, WhiteInfo)
        detected == NetMode.BLACK -> stringResource(R.string.network_detected, BlackInfo)
        else -> stringResource(R.string.network_unknown)
    }
}

private val WhiteInfo: String
    @Composable get() = stringResource(R.string.network_white_info)
private val BlackInfo: String
    @Composable get() = stringResource(R.string.network_black_info)

@Composable
private fun statusText(source: SourceUi): String = stringResource(R.string.source_status_header, modeShort(source.mode), intervalLabel(source)) + when {
    source.loading -> stringResource(R.string.source_refreshing)
    !source.enabled -> stringResource(R.string.source_off)
    !source.inMode -> if (source.mode == SourceMode.BLACK) stringResource(R.string.source_wait_black) else stringResource(R.string.source_wait_white)
    source.error != null && source.nodeCount > 0 -> stringResource(R.string.source_cached, nodeCountLabel(source.nodeCount), source.error.orEmpty())
    source.error != null -> source.error.replaceFirstChar { it.uppercase() }
    source.updatedAt > 0 -> stringResource(R.string.source_updated, nodeCountLabel(source.nodeCount), formatAgo(source.updatedAt))
    else -> formatAgo(0).replaceFirstChar { it.uppercase() }
} + (source.hidden?.let { stringResource(R.string.source_hidden, it) } ?: "") + (source.usage?.let { "\n" + usageText(it) } ?: "")

@Composable
private fun usageText(usage: Usage): String {
    val traffic = stringResource(R.string.usage_of, formatBytes(usage.used), if (usage.total > 0) formatBytes(usage.total) else "∞")
    if (usage.expire <= 0) return stringResource(R.string.usage_traffic, traffic)
    val date = SimpleDateFormat("dd.MM.yyyy", androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(Date(usage.expire * 1000))
    return stringResource(R.string.usage_traffic, traffic) + " · " + if (usage.expire * 1000 < System.currentTimeMillis()) stringResource(R.string.usage_expired, date) else stringResource(R.string.usage_until, date)
}

@Composable
private fun statusColor(source: SourceUi): Color? {
    val c = Ios.colors
    return when {
        source.error != null && source.nodeCount == 0 -> c.red
        else -> null
    }
}

@Composable
private fun nodeCountLabel(count: Int): String =
    androidx.compose.ui.res.pluralStringResource(R.plurals.nodes_count, count, count)

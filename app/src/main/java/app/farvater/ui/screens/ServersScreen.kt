package app.farvater.ui.screens

import app.farvater.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.farvater.core.model.NetMode
import app.farvater.core.model.ProxyNode
import app.farvater.engine.TestResult
import app.farvater.ui.LocalBottomInset
import app.farvater.ui.UiState
import app.farvater.ui.components.DelayLabel
import app.farvater.ui.components.NodeAvatar
import app.farvater.ui.components.nodeSummary
import app.farvater.ui.ios.EdgeHaptics
import app.farvater.ui.ios.IosActionPanel
import app.farvater.ui.ios.IosButton
import app.farvater.ui.ios.IosButtonStyle
import app.farvater.ui.ios.IosCircleButton
import app.farvater.ui.ios.IosCompactBar
import app.farvater.ui.ios.glassSource
import app.farvater.ui.ios.rememberGlassBackdrop
import app.farvater.ui.ios.IosDivider
import app.farvater.ui.ios.IosLargeTitle
import app.farvater.ui.ios.IosProgressBar
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSearchField
import app.farvater.ui.ios.IosSection
import app.farvater.ui.ios.IosSegmented
import app.farvater.ui.ios.IosSpinner
import app.farvater.ui.ios.SectionCorner
import app.farvater.ui.splitFlag
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType
import app.farvater.ui.theme.NothingLabel

private val Filters: List<String>
    @Composable get() = listOf(stringResource(R.string.filter_all), stringResource(R.string.filter_alive), "Reality", "XHTTP")

@Suppress("DEPRECATION")
@Composable
fun ServersScreen(
    state: UiState,
    onSelect: (ProxyNode) -> Unit,
    onTestAll: () -> Unit,
    onCancelTest: () -> Unit,
    onDeleteManual: (ProxyNode) -> Unit,
    onCopied: () -> Unit,
    onDuplicates: (Boolean) -> Unit = {},
    onTestNode: (ProxyNode) -> Unit = {},
    onToggleGroup: (String) -> Unit = {},
) {
    val c = Ios.colors
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<ProxyNode?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_node)
    val list = rememberLazyListState()
    EdgeHaptics(list)
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 70 } }

    val visible = remember(state.nodes, state.results, filter, state.settings.hideDead, query) {
        val needle = query.trim().lowercase()
        state.nodes.filter { node ->
            val r = state.results[node.id]
            val passesDead = !state.settings.hideDead || r == null || r.alive
            val passesFilter = when (filter) {
                1 -> r?.alive == true
                2 -> node.security == "reality"
                3 -> node.transport == "xhttp"
                else -> true
            }
            val passesQuery = needle.isEmpty() || node.name.lowercase().contains(needle) ||
                node.protocol.title.lowercase().contains(needle) || node.address.contains(needle)
            passesDead && passesFilter && passesQuery
        }
    }
    val alive = remember(state.nodes, state.results) { state.nodes.count { state.results[it.id]?.alive == true } }
    val groups = remember(visible, state.sources, state.dead) {
        val order = (state.sources.map { it.id } + ProxyNode.MANUAL_SOURCE).withIndex().associate { it.value to it.index }
        val (dead, rest) = visible.partition { it.id in state.dead }
        val bySource = rest.groupBy { it.sourceId }.entries
            .sortedBy { order[it.key] ?: Int.MAX_VALUE }
            .map { it.key to it.value }
        if (dead.isEmpty()) bySource else bySource + (DeadGroup to dead)
    }
    val searching = query.isNotBlank()

    val backdrop = rememberGlassBackdrop()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().glassSource(backdrop),
            contentPadding = PaddingValues(bottom = 20.dp + LocalBottomInset.current),
        ) {
            item(key = "title") {
                val modeNote = when (state.netMode) {
                    NetMode.WHITE -> stringResource(R.string.mode_whitelist_note)
                    NetMode.BLACK -> stringResource(R.string.mode_blocklist_note)
                    null -> ""
                }
                IosLargeTitle(
                    stringResource(R.string.tab_nodes),
                    subtitle = (
                        if (state.results.isEmpty()) stringResource(R.string.nodes_untested, state.nodes.size)
                        else stringResource(R.string.nodes_tested, state.nodes.size, alive)
                        ) + modeNote + (if (state.hiddenInsecure > 0) stringResource(R.string.hidden_insecure, state.hiddenInsecure) else "") +
                        if (state.settings.dropDuplicates && state.duplicates > 0) stringResource(R.string.hidden_duplicates, state.duplicates) else "",
                ) {
                    if (state.progress != null) {
                        IosCircleButton(Icons.Rounded.Stop, stringResource(R.string.stop_testing), onCancelTest, tint = c.red)
                    } else {
                        IosCircleButton(Icons.Rounded.Speed, stringResource(R.string.test_all_nodes), onTestAll)
                    }
                }
            }
            item(key = "search") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    IosSearchField(query, { query = it }, stringResource(R.string.node_search_hint))
                    Spacer(Modifier.height(10.dp))
                    IosSegmented(Filters, filter, { filter = it })
                }
            }
            item(key = "progress") {
                val p = state.progress
                AnimatedVisibility(
                    visible = p != null,
                    enter = fadeIn() + expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    if (p != null) {
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
                            Text(
                                stringResource(R.string.checked_alive_progress, p.done, p.total, p.alive),
                                style = IosType.footnote,
                                color = c.secondaryLabel,
                            )
                            Spacer(Modifier.height(6.dp))
                            IosProgressBar(if (p.total == 0) 0f else p.done.toFloat() / p.total)
                        }
                    }
                }
            }
            item(key = "gap") { Spacer(Modifier.height(4.dp)) }

            if (state.duplicates > 0 && !state.settings.dropDuplicates && !state.settings.duplicatesAsked) {
                item(key = "duplicates") {
                    IosSection(footer = stringResource(R.string.duplicates_settings_hint)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.duplicates_found, state.duplicates), style = IosType.headline, color = c.label)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.duplicates_explanation),
                                style = IosType.subheadline,
                                color = c.secondaryLabel,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                IosButton(stringResource(R.string.action_remove), onClick = { onDuplicates(true) }, modifier = Modifier.weight(1f))
                                IosButton(
                                    stringResource(R.string.action_keep),
                                    onClick = { onDuplicates(false) },
                                    style = IosButtonStyle.Tinted,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            if (visible.isEmpty()) {
                item(key = "empty") { EmptyState(state.nodes.isEmpty()) }
            } else {
                groups.forEach { (sourceId, nodes) ->
                    val isDead = sourceId == DeadGroup
                    val collapsed = !searching && if (isDead) DeadGroupOpen !in state.collapsedGroups else sourceId in state.collapsedGroups
                    item(key = "group:$sourceId", contentType = "group") {
                        GroupHeader(
                            title = if (isDead) stringResource(R.string.nodes_dead) else state.sourceTitle(sourceId, stringResource(R.string.interval_manual)),
                            count = nodes.size,
                            alive = nodes.count { state.results[it.id]?.alive == true },
                            collapsed = collapsed,
                            onClick = { onToggleGroup(if (isDead) DeadGroupOpen else sourceId) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (!collapsed) {
                        itemsIndexed(nodes, key = { _, n -> n.id }, contentType = { _, _ -> "node" }) { i, node ->
                            val shape = when {
                                nodes.size == 1 -> RoundedCornerShape(SectionCorner)
                                i == 0 -> RoundedCornerShape(topStart = SectionCorner, topEnd = SectionCorner)
                                i == nodes.lastIndex -> RoundedCornerShape(bottomStart = SectionCorner, bottomEnd = SectionCorner)
                                else -> RoundedCornerShape(0.dp)
                            }
                            Column(
                                Modifier
                                    .animateItem()
                                    .padding(horizontal = 16.dp)
                                    .clip(shape)
                                    .background(c.cell),
                            ) {
                                NodeRow(
                                    node = node,
                                    result = state.results[node.id],
                                    selected = node.id == state.selectedId,
                                    checking = node.id in state.checking,
                                    onClick = { onSelect(node) },
                                    onLongClick = { menuFor = node },
                                )
                                if (i != nodes.lastIndex) IosDivider(start = 68.dp)
                            }
                        }
                    }
                }
            }
        }
        IosCompactBar(stringResource(R.string.tab_nodes), visible = collapsed, backdrop = backdrop)
    }

    menuFor?.let { node ->
        IosActionPanel(
            title = splitFlag(node.name).second,
            subtitle = "${node.protocol.title}, ${node.address}:${node.port} · ${state.sourceTitle(node.sourceId, stringResource(R.string.interval_manual))}",
            onDismiss = { menuFor = null },
        ) { close ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosButton(
                    stringResource(R.string.action_test),
                    onClick = { close { onTestNode(node) } },
                    icon = Icons.Rounded.Bolt,
                    enabled = node.id !in state.checking,
                    modifier = Modifier.fillMaxWidth(),
                )
                IosButton(
                    stringResource(R.string.action_share),
                    onClick = {
                        close {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, node.raw)
                            runCatching { context.startActivity(Intent.createChooser(send, shareTitle)) }
                        }
                    },
                    style = IosButtonStyle.Tinted,
                    icon = Icons.Rounded.Share,
                    modifier = Modifier.fillMaxWidth(),
                )
                IosButton(
                    stringResource(R.string.action_copy),
                    onClick = {
                        close {
                            clipboard.setText(AnnotatedString(node.raw))
                            onCopied()
                        }
                    },
                    style = IosButtonStyle.Plain,
                    icon = Icons.Rounded.ContentCopy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (node.sourceId == ProxyNode.MANUAL_SOURCE) {
                    IosButton(
                        stringResource(R.string.action_delete),
                        onClick = { close { onDeleteManual(node) } },
                        style = IosButtonStyle.Plain,
                        color = Ios.colors.red,
                        icon = Icons.Rounded.Delete,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private const val DeadGroup = "dead"
private const val DeadGroupOpen = "dead:open"

@Composable
private fun GroupHeader(title: String, count: Int, alive: Int, collapsed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val turn by animateFloatAsState(if (collapsed) -90f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "chevron")
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 32.dp, end = 24.dp, top = 18.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val material = Ios.material && !Ios.nothing
        Text(
            if (material) title else title.uppercase(),
            style = when {
                Ios.nothing -> NothingLabel
                material -> MaterialTheme.typography.titleSmall
                else -> IosType.footnote
            },
            color = if (material) c.tint else c.secondaryLabel,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(if (alive > 0) stringResource(R.string.count_of, alive, count) else "$count", style = IosType.footnote, color = c.secondaryLabel)
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (collapsed) stringResource(R.string.action_expand) else stringResource(R.string.action_collapse),
            tint = c.secondaryLabel,
            modifier = Modifier.size(18.dp).rotate(turn),
        )
    }
}

@Composable
private fun NodeRow(
    node: ProxyNode,
    result: TestResult?,
    selected: Boolean,
    checking: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = Ios.colors
    val selectedLabel = stringResource(R.string.node_selected)
    val (flag, title) = remember(node.name) { splitFlag(node.name) }
    val subtitle = (if (node.isInsecure) stringResource(R.string.node_unencrypted_prefix) else "") + nodeSummary(node)
    IosRow(
        title = title,
        subtitle = subtitle,
        subtitleColor = if (node.isInsecure) c.red else null,
        subtitleLines = 1,
        leading = { NodeAvatar(flag, node) },
        onClick = onClick,
        onLongClick = onLongClick,
        trailing = {
            if (checking) IosSpinner(size = 16.dp) else DelayLabel(result)
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f, stiffness = 600f), initialScale = 0.4f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    if (Ios.nothing) {
                        Box(Modifier.size(8.dp).background(c.red, CircleShape).semantics { contentDescription = selectedLabel })
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = selectedLabel, tint = c.tint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun EmptyState(noNodes: Boolean) {
    val c = Ios.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Dns, contentDescription = null, tint = c.tertiaryLabel, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(if (noNodes) stringResource(R.string.nodes_empty) else stringResource(R.string.search_empty), style = IosType.title3, color = c.label)
        Spacer(Modifier.height(4.dp))
        Text(
            if (noNodes) stringResource(R.string.nodes_empty_hint) else stringResource(R.string.search_empty_hint),
            style = IosType.subheadline,
            color = c.secondaryLabel,
            textAlign = TextAlign.Center,
        )
    }
}

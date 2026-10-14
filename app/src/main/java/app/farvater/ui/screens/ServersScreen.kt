package app.farvater.ui.screens

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

private val Filters = listOf("Все", "Живые", "Reality", "XHTTP")

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
    val list = rememberLazyListState()
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
    val groups = remember(visible, state.sources) {
        val order = (state.sources.map { it.id } + ProxyNode.MANUAL_SOURCE).withIndex().associate { it.value to it.index }
        visible.groupBy { it.sourceId }.entries
            .sortedBy { order[it.key] ?: Int.MAX_VALUE }
            .map { it.key to it.value }
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
                    NetMode.WHITE -> ", режим БС"
                    NetMode.BLACK -> ", режим ЧС"
                    null -> ""
                }
                IosLargeTitle(
                    "Узлы",
                    subtitle = (
                        if (state.results.isEmpty()) "${state.nodes.size} в списке, не проверены"
                        else "${state.nodes.size} в списке, отвечают $alive"
                        ) + modeNote + (if (state.hiddenInsecure > 0) ", скрыто небезопасных ${state.hiddenInsecure}" else "") +
                        if (state.settings.dropDuplicates && state.duplicates > 0) ", скрыто дублей ${state.duplicates}" else "",
                ) {
                    if (state.progress != null) {
                        IosCircleButton(Icons.Rounded.Stop, "Остановить проверку", onCancelTest, tint = c.red)
                    } else {
                        IosCircleButton(Icons.Rounded.Speed, "Проверить все узлы", onTestAll)
                    }
                }
            }
            item(key = "search") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    IosSearchField(query, { query = it }, "Страна, город или протокол")
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
                                "Проверено ${p.done} из ${p.total}, отвечают ${p.alive}",
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
                    IosSection(footer = "Передумать можно в «Настройки → Проверка узлов».") {
                        Column(Modifier.padding(16.dp)) {
                            Text("Найдено дублей: ${state.duplicates}", style = IosType.headline, color = c.label)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Это узлы с теми же адресом, портом и протоколом, что и у других, но с другими " +
                                    "настройками, например SNI или отпечатком TLS. Убрать их? Из каждой группы " +
                                    "останется один узел, проверка пойдёт быстрее, но скрытые узлы не будут проверяться.",
                                style = IosType.subheadline,
                                color = c.secondaryLabel,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                IosButton("Убрать", onClick = { onDuplicates(true) }, modifier = Modifier.weight(1f))
                                IosButton(
                                    "Оставить",
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
                    val collapsed = !searching && sourceId in state.collapsedGroups
                    item(key = "group:$sourceId", contentType = "group") {
                        GroupHeader(
                            title = state.sourceTitle(sourceId),
                            count = nodes.size,
                            alive = nodes.count { state.results[it.id]?.alive == true },
                            collapsed = collapsed,
                            onClick = { onToggleGroup(sourceId) },
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
        IosCompactBar("Узлы", visible = collapsed, backdrop = backdrop)
    }

    menuFor?.let { node ->
        IosActionPanel(
            title = splitFlag(node.name).second,
            subtitle = "${node.protocol.title}, ${node.address}:${node.port} · ${state.sourceTitle(node.sourceId)}",
            onDismiss = { menuFor = null },
        ) { close ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IosButton(
                    "Проверить",
                    onClick = { close { onTestNode(node) } },
                    icon = Icons.Rounded.Bolt,
                    enabled = node.id !in state.checking,
                    modifier = Modifier.weight(1f),
                )
                IosButton(
                    "Поделиться",
                    onClick = {
                        close {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, node.raw)
                            runCatching { context.startActivity(Intent.createChooser(send, "Поделиться узлом")) }
                        }
                    },
                    style = IosButtonStyle.Tinted,
                    icon = Icons.Rounded.Share,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IosButton(
                    "Скопировать",
                    onClick = {
                        close {
                            clipboard.setText(AnnotatedString(node.raw))
                            onCopied()
                        }
                    },
                    style = IosButtonStyle.Plain,
                    icon = Icons.Rounded.ContentCopy,
                    modifier = Modifier.weight(1f),
                )
                if (node.sourceId == ProxyNode.MANUAL_SOURCE) {
                    IosButton(
                        "Удалить",
                        onClick = { close { onDeleteManual(node) } },
                        style = IosButtonStyle.Plain,
                        color = c.red,
                        icon = Icons.Rounded.Delete,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

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
        Text(if (alive > 0) "$alive из $count" else "$count", style = IosType.footnote, color = c.secondaryLabel)
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (collapsed) "Развернуть" else "Свернуть",
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
    val (flag, title) = remember(node.name) { splitFlag(node.name) }
    val subtitle = remember(node) { (if (node.isInsecure) "Без шифрования · " else "") + nodeSummary(node) }
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
                        Box(Modifier.size(8.dp).background(c.red, CircleShape).semantics { contentDescription = "Выбран" })
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = "Выбран", tint = c.tint, modifier = Modifier.size(20.dp))
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
        Text(if (noNodes) "Узлов пока нет" else "Ничего не нашлось", style = IosType.title3, color = c.label)
        Spacer(Modifier.height(4.dp))
        Text(
            if (noNodes) "Включите источники на вкладке «Источники» или вставьте свою подписку" else "Измените запрос или фильтр",
            style = IosType.subheadline,
            color = c.secondaryLabel,
            textAlign = TextAlign.Center,
        )
    }
}

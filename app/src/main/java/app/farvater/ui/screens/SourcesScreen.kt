package app.farvater.ui.screens

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
                IosLargeTitle("Источники", subtitle = "Узлов в работе: $total") {
                    if (state.refreshing) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { IosSpinner() }
                    } else {
                        IosCircleButton(Icons.Rounded.Refresh, "Обновить все", onRefresh)
                    }
                }
            }

            item(key = "add") {
                IosSection {
                    IosRow(
                        title = "Добавить подписку",
                        titleColor = c.tint,
                        icon = Icons.Rounded.Add,
                        iconTint = c.tint,
                        onClick = { showAdd = true },
                    )
                    IosDivider(start = 59.dp)
                    IosRow(
                        title = "Вставить из буфера",
                        titleColor = c.tint,
                        icon = Icons.Rounded.ContentPaste,
                        iconTint = c.blue,
                        onClick = { onPaste(clipboard.getText()?.text.orEmpty()) },
                    )
                }
            }

            item(key = "mode") {
                val choices = NetModeChoice.entries
                IosSection(header = "Режим сети", footer = modeFooter(state.settings.netMode, state.detectedMode, state.detecting)) {
                    IosSegmented(
                        options = listOf("Авто", "БС", "ЧС"),
                        selected = choices.indexOf(state.settings.netMode),
                        onSelect = { onNetMode(choices[it]) },
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (own.isNotEmpty()) {
                item(key = "own") {
                    IosSection(header = "Мои подписки") {
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
                            source.announce?.let { text ->
                                IosDivider(start = 59.dp)
                                IosRow(
                                    title = "Сообщение подписки",
                                    subtitle = text,
                                    subtitleLines = 8,
                                    icon = Icons.Rounded.Info,
                                    iconTint = c.gray,
                                    modifier = Modifier.graphicsLayer { this.alpha = alpha },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "community") {
                if (!state.settings.communityEnabled) {
                    IosSection(
                        header = "Публичные подписки",
                        footer = "Это бесплатные чужие серверы. Удобно при белых списках, но для паролей и банков они не годятся.",
                    ) {
                        IosRow(
                            title = "Включить публичные подписки",
                            titleColor = c.tint,
                            icon = Icons.Rounded.Public,
                            iconTint = c.tint,
                            onClick = onEnableCommunity,
                        )
                    }
                } else {
                    IosSection(
                        header = "Публичные подписки",
                        footer = "Фарватер хранит только ссылки. Серверы принадлежат авторам, поддержите их на страницах проектов.",
                    ) {
                        community.forEachIndexed { i, source ->
                            if (i > 0) IosDivider(start = 59.dp)
                            CommunityRow(source, onToggle) { menuFor = source }
                        }
                    }
                }
            }
        }
        IosCompactBar("Источники", visible = collapsed, backdrop = backdrop)
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
            title = "Добавить подписку",
            message = "Вставьте ссылку, которую дал ваш провайдер или автор подписки.",
            onDismiss = { showAdd = false },
            content = { IosTextField(url, { url = it }, "https://…") },
            actions = listOf(
                AlertAction("Отмена", AlertRole.Default) { showAdd = false },
                AlertAction("Добавить", AlertRole.Preferred) {
                    if (url.isNotBlank()) onAddSubscription(url)
                    showAdd = false
                },
            ),
        )
    }

    removing?.let { source ->
        IosAlert(
            title = "Удалить подписку?",
            message = source.homepage,
            onDismiss = { removing = null },
            actions = listOf(
                AlertAction("Отмена", AlertRole.Cancel) { removing = null },
                AlertAction("Удалить", AlertRole.Destructive) {
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
}

private fun modeFooter(choice: NetModeChoice, detected: NetMode?, detecting: Boolean): String = when (choice) {
    NetModeChoice.WHITE -> WhiteInfo.replaceFirstChar { it.uppercase() }
    NetModeChoice.BLACK -> BlackInfo.replaceFirstChar { it.uppercase() }
    NetModeChoice.AUTO -> when {
        detecting && detected == null -> "Определяю, какие сейчас ограничения…"
        detected == NetMode.WHITE -> "Сейчас $WhiteInfo"
        detected == NetMode.BLACK -> "Сейчас $BlackInfo"
        else -> "Режим пока не определён, работают все подписки."
    }
}

private const val WhiteInfo = "белые списки: мобильный интернет ограничен, открываются только разрешённые сайты, " +
    "обычные серверы недоступны. Работают подписки БС."
private const val BlackInfo = "обычные блокировки: закрыты отдельные сайты, хватает обычных серверов. " +
    "Работают подписки ЧС, а БС включатся только при белых списках, чтобы не перегружать их серверы."

private fun statusText(source: SourceUi): String = "${modeShort(source.mode)} · обновление ${intervalLabel(source)}\n" + when {
    source.loading -> "Обновляю…"
    !source.enabled -> "Выключена"
    !source.inMode -> if (source.mode == SourceMode.BLACK) "Включится при обычных блокировках" else "Включится при белых списках"
    source.error != null && source.nodeCount > 0 -> "Из кэша: ${source.nodeCount} узлов, ${source.error}"
    source.error != null -> source.error.replaceFirstChar { it.uppercase() }
    source.updatedAt > 0 -> "${source.nodeCount} узлов, ${formatAgo(source.updatedAt).removePrefix("обновлён ")}"
    else -> formatAgo(0).replaceFirstChar { it.uppercase() }
} + (source.hidden?.let { "\nСкрыто: $it" } ?: "") + (source.usage?.let { "\n" + usageText(it) } ?: "")

private fun usageText(usage: Usage): String {
    val traffic = formatBytes(usage.used) + " из " + if (usage.total > 0) formatBytes(usage.total) else "∞"
    if (usage.expire <= 0) return "Трафик: $traffic"
    val date = SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("ru")).format(Date(usage.expire * 1000))
    return "Трафик: $traffic · " + if (usage.expire * 1000 < System.currentTimeMillis()) "истекла $date" else "до $date"
}

@Composable
private fun statusColor(source: SourceUi): Color? {
    val c = Ios.colors
    return when {
        source.error != null && source.nodeCount == 0 -> c.red
        else -> null
    }
}

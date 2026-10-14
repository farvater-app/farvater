package app.farvater.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import app.farvater.data.UiStyle
import app.farvater.ui.LocalBottomInset
import app.farvater.ui.UiState
import app.farvater.ui.components.Badge
import app.farvater.ui.components.ConnectButton
import app.farvater.ui.components.ConnectMode
import app.farvater.ui.components.DelayLabel
import app.farvater.ui.components.DotBars
import app.farvater.ui.components.DotConnectButton
import app.farvater.ui.components.NodeAvatar
import app.farvater.ui.components.Sparkline
import app.farvater.ui.components.SumieHero
import app.farvater.ui.components.nodeSummary
import app.farvater.ui.formatBytes
import app.farvater.ui.formatDuration
import app.farvater.ui.formatSpeed
import app.farvater.ui.ios.IosButton
import app.farvater.ui.ios.IosButtonStyle
import app.farvater.ui.ios.IosCompactBar
import app.farvater.ui.ios.glassSource
import app.farvater.ui.ios.rememberGlassBackdrop
import app.farvater.ui.ios.IosDivider
import app.farvater.ui.ios.IosLargeTitle
import app.farvater.ui.ios.IosProgressBar
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSection
import app.farvater.ui.splitFlag
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType
import app.farvater.ui.theme.LocalUiStyle
import app.farvater.ui.theme.NothingLabel
import app.farvater.ui.theme.Numeric
import app.farvater.vpn.Traffic
import app.farvater.vpn.VpnState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: UiState,
    vpn: VpnState,
    traffic: Traffic,
    history: List<Long>,
    onToggle: () -> Unit,
    onFindWorking: () -> Unit,
    onCancelTest: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenUpdate: () -> Unit = {},
    stalled: Boolean = false,
) {
    val c = Ios.colors
    val mode = when {
        state.progress != null || state.pathfinding != null -> ConnectMode.Searching
        vpn is VpnState.Connecting -> ConnectMode.Connecting
        vpn is VpnState.Connected -> ConnectMode.Connected
        vpn is VpnState.Failed -> ConnectMode.Error
        else -> ConnectMode.Idle
    }
    val sumie = LocalUiStyle.current == UiStyle.SUMIE
    val nothing = Ios.nothing
    val scroll = rememberScrollState()
    val collapsed by remember { derivedStateOf { scroll.value > 70 } }

    val glow by animateColorAsState(
        if (mode == ConnectMode.Idle) c.tint.copy(alpha = 0.06f) else statusColor(mode).copy(alpha = 0.2f),
        animationSpec = tween(700),
        label = "glow",
    )

    val backdrop = rememberGlassBackdrop()
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop)
                .verticalScroll(scroll)
                .drawBehind {
                    if (!sumie && !nothing) drawRect(
                        Brush.radialGradient(
                            listOf(glow, Color.Transparent),
                            center = Offset(size.width / 2, 190.dp.toPx()),
                            radius = size.width * 0.8f,
                        ),
                    )
                },
        ) {
            IosLargeTitle("Фарватер") { Badge(shortStatus(mode), statusColor(mode), active = mode != ConnectMode.Idle) }

            state.update?.let { info ->
                IosSection {
                    IosRow(
                        title = if (info.critical) "Важное обновление ${info.versionName}" else "Доступен Фарватер ${info.versionName}",
                        subtitle = info.notes.firstOrNull() ?: "Нажмите, чтобы посмотреть, что нового",
                        subtitleLines = 1,
                        icon = Icons.Rounded.SystemUpdate,
                        iconTint = if (info.critical) c.red else c.blue,
                        chevron = true,
                        onClick = onOpenUpdate,
                    )
                }
            }

            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                when {
                    sumie -> SumieHero(mode = mode, onClick = onToggle)
                    nothing -> DotConnectButton(mode = mode, onClick = onToggle, size = 204.dp)
                    else -> ConnectButton(mode = mode, onClick = onToggle, size = 184.dp)
                }
            }

            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(tween(240)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 400f), initialScale = 0.92f)) togetherWith
                        fadeOut(tween(120))
                },
                label = "status",
                modifier = Modifier.fillMaxWidth(),
            ) { m ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (nothing) headline(m).uppercase() else headline(m),
                        style = if (Ios.material) MaterialTheme.typography.headlineSmall else IosType.title2,
                        color = statusColor(m),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (m == ConnectMode.Connected && stalled) "Узел не отвечает. Проверьте интернет или выберите другой узел" else supporting(m, vpn, state, sumie),
                        style = IosType.subheadline,
                        color = c.secondaryLabel,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val progress = state.progress
            AnimatedVisibility(
                visible = progress != null,
                enter = fadeIn() + expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)),
                exit = fadeOut() + shrinkVertically(),
            ) {
                if (progress != null) {
                    IosSection {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Проверено ${progress.done} из ${progress.total}",
                                    style = IosType.subheadline.merge(Numeric),
                                    color = c.label,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "Остановить",
                                    style = IosType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                                    color = c.red,
                                    modifier = Modifier.clickable(onClick = onCancelTest),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            IosProgressBar(if (progress.total == 0) 0f else progress.done.toFloat() / progress.total)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = vpn is VpnState.Connected,
                enter = fadeIn() + expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)),
                exit = fadeOut() + shrinkVertically(),
            ) {
                if (vpn is VpnState.Connected) SessionSection(vpn, traffic, history, state.settings.encryptedDns)
            }

            IosSection(header = "Узел") {
                val node = state.selectedNode
                if (node != null) {
                    val (flag, title) = remember(node.name) { splitFlag(node.name) }
                    val listed = remember(node.id, state.nodes) { state.nodes.any { it.id == node.id } }
                    IosRow(
                        title = title,
                        subtitle = if (listed) nodeSummary(node) else "Сейчас его нет в списке источников, но он остаётся выбранным",
                        leading = { NodeAvatar(flag, node, size = 40.dp) },
                        chevron = true,
                        onClick = onOpenServers,
                        trailing = { DelayLabel(state.results[node.id]) },
                    )
                } else {
                    IosRow(
                        title = "Выберется автоматически",
                        subtitle = "В списке ${state.nodes.size}, нажмите, чтобы выбрать",
                        icon = Icons.Rounded.Radar,
                        iconTint = c.gray,
                        chevron = true,
                        onClick = onOpenServers,
                    )
                }
            }

            AnimatedVisibility(visible = progress == null && state.pathfinding == null, enter = fadeIn(), exit = fadeOut()) {
                IosButton(
                    "Найти рабочий узел",
                    onClick = onFindWorking,
                    style = IosButtonStyle.Tinted,
                    icon = Icons.Rounded.Radar,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp),
                )
            }

            Spacer(Modifier.height(24.dp + LocalBottomInset.current))
        }
        IosCompactBar("Фарватер", visible = collapsed, backdrop = backdrop)
    }
}

@Composable
private fun statusColor(mode: ConnectMode): Color {
    val c = Ios.colors
    return when (mode) {
        ConnectMode.Idle -> c.secondaryLabel
        ConnectMode.Searching, ConnectMode.Connecting -> c.tint
        ConnectMode.Connected -> c.green
        ConnectMode.Error -> c.red
    }
}

private fun shortStatus(mode: ConnectMode): String = when (mode) {
    ConnectMode.Idle -> "Выключен"
    ConnectMode.Searching -> "Поиск"
    ConnectMode.Connecting -> "Подключение"
    ConnectMode.Connected -> "Защищено"
    ConnectMode.Error -> "Ошибка"
}

private fun headline(mode: ConnectMode): String = when (mode) {
    ConnectMode.Idle -> "Не подключено"
    ConnectMode.Searching -> "Ищу рабочий узел"
    ConnectMode.Connecting -> "Подключаюсь"
    ConnectMode.Connected -> "Подключено"
    ConnectMode.Error -> "Не удалось подключиться"
}

private fun supporting(mode: ConnectMode, vpn: VpnState, state: UiState, sumie: Boolean): String = when (mode) {
    ConnectMode.Idle -> if (sumie) "Нажмите на круг, чтобы подключиться" else "Нажмите кнопку, чтобы подключиться"
    ConnectMode.Searching -> state.pathfinding?.let { "Галс: проверяю, идут ли данные через «${splitFlag(it).second}»" }
        ?: state.progress?.let { "Отвечают ${it.alive}" }.orEmpty()
    ConnectMode.Connecting -> (vpn as? VpnState.Connecting)?.node?.let { splitFlag(it.name).second }.orEmpty()
    ConnectMode.Connected -> "Трафик идёт через выбранный узел"
    ConnectMode.Error -> (vpn as? VpnState.Failed)?.message ?: "Попробуйте другой узел"
}

@Composable
private fun SessionSection(vpn: VpnState.Connected, traffic: Traffic, history: List<Long>, encryptedDns: Boolean) {
    val c = Ios.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vpn.since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    IosSection(header = "Сеанс") {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Stat(Icons.Rounded.ArrowDownward, "Загрузка", formatSpeed(traffic.downBps), c.green, Modifier.weight(1f))
            Box(Modifier.width(0.5.dp).height(36.dp).background(c.separator))
            Stat(Icons.Rounded.ArrowUpward, "Отдача", formatSpeed(traffic.upBps), c.blue, Modifier.weight(1f).padding(start = 16.dp))
        }
        if (Ios.nothing) {
            DotBars(history, Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp, vertical = 8.dp))
        } else {
            Sparkline(history, c.green, Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp, vertical = 8.dp))
        }
        IosDivider()
        IosRow(title = "В сети", value = formatDuration(now - vpn.since))
        IosDivider()
        IosRow(title = "За сеанс", value = formatBytes(traffic.upTotal + traffic.downTotal))
        IosDivider()
        IosRow(title = "За сегодня", value = formatBytes(traffic.today))
        IosDivider()
        IosRow(
            title = "Защита",
            value = listOfNotNull(
                vpn.node.securityIssue ?: protectionOf(vpn.node),
                if (encryptedDns) "DNS по HTTPS" else null,
            ).joinToString(", "),
            valueColor = if (vpn.node.isInsecure) Ios.colors.red else Ios.colors.green,
        )
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier) {
    val c = Ios.colors
    Column(modifier) {
        if (Ios.nothing) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = c.label, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label.uppercase(), style = NothingLabel.copy(fontSize = 9.sp), color = c.secondaryLabel)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(), style = IosType.caption2, color = c.secondaryLabel)
        }
        Spacer(Modifier.height(2.dp))
        Text(value, style = IosType.title3.merge(Numeric), color = c.label, maxLines = 1)
    }
}

private fun protectionOf(node: ProxyNode): String = when {
    node.security == "reality" -> "Reality"
    node.security == "tls" || node.protocol == Protocol.HYSTERIA2 ||
        (node.protocol == Protocol.TROJAN && node.security.isBlank()) -> "TLS"
    node.protocol == Protocol.SHADOWSOCKS -> "Shadowsocks"
    node.protocol == Protocol.VMESS -> "VMess"
    else -> "шифрование узла"
}

package app.farvater.ui.screens

import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.rounded.Refresh
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
import app.farvater.core.model.NetMode
import app.farvater.core.model.NodeProfile
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
import app.farvater.ui.components.HarmonyConnect
import app.farvater.ui.components.HyperConnect
import app.farvater.ui.components.NodeAvatar
import app.farvater.ui.components.OneUiConnect
import app.farvater.ui.components.OriginBars
import app.farvater.ui.components.OriginConnect
import app.farvater.ui.components.OriginTile
import app.farvater.ui.components.OxygenConnect
import app.farvater.ui.components.Sparkline
import app.farvater.ui.components.StatusIsland
import app.farvater.ui.components.WitchHero
import androidx.compose.ui.res.stringResource
import app.farvater.R
import app.farvater.ui.components.SumieHero
import app.farvater.ui.components.nodeSummary
import app.farvater.ui.formatBytes
import app.farvater.ui.formatDuration
import app.farvater.ui.formatSpeed
import app.farvater.ui.ios.EdgeHaptics
import app.farvater.ui.ios.IosButton
import app.farvater.ui.ios.IosButtonStyle
import app.farvater.ui.ios.IosCircleButton
import app.farvater.ui.ios.IosCompactBar
import app.farvater.ui.ios.IosSpinner
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
    onRefresh: () -> Unit = {},
    stalled: Boolean = false,
) {
    val c = Ios.colors
    val mode = when {
        state.refining && vpn is VpnState.Connected -> ConnectMode.Connected
        state.refining && vpn is VpnState.Connecting -> ConnectMode.Connecting
        state.gals || state.progress != null || state.pathfinding != null -> ConnectMode.Searching
        state.preflight != null || vpn is VpnState.Connecting -> ConnectMode.Connecting
        vpn is VpnState.Connected -> ConnectMode.Connected
        vpn is VpnState.Failed -> ConnectMode.Error
        else -> ConnectMode.Idle
    }
    val witch = Ios.witch
    val sumie = LocalUiStyle.current == UiStyle.SUMIE
    val nothing = Ios.nothing
    val origin = Ios.origin
    val hyper = Ios.hyper
    val harmony = Ios.harmony
    val oneUi = Ios.oneUi
    val oxygen = Ios.oxygen
    val island = origin || hyper || harmony || oxygen
    val scroll = rememberScrollState()
    EdgeHaptics(scroll)
    val collapsed by remember { derivedStateOf { scroll.value > 70 } }

    val glow by animateColorAsState(
        when {
            mode == ConnectMode.Idle -> c.tint.copy(alpha = if (origin) 0.1f else 0.06f)
            else -> statusColor(mode).copy(alpha = if (origin) 0.26f else 0.2f)
        },
        animationSpec = tween(700),
        label = "glow",
    )
    val connectedNode = remember(vpn) { (vpn as? VpnState.Connected)?.node?.name?.let { splitFlag(it).second } }

    val status: @Composable () -> Unit = {
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
                    headline(m, state).let { if (nothing) it.uppercase() else it },
                    style = if (Ios.material) MaterialTheme.typography.headlineSmall else IosType.title2,
                    color = statusColor(m),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (witch && m == ConnectMode.Idle) stringResource(R.string.witch_connect_hint) else if (m == ConnectMode.Connected && stalled) stringResource(R.string.node_stalled) else supporting(m, vpn, state, sumie),
                    style = IosType.subheadline,
                    color = c.secondaryLabel,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (m == ConnectMode.Connected && stalled) {
                    IosButton(
                        stringResource(R.string.find_working_short),
                        onClick = onFindWorking,
                        style = IosButtonStyle.Plain,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    val backdrop = rememberGlassBackdrop()
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop)
                .verticalScroll(scroll)
                .drawBehind {
                    if (!witch && !sumie && !nothing && !hyper && !harmony && !oneUi && !oxygen) drawRect(
                        Brush.radialGradient(
                            listOf(glow, Color.Transparent),
                            center = Offset(size.width / 2, if (origin) 260.dp.toPx() else 190.dp.toPx()),
                            radius = size.width * 0.8f,
                        ),
                    )
                },
        ) {
            if (island) {
                StatusIsland(
                    mode = mode,
                    node = connectedNode,
                    since = (vpn as? VpnState.Connected)?.since,
                    downBps = traffic.downBps,
                    progress = state.progress?.let { it.done to it.total },
                )
            }
            IosLargeTitle(
                title = if (oneUi) headline(mode, state) else stringResource(R.string.app_name),
                subtitle = if (!oneUi) null else if (mode == ConnectMode.Connected && stalled) stringResource(R.string.node_stalled) else supporting(mode, vpn, state, sumie),
            ) {
                if (!island && !oneUi) {
                    Badge(shortStatus(mode), statusColor(mode), active = mode != ConnectMode.Idle)
                    Spacer(Modifier.width(10.dp))
                }
                if (state.refreshing) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { IosSpinner() }
                } else {
                    IosCircleButton(Icons.Rounded.Refresh, stringResource(R.string.refresh_all), onRefresh)
                }
            }

            state.update?.let { info ->
                IosSection {
                    IosRow(
                        title = if (info.critical) stringResource(R.string.update_critical, info.versionName) else stringResource(R.string.update_available, info.versionName),
                        subtitle = info.notes.firstOrNull() ?: stringResource(R.string.update_view_changes),
                        subtitleLines = 1,
                        icon = Icons.Rounded.SystemUpdate,
                        iconTint = if (info.critical) c.red else c.blue,
                        chevron = true,
                        onClick = onOpenUpdate,
                    )
                }
            }

            if (origin) {
                OriginTile(Modifier.padding(top = 4.dp)) {
                    OriginConnect(mode = mode, onClick = onToggle)
                    status()
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    when {
                        witch -> WitchFeedback { WitchHero(mode = mode, onClick = onToggle) }
                        sumie -> SumieHero(mode = mode, onClick = onToggle)
                        nothing -> DotConnectButton(mode = mode, onClick = onToggle, size = 204.dp)
                        hyper -> HyperConnect(
                            mode = mode,
                            status = tileStatus(mode, state, connectedNode),
                            downBps = traffic.downBps,
                            upBps = traffic.upBps,
                            history = history,
                            onClick = onToggle,
                        )
                        harmony -> HarmonyConnect(
                            mode = mode,
                            status = tileStatus(mode, state, connectedNode),
                            onClick = onToggle,
                        )
                        oneUi -> OneUiConnect(mode = mode, onClick = onToggle)
                        oxygen -> OxygenConnect(mode = mode, onClick = onToggle)
                        else -> ConnectButton(mode = mode, onClick = onToggle, size = 184.dp)
                    }
                }
                Spacer(Modifier.height(if (hyper || harmony) 16.dp else 8.dp))
                if (!oneUi) status()
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
                                    stringResource(R.string.checked_progress, progress.done, progress.total),
                                    style = IosType.subheadline.merge(Numeric),
                                    color = c.label,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    stringResource(R.string.action_stop_search),
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

            IosSection(
                header = when {
                    !state.settings.modeNodes -> stringResource(R.string.node_title)
                    state.netMode == NetMode.WHITE -> stringResource(R.string.node_whitelist)
                    state.netMode == NetMode.BLACK -> stringResource(R.string.node_blocklist)
                    else -> stringResource(R.string.node_title)
                },
            ) {
                val node = state.selectedNode
                if (node != null) {
                    val (flag, title) = remember(node.name) { splitFlag(node.name) }
                    val listed = remember(node.id, state.nodes) { state.nodes.any { it.id == node.id } }
                    IosRow(
                        title = title,
                        subtitle = if (listed) nodeSummary(node) else stringResource(R.string.node_unlisted_selected),
                        leading = { NodeAvatar(flag, node, size = 40.dp) },
                        chevron = true,
                        onClick = onOpenServers,
                        trailing = { DelayLabel(state.results[node.id]) },
                    )
                } else {
                    IosRow(
                        title = stringResource(R.string.node_auto),
                        subtitle = stringResource(R.string.nodes_choose_count, state.nodes.size),
                        icon = Icons.Rounded.Radar,
                        iconTint = c.gray,
                        chevron = true,
                        onClick = onOpenServers,
                    )
                }
            }

            AnimatedVisibility(visible = progress == null && state.pathfinding == null && !state.gals, enter = fadeIn(), exit = fadeOut()) {
                IosButton(
                    stringResource(R.string.find_working_node),
                    onClick = onFindWorking,
                    style = IosButtonStyle.Tinted,
                    icon = Icons.Rounded.Radar,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp),
                )
            }

            Spacer(Modifier.height(24.dp + LocalBottomInset.current))
        }
        IosCompactBar(stringResource(R.string.app_name), visible = collapsed, backdrop = backdrop)
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

@Composable
private fun shortStatus(mode: ConnectMode): String = when (mode) {
    ConnectMode.Idle -> stringResource(R.string.vpn_state_off)
    ConnectMode.Searching -> stringResource(R.string.state_searching)
    ConnectMode.Connecting -> stringResource(R.string.settings_connection)
    ConnectMode.Connected -> stringResource(R.string.state_protected)
    ConnectMode.Error -> stringResource(R.string.state_error)
}

@Composable
private fun tileStatus(mode: ConnectMode, state: UiState, node: String?): String = when (mode) {
    ConnectMode.Idle -> stringResource(R.string.vpn_state_off)
    ConnectMode.Searching -> stringResource(R.string.state_searching)
    ConnectMode.Connecting -> stringResource(R.string.settings_connection)
    ConnectMode.Connected -> node ?: stringResource(R.string.state_protected)
    ConnectMode.Error -> stringResource(R.string.state_error)
}

@Composable
private fun profileLabel(profile: NodeProfile): String = listOfNotNull(
    profile.fingerprint?.replaceFirstChar { it.uppercase() },
    "mux".takeIf { profile.mux },
    stringResource(R.string.profile_fragment).takeIf { profile.fragment },
).joinToString(" + ").ifEmpty { stringResource(R.string.profile_default) }.let { stringResource(R.string.profile_label, it) }

@Composable
private fun headline(mode: ConnectMode, state: UiState): String = when (mode) {
    ConnectMode.Idle -> stringResource(R.string.state_disconnected)
    ConnectMode.Searching -> when {
        state.sensing -> stringResource(R.string.state_gals_network)
        state.pathfinding != null -> stringResource(R.string.state_gals_profiles)
        state.gals -> stringResource(R.string.state_gals_nodes)
        else -> stringResource(R.string.state_testing)
    }
    ConnectMode.Connecting -> stringResource(R.string.state_connecting)
    ConnectMode.Connected -> stringResource(R.string.state_connected)
    ConnectMode.Error -> stringResource(R.string.state_failed)
}

@Composable
private fun supporting(mode: ConnectMode, vpn: VpnState, state: UiState, sumie: Boolean): String = when (mode) {
    ConnectMode.Idle -> if (sumie) stringResource(R.string.connect_circle_hint) else stringResource(R.string.connect_button_hint)
    ConnectMode.Searching -> when {
        state.sensing -> stringResource(R.string.network_mode_detecting)
        state.pathfinding != null -> state.pathfinding.let { "«${splitFlag(it.node).second}», ${profileLabel(it.profile)}" }
        else -> state.progress?.let { p ->
            when (state.galsNet) {
                NetMode.WHITE -> stringResource(R.string.whitelist_alive, p.alive)
                NetMode.BLACK -> stringResource(R.string.blocklist_alive, p.alive)
                null -> stringResource(R.string.nodes_alive, p.alive)
            }
        }.orEmpty()
    }
    ConnectMode.Connecting -> ((vpn as? VpnState.Connecting)?.node ?: state.preflight)?.let { splitFlag(it.name).second }.orEmpty()
    ConnectMode.Connected -> if (state.refining) stringResource(R.string.gals_refining) else stringResource(R.string.traffic_selected_node)
    ConnectMode.Error -> (vpn as? VpnState.Failed)?.message ?: stringResource(R.string.try_another_node)
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
    IosSection(header = stringResource(R.string.session_title)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Stat(Icons.Rounded.ArrowDownward, stringResource(R.string.traffic_download), formatSpeed(traffic.downBps), c.green, Modifier.weight(1f))
            Box(Modifier.width(0.5.dp).height(36.dp).background(c.separator))
            Stat(Icons.Rounded.ArrowUpward, stringResource(R.string.traffic_upload), formatSpeed(traffic.upBps), c.blue, Modifier.weight(1f).padding(start = 16.dp))
        }
        if (Ios.nothing) {
            DotBars(history, Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp, vertical = 8.dp))
        } else if (Ios.origin) {
            OriginBars(history, c.green, Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp, vertical = 8.dp))
        } else {
            Sparkline(history, c.green, Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp, vertical = 8.dp))
        }
        IosDivider()
        IosRow(title = stringResource(R.string.session_online), value = formatDuration(now - vpn.since))
        IosDivider()
        IosRow(title = stringResource(R.string.traffic_session), value = formatBytes(traffic.upTotal + traffic.downTotal))
        IosDivider()
        IosRow(title = stringResource(R.string.traffic_today), value = formatBytes(traffic.today))
        IosDivider()
        IosRow(
            title = stringResource(R.string.protection_title),
            value = listOfNotNull(
                securityLabel(vpn.node.securityIssue) ?: protectionOf(vpn.node),
                if (encryptedDns) stringResource(R.string.protection_https_dns) else null,
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
            Text(value, style = if (Ios.witch) MaterialTheme.typography.headlineSmall.copy(fontFamily = app.farvater.ui.theme.OriginFont) else MaterialTheme.typography.headlineSmall, color = c.label, maxLines = 1)
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

@Composable
private fun protectionOf(node: ProxyNode): String = when {
    node.security == "reality" -> "Reality"
    node.security == "tls" || node.protocol == Protocol.HYSTERIA2 ||
        (node.protocol == Protocol.TROJAN && node.security.isBlank()) -> "TLS"
    node.protocol == Protocol.SHADOWSOCKS -> "Shadowsocks"
    node.protocol == Protocol.VMESS -> "VMess"
    else -> stringResource(R.string.protection_node)
}

@Composable
private fun securityLabel(issue: String?): String? = when (issue) {
    "Без проверки сертификата" -> stringResource(R.string.security_no_certificate)
    "Без шифрования" -> stringResource(R.string.security_no_encryption)
    "Устаревшее шифрование" -> stringResource(R.string.security_old_encryption)
    else -> issue
}

@Composable
private fun WitchFeedback(content: @Composable () -> Unit) {
    val system = androidx.compose.ui.platform.LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val feedback = remember(system, view, scope) {
        object : androidx.compose.ui.hapticfeedback.HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
                if (hapticFeedbackType == androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm) {
                    scope.launch {
                        repeat(3) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            kotlinx.coroutines.delay(75)
                        }
                    }
                } else {
                    system.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalHapticFeedback provides feedback, content = content)
}

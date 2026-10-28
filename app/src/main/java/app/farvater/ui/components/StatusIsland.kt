package app.farvater.ui.components

import app.farvater.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.ui.formatDuration
import app.farvater.ui.formatSpeed
import app.farvater.ui.theme.HarmonyFont
import app.farvater.ui.theme.HyperFont
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.Numeric
import app.farvater.ui.theme.OriginFont
import app.farvater.ui.theme.OxygenFont
import kotlinx.coroutines.delay

private enum class Island { Off, Busy, On, Big, Fail }

private val IslandGreen = Color(0xFF2FD27A)
private val IslandBlue = Color(0xFF6FA6FF)

@Composable
fun StatusIsland(
    mode: ConnectMode,
    node: String?,
    since: Long?,
    downBps: Long,
    progress: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
) {
    val c = Ios.colors
    val hyper = Ios.hyper
    val font = when {
        hyper -> HyperFont
        Ios.harmony -> HarmonyFont
        Ios.oxygen -> OxygenFont
        else -> OriginFont
    }
    val text = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, color = Color.White)

    var big by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        val entered = previous != ConnectMode.Connected && mode == ConnectMode.Connected
        previous = mode
        if (entered && hyper) {
            big = true
            try {
                delay(1800)
            } finally {
                big = false
            }
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (mode == ConnectMode.Connected && since != null) {
        LaunchedEffect(since) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val spoken = when (mode) {
        ConnectMode.Idle -> stringResource(R.string.vpn_off)
        ConnectMode.Searching -> stringResource(R.string.node_searching)
        ConnectMode.Connecting -> stringResource(R.string.settings_connection)
        ConnectMode.Connected -> if (node != null) stringResource(R.string.connected_node, node) else stringResource(R.string.state_connected)
        ConnectMode.Error -> stringResource(R.string.connection_error)
    }

    val state = when (mode) {
        ConnectMode.Idle -> Island.Off
        ConnectMode.Searching, ConnectMode.Connecting -> Island.Busy
        ConnectMode.Connected -> if (big) Island.Big else Island.On
        ConnectMode.Error -> Island.Fail
    }

    Box(
        modifier.fillMaxWidth().padding(top = 6.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 420f), initialScale = 0.9f)) togetherWith
                    fadeOut(tween(90)) using SizeTransform(clip = true) { _, _ -> spring(dampingRatio = 0.62f, stiffness = 360f) }
            },
            label = "island",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black)
                .then(if (c.dark) Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50)) else Modifier)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
        ) { s ->
            when (s) {
                Island.Big -> Row(
                    Modifier.height(64.dp).padding(start = 12.dp, end = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(40.dp).background(IslandGreen, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GppGood, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text(stringResource(R.string.state_connected), style = text.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        if (node != null) {
                            Text(
                                node,
                                style = text.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.62f)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 220.dp),
                            )
                        }
                    }
                }
                else -> Row(
                    Modifier.height(36.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (s) {
                        Island.Busy -> CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = IslandBlue,
                            trackColor = IslandBlue.copy(alpha = 0.25f),
                            strokeWidth = 2.dp,
                        )
                        Island.On -> Icon(Icons.Rounded.GppGood, contentDescription = null, tint = IslandGreen, modifier = Modifier.size(16.dp))
                        else -> Box(
                            Modifier
                                .size(8.dp)
                                .background(if (s == Island.Fail) c.red else Color(0xFF8E8E93), CircleShape),
                        )
                    }
                    Text(
                        when (s) {
                            Island.Busy -> if (mode == ConnectMode.Connecting) stringResource(R.string.settings_connection) else stringResource(R.string.state_searching)
                            Island.On -> node ?: stringResource(R.string.state_protected)
                            Island.Fail -> stringResource(R.string.state_error)
                            else -> stringResource(R.string.vpn_state_off)
                        },
                        style = text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 170.dp),
                    )
                    val extra = when {
                        s == Island.Busy && progress != null && mode == ConnectMode.Searching -> "${progress.first}/${progress.second}"
                        s == Island.On && hyper -> "↓ ${formatSpeed(downBps)}"
                        s == Island.On && since != null -> formatDuration(now - since)
                        else -> null
                    }
                    if (extra != null) {
                        Text(
                            extra,
                            style = text.merge(Numeric).copy(color = if (s == Island.Busy) IslandBlue else IslandGreen),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

package app.farvater.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.ui.formatSpeed
import app.farvater.ui.theme.HyperFont
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.Numeric

@Composable
fun HyperConnect(
    mode: ConnectMode,
    status: String,
    progress: Float?,
    downBps: Long,
    upBps: Long,
    history: List<Long>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ios.colors
    val active = mode == ConnectMode.Connected
    val peak = (history.maxOrNull() ?: 0L).coerceAtLeast(512L * 1024)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).height(196.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VpnTile(mode, status, progress, onClick, Modifier.weight(1f).fillMaxHeight())
        SpeedMeter(downBps, peak, active, Icons.Rounded.ArrowDownward, "Загрузка", c.tint, Modifier.width(74.dp).fillMaxHeight())
        SpeedMeter(
            upBps, peak, active, Icons.Rounded.ArrowUpward, "Отдача",
            lerp(c.tint, if (c.dark) Color.Black else Color.White, 0.35f),
            Modifier.width(74.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun VpnTile(mode: ConnectMode, status: String, progress: Float?, onClick: () -> Unit, modifier: Modifier) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val still = rememberReduceMotion()
    val on = mode == ConnectMode.Connected
    val busy = mode == ConnectMode.Searching || mode == ConnectMode.Connecting

    val shake = remember { Animatable(0f) }
    var previous by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        val changed = previous != mode
        previous = mode
        if (!changed) return@LaunchedEffect
        when (mode) {
            ConnectMode.Connected -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            ConnectMode.Error -> {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                if (!still) {
                    shake.snapTo(10f)
                    shake.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = 900f))
                }
            }
            else -> Unit
        }
    }

    val bg by animateColorAsState(
        when (mode) {
            ConnectMode.Connected -> c.tint
            ConnectMode.Error -> c.red.copy(alpha = 0.14f).compositeOver(c.cell)
            else -> c.cell
        },
        tween(380),
        label = "tile",
    )
    val fg by animateColorAsState(if (on) Color.White else c.label, tween(380), label = "fg")
    val sub by animateColorAsState(if (on) Color.White.copy(alpha = 0.82f) else c.secondaryLabel, tween(380), label = "sub")
    val iconBg by animateColorAsState(
        when {
            on -> Color.White.copy(alpha = 0.22f)
            busy -> c.tint.copy(alpha = 0.14f)
            mode == ConnectMode.Error -> c.red.copy(alpha = 0.16f)
            else -> c.fill
        },
        tween(380),
        label = "iconBg",
    )
    val iconFg by animateColorAsState(
        when {
            on -> Color.White
            busy -> c.tint
            mode == ConnectMode.Error -> c.red
            else -> c.label
        },
        tween(380),
        label = "iconFg",
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press")
    val sweep by if (on && !still) {
        rememberInfiniteTransition(label = "sweep").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
            label = "sweep",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val shown by animateFloatAsState(progress ?: 0f, spring(stiffness = 200f), label = "progress")

    val description = when (mode) {
        ConnectMode.Idle -> "Подключиться"
        ConnectMode.Searching -> "Идёт поиск рабочего узла"
        ConnectMode.Connecting -> "Подключение"
        ConnectMode.Connected -> "Отключиться"
        ConnectMode.Error -> "Повторить подключение"
    }

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = shake.value.dp.toPx()
            }
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .drawWithContent {
                drawContent()
                if (on && sweep < 0.55f) {
                    val w = size.width
                    val x = -0.6f * w + 2.2f * w * (sweep / 0.55f)
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.32f), Color.Transparent),
                            start = Offset(x - w * 0.35f, 0f),
                            end = Offset(x + w * 0.35f, size.height),
                        ),
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                stateDescription = status
            }
            .padding(18.dp),
    ) {
        Box(Modifier.size(52.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(52.dp),
                    color = c.tint,
                    trackColor = Color.Transparent,
                    strokeWidth = 2.5.dp,
                )
            }
            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = iconFg, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            Text("VPN", style = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp), color = fg)
            Text(
                status,
                style = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
                color = sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(c.fill),
                ) {
                    Box(Modifier.fillMaxWidth(shown.coerceIn(0f, 1f)).fillMaxHeight().background(c.tint))
                }
            }
        }
    }
}

@Composable
private fun SpeedMeter(
    bps: Long,
    peak: Long,
    active: Boolean,
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier,
) {
    val c = Ios.colors
    val fraction by animateFloatAsState(
        if (active) (bps.toFloat() / peak).coerceIn(0.05f, 1f) else 0f,
        spring(dampingRatio = 0.7f, stiffness = 180f),
        label = "meter",
    )
    val text = if (active) formatSpeed(bps) else ""
    val value = if (active) text.substringBeforeLast(' ') else "—"
    val unit = if (active) text.substringAfterLast(' ') else ""
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(c.fill)
            .semantics(mergeDescendants = true) { contentDescription = if (active) "$label $text" else label },
    ) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fraction).background(color))
        Column(Modifier.align(Alignment.TopCenter).padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 20.sp).merge(Numeric),
                color = if (fraction > 0.84f) Color.White else c.label,
                maxLines = 1,
            )
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    style = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
                    color = if (fraction > 0.76f) Color.White.copy(alpha = 0.85f) else c.secondaryLabel,
                    maxLines = 1,
                )
            }
        }
        Icon(
            icon,
            contentDescription = null,
            tint = if (fraction > 0.16f) Color.White else c.secondaryLabel,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).size(22.dp),
        )
    }
}

package app.farvater.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.farvater.ui.ios.originRim
import app.farvater.ui.theme.EaseInOut
import app.farvater.ui.theme.Ios

@Composable
fun OriginTile(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Ios.colors
    val shape = RoundedCornerShape(34.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, originRim(c.dark), shape)
            .clip(shape)
            .background(c.cell.copy(alpha = if (c.dark) 0.8f else 0.88f))
            .padding(top = 14.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun OriginConnect(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 236.dp) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val still = rememberReduceMotion()

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
                    shake.snapTo(12f)
                    shake.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = 900f))
                }
            }
            else -> Unit
        }
    }

    val busy = mode == ConnectMode.Searching || mode == ConnectMode.Connecting
    val soft = spring<Color>(stiffness = Spring.StiffnessLow)
    val accent by animateColorAsState(
        when (mode) {
            ConnectMode.Idle -> c.secondaryLabel
            ConnectMode.Searching, ConnectMode.Connecting -> c.tint
            ConnectMode.Connected -> c.green
            ConnectMode.Error -> c.red
        },
        soft,
        label = "accent",
    )
    val top by animateColorAsState(
        when (mode) {
            ConnectMode.Idle -> if (c.dark) Color(0xFF2C3242) else Color.White
            ConnectMode.Searching, ConnectMode.Connecting -> Color(0xFF6E86FF)
            ConnectMode.Connected -> Color(0xFF36E39B)
            ConnectMode.Error -> Color(0xFFFF7A7F)
        },
        soft,
        label = "top",
    )
    val bottom by animateColorAsState(
        when (mode) {
            ConnectMode.Idle -> if (c.dark) Color(0xFF161921) else Color(0xFFE2E7F2)
            ConnectMode.Searching, ConnectMode.Connecting -> Color(0xFF3149E8)
            ConnectMode.Connected -> Color(0xFF0FA866)
            ConnectMode.Error -> Color(0xFFE5384A)
        },
        soft,
        label = "bottom",
    )
    val glyph by animateColorAsState(if (mode == ConnectMode.Idle) c.label else Color.White, label = "glyph")
    val ring = c.separator

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squashX by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.36f, stiffness = 420f), label = "squashX")
    val squashY by animateFloatAsState(if (pressed) 0.84f else 1f, spring(dampingRatio = 0.36f, stiffness = 420f), label = "squashY")

    val breath by originLoop(!still, 2600, EaseInOut, RepeatMode.Reverse, "breath")
    val orbit by originLoop(busy && !still, 1100, LinearEasing, RepeatMode.Restart, "orbit")
    val ripple by originLoop(mode == ConnectMode.Connected && !still, 2400, FastOutSlowInEasing, RepeatMode.Restart, "ripple")

    val description = when (mode) {
        ConnectMode.Idle -> "Подключиться"
        ConnectMode.Searching -> "Идёт поиск рабочего узла"
        ConnectMode.Connecting -> "Подключение"
        ConnectMode.Connected -> "Отключиться"
        ConnectMode.Error -> "Повторить подключение"
    }
    val button = size * 0.66f

    Box(
        modifier
            .size(size)
            .graphicsLayer { translationX = shake.value.dp.toPx() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            val br = button.toPx() / 2
            val glow = if (mode == ConnectMode.Idle) 0.16f else 0.34f
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = glow + 0.14f * breath), Color.Transparent),
                    radius = r * (0.9f + 0.1f * breath),
                ),
                radius = r,
            )
            val orbitR = br + 13.dp.toPx()
            if (mode == ConnectMode.Connected) {
                for (i in 0..1) {
                    val p = (ripple + i * 0.5f) % 1f
                    drawCircle(
                        accent.copy(alpha = (1f - p) * 0.5f),
                        radius = br + (r - br) * p,
                        style = Stroke(2.dp.toPx()),
                    )
                }
            } else {
                drawCircle(
                    if (mode == ConnectMode.Error) accent.copy(alpha = 0.5f) else ring.copy(alpha = 0.7f),
                    radius = orbitR,
                    style = Stroke(2.dp.toPx()),
                )
            }
            if (busy) {
                drawArc(
                    accent,
                    startAngle = orbit * 360f - 90f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - orbitR, center.y - orbitR),
                    size = Size(orbitR * 2, orbitR * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Box(
            Modifier
                .size(button)
                .graphicsLayer {
                    scaleX = squashX
                    scaleY = squashY
                }
                .shadow(18.dp, CircleShape, ambientColor = bottom, spotColor = bottom)
                .background(Brush.linearGradient(listOf(top, bottom)), CircleShape)
                .border(
                    1.5.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (c.dark) 0.5f else 0.9f), Color.White.copy(alpha = 0f))),
                    CircleShape,
                )
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.6f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(targetScale = 1.2f))
                },
                label = "glyph",
            ) { m ->
                Icon(
                    when (m) {
                        ConnectMode.Searching -> Icons.Rounded.Radar
                        ConnectMode.Error -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.PowerSettingsNew
                    },
                    contentDescription = null,
                    tint = glyph,
                    modifier = Modifier.size(54.dp),
                )
            }
        }
    }
}

@Composable
fun OriginBars(values: List<Long>, color: Color, modifier: Modifier = Modifier, count: Int = 26) {
    val c = Ios.colors
    val tail = values.takeLast(count)
    val max = (tail.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    val offset = count - tail.size
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        repeat(count) { i ->
            val v = tail.getOrNull(i - offset) ?: 0L
            val h by animateFloatAsState(
                (v / max).coerceIn(0.08f, 1f),
                spring(dampingRatio = 0.55f, stiffness = 300f),
                label = "bar",
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(h)
                    .background(if (v > 0) color else c.fill, RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun originLoop(active: Boolean, durationMs: Int, easing: Easing, repeat: RepeatMode, label: String): State<Float> {
    if (!active) return remember { mutableFloatStateOf(if (repeat == RepeatMode.Reverse) 0.5f else 0f) }
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = easing), repeat),
        label = label,
    )
}

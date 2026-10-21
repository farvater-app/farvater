package app.farvater.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.farvater.R
import app.farvater.data.UiStyle
import app.farvater.ui.ios.glassRim
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.LocalUiStyle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class ConnectMode { Idle, Searching, Connecting, Connected, Error }

@Composable
fun ConnectButton(
    mode: ConnectMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 184.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val c = Ios.colors
    val material = Ios.material
    val power = if (LocalUiStyle.current == UiStyle.ROSKOMPOZOR) ImageVector.vectorResource(R.drawable.ic_rkp) else Icons.Rounded.PowerSettingsNew
    val dark = c.dark
    val rim = glassRim(dark)

    val still = rememberReduceMotion()
    val burst = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    var previous by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        val changed = previous != mode
        previous = mode
        if (!changed) return@LaunchedEffect
        when (mode) {
            ConnectMode.Connected -> {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                if (!still) {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
                }
            }
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

    val lamp by animateColorAsState(
        targetValue = when (mode) {
            ConnectMode.Idle -> if (material) scheme.primaryContainer else scheme.surfaceContainerHigh
            ConnectMode.Searching, ConnectMode.Connecting -> scheme.primary
            ConnectMode.Connected -> c.green
            ConnectMode.Error -> c.red
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lamp",
    )
    val idleRing = scheme.primary
    val glyph by animateColorAsState(
        targetValue = when {
            mode != ConnectMode.Idle -> scheme.surface
            material -> scheme.onPrimaryContainer
            else -> scheme.primary
        },
        label = "glyph",
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )

    val beamStrength by animateFloatAsState(
        targetValue = if (mode == ConnectMode.Searching || mode == ConnectMode.Connecting) 1f else 0f,
        animationSpec = tween(450),
        label = "beam",
    )
    var calm by remember { mutableStateOf(false) }
    LaunchedEffect(mode) {
        calm = false
        if (mode == ConnectMode.Connected) {
            delay(PULSE_MS * 3L)
            calm = true
        }
    }
    val ringStrength by animateFloatAsState(
        targetValue = when (mode) {
            ConnectMode.Connected -> if (calm) 0f else 1f
            ConnectMode.Idle -> 0f
            else -> 0.5f
        },
        animationSpec = tween(600),
        label = "rings",
    )
    val spinning = mode == ConnectMode.Searching || mode == ConnectMode.Connecting || beamStrength > 0.01f
    val angle by loop(spinning, 1800, LinearEasing, "angle")
    val pulse by loop(ringStrength > 0.01f, PULSE_MS, FastOutSlowInEasing, "pulse")

    val description = when (mode) {
        ConnectMode.Idle -> "Подключиться"
        ConnectMode.Searching -> "Остановить"
        ConnectMode.Connecting -> "Отменить подключение"
        ConnectMode.Connected -> "Отключиться"
        ConnectMode.Error -> "Повторить подключение"
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = shake.value.dp.toPx()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val radius = this.size.minDimension / 2
            val lampRadius = radius * 0.6f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lamp.copy(alpha = 0.28f), Color.Transparent),
                    radius = radius,
                ),
                radius = radius,
            )

            if (burst.value < 1f) {
                val p = burst.value
                drawCircle(
                    color = lamp.copy(alpha = (1f - p) * 0.55f),
                    radius = lampRadius + (radius - lampRadius) * p,
                    style = Stroke(width = (4f - 2.5f * p).dp.toPx()),
                )
            }

            if (ringStrength > 0f) {
                for (i in 0..1) {
                    val p = (pulse + i * 0.5f) % 1f
                    drawCircle(
                        color = lamp.copy(alpha = (1f - p) * 0.45f * ringStrength),
                        radius = lampRadius + (radius - lampRadius) * p,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }

            if (beamStrength > 0f && !material) {
                rotate(angle * 360f) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent,
                            0.80f to Color.Transparent,
                            0.97f to lamp.copy(alpha = 0.6f * beamStrength),
                            1f to Color.Transparent,
                        ),
                        radius = radius,
                    )
                }
            }

            if (mode == ConnectMode.Idle && !material) {
                drawCircle(
                    color = idleRing.copy(alpha = 0.45f),
                    radius = lampRadius + 7.dp.toPx(),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            if (material) {
                val turn = if (spinning) angle * 2f * PI.toFloat() / 9f else 0f
                drawPath(cookie(center, lampRadius * 1.06f, turn), color = lamp)
            } else {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(lerp(lamp, Color.White, 0.10f), lamp, lerp(lamp, Color.Black, 0.08f)),
                        center = center + Offset(0f, -lampRadius * 0.3f),
                        radius = lampRadius * 1.4f,
                    ),
                    radius = lampRadius,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.14f else 0.3f), Color.Transparent),
                        center = center + Offset(0f, lampRadius * 0.85f),
                        radius = lampRadius * 0.7f,
                    ),
                    radius = lampRadius,
                )
                drawCircle(
                    brush = Brush.linearGradient(
                        rim,
                        start = Offset(center.x - lampRadius, center.y - lampRadius),
                        end = Offset(center.x + lampRadius, center.y + lampRadius),
                    ),
                    radius = lampRadius - 0.75.dp.toPx(),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(size * 0.6f)
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.75f)) togetherWith
                        (fadeOut(tween(140)) + scaleOut(targetScale = 1.15f))
                },
                label = "glyph",
            ) { m ->
                Icon(
                    imageVector = when (m) {
                        ConnectMode.Searching -> Icons.Rounded.Radar
                        ConnectMode.Error -> Icons.Rounded.ErrorOutline
                        else -> power
                    },
                    contentDescription = null,
                    tint = glyph,
                    modifier = Modifier.size(50.dp),
                )
            }
        }
    }
}

private const val PULSE_MS = 2600

private fun cookie(center: Offset, radius: Float, rotation: Float): Path {
    val path = Path()
    val steps = 180
    for (i in 0..steps) {
        val t = i.toFloat() / steps * 2f * PI.toFloat()
        val r = radius * (0.92f + 0.08f * cos(9f * t))
        val x = center.x + r * cos(t + rotation)
        val y = center.y + r * sin(t + rotation)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

@Composable
private fun loop(active: Boolean, durationMs: Int, easing: Easing, label: String): State<Float> {
    if (!active) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = easing), RepeatMode.Restart),
        label = label,
    )
}

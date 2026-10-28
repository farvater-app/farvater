package app.farvater.ui.components

import app.farvater.R
import androidx.compose.ui.res.stringResource
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.farvater.ui.theme.Ios
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private val PowerGlyph = listOf(
    "....#....",
    "....#....",
    ".#..#..#.",
    "#...#...#",
    "#.......#",
    "#.......#",
    "#.......#",
    ".#.....#.",
    "..#####..",
)

private val AlertGlyph = listOf(
    "....#....",
    "...###...",
    "...###...",
    "...###...",
    "....#....",
    "....#....",
    ".........",
    "....#....",
    "....#....",
)

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

@Composable
fun DotConnectButton(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val c = Ios.colors
    val fg = c.label
    val off = c.separator
    val red = c.red
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
                    shake.snapTo(10f)
                    shake.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = 900f))
                }
            }
            else -> Unit
        }
    }

    val busy = mode == ConnectMode.Searching || mode == ConnectMode.Connecting
    val currentMode by rememberUpdatedState(mode)
    val clock by produceState(0f, still) {
        if (still) return@produceState
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { value = (it - start) / 1000f }
            val m = currentMode
            if (m == ConnectMode.Idle || m == ConnectMode.Connected) delay(33)
        }
    }

    val fill = remember { Animatable(if (mode == ConnectMode.Connected) 1f else 0f) }
    val blink = remember { Animatable(0f) }
    LaunchedEffect(mode) {
        if (mode == ConnectMode.Connected) {
            fill.animateTo(1f, tween(if (still) 0 else 900, easing = FastOutSlowInEasing))
        } else {
            fill.animateTo(0f, tween(if (still) 0 else 320))
        }
    }
    LaunchedEffect(mode) {
        if (mode == ConnectMode.Error && !still) {
            repeat(3) {
                blink.animateTo(1f, tween(140))
                blink.animateTo(0f, tween(220))
            }
        } else {
            blink.snapTo(0f)
        }
    }
    val activity by animateFloatAsState(if (busy) 1f else 0f, tween(420), label = "activity")
    val searching by animateFloatAsState(if (mode == ConnectMode.Searching) 1f else 0f, tween(420), label = "searching")
    val failure by animateFloatAsState(if (mode == ConnectMode.Error) 1f else 0f, tween(360), label = "failure")

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val description = when (mode) {
        ConnectMode.Idle -> stringResource(R.string.action_connect)
        ConnectMode.Searching -> stringResource(R.string.action_stop_search)
        ConnectMode.Connecting -> stringResource(R.string.action_cancel_connect)
        ConnectMode.Connected -> stringResource(R.string.action_disconnect)
        ConnectMode.Error -> stringResource(R.string.action_retry_connect)
    }

    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = shake.value.dp.toPx()
            }
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val t = if (still) 0f else clock
            val radius = this.size.minDimension / 2
            val outerDot = this.size.minDimension * 0.021f
            val innerDot = this.size.minDimension * 0.016f
            val errorPulse = 0.55f + 0.45f * blink.value

            fun ring(count: Int, r: Float, dotSize: Float, brightness: (Int, Float) -> Float) {
                for (i in 0 until count) {
                    val pos = i.toFloat() / count
                    val angle = pos * 2f * PI.toFloat() - PI.toFloat() / 2f
                    val b = brightness(i, pos).coerceIn(0f, 1f)
                    val normal = lerp(off, fg, b)
                    val color = lerp(normal, lerp(off, red, errorPulse), failure)
                    drawCircle(
                        color = color,
                        radius = dotSize * (0.8f + 0.35f * max(b, failure * errorPulse)),
                        center = Offset(center.x + cos(angle) * r, center.y + sin(angle) * r),
                    )
                }
            }

            val head = if (still) 0.75f else (t * 0.62f) % 1f
            val back = if (still) 0.25f else 1f - (t * 0.41f) % 1f
            ring(64, radius * 0.93f, outerDot) { i, pos ->
                val marker = if (i % 8 == 0) 0.4f + 0.18f * sin(t * 1.7f + i) else 0f
                val behind = ((head - pos) % 1f + 1f) % 1f
                val comet = if (still) (if (pos < 0.5f) 0.9f else 0f) else (1f - behind / 0.38f).coerceAtLeast(0f).pow(1.7f)
                val sweep = ((fill.value * 1.2f - pos) / 0.18f).coerceIn(0f, 1f)
                val wave = (1f - abs(((t * 0.22f) % 1f) - pos) / 0.07f).coerceAtLeast(0f)
                max(max(marker * (1f - activity) * (1f - fill.value), comet * activity), sweep * (0.8f + 0.2f * wave))
            }
            ring(48, radius * 0.79f, innerDot) { _, pos ->
                val behind = ((pos - back) % 1f + 1f) % 1f
                val counter = (1f - behind / 0.3f).coerceAtLeast(0f).pow(1.5f) * searching
                val breathe = (0.25f + 0.25f * sin(t * 4f)) * (activity - searching).coerceAtLeast(0f)
                val sweep = ((fill.value * 1.2f - 0.2f - pos) / 0.18f).coerceIn(0f, 1f) * 0.55f
                max(max(counter, breathe), sweep)
            }

            if (fill.value > 0.01f && failure < 0.5f) {
                val beat = if (still) 1f else 0.75f + 0.25f * sin(t * 2.4f)
                drawCircle(
                    color = red.copy(alpha = fill.value * beat),
                    radius = outerDot * 1.45f,
                    center = Offset(center.x, center.y - radius * 0.93f),
                )
            }

            val cell = this.size.minDimension * 0.046f
            val scan = if (busy && !still) (t * 1.6f) % 1f else -1f
            glyph(PowerGlyph, cell, fg, off, 1f - failure, scan)
            if (failure > 0.01f) glyph(AlertGlyph, cell, red, off, failure, -1f)
        }
    }
}

private fun DrawScope.glyph(rows: List<String>, cell: Float, lit: Color, off: Color, alpha: Float, scan: Float) {
    if (alpha <= 0.01f) return
    val height = rows.size
    val width = rows.first().length
    val left = center.x - cell * (width - 1) / 2f
    val top = center.y - cell * (height - 1) / 2f
    rows.forEachIndexed { y, row ->
        val glow = if (scan >= 0f) (1f - abs(scan * (height + 2) - 1 - y) / 1.6f).coerceAtLeast(0f) else 0f
        row.forEachIndexed { x, ch ->
            val on = ch == '#'
            val color = if (on) lit.copy(alpha = alpha * (0.82f + 0.18f * glow)) else off.copy(alpha = alpha * (0.28f + 0.4f * glow))
            drawCircle(color, radius = cell * if (on) 0.34f else 0.24f, center = Offset(left + x * cell, top + y * cell))
        }
    }
}

@Composable
fun DotBars(values: List<Long>, modifier: Modifier = Modifier, rows: Int = 6, columns: Int = 32) {
    val c = Ios.colors
    val lit = c.label
    val off = c.separator
    val red = c.red
    Canvas(modifier) {
        val data = values.takeLast(columns)
        val max = (data.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val dx = size.width / columns
        val dy = size.height / rows
        val dot = minOf(dx, dy) * 0.3f
        val shift = columns - data.size
        for (i in 0 until columns) {
            val v = data.getOrNull(i - shift)
            val height = if (v == null || v <= 0) 0 else ceil(v / max * rows).toInt().coerceIn(1, rows)
            for (j in 0 until rows) {
                val on = rows - j <= height
                val color = when {
                    !on -> off
                    i == columns - 1 -> red
                    else -> lit
                }
                drawCircle(color, radius = dot, center = Offset(i * dx + dx / 2, j * dy + dy / 2))
            }
        }
    }
}

fun Modifier.dotGrid(ink: Color, spacing: Dp = 18.dp, alpha: Float = 0.09f): Modifier = drawWithCache {
    val step = spacing.toPx().toInt().coerceAtLeast(4)
    val tile = ImageBitmap(step, step)
    androidx.compose.ui.graphics.Canvas(tile).drawCircle(
        Offset(step / 2f, step / 2f),
        1.1.dp.toPx(),
        Paint().apply { color = ink },
    )
    val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind { drawRect(brush, alpha = alpha) }
}

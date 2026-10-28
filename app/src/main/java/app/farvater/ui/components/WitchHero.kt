package app.farvater.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.farvater.R
import kotlin.math.PI
import kotlin.math.sin

private val WitchInk = Color(0xFF0C0618)
private val WitchLilac = Color(0xFFB48CFF)
private val WitchMoon = Color(0xFFF1DFB3)

@Composable
fun WitchHero(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val still = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val clock = remember { mutableFloatStateOf(0f) }
    var previous by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        if (previous != mode) {
            when (mode) {
                ConnectMode.Connected -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                ConnectMode.Error -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
                else -> Unit
            }
        }
        previous = mode
    }
    LaunchedEffect(still) {
        clock.floatValue = 0f
        if (still) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { clock.floatValue = (it - start) / 1000f }
    }
    val description = stringResource(when (mode) {
        ConnectMode.Idle -> R.string.action_connect
        ConnectMode.Searching -> R.string.action_stop_search
        ConnectMode.Connecting -> R.string.action_cancel_connect
        ConnectMode.Connected -> R.string.action_disconnect
        ConnectMode.Error -> R.string.action_retry_connect
    })
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(360f / 236f)) {
            val unit = maxWidth / 360f
            Canvas(Modifier.fillMaxSize()) {
                scale(size.width / 360f, pivot = Offset.Zero) {
                    witchScene(mode, clock.floatValue)
                }
            }
            Box(
                Modifier.absoluteOffset(unit * 122, unit * 132).size(unit * 116, unit * 91)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics { contentDescription = description },
            )
        }
    }
}

private fun DrawScope.witchScene(mode: ConnectMode, time: Float) {
    val busy = mode == ConnectMode.Searching || mode == ConnectMode.Connecting
    val active = busy || mode == ConnectMode.Connected
    val potion = when (mode) {
        ConnectMode.Idle -> Color(0xFF26331F)
        ConnectMode.Searching, ConnectMode.Connecting -> Color(0xFF6FBF3F)
        ConnectMode.Connected -> Color(0xFF9BE15D)
        ConnectMode.Error -> Color(0xFF7A4FB3)
    }
    drawCircle(Brush.radialGradient(listOf(WitchLilac.copy(alpha = 0.18f), Color.Transparent), Offset(142f, 80f), 105f), 105f, Offset(142f, 80f))
    val stars = listOf(28f to 46f, 59f to 18f, 98f to 27f, 231f to 21f, 273f to 45f, 327f to 24f, 308f to 84f, 42f to 108f, 257f to 94f)
    stars.forEachIndexed { index, (x, y) ->
        val alpha = 0.5f + 0.35f * sin(time * (2f * PI.toFloat() / 2.4f) + index)
        val color = WitchMoon.copy(alpha = alpha)
        val r = if (index % 3 == 0) 3f else 1.5f
        drawLine(color, Offset(x - r, y), Offset(x + r, y), 1f, StrokeCap.Round)
        drawLine(color, Offset(x, y - r), Offset(x, y + r), 1f, StrokeCap.Round)
    }
    drawCircle(WitchMoon, 62f, Offset(142f, 80f))
    drawCircle(Color(0xFFE8CAA1).copy(alpha = 0.55f), 12f, Offset(116f, 52f))
    drawCircle(Color(0xFFE8CAA1).copy(alpha = 0.4f), 7f, Offset(157f, 110f))
    drawCircle(Color(0xFF160C29), 53f, Offset(165f, 61f))
    val flight = ((0.42f + time / if (busy) 6f else 11f) % 1f)
    translate(-32f + 424f * flight, 99f - sin(flight * PI.toFloat()) * 22f) {
        scale(0.42f, pivot = Offset.Zero) {
            drawLine(Color(0xFF997456), Offset(-43f, 7f), Offset(48f, 1f), 5f, StrokeCap.Round)
            for (i in 0..4) drawLine(Color(0xFFB38C61), Offset(-39f, 7f), Offset(-60f, -2f + i * 5f), 3f, StrokeCap.Round)
            witchCat(WitchMoon, 1f)
        }
    }
    drawPath(Path().apply {
        moveTo(0f, 200f)
        cubicTo(60f, 174f, 96f, 183f, 154f, 195f)
        cubicTo(216f, 176f, 301f, 179f, 360f, 196f)
        lineTo(360f, 236f); lineTo(0f, 236f); close()
    }, Color(0xFF1D1032))
    drawPath(Path().apply {
        moveTo(0f, 225f); cubicTo(110f, 200f, 226f, 219f, 360f, 207f)
        lineTo(360f, 236f); lineTo(0f, 236f); close()
    }, WitchInk)
    if (active) {
        val pulse = 0.16f + 0.05f * sin(time * 2f * PI.toFloat() / 2.6f)
        drawCircle(Brush.radialGradient(listOf(potion.copy(alpha = pulse), Color.Transparent), Offset(180f, 168f), 82f), 82f, Offset(180f, 168f))
        for (i in 0..4) {
            val x = 153f + i * 13f
            val h = 12f + 5f * sin(time * 2f * PI.toFloat() / 0.25f + i)
            drawPath(Path().apply {
                moveTo(x - 6f, 221f); quadraticTo(x - 8f, 213f, x, 221f - h)
                quadraticTo(x + 9f, 214f, x + 5f, 221f); close()
            }, if (i % 2 == 0) Color(0xFFDBA058) else Color(0xFFB477DE))
        }
    }
    drawLine(Color(0xFF6A4554), Offset(150f, 222f), Offset(211f, 215f), 5f, StrokeCap.Round)
    drawLine(Color(0xFF6A4554), Offset(153f, 215f), Offset(207f, 223f), 5f, StrokeCap.Round)
    drawOval(WitchLilac.copy(alpha = 0.55f), Offset(116f, 154f), Size(25f, 24f), style = Stroke(3f))
    drawOval(WitchLilac.copy(alpha = 0.55f), Offset(219f, 154f), Size(25f, 24f), style = Stroke(3f))
    val pot = Path().apply {
        moveTo(130f, 147f); cubicTo(115f, 183f, 132f, 211f, 180f, 211f)
        cubicTo(228f, 211f, 245f, 183f, 230f, 147f); close()
    }
    drawPath(pot, Color(0xFF221632))
    drawPath(pot, Color(0xFF65517D), style = Stroke(2f))
    drawOval(Color(0xFF65517D), Offset(127f, 136f), Size(106f, 24f))
    drawOval(potion, Offset(132f, 140f), Size(96f, 16f))
    drawArc(Color(0xFFF1E9FF), 135f, 270f, false, Offset(169f, 176f), Size(22f, 22f), style = Stroke(2.5f, cap = StrokeCap.Round))
    drawLine(Color(0xFFF1E9FF), Offset(180f, 174f), Offset(180f, 185f), 2.5f, StrokeCap.Round)
    drawLine(Color(0xFF65517D), Offset(145f, 206f), Offset(142f, 215f), 4f, StrokeCap.Round)
    drawLine(Color(0xFF65517D), Offset(214f, 206f), Offset(217f, 215f), 4f, StrokeCap.Round)
    if (active) repeat(7) { i ->
        val progress = (time * 0.38f + i * 0.17f) % 1f
        val x = 151f + i * 9f + sin(time + i) * 3f
        drawCircle(potion.copy(alpha = (1f - progress) * 0.8f), 2f + i % 3, Offset(x, 145f - progress * 40f), style = Stroke(1.4f))
    }
    if (mode == ConnectMode.Error) repeat(3) { i ->
        val rise = (time * 0.2f + i * 0.3f) % 1f
        drawOval(Brush.radialGradient(listOf(potion.copy(alpha = (1f - rise) * 0.3f), Color.Transparent), Offset(176f + i * 7f, 139f - rise * 48f), 23f), Offset(155f + i * 7f, 119f - rise * 48f), Size(44f, 44f))
    }
    translate(294f, 219f) {
        val eyes = when (mode) {
            ConnectMode.Connected -> potion
            ConnectMode.Error -> WitchLilac
            else -> Color(0xFFF6D77B)
        }
        witchCat(eyes, if (time % 4f > 3.86f) 0.12f else 1f)
    }
    drawLine(Color(0xFF6A427D), Offset(58f, 211f), Offset(62f, 186f), 2f)
    drawLine(Color(0xFF6A427D), Offset(61f, 197f), Offset(51f, 189f), 2f)
    drawLine(Color(0xFF6A427D), Offset(60f, 201f), Offset(69f, 192f), 2f)
    drawCircle(Color(0xFF9BE15D).copy(alpha = 0.7f), 2f, Offset(87f, 182f + sin(time) * 3f))
}

private fun DrawScope.witchCat(eyes: Color, blink: Float) {
    val fur = Color(0xFF090610)
    drawPath(Path().apply {
        moveTo(12f, -7f); cubicTo(43f, 2f, 44f, -26f, 32f, -20f)
    }, fur, style = Stroke(8f, cap = StrokeCap.Round))
    drawOval(fur, Offset(-18f, -40f), Size(36f, 42f))
    drawCircle(fur, 18f, Offset(0f, -46f))
    drawPath(Path().apply {
        moveTo(-17f, -49f); lineTo(-18f, -68f); lineTo(-6f, -60f)
        lineTo(8f, -60f); lineTo(19f, -68f); lineTo(17f, -48f); close()
    }, fur)
    drawOval(eyes, Offset(-12f, -49f), Size(8f, 5f * blink))
    drawOval(eyes, Offset(4f, -49f), Size(8f, 5f * blink))
    drawLine(fur, Offset(-8f, -50f), Offset(-8f, -44f), 1.5f)
    drawLine(fur, Offset(8f, -50f), Offset(8f, -44f), 1.5f)
    drawPath(Path().apply {
        moveTo(-17f, -64f); lineTo(-2f, -98f); quadraticTo(4f, -104f, 16f, -93f)
        lineTo(5f, -94f); lineTo(22f, -63f); close()
    }, Color(0xFF4D2B6F))
    drawLine(WitchLilac, Offset(-11f, -73f), Offset(17f, -71f), 4f)
    drawOval(Color(0xFF39204F), Offset(-29f, -67f), Size(60f, 11f))
    drawOval(WitchMoon, Offset(2f, -76f), Size(6f, 7f), style = Stroke(1.5f))
    drawLine(Color(0xFF39204F), Offset(-9f, -20f), Offset(-10f, -3f), 1.2f)
    drawLine(Color(0xFF39204F), Offset(4f, -20f), Offset(4f, -3f), 1.2f)
}

@Composable
fun WitchStyleMark() {
    Canvas(Modifier.size(30.dp)) {
        scale(size.width / 60f, pivot = Offset.Zero) {
            drawCircle(WitchMoon, 28f, Offset(30f, 30f))
            translate(30f, 55f) { scale(0.45f, pivot = Offset.Zero) { witchCat(Color(0xFF9BE15D), 1f) } }
        }
    }
}

package app.farvater.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.farvater.R
import app.farvater.ui.theme.Ios
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WinterHero(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val still = rememberWinterStill()
    val clock = rememberWinterClock(still || (mode != ConnectMode.Searching && mode != ConnectMode.Connecting))
    val illumination = remember { Animatable(if (mode == ConnectMode.Connected) 1f else 0f) }
    LaunchedEffect(mode, still) {
        val target = if (mode == ConnectMode.Connected) 1f else 0f
        if (still) illumination.snapTo(target) else illumination.animateTo(target, tween(if (target > 0f) 1200 else 500))
    }
    val description = stringResource(when (mode) {
        ConnectMode.Idle -> R.string.action_connect
        ConnectMode.Searching -> R.string.action_stop_search
        ConnectMode.Connecting -> R.string.action_cancel_connect
        ConnectMode.Connected -> R.string.action_disconnect
        ConnectMode.Error -> R.string.action_retry_connect
    })
    val dark = Ios.colors.dark
    Box(modifier.size(240.dp, 290.dp).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }) {
        Canvas(Modifier.matchParentSize().graphicsLayer()) {
            scale(size.width / 240f, size.height / 300f, pivot = Offset.Zero) { winterTree(dark) }
        }
        Canvas(Modifier.matchParentSize().graphicsLayer()) {
            scale(size.width / 240f, size.height / 300f, pivot = Offset.Zero) {
                winterLights(mode, illumination.value, clock.value, still)
            }
        }
    }
}

private fun DrawScope.winterTree(dark: Boolean) {
    drawOval(Color(0xFF244E68).copy(alpha = if (dark) 0.3f else 0.12f), Offset(56f, 278f), Size(128f, 12f))
    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF5D4434), Color(0xFF977359))), Offset(113f, 248f), Size(15f, 36f), androidx.compose.ui.geometry.CornerRadius(3f))
    for (tier in 5 downTo 0) {
        val top = 38f + tier * 32f
        val width = 28f + tier * 14f
        val branch = Path().apply {
            moveTo(120f, top)
            cubicTo(120f - width * 0.22f, top + 23f, 120f - width * 0.72f, top + 47f, 120f - width, top + 60f)
            lineTo(120f - width * 0.72f, top + 57f)
            lineTo(120f - width * 0.82f, top + 67f)
            quadraticTo(120f, top + 80f, 120f + width * 0.82f, top + 67f)
            lineTo(120f + width * 0.72f, top + 57f)
            lineTo(120f + width, top + 60f)
            cubicTo(120f + width * 0.72f, top + 47f, 120f + width * 0.22f, top + 23f, 120f, top)
            close()
        }
        drawPath(branch, Brush.horizontalGradient(listOf(Color(0xFF194F47), Color(0xFF357B67), Color(0xFF173F3D)), startX = 120f - width, endX = 120f + width))
        val snow = Path().apply {
            moveTo(120f - width * 0.82f, top + 66f)
            cubicTo(120f - width * 0.34f, top + 75f, 120f + width * 0.38f, top + 76f, 120f + width * 0.82f, top + 66f)
        }
        drawPath(snow, Color(0xFFE6F5F5), style = Stroke(3.5f, cap = StrokeCap.Round))
        val wire = Path().apply {
            moveTo(120f - width * 0.75f, top + 52f)
            quadraticTo(120f, top + 76f, 120f + width * 0.75f, top + 52f)
        }
        drawPath(wire, Color(0xFF9DB69A).copy(alpha = 0.65f), style = Stroke(1.1f))
        for (bauble in 0..2) {
            val x = 120f + (bauble - 1) * width * 0.55f
            val y = top + 44f + if (bauble == 1) 14f else 0f
            val color = if ((tier + bauble) % 2 == 0) Color(0xFFA95751) else Color(0xFFC5AF76)
            drawLine(Color(0xFFCAD7C7), Offset(x, y - 7f), Offset(x, y), 0.7f)
            drawCircle(color, 3.6f, Offset(x, y + 2f))
            drawCircle(Color.White.copy(alpha = 0.5f), 1f, Offset(x - 1f, y + 0.8f))
        }
    }
    drawPath(winterStar(Offset(120f, 33f), 14f), Color(0xFF9C9E7D))
}

private fun DrawScope.winterLights(mode: ConnectMode, progress: Float, time: Float, still: Boolean) {
    val busy = mode == ConnectMode.Searching || mode == ConnectMode.Connecting
    val palette = listOf(Color(0xFFFFD98C), Color(0xFFFF9D88), Color(0xFFA8E5CE))
    for (tier in 5 downTo 0) {
        val width = 28f + tier * 14f
        for (lamp in 0..6) {
            val index = (5 - tier) * 7 + lamp
            val power = winterLampPower(mode, progress, time, index, still)
            val x = 120f - width * 0.75f + lamp * width * 0.25f
            val y = 38f + tier * 32f + 52f + 12f * sin(lamp * PI.toFloat() / 6f)
            val point = Offset(x, y)
            val color = palette[(tier + lamp) % palette.size]
            if (power > 0f) {
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.42f * power), Color.Transparent), point, 10f), 10f, point)
            }
            drawCircle(Color(0xFF52796A), 2.2f, point)
            drawCircle(color.copy(alpha = power), 2.2f, point)
            if (power > 0.7f) drawCircle(Color(0xFFFFF6DD), 0.8f, point)
        }
    }
    val starPower = when {
        busy -> if (still) 0.45f else 0.5f + 0.3f * sin(time * 2f)
        else -> ((progress - 0.82f) / 0.18f).coerceIn(0f, 1f)
    }
    val center = Offset(120f, 33f)
    val starColor = if (mode == ConnectMode.Error) Color(0xFFD96C65) else Color(0xFFFFDF92)
    if (starPower > 0f) {
        val flare = if (progress in 0.83f..0.99f && !still) 1.3f else 1f
        drawCircle(Brush.radialGradient(listOf(starColor.copy(alpha = starPower * 0.5f), Color.Transparent), center, 32f * flare), 32f * flare, center)
        drawPath(winterStar(center, 14f), starColor.copy(alpha = starPower))
    }
    if (mode == ConnectMode.Error) drawPath(winterStar(center, 17f), starColor, style = Stroke(1.5f))
}

internal fun winterLampPower(mode: ConnectMode, progress: Float, time: Float, index: Int, still: Boolean): Float {
    if (mode == ConnectMode.Searching || mode == ConnectMode.Connecting) {
        if (still) return if (index % 7 == 0) 0.7f else 0.15f
        val cursor = (time * 13f) % 42f
        val distance = (cursor - index + 42f) % 42f
        return (1f - distance / 7f).coerceIn(0.12f, 1f)
    }
    return (progress * 43f - index).coerceIn(0f, 1f)
}

internal fun winterStar(center: Offset, radius: Float): Path = Path().apply {
    repeat(10) { i ->
        val angle = -PI / 2 + i * PI / 5
        val r = if (i % 2 == 0) radius else radius * 0.43f
        val x = center.x + cos(angle).toFloat() * r
        val y = center.y + sin(angle).toFloat() * r
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

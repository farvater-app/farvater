package app.farvater.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.farvater.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WinterHero(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val still = rememberReduceMotion()
    val owner = LocalLifecycleOwner.current
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(still, owner) {
        clock.floatValue = 0f
        if (still) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val start = withFrameMillis { it }
            while (true) withFrameMillis { clock.floatValue = (it - start) / 1000f }
        }
    }
    val description = stringResource(when (mode) {
        ConnectMode.Idle -> R.string.action_connect
        ConnectMode.Searching -> R.string.action_stop_search
        ConnectMode.Connecting -> R.string.action_cancel_connect
        ConnectMode.Connected -> R.string.action_disconnect
        ConnectMode.Error -> R.string.action_retry_connect
    })
    Canvas(modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(360f / 248f)
        .clip(RoundedCornerShape(28.dp)).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }) {
        scale(size.width / 360f, pivot = Offset.Zero) { winterScene(mode, clock.floatValue) }
    }
}

private fun DrawScope.winterScene(mode: ConnectMode, time: Float) {
    val active = mode == ConnectMode.Connected
    val busy = mode == ConnectMode.Connecting || mode == ConnectMode.Searching
    val snow = Color(0xFFEAF3FC)
    val glow = when (mode) {
        ConnectMode.Error -> Color(0xFFFFB4B8)
        ConnectMode.Connected -> Color(0xFFFFDE91)
        ConnectMode.Connecting, ConnectMode.Searching -> Color(0xFFB4DEFF)
        else -> Color(0xFF829CAF)
    }
    drawRect(Brush.verticalGradient(listOf(Color(0xFF101B37), Color(0xFF25445B)), endY = 248f), size = Size(360f, 248f))
    drawCircle(Brush.radialGradient(listOf(Color(0x335DBCD1), Color.Transparent), Offset(180f, 95f), 170f), 170f, Offset(180f, 95f))
    drawCircle(Color(0xFFFFEDC5), 18f, Offset(292f, 43f))
    drawCircle(Color(0xFF172640), 17f, Offset(299f, 37f))
    repeat(18) { i ->
        val x = 17f + (i * 67 % 326)
        val y = 16f + (i * 31 % 94)
        drawCircle(Color(0xFFDAE8FC).copy(alpha = 0.55f + 0.2f * sin(i + time * 0.4f)), 0.8f, Offset(x, y))
    }
    repeat(8) { i ->
        val x = i * 54f - 10f
        val y = 145f + (i % 3) * 8f
        val tree = Path().apply { moveTo(x, y - 54); lineTo(x - 24, y + 28); lineTo(x + 24, y + 28); close() }
        drawPath(tree, Color(0xFF152D3F))
    }
    val drift = Path().apply { moveTo(0f, 189f); cubicTo(95f, 161f, 234f, 205f, 360f, 175f); lineTo(360f, 248f); lineTo(0f, 248f); close() }
    drawPath(drift, Color(0xFFC5D9E8))
    val foreground = Path().apply { moveTo(0f, 227f); cubicTo(128f, 198f, 220f, 219f, 360f, 207f); lineTo(360f, 248f); lineTo(0f, 248f); close() }
    drawPath(foreground, snow)
    drawRoundRect(Color(0xFF314453), Offset(42f, 159f), Size(48f, 36f), androidx.compose.ui.geometry.CornerRadius(3f))
    val roof = Path().apply { moveTo(33f, 163f); lineTo(65f, 136f); lineTo(100f, 163f); close() }
    drawPath(roof, snow)
    drawRect(Color(0xFFFFDA96), Offset(54f, 169f), Size(10f, 13f))
    drawRect(Color(0xFFFFDA96), Offset(72f, 169f), Size(10f, 13f))
    drawRect(Color(0xFF6D5246), Offset(175f, 186f), Size(13f, 31f))
    for (tier in 3 downTo 0) {
        val top = 57f + tier * 29f
        val width = 25f + tier * 13f
        val branch = Path().apply {
            moveTo(182f, top); quadraticTo(175f - width * 0.2f, top + 22f, 182f - width, top + 58f)
            quadraticTo(182f, top + 69f, 182f + width, top + 58f); quadraticTo(185f + width * 0.2f, top + 22f, 182f, top); close()
        }
        drawPath(branch, if (tier % 2 == 0) Color(0xFF2D6A61) else Color(0xFF245C55))
        val cap = Path().apply { moveTo(182f - width, top + 58f); quadraticTo(182f, top + 70f, 182f + width, top + 58f) }
        drawPath(cap, snow.copy(alpha = 0.85f), style = Stroke(3f, cap = StrokeCap.Round))
        for (lamp in 0..4) {
            val x = 182f - width * 0.72f + lamp * width * 0.36f
            val y = top + 45f + 7f * sin(lamp * PI.toFloat() / 4f)
            val power = if (busy) 0.55f + 0.4f * sin(time * 3f + lamp + tier) else if (active) 1f else 0.6f
            drawCircle(glow.copy(alpha = power * 0.17f), 6f, Offset(x, y))
            drawCircle(glow.copy(alpha = power.coerceIn(0f, 1f)), 2f, Offset(x, y))
        }
    }
    val star = Path().apply {
        repeat(10) { i ->
            val angle = -PI / 2 + i * PI / 5
            val radius = if (i % 2 == 0) 10f else 4.5f
            val x = 182f + cos(angle).toFloat() * radius
            val y = 54f + sin(angle).toFloat() * radius
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawCircle(glow.copy(alpha = if (active) 0.16f else 0.07f), 24f, Offset(182f, 54f))
    drawPath(star, glow)
    repeat(42) { i ->
        val speed = 5f + i % 6
        val x = ((i * 83 % 360) + sin(time * 0.3f + i) * 5f + 360f) % 360f
        val y = ((i * 47 % 248) + time * speed) % 248f
        drawCircle(snow.copy(alpha = 0.45f + (i % 3) * 0.18f), 0.8f + (i % 3) * 0.4f, Offset(x, y))
    }
}

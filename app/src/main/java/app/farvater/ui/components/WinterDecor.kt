package app.farvater.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.farvater.ui.theme.Ios
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WinterBackdrop(modifier: Modifier = Modifier) {
    val dark = Ios.colors.dark
    val background = winterBackgroundColors(dark)
    val snowColor = winterSnowColor(dark)
    val clock = rememberWinterClock(rememberWinterStill())
    Box(modifier.clearAndSetSemantics {}) {
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            drawRect(Brush.verticalGradient(background.take(2)))
            val hill = Path().apply {
                moveTo(0f, size.height * 0.88f)
                cubicTo(size.width * 0.35f, size.height * 0.8f, size.width * 0.55f, size.height * 0.98f, size.width, size.height * 0.86f)
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(hill, background[2])
            val front = Path().apply {
                moveTo(0f, size.height * 0.96f)
                cubicTo(size.width * 0.3f, size.height * 0.9f, size.width * 0.7f, size.height * 0.94f, size.width, size.height * 0.92f)
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(front, background[3])
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val time = clock.value
            repeat(44) { i ->
                val x = ((i * 73 % 431) / 431f * size.width + sin(time * 0.24f + i) * 7.dp.toPx() + size.width) % size.width
                val y = ((i * 137 % 701) / 701f * size.height + time * (9 + i % 8).dp.toPx()) % size.height
                drawCircle(snowColor.copy(alpha = snowColor.alpha * (0.7f + i % 3 * 0.15f)),
                    (0.7f + i % 3 * 0.4f).dp.toPx(), Offset(x, y))
            }
        }
    }
}

internal fun winterBackgroundColors(dark: Boolean): List<Color> = if (dark)
    listOf(Color(0xFF0C1D32), Color(0xFF153B4C), Color(0xFF173649), Color(0xFF1C3F50))
else listOf(Color(0xFFE8F2FA), Color(0xFFF4F8FC), Color(0xFFD6E5F1), Color(0xFFE9F2F8))

internal fun winterSnowColor(dark: Boolean): Color = if (dark) Color(0xFFD5EAF5).copy(alpha = 0.11f) else Color.White.copy(alpha = 0.6f)

enum class WinterDecoration { Nodes, Sources, Settings }

@Composable
fun WinterHeaderDecoration(kind: WinterDecoration) {
    if (!Ios.winter) return
    val dark = Ios.colors.dark
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(if (kind == WinterDecoration.Settings) 46.dp else 32.dp)
        .clearAndSetSemantics {}) {
        scale(size.width / 360f, size.height / if (kind == WinterDecoration.Settings) 46f else 32f, pivot = Offset.Zero) {
            when (kind) {
                WinterDecoration.Nodes -> winterGarland(dark)
                WinterDecoration.Sources -> winterBranches(dark)
                WinterDecoration.Settings -> winterMittens(dark)
            }
        }
    }
}

private fun DrawScope.winterGarland(dark: Boolean) {
    val wire = Path().apply { moveTo(18f, 4f); cubicTo(90f, 27f, 270f, 27f, 342f, 4f) }
    drawPath(wire, if (dark) Color(0xFF7CA99F) else Color(0xFF51776C), style = Stroke(1.4f))
    repeat(11) { i ->
        val x = 30f + i * 30f
        val y = 5f + 16f * sin((i + 0.5f) * PI.toFloat() / 11f)
        drawLine(Color(0xFF728B7C), Offset(x, y), Offset(x, y + 4f), 1.3f)
        val color = listOf(Color(0xFFC88546), Color(0xFF5E9684), Color(0xFFC16E70))[i % 3]
        drawOval(if (dark) color.copy(alpha = 0.25f) else color.copy(alpha = 0.1f), Offset(x - 7f, y), Size(14f, 14f))
        drawOval(color, Offset(x - 2.6f, y + 3f), Size(5.2f, 7f))
        drawCircle(Color(0xFFFFE6B8), 0.9f, Offset(x - 0.6f, y + 5f))
    }
    snowflake(Offset(7f, 16f), 5f, if (dark) Color(0xFFBDD9E7) else Color(0xFF597E96))
    snowflake(Offset(354f, 15f), 4f, if (dark) Color(0xFFBDD9E7) else Color(0xFF597E96))
}

private fun DrawScope.winterBranches(dark: Boolean) {
    val ice = if (dark) Color(0xFF95BCCA) else Color(0xFF88AFC6)
    repeat(9) { i ->
        val x = 8f + i * 13f
        val length = 7f + (i * 7 % 17)
        val icicle = Path().apply { moveTo(x, 2f); lineTo(x + 3f, length); lineTo(x + 6f, 2f); close() }
        drawPath(icicle, ice.copy(alpha = 0.75f))
        drawLine(Color(0xFFE2F5FC), Offset(x + 2f, 3f), Offset(x + 3f, length - 2), 0.7f)
    }
    drawLine(ice, Offset(6f, 2f), Offset(123f, 2f), 2.8f, StrokeCap.Round)
    val start = Offset(243f, 5f)
    val end = Offset(352f, 24f)
    drawLine(Color(0xFF5B776A), start, end, 2f, StrokeCap.Round)
    repeat(13) { i ->
        val x = 247f + i * 8f
        val y = 6f + i * 1.4f
        drawLine(Color(0xFF3E7B66), Offset(x, y), Offset(x - 8f, y + 11f), 2f, StrokeCap.Round)
        drawLine(if (dark) Color(0xFF7CAC91) else Color(0xFF4F9077), Offset(x, y), Offset(x - 5f, y - 5f), 1.8f, StrokeCap.Round)
    }
    drawLine(Color(0xFFDDEFF2), Offset(263f, 8f), Offset(325f, 19f), 2.3f, StrokeCap.Round)
}

private fun DrawScope.winterMittens(dark: Boolean) {
    val snow = if (dark) Color(0xFF88AEBE) else Color(0xFFBBCFDD)
    val bank = Path().apply { moveTo(0f, 34f); cubicTo(55f, 20f, 101f, 39f, 155f, 32f); cubicTo(235f, 22f, 270f, 38f, 360f, 29f) }
    drawPath(bank, snow, style = Stroke(2.5f, cap = StrokeCap.Round))
    val cord = Path().apply { moveTo(278f, 13f); cubicTo(291f, -2f, 318f, -2f, 328f, 13f) }
    drawPath(cord, if (dark) Color(0xFFB8C8D5) else Color(0xFF708A9D), style = Stroke(1.2f))
    for (x in listOf(270f, 320f)) {
        drawRoundRect(Color(0xFFB96C6A), Offset(x, 13f), Size(19f, 22f), CornerRadius(7f))
        drawRoundRect(Color(0xFFB96C6A), Offset(x - 5f, 19f), Size(9f, 12f), CornerRadius(4f))
        drawRoundRect(Color(0xFFD9E8E9), Offset(x, 10f), Size(19f, 7f), CornerRadius(2f))
        snowflake(Offset(x + 10f, 25f), 4f, Color(0xFFF5EDE4))
    }
    snowflake(Offset(28f, 15f), 5f, snow)
    snowflake(Offset(49f, 7f), 3f, snow)
}

private fun DrawScope.snowflake(center: Offset, radius: Float, color: Color) {
    repeat(3) { i ->
        val angle = i * PI.toFloat() / 3f
        val delta = Offset(cos(angle) * radius, sin(angle) * radius)
        drawLine(color, center - delta, center + delta, 1f, StrokeCap.Round)
    }
}

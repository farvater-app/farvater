package app.farvater.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.core.model.ProxyNode
import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType
import app.farvater.ui.theme.NothingDot
import app.farvater.ui.theme.NothingLabel
import app.farvater.ui.theme.Numeric

@Composable
fun delayColor(result: TestResult?): Color {
    val c = Ios.colors
    if (Ios.nothing) return when {
        result == null -> c.tertiaryLabel
        !result.alive -> c.red
        else -> c.label
    }
    return when {
        result == null -> c.tertiaryLabel
        !result.alive -> c.red
        result.delayMs <= 350 -> c.green
        result.delayMs <= 900 -> c.tint
        else -> c.red
    }
}

fun delayText(result: TestResult?): String = when {
    result == null -> "—"
    !result.alive -> "нет ответа"
    result.method == TestMethod.TCP -> "${result.delayMs} мс*"
    else -> "${result.delayMs} мс"
}

@Composable
fun DelayLabel(result: TestResult?, modifier: Modifier = Modifier) {
    val color by animateColorAsState(delayColor(result), label = "delay")
    val dots = Ios.nothing && result?.alive == true
    Text(
        delayText(result),
        style = when {
            dots -> TextStyle(fontFamily = NothingDot, fontSize = 18.sp, lineHeight = 22.sp)
            Ios.nothing -> NothingLabel.copy(fontSize = 10.sp)
            else -> IosType.subheadline.merge(Numeric).copy(fontWeight = FontWeight.Medium)
        },
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
fun NodeAvatar(flag: String?, node: ProxyNode, size: Dp = 36.dp) {
    val c = Ios.colors
    Box(
        modifier = Modifier.size(size).background(c.fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (flag != null) {
            Text(flag, fontSize = (size.value * 0.52f).sp)
        } else {
            Text(
                node.protocol.title.take(2).uppercase(),
                style = IosType.caption1.copy(fontWeight = FontWeight.SemiBold),
                color = c.secondaryLabel,
            )
        }
    }
}

fun nodeSummary(node: ProxyNode): String = node.tags.joinToString(" · ")

@Composable
fun Sparkline(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().coerceAtLeast(1).toFloat()
        val step = size.width / (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v / max) * size.height * 0.85f
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent), endY = size.height),
            style = Fill,
        )
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        val lastY = size.height - (values.last() / max) * size.height * 0.85f
        drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(size.width, lastY))
    }
}

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier, active: Boolean = false) {
    if (Ios.nothing) {
        val c = Ios.colors
        val lit by animateColorAsState(if (active) c.red else c.secondaryLabel, label = "badge")
        Row(
            modifier
                .border(1.dp, c.separator, CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(lit, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(text.uppercase(), style = NothingLabel.copy(fontSize = 10.sp), color = c.label)
        }
        return
    }
    Text(
        text,
        style = IosType.caption1.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

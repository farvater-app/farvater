package app.farvater.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.SumieSeal
import app.farvater.ui.theme.SumieSealNight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val W = 360f
private const val H = 236f
private const val CX = 180f
private const val CY = 94f
private const val RADIUS = 70f
private val A0 = (-52.0 * PI / 180.0).toFloat()
private val SWEEP = (-332.0 * PI / 180.0).toFloat()
private const val SOLID_END = 0.84f
private val ensoBounds = Rect(CX - 92f, CY - 92f, CX + 92f, CY + 92f)

private class Palette(val ink: Color, val paper: Color, val seal: Color, val straw: Color) {
    val whisker = lerp(paper, ink, 0.38f)
}

@Composable
fun SumieHero(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val pal = remember(c.label, c.background, c.dark) {
        Palette(
            ink = c.label,
            paper = c.background,
            seal = if (c.dark) SumieSealNight else SumieSeal,
            straw = if (c.dark) Color(0xFF8C7B57) else Color(0xFFCDBB92),
        )
    }
    val haptics = LocalHapticFeedback.current
    val still = rememberReduceMotion()
    val measurer = rememberTextMeasurer()

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

    val painting = mode == ConnectMode.Searching || mode == ConnectMode.Connecting
    val drawn = remember { Animatable(1f) }
    val wash = remember { Animatable(1f) }
    LaunchedEffect(mode, still) {
        when {
            painting && still -> {
                wash.snapTo(1f)
                drawn.snapTo(0.62f)
            }
            painting -> while (true) {
                drawn.snapTo(0f)
                wash.snapTo(1f)
                drawn.animateTo(1f, tween(2400, easing = LinearOutSlowInEasing))
                delay(250)
                wash.animateTo(0f, tween(350))
            }
            else -> {
                launch { wash.animateTo(1f, tween(300)) }
                drawn.animateTo(
                    if (mode == ConnectMode.Error) 0.6f else 1f,
                    tween(if (mode == ConnectMode.Connected) 700 else 450, easing = FastOutSlowInEasing),
                )
            }
        }
    }
    val stamp = remember { Animatable(0f) }
    LaunchedEffect(mode) {
        if (mode == ConnectMode.Connected) {
            delay(450)
            stamp.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f))
        } else {
            stamp.animateTo(0f, tween(250))
        }
    }
    val ensoAlpha by animateFloatAsState(
        when (mode) {
            ConnectMode.Idle -> 0.26f
            ConnectMode.Error -> 0.7f
            else -> 0.95f
        },
        tween(500),
        label = "ensoAlpha",
    )
    val sleep by animateFloatAsState(if (mode == ConnectMode.Idle) 1f else 0f, tween(450), label = "sleep")
    val row by animateFloatAsState(if (painting) 1f else 0f, tween(450), label = "row")
    val light by animateFloatAsState(if (mode == ConnectMode.Connected) 1f else 0f, tween(450), label = "light")
    val storm by animateFloatAsState(if (mode == ConnectMode.Error) 1f else 0f, tween(600), label = "storm")

    val currentMode by rememberUpdatedState(mode)
    val clock by produceState(0L, still) {
        if (still) return@produceState
        while (true) {
            withFrameMillis { value = it }
            if (currentMode == ConnectMode.Idle || currentMode == ConnectMode.Connected) delay(40)
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    val description = when (mode) {
        ConnectMode.Idle -> "Подключиться"
        ConnectMode.Searching -> "Идёт поиск рабочего узла"
        ConnectMode.Connecting -> "Подключение"
        ConnectMode.Connected -> "Отключиться"
        ConnectMode.Error -> "Повторить подключение"
    }

    Canvas(
        modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .aspectRatio(W / H)
            .graphicsLayer { scaleX = press; scaleY = press }
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        val t = clock / 1000f
        val k = size.width / W
        scale(k, k, pivot = Offset.Zero) {
            mist(pal.ink, t, still)
            if (light > 0.01f) birds(t, still, pal.ink, light)
            enso(drawn.value, pal.ink, ensoAlpha * wash.value, painting)
            if (painting && drawn.value < 0.995f) inkBrush(drawn.value, pal, wash.value)
            powerGlyph(pal.ink, if (mode == ConnectMode.Idle) 0.45f else 0.9f)
            if (stamp.value > 0.01f) sealStamp(stamp.value, pal, measurer)
            water(pal.ink, t, still)
            if (light > 0.01f) reflection(t, still, pal.seal, light)
            val rough = painting || mode == ConnectMode.Error
            val bob = if (still) 0f else if (rough) 2.4f * sin(t * 3.6f) else 1.4f * sin(t * 1.9f)
            val tilt = if (still) 0f else if (rough) 1.2f * sin(t * 3.6f + 0.6f) else 0.6f * sin(t * 1.9f + 0.6f)
            translate(0f, bob) {
                rotate(tilt, Offset(296f, 196f)) {
                    if (row > 0.01f) rowingCat(t, still, pal, row)
                    drawPath(hull, pal.ink)
                    drawPath(rim, pal.paper, alpha = 0.42f, style = Stroke(0.9f))
                    if (sleep > 0.01f) sleepingCat(t, still, pal, sleep, measurer)
                    if (light > 0.01f) lanternCat(t, still, pal, light)
                    if (storm > 0.01f) umbrellaCat(t, still, pal, storm)
                }
            }
            if (storm > 0.01f) rain(storm, t, pal.ink)
        }
    }
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

private val grain: ImageBitmap by lazy {
    val side = 96
    val random = java.util.Random(7)
    val pixels = IntArray(side * side) { (random.nextInt(34) shl 24) or 0xFFFFFF }
    android.graphics.Bitmap.createBitmap(pixels, side, side, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

fun Modifier.paperGrain(ink: Color): Modifier = drawBehind {
    drawRect(
        ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)),
        colorFilter = ColorFilter.tint(ink, BlendMode.SrcIn),
        alpha = 0.55f,
    )
}

private fun widthAt(t: Float): Float = when {
    t < 0.05f -> 9f + 6f * (t / 0.05f)
    t < 0.2f -> 15f - 0.6f * ((t - 0.05f) / 0.15f)
    t < SOLID_END -> 14.4f - 3.9f * ((t - 0.2f) / (SOLID_END - 0.2f))
    else -> 10.5f - 3.5f * ((t - SOLID_END) / (1f - SOLID_END))
}

private fun radiusAt(a: Float): Float = RADIUS + 1.2f * sin(a * 2f + 1.3f) + 0.5f * sin(a * 5.3f)

private fun ensoPoint(t: Float, offset: Float = 0f): Offset {
    val a = A0 + SWEEP * t
    val r = radiusAt(a) + offset
    return Offset(CX + r * cos(a), CY + r * sin(a))
}

private inline fun band(from: Float, to: Float, steps: Int, offset: (Float) -> Float, width: (Float, Float) -> Float): Path {
    val path = Path()
    for (i in 0..steps) {
        val u = i / steps.toFloat()
        val t = from + (to - from) * u
        val p = ensoPoint(t, offset(t) + width(t, u) / 2)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    for (i in steps downTo 0) {
        val u = i / steps.toFloat()
        val t = from + (to - from) * u
        val p = ensoPoint(t, offset(t) - width(t, u) / 2)
        path.lineTo(p.x, p.y)
    }
    path.close()
    return path
}

private class Streak(val across: Float, val start: Float, val end: Float, val width: Float, val alpha: Float)

private val streaks: List<Streak> = run {
    var seed = 11L
    fun next(): Float {
        seed = seed * 16807 % 2147483647
        return (seed - 1) / 2147483646f
    }
    List(14) {
        val across = -0.32f + 0.64f * next()
        val start = 0.12f + 0.8f * next().pow(0.45f)
        val length = 0.05f + 0.16f * next()
        Streak(across, start, min(start + length, 0.99f), 0.7f + 1.1f * next(), 0.6f + 0.4f * next())
    }
}

private class Bristle(val across: Float, val end: Float, val width: Float)

private val bristles = listOf(
    Bristle(-0.4f, 0.93f, 2f),
    Bristle(-0.22f, 0.975f, 2.6f),
    Bristle(-0.04f, 0.995f, 2.8f),
    Bristle(0.14f, 0.985f, 2.6f),
    Bristle(0.3f, 0.955f, 2.2f),
    Bristle(0.43f, 0.9f, 1.6f),
)

private val inkTone: Brush = run {
    fun tone(t: Float) = Color.Black.copy(alpha = 1f - 0.3f * ((t - 0.08f) / 0.9f).coerceIn(0f, 1f))
    val stops = ArrayList<Pair<Float, Color>>()
    for (i in 0..24) {
        val t = 0.928f * i / 24
        stops += ((308f - 332f * t) / 360f).coerceIn(0f, 1f) to tone(t)
    }
    stops += 336f / 360f to tone(1f)
    stops += 1f to tone(0.928f)
    stops.sortBy { it.first }
    Brush.sweepGradient(*stops.toTypedArray(), center = Offset(CX, CY))
}

private fun DrawScope.enso(progress: Float, ink: Color, alpha: Float, painting: Boolean) {
    if (alpha < 0.01f) return
    val layer = Paint().also { it.alpha = alpha }
    drawIntoCanvas { it.saveLayer(ensoBounds, layer) }
    val solid = min(progress, SOLID_END)
    drawPath(band(0f, solid, max(2, (220 * solid).toInt()), { 0f }, { t, _ -> widthAt(t) }), ink)
    drawCircle(ink, widthAt(0f) / 2, ensoPoint(0f))
    if (progress > SOLID_END - 0.004f) {
        bristles.forEach { b ->
            val end = min(progress, b.end)
            if (end > SOLID_END - 0.004f) {
                drawPath(band(SOLID_END - 0.004f, end, 30, { t -> b.across * widthAt(t) }, { _, u -> b.width * (1f - 0.85f * u) }), ink)
            }
        }
    }
    streaks.forEach { s ->
        val end = min(s.end, progress - 0.01f)
        if (end > s.start) {
            val span = (end - s.start) / (s.end - s.start)
            drawPath(
                band(s.start, end, 24, { t -> s.across * widthAt(t) }, { _, u -> s.width * sin(PI.toFloat() * min(1f, u * span)) }),
                Color.Black,
                alpha = s.alpha,
                blendMode = BlendMode.DstOut,
            )
        }
    }
    drawRect(inkTone, ensoBounds.topLeft, ensoBounds.size, blendMode = BlendMode.DstIn)
    if (painting && progress < 0.995f) drawCircle(ink, widthAt(progress) / 2 + 0.8f, ensoPoint(progress))
    drawIntoCanvas { it.restore() }
}

private fun DrawScope.inkBrush(progress: Float, pal: Palette, alpha: Float) {
    val q = ensoPoint(progress)
    rotate(-62f + 4f * sin(progress * 20f), q) {
        val tuft = Path().apply {
            moveTo(q.x - 2f, q.y)
            quadraticTo(q.x + 3f, q.y - 4f, q.x + 11f, q.y - 2.8f)
            lineTo(q.x + 11f, q.y + 2.8f)
            quadraticTo(q.x + 3f, q.y + 4f, q.x - 2f, q.y)
            close()
        }
        drawPath(tuft, pal.ink, alpha = alpha)
        drawRect(pal.straw, Offset(q.x + 11f, q.y - 2f), Size(32f, 4f), alpha = alpha)
        drawRect(pal.ink, Offset(q.x + 11f, q.y - 2f), Size(32f, 4f), alpha = 0.9f * alpha, style = Stroke(0.7f))
        drawLine(pal.ink, Offset(q.x + 11.5f, q.y - 2.2f), Offset(q.x + 11.5f, q.y + 2.2f), 1.8f, alpha = alpha)
        drawLine(pal.ink, Offset(q.x + 38f, q.y - 2f), Offset(q.x + 38f, q.y + 2f), 0.9f, alpha = 0.7f * alpha)
        val cord = Path().apply {
            moveTo(q.x + 43f, q.y)
            quadraticTo(q.x + 48f, q.y + 3f, q.x + 46f, q.y + 7f)
        }
        drawPath(cord, pal.seal, alpha = 0.9f * alpha, style = Stroke(0.7f, cap = StrokeCap.Round))
    }
}

private fun taper(x1: Float, y1: Float, x2: Float, y2: Float, w1: Float, w2: Float): Path {
    val len = hypot(x2 - x1, y2 - y1)
    val nx = -(y2 - y1) / len
    val ny = (x2 - x1) / len
    return Path().apply {
        moveTo(x1 + nx * w1 / 2, y1 + ny * w1 / 2)
        lineTo(x2 + nx * w2 / 2, y2 + ny * w2 / 2)
        lineTo(x2 - nx * w2 / 2, y2 - ny * w2 / 2)
        lineTo(x1 - nx * w1 / 2, y1 - ny * w1 / 2)
        close()
    }
}

private val powerArc = Path().apply {
    val from = (-58.0 * PI / 180.0).toFloat()
    val to = (238.0 * PI / 180.0).toFloat()
    fun point(i: Int, side: Float): Offset {
        val f = i / 40f
        val a = from + (to - from) * f
        val r = 21f + side * (1.4f + 3.8f * f) / 2
        return Offset(CX + r * cos(a), CY - 1f + r * sin(a))
    }
    point(0, 1f).let { moveTo(it.x, it.y) }
    for (i in 0..40) point(i, 1f).let { lineTo(it.x, it.y) }
    for (i in 40 downTo 0) point(i, -1f).let { lineTo(it.x, it.y) }
    close()
}

private val powerStem = taper(CX, CY - 30f, CX + 0.4f, CY - 4f, 5.2f, 1.6f)

private fun DrawScope.powerGlyph(ink: Color, alpha: Float) {
    drawPath(powerStem, ink, alpha = alpha)
    drawPath(powerArc, ink, alpha = alpha)
}

private val farRidge = Path().apply {
    moveTo(-30f, 146f)
    cubicTo(0f, 130f, 24f, 116f, 52f, 122f)
    cubicTo(74f, 127f, 90f, 104f, 116f, 106f)
    cubicTo(142f, 108f, 154f, 132f, 184f, 130f)
    cubicTo(212f, 128f, 232f, 110f, 258f, 112f)
    cubicTo(286f, 114f, 300f, 130f, 330f, 124f)
    cubicTo(350f, 120f, 370f, 124f, 392f, 130f)
    lineTo(392f, 206f)
    lineTo(-30f, 206f)
    close()
}

private val nearRidge = Path().apply {
    moveTo(-30f, 170f)
    cubicTo(8f, 162f, 34f, 146f, 70f, 150f)
    cubicTo(102f, 154f, 120f, 166f, 156f, 160f)
    cubicTo(196f, 152f, 224f, 140f, 262f, 144f)
    cubicTo(300f, 148f, 324f, 160f, 392f, 154f)
    lineTo(392f, 212f)
    lineTo(-30f, 212f)
    close()
}

private fun DrawScope.mist(ink: Color, t: Float, still: Boolean) {
    translate(if (still) 0f else 6f * sin(t * 2f * PI.toFloat() / 40f), 0f) {
        drawPath(farRidge, Brush.verticalGradient(listOf(ink.copy(alpha = 0.085f), Color.Transparent), startY = 104f, endY = 200f))
    }
    translate(if (still) 0f else 9f * sin(t * 2f * PI.toFloat() / 28f + 1f), 0f) {
        drawPath(nearRidge, Brush.verticalGradient(listOf(ink.copy(alpha = 0.13f), Color.Transparent), startY = 140f, endY = 206f))
    }
}

private fun DrawScope.water(ink: Color, t: Float, still: Boolean) {
    val s = if (still) 0f else 4f * sin(t * 0.8f)
    drawPath(taper(20f, 209f, 128f, 208f, 0.3f, 1.6f), ink, alpha = 0.22f)
    drawPath(taper(132f, 206f, 356f, 204f, 0.6f, 3.2f), ink, alpha = 0.5f)
    drawPath(taper(214f + s, 214f, 312f + s, 213f, 2f, 0.4f), ink, alpha = 0.3f)
    drawPath(taper(70f - s, 218f, 196f - s, 217f, 0.4f, 1.6f), ink, alpha = 0.18f)
    drawPath(taper(250f - s, 221f, 330f - s, 220.5f, 0.3f, 1.2f), ink, alpha = 0.16f)
}

private val hull = Path().apply {
    moveTo(240f, 190f)
    cubicTo(260f, 201.5f, 318f, 203.5f, 340f, 190f)
    cubicTo(346f, 186f, 350f, 180f, 355f, 175.5f)
    cubicTo(353f, 188f, 346f, 197f, 332f, 202f)
    cubicTo(300f, 211f, 260f, 209f, 240f, 190f)
    close()
}

private val rim = Path().apply {
    moveTo(247f, 193f)
    cubicTo(266f, 201f, 312f, 202f, 336f, 192f)
}

private fun DrawScope.sealStamp(progress: Float, pal: Palette, measurer: TextMeasurer) {
    val center = Offset(56f, 58f)
    val s = 1.5f - 0.5f * progress
    val alpha = progress.coerceIn(0f, 1f)
    rotate(-6f, center) {
        scale(s, s, center) {
            drawRoundRect(pal.seal, Offset(center.x - 13.5f, center.y - 13.5f), Size(27f, 27f), CornerRadius(3.5f), alpha = alpha)
            drawRoundRect(
                pal.paper,
                Offset(center.x - 11f, center.y - 11f),
                Size(22f, 22f),
                CornerRadius(2f),
                alpha = 0.35f * alpha,
                style = Stroke(0.8f),
            )
            glyph(measurer, "航", center, 16f, pal.paper, alpha)
        }
    }
}

private fun DrawScope.glyph(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    size: Float,
    color: Color,
    alpha: Float,
    italic: Boolean = false,
) {
    val layout = measurer.measure(
        text,
        TextStyle(
            fontSize = (size / density / fontScale).sp,
            fontFamily = FontFamily.Serif,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            color = color,
        ),
    )
    drawText(
        layout,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
        alpha = alpha,
    )
}

private fun DrawScope.birds(t: Float, still: Boolean, ink: Color, alpha: Float) {
    listOf(Triple(0f, 0f, 1f), Triple(14f, 9f, 0.8f), Triple(-11f, 13f, 0.7f)).forEachIndexed { i, (dx, dy, s) ->
        val phase = if (still) 0.5f else ((t * 6f + i * 3f) % 60f) / 60f
        val x = 262f + 60f * phase + dx
        val y = 34f + dy + if (still) 0f else 1.5f * sin(t * 0.9f + i)
        val flap = if (still) 0.5f else 0.5f + 0.5f * sin(t * 7f + i * 1.7f)
        val wing = Path().apply {
            moveTo(x - 6f * s, y - 1.5f * s - 2.5f * s * flap)
            quadraticTo(x - 2.5f * s, y - s * flap, x, y + 0.8f * s)
            quadraticTo(x + 2.5f * s, y - s * flap, x + 6f * s, y - 1.5f * s - 2.5f * s * flap)
        }
        drawPath(wing, ink, alpha = 0.75f * alpha * sin(PI.toFloat() * phase), style = Stroke(1.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.rain(alpha: Float, t: Float, ink: Color) {
    for (i in 0 until 34) {
        val x = ((i * 41 + 13) % 380).toFloat() - 10f
        val y = ((i * 67 + t * 170f) % 250f) - 10f
        drawPath(taper(x, y, x - 5f, y + 15f, 0.2f, 1.2f), ink, alpha = 0.26f * alpha)
    }
}

private val headShape = Path().apply {
    moveTo(-12.5f, 2f)
    cubicTo(-13.5f, -6.5f, -7.5f, -10.5f, 0f, -10.5f)
    cubicTo(7.5f, -10.5f, 13.5f, -6.5f, 12.5f, 2f)
    lineTo(15.5f, 4.6f)
    lineTo(11.6f, 5.4f)
    cubicTo(9.5f, 9.6f, 5f, 11f, 0f, 11f)
    cubicTo(-5f, 11f, -9.5f, 9.6f, -11.6f, 5.4f)
    lineTo(-15.5f, 4.6f)
    close()
}

private val earsUp = Path().apply {
    moveTo(-12.6f, -2f); lineTo(-12.8f, -17.5f); quadraticTo(-7f, -13.5f, -3f, -10f); close()
    moveTo(12.6f, -2f); lineTo(12.8f, -17.5f); quadraticTo(7f, -13.5f, 3f, -10f); close()
}

private val earsDown = Path().apply {
    moveTo(-6f, -9f); lineTo(-17.5f, -8.5f); lineTo(-11.5f, -2f); close()
    moveTo(6f, -9f); lineTo(17.5f, -8.5f); lineTo(11.5f, -2f); close()
}

private val innerEars = Path().apply {
    moveTo(-10.6f, -6f); lineTo(-10.8f, -13.6f); lineTo(-6.6f, -9.8f); close()
    moveTo(10.6f, -6f); lineTo(10.8f, -13.6f); lineTo(6.6f, -9.8f); close()
}

private fun closedEyes(y: Float, lift: Float) = Path().apply {
    moveTo(-7.6f, y); quadraticTo(-5.1f, y + lift, -2.6f, y)
    moveTo(2.6f, y); quadraticTo(5.1f, y + lift, 7.6f, y)
}

private val happyEyes = closedEyes(2f, -3.2f)
private val sleepyEyes = closedEyes(0.8f, 2.4f)

private val sadEyes = Path().apply {
    moveTo(-7.6f, 0.2f); quadraticTo(-5f, 2.8f, -2.6f, 1.6f)
    moveTo(7.6f, 0.2f); quadraticTo(5f, 2.8f, 2.6f, 1.6f)
}

private val nose = Path().apply {
    moveTo(-1.7f, 4.4f); lineTo(1.7f, 4.4f); lineTo(0f, 6.1f); close()
}

private val mouth = Path().apply {
    moveTo(-2.6f, 6.9f)
    quadraticTo(-1.3f, 8.3f, 0f, 6.4f)
    quadraticTo(1.3f, 8.3f, 2.6f, 6.9f)
}

private val whiskers = Path().apply {
    for (s in floatArrayOf(-1f, 1f)) {
        moveTo(s * 5f, 5.2f); quadraticTo(s * 13f, 3.4f, s * 22f, 3.2f)
        moveTo(s * 5f, 6.6f); quadraticTo(s * 13f, 6.8f, s * 21f, 8.6f)
    }
}

private enum class Eyes { Open, Happy, Sleep, Sad }

private fun blinking(t: Float): Boolean = (t % 4.3f) > 4.15f

private fun DrawScope.ears(cx: Float, cy: Float, down: Boolean, pal: Palette, alpha: Float) {
    translate(cx, cy) {
        drawPath(if (down) earsDown else earsUp, pal.ink, alpha = alpha)
        if (!down) drawPath(innerEars, pal.seal, alpha = 0.5f * alpha)
    }
}

private fun DrawScope.head(cx: Float, cy: Float, eyes: Eyes, earsDown: Boolean, blink: Boolean, look: Float, pal: Palette, alpha: Float) {
    ears(cx, cy, earsDown, pal, alpha)
    translate(cx, cy) {
        drawPath(headShape, pal.ink, alpha = alpha)
        val line = Stroke(1.4f, cap = StrokeCap.Round)
        when (eyes) {
            Eyes.Open -> {
                val ry = if (blink) 0.35f else 2.5f
                for (dx in floatArrayOf(-5.2f, 5.2f)) {
                    drawOval(pal.paper, Offset(dx - 2.3f, 0.6f - ry), Size(4.6f, ry * 2), alpha = alpha)
                    if (!blink) drawOval(pal.ink, Offset(dx - 0.75f + look, -1.3f), Size(1.5f, 3.8f), alpha = alpha)
                }
            }
            Eyes.Happy -> drawPath(happyEyes, pal.paper, alpha = alpha, style = line)
            Eyes.Sleep -> drawPath(sleepyEyes, pal.paper, alpha = alpha, style = line)
            Eyes.Sad -> drawPath(sadEyes, pal.paper, alpha = alpha, style = line)
        }
        drawPath(nose, pal.seal, alpha = 0.9f * alpha)
        drawPath(mouth, pal.paper, alpha = 0.85f * alpha, style = Stroke(0.7f, cap = StrokeCap.Round))
        drawPath(whiskers, pal.whisker, alpha = alpha, style = Stroke(0.65f, cap = StrokeCap.Round))
    }
}

private val brim = Path().apply {
    moveTo(-24f, 4f)
    quadraticTo(0f, -16f, 24f, 4f)
    quadraticTo(0f, 0.5f, -24f, 4f)
    close()
}

private fun DrawScope.hat(cx: Float, cy: Float, degrees: Float, pal: Palette, alpha: Float) {
    rotate(degrees, Offset(cx, cy)) {
        translate(cx, cy) {
            drawPath(brim, pal.straw, alpha = alpha)
            drawPath(brim, pal.ink, alpha = alpha, style = Stroke(1.3f))
            for (dx in floatArrayOf(-13f, -6.5f, 0f, 6.5f, 13f)) {
                drawLine(pal.ink, Offset(0f, -10f), Offset(dx, 2.4f - abs(dx) * 0.05f), 0.6f, alpha = 0.45f * alpha)
            }
            drawCircle(pal.ink, 1.4f, Offset(0f, -10.5f), alpha = alpha)
        }
    }
}

private val scarfBand = Path().apply {
    moveTo(-9.5f, 0f)
    quadraticTo(0f, 5f, 9.5f, 0f)
    lineTo(8.5f, 4.2f)
    quadraticTo(0f, 9f, -8.5f, 4.2f)
    close()
}

private val scarfEnd = Path().apply {
    moveTo(4f, 5f)
    quadraticTo(9f, 8f, 14f, 13f)
    lineTo(10f, 14.5f)
    quadraticTo(7f, 10f, 2f, 7f)
    close()
}

private fun DrawScope.scarf(cx: Float, cy: Float, flutter: Float, pal: Palette, alpha: Float) {
    translate(cx, cy) {
        drawPath(scarfBand, pal.seal, alpha = alpha)
        rotate(flutter, Offset(5f, 5f)) { drawPath(scarfEnd, pal.seal, alpha = alpha) }
    }
}

private val sitBody = Path().apply {
    moveTo(-12f, 0f)
    cubicTo(-16f, -9f, -13f, -24f, -6.5f, -29f)
    cubicTo(-2.5f, -31f, 2.5f, -31f, 6.5f, -29f)
    cubicTo(13f, -24f, 16f, -9f, 12f, 0f)
    close()
}

private val pawLine = Path().apply {
    moveTo(-1.2f, -13f)
    quadraticTo(-1.6f, -5f, -1.2f, -0.5f)
}

private fun tailPath(curl: Float) = Path().apply {
    moveTo(0f, 0f)
    cubicTo(12f * curl, 2f, 17f * curl, -8f, 12f * curl, -16f)
    cubicTo(10f * curl, -19f, 7f * curl, -18f, 7.5f * curl, -15.5f)
}

private val tailRight = tailPath(1f)
private val tailLeft = tailPath(-1f)

private fun DrawScope.tail(x: Float, y: Float, sway: Float, right: Boolean, pal: Palette, alpha: Float) {
    rotate(sway, Offset(x, y)) {
        translate(x, y) {
            drawPath(if (right) tailRight else tailLeft, pal.ink, alpha = alpha, style = Stroke(4.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.sittingCat(t: Float, still: Boolean, cx: Float, by: Float, eyes: Eyes, earsDown: Boolean, pal: Palette, alpha: Float) {
    tail(cx + 10f, by - 2f, if (still) 0f else 6f * sin(t * 1.7f), true, pal, alpha)
    translate(cx, by) {
        drawPath(sitBody, pal.ink, alpha = alpha)
        drawPath(pawLine, pal.paper, alpha = 0.7f * alpha, style = Stroke(0.8f, cap = StrokeCap.Round))
        drawOval(pal.ink, Offset(-7.5f, -3.6f), Size(6.5f, 4f), alpha = alpha)
        drawOval(pal.ink, Offset(1f, -3.6f), Size(6.5f, 4f), alpha = alpha)
    }
    scarf(cx, by - 30f, if (still) 0f else 5f * sin(t * 2.2f), pal, alpha)
    head(cx, by - 40f, eyes, earsDown, eyes == Eyes.Open && !still && blinking(t), 0f, pal, alpha)
}

private val oarBlade = Path().apply {
    moveTo(229f, 210f); lineTo(218f, 222f); lineTo(212f, 218f); lineTo(224f, 206f); close()
}

private fun DrawScope.rowingCat(t: Float, still: Boolean, pal: Palette, alpha: Float) {
    val cx = 268f
    val by = 196f
    val stroke = if (still) 0f else sin(t * 3.6f)
    val oarPivot = Offset(246f, 190f)
    rotate(stroke * 5f, oarPivot) {
        drawLine(pal.ink, Offset(282f, 158f), Offset(222f, 220f), 2.4f, StrokeCap.Round, alpha = alpha)
        drawPath(oarBlade, pal.ink, alpha = alpha)
    }
    rotate(stroke * 2.5f, Offset(cx, by)) {
        tail(cx - 10f, by - 2f, if (still) 0f else 8f * sin(t * 3.6f + 1f), false, pal, alpha)
        translate(cx, by) { drawPath(sitBody, pal.ink, alpha = alpha) }
        scarf(cx, by - 30f, if (still) 0f else 8f * sin(t * 4.4f), pal, alpha)
        head(cx, by - 40f, Eyes.Open, false, !still && blinking(t), 1f, pal, alpha)
        hat(cx, by - 59f, -3f, pal, alpha)
        rotate(stroke * 5f, oarPivot) {
            val arm = Path().apply {
                moveTo(cx + 6f, by - 22f)
                quadraticTo(cx + 12f, by - 28f, cx + 14f, by - 36f)
            }
            drawPath(arm, pal.ink, alpha = alpha, style = Stroke(4.4f, cap = StrokeCap.Round))
            drawCircle(pal.ink, 3f, Offset(cx + 14f, by - 36.5f), alpha = alpha)
        }
    }
    if (!still) {
        for (i in 0..1) {
            val phase = (t * 0.9f + i * 0.5f) % 1f
            val r = 4f + 12f * phase
            drawOval(pal.ink, Offset(218f - r, 214f - r * 0.25f), Size(r * 2, r * 0.5f), alpha = 0.35f * (1f - phase) * alpha, style = Stroke(0.8f))
        }
    }
}

private val sleepBody = Path().apply {
    moveTo(-24f, 0f)
    cubicTo(-25f, -13f, -10f, -21f, 4f, -20f)
    cubicTo(18f, -19f, 25f, -10f, 23f, 0f)
    close()
}

private val sleepTail = Path().apply {
    moveTo(21f, -2f)
    cubicTo(10f, 1f, -8f, 1f, -17f, -1.5f)
}

private fun DrawScope.sleepingCat(t: Float, still: Boolean, pal: Palette, alpha: Float, measurer: TextMeasurer) {
    val cx = 292f
    val by = 198f
    val breath = if (still) 1f else 1f + 0.03f * sin(t * 1.6f)
    scale(1f, breath, Offset(cx, by)) {
        translate(cx, by) { drawPath(sleepBody, pal.ink, alpha = alpha) }
    }
    translate(cx, by) {
        drawPath(sleepTail, pal.paper, alpha = 0.55f * alpha, style = Stroke(0.8f, cap = StrokeCap.Round))
    }
    head(cx - 14f, by - 16f, Eyes.Sleep, false, false, 0f, pal, alpha)
    hat(cx + 12f, by - 24f, 14f, pal, alpha)
    val first = if (still) 0.3f else (t / 2.6f) % 1f
    glyph(measurer, "z", Offset(cx - 2f + 3f * first, by - 38f - 10f * first), 12f, pal.ink, 0.5f * alpha * (1f - first), italic = true)
    val second = if (still) 0.6f else ((t + 1.3f) / 2.6f) % 1f
    glyph(measurer, "z", Offset(cx + 7f + 3f * second, by - 47f - 10f * second), 9f, pal.ink, 0.35f * alpha * (1f - second), italic = true)
}

private fun DrawScope.lanternCat(t: Float, still: Boolean, pal: Palette, alpha: Float) {
    val cx = 284f
    val by = 198f
    sittingCat(t, still, cx, by, Eyes.Happy, false, pal, alpha)
    hat(cx, by - 59f, -4f, pal, alpha)
    val sway = if (still) 0f else 2.5f * sin(t * 1.1f)
    rotate(sway, Offset(cx + 14f, by - 1f)) {
        drawLine(pal.ink, Offset(cx + 14f, by - 1f), Offset(cx + 40f, by - 54f), 2f, StrokeCap.Round, alpha = alpha)
        lantern(t, still, cx + 40f, by - 54f, -sway, pal, alpha)
    }
    val arm = Path().apply {
        moveTo(cx + 6f, by - 24f)
        quadraticTo(cx + 14f, by - 24f, cx + 22.5f, by - 21f)
    }
    drawPath(arm, pal.ink, alpha = alpha, style = Stroke(4.4f, cap = StrokeCap.Round))
    drawCircle(pal.ink, 3f, Offset(cx + 23f, by - 21.5f), alpha = alpha)
}

private fun glow(t: Float, still: Boolean) = if (still) 1f else 0.85f + 0.15f * sin(t * 2.3f)

private fun DrawScope.lantern(t: Float, still: Boolean, x: Float, y: Float, counter: Float, pal: Palette, alpha: Float) {
    val glow = glow(t, still)
    rotate(counter + if (still) 0f else 3f * sin(t * 1.3f), Offset(x, y)) {
        drawCircle(pal.seal, 16f, Offset(x, y + 15f), alpha = 0.12f * glow * alpha)
        drawCircle(pal.seal, 10f, Offset(x, y + 15f), alpha = 0.16f * glow * alpha)
        drawLine(pal.ink, Offset(x, y), Offset(x, y + 6f), 1.1f, alpha = alpha)
        drawOval(pal.seal, Offset(x - 7f, y + 6f), Size(14f, 18f), alpha = alpha)
        for (dy in floatArrayOf(10.5f, 15f, 19.5f)) {
            drawLine(Color.Black, Offset(x - 6f, y + dy), Offset(x + 6f, y + dy), 0.8f, alpha = 0.2f * alpha)
        }
        drawRect(pal.ink, Offset(x - 2.5f, y + 5f), Size(5f, 1.8f), alpha = alpha)
        drawRect(pal.ink, Offset(x - 2.5f, y + 23.5f), Size(5f, 1.8f), alpha = alpha)
    }
}

private fun DrawScope.reflection(t: Float, still: Boolean, seal: Color, alpha: Float) {
    val glow = glow(t, still)
    for (i in 0..3) {
        val w = 9f - i * 1.8f
        val y = 207f + i * 3.2f
        val shift = if (still) 0f else 1.6f * sin(t * 2.6f + i)
        drawLine(seal, Offset(324f - w / 2 + shift, y), Offset(324f + w / 2 + shift, y), 1.1f, StrokeCap.Round, alpha = 0.4f * glow * alpha * (1f - i * 0.2f))
    }
}

private val canopy = Path().apply {
    moveTo(-34f, 15f)
    quadraticTo(-30f, -5f, 0f, -7f)
    quadraticTo(30f, -5f, 34f, 15f)
    for ((a, b) in listOf(34f to 17f, 17f to 0f, 0f to -17f, -17f to -34f)) quadraticTo((a + b) / 2, 10f, b, 15f)
    close()
}

private fun DrawScope.umbrellaCat(t: Float, still: Boolean, pal: Palette, alpha: Float) {
    val cx = 290f
    val by = 198f
    val top = Offset(cx, by - 68f)
    rotate(-12f + if (still) 0f else 1.5f * sin(t * 1.4f), Offset(cx, by - 20f)) {
        drawLine(pal.ink, top, Offset(cx, by - 20f), 1.6f, StrokeCap.Round, alpha = alpha)
        translate(top.x, top.y) {
            drawPath(canopy, pal.seal, alpha = alpha)
            for (dx in floatArrayOf(-17f, 0f, 17f)) {
                drawLine(pal.ink, Offset(0f, -6.5f), Offset(dx, 14.5f), 0.7f, alpha = 0.45f * alpha)
            }
            drawPath(canopy, pal.ink, alpha = 0.9f * alpha, style = Stroke(1f, join = StrokeJoin.Round))
            drawCircle(pal.ink, 1.8f, Offset(0f, -7.5f), alpha = alpha)
        }
    }
    val shiver = if (still || sin(t * 0.9f) <= 0.6f) 0f else 0.5f * sin(t * 20f)
    translate(shiver, 0f) { sittingCat(t, still, cx, by, Eyes.Sad, true, pal, alpha) }
}

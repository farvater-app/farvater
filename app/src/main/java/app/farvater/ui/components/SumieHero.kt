package app.farvater.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val W = 360f
private const val H = 236f
private const val CX = 180f
private const val CY = 100f
private const val RADIUS = 72f
private val A0 = (-52.0 * PI / 180.0).toFloat()
private val SWEEP = (-332.0 * PI / 180.0).toFloat()
private const val SOLID_END = 0.86f

@Composable
fun SumieHero(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val ink = c.label
    val paper = c.background
    val seal = if (c.dark) SumieSealNight else SumieSeal
    val straw = if (c.dark) Color(0xFF8C7B57) else Color(0xFFCDBB92)
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
    val enso = remember { Animatable(1f) }
    LaunchedEffect(mode, still) {
        when {
            painting && still -> enso.snapTo(0.62f)
            painting -> while (true) {
                enso.snapTo(0f)
                enso.animateTo(1f, tween(2400, easing = LinearOutSlowInEasing))
                delay(300)
            }
            mode == ConnectMode.Error -> enso.animateTo(0.6f, tween(450))
            else -> enso.animateTo(1f, tween(if (mode == ConnectMode.Connected) 700 else 400, easing = FastOutSlowInEasing))
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
            ConnectMode.Idle -> 0.24f
            ConnectMode.Error -> 0.72f
            else -> 0.94f
        },
        tween(500),
        label = "ensoAlpha",
    )
    val ensoColor by animateColorAsState(if (mode == ConnectMode.Error) seal else ink, tween(500), label = "ensoColor")
    val sleepPose by animateFloatAsState(if (mode == ConnectMode.Idle) 1f else 0f, tween(450), label = "sleep")
    val sitPose by animateFloatAsState(if (mode == ConnectMode.Connected) 1f else 0f, tween(450), label = "sit")
    val rain by animateFloatAsState(if (mode == ConnectMode.Error) 1f else 0f, tween(600), label = "rain")

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
            mist(ink)
            enso(enso.value, ensoColor, ensoAlpha, paper, painting)
            powerGlyph(ink, if (mode == ConnectMode.Idle) 0.45f else 0.9f)
            if (stamp.value > 0.01f) sealStamp(stamp.value, seal, paper, measurer)
            water(ink, if (still) 0f else 5f * sin(t * 0.9f))
            val rowing = painting || mode == ConnectMode.Error
            val bob = if (still) 0f else if (rowing) 2.4f * sin(t * 3.9f) else 1.4f * sin(t * 1.9f)
            val tilt = if (still) 0f else if (rowing) 1.2f * sin(t * 3.9f + 0.6f) else 0.6f * sin(t * 1.9f + 0.6f)
            translate(0f, bob) {
                rotate(tilt, pivot = Offset(280f, 195f)) {
                    boat(ink, paper)
                    val row = (1f - sleepPose - sitPose).coerceIn(0f, 1f)
                    if (sleepPose > 0.01f) sleepingCat(sleepPose, t, ink, paper, straw, seal, measurer)
                    if (row > 0.01f) rowingCat(row, t, mode == ConnectMode.Error, still, ink, paper, straw, seal)
                    if (sitPose > 0.01f) sittingCat(sitPose, t, still, ink, paper, straw, seal)
                }
            }
            if (rain > 0.01f) rain(rain, t, ink)
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
    t < 0.06f -> 10f + 5f * (t / 0.06f)
    t < 0.18f -> 15f - (t - 0.06f) / 0.12f
    t < SOLID_END -> 14f - 4.5f * ((t - 0.18f) / (SOLID_END - 0.18f))
    else -> 9.5f - 6f * ((t - SOLID_END) / (1f - SOLID_END))
}

private fun radiusAt(a: Float): Float = RADIUS + 1.1f * sin(a * 2f + 1.3f) + 0.5f * sin(a * 5.3f)

private fun ensoPoint(t: Float, offset: Float = 0f): Offset {
    val a = A0 + SWEEP * t
    val r = radiusAt(a) + offset
    return Offset(CX + r * cos(a), CY + r * sin(a))
}

private fun ensoTrack(from: Float, to: Float, offset: (Float) -> Float, steps: Int = 60): Path = Path().apply {
    for (i in 0..steps) {
        val t = from + (to - from) * i / steps
        val p = ensoPoint(t, offset(t))
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
}

private fun DrawScope.enso(progress: Float, color: Color, alpha: Float, paper: Color, painting: Boolean) {
    val solid = min(progress, SOLID_END)
    val steps = max(2, (200 * solid).toInt())
    val body = Path()
    for (i in 0..steps) {
        val t = solid * i / steps
        val p = ensoPoint(t, widthAt(t) / 2)
        if (i == 0) body.moveTo(p.x, p.y) else body.lineTo(p.x, p.y)
    }
    for (i in steps downTo 0) {
        val t = solid * i / steps
        val p = ensoPoint(t, -widthAt(t) / 2)
        body.lineTo(p.x, p.y)
    }
    body.close()
    drawPath(body, color, alpha = alpha)
    drawCircle(color, radius = widthAt(0f) / 2, center = ensoPoint(0f), alpha = alpha)

    listOf(-0.28f, 0.05f, 0.3f).forEachIndexed { i, f ->
        val from = 0.08f + 0.05f * i
        val to = solid - 0.02f * i
        if (to > from) {
            drawPath(
                ensoTrack(from, to, { t -> f * widthAt(t) }),
                paper,
                alpha = 0.16f + 0.06f * i,
                style = Stroke(0.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f + 7f * i, 3f + i))),
            )
        }
    }

    if (progress > SOLID_END - 0.01f) {
        val offsets = floatArrayOf(-3.4f, -1.2f, 1.1f, 3.3f)
        val ends = floatArrayOf(0.95f, 0.985f, 0.97f, 0.93f)
        val alphas = floatArrayOf(0.6f, 0.85f, 0.75f, 0.5f)
        offsets.forEachIndexed { i, off ->
            val end = min(progress, ends[i])
            if (end > SOLID_END - 0.01f) {
                drawPath(
                    ensoTrack(SOLID_END - 0.01f, end, { t -> off * widthAt(t) / 10.5f }, steps = 30),
                    color,
                    alpha = alpha * alphas[i],
                    style = Stroke(
                        width = if (i == 1) 2.6f else 1.9f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(if (i % 2 == 1) floatArrayOf(7f, 1.4f, 12f, 2f) else floatArrayOf(10f, 2.2f, 5f, 1.4f)),
                    ),
                )
            }
        }
    }
    if (painting && progress < 0.995f) {
        drawCircle(color, radius = widthAt(progress) / 2 + 1f, center = ensoPoint(progress), alpha = alpha)
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

private fun DrawScope.powerGlyph(ink: Color, alpha: Float) {
    drawPath(taper(180f, 70f, 180.4f, 96f, 5.2f, 1.6f), ink, alpha = alpha)
    val arc = Path()
    val outer = ArrayList<Offset>()
    val inner = ArrayList<Offset>()
    val from = (-58.0 * PI / 180.0).toFloat()
    val to = (238.0 * PI / 180.0).toFloat()
    for (i in 0..40) {
        val f = i / 40f
        val a = from + (to - from) * f
        val w = 1.4f + 3.8f * f
        outer += Offset(180f + (21f + w / 2) * cos(a), 99f + (21f + w / 2) * sin(a))
        inner += Offset(180f + (21f - w / 2) * cos(a), 99f + (21f - w / 2) * sin(a))
    }
    arc.moveTo(outer[0].x, outer[0].y)
    outer.forEach { arc.lineTo(it.x, it.y) }
    inner.asReversed().forEach { arc.lineTo(it.x, it.y) }
    arc.close()
    drawPath(arc, ink, alpha = alpha)
}

private fun DrawScope.mist(ink: Color) {
    val far = Path().apply {
        moveTo(-10f, 146f)
        cubicTo(20f, 128f, 44f, 118f, 66f, 124f)
        cubicTo(84f, 129f, 98f, 106f, 120f, 108f)
        cubicTo(144f, 110f, 156f, 134f, 186f, 132f)
        cubicTo(214f, 130f, 232f, 112f, 258f, 114f)
        cubicTo(284f, 116f, 300f, 132f, 330f, 126f)
        cubicTo(346f, 123f, 356f, 126f, 370f, 130f)
        lineTo(370f, 200f)
        lineTo(-10f, 200f)
        close()
    }
    drawPath(far, Brush.verticalGradient(listOf(ink.copy(alpha = 0.09f), Color.Transparent), startY = 106f, endY = 200f))
    val near = Path().apply {
        moveTo(-10f, 170f)
        cubicTo(26f, 160f, 48f, 146f, 80f, 150f)
        cubicTo(110f, 154f, 126f, 166f, 160f, 160f)
        cubicTo(200f, 152f, 226f, 140f, 262f, 144f)
        cubicTo(300f, 148f, 322f, 160f, 370f, 156f)
        lineTo(370f, 206f)
        lineTo(-10f, 206f)
        close()
    }
    drawPath(near, Brush.verticalGradient(listOf(ink.copy(alpha = 0.14f), Color.Transparent), startY = 140f, endY = 200f))
}

private fun DrawScope.water(ink: Color, shift: Float) {
    drawPath(taper(140f, 207f, 352f, 205f, 0.6f, 3.4f), ink, alpha = 0.5f)
    drawPath(taper(206f + shift, 214f, 306f + shift, 213f, 2.2f, 0.4f), ink, alpha = 0.32f)
    drawPath(taper(160f - shift, 220f, 232f - shift, 219f, 0.4f, 1.8f), ink, alpha = 0.22f)
}

private fun DrawScope.boat(ink: Color, paper: Color) {
    val hull = Path().apply {
        moveTo(206f, 187f)
        cubicTo(228f, 203f, 298f, 206f, 336f, 188f)
        cubicTo(343f, 184f, 347f, 178f, 352f, 175f)
        cubicTo(349f, 188f, 342f, 197f, 327f, 202f)
        cubicTo(292f, 212f, 234f, 210f, 206f, 187f)
        close()
    }
    drawPath(hull, ink)
    val rim = Path().apply {
        moveTo(214f, 190f)
        cubicTo(240f, 201f, 296f, 203f, 332f, 190f)
    }
    drawPath(rim, paper, alpha = 0.45f, style = Stroke(0.9f))
}

private fun DrawScope.sealStamp(progress: Float, seal: Color, paper: Color, measurer: androidx.compose.ui.text.TextMeasurer) {
    val pivot = Offset(238.5f, 44.5f)
    val s = 1.5f - 0.5f * progress
    rotate(-7f, pivot) {
        scale(s, s, pivot) {
            drawRoundRect(seal, Offset(225f, 31f), Size(27f, 27f), CornerRadius(3.5f), alpha = progress.coerceIn(0f, 1f))
            drawRoundRect(
                paper,
                Offset(227.5f, 33.5f),
                Size(22f, 22f),
                CornerRadius(2f),
                alpha = 0.35f * progress.coerceIn(0f, 1f),
                style = Stroke(0.8f),
            )
            glyph(measurer, "航", pivot, 16f, paper, progress.coerceIn(0f, 1f))
        }
    }
}

private fun DrawScope.glyph(
    measurer: androidx.compose.ui.text.TextMeasurer,
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

private fun DrawScope.hat(cx: Float, cy: Float, rotation: Float, ink: Color, straw: Color, alpha: Float) {
    rotate(rotation, Offset(cx, cy)) {
        val brim = Path().apply {
            moveTo(cx - 23f, cy + 4f)
            quadraticTo(cx, cy - 15f, cx + 23f, cy + 4f)
            quadraticTo(cx, cy, cx - 23f, cy + 4f)
            close()
        }
        drawPath(brim, straw, alpha = alpha)
        drawPath(brim, ink, alpha = alpha, style = Stroke(1.4f))
        val weave = Stroke(0.7f)
        drawLine(ink, Offset(cx, cy - 9f), Offset(cx - 12f, cy + 2f), weave.width, alpha = 0.55f * alpha)
        drawLine(ink, Offset(cx, cy - 9f), Offset(cx + 12f, cy + 2f), weave.width, alpha = 0.55f * alpha)
        drawLine(ink, Offset(cx, cy - 9f), Offset(cx, cy + 1.5f), weave.width, alpha = 0.55f * alpha)
    }
}

private enum class Eyes { Open, Happy, Sleep, Sad }

private fun DrawScope.head(cx: Float, cy: Float, eyes: Eyes, earsDown: Boolean, blink: Boolean, ink: Color, paper: Color, seal: Color, alpha: Float) {
    if (earsDown) {
        val ears = Path().apply {
            moveTo(cx - 10f, cy - 4f); lineTo(cx - 17f, cy - 6f); lineTo(cx - 8f, cy - 9f); close()
            moveTo(cx + 10f, cy - 4f); lineTo(cx + 17f, cy - 6f); lineTo(cx + 8f, cy - 9f); close()
        }
        drawPath(ears, ink, alpha = alpha)
    } else {
        val ears = Path().apply {
            moveTo(cx - 11f, cy - 3f); lineTo(cx - 11f, cy - 17f); lineTo(cx - 2f, cy - 9f); close()
            moveTo(cx + 11f, cy - 3f); lineTo(cx + 11f, cy - 17f); lineTo(cx + 2f, cy - 9f); close()
        }
        drawPath(ears, ink, alpha = alpha)
        val inner = Path().apply {
            moveTo(cx - 9f, cy - 6f); lineTo(cx - 9f, cy - 12f); lineTo(cx - 5f, cy - 8f); close()
            moveTo(cx + 9f, cy - 6f); lineTo(cx + 9f, cy - 12f); lineTo(cx + 5f, cy - 8f); close()
        }
        drawPath(inner, seal, alpha = 0.55f * alpha)
    }
    drawOval(ink, Offset(cx - 12.5f, cy - 11f), Size(25f, 22f), alpha = alpha)
    val line = Stroke(1.5f, cap = StrokeCap.Round)
    when (eyes) {
        Eyes.Open -> {
            val ry = if (blink) 0.4f else 2.1f
            drawOval(paper, Offset(cx - 6.2f, cy + 1f - ry), Size(3.4f, ry * 2), alpha = alpha)
            drawOval(paper, Offset(cx + 2.8f, cy + 1f - ry), Size(3.4f, ry * 2), alpha = alpha)
        }
        Eyes.Happy, Eyes.Sleep -> {
            val lift = if (eyes == Eyes.Happy) -3.2f else 2.6f
            val y = if (eyes == Eyes.Happy) cy + 2f else cy + 1f
            val arcs = Path().apply {
                moveTo(cx - 7f, y); quadraticTo(cx - 4.5f, y + lift, cx - 2f, y)
                moveTo(cx + 2f, y); quadraticTo(cx + 4.5f, y + lift, cx + 7f, y)
            }
            drawPath(arcs, paper, alpha = alpha, style = line)
        }
        Eyes.Sad -> {
            drawLine(paper, Offset(cx - 7f, cy + 0.5f), Offset(cx - 2.5f, cy + 2.1f), 1.5f, StrokeCap.Round, alpha = alpha)
            drawLine(paper, Offset(cx + 7f, cy + 0.5f), Offset(cx + 2.5f, cy + 2.1f), 1.5f, StrokeCap.Round, alpha = alpha)
        }
    }
    val whisker = 0.7f
    drawLine(ink, Offset(cx - 8f, cy + 5f), Offset(cx - 17f, cy + 4f), whisker, alpha = 0.7f * alpha)
    drawLine(ink, Offset(cx - 8f, cy + 6.5f), Offset(cx - 16f, cy + 8.5f), whisker, alpha = 0.7f * alpha)
    drawLine(ink, Offset(cx + 8f, cy + 5f), Offset(cx + 17f, cy + 4f), whisker, alpha = 0.7f * alpha)
    drawLine(ink, Offset(cx + 8f, cy + 6.5f), Offset(cx + 16f, cy + 8.5f), whisker, alpha = 0.7f * alpha)
}

private fun DrawScope.scarf(cx: Float, cy: Float, seal: Color, alpha: Float) {
    val band = Path().apply {
        moveTo(cx - 9f, cy)
        quadraticTo(cx, cy + 5f, cx + 9f, cy)
        lineTo(cx + 8f, cy + 4f)
        quadraticTo(cx, cy + 9f, cx - 8f, cy + 4f)
        close()
        moveTo(cx + 4f, cy + 6f); lineTo(cx + 9f, cy + 13f); lineTo(cx + 12f, cy + 9f); close()
    }
    drawPath(band, seal, alpha = alpha)
}

private fun blinking(t: Float): Boolean = (t % 4.3f) > 4.15f

private fun DrawScope.rowingCat(alpha: Float, t: Float, sad: Boolean, still: Boolean, ink: Color, paper: Color, straw: Color, seal: Color) {
    val body = Path().apply {
        moveTo(232f, 188f)
        cubicTo(229f, 170f, 233f, 156f, 246f, 152f)
        cubicTo(259f, 156f, 263f, 170f, 260f, 188f)
        close()
    }
    drawPath(body, ink, alpha = alpha)
    val tail = Path().apply {
        moveTo(231f, 186f)
        cubicTo(224f, 192f, 218f, 192f, 214f, 187f)
    }
    drawPath(tail, ink, alpha = alpha, style = Stroke(4.2f, cap = StrokeCap.Round))
    scarf(246f, 150f, seal, alpha)
    head(246f, 140f, if (sad) Eyes.Sad else Eyes.Open, sad, blinking(t), ink, paper, seal, alpha)
    hat(246f, 128f, -4f, ink, straw, alpha)
    val sweep = if (still) 0f else 9f * sin(t * 3.9f)
    rotate((if (sad) 4f else 0f) + sweep, Offset(268.5f, 153f)) {
        drawLine(ink, Offset(266f, 146f), Offset(314f, 214f), 2.6f, StrokeCap.Round, alpha = alpha)
        val blade = Path().apply {
            moveTo(308f, 205f); lineTo(321f, 216f); lineTo(315f, 221f); lineTo(303f, 211f); close()
        }
        drawPath(blade, ink, alpha = alpha)
    }
    val arm = Path().apply {
        moveTo(256f, 160f)
        cubicTo(262f, 158f, 266f, 156f, 268f, 153f)
    }
    drawPath(arm, ink, alpha = alpha, style = Stroke(4.6f, cap = StrokeCap.Round))
    drawCircle(ink, 3f, Offset(268.5f, 153f), alpha = alpha)
}

private fun DrawScope.sittingCat(alpha: Float, t: Float, still: Boolean, ink: Color, paper: Color, straw: Color, seal: Color) {
    val body = Path().apply {
        moveTo(233f, 189f)
        cubicTo(224f, 178f, 228f, 160f, 244f, 156f)
        cubicTo(259f, 160f, 264f, 177f, 257f, 189f)
        close()
    }
    drawPath(body, ink, alpha = alpha)
    rotate(if (still) 0f else 6f * sin(t * 1.7f), Offset(257f, 187f)) {
        val tail = Path().apply {
            moveTo(256f, 187f)
            cubicTo(270f, 189f, 276f, 179f, 271f, 170f)
            cubicTo(269f, 166f, 266f, 166f, 265f, 169f)
        }
        drawPath(tail, ink, alpha = alpha, style = Stroke(4.2f, cap = StrokeCap.Round))
    }
    scarf(244f, 153f, seal, alpha)
    head(244f, 143f, Eyes.Happy, false, false, ink, paper, seal, alpha)
    hat(244f, 131f, -3f, ink, straw, alpha)

    drawLine(ink, Offset(320f, 188f), Offset(328f, 149f), 2f, alpha = alpha)
    drawLine(ink, Offset(328f, 149f), Offset(341f, 151f), 1.5f, alpha = alpha)
    val glow = if (still) 1f else 0.85f + 0.15f * sin(t * 2.3f)
    rotate(if (still) 0f else 3f * sin(t * 1.3f), Offset(341f, 151f)) {
        drawCircle(seal, 19f, Offset(341f, 166f), alpha = 0.14f * glow * alpha)
        drawCircle(seal, 11f, Offset(341f, 166f), alpha = 0.18f * glow * alpha)
        drawLine(ink, Offset(341f, 151f), Offset(341f, 157f), 1.1f, alpha = alpha)
        drawOval(seal, Offset(334f, 157f), Size(14f, 18f), alpha = alpha)
        listOf(161.5f, 166f, 170.5f).forEach { y ->
            drawLine(Color.Black, Offset(335f, y), Offset(347f, y), 0.8f, alpha = 0.2f * alpha)
        }
        drawRect(ink, Offset(338.5f, 156f), Size(5f, 1.8f), alpha = alpha)
        drawRect(ink, Offset(338.5f, 174.5f), Size(5f, 1.8f), alpha = alpha)
    }
}

private fun DrawScope.sleepingCat(
    alpha: Float,
    t: Float,
    ink: Color,
    paper: Color,
    straw: Color,
    seal: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val breath = 1f + 0.025f * sin(t * 1.6f)
    scale(1f, breath, Offset(250f, 189f)) {
        val body = Path().apply {
            moveTo(226f, 189f)
            cubicTo(224f, 175f, 238f, 167f, 256f, 168f)
            cubicTo(273f, 169f, 280f, 181f, 275f, 189f)
            close()
        }
        drawPath(body, ink, alpha = alpha)
    }
    val tail = Path().apply {
        moveTo(274f, 187f)
        cubicTo(285f, 187f, 289f, 180f, 283f, 173f)
    }
    drawPath(tail, ink, alpha = alpha, style = Stroke(4.2f, cap = StrokeCap.Round))
    head(236f, 177f, Eyes.Sleep, false, false, ink, paper, seal, alpha)
    hat(236f, 163f, -16f, ink, straw, alpha)
    val phase = (t / 2.6f) % 1f
    glyph(measurer, "z", Offset(258f, 146f - 10f * phase), 13f, ink, alpha * 0.5f * (1f - phase), italic = true)
    val phase2 = ((t + 1.3f) / 2.6f) % 1f
    glyph(measurer, "z", Offset(268f, 134f - 10f * phase2), 10f, ink, alpha * 0.35f * (1f - phase2), italic = true)
}

private fun DrawScope.rain(alpha: Float, t: Float, ink: Color) {
    for (i in 0 until 30) {
        val x = ((i * 41 + 13) % 370).toFloat()
        val y = ((i * 67 + t * 160f) % 250f) - 10f
        drawPath(taper(x, y, x - 5f, y + 15f, 0.2f, 1.2f), ink, alpha = 0.28f * alpha)
    }
}

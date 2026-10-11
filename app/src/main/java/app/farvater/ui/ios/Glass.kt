package app.farvater.ui.ios

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.farvater.ui.theme.Ios
import kotlin.math.ceil

@Stable
class GlassBackdrop internal constructor() {
    internal var layer: GraphicsLayer? by mutableStateOf(null)
    internal var coordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop = remember { GlassBackdrop() }

var powerSave by mutableStateOf(false)

val glassSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !powerSave

fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = composed {
    val layer = rememberGraphicsLayer()
    DisposableEffect(backdrop, layer) {
        backdrop.layer = layer
        onDispose {
            if (backdrop.layer === layer) {
                backdrop.layer = null
                backdrop.coordinates = null
            }
        }
    }
    onGloballyPositioned { backdrop.coordinates = it }
        .drawWithContent {
            if (glassSupported) {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            } else {
                drawContent()
            }
        }
}

@Immutable
data class GlassStyle(
    val blur: Dp = 8.dp,
    val lensHeight: Dp = 0.dp,
    val lensAmount: Dp = 0.dp,
    val saturation: Float = 1.6f,
    val rim: Boolean = true,
    val elevation: Dp = 0.dp,
)

object GlassStyles {
    val Bar = GlassStyle(blur = 6.dp, lensHeight = 18.dp, lensAmount = 26.dp, elevation = 14.dp)
    val Chrome = GlassStyle(blur = 22.dp, saturation = 1.8f, rim = false)
    val Toast = GlassStyle(blur = 12.dp, lensHeight = 10.dp, lensAmount = 14.dp, elevation = 10.dp)
    val Control = GlassStyle(blur = 0.dp)
}

fun Modifier.liquidGlass(
    backdrop: GlassBackdrop?,
    shape: Shape,
    style: GlassStyle = GlassStyles.Bar,
    tint: Color = Color.Unspecified,
    fallback: Color = Color.Unspecified,
): Modifier = composed {
    val c = Ios.colors
    val live = backdrop != null && glassSupported
    val layer = if (live) rememberGraphicsLayer() else null
    var own by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
    val surface = if (tint.isSpecified) tint else glassTint(c.dark)
    val solid = if (fallback.isSpecified) fallback else c.bar
    val rim = glassRim(c.dark)
    val sheen = if (c.dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.28f)
    val shadowColor = Color.Black.copy(alpha = if (c.dark) 0.5f else 0.26f)

    this
        .then(
            if (style.elevation > 0.dp) {
                Modifier.shadow(style.elevation, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
            } else {
                Modifier
            },
        )
        .onGloballyPositioned { own = it }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            val pad = ceil(style.blur.toPx()).toInt()
            if (layer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                layer.renderEffect = runCatching {
                    glassEffect(
                        size = size,
                        origin = Offset(pad.toFloat(), pad.toFloat()),
                        radius = cornerRadius(shape, size, this),
                        blur = style.blur.toPx(),
                        saturation = style.saturation,
                        lensHeight = style.lensHeight.toPx(),
                        lensAmount = style.lensAmount.toPx(),
                    ).asComposeRenderEffect()
                }.getOrNull()
            }
            val rimBrush = Brush.linearGradient(rim, start = Offset.Zero, end = Offset(size.width, size.height))
            val sheenBrush = Brush.verticalGradient(listOf(sheen, Color.Transparent), endY = size.height * 0.55f)
            val stroke = Stroke(width = 1.dp.toPx())
            onDrawBehind {
                val source = backdrop?.layer
                val from = backdrop?.coordinates
                val to = own
                clipPath(path) {
                    if (layer != null && source != null && from != null && to != null &&
                        from.isAttached && to.isAttached && size.width >= 1f && size.height >= 1f
                    ) {
                        val offset = from.localPositionOf(to, Offset.Zero)
                        layer.record(IntSize(size.width.toInt() + pad * 2, size.height.toInt() + pad * 2)) {
                            translate(pad - offset.x, pad - offset.y) { drawLayer(source) }
                        }
                        layer.topLeft = IntOffset(-pad, -pad)
                        drawLayer(layer)
                        drawRect(surface)
                    } else {
                        drawRect(solid)
                    }
                    drawRect(sheenBrush)
                }
                if (style.rim) drawOutline(outline, rimBrush, style = stroke)
            }
        }
}

fun glassTint(dark: Boolean): Color =
    if (dark) Color(0xFF1C1C1E).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.66f)

fun glassControlFill(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.92f)

fun glassRim(dark: Boolean): List<Color> =
    if (dark) {
        listOf(
            Color.White.copy(alpha = 0.42f),
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.04f),
            Color.White.copy(alpha = 0.24f),
        )
    } else {
        listOf(
            Color.White,
            Color.White.copy(alpha = 0.55f),
            Color.Black.copy(alpha = 0.05f),
            Color.Black.copy(alpha = 0.09f),
        )
    }

private fun cornerRadius(shape: Shape, size: Size, density: Density): Float = when (shape) {
    is CornerBasedShape -> shape.topStart.toPx(size, density).coerceAtMost(size.minDimension / 2f)
    else -> 0f
}

@RequiresApi(Build.VERSION_CODES.S)
private fun glassEffect(
    size: Size,
    origin: Offset,
    radius: Float,
    blur: Float,
    saturation: Float,
    lensHeight: Float,
    lensAmount: Float,
): RenderEffect {
    val matrix = ColorMatrix().apply { setSaturation(saturation) }
    var effect = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
    if (blur > 0f) {
        effect = RenderEffect.createChainEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP), effect)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && lensHeight > 0f && lensAmount > 0f) {
        effect = RenderEffect.createChainEffect(lensEffect(size, origin, radius, lensHeight, lensAmount), effect)
    }
    return effect
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun lensEffect(size: Size, origin: Offset, radius: Float, height: Float, amount: Float): RenderEffect {
    val shader = RuntimeShader(LensShader).apply {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("origin", origin.x, origin.y)
        setFloatUniform("radius", radius)
        setFloatUniform("height", height.coerceAtMost(size.minDimension / 2f))
        setFloatUniform("amount", amount)
    }
    return RenderEffect.createRuntimeShaderEffect(shader, "content")
}

private const val LensShader = """
uniform shader content;
uniform float2 size;
uniform float2 origin;
uniform float radius;
uniform float height;
uniform float amount;

float box(float2 p, float2 h, float r) {
    float2 q = abs(p) - h + float2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    float2 h = size * 0.5;
    float2 p = coord - origin - h;
    float inner = -box(p, h, radius);
    if (inner <= 0.0 || inner >= height) {
        return content.eval(coord);
    }
    float dx = box(p + float2(1.0, 0.0), h, radius) - box(p - float2(1.0, 0.0), h, radius);
    float dy = box(p + float2(0.0, 1.0), h, radius) - box(p - float2(0.0, 1.0), h, radius);
    float2 n = float2(dx, dy);
    float len = length(n);
    if (len < 0.0001) {
        return content.eval(coord);
    }
    n = n / len;
    float t = 1.0 - inner / height;
    float k = 1.0 - sqrt(max(1.0 - t * t, 0.0));
    return content.eval(coord - n * k * amount);
}
"""

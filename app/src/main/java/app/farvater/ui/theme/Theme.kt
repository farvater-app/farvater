package app.farvater.ui.theme

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.data.UiStyle

@Immutable
data class IosColors(
    val dark: Boolean,
    val background: Color,
    val cell: Color,
    val cellRaised: Color,
    val fill: Color,
    val highlight: Color,
    val segmentThumb: Color,
    val bar: Color,
    val barBorder: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val tint: Color,
    val green: Color,
    val red: Color,
    val blue: Color,
    val teal: Color,
    val indigo: Color,
    val purple: Color,
    val gray: Color,
    val yellow: Color,
    val pink: Color,
)

val IosDark = IosColors(
    dark = true,
    background = Color(0xFF000000),
    cell = Color(0xFF1C1C1E),
    cellRaised = Color(0xFF2C2C2E),
    fill = Color(0xFF39393D),
    highlight = Color(0xFF3A3A3C),
    segmentThumb = Color(0xFF636366),
    bar = Color(0xF71C1C1E),
    barBorder = Color(0x1FFFFFFF),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFF98989F),
    tertiaryLabel = Color(0xFF5A5A5F),
    separator = Color(0xFF38383A),
    tint = Color(0xFFFF9F0A),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    blue = Color(0xFF0A84FF),
    teal = Color(0xFF40C8E0),
    indigo = Color(0xFF5E5CE6),
    purple = Color(0xFFBF5AF2),
    gray = Color(0xFF8E8E93),
    yellow = Color(0xFFFFD60A),
    pink = Color(0xFFFF375F),
)

val IosLight = IosColors(
    dark = false,
    background = Color(0xFFF2F2F7),
    cell = Color(0xFFFFFFFF),
    cellRaised = Color(0xFFFFFFFF),
    fill = Color(0xFFE3E3E8),
    highlight = Color(0xFFD1D1D6),
    segmentThumb = Color(0xFFFFFFFF),
    bar = Color(0xF7FBFBFD),
    barBorder = Color(0x14000000),
    label = Color(0xFF000000),
    secondaryLabel = Color(0xFF8A8A8E),
    tertiaryLabel = Color(0xFFC4C4C7),
    separator = Color(0xFFC6C6C8),
    tint = Color(0xFFFF9500),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    blue = Color(0xFF007AFF),
    teal = Color(0xFF30B0C7),
    indigo = Color(0xFF5856D6),
    purple = Color(0xFFAF52DE),
    gray = Color(0xFF8E8E93),
    yellow = Color(0xFFFFCC00),
    pink = Color(0xFFFF2D55),
)

val LocalIos = staticCompositionLocalOf { IosDark }

val LocalUiStyle = staticCompositionLocalOf { UiStyle.IOS }

object IosType {
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    val title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    val title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.15).sp)
    val subheadline = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.05).sp)
    val caption1 = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.05.sp)
}

object Ios {
    val colors: IosColors
        @Composable @ReadOnlyComposable get() = LocalIos.current

    val material: Boolean
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current != UiStyle.IOS
}

private val OrangeLight = lightColorScheme(
    primary = Color(0xFF855400), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB8), onPrimaryContainer = Color(0xFF2A1700),
    secondary = Color(0xFF715A41), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCDDBD), onSecondaryContainer = Color(0xFF281805),
    tertiary = Color(0xFF53643E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E9B9), onTertiaryContainer = Color(0xFF121F03),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F4), onBackground = Color(0xFF201B16),
    surface = Color(0xFFFFF8F4), onSurface = Color(0xFF201B16),
    surfaceVariant = Color(0xFFF0E0D0), onSurfaceVariant = Color(0xFF50453A),
    outline = Color(0xFF827568), outlineVariant = Color(0xFFD4C4B5),
    inverseSurface = Color(0xFF362F2A), inverseOnSurface = Color(0xFFFBEEE5), inversePrimary = Color(0xFFFFB95F),
    surfaceTint = Color(0xFF855400), scrim = Color.Black,
    surfaceDim = Color(0xFFE4D8CF), surfaceBright = Color(0xFFFFF8F4),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFEF1E8),
    surfaceContainer = Color(0xFFF8EBE2), surfaceContainerHigh = Color(0xFFF3E6DC),
    surfaceContainerHighest = Color(0xFFEDE0D7),
)

private val OrangeDark = darkColorScheme(
    primary = Color(0xFFFFB95F), onPrimary = Color(0xFF462A00),
    primaryContainer = Color(0xFF653E00), onPrimaryContainer = Color(0xFFFFDDB8),
    secondary = Color(0xFFDFC2A2), onSecondary = Color(0xFF3F2D17),
    secondaryContainer = Color(0xFF58432B), onSecondaryContainer = Color(0xFFFCDDBD),
    tertiary = Color(0xFFBACD9F), onTertiary = Color(0xFF263514),
    tertiaryContainer = Color(0xFF3C4C28), onTertiaryContainer = Color(0xFFD6E9B9),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF18120C), onBackground = Color(0xFFEDE0D7),
    surface = Color(0xFF18120C), onSurface = Color(0xFFEDE0D7),
    surfaceVariant = Color(0xFF50453A), onSurfaceVariant = Color(0xFFD4C4B5),
    outline = Color(0xFF9C8E80), outlineVariant = Color(0xFF50453A),
    inverseSurface = Color(0xFFEDE0D7), inverseOnSurface = Color(0xFF362F2A), inversePrimary = Color(0xFF855400),
    surfaceTint = Color(0xFFFFB95F), scrim = Color.Black,
    surfaceDim = Color(0xFF18120C), surfaceBright = Color(0xFF3F3731),
    surfaceContainerLowest = Color(0xFF120D08), surfaceContainerLow = Color(0xFF201B16),
    surfaceContainer = Color(0xFF251F1A), surfaceContainerHigh = Color(0xFF302924),
    surfaceContainerHighest = Color(0xFF3B342E),
)

val RkpBlue = Color(0xFF3FC4FF)

private val RkpDark = darkColorScheme(
    primary = RkpBlue, onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF004C69), onPrimaryContainer = Color(0xFFC4E7FF),
    secondary = Color(0xFFB5C9D7), onSecondary = Color(0xFF1F333E),
    secondaryContainer = Color(0xFF364955), onSecondaryContainer = Color(0xFFD1E5F4),
    tertiary = Color(0xFFC9C1EA), onTertiary = Color(0xFF312C4C),
    tertiaryContainer = Color(0xFF484264), onTertiaryContainer = Color(0xFFE6DEFF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1E1F23), onBackground = Color(0xFFE3E3E6),
    surface = Color(0xFF1E1F23), onSurface = Color(0xFFE3E3E6),
    surfaceVariant = Color(0xFF41474D), onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9198), outlineVariant = Color(0xFF41474D),
    inverseSurface = Color(0xFFE3E3E6), inverseOnSurface = Color(0xFF2E3034), inversePrimary = Color(0xFF00658B),
    surfaceTint = RkpBlue, scrim = Color.Black,
    surfaceDim = Color(0xFF1E1F23), surfaceBright = Color(0xFF3B3D42),
    surfaceContainerLowest = Color(0xFF18191C), surfaceContainerLow = Color(0xFF232428),
    surfaceContainer = Color(0xFF27292D), surfaceContainerHigh = Color(0xFF313338),
    surfaceContainerHighest = Color(0xFF3B3D42),
)

val SumieSeal = Color(0xFFB23A30)
val SumieSealNight = Color(0xFFD4574A)

private val SumieLight = lightColorScheme(
    primary = Color(0xFFA9322A), onPrimary = Color(0xFFFFF8F0),
    primaryContainer = Color(0xFFF2D9CF), onPrimaryContainer = Color(0xFF3E0D08),
    secondary = Color(0xFF5E5A52), onSecondary = Color(0xFFFBF8F1),
    secondaryContainer = Color(0xFFE2DCCD), onSecondaryContainer = Color(0xFF1F1D1A),
    tertiary = Color(0xFF3F6A4E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5E5D3), onTertiaryContainer = Color(0xFF0E2015),
    error = Color(0xFFA9322A), onError = Color.White,
    errorContainer = Color(0xFFF2D9CF), onErrorContainer = Color(0xFF3E0D08),
    background = Color(0xFFEFEADF), onBackground = Color(0xFF1F1D1A),
    surface = Color(0xFFEFEADF), onSurface = Color(0xFF1F1D1A),
    surfaceVariant = Color(0xFFE2DCCD), onSurfaceVariant = Color(0xFF55504A),
    outline = Color(0xFF8A8378), outlineVariant = Color(0xFFD3CBBB),
    inverseSurface = Color(0xFF2E2B27), inverseOnSurface = Color(0xFFF4EFE5), inversePrimary = Color(0xFFF0A093),
    surfaceTint = Color.Transparent, scrim = Color.Black,
    surfaceDim = Color(0xFFDDD7CA), surfaceBright = Color(0xFFF7F3EA),
    surfaceContainerLowest = Color(0xFFF8F5EE), surfaceContainerLow = Color(0xFFF3EEE4),
    surfaceContainer = Color(0xFFEBE5D8), surfaceContainerHigh = Color(0xFFE5DFD1),
    surfaceContainerHighest = Color(0xFFDFD8C9),
)

private val SumieDark = darkColorScheme(
    primary = Color(0xFFE0705F), onPrimary = Color(0xFF3B0D07),
    primaryContainer = Color(0xFF5B1C15), onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFFCCC4B4), onSecondary = Color(0xFF33302A),
    secondaryContainer = Color(0xFF3A3731), onSecondaryContainer = Color(0xFFEDE6D7),
    tertiary = Color(0xFF9CC7A6), onTertiary = Color(0xFF0E2A17),
    tertiaryContainer = Color(0xFF26402E), onTertiaryContainer = Color(0xFFCDE9D2),
    error = Color(0xFFE0705F), onError = Color(0xFF3B0D07),
    errorContainer = Color(0xFF5B1C15), onErrorContainer = Color(0xFFFFDAD3),
    background = Color(0xFF151412), onBackground = Color(0xFFEAE3D5),
    surface = Color(0xFF151412), onSurface = Color(0xFFEAE3D5),
    surfaceVariant = Color(0xFF3A3731), onSurfaceVariant = Color(0xFFC2BAAB),
    outline = Color(0xFF8E877B), outlineVariant = Color(0xFF3A3731),
    inverseSurface = Color(0xFFEAE3D5), inverseOnSurface = Color(0xFF2B2925), inversePrimary = Color(0xFFA9322A),
    surfaceTint = Color.Transparent, scrim = Color.Black,
    surfaceDim = Color(0xFF151412), surfaceBright = Color(0xFF3A3731),
    surfaceContainerLowest = Color(0xFF100F0D), surfaceContainerLow = Color(0xFF1C1B18),
    surfaceContainer = Color(0xFF201E1B), surfaceContainerHigh = Color(0xFF2A2824),
    surfaceContainerHighest = Color(0xFF35322D),
)

private fun sumieColors(s: ColorScheme, dark: Boolean): IosColors = materialColors(s, dark).copy(
    green = if (dark) Color(0xFF8FC29C) else Color(0xFF3F6A4E),
    red = if (dark) Color(0xFFE0705F) else Color(0xFFA9322A),
    blue = if (dark) Color(0xFF9DB6D3) else Color(0xFF3D5A78),
    teal = if (dark) Color(0xFF8CC2BD) else Color(0xFF3B6B68),
    indigo = if (dark) Color(0xFFAEB2DE) else Color(0xFF4A4E78),
    purple = if (dark) Color(0xFFCBA9CB) else Color(0xFF6B4A6B),
    yellow = if (dark) Color(0xFFD8B565) else Color(0xFF94701F),
    pink = if (dark) Color(0xFFE3A1B0) else Color(0xFF9C4A5C),
)

private fun materialColors(s: ColorScheme, dark: Boolean): IosColors {
    val cell = if (dark) s.surfaceContainerHigh else s.surfaceContainerLowest
    return IosColors(
        dark = dark,
        background = if (dark) s.surface else s.surfaceContainer,
        cell = cell,
        cellRaised = s.surfaceContainerHighest,
        fill = s.surfaceContainerHighest,
        highlight = s.onSurface.copy(alpha = 0.1f).compositeOver(cell),
        segmentThumb = s.secondaryContainer,
        bar = s.surfaceContainer,
        barBorder = s.outlineVariant,
        label = s.onSurface,
        secondaryLabel = s.onSurfaceVariant,
        tertiaryLabel = s.outline,
        separator = s.outlineVariant,
        tint = s.primary,
        green = if (dark) Color(0xFF6DD58C) else Color(0xFF146C2E),
        red = s.error,
        blue = if (dark) Color(0xFFA8C7FA) else Color(0xFF0B57D0),
        teal = if (dark) Color(0xFF4FD8EB) else Color(0xFF006A6A),
        indigo = if (dark) Color(0xFFBAC3FF) else Color(0xFF4355B9),
        purple = if (dark) Color(0xFFD0BCFF) else Color(0xFF6750A4),
        gray = s.outline,
        yellow = if (dark) Color(0xFFFDD663) else Color(0xFF7C5800),
        pink = if (dark) Color(0xFFFFB0CD) else Color(0xFFA0306A),
    )
}

private fun scheme(c: IosColors) = if (c.dark) {
    darkColorScheme(
        primary = c.tint, onPrimary = Color.White,
        primaryContainer = Color(0xFF3D2A0B), onPrimaryContainer = Color(0xFFFFD9A8),
        secondary = c.gray, onSecondary = Color.White,
        secondaryContainer = c.cellRaised, onSecondaryContainer = c.label,
        tertiary = c.green, onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF0E3A1A), onTertiaryContainer = Color(0xFFB7F5C6),
        background = c.background, onBackground = c.label,
        surface = c.background, onSurface = c.label,
        surfaceVariant = c.cellRaised, onSurfaceVariant = c.secondaryLabel,
        surfaceTint = Color.Transparent,
        inverseSurface = c.label, inverseOnSurface = c.cell, inversePrimary = c.tint,
        error = c.red, onError = Color.White,
        errorContainer = Color(0xFF4A1512), onErrorContainer = Color(0xFFFFDAD6),
        outline = c.gray, outlineVariant = c.separator, scrim = Color.Black,
        surfaceBright = c.cellRaised, surfaceDim = c.background,
        surfaceContainerLowest = c.background, surfaceContainerLow = c.cell,
        surfaceContainer = c.cell, surfaceContainerHigh = c.cellRaised, surfaceContainerHighest = c.fill,
    )
} else {
    lightColorScheme(
        primary = c.tint, onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE8CC), onPrimaryContainer = Color(0xFF3A2200),
        secondary = c.gray, onSecondary = Color.White,
        secondaryContainer = c.fill, onSecondaryContainer = c.label,
        tertiary = c.green, onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD3F5DC), onTertiaryContainer = Color(0xFF00210A),
        background = c.background, onBackground = c.label,
        surface = c.background, onSurface = c.label,
        surfaceVariant = c.fill, onSurfaceVariant = c.secondaryLabel,
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFF1C1C1E), inverseOnSurface = Color.White, inversePrimary = c.tint,
        error = c.red, onError = Color.White,
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        outline = c.gray, outlineVariant = c.separator, scrim = Color.Black,
        surfaceBright = c.cell, surfaceDim = c.fill,
        surfaceContainerLowest = c.cell, surfaceContainerLow = c.cell,
        surfaceContainer = c.cell, surfaceContainerHigh = c.cell, surfaceContainerHighest = c.fill,
    )
}

private val Type = Typography(
    displaySmall = IosType.largeTitle,
    headlineLarge = IosType.largeTitle,
    headlineMedium = IosType.title1,
    headlineSmall = IosType.title2,
    titleLarge = IosType.title3,
    titleMedium = IosType.headline,
    titleSmall = IosType.subheadline.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = IosType.body,
    bodyMedium = IosType.subheadline,
    bodySmall = IosType.footnote,
    labelLarge = IosType.headline,
    labelMedium = IosType.caption1.copy(fontWeight = FontWeight.Medium),
    labelSmall = IosType.caption2.copy(fontWeight = FontWeight.Medium),
)

private val MaterialType = Typography()

private val SumieType = MaterialType.copy(
    displayLarge = MaterialType.displayLarge.copy(fontFamily = FontFamily.Serif),
    displayMedium = MaterialType.displayMedium.copy(fontFamily = FontFamily.Serif),
    displaySmall = MaterialType.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = MaterialType.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = MaterialType.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = MaterialType.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
    titleLarge = MaterialType.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
)

private val SumieShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

private val M3Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

object IosMotion {
    const val SoftDamping = 0.86f
    const val SoftStiffness = 300f
    const val SnappyDamping = 0.7f
    const val SnappyStiffness = 520f
}

val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private val ThemeFade = tween<Float>(durationMillis = 350, easing = EaseInOut)

@Composable
private fun <T> fadeTo(target: T, lerp: (T, T, Float) -> T): T {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val shown = if (progress.value < 1f) lerp(from, to, progress.value) else to
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = shown
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, ThemeFade)
    }
    return shown
}

private fun IosColors.lerpTo(o: IosColors, t: Float) = IosColors(
    dark = if (t < 0.5f) dark else o.dark,
    background = lerp(background, o.background, t), cell = lerp(cell, o.cell, t),
    cellRaised = lerp(cellRaised, o.cellRaised, t), fill = lerp(fill, o.fill, t),
    highlight = lerp(highlight, o.highlight, t), segmentThumb = lerp(segmentThumb, o.segmentThumb, t),
    bar = lerp(bar, o.bar, t), barBorder = lerp(barBorder, o.barBorder, t), label = lerp(label, o.label, t),
    secondaryLabel = lerp(secondaryLabel, o.secondaryLabel, t), tertiaryLabel = lerp(tertiaryLabel, o.tertiaryLabel, t),
    separator = lerp(separator, o.separator, t), tint = lerp(tint, o.tint, t), green = lerp(green, o.green, t),
    red = lerp(red, o.red, t), blue = lerp(blue, o.blue, t), teal = lerp(teal, o.teal, t),
    indigo = lerp(indigo, o.indigo, t), purple = lerp(purple, o.purple, t), gray = lerp(gray, o.gray, t),
    yellow = lerp(yellow, o.yellow, t), pink = lerp(pink, o.pink, t),
)

private fun ColorScheme.lerpTo(o: ColorScheme, t: Float) = copy(
    primary = lerp(primary, o.primary, t), onPrimary = lerp(onPrimary, o.onPrimary, t),
    primaryContainer = lerp(primaryContainer, o.primaryContainer, t),
    onPrimaryContainer = lerp(onPrimaryContainer, o.onPrimaryContainer, t),
    inversePrimary = lerp(inversePrimary, o.inversePrimary, t),
    secondary = lerp(secondary, o.secondary, t), onSecondary = lerp(onSecondary, o.onSecondary, t),
    secondaryContainer = lerp(secondaryContainer, o.secondaryContainer, t),
    onSecondaryContainer = lerp(onSecondaryContainer, o.onSecondaryContainer, t),
    tertiary = lerp(tertiary, o.tertiary, t), onTertiary = lerp(onTertiary, o.onTertiary, t),
    tertiaryContainer = lerp(tertiaryContainer, o.tertiaryContainer, t),
    onTertiaryContainer = lerp(onTertiaryContainer, o.onTertiaryContainer, t),
    background = lerp(background, o.background, t), onBackground = lerp(onBackground, o.onBackground, t),
    surface = lerp(surface, o.surface, t), onSurface = lerp(onSurface, o.onSurface, t),
    surfaceVariant = lerp(surfaceVariant, o.surfaceVariant, t),
    onSurfaceVariant = lerp(onSurfaceVariant, o.onSurfaceVariant, t),
    surfaceTint = lerp(surfaceTint, o.surfaceTint, t),
    inverseSurface = lerp(inverseSurface, o.inverseSurface, t),
    inverseOnSurface = lerp(inverseOnSurface, o.inverseOnSurface, t),
    error = lerp(error, o.error, t), onError = lerp(onError, o.onError, t),
    errorContainer = lerp(errorContainer, o.errorContainer, t),
    onErrorContainer = lerp(onErrorContainer, o.onErrorContainer, t),
    outline = lerp(outline, o.outline, t), outlineVariant = lerp(outlineVariant, o.outlineVariant, t),
    scrim = lerp(scrim, o.scrim, t),
    surfaceBright = lerp(surfaceBright, o.surfaceBright, t), surfaceDim = lerp(surfaceDim, o.surfaceDim, t),
    surfaceContainer = lerp(surfaceContainer, o.surfaceContainer, t),
    surfaceContainerHigh = lerp(surfaceContainerHigh, o.surfaceContainerHigh, t),
    surfaceContainerHighest = lerp(surfaceContainerHighest, o.surfaceContainerHighest, t),
    surfaceContainerLow = lerp(surfaceContainerLow, o.surfaceContainerLow, t),
    surfaceContainerLowest = lerp(surfaceContainerLowest, o.surfaceContainerLowest, t),
)

val Numeric = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun FarvaterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    style: UiStyle = UiStyle.IOS,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val material = style != UiStyle.IOS
    val dark = darkTheme || style == UiStyle.ROSKOMPOZOR
    val targetScheme = remember(dark, style, dynamicColor) {
        if (style == UiStyle.ROSKOMPOZOR) {
            RkpDark
        } else if (style == UiStyle.SUMIE) {
            if (dark) SumieDark else SumieLight
        } else if (!material) {
            scheme(if (dark) IosDark else IosLight)
        } else if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) {
            OrangeDark
        } else {
            OrangeLight
        }
    }
    val targetIos = remember(targetScheme, dark, style) {
        when {
            style == UiStyle.SUMIE -> sumieColors(targetScheme, dark)
            material -> materialColors(targetScheme, dark)
            dark -> IosDark
            else -> IosLight
        }
    }
    val colorScheme = fadeTo(targetScheme) { a, b, t -> a.lerpTo(b, t) }
    val ios = fadeTo(targetIos) { a, b, t -> a.lerpTo(b, t) }
    CompositionLocalProvider(LocalIos provides ios, LocalUiStyle provides style) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = when {
                style == UiStyle.SUMIE -> SumieType
                material -> MaterialType
                else -> Type
            },
            shapes = when {
                style == UiStyle.SUMIE -> SumieShapes
                material -> M3Shapes
                else -> IosShapes
            },
            content = content,
        )
    }
}

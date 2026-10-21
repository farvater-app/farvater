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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.R
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

    val nothing: Boolean
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current == UiStyle.NOTHING

    val origin: Boolean
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current == UiStyle.ORIGIN

    val hyper: Boolean
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current == UiStyle.HYPER

    val harmony: Boolean
        @Composable @ReadOnlyComposable get() = LocalUiStyle.current == UiStyle.HARMONY
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

val NothingRed = Color(0xFFD71921)
val NothingRedNight = Color(0xFFE8333C)

val NothingDot = FontFamily(Font(R.font.farvater_dot))

private val NothingDark = darkColorScheme(
    primary = Color(0xFFF2F2F2), onPrimary = Color.Black,
    primaryContainer = Color(0xFF1C1C1C), onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFFBDBDBD), onSecondary = Color.Black,
    secondaryContainer = Color(0xFFF2F2F2), onSecondaryContainer = Color.Black,
    tertiary = NothingRedNight, onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3A0A0D), onTertiaryContainer = Color(0xFFFFDAD8),
    error = NothingRedNight, onError = Color.White,
    errorContainer = Color(0xFF3A0A0D), onErrorContainer = Color(0xFFFFDAD8),
    background = Color.Black, onBackground = Color(0xFFF2F2F2),
    surface = Color.Black, onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1C1C1C), onSurfaceVariant = Color(0xFF8C8C8C),
    outline = Color(0xFF5C5C5C), outlineVariant = Color(0xFF262626),
    inverseSurface = Color(0xFFF2F2F2), inverseOnSurface = Color.Black, inversePrimary = Color.Black,
    surfaceTint = Color.Transparent, scrim = Color.Black,
    surfaceDim = Color.Black, surfaceBright = Color(0xFF262626),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF0E0E0E), surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF242424),
)

private val NothingLight = lightColorScheme(
    primary = Color(0xFF0E0E0E), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E6), onPrimaryContainer = Color(0xFF0E0E0E),
    secondary = Color(0xFF4A4A4A), onSecondary = Color.White,
    secondaryContainer = Color(0xFF0E0E0E), onSecondaryContainer = Color.White,
    tertiary = NothingRed, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD8), onTertiaryContainer = Color(0xFF410004),
    error = NothingRed, onError = Color.White,
    errorContainer = Color(0xFFFFDAD8), onErrorContainer = Color(0xFF410004),
    background = Color(0xFFF2F2F2), onBackground = Color(0xFF0E0E0E),
    surface = Color(0xFFF2F2F2), onSurface = Color(0xFF0E0E0E),
    surfaceVariant = Color(0xFFE6E6E6), onSurfaceVariant = Color(0xFF7A7A7A),
    outline = Color(0xFF9E9E9E), outlineVariant = Color(0xFFDDDDDD),
    inverseSurface = Color(0xFF0E0E0E), inverseOnSurface = Color.White, inversePrimary = Color.White,
    surfaceTint = Color.Transparent, scrim = Color.Black,
    surfaceDim = Color(0xFFE2E2E2), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF2F2F2), surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE0E0E0),
)

val OriginBlue = Color(0xFF3D5AFE)
val OriginBlueNight = Color(0xFF8193FF)

val OriginFont = FontFamily(
    Font(R.font.farvater_manrope_semibold, FontWeight.SemiBold),
    Font(R.font.farvater_manrope_bold, FontWeight.Bold),
    Font(R.font.farvater_manrope_extrabold, FontWeight.ExtraBold),
)

private val OriginDark = darkColorScheme(
    primary = OriginBlueNight, onPrimary = Color(0xFF0A1060),
    primaryContainer = Color(0xFF2A35A8), onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFB9BCD9), onSecondary = Color(0xFF23263D),
    secondaryContainer = Color(0xFF2C3048), onSecondaryContainer = Color(0xFFE0E2FF),
    tertiary = Color(0xFF2FD891), onTertiary = Color(0xFF00391F),
    tertiaryContainer = Color(0xFF0B4F31), onTertiaryContainer = Color(0xFF9CF5C6),
    error = Color(0xFFFF6B6F), onError = Color(0xFF5C0010),
    errorContainer = Color(0xFF7A1020), onErrorContainer = Color(0xFFFFDADB),
    background = Color(0xFF07080D), onBackground = Color(0xFFF4F6FB),
    surface = Color(0xFF07080D), onSurface = Color(0xFFF4F6FB),
    surfaceVariant = Color(0xFF1E2130), onSurfaceVariant = Color(0xFFA3A8BE),
    outline = Color(0xFF6B7088), outlineVariant = Color(0xFF262A3A),
    inverseSurface = Color(0xFFF4F6FB), inverseOnSurface = Color(0xFF14161F), inversePrimary = OriginBlue,
    surfaceTint = Color.Transparent, scrim = Color.Black,
    surfaceDim = Color(0xFF07080D), surfaceBright = Color(0xFF2A2E40),
    surfaceContainerLowest = Color(0xFF050609), surfaceContainerLow = Color(0xFF0E1018),
    surfaceContainer = Color(0xFF12141D), surfaceContainerHigh = Color(0xFF181B26),
    surfaceContainerHighest = Color(0xFF212533),
)

private val OriginLight = lightColorScheme(
    primary = OriginBlue, onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF), onPrimaryContainer = Color(0xFF0A1060),
    secondary = Color(0xFF5A5F7A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E5F6), onSecondaryContainer = Color(0xFF171B2E),
    tertiary = Color(0xFF0A7A48), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8F5DE), onTertiaryContainer = Color(0xFF002112),
    error = Color(0xFFD02A3C), onError = Color.White,
    errorContainer = Color(0xFFFFDADB), onErrorContainer = Color(0xFF41000A),
    background = Color(0xFFEEF1F7), onBackground = Color(0xFF0E1220),
    surface = Color(0xFFEEF1F7), onSurface = Color(0xFF0E1220),
    surfaceVariant = Color(0xFFE1E5F0), onSurfaceVariant = Color(0xFF5B6178),
    outline = Color(0xFF8A90A6), outlineVariant = Color(0xFFD5D9E6),
    inverseSurface = Color(0xFF1A1D2B), inverseOnSurface = Color(0xFFF1F3FA), inversePrimary = Color(0xFFB9C3FF),
    surfaceTint = Color.Transparent, scrim = Color.Black,
    surfaceDim = Color(0xFFDDE1EC), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F8FC),
    surfaceContainer = Color(0xFFEEF1F7), surfaceContainerHigh = Color(0xFFE9EDF5),
    surfaceContainerHighest = Color(0xFFE2E7F0),
)

private fun originColors(s: ColorScheme, dark: Boolean): IosColors = materialColors(s, dark).copy(
    cell = if (dark) Color(0xFF13151E) else Color.White,
    cellRaised = if (dark) Color(0xFF1B1E2A) else Color(0xFFF4F6FB),
    highlight = if (dark) Color(0xFF1F2230) else Color(0xFFE9ECF4),
    bar = if (dark) Color(0xF2161822) else Color(0xF2FFFFFF),
    tint = s.primary,
    green = if (dark) Color(0xFF2FD891) else Color(0xFF0A7A48),
    blue = s.primary,
    indigo = if (dark) Color(0xFFA7B2FF) else Color(0xFF5B4CF0),
)

val HyperBlue = Color(0xFF1A66E0)
val HyperBlueNight = Color(0xFF4D94FF)

val HyperFont = FontFamily(
    Font(R.font.farvater_onest_medium, FontWeight.Medium),
    Font(R.font.farvater_onest_semibold, FontWeight.SemiBold),
    Font(R.font.farvater_onest_bold, FontWeight.Bold),
)

private val HyperLight = lightColorScheme(
    primary = HyperBlue, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF), onPrimaryContainer = Color(0xFF00214F),
    secondary = Color(0xFF5E6470), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E8ED), onSecondaryContainer = Color(0xFF191C22),
    tertiary = Color(0xFF13803F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC9F4DA), onTertiaryContainer = Color(0xFF00210E),
    error = Color(0xFFC8291D), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF3F4F7), onBackground = Color(0xFF111114),
    surface = Color(0xFFF3F4F7), onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFE6E8ED), onSurfaceVariant = Color(0xFF6A6D75),
    outline = Color(0xFFA2A5AD), outlineVariant = Color(0xFFE3E5EA),
    inverseSurface = Color(0xFF1F2024), inverseOnSurface = Color(0xFFF3F4F7), inversePrimary = Color(0xFFA9C7FF),
    surfaceTint = Color.Transparent, scrim = Color.Black,
    surfaceDim = Color(0xFFE1E3E8), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFF3F4F7), surfaceContainerHigh = Color(0xFFECEEF2),
    surfaceContainerHighest = Color(0xFFE6E8ED),
)

private val HyperDark = darkColorScheme(
    primary = HyperBlueNight, onPrimary = Color(0xFF002E6B),
    primaryContainer = Color(0xFF0B3F8F), onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFFB5B8C0), onSecondary = Color(0xFF202227),
    secondaryContainer = Color(0xFF2A2B30), onSecondaryContainer = Color(0xFFE3E4E9),
    tertiary = Color(0xFF2FD27A), onTertiary = Color(0xFF003919),
    tertiaryContainer = Color(0xFF0C4F2A), onTertiaryContainer = Color(0xFFA6F5C3),
    error = Color(0xFFFF6B5E), onError = Color(0xFF5F0A04),
    errorContainer = Color(0xFF7F1A12), onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1C), onSurfaceVariant = Color(0xFF8C8C92),
    outline = Color(0xFF5E5E64), outlineVariant = Color(0xFF26262A),
    inverseSurface = Color(0xFFF3F4F7), inverseOnSurface = Color(0xFF111114), inversePrimary = Color(0xFF1F64F0),
    surfaceTint = Color.Transparent, scrim = Color.Black,
    surfaceDim = Color.Black, surfaceBright = Color(0xFF2C2C30),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0D0D0F),
    surfaceContainer = Color(0xFF141416), surfaceContainerHigh = Color(0xFF1A1A1C),
    surfaceContainerHighest = Color(0xFF242428),
)

private fun hyperColors(s: ColorScheme, dark: Boolean): IosColors = materialColors(s, dark).copy(
    background = if (dark) Color.Black else Color(0xFFF3F4F7),
    cell = if (dark) Color(0xFF1A1A1C) else Color.White,
    cellRaised = if (dark) Color(0xFF242428) else Color(0xFFF3F4F7),
    highlight = if (dark) Color(0xFF26262A) else Color(0xFFEDEEF2),
    fill = if (dark) Color(0xFF242428) else Color(0xFFE6E8ED),
    bar = if (dark) Color(0xFA0D0D0F) else Color(0xFAFFFFFF),
    tint = s.primary,
    green = if (dark) Color(0xFF2FD27A) else Color(0xFF13803F),
    blue = s.primary,
)

val HarmonyBlue = Color(0xFF0A59F7)
val HarmonyBlueNight = Color(0xFF4D8EFF)

val HarmonyFont = FontFamily(
    Font(R.font.farvater_inter_regular, FontWeight.Normal),
    Font(R.font.farvater_inter_medium, FontWeight.Medium),
    Font(R.font.farvater_inter_semibold, FontWeight.SemiBold),
    Font(R.font.farvater_inter_bold, FontWeight.Bold),
)

private val HarmonyLight = lightColorScheme(
    primary = HarmonyBlue, onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE7FF), onPrimaryContainer = Color(0xFF001A4D),
    secondary = Color(0xFF5C5F66), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E8EC), onSecondaryContainer = Color(0xFF191919),
    tertiary = Color(0xFF177A35), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEFD6), onTertiaryContainer = Color(0xFF00210B),
    error = Color(0xFFC9361E), onError = Color.White,
    errorContainer = Color(0xFFFFDAD3), onErrorContainer = Color(0xFF3F0500),
    background = Color(0xFFF1F3F5), onBackground = Color(0xFF191919),
    surface = Color(0xFFF1F3F5), onSurface = Color(0xFF191919),
    surfaceVariant = Color(0xFFE5E8EC), onSurfaceVariant = Color(0xFF666666),
    outline = Color(0xFF999999), outlineVariant = Color(0xFFE0E3E7),
    inverseSurface = Color(0xFF202224), inverseOnSurface = Color(0xFFF1F3F5), inversePrimary = Color(0xFFA9C4FF),
    surfaceTint = Color.Transparent, scrim = Color.Black,
    surfaceDim = Color(0xFFDFE2E6), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainer = Color(0xFFF1F3F5), surfaceContainerHigh = Color(0xFFEAEDF0),
    surfaceContainerHighest = Color(0xFFE5E8EC),
)

private val HarmonyDark = darkColorScheme(
    primary = HarmonyBlueNight, onPrimary = Color(0xFF002A75),
    primaryContainer = Color(0xFF0D3C9E), onPrimaryContainer = Color(0xFFDDE7FF),
    secondary = Color(0xFFB4B7BD), onSecondary = Color(0xFF1F2124),
    secondaryContainer = Color(0xFF2B2D30), onSecondaryContainer = Color(0xFFE4E6E9),
    tertiary = Color(0xFF5CC95C), onTertiary = Color(0xFF00390F),
    tertiaryContainer = Color(0xFF0E4F22), onTertiaryContainer = Color(0xFFB4F1BF),
    error = Color(0xFFFF5A47), onError = Color(0xFF5F0B02),
    errorContainer = Color(0xFF7D1C0E), onErrorContainer = Color(0xFFFFDAD3),
    background = Color.Black, onBackground = Color(0xFFE6E6E6),
    surface = Color.Black, onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFF9E9EA3),
    outline = Color(0xFF5F6066), outlineVariant = Color(0xFF2A2A2D),
    inverseSurface = Color(0xFFF1F3F5), inverseOnSurface = Color(0xFF191919), inversePrimary = Color(0xFF0A59F7),
    surfaceTint = Color.Transparent, scrim = Color.Black,
    surfaceDim = Color.Black, surfaceBright = Color(0xFF2E2E31),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0E0E0F),
    surfaceContainer = Color(0xFF151517), surfaceContainerHigh = Color(0xFF1C1C1E),
    surfaceContainerHighest = Color(0xFF262628),
)

private fun harmonyColors(s: ColorScheme, dark: Boolean): IosColors = materialColors(s, dark).copy(
    background = if (dark) Color.Black else Color(0xFFF1F3F5),
    cell = if (dark) Color(0xFF1C1C1E) else Color.White,
    cellRaised = if (dark) Color(0xFF262628) else Color(0xFFF1F3F5),
    highlight = if (dark) Color(0xFF29292C) else Color(0xFFEAEDF0),
    fill = if (dark) Color(0xFF262628) else Color(0xFFE5E8EC),
    bar = if (dark) Color(0xF20E0E0F) else Color(0xF2F1F3F5),
    tint = s.primary,
    green = if (dark) Color(0xFF5CC95C) else Color(0xFF177A35),
    blue = s.primary,
)

private fun nothingColors(s: ColorScheme, dark: Boolean): IosColors = materialColors(s, dark).copy(
    cell = if (dark) Color(0xFF111111) else Color.White,
    highlight = if (dark) Color(0xFF1E1E1E) else Color(0xFFEDEDED),
    tint = s.onSurface,
    green = s.onSurface,
    red = if (dark) NothingRedNight else NothingRed,
    blue = s.onSurface,
    teal = s.onSurface,
    indigo = s.onSurface,
    purple = s.onSurface,
    yellow = s.onSurface,
    pink = s.onSurface,
    gray = s.onSurfaceVariant,
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

val NothingLabel = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp)

private val NothingType = MaterialType.copy(
    displaySmall = TextStyle(fontFamily = NothingDot, fontSize = 40.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = NothingDot, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = 0.5.sp),
    headlineMedium = TextStyle(fontFamily = NothingDot, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontFamily = NothingDot, fontSize = 27.sp, lineHeight = 32.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontFamily = NothingDot, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.5.sp),
    titleSmall = NothingLabel,
)

private val OriginType = MaterialType.copy(
    displaySmall = TextStyle(fontFamily = OriginFont, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = OriginFont, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = OriginFont, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = OriginFont, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = OriginFont, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = OriginFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = OriginFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = OriginFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
)

private val HyperType = MaterialType.copy(
    displaySmall = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 33.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

private val HarmonyType = MaterialType.copy(
    displaySmall = TextStyle(fontFamily = HarmonyFont, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = HarmonyFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = HarmonyFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 33.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = HarmonyFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 29.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = HarmonyFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = HarmonyFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = HarmonyFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = HarmonyFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = HarmonyFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = HarmonyFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = HarmonyFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
)

private val HarmonyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val OriginShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val HyperShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

private val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
        } else if (style == UiStyle.NOTHING) {
            if (dark) NothingDark else NothingLight
        } else if (style == UiStyle.ORIGIN) {
            if (dark) OriginDark else OriginLight
        } else if (style == UiStyle.HYPER) {
            if (dark) HyperDark else HyperLight
        } else if (style == UiStyle.HARMONY) {
            if (dark) HarmonyDark else HarmonyLight
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
            style == UiStyle.NOTHING -> nothingColors(targetScheme, dark)
            style == UiStyle.ORIGIN -> originColors(targetScheme, dark)
            style == UiStyle.HYPER -> hyperColors(targetScheme, dark)
            style == UiStyle.HARMONY -> harmonyColors(targetScheme, dark)
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
                style == UiStyle.NOTHING -> NothingType
                style == UiStyle.ORIGIN -> OriginType
                style == UiStyle.HYPER -> HyperType
                style == UiStyle.HARMONY -> HarmonyType
                material -> MaterialType
                else -> Type
            },
            shapes = when {
                style == UiStyle.SUMIE -> SumieShapes
                style == UiStyle.NOTHING -> NothingShapes
                style == UiStyle.ORIGIN -> OriginShapes
                style == UiStyle.HYPER -> HyperShapes
                style == UiStyle.HARMONY -> HarmonyShapes
                material -> M3Shapes
                else -> IosShapes
            },
            content = content,
        )
    }
}

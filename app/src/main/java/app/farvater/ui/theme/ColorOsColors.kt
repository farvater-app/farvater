package app.farvater.ui.theme

import androidx.compose.ui.graphics.Color

internal fun colorOsColors(dark: Boolean): IosColors = if (dark) IosDark.copy(
    background = Color(0xFF08090B), cell = Color(0xFF1A1C20), cellRaised = Color(0xFF24272D),
    fill = Color(0xFF30353E), highlight = Color(0xFF354354), segmentThumb = Color(0xFF354354),
    bar = Color(0xFF1C1F24), barBorder = Color(0xFF34383F), separator = Color(0xFF373A40),
    label = Color(0xFFF5F6F8), secondaryLabel = Color(0xFFB6BBC5), tertiaryLabel = Color(0xFFB6BBC5),
    tint = Color(0xFF94BEFF), blue = Color(0xFF94BEFF), green = Color(0xFF83DBA0),
    red = Color(0xFFFFACAB), teal = Color(0xFF80D7D3), indigo = Color(0xFFB7BDFF),
    purple = Color(0xFFD6B5F6), gray = Color(0xFFB6BBC5), yellow = Color(0xFFF3CF81), pink = Color(0xFFF3AECC),
) else IosLight.copy(
    background = Color(0xFFF3F4F6), cell = Color.White, cellRaised = Color(0xFFFAFBFD),
    fill = Color(0xFFE8EBF0), highlight = Color(0xFFDCE9FD), segmentThumb = Color.White,
    bar = Color.White, barBorder = Color(0xFFE0E3E9), separator = Color(0xFFE5E7EB),
    label = Color(0xFF17191E), secondaryLabel = Color(0xFF565D68), tertiaryLabel = Color(0xFF565D68),
    tint = Color(0xFF075FCB), blue = Color(0xFF075FCB), green = Color(0xFF18743D),
    red = Color(0xFFB82D38), teal = Color(0xFF116F73), indigo = Color(0xFF4D52AA),
    purple = Color(0xFF7E439E), gray = Color(0xFF565D68), yellow = Color(0xFF865700), pink = Color(0xFFAB3569),
)

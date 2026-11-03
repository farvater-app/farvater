package app.farvater.ui.theme

import androidx.compose.ui.graphics.Color

internal fun colorOsColors(dark: Boolean): IosColors = if (dark) IosDark.copy(
    background = Color(0xFF0C1411), cell = Color(0xFF182720), cellRaised = Color(0xFF203229),
    fill = Color(0xFF293C35), highlight = Color(0xFF2B4A3D), segmentThumb = Color(0xFF2B4A3D),
    bar = Color(0xFF14241D), barBorder = Color(0xFF355044), separator = Color(0xFF344A3F),
    label = Color(0xFFF3F8F3), secondaryLabel = Color(0xFFC0CEC4), tertiaryLabel = Color(0xFFC0CEC4),
    tint = Color(0xFF8EE5C3), blue = Color(0xFF8EE5C3), green = Color(0xFF83DBA0),
    red = Color(0xFFFFACAB), teal = Color(0xFF80D7D3), indigo = Color(0xFFB7BDFF),
    purple = Color(0xFFD6B5F6), gray = Color(0xFFC0CEC4), yellow = Color(0xFFF3CF81), pink = Color(0xFFF3AECC),
) else IosLight.copy(
    background = Color(0xFFF1F5EF), cell = Color.White, cellRaised = Color(0xFFF7FAF5),
    fill = Color(0xFFE8EFEA), highlight = Color(0xFFE1EEE5), segmentThumb = Color.White,
    bar = Color.White, barBorder = Color(0xFFD9E5DC), separator = Color(0xFFDCE7DE),
    label = Color(0xFF172820), secondaryLabel = Color(0xFF4D5F56), tertiaryLabel = Color(0xFF4D5F56),
    tint = Color(0xFF006951), blue = Color(0xFF006951), green = Color(0xFF18743D),
    red = Color(0xFFB82D38), teal = Color(0xFF116F73), indigo = Color(0xFF4D52AA),
    purple = Color(0xFF7E439E), gray = Color(0xFF4D5F56), yellow = Color(0xFF865700), pink = Color(0xFFAB3569),
)

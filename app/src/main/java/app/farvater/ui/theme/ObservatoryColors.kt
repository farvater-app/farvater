package app.farvater.ui.theme

import androidx.compose.ui.graphics.Color

internal fun observatoryColors(dark: Boolean): IosColors = if (dark) IosDark.copy(
    background = Color(0xFF091623), cell = Color(0xFF102336), cellRaised = Color(0xFF183248),
    fill = Color(0xFF233C4C), bar = Color(0xFF0C1B2A), highlight = Color(0xFF254058),
    barBorder = Color(0xFF465660), separator = Color(0xFF40515C), label = Color(0xFFF8EFDD),
    secondaryLabel = Color(0xFFB8C9D0), tertiaryLabel = Color(0xFFAFBFC6),
    red = Color(0xFFFFA29B), yellow = Color(0xFFE6C17A),
    tint = Color(0xFFF2CC83), blue = Color(0xFF9FD6EF), green = Color(0xFF99DABA)
) else IosLight.copy(
    background = Color(0xFFF3ECD9), cell = Color(0xFFFCF7E9), cellRaised = Color(0xFFEDE4CA),
    fill = Color(0xFFE7DFC9), bar = Color(0xFFF7F0DC), highlight = Color(0xFFE7DCBF),
    barBorder = Color(0xFFB3A68B), separator = Color(0xFFBDB19A), label = Color(0xFF172A36),
    secondaryLabel = Color(0xFF485A60), tertiaryLabel = Color(0xFF546367),
    red = Color(0xFFA52932), yellow = Color(0xFF775300),
    tint = Color(0xFF765018), blue = Color(0xFF245A70), green = Color(0xFF30694E)
)

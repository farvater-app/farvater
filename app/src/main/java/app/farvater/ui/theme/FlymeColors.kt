package app.farvater.ui.theme

import androidx.compose.ui.graphics.Color

internal fun flymeColors(dark: Boolean): IosColors = if (dark) IosDark.copy(
    background = Color(0xFF0A1425), cell = Color(0xFF122039), cellRaised = Color(0xFF1A2943),
    fill = Color(0xFF23334D), highlight = Color(0xFF293E5C), segmentThumb = Color(0xFF3B4658),
    bar = Color(0xFF122039), barBorder = Color(0xFF343A46), separator = Color(0xFF343A46),
    label = Color(0xFFF2F5FB), secondaryLabel = Color(0xFFB8C5DA), tertiaryLabel = Color(0xFFB8C5DA),
    tint = Color(0xFF97C3FF), blue = Color(0xFF97C3FF), green = Color(0xFF80D6B0),
    red = Color(0xFFFFA8B0), teal = Color(0xFF82D5DF), indigo = Color(0xFFBAC4FF),
    purple = Color(0xFFD2B7FF), gray = Color(0xFFB8C5DA), yellow = Color(0xFFE8CE91), pink = Color(0xFFFFB0CF),
) else IosLight.copy(
    background = Color(0xFFF0F5FD), cell = Color.White, cellRaised = Color(0xFFE8F0FC),
    fill = Color(0xFFE2EBF8), highlight = Color(0xFFE1ECFF), segmentThumb = Color.White,
    bar = Color.White, barBorder = Color(0xFFE1E7F0), separator = Color(0xFFE5EAF2),
    label = Color(0xFF152540), secondaryLabel = Color(0xFF495B76), tertiaryLabel = Color(0xFF52617A),
    tint = Color(0xFF155AC6), blue = Color(0xFF155AC6), green = Color(0xFF167352),
    red = Color(0xFFB32640), teal = Color(0xFF006C7B), indigo = Color(0xFF4A55B8),
    purple = Color(0xFF7544AC), gray = Color(0xFF495B76), yellow = Color(0xFF805B10), pink = Color(0xFFA43066),
)

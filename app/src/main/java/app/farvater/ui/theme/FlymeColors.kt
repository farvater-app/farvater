package app.farvater.ui.theme

import androidx.compose.ui.graphics.Color

internal fun flymeColors(dark: Boolean): IosColors = if (dark) IosDark.copy(
    background = Color(0xFF101115), cell = Color(0xFF1C1E24), cellRaised = Color(0xFF262A32),
    fill = Color(0xFF303540), highlight = Color(0xFF293E5C), segmentThumb = Color(0xFF3B4658),
    bar = Color(0xFF1C1E24), barBorder = Color(0xFF343A46), separator = Color(0xFF343A46),
    label = Color(0xFFF2F5FB), secondaryLabel = Color(0xFFB3BDC9), tertiaryLabel = Color(0xFFADB8C6),
    tint = Color(0xFF97C3FF), blue = Color(0xFF97C3FF), green = Color(0xFF80D6B0),
    red = Color(0xFFFFA8B0), teal = Color(0xFF82D5DF), indigo = Color(0xFFBAC4FF),
    purple = Color(0xFFD2B7FF), gray = Color(0xFFB3BDC9), yellow = Color(0xFFE8CE91), pink = Color(0xFFFFB0CF),
) else IosLight.copy(
    background = Color(0xFFF6F8FC), cell = Color.White, cellRaised = Color(0xFFEDF3FF),
    fill = Color(0xFFE7EDF6), highlight = Color(0xFFE1ECFF), segmentThumb = Color.White,
    bar = Color.White, barBorder = Color(0xFFE1E7F0), separator = Color(0xFFE5EAF2),
    label = Color(0xFF182235), secondaryLabel = Color(0xFF536176), tertiaryLabel = Color(0xFF59677B),
    tint = Color(0xFF155AC6), blue = Color(0xFF155AC6), green = Color(0xFF167352),
    red = Color(0xFFB32640), teal = Color(0xFF006C7B), indigo = Color(0xFF4A55B8),
    purple = Color(0xFF7544AC), gray = Color(0xFF536176), yellow = Color(0xFF805B10), pink = Color(0xFFA43066),
)

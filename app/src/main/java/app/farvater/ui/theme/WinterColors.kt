package app.farvater.ui.theme

import androidx.compose.ui.graphics.Color

internal fun winterColors(dark: Boolean): IosColors = if (dark) IosDark.copy(
    background = Color(0xFF0B1628), cell = Color(0xFF14253D), cellRaised = Color(0xFF1B3049),
    fill = Color(0xFF263B52), highlight = Color(0xFF30465E), segmentThumb = Color(0xFF30465E),
    bar = Color(0xFF14253D), barBorder = Color(0xFF3B526B), separator = Color(0xFF3B526B),
    label = Color(0xFFF0F6FF), secondaryLabel = Color(0xFFBCCBDD), tertiaryLabel = Color(0xFFBCCBDD),
    tint = Color(0xFF9FD4FF), green = Color(0xFF8CDEBA), red = Color(0xFFFFB4B8),
    blue = Color(0xFF9FD4FF), teal = Color(0xFF87DEDB), indigo = Color(0xFFBCC8FF),
    purple = Color(0xFFD7BCF6), gray = Color(0xFFBCCBDD), yellow = Color(0xFFF7D493), pink = Color(0xFFF5B8D3),
) else IosLight.copy(
    background = Color(0xFFF3F7FB), cell = Color.White, cellRaised = Color(0xFFF3F7FB),
    fill = Color(0xFFDCE6F0), highlight = Color(0xFFD4E0ED), segmentThumb = Color.White,
    bar = Color(0xFFF3F7FB), barBorder = Color(0xFFADBDCE), separator = Color(0xFFADBDCE),
    label = Color(0xFF12273C), secondaryLabel = Color(0xFF405269), tertiaryLabel = Color(0xFF405269),
    tint = Color(0xFF1F5D8F), green = Color(0xFF176543), red = Color(0xFFA92E40),
    blue = Color(0xFF1F5D8F), teal = Color(0xFF175E64), indigo = Color(0xFF424D91),
    purple = Color(0xFF704177), gray = Color(0xFF405269), yellow = Color(0xFF784B00), pink = Color(0xFF8B3860),
)

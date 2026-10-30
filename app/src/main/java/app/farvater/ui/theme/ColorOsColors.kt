package app.farvater.ui.theme

import androidx.compose.ui.graphics.Color

internal fun colorOsColors(dark: Boolean): IosColors = if (dark) winterColors(true).copy(
    background = Color(0xFF101216), cell = Color(0xFF1B1E24), cellRaised = Color(0xFF242830),
    fill = Color(0xFF303641), highlight = Color(0xFF39414E), segmentThumb = Color(0xFF39414E),
    bar = Color(0xFF242830), tint = Color(0xFFAFCBFF), blue = Color(0xFFAFCBFF),
) else winterColors(false).copy(
    background = Color(0xFFF3F4F7), cell = Color.White, cellRaised = Color(0xFFF3F4F7),
    fill = Color(0xFFE0E6F2), highlight = Color(0xFFD7E1F2), segmentThumb = Color.White,
    bar = Color.White, tint = Color(0xFF2452A0), blue = Color(0xFF2452A0),
)

package app.farvater.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import app.farvater.R
import app.farvater.data.AppIcon

private val LogoGradient = Brush.linearGradient(listOf(Color(0xFF173A6B), Color(0xFF081628)))

@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.235f)).background(LogoGradient),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}

@Composable
fun AppIconImage(icon: AppIcon, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.235f)), contentAlignment = Alignment.Center) {
        Image(painterResource(icon.background), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
        Image(painterResource(icon.foreground), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
    }
}

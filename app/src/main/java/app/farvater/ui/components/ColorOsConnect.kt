package app.farvater.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.farvater.R
import app.farvater.ui.theme.Ios

@Composable
fun ColorOsConnect(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val description = stringResource(when (mode) {
        ConnectMode.Idle -> R.string.action_connect
        ConnectMode.Searching -> R.string.action_stop_search
        ConnectMode.Connecting -> R.string.action_cancel_connect
        ConnectMode.Connected -> R.string.action_disconnect
        ConnectMode.Error -> R.string.action_retry_connect
    })
    val active = mode == ConnectMode.Connected
    val ink = if (active) Color.White else c.label
    val accent = if (mode == ConnectMode.Error) c.red else if (active) Color.White else c.tint
    val base = if (active) Color(0xFF065AC7) else c.cell
    val end = if (active) Color(0xFF234DAA) else c.highlight
    Box(modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
        .graphicsLayer { scaleX = if (pressed) 0.98f else 1f; scaleY = scaleX }
        .clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(base, base, end)))
        .then(if (focused) Modifier.border(2.dp, if (active) Color.White else c.tint, RoundedCornerShape(28.dp)) else Modifier)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick).semantics { contentDescription = description }) {
        Canvas(Modifier.align(Alignment.CenterEnd).size(150.dp).clearAndSetSemantics {}) {
            val center = Offset(size.width * 0.68f, size.height * 0.48f)
            val radius = size.minDimension * 0.43f
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), center, radius), radius, center)
            repeat(3) { ring ->
                drawCircle(accent.copy(alpha = 0.1f + ring * 0.04f), radius * (0.5f + ring * 0.26f), center,
                    style = Stroke((1.4f - ring * 0.25f).dp.toPx()))
            }
            drawCircle(accent.copy(alpha = 0.55f), 4.dp.toPx(), center + Offset(-radius * 0.73f, -radius * 0.43f))
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(48.dp).background(if (active) Color.White.copy(alpha = 0.18f) else c.highlight, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PowerSettingsNew, null, tint = accent, modifier = Modifier.size(27.dp))
            }
            Text(description, color = ink, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
        }
    }
}

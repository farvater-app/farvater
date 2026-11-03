package app.farvater.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.farvater.R
import app.farvater.ui.theme.Ios

@Composable
fun FlymeConnect(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val active = mode == ConnectMode.Connected
    val busy = mode == ConnectMode.Searching || mode == ConnectMode.Connecting
    val still = LocalShellMotionDisabled.current
    val clock = rememberWinterClock(still || !busy)
    val accent by animateColorAsState(if (mode == ConnectMode.Error) c.red else c.tint, shellSpring(), label = "flymeAccent")
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, shellSpring(), label = "flymePress")
    val fill by animateColorAsState(if (active) c.tint else c.background, shellSpring(), label = "flymePower")
    val description = stringResource(when (mode) {
        ConnectMode.Idle -> R.string.action_connect
        ConnectMode.Searching -> R.string.action_stop_search
        ConnectMode.Connecting -> R.string.action_cancel_connect
        ConnectMode.Connected -> R.string.action_disconnect
        ConnectMode.Error -> R.string.action_retry_connect
    })
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(224.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
                val radius = size.minDimension / 2
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.2f), accent.copy(alpha = 0.035f), Color.Transparent)), radius)
                drawCircle(accent.copy(alpha = 0.18f), radius * 0.85f, style = Stroke(1.dp.toPx()))
                if (busy) drawArc(accent, if (still) -90f else clock.value * 110f - 90f, 88f, false,
                    topLeft = Offset(radius * 0.15f, radius * 0.15f), size = Size(radius * 1.7f, radius * 1.7f),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            Box(Modifier.size(154.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
                .background(fill).border(if (focused) 3.dp else 1.dp, if (focused) app.farvater.ui.ios.readableOn(fill) else accent.copy(alpha = 0.25f), CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PowerSettingsNew, null, tint = app.farvater.ui.ios.readableOn(fill), modifier = Modifier.size(46.dp))
            }
        }
        Text(description, color = c.label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).clearAndSetSemantics {})
    }
}

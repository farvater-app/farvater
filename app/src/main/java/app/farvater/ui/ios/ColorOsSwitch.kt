package app.farvater.ui.ios

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import app.farvater.ui.components.shellSpring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.farvater.ui.theme.Ios

@Composable
internal fun ColorOsSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val offset by animateDpAsState(if (checked) 22.dp else 2.dp, shellSpring(), label = "switchPosition")
    val track by animateColorAsState(if (checked) c.tint else c.fill, shellSpring(), label = "switchColor")
    val trackHeight = if (Ios.flyme) 22.dp else 28.dp
    val thumb = if (Ios.flyme) 18.dp else 24.dp
    Box(modifier.size(48.dp).then(if (onCheckedChange == null) Modifier else
        Modifier.toggleable(interactionSource = interaction, indication = null, value = checked, role = Role.Switch, onValueChange = onCheckedChange)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (Ios.flyme) 42.dp else 48.dp, trackHeight).graphicsLayer { scaleX = if (pressed) 0.94f else 1f; scaleY = scaleX }.background(track, CircleShape)
            .then(if (focused) Modifier.border(2.dp, if (checked) c.background else c.label, CircleShape)
                else if (checked) Modifier else Modifier.border(1.dp, c.secondaryLabel, CircleShape)), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.offset(x = offset).size(thumb)
                .background(if (checked && c.dark) c.background else Color.White, CircleShape)
                .then(if (!checked && !c.dark) Modifier.border(1.dp, c.secondaryLabel, CircleShape) else Modifier))
        }
    }
}

package app.farvater.ui.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.farvater.ui.theme.Ios

@Composable
internal fun ColorOsSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val c = Ios.colors
    Box(modifier.size(48.dp).then(if (onCheckedChange == null) Modifier else
        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(46.dp, 26.dp).background(if (checked) c.tint else c.fill, CircleShape)
            .then(if (checked) Modifier else Modifier.border(1.dp, c.secondaryLabel, CircleShape)), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.offset(x = if (checked) 22.dp else 2.dp).size(22.dp)
                .background(if (checked && c.dark) c.background else Color.White, CircleShape)
                .then(if (!checked && !c.dark) Modifier.border(1.dp, c.secondaryLabel, CircleShape) else Modifier))
        }
    }
}

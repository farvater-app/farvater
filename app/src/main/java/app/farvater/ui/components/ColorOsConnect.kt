package app.farvater.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun ColorOsConnect(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val description = stringResource(when (mode) {
        ConnectMode.Idle -> R.string.action_connect
        ConnectMode.Searching -> R.string.action_stop_search
        ConnectMode.Connecting -> R.string.action_cancel_connect
        ConnectMode.Connected -> R.string.action_disconnect
        ConnectMode.Error -> R.string.action_retry_connect
    })
    val accent = when (mode) {
        ConnectMode.Connected -> c.green
        ConnectMode.Error -> c.red
        else -> c.tint
    }
    Column(modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()
        .clip(RoundedCornerShape(36.dp)).background(c.cell)
        .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }
        .padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(c.fill), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PowerSettingsNew, null, tint = accent, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(description, color = c.label, style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
        Spacer(Modifier.height(16.dp))
        Box(Modifier.width(52.dp).height(5.dp).clip(CircleShape).background(accent))
    }
}

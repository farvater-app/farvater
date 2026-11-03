package app.farvater.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.ui.components.shellSpring
import app.farvater.ui.theme.Ios

@Composable
internal fun ShellFrame(tab: Tab, onTab: (Tab) -> Unit, snackbar: SnackbarHostState, content: @Composable (PaddingValues) -> Unit) {
    val c = Ios.colors
    val flyme = Ios.flyme
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = navInset + 88.dp
    val accent by animateColorAsState(when (tab) {
        Tab.Home -> c.blue
        Tab.Servers -> c.teal
        Tab.Sources -> c.indigo
        Tab.Settings -> c.purple
    }, shellSpring(), label = "sectionAccent")
    Surface(color = c.background, contentColor = c.label, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(accent.copy(alpha = if (flyme) 0.055f else 0.09f), Color.Transparent))))
            CompositionLocalProvider(LocalBottomInset provides bottom) { content(WindowInsets.statusBars.asPaddingValues()) }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 12.dp))
            val shape = RoundedCornerShape(if (flyme) 22.dp else 32.dp)
            BoxWithConstraints(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).padding(bottom = navInset + 12.dp)
                .fillMaxWidth().height(72.dp).shadow(if (flyme) 4.dp else 12.dp, shape)
                .clip(shape).background(c.bar).border(0.75.dp, c.barBorder, shape).padding(6.dp)) {
                val itemWidth = maxWidth / Tab.entries.size
                val offset by animateDpAsState(itemWidth * tab.ordinal, shellSpring(), label = "navigationSelection")
                Box(Modifier.offset(x = offset).width(itemWidth).fillMaxHeight().padding(horizontal = if (flyme) 14.dp else 2.dp)
                    .then(if (flyme) Modifier.padding(top = 54.dp, bottom = 2.dp) else Modifier)
                    .background(if (flyme) accent else accent.copy(alpha = if (c.dark) 0.18f else 0.1f), if (flyme) CircleShape else RoundedCornerShape(26.dp)))
                Row(Modifier.fillMaxSize()) {
                    Tab.entries.forEach { item ->
                        val active = item == tab
                        val tint by animateColorAsState(if (active) accent else c.secondaryLabel, shellSpring(), label = "navigationTint")
                        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                            .clickable(role = Role.Tab, onClick = { onTab(item) }).semantics { selected = active },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(if (active) item.icon else item.iconIdle, null, tint = tint, modifier = Modifier.size(23.dp))
                            Spacer(Modifier.height(5.dp))
                            Text(item.title, color = tint, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

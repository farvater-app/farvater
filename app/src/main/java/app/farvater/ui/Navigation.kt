package app.farvater.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.data.UiStyle
import app.farvater.ui.components.dotGrid
import app.farvater.ui.components.paperGrain
import app.farvater.ui.ios.GlassBackdrop
import app.farvater.ui.ios.GlassStyles
import app.farvater.ui.ios.glassSource
import app.farvater.ui.ios.liquidGlass
import app.farvater.ui.ios.originRim
import app.farvater.ui.ios.rememberGlassBackdrop
import app.farvater.ui.theme.HarmonyFont
import app.farvater.ui.theme.HyperFont
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType
import app.farvater.ui.theme.LocalUiStyle
import app.farvater.ui.theme.NothingLabel
import app.farvater.ui.theme.OneUiFont
import app.farvater.ui.theme.OriginFont
import app.farvater.ui.theme.OxygenFont

internal enum class Tab(val title: String, val icon: ImageVector, val iconIdle: ImageVector) {
    Home("Фарватер", Icons.Rounded.Home, Icons.Outlined.Home),
    Servers("Узлы", Icons.Rounded.Dns, Icons.Outlined.Dns),
    Sources("Источники", Icons.Rounded.Layers, Icons.Outlined.Layers),
    Settings("Настройки", Icons.Rounded.Settings, Icons.Outlined.Settings),
}

val LocalBottomInset = staticCompositionLocalOf { 0.dp }

private val BarHeight = 62.dp
private val BarGap = 10.dp

@Composable
internal fun FarvaterFrame(
    tab: Tab,
    onTab: (Tab) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit,
) {
    when {
        Ios.nothing -> NothingFrame(tab, onTab, snackbar, content)
        Ios.origin -> OriginFrame(tab, onTab, snackbar, content)
        Ios.hyper -> HyperFrame(tab, onTab, snackbar, content)
        Ios.harmony -> HarmonyFrame(tab, onTab, snackbar, content)
        Ios.oneUi -> OneUiFrame(tab, onTab, snackbar, content)
        Ios.oxygen -> OxygenFrame(tab, onTab, snackbar, content)
        Ios.material -> MaterialFrame(tab, onTab, snackbar, content)
        else -> GlassFrame(tab, onTab, snackbar, content)
    }
}

@Composable
private fun NothingFrame(
    tab: Tab,
    onTab: (Tab) -> Unit,
    snackbar: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset: Dp = navInset + 66.dp
    Surface(color = c.background, contentColor = c.label, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().dotGrid(c.label)) {
            CompositionLocalProvider(LocalBottomInset provides bottomInset) {
                content(WindowInsets.statusBars.asPaddingValues())
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 10.dp)) { data ->
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .background(c.label, RoundedCornerShape(18.dp)),
                ) {
                    Text(
                        data.visuals.message,
                        style = IosType.subheadline,
                        color = c.background,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                    )
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.background)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.separator))
                Row(Modifier.fillMaxWidth().height(65.dp).padding(horizontal = 6.dp)) {
                    Tab.entries.forEach { t ->
                        NothingTab(t, selected = t == tab, modifier = Modifier.weight(1f)) {
                            if (t != tab) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onTab(t)
                        }
                    }
                }
                Spacer(Modifier.height(navInset))
            }
        }
    }
}

@Composable
private fun NothingTab(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ios.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dot by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 520f), label = "dot")
    val tint by animateColorAsState(if (selected) c.label else c.secondaryLabel, spring(stiffness = 500f), label = "tint")
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "scale")
    Column(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(5.dp)
                .graphicsLayer { scaleX = dot; scaleY = dot; alpha = dot }
                .background(c.red, CircleShape),
        )
        Spacer(Modifier.height(5.dp))
        Icon(tab.iconIdle, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(tab.title.uppercase(), style = NothingLabel.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = tint, maxLines = 1)
    }
}

@Composable
private fun OriginFrame(
    tab: Tab,
    onTab: (Tab) -> Unit,
    snackbar: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset: Dp = navInset + 64.dp + 24.dp
    Surface(color = c.background, contentColor = c.label, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(c.tint.copy(alpha = if (c.dark) 0.22f else 0.14f), Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.width * 1.15f,
                        ),
                    )
                },
        ) {
            CompositionLocalProvider(LocalBottomInset provides bottomInset) {
                content(WindowInsets.statusBars.asPaddingValues())
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset)) { data ->
                val shape = RoundedCornerShape(22.dp)
                Box(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .shadow(14.dp, shape)
                        .background(c.cellRaised, shape)
                        .border(1.dp, originRim(c.dark), shape),
                ) {
                    Text(
                        data.visuals.message,
                        style = IosType.subheadline,
                        color = c.label,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = navInset + 12.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .shadow(16.dp, CircleShape)
                    .background(c.bar, CircleShape)
                    .border(1.dp, originRim(c.dark), CircleShape),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
                    val item = maxWidth / Tab.entries.size
                    val pill by animateDpAsState(
                        item * tab.ordinal,
                        spring(dampingRatio = 0.6f, stiffness = 420f),
                        label = "pill",
                    )
                    Box(
                        Modifier
                            .offset { IntOffset(pill.roundToPx(), 0) }
                            .width(item)
                            .fillMaxHeight()
                            .background(c.tint.copy(alpha = if (c.dark) 0.22f else 0.12f), CircleShape),
                    )
                    Row(Modifier.fillMaxSize()) {
                        Tab.entries.forEach { t ->
                            OriginTab(t, selected = t == tab, modifier = Modifier.weight(1f)) {
                                if (t != tab) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onTab(t)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OriginTab(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ios.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint by animateColorAsState(if (selected) c.tint else c.secondaryLabel, spring(stiffness = 500f), label = "tint")
    val squashX by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.4f, stiffness = 520f), label = "squashX")
    val squashY by animateFloatAsState(if (pressed) 0.8f else 1f, spring(dampingRatio = 0.4f, stiffness = 520f), label = "squashY")
    Column(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .graphicsLayer { scaleX = squashX; scaleY = squashY },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(if (selected) tab.icon else tab.iconIdle, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            tab.title,
            style = TextStyle(fontFamily = OriginFont, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, lineHeight = 13.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HyperFrame(
    tab: Tab,
    onTab: (Tab) -> Unit,
    snackbar: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset: Dp = navInset + 64.dp
    Surface(color = c.background, contentColor = c.label, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalBottomInset provides bottomInset) {
                content(WindowInsets.statusBars.asPaddingValues())
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 12.dp)) { data ->
                Box(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .background(Color(0xF2232326), RoundedCornerShape(22.dp)),
                ) {
                    Text(
                        data.visuals.message,
                        style = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.bar)) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp)) {
                    Tab.entries.forEach { t ->
                        HyperTab(t, selected = t == tab, modifier = Modifier.weight(1f)) {
                            if (t != tab) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onTab(t)
                        }
                    }
                }
                Spacer(Modifier.height(navInset))
            }
        }
    }
}

@Composable
private fun HyperTab(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ios.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint by animateColorAsState(if (selected) c.tint else c.secondaryLabel, spring(stiffness = 500f), label = "tint")
    val press by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    val pop = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !seen) {
            pop.snapTo(0.78f)
            pop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 420f))
        }
        seen = selected
    }
    Column(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .graphicsLayer { scaleX = press; scaleY = press },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            if (selected) tab.icon else tab.iconIdle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(25.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
        Spacer(Modifier.height(3.dp))
        Text(
            tab.title,
            style = TextStyle(fontFamily = HyperFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OneUiFrame(
    tab: Tab,
    onTab: (Tab) -> Unit,
    snackbar: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset: Dp = navInset + 56.dp
    Surface(color = c.background, contentColor = c.label, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalBottomInset provides bottomInset) {
                content(WindowInsets.statusBars.asPaddingValues())
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 12.dp)) { data ->
                Box(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .background(Color(0xF2333336), RoundedCornerShape(26.dp)),
                ) {
                    Text(
                        data.visuals.message,
                        style = TextStyle(fontFamily = OneUiFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.background)) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
                    Tab.entries.forEach { t ->
                        OneUiTab(t, selected = t == tab, modifier = Modifier.weight(1f)) {
                            if (t != tab) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onTab(t)
                        }
                    }
                }
                Spacer(Modifier.height(navInset))
            }
        }
    }
}

@Composable
private fun OneUiTab(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ios.colors
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(if (selected) c.label else c.secondaryLabel, tween(200), label = "tint")
    val bar by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "bar")
    Column(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            tab.title,
            style = TextStyle(fontFamily = OneUiFont, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .height(3.dp)
                .width(22.dp)
                .graphicsLayer { scaleX = bar; alpha = bar }
                .background(c.tint, RoundedCornerShape(50)),
        )
    }
}

@Composable
private fun OxygenFrame(
    tab: Tab,
    onTab: (Tab) -> Unit,
    snackbar: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset: Dp = navInset + 62.dp
    Surface(color = c.background, contentColor = c.label, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalBottomInset provides bottomInset) {
                content(WindowInsets.statusBars.asPaddingValues())
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 12.dp)) { data ->
                Box(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .background(Color(0xF2202022), RoundedCornerShape(18.dp)),
                ) {
                    Text(
                        data.visuals.message,
                        style = TextStyle(fontFamily = OxygenFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.background)) {
                Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp)) {
                    Tab.entries.forEach { t ->
                        OxygenTab(t, selected = t == tab, modifier = Modifier.weight(1f)) {
                            if (t != tab) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onTab(t)
                        }
                    }
                }
                Spacer(Modifier.height(navInset))
            }
        }
    }
}

@Composable
private fun OxygenTab(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ios.colors
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(if (selected) c.label else c.secondaryLabel, tween(200), label = "tint")
    val dot by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "dot")
    Column(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(if (selected) tab.icon else tab.iconIdle, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            tab.title,
            style = TextStyle(fontFamily = OxygenFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(5.dp).graphicsLayer { scaleX = dot; scaleY = dot }.background(c.tint, CircleShape))
    }
}

@Composable
private fun HarmonyFrame(
    tab: Tab,
    onTab: (Tab) -> Unit,
    snackbar: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset: Dp = navInset + 60.dp
    Surface(color = c.background, contentColor = c.label, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalBottomInset provides bottomInset) {
                content(WindowInsets.statusBars.asPaddingValues())
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 12.dp)) { data ->
                Box(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .background(Color(0xF2303133), RoundedCornerShape(20.dp)),
                ) {
                    Text(
                        data.visuals.message,
                        style = TextStyle(fontFamily = HarmonyFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.bar)) {
                Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp)) {
                    Tab.entries.forEach { t ->
                        HarmonyTab(t, selected = t == tab, modifier = Modifier.weight(1f)) {
                            if (t != tab) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onTab(t)
                        }
                    }
                }
                Spacer(Modifier.height(navInset))
            }
        }
    }
}

@Composable
private fun HarmonyTab(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ios.colors
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(if (selected) c.tint else c.secondaryLabel, tween(220), label = "tint")
    val lift by animateFloatAsState(if (selected) -2f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")
    Column(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            if (selected) tab.icon else tab.iconIdle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp).graphicsLayer { translationY = lift.dp.toPx() },
        )
        Spacer(Modifier.height(3.dp))
        Text(
            tab.title,
            style = TextStyle(fontFamily = HarmonyFont, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, fontSize = 10.sp, lineHeight = 13.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GlassFrame(
    tab: Tab,
    onTab: (Tab) -> Unit,
    snackbar: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = Ios.colors
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset: Dp = navInset + BarHeight + BarGap * 2
    val backdrop = rememberGlassBackdrop()
    Surface(color = c.background, contentColor = c.label, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(c.background).glassSource(backdrop)) {
                CompositionLocalProvider(LocalBottomInset provides bottomInset) {
                    content(WindowInsets.statusBars.asPaddingValues())
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navInset + BarHeight + BarGap)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, c.background.copy(alpha = 0.72f)))),
            )
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset)) { data ->
                Box(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .liquidGlass(backdrop, RoundedCornerShape(18.dp), GlassStyles.Toast),
                ) {
                    Text(
                        data.visuals.message,
                        style = IosType.subheadline,
                        color = c.label,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                    )
                }
            }
            TabBar(
                current = tab,
                onTab = onTab,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = navInset + BarGap),
            )
        }
    }
}

@Composable
private fun MaterialFrame(
    tab: Tab,
    onTab: (Tab) -> Unit,
    snackbar: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset: Dp = navInset + 64.dp
    val paper = if (LocalUiStyle.current == UiStyle.SUMIE) Modifier.paperGrain(c.label) else Modifier
    Surface(color = c.background, contentColor = c.label, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(paper)) {
            CompositionLocalProvider(LocalBottomInset provides bottomInset) {
                content(WindowInsets.statusBars.asPaddingValues())
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 8.dp)) { data ->
                Snackbar(data, modifier = Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp))
            }
            ShortNavigationBar(modifier = Modifier.align(Alignment.BottomCenter), containerColor = c.bar) {
                Tab.entries.forEach { t ->
                    ShortNavigationBarItem(
                        selected = t == tab,
                        onClick = {
                            if (t != tab) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onTab(t)
                        },
                        icon = { Icon(if (t == tab) t.icon else t.iconIdle, contentDescription = null) },
                        label = { Text(t.title, maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBar(current: Tab, onTab: (Tab) -> Unit, backdrop: GlassBackdrop, modifier: Modifier) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    Box(modifier.fillMaxWidth().height(BarHeight).liquidGlass(backdrop, CircleShape, GlassStyles.Bar)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val item = maxWidth / Tab.entries.size
            val lensX by animateDpAsState(
                item * current.ordinal,
                spring(dampingRatio = 0.72f, stiffness = 380f),
                label = "lens",
            )
            Box(
                Modifier
                    .offset { IntOffset(lensX.roundToPx(), 0) }
                    .width(item)
                    .fillMaxHeight()
                    .background(if (c.dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f), CircleShape),
            )
            Row(Modifier.fillMaxSize()) {
                Tab.entries.forEach { t ->
                    TabItem(t, selected = t == current, modifier = Modifier.weight(1f)) {
                        if (t != current) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onTab(t)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ios.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint by animateColorAsState(if (selected) c.tint else c.label, spring(stiffness = 500f), label = "tint")
    val scale by animateFloatAsState(
        when {
            pressed -> 0.86f
            selected -> 1f
            else -> 0.96f
        },
        spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "scale",
    )
    Column(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            if (selected) tab.icon else tab.iconIdle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            tab.title,
            style = IosType.caption2.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = tint,
            maxLines = 1,
        )
    }
}

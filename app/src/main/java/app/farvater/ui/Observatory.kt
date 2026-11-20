package app.farvater.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.R
import app.farvater.ui.components.ConnectMode
import app.farvater.ui.components.LocalShellMotionDisabled
import app.farvater.ui.theme.Ios
import kotlin.math.cos
import kotlin.math.sin

internal val LocalAtlasPage = staticCompositionLocalOf { Tab.Home }

private fun DrawScope.star(at: Offset, radius: Float, color: Color) {
    drawLine(color, at - Offset(radius, 0f), at + Offset(radius, 0f), 1.dp.toPx())
    drawLine(color, at - Offset(0f, radius), at + Offset(0f, radius), 1.dp.toPx())
    drawCircle(color, radius * .28f, at)
}

@Composable
internal fun AtlasSky(tab: Tab, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val position by animateFloatAsState(tab.ordinal.toFloat(), tween(if (LocalShellMotionDisabled.current) 0 else 360), label = "atlasLongitude")
    Canvas(modifier) {
        val grid = c.tint.copy(alpha = .12f)
        val focus = Offset(size.width * (.12f + .23f * position), size.height * .16f)
        for (i in 1..5) drawCircle(grid, size.width * i * .19f, focus, style = Stroke(.65.dp.toPx()))
        for (i in 0..44) {
            val point = Offset(size.width * ((i * 173 + 39) % 457) / 457f, size.height * ((i * 79 + 7) % 263) / 263f)
            if (i % 6 == 0) star(point, 2.5.dp.toPx(), c.tint.copy(alpha = .45f))
            else drawCircle(c.secondaryLabel.copy(alpha = .35f), .85.dp.toPx(), point)
        }
    }
}

@Composable
internal fun OrbitMark(seed: Int, active: Boolean, modifier: Modifier = Modifier) {
    val c = Ios.colors
    Canvas(modifier) {
        val r = size.minDimension * .4f
        val stroke = if (active) c.tint else c.secondaryLabel
        drawCircle(stroke.copy(alpha = .55f), r, style = Stroke(1.dp.toPx()))
        rotate(-30f, center) {
            drawOval(stroke, center - Offset(r * 1.12f, r * .42f), Size(r * 2.24f, r * .84f), style = Stroke(1.dp.toPx()))
        }
        val angle = Math.toRadians(Math.floorMod(seed, 360).toDouble())
        drawCircle(stroke, r * .17f, center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * r)
        if (active) star(center, r * .42f, c.tint) else drawCircle(stroke, r * .15f)
    }
}

@Composable
internal fun AtlasHeading(title: String, subtitle: String?, modifier: Modifier, trailing: @Composable RowScope.() -> Unit) {
    val c = Ios.colors
    val page = LocalAtlasPage.current
    Column(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 9.dp)) {
        Canvas(Modifier.fillMaxWidth().height(25.dp)) {
            val line = c.tint.copy(alpha = .72f)
            val y = size.height * .67f
            when (page) {
                Tab.Sources -> {
                    val points = listOf(.04f to .63f, .23f to .23f, .42f to .77f, .7f to .3f, .94f to .55f)
                        .map { (x, h) -> Offset(x * size.width, h * size.height) }
                    points.zipWithNext().forEach { (a, b) -> drawLine(line, a, b, .9.dp.toPx()) }
                    points.forEach { star(it, 3.dp.toPx(), c.tint) }
                }
                Tab.Servers -> for (i in 0..5) {
                    val x = size.width * i / 5
                    drawOval(line, Offset(x - 21.dp.toPx(), y - 7.dp.toPx()), Size(42.dp.toPx(), 14.dp.toPx()), style = Stroke(.8.dp.toPx()))
                    drawCircle(c.tint, 2.dp.toPx(), Offset(x, y - 7.dp.toPx()))
                }
                Tab.Settings -> {
                    drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    for (i in 0..40) {
                        val x = size.width * i / 40
                        drawLine(line, Offset(x, y), Offset(x, y - (if (i % 5 == 0) 11.dp else 4.dp).toPx()), 1.dp.toPx())
                    }
                }
                Tab.Home -> {
                    drawLine(line, Offset(0f, y), Offset(size.width, y), .8.dp.toPx())
                    star(Offset(size.width * .16f, y), 7.dp.toPx(), c.tint)
                }
            }
        }
        val largeType = LocalDensity.current.fontScale > 1.25f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, color = c.label, style = MaterialTheme.typography.headlineLarge)
            if (!largeType) trailing()
        }
        if (subtitle != null) Text(subtitle, color = c.secondaryLabel, style = MaterialTheme.typography.bodyMedium)
        if (largeType) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = trailing)
    }
}

@Composable
internal fun AtlasTabIcon(tab: Tab, selected: Boolean, modifier: Modifier) {
    val drawable = when (tab) {
        Tab.Home -> R.drawable.ic_atlas_telescope
        Tab.Servers -> R.drawable.ic_atlas_planet
        Tab.Sources -> R.drawable.ic_atlas_constellation
        Tab.Settings -> R.drawable.ic_atlas_controls
    }
    androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(drawable), contentDescription = null,
        modifier = modifier, tint = if (selected) Ios.colors.tint else Ios.colors.secondaryLabel)
}

@Composable
internal fun ObservatoryFrame(tab: Tab, onTab: (Tab) -> Unit, snackbar: SnackbarHostState, content: @Composable (PaddingValues) -> Unit) {
    val c = Ios.colors
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = inset + 72.dp
    Box(Modifier.fillMaxSize().background(c.background)) {
        AtlasSky(tab, Modifier.matchParentSize())
        CompositionLocalProvider(LocalAtlasPage provides tab, LocalBottomInset provides bottom) { content(WindowInsets.statusBars.asPaddingValues()) }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 4.dp))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.bar).padding(bottom = inset)) {
            HorizontalDivider(color = c.barBorder)
            Row(Modifier.height(71.dp)) {
                Tab.entries.forEach { item ->
                    val chosen = item == tab
                    Column(Modifier.weight(1f).fillMaxHeight().selectable(chosen, role = Role.Tab, onClick = { onTab(item) }),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        AtlasTabIcon(item, chosen, Modifier.size(25.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(item.title, modifier = Modifier.fillMaxWidth(), color = if (chosen) c.tint else c.secondaryLabel, textAlign = TextAlign.Center,
                            fontSize = 12.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TelescopeConnect(mode: ConnectMode, onClick: () -> Unit) {
    val c = Ios.colors
    val still = LocalShellMotionDisabled.current
    val angle by animateFloatAsState(when (mode) {
        ConnectMode.Idle -> -12f
        ConnectMode.Searching -> -26f
        ConnectMode.Connecting -> -39f
        ConnectMode.Connected -> -52f
        ConnectMode.Error -> -6f
    }, tween(if (still) 0 else 650), label = "telescopeElevation")
    val label = stringResource(when (mode) {
        ConnectMode.Idle -> R.string.action_connect
        ConnectMode.Searching -> R.string.action_stop_search
        ConnectMode.Connecting -> R.string.action_cancel_connect
        ConnectMode.Connected -> R.string.action_disconnect
        ConnectMode.Error -> R.string.action_retry_connect
    })
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(176.dp)) {
            val unit = minOf(size.width / 300f, size.height / 170f)
            val mount = center + Offset(-9f, 7f) * unit
            val brass = if (mode == ConnectMode.Error) c.red else c.tint
            drawCircle(c.cell, 77f * unit, center)
            drawCircle(c.barBorder, 77f * unit, center, style = Stroke(1.dp.toPx()))
            for (i in 0 until 48) {
                val a = Math.PI * i / 24
                val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(c.barBorder, center + dir * (if (i % 4 == 0) 66f else 73f) * unit,
                    center + dir * 77f * unit, 1.dp.toPx())
            }
            drawLine(c.secondaryLabel, mount, mount + Offset(-32f, 53f) * unit, 4f * unit, StrokeCap.Round)
            drawLine(c.secondaryLabel, mount, mount + Offset(26f, 53f) * unit, 4f * unit, StrokeCap.Round)
            rotate(angle, mount) {
                drawLine(c.secondaryLabel, mount - Offset(33f, 0f) * unit, mount + Offset(7f, 0f) * unit, 8f * unit)
                drawLine(brass, mount - Offset(15f, 0f) * unit, mount + Offset(49f, 0f) * unit, 15f * unit)
                drawLine(c.label, mount + Offset(48f, -10f) * unit, mount + Offset(48f, 10f) * unit, 5f * unit)
                star(mount + Offset(93f, 0f) * unit, 5f * unit, brass)
            }
            drawCircle(c.label, 5f * unit, mount)
        }
        Text(label, color = c.tint, style = MaterialTheme.typography.titleLarge)
    }
}

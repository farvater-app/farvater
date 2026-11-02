package app.farvater.ui.components

import app.farvater.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.farvater.ui.theme.Ios
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun OneUiConnect(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val still = rememberWinterStill()
    val on = mode == ConnectMode.Connected
    val motion = rememberOneUiArcMotion(mode, still)
    var previous by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        val changed = previous != mode
        previous = mode
        if (!changed) return@LaunchedEffect
        when (mode) {
            ConnectMode.Connected -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            ConnectMode.Error -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            else -> Unit
        }
    }
    val arc by animateColorAsState(
        when (mode) {
            ConnectMode.Error -> c.red
            else -> c.tint
        },
        if (still) snap() else tween(400),
        label = "arc",
    )
    val core by animateColorAsState(if (on) c.tint else c.cell, if (still) snap() else tween(400), label = "core")
    val glyph by animateColorAsState(
        when {
            on -> androidx.compose.ui.graphics.Color.White
            mode == ConnectMode.Error -> c.red
            else -> c.tint
        },
        if (still) snap() else tween(400),
        label = "glyph",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f,
        if (still) snap() else spring(dampingRatio = 0.55f, stiffness = 500f), label = "press")

    val description = when (mode) {
        ConnectMode.Idle -> stringResource(R.string.action_connect)
        ConnectMode.Searching -> stringResource(R.string.action_stop_search)
        ConnectMode.Connecting -> stringResource(R.string.action_cancel_connect)
        ConnectMode.Connected -> stringResource(R.string.action_disconnect)
        ConnectMode.Error -> stringResource(R.string.action_retry_connect)
    }

    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(216.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val width = 14.dp.toPx()
                val inset = width / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - width, size.height - width)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(c.fill, 0f, 360f, false, topLeft, arcSize, style = Stroke(width))
                if (motion.sweep.value > 0f) {
                    drawArc(arc, motion.startAngle, motion.sweep.value, false, topLeft, arcSize,
                        style = Stroke(width, cap = StrokeCap.Round))
                }
            }
            Box(Modifier.size(150.dp).background(core, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = glyph, modifier = Modifier.size(56.dp))
            }
        }
    }
}

internal class OneUiArcMotion(mode: ConnectMode) {
    var startAngle by mutableFloatStateOf(-90f)
    val sweep = Animatable(oneUiSweep(mode))
}

@Composable
internal fun rememberOneUiArcMotion(mode: ConnectMode, still: Boolean): OneUiArcMotion {
    val motion = remember { OneUiArcMotion(mode) }
    val owner = LocalLifecycleOwner.current
    val busy = mode == ConnectMode.Searching || mode == ConnectMode.Connecting
    LaunchedEffect(mode, still, owner) {
        val target = oneUiSweep(mode)
        if (still) {
            motion.startAngle = -90f
            motion.sweep.snapTo(target)
        } else coroutineScope {
            launch { motion.sweep.animateTo(target, tween(if (busy) 250 else if (target > 0f) 700 else 300)) }
            if (busy) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previous = withInfiniteAnimationFrameMillis { it }
                while (true) withInfiniteAnimationFrameMillis { now ->
                    if (now - previous >= 32) {
                        motion.startAngle = (motion.startAngle + (now - previous) * 360f / 1100f) % 360f
                        previous = now
                    }
                }
            }
        }
    }
    return motion
}

private fun oneUiSweep(mode: ConnectMode) = when (mode) {
    ConnectMode.Idle -> 0f
    ConnectMode.Searching, ConnectMode.Connecting -> 96f
    ConnectMode.Connected, ConnectMode.Error -> 360f
}

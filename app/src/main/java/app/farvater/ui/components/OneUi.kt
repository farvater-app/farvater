package app.farvater.ui.components

import app.farvater.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
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
import app.farvater.ui.theme.Ios

@Composable
fun OneUiConnect(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val still = rememberReduceMotion()
    val on = mode == ConnectMode.Connected
    val busy = mode == ConnectMode.Searching || mode == ConnectMode.Connecting

    val fill = remember { Animatable(if (on) 1f else 0f) }
    var previous by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        val changed = previous != mode
        previous = mode
        val target = if (on || mode == ConnectMode.Error) 1f else 0f
        if (!changed || still) {
            fill.snapTo(target)
        } else {
            fill.animateTo(target, tween(if (target > 0f) 700 else 300))
        }
        if (!changed) return@LaunchedEffect
        when (mode) {
            ConnectMode.Connected -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            ConnectMode.Error -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            else -> Unit
        }
    }
    val spin by if (busy && !still) {
        rememberInfiniteTransition(label = "spin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "spin",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val arc by animateColorAsState(
        when (mode) {
            ConnectMode.Error -> c.red
            else -> c.tint
        },
        tween(400),
        label = "arc",
    )
    val core by animateColorAsState(if (on) c.tint else c.cell, tween(400), label = "core")
    val glyph by animateColorAsState(
        when {
            on -> androidx.compose.ui.graphics.Color.White
            mode == ConnectMode.Error -> c.red
            else -> c.tint
        },
        tween(400),
        label = "glyph",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "press")

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
                when {
                    busy -> drawArc(arc, spin - 90f, 96f, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
                    fill.value > 0f -> drawArc(arc, -90f, 360f * fill.value, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
                }
            }
            Box(Modifier.size(150.dp).background(core, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = glyph, modifier = Modifier.size(56.dp))
            }
        }
    }
}

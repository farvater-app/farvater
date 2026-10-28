package app.farvater.ui.components

import app.farvater.R
import androidx.compose.ui.res.stringResource
import app.farvater.ui.theme.localizedFont
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.OxygenFont

@Composable
fun OxygenConnect(mode: ConnectMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val still = rememberReduceMotion()
    val on = mode == ConnectMode.Connected
    val busy = mode == ConnectMode.Searching || mode == ConnectMode.Connecting

    val shake = remember { Animatable(0f) }
    var previous by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        val changed = previous != mode
        previous = mode
        if (!changed) return@LaunchedEffect
        when (mode) {
            ConnectMode.Connected -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            ConnectMode.Error -> {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                if (!still) {
                    shake.snapTo(8f)
                    shake.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 900f))
                }
            }
            else -> Unit
        }
    }
    val position by animateFloatAsState(
        if (on) 1f else 0f,
        if (still) tween(0) else tween(420, easing = FastOutSlowInEasing),
        label = "slide",
    )
    val track by animateColorAsState(
        when (mode) {
            ConnectMode.Connected -> c.tint
            ConnectMode.Error -> c.red.copy(alpha = 0.16f).compositeOver(c.cell)
            else -> c.fill
        },
        tween(420, easing = FastOutSlowInEasing),
        label = "track",
    )
    val glyph by animateColorAsState(
        when (mode) {
            ConnectMode.Connected -> c.tint
            ConnectMode.Error -> c.red
            else -> if (c.dark) Color(0xFF242424) else c.label
        },
        tween(360),
        label = "glyph",
    )
    val label by animateColorAsState(if (on) Color.White else c.secondaryLabel, tween(360), label = "label")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squash by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")

    val description = when (mode) {
        ConnectMode.Idle -> stringResource(R.string.action_connect)
        ConnectMode.Searching -> stringResource(R.string.action_stop_search)
        ConnectMode.Connecting -> stringResource(R.string.action_cancel_connect)
        ConnectMode.Connected -> stringResource(R.string.action_disconnect)
        ConnectMode.Error -> stringResource(R.string.action_retry_connect)
    }

    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier
                .width(272.dp)
                .height(96.dp)
                .graphicsLayer {
                    scaleX = squash
                    scaleY = squash
                    translationX = shake.value.dp.toPx()
                }
                .clip(RoundedCornerShape(50))
                .background(track)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(8.dp),
        ) {
            val travel = maxWidth - 80.dp
            val caption = TextStyle(fontFamily = localizedFont(OxygenFont), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 1.5.sp)
            Text(
                stringResource(R.string.switch_on),
                style = caption,
                color = label,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 22.dp)
                    .graphicsLayer { alpha = position },
            )
            Text(
                stringResource(R.string.switch_off),
                style = caption,
                color = label,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 22.dp)
                    .graphicsLayer { alpha = 1f - position },
            )
            Box(
                Modifier
                    .offset(x = travel * position)
                    .size(80.dp)
                    .shadow(6.dp, CircleShape)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(80.dp),
                        color = c.tint,
                        trackColor = Color.Transparent,
                        strokeWidth = 3.dp,
                    )
                }
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = glyph, modifier = Modifier.size(34.dp))
            }
        }
    }
}

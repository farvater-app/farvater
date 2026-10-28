package app.farvater.ui.components

import app.farvater.R
import androidx.compose.ui.res.stringResource
import app.farvater.ui.theme.localizedFont
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.ui.theme.HarmonyFont
import app.farvater.ui.theme.Ios

private val HarmonyCardOn = Color(0xFF0A59F7)
private val HarmonyCardOnEnd = Color(0xFF2F6EF5)

@Composable
fun HarmonyConnect(
    mode: ConnectMode,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

    val start by animateColorAsState(
        when (mode) {
            ConnectMode.Connected -> HarmonyCardOn
            ConnectMode.Error -> c.red.copy(alpha = 0.12f).compositeOver(c.cell)
            else -> c.cell
        },
        tween(420),
        label = "cardStart",
    )
    val end by animateColorAsState(
        when (mode) {
            ConnectMode.Connected -> HarmonyCardOnEnd
            ConnectMode.Error -> c.red.copy(alpha = 0.06f).compositeOver(c.cell)
            else -> c.cell
        },
        tween(420),
        label = "cardEnd",
    )
    val title by animateColorAsState(if (on) Color.White else c.label, tween(420), label = "title")
    val sub by animateColorAsState(if (on) Color.White.copy(alpha = 0.95f) else c.secondaryLabel, tween(420), label = "sub")
    val knob by animateColorAsState(
        when {
            on -> Color.White
            mode == ConnectMode.Error -> c.red
            else -> c.tint
        },
        tween(420),
        label = "knob",
    )
    val glyph by animateColorAsState(if (on) HarmonyCardOn else Color.White, tween(420), label = "glyph")

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "press")

    val description = when (mode) {
        ConnectMode.Idle -> stringResource(R.string.action_connect)
        ConnectMode.Searching -> stringResource(R.string.action_stop_search)
        ConnectMode.Connecting -> stringResource(R.string.action_cancel_connect)
        ConnectMode.Connected -> stringResource(R.string.action_disconnect)
        ConnectMode.Error -> stringResource(R.string.action_retry_connect)
    }

    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = shake.value.dp.toPx()
                }
                .clip(RoundedCornerShape(28.dp))
                .drawBehind {
                    drawRect(Brush.linearGradient(listOf(start, end), start = Offset.Zero, end = Offset(size.width, size.height)))
                    if (on) {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(size.width * 0.86f, size.height * 0.08f),
                                radius = size.width * 0.55f,
                            ),
                            radius = size.width * 0.55f,
                            center = Offset(size.width * 0.86f, size.height * 0.08f),
                        )
                    }
                }
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = description
                    stateDescription = status
                }
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = title, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("VPN", style = TextStyle(fontFamily = localizedFont(HarmonyFont), fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp), color = title)
            }
            Text(
                status,
                style = TextStyle(fontFamily = localizedFont(HarmonyFont), fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
                color = sub,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(end = 104.dp),
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(84.dp)
                    .background(knob, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(84.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeWidth = 3.dp,
                    )
                }
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = glyph, modifier = Modifier.size(36.dp))
            }
        }
    }
}

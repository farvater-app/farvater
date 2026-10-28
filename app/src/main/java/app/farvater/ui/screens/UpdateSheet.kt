package app.farvater.ui.screens

import app.farvater.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.farvater.ui.components.AppLogo
import app.farvater.BuildConfig
import app.farvater.data.UpdateInfo
import app.farvater.ui.UpdateStage
import app.farvater.ui.ios.IosButton
import app.farvater.ui.ios.IosButtonStyle
import app.farvater.ui.ios.IosGrabber
import app.farvater.ui.ios.IosProgressBar
import app.farvater.ui.ios.SectionCorner
import app.farvater.ui.ios.SheetCorner
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType
import kotlinx.coroutines.launch

@Composable
fun UpdateSheet(info: UpdateInfo, stage: UpdateStage, onUpdate: () -> Unit, onLater: () -> Unit) {
    val c = Ios.colors
    val corner = SheetCorner
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(1f) }
    LaunchedEffect(Unit) { slide.animateTo(0f, spring(dampingRatio = 0.88f, stiffness = 300f)) }
    val close: () -> Unit = {
        scope.launch {
            slide.animateTo(1f, spring(dampingRatio = 1f, stiffness = 520f))
            onLater()
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f * (1f - slide.value)))) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .graphicsLayer { translationY = size.height * slide.value }
                    .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
                    .background(c.background)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                IosGrabber()
                Spacer(Modifier.height(24.dp))
                AppLogo(64.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    if (info.critical) stringResource(R.string.update_important) else stringResource(R.string.update_ready),
                    style = IosType.title2,
                    color = c.label,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.update_versions, BuildConfig.VERSION_NAME, info.versionName) + if (info.published.isNotBlank()) " · ${info.published}" else "",
                    style = IosType.subheadline,
                    color = c.secondaryLabel,
                )
                Spacer(Modifier.height(18.dp))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SectionCorner))
                        .background(c.cell)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.whats_new), style = IosType.footnote, color = c.secondaryLabel)
                    Spacer(Modifier.height(8.dp))
                    info.notes.ifEmpty { listOf(stringResource(R.string.update_generic_notes)) }.forEach { note ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(c.tint))
                            Spacer(Modifier.width(10.dp))
                            Text(note, style = IosType.subheadline, color = c.label)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                when (stage) {
                    is UpdateStage.Downloading -> {
                        Text(
                            stringResource(R.string.update_downloading, (stage.progress * 100).toInt()),
                            style = IosType.subheadline.copy(fontWeight = FontWeight.Medium),
                            color = c.label,
                        )
                        Spacer(Modifier.height(10.dp))
                        IosProgressBar(stage.progress)
                        Spacer(Modifier.height(52.dp))
                    }
                    else -> {
                        if (stage is UpdateStage.Failed) {
                            Text(stage.message.replaceFirstChar { it.uppercase() }, style = IosType.footnote, color = c.red, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(10.dp))
                        }
                        IosButton(
                            if (stage is UpdateStage.Failed) stringResource(R.string.action_try_again) else stringResource(R.string.action_update),
                            onClick = onUpdate,
                            color = if (info.critical) c.red else c.tint,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        IosButton(stringResource(R.string.action_later), onClick = close, style = IosButtonStyle.Plain, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

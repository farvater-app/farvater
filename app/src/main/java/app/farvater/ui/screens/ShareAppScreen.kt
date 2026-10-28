package app.farvater.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.farvater.R
import app.farvater.data.ApkSharing
import app.farvater.data.LocalizedError
import app.farvater.data.userMessage
import app.farvater.ui.ios.IosButton
import app.farvater.ui.ios.IosSection
import app.farvater.ui.ios.IosSpinner
import app.farvater.ui.ltrText
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ShareAppPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.share_app)
    val unavailable = stringResource(R.string.share_apk_unavailable)
    var architectures by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        try {
            architectures = ApkSharing.architectures(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = if (e is LocalizedError) e.userMessage(context) else unavailable
        }
    }
    ShareAppContent(architectures, preparing, error) {
        if (preparing) return@ShareAppContent
        scope.launch {
            preparing = true
            error = null
            try {
                val send = ApkSharing.prepare(context)
                context.startActivity(Intent.createChooser(send, title))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = if (e is LocalizedError) e.userMessage(context) else unavailable
            } finally {
                preparing = false
            }
        }
    }
}

@Composable
fun ShareAppContent(architectures: List<String>?, preparing: Boolean, error: String?, onShare: () -> Unit) {
    val c = Ios.colors
    IosSection(footer = stringResource(R.string.share_apk_private)) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.share_apk_explanation), style = IosType.body, color = c.label)
            Spacer(Modifier.height(12.dp))
            if (architectures != null) {
                Text(stringResource(R.string.share_apk_architectures, ltrText(architectures.joinToString(", "))), style = IosType.subheadline, color = c.secondaryLabel)
            } else if (error == null) {
                IosSpinner()
            }
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error, style = IosType.subheadline, color = c.label)
            }
            Spacer(Modifier.height(20.dp))
            IosButton(
                stringResource(if (preparing) R.string.share_apk_preparing else R.string.action_share),
                onClick = onShare,
                icon = Icons.Rounded.Share,
                enabled = architectures != null && !preparing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

package app.farvater.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.PersistableBundle
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.farvater.R
import app.farvater.ui.ios.*
import app.farvater.vpn.*

@Composable
internal fun VpnControls() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val vpn by VpnBus.state.collectAsStateWithLifecycle()
    var resumed by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumed++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val lockdown = remember(resumed, vpn) { FarvaterVpnService.lockdownStatus() }
    var issued by remember { mutableStateOf(AutomationAccess.current(context) != null) }
    var confirm by remember { mutableStateOf(false) }
    fun error() { Toast.makeText(context, R.string.state_error, Toast.LENGTH_SHORT).show() }
    IosSection(footer = stringResource(R.string.kill_hint)) {
        IosRow(stringResource(R.string.kill_title), titleLines = 3, subtitle = stringResource(when (lockdown) {
            true -> R.string.kill_on
            false -> R.string.kill_off
            null -> R.string.kill_unknown
        }), chevron = true, onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }.onFailure { error() }
        })
    }
    IosSection(header = stringResource(R.string.automation_title),
        footer = stringResource(R.string.automation_help, app.farvater.ui.ltrText(context.packageName), app.farvater.ui.ltrText(AutomationReceiver::class.java.name),
            app.farvater.ui.ltrText(AutomationReceiver.CONNECT), app.farvater.ui.ltrText(AutomationReceiver.DISCONNECT))) {
        IosRow(stringResource(R.string.automation_issue), titleLines = 3, subtitle = stringResource(
            if (issued) R.string.automation_active else R.string.automation_inactive), onClick = { confirm = true })
        if (issued) IosRow(stringResource(R.string.automation_revoke), titleLines = 3, onClick = {
            AutomationAccess.revoke(context)
            issued = AutomationAccess.current(context) != null
            if (issued) error()
        })
    }
    if (confirm) ActionConfirmation(stringResource(R.string.automation_title), stringResource(R.string.automation_warn),
        stringResource(R.string.automation_issue), destructive = false, onDismiss = { confirm = false }, onConfirm = {
                confirm = false
                runCatching {
                    val key = AutomationAccess.issue(context)
                    try {
                        val clip = ClipData.newPlainText("Farvater", key)
                        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    } catch (e: RuntimeException) {
                        AutomationAccess.revoke(context)
                        throw e
                    }
                }.onFailure { error() }
                issued = AutomationAccess.current(context) != null
        })
}

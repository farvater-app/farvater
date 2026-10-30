package app.farvater.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalConfiguration
import app.farvater.ui.theme.IosType
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.farvater.R
import app.farvater.ui.ios.IosAlert
import app.farvater.ui.ios.IosButton
import app.farvater.ui.ios.IosButtonStyle
import app.farvater.ui.ios.IosSection
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSwitch
import app.farvater.data.BackupSecurityChanges
import app.farvater.ui.theme.Ios

@Composable
fun BackupPage(
    busy: Boolean,
    preview: Pair<Int, Int>?,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onRestore: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    securityChanges: BackupSecurityChanges = BackupSecurityChanges(),
) {
    var warnExport by remember { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(onExport)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onImport) }
    IosSection(footer = stringResource(R.string.backup_description)) {
        Column(Modifier.padding(16.dp)) {
            IosButton(stringResource(R.string.backup_export), modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = { warnExport = true })
            Spacer(Modifier.height(12.dp))
            IosButton(stringResource(R.string.backup_import), modifier = Modifier.fillMaxWidth(), style = IosButtonStyle.Tinted, color = Ios.colors.label, enabled = !busy, onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
        }
    }
    if (warnExport) BackupConfirmation(
        title = stringResource(R.string.backup_title),
        message = stringResource(R.string.backup_secrets),
        action = stringResource(R.string.backup_export),
        destructive = false,
        onDismiss = { warnExport = false },
        onConfirm = {
            warnExport = false
            create.launch("Farvater-backup.json")
        },
    )
    if (preview != null && !busy) {
        var includeSecurity by remember(preview, securityChanges) { mutableStateOf(false) }
        val transfer = stringResource(R.string.backup_security_transfer)
        val keep = stringResource(R.string.backup_security_keep)
        val changes = backupSecuritySummary(securityChanges)
        BackupConfirmation(
            title = stringResource(R.string.backup_restore),
            message = stringResource(R.string.backup_preview, preview.first, preview.second) + "\n\n" + keep,
            action = stringResource(R.string.backup_restore),
            destructive = true,
            onDismiss = onDismiss,
            onConfirm = { onRestore(includeSecurity) },
            details = {
                if (!securityChanges.isEmpty) {
                    Spacer(Modifier.height(16.dp))
                    Text(changes, modifier = Modifier.fillMaxWidth(), style = IosType.subheadline, color = Ios.colors.label)
                    if (LocalDensity.current.fontScale > 1.3f) {
                        Column(Modifier.fillMaxWidth().toggleable(includeSecurity, role = Role.Switch) { includeSecurity = it }
                            .padding(vertical = 12.dp)) {
                            Text(transfer, modifier = Modifier.fillMaxWidth(), style = IosType.body, color = Ios.colors.label)
                            IosSwitch(includeSecurity, null, Modifier.align(Alignment.End))
                        }
                    } else {
                        IosRow(transfer, titleLines = Int.MAX_VALUE, checked = includeSecurity, onCheckedChange = { includeSecurity = it })
                    }
                }
            },
        )
    }
    if (busy) IosAlert(
        title = stringResource(R.string.backup_title),
        message = stringResource(R.string.backup_working),
        onDismiss = {},
        actions = emptyList(),
    )
}

@Composable
private fun BackupConfirmation(title: String, message: String, action: String, destructive: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit, details: @Composable () -> Unit = {}) {
    val cancel = stringResource(R.string.action_cancel)
    val readMore = stringResource(R.string.backup_read_more)
    IosAlert(title = title, onDismiss = onDismiss, actions = emptyList(), content = {
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.6f).dp)) {
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(scroll)) {
                Text(message, style = IosType.subheadline, color = Ios.colors.label, modifier = Modifier.fillMaxWidth())
                details()
            }
            if (scroll.maxValue > 0) {
                IosButton(readMore,
                    onClick = { scope.launch { scroll.scrollTo((scroll.value + scroll.viewportSize).coerceAtMost(scroll.maxValue)) } },
                    modifier = Modifier.fillMaxWidth(), style = IosButtonStyle.Plain, color = Ios.colors.label,
                    enabled = scroll.canScrollForward, maxLines = 3)
            }
            Spacer(Modifier.height(16.dp))
            IosButton(cancel, onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                style = IosButtonStyle.Tinted, color = Ios.colors.label, maxLines = 3)
            Spacer(Modifier.height(10.dp))
            IosButton(action, onClick = onConfirm, modifier = Modifier.fillMaxWidth(),
                color = if (destructive) Ios.colors.red else Ios.colors.tint, maxLines = 3)
        }
    })
}

@Composable
private fun backupSecuritySummary(changes: BackupSecurityChanges): String {
    val lines = mutableListOf<String>()
    if (changes.addedBypassApps.isNotEmpty()) lines += stringResource(R.string.backup_security_bypass_add, changes.addedBypassApps.sorted().joinToString("\n"))
    if (changes.removedBypassApps.isNotEmpty()) lines += stringResource(R.string.backup_security_bypass_remove, changes.removedBypassApps.sorted().joinToString("\n"))
    listOf(changes.safeMode to R.string.safe_nodes, changes.encryptedDns to R.string.encrypted_dns,
        changes.autoUpdates to R.string.backup_security_updates, changes.sendHwid to R.string.send_hwid,
        changes.directRuServices to R.string.backup_security_ru_direct).forEach { (value, title) ->
        if (value != null) lines += stringResource(if (value) R.string.backup_security_enable else R.string.backup_security_disable, stringResource(title))
    }
    return lines.joinToString("\n\n")
}

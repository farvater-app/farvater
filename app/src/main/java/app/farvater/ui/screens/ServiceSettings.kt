package app.farvater.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import app.farvater.ui.ios.IosButton
import app.farvater.ui.ios.IosButtonStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.farvater.R
import app.farvater.data.AppSettings
import app.farvater.data.ServiceChecks
import app.farvater.data.ServiceKind
import app.farvater.ui.ios.IosAlert
import app.farvater.ui.ios.IosDivider
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSection
import app.farvater.ui.ios.IosTextField
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType

@Composable
fun serviceName(kind: ServiceKind): String = stringResource(when (kind) {
    ServiceKind.GEMINI -> R.string.service_gemini
    ServiceKind.CHATGPT -> R.string.service_chatgpt
    ServiceKind.CLAUDE -> R.string.service_claude
    ServiceKind.YOUTUBE -> R.string.service_youtube
    ServiceKind.CUSTOM -> R.string.service_custom
})

@Composable
fun ServiceSettings(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var url by remember(settings.serviceCustomUrl) { mutableStateOf(settings.serviceCustomUrl) }
    var code by remember(settings.serviceExpectedCode) { mutableStateOf(settings.serviceExpectedCode.toString()) }
    var invalid by remember { mutableStateOf(false) }
    IosSection(footer = stringResource(R.string.services_hint)) {
        IosRow(title = stringResource(R.string.services_enable), titleLines = 2,
            checked = settings.serviceChecksEnabled, onCheckedChange = { onChange { old -> old.copy(serviceChecksEnabled = it) } })
        if (settings.serviceChecksEnabled) {
            ServiceChecks.builtIn.forEach { target ->
                IosDivider()
                IosRow(title = serviceName(target.kind), checked = target.kind in settings.checkedServices,
                    onCheckedChange = { checked -> onChange {
                        it.copy(checkedServices = if (checked) it.checkedServices + target.kind else it.checkedServices - target.kind)
                    } })
            }
            IosDivider()
            IosRow(title = stringResource(R.string.services_add_custom), chevron = true, onClick = { invalid = false; editing = true })
            if (settings.serviceCustomUrl.isNotEmpty()) {
                IosDivider()
                IosRow(title = serviceName(ServiceKind.CUSTOM),
                    subtitle = stringResource(R.string.services_expected_code, settings.serviceExpectedCode),
                    checked = ServiceKind.CUSTOM in settings.checkedServices,
                    onCheckedChange = { checked -> onChange { it.copy(checkedServices = if (checked) it.checkedServices + ServiceKind.CUSTOM else it.checkedServices - ServiceKind.CUSTOM) } })
            }
        }
    }
    val urlLabel = stringResource(R.string.services_url)
    val codeLabel = stringResource(R.string.services_status)
    val hint = stringResource(R.string.services_custom_hint)
    val invalidText = stringResource(R.string.services_invalid)
    val cancelText = stringResource(R.string.action_cancel)
    val saveText = stringResource(R.string.action_save)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    if (editing) IosAlert(
        title = stringResource(R.string.service_custom),
        onDismiss = { editing = false },
        actions = emptyList(),
        content = {
            Column(Modifier.heightIn(max = maxHeight)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Text(hint, color = Ios.colors.label, style = IosType.footnote)
                    Spacer(Modifier.height(12.dp))
                    Text(urlLabel, color = Ios.colors.label, style = IosType.footnote)
                    IosTextField(url, { url = it.take(2048) }, urlLabel, label = urlLabel)
                    Spacer(Modifier.height(8.dp))
                    Text(codeLabel, color = Ios.colors.label, style = IosType.footnote)
                    IosTextField(code, { code = it.filter(Char::isDigit).take(3) }, codeLabel, label = codeLabel)
                    if (invalid) Text(invalidText, color = Ios.colors.label, style = IosType.footnote)
                }
                Spacer(Modifier.height(12.dp))
                IosButton(saveText, modifier = Modifier.fillMaxWidth(), maxLines = 3, onClick = {
                    val parsed = ServiceChecks.customUrl(url)
                    val status = code.toIntOrNull()
                    if (parsed == null || status == null || status !in 200..599) invalid = true else {
                        onChange { it.copy(serviceCustomUrl = parsed.toString(), serviceExpectedCode = status, checkedServices = it.checkedServices + ServiceKind.CUSTOM) }
                        editing = false
                    }
                })
                Spacer(Modifier.height(8.dp))
                IosButton(cancelText, { editing = false }, Modifier.fillMaxWidth(),
                    style = IosButtonStyle.Tinted, color = Ios.colors.label, maxLines = 3)
            }
        },
    )
}

package app.farvater.ui.screens

import app.farvater.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.farvater.core.model.SourceMode
import app.farvater.ui.SourceUi
import app.farvater.ui.ios.IosChoiceRow
import app.farvater.ui.ios.IosDivider
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSection
import app.farvater.ui.ios.IosSheet
import app.farvater.ui.ios.IosSegmented
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType

private val Modes = listOf(SourceMode.ANY, SourceMode.WHITE, SourceMode.BLACK)

private val Intervals: List<Pair<Int?, String>>
    @Composable get() = listOf(
    null to stringResource(R.string.automatic),
    1 to stringResource(R.string.interval_hourly),
    3 to stringResource(R.string.interval_three_hours),
    6 to stringResource(R.string.interval_six_hours),
    12 to stringResource(R.string.interval_twelve_hours),
    24 to stringResource(R.string.interval_daily),
    0 to stringResource(R.string.interval_manual_only),
)

@Composable
fun modeShort(mode: SourceMode): String = when (mode) {
    SourceMode.ANY -> stringResource(R.string.mode_both_short)
    SourceMode.WHITE -> stringResource(R.string.mode_white_short)
    SourceMode.BLACK -> stringResource(R.string.mode_black_short)
}

@Composable
fun intervalLabel(source: SourceUi): String = when (val h = source.intervalHours) {
    0 -> stringResource(R.string.interval_manual)
    null -> stringResource(R.string.interval_auto, hours(source.ownIntervalHours?.takeIf { it > 0 } ?: 6))
    else -> stringResource(R.string.interval_every, hours(h))
}

@Composable
private fun hours(h: Int): String = when (h) {
    1 -> stringResource(R.string.interval_one_hour)
    24 -> stringResource(R.string.interval_one_day)
    else -> androidx.compose.ui.res.pluralStringResource(R.plurals.hours_count, h, h)
}

@Composable
fun SourceSheet(
    source: SourceUi,
    status: String,
    onMode: (SourceMode) -> Unit,
    onInterval: (Int?) -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Ios.colors
    val uri = LocalUriHandler.current
    IosSheet(title = source.title, onClose = onClose) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 40.dp),
        ) {
            Text(
                status,
                style = IosType.footnote,
                color = c.secondaryLabel,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 14.dp),
            )

            IosSection(
                header = stringResource(R.string.network_mode_title),
                footer = stringResource(R.string.source_mode_hint),
            ) {
                IosSegmented(
                    options = Modes.map { modeShort(it) },
                    selected = Modes.indexOf(source.mode),
                    onSelect = { onMode(Modes[it]) },
                    modifier = Modifier.padding(12.dp),
                )
            }

            IosSection(
                header = stringResource(R.string.refresh_interval),
                footer = stringResource(R.string.refresh_interval_hint),
            ) {
                Intervals.forEachIndexed { i, (value, label) ->
                    if (i > 0) IosDivider()
                    IosChoiceRow(
                        title = label,
                        selected = source.intervalHours == value,
                        onClick = { onInterval(value) },
                    )
                }
            }

            IosSection {
                IosRow(
                    title = stringResource(R.string.refresh_now),
                    titleColor = c.tint,
                    icon = Icons.Rounded.Refresh,
                    iconTint = c.tint,
                    onClick = onRefresh,
                )
                if (source.community) {
                    IosDivider(start = 59.dp)
                    IosRow(
                        title = if (source.homepage.startsWith("https://t.me/")) stringResource(R.string.telegram_channel) else stringResource(R.string.project_page),
                        subtitle = source.homepage.removePrefix("https://"),
                        titleColor = c.tint,
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        iconTint = c.blue,
                        onClick = { runCatching { uri.openUri(source.homepage) } },
                    )
                    source.forum?.let { forum ->
                        IosDivider(start = 59.dp)
                        IosRow(
                            title = stringResource(R.string.forum),
                            subtitle = forum.removePrefix("https://"),
                            titleColor = c.tint,
                            icon = Icons.Rounded.Forum,
                            iconTint = c.indigo,
                            onClick = { runCatching { uri.openUri(forum) } },
                        )
                    }
                } else {
                    IosDivider(start = 59.dp)
                    IosRow(
                        title = stringResource(R.string.subscription_delete),
                        titleColor = c.red,
                        icon = Icons.Rounded.DeleteOutline,
                        iconTint = c.red,
                        onClick = onRemove,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

package app.farvater.ui.screens

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

private val Intervals = listOf(
    null to "Авто",
    1 to "Каждый час",
    3 to "Каждые 3 часа",
    6 to "Каждые 6 часов",
    12 to "Каждые 12 часов",
    24 to "Раз в сутки",
    0 to "Только вручную",
)

fun modeShort(mode: SourceMode): String = when (mode) {
    SourceMode.ANY -> "БС и ЧС"
    SourceMode.WHITE -> "БС"
    SourceMode.BLACK -> "ЧС"
}

fun intervalLabel(source: SourceUi): String = when (val h = source.intervalHours) {
    0 -> "вручную"
    null -> "авто, раз в ${hours(source.ownIntervalHours?.takeIf { it > 0 } ?: 6)}"
    else -> "раз в ${hours(h)}"
}

private fun hours(h: Int): String = when {
    h == 1 -> "час"
    h == 24 -> "сутки"
    h % 10 in 2..4 && h % 100 !in 12..14 -> "$h часа"
    else -> "$h часов"
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
                header = "Режим сети",
                footer = "БС — белые списки: открываются только разрешённые сайты, нужны особые серверы. " +
                    "ЧС — обычные блокировки: закрыты отдельные сайты, хватает обычных серверов. " +
                    "Подписка работает только в выбранном режиме.",
            ) {
                IosSegmented(
                    options = Modes.map(::modeShort),
                    selected = Modes.indexOf(source.mode),
                    onSelect = { onMode(Modes[it]) },
                    modifier = Modifier.padding(12.dp),
                )
            }

            IosSection(
                header = "Обновлять",
                footer = "Авто — как просит сама подписка, а если она молчит, раз в 6 часов.",
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
                    title = "Обновить сейчас",
                    titleColor = c.tint,
                    icon = Icons.Rounded.Refresh,
                    iconTint = c.tint,
                    onClick = onRefresh,
                )
                if (source.community) {
                    IosDivider(start = 59.dp)
                    IosRow(
                        title = if (source.homepage.startsWith("https://t.me/")) "Телеграм-канал" else "Страница проекта",
                        subtitle = source.homepage.removePrefix("https://"),
                        titleColor = c.tint,
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        iconTint = c.blue,
                        onClick = { runCatching { uri.openUri(source.homepage) } },
                    )
                    source.forum?.let { forum ->
                        IosDivider(start = 59.dp)
                        IosRow(
                            title = "Форум",
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
                        title = "Удалить подписку",
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

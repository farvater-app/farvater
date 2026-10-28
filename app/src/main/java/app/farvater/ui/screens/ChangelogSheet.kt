package app.farvater.ui.screens

import app.farvater.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.farvater.BuildConfig
import app.farvater.ui.ios.IosSheet
import app.farvater.ui.ios.IosSpinner
import app.farvater.ui.ios.SectionCorner
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChangelogGroup(val title: String, val items: List<String>)

data class ChangelogRelease(val version: String, val date: String?, val intro: List<String>, val groups: List<ChangelogGroup>)

private val ReleaseHeader = Regex("""^## \[(.+?)\](?:\s+—\s+(\d{4})-(\d{2})-(\d{2}))?""")

private val Months = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

private fun plain(text: String): String = text.replace("`", "").replace("**", "").trim()

fun parseChangelog(text: String): List<ChangelogRelease> {
    val releases = mutableListOf<ChangelogRelease>()
    var version: String? = null
    var date: String? = null
    val intro = mutableListOf<String>()
    val groups = mutableListOf<ChangelogGroup>()
    var groupTitle: String? = null
    val items = mutableListOf<String>()

    fun closeGroup() {
        val title = groupTitle ?: return
        if (items.isNotEmpty()) groups += ChangelogGroup(title, items.toList())
        groupTitle = null
        items.clear()
    }

    fun closeRelease() {
        closeGroup()
        val v = version ?: return
        if (groups.isNotEmpty() || intro.isNotEmpty()) releases += ChangelogRelease(v, date, intro.toList(), groups.toList())
        version = null
        date = null
        intro.clear()
        groups.clear()
    }

    for (raw in text.lines()) {
        val line = raw.trim()
        val header = ReleaseHeader.find(line)
        when {
            header != null -> {
                closeRelease()
                val name = header.groupValues[1]
                if (name.first().isDigit()) {
                    version = name
                    date = header.groupValues[2].takeIf { it.isNotEmpty() }?.let {
                        val month = header.groupValues[3].toInt()
                        "${header.groupValues[4].toInt()} ${Months[month - 1]} $it"
                    }
                }
            }
            version == null -> Unit
            line.startsWith("### ") -> {
                closeGroup()
                groupTitle = line.removePrefix("### ").trim()
            }
            line.startsWith("- ") && groupTitle != null -> items += plain(line.removePrefix("- "))
            line.startsWith("[") || line.isEmpty() -> Unit
            groupTitle == null -> intro += plain(line)
        }
    }
    closeRelease()
    return releases
}

@Composable
fun ChangelogSheet(onClose: () -> Unit) {
    val c = Ios.colors
    val context = LocalContext.current
    val releases by produceState<List<ChangelogRelease>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { parseChangelog(context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }) }
                .getOrDefault(emptyList())
        }
    }
    IosSheet(title = stringResource(R.string.changelog_title), onClose = onClose) {
        val list = releases
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { IosSpinner() }
            list.isEmpty() -> Text(
                stringResource(R.string.changelog_open_error),
                style = IosType.body,
                color = c.secondaryLabel,
                modifier = Modifier.padding(32.dp),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                items(list, key = { it.version }) { release -> ReleaseCard(release) }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: ChangelogRelease) {
    val c = Ios.colors
    val installed = release.version == BuildConfig.VERSION_NAME
    Column(Modifier.padding(top = 16.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.version_label, release.version), style = IosType.headline, color = c.label)
            if (installed) {
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.version_installed),
                    style = IosType.caption1,
                    color = c.tint,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.tint.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            release.date?.let { Text(it, style = IosType.footnote, color = c.secondaryLabel) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SectionCorner))
                .background(c.cell)
                .padding(16.dp),
        ) {
            release.intro.forEach {
                Text(it, style = IosType.subheadline, color = c.secondaryLabel)
                Spacer(Modifier.height(8.dp))
            }
            release.groups.forEachIndexed { i, group ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Text(group.title.uppercase(), style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold), color = groupColor(group.title))
                Spacer(Modifier.height(6.dp))
                group.items.forEach { item ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•", style = IosType.subheadline, color = groupColor(group.title))
                        Spacer(Modifier.width(8.dp))
                        Text(item, style = IosType.subheadline, color = c.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun groupColor(title: String): Color {
    val c = Ios.colors
    if (Ios.nothing) return when (title) {
        "Добавлено" -> if (c.dark) Color(0xFF4CD07D) else Color(0xFF1B8A4B)
        "Изменено" -> if (c.dark) Color(0xFF5B9BFF) else Color(0xFF1D5FD1)
        "Удалено" -> c.red
        else -> c.label
    }
    return when (title) {
        "Добавлено" -> c.green
        "Исправлено" -> c.blue
        "Удалено" -> c.red
        else -> c.tint
    }
}

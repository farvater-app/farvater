package app.farvater.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.farvater.ui.ios.IosDivider
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSheet
import app.farvater.ui.ios.IosSearchField
import app.farvater.ui.ios.IosSpinner
import app.farvater.ui.ios.IosSwitch
import app.farvater.ui.ios.SectionCorner
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(val pkg: String, val label: String)

@Composable
fun BypassAppsSheet(selected: Set<String>, ruApps: Set<String>, onChange: (Set<String>) -> Unit, onClose: () -> Unit) {
    val c = Ios.colors
    val context = LocalContext.current
    val pm = context.packageManager
    var query by remember { mutableStateOf("") }
    val initial = remember { selected + ruApps }
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { loadApps(pm, context.packageName, initial) }
    }
    val icons = remember { HashMap<String, ImageBitmap?>() }

    IosSheet(title = "Приложения мимо VPN", onClose = onClose) {
        IosSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Поиск приложений",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Text(
            "Отмеченные приложения ходят в интернет напрямую. Изменения применяются сразу." +
                if (ruApps.isEmpty()) "" else " Российские приложения идут напрямую сами, пока в настройках подключения включены «Российские сервисы».",
            style = IosType.footnote,
            color = c.secondaryLabel,
            modifier = Modifier.padding(horizontal = 32.dp).padding(bottom = 10.dp),
        )

        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { IosSpinner() }
        } else {
            val shown = remember(list, query) {
                val q = query.trim()
                if (q.isEmpty()) list else list.filter { it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                itemsIndexed(shown, key = { _, app -> app.pkg }) { index, app ->
                    val top = if (index == 0) SectionCorner else 0.dp
                    val bottom = if (index == shown.lastIndex) SectionCorner else 0.dp
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
                            .background(c.cell),
                    ) {
                        val checked = app.pkg in selected
                        val toggle = { v: Boolean -> onChange(if (v) selected + app.pkg else selected - app.pkg) }
                        if (app.pkg in ruApps) {
                            IosRow(title = app.label, leading = { AppIcon(app.pkg, pm, icons) }, value = "напрямую")
                        } else {
                            IosRow(
                                title = app.label,
                                leading = { AppIcon(app.pkg, pm, icons) },
                                onClick = { toggle(!checked) },
                                trailing = { IosSwitch(checked, toggle) },
                            )
                        }
                        if (index != shown.lastIndex) IosDivider(start = 60.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(pkg: String, pm: PackageManager, cache: HashMap<String, ImageBitmap?>) {
    val icon by produceState(cache[pkg], pkg) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            }
            cache[pkg] = value
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(32.dp))
    } else {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Ios.colors.fill))
    }
}

@Suppress("DEPRECATION")
private fun loadApps(pm: PackageManager, self: String, selected: Set<String>): List<InstalledApp> =
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .asSequence()
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .filter { it.first != self }
        .distinctBy { it.first }
        .map { InstalledApp(it.first, it.second) }
        .sortedWith(compareBy<InstalledApp>({ it.pkg !in selected }, { it.label.lowercase() }))
        .toList()

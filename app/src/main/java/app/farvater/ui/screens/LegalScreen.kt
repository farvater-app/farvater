package app.farvater.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.List
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farvater.R
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSheet
import app.farvater.ui.theme.Ios

object LegalDocs {
    const val TERMS = "terms.txt"
    const val PRIVACY = "privacy.txt"
    const val LICENSES = "licenses.txt"
}

private val sectionTitle = Regex("""^(\d{1,2})\. (\S.*)""")

internal fun documentHeadings(paragraphs: List<String>): List<Int> {
    val result = mutableListOf<Int>()
    paragraphs.forEachIndexed { i, text ->
        val number = sectionTitle.matchEntire(text)?.groupValues?.get(1)?.toIntOrNull()
        if (number == result.size + 1 && text.length < 100 && !text.endsWith('.')) result += i
    }
    return result
}

@Composable
fun LegalDocument(asset: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val failure = stringResource(R.string.document_open_error)
    val lines = remember(asset, context, failure) {
        runCatching { context.assets.open(asset).bufferedReader().use { it.readLines().filter(String::isNotBlank) } }
            .getOrDefault(listOf(failure))
    }
    val body = remember(lines) { lines.drop(1) }
    val headings = remember(body) { documentHeadings(body) }
    val list = rememberLazyListState()
    var contents by rememberSaveable(asset) { mutableStateOf(false) }
    var destination by rememberSaveable(asset) { mutableIntStateOf(-1) }
    val c = Ios.colors
    IosSheet(title = lines.firstOrNull() ?: failure, onClose = onClose) {
        BackHandler(enabled = contents) { contents = false }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (headings.isNotEmpty()) {
                IosRow(stringResource(if (contents) R.string.legal_back_text else R.string.legal_contents),
                    modifier = Modifier.widthIn(max = 720.dp), icon = Icons.Rounded.List, iconTint = c.tint,
                    titleLines = 3, onClick = { contents = !contents })
                HorizontalDivider(color = c.separator)
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                val area = Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f)
                if (contents) {
                    LazyColumn(modifier = area, contentPadding = PaddingValues(vertical = 12.dp)) {
                        items(headings, key = { it }) { index ->
                            IosRow(body[index], titleLines = Int.MAX_VALUE, onClick = {
                                destination = index
                                contents = false
                            })
                        }
                    }
                } else {
                    LazyColumn(modifier = area, state = list, contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 40.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(body, key = { index, _ -> index }) { index, text ->
                            if (index in headings) {
                                HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 10.dp), color = c.barBorder)
                                Text(text, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, color = c.label)
                            } else {
                                val edition = index == 0 && text.startsWith("Редакция ")
                                SelectionContainer { Text(text, style = if (edition) MaterialTheme.typography.labelLarge else
                                    MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
                                    color = if (edition) c.tint else c.label) }
                            }
                        }
                    }
                    LaunchedEffect(destination) {
                        if (destination in body.indices) {
                            list.scrollToItem(destination)
                            destination = -1
                        }
                    }
                }
            }
        }
    }
}

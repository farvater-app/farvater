package app.farvater.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.farvater.ui.ios.IosSheet
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType

object LegalDocs {
    const val TERMS = "terms.txt"
    const val PRIVACY = "privacy.txt"
    const val LICENSES = "licenses.txt"
}

private val SectionTitle = Regex("""^\d{1,2}\. \S.*""")

@Composable
fun LegalDocument(asset: String, onClose: () -> Unit) {
    val c = Ios.colors
    val context = LocalContext.current
    val failure = androidx.compose.ui.res.stringResource(app.farvater.R.string.document_open_error)
    val lines = remember(asset, context, failure) {
        runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }
            .getOrDefault(failure)
            .trim()
            .lines()
    }
    IosSheet(title = lines.firstOrNull().orEmpty(), onClose = onClose) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
            ) {
                lines.forEachIndexed { index, line -> DocumentLine(line, isTitle = index == 0) }
            }
        }
    }
}

@Composable
private fun DocumentLine(line: String, isTitle: Boolean) {
    val c = Ios.colors
    when {
        isTitle -> {
            Text(line, style = IosType.title2, color = c.label)
            Spacer(Modifier.height(4.dp))
        }
        line.matches(SectionTitle) && line.length < 80 && !line.endsWith('.') -> {
            Spacer(Modifier.height(18.dp))
            Text(line, style = IosType.headline, color = c.label)
        }
        line.startsWith("• ") -> Row(Modifier.padding(start = 4.dp, top = 2.dp)) {
            Text("•", style = IosType.body, color = c.tint)
            Spacer(Modifier.width(10.dp))
            Text(line.removePrefix("• "), style = IosType.body, color = c.label)
        }
        line.isBlank() -> Spacer(Modifier.height(10.dp))
        else -> Text(line, style = IosType.body, color = c.label)
    }
}

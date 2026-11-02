package app.farvater.ui

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.UiStyle
import app.farvater.ui.theme.FarvaterTheme
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class NavigationLabelsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyStyleKeepsCompleteLabelsOnNarrowScreensAndLargeFontsInAllLanguages() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val contexts = listOf("ru", "en", "fa").associateWith { language ->
            app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            })
        }
        val style = mutableStateOf(UiStyle.IOS)
        val language = mutableStateOf("ru")
        val width = mutableStateOf(393)
        val fontScale = mutableStateOf(1f)
        var density = 1f
        rule.setContent {
            val context = contexts.getValue(language.value)
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalLayoutDirection provides if (language.value == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density, fontScale.value),
            ) {
                Box(Modifier.width(width.value.dp).fillMaxHeight()) {
                    FarvaterTheme(style = style.value) {
                        FarvaterFrame(Tab.Home, {}) {}
                    }
                }
            }
        }
        for (ui in UiStyle.entries) for (locale in contexts.keys) {
            for (screenWidth in listOf(320, 360, 393)) for (scale in listOf(1f, 1.3f, 2f)) {
                rule.runOnIdle {
                    style.value = ui
                    language.value = locale
                    width.value = screenWidth
                    fontScale.value = scale
                }
                val case = "$ui/$locale/$screenWidth/$scale"
                Tab.entries.forEach { tab ->
                    val title = contexts.getValue(locale).getString(tab.titleRes)
                    val node = rule.onNodeWithText(title, ignoreCase = true)
                    node.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    val lines = (0 until layout.lineCount).joinToString { "${layout.getLineLeft(it)}..${layout.getLineRight(it)}" }
                    assertFalse("$case/$tab: ${layout.size} width=${layout.didOverflowWidth} height=${layout.didOverflowHeight} paragraph=${layout.multiParagraph.width} lines=$lines bounds=$bounds intrinsic=${layout.multiParagraph.intrinsics.maxIntrinsicWidth} constraints=${layout.layoutInput.constraints}", layout.hasVisualOverflow)
                    assertTrue("$case/$tab: outside screen $bounds", bounds.left >= 0f && bounds.right <= screenWidth * density + 1f)
                    assertTrue("$case/$tab: touch height ${bounds.height}", bounds.height >= 48 * density - 1f)
                }
            }
        }
    }
}

package app.farvater.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import app.farvater.data.UiStyle
import app.farvater.ui.theme.FarvaterTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class IosLargeTextTest {
    @get:Rule val rule = createComposeRule()

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun narrowNodeFiltersAndSearchHintFitAtDoubleFont() {
        val labels = listOf("Все", "Живые", "Reality", "XHTTP")
        val hint = "Страна, город или протокол"
        val selected = mutableStateOf(0)
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)
            ) {
                FarvaterTheme(style = UiStyle.IOS) {
                    androidx.compose.foundation.layout.Column {
                        app.farvater.ui.ios.IosSearchField("", {}, hint)
                        app.farvater.ui.ios.IosSegmented(labels, selected.value, { selected.value = it })
                    }
                }
            }
        }
        (labels + hint).forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("$label: size=${layout.size} lines=${layout.lineCount} max=${layout.layoutInput.maxLines} " +
                "width=${layout.didOverflowWidth} height=${layout.didOverflowHeight} constraints=${layout.layoutInput.constraints}",
                layout.hasVisualOverflow)
        }
        val buttons = labels.map { rule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        assertTrue(buttons[2].top >= buttons[0].bottom)
        rule.onNodeWithText("Reality").performClick()
        assertEquals(2, selected.value)
    }

    @Test fun doubleFontKeepsEveryTabCompleteAndAccessible() {
        RuntimeEnvironment.setFontScale(2f)
        val selected = mutableStateOf(Tab.Home)
        try {
            rule.setContent { FarvaterTheme(style = UiStyle.IOS) {
                FarvaterFrame(selected.value, { selected.value = it }) {}
            } }
            listOf("Фарватер", "Узлы", "Источники", "Настройки").forEachIndexed { index, title ->
                val node = rule.onNodeWithText(title)
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse(title, layouts.single().hasVisualOverflow)
                node.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
                assertEquals(Tab.entries[index], selected.value)
            }
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }
}

package app.farvater.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import app.farvater.data.UiStyle
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSegmented
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
class NewThemeLargeTextTest {
    @get:Rule val rule = createComposeRule()

    @Test fun labelsAndFiltersStayCompleteAndClickableAtDoubleFontSize() {
        RuntimeEnvironment.setFontScale(2f)
        val style = mutableStateOf(UiStyle.WINTER)
        val selected = mutableStateOf(0)
        val labels = listOf("Все", "Живые", "Reality", "XHTTP")
        val title = "Проверять страницы сервисов"
        try {
            rule.setContent { FarvaterTheme(style = style.value) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    IosRow(title, checked = false, onCheckedChange = {})
                    IosSegmented(labels, selected.value, { selected.value = it })
                }
            } }
            listOf(UiStyle.WINTER, UiStyle.COLOROS, UiStyle.FLYME).forEach { current ->
                rule.runOnIdle { style.value = current }
                (labels + title).forEach { text ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertFalse("$current: $text size=${layouts.single().size} paragraph=${layouts.single().multiParagraph.width} constraints=${layouts.single().layoutInput.constraints} height=${layouts.single().didOverflowHeight}", layouts.single().hasVisualOverflow)
                }
                rule.onNodeWithText("XHTTP").performClick()
                assertEquals(3, selected.value)
                rule.onNodeWithText("Все").performClick()
                assertEquals(0, selected.value)
            }
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }
}

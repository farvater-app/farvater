package app.farvater.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.farvater.engine.TestFailure
import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import app.farvater.ui.components.DelayLabel
import app.farvater.ui.theme.FarvaterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru-w393dp-h852dp")
class NodeFailureLabelTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun reliableReasonsHaveDifferentLabelsAndUnknownKeepsNoResponse() {
        rule.setContent { FarvaterTheme(dynamicColor = false) {
            Column {
                TestFailure.entries.forEach { DelayLabel(TestResult(it.name, -1, TestMethod.TCP, reason = it)) }
            }
        } }
        listOf("нет ответа", "тайм-аут", "отказ соединения", "ошибка TLS", "ошибка соединения").forEach {
            rule.onNodeWithText(it).assertExists()
        }
    }
}

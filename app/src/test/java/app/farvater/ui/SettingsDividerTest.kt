package app.farvater.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.farvater.data.AppSettings
import app.farvater.data.UiStyle
import app.farvater.ui.screens.SettingsScreen
import app.farvater.ui.theme.FarvaterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class SettingsDividerTest {
    @get:Rule val rule = createComposeRule()

    @Test fun languageHasASeparateBoundaryInEveryStyle() {
        val style = mutableStateOf(UiStyle.IOS)
        rule.setContent { FarvaterTheme(style = style.value) {
            SettingsScreen(AppSettings(uiStyle = style.value), {}, {})
        } }
        for (current in UiStyle.entries) {
            rule.runOnIdle { style.value = current }
            rule.onNodeWithText("Язык").performScrollTo()
            val appearance = rule.onNodeWithText("Оформление").fetchSemanticsNode().boundsInRoot
            val language = rule.onNodeWithText("Язык").fetchSemanticsNode().boundsInRoot
            assertTrue("$current: rows touch", language.top > appearance.bottom)
        }
    }
}

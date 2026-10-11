package app.farvater.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.farvater.data.UiStyle
import app.farvater.ui.theme.FarvaterTheme
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosDark
import app.farvater.ui.theme.IosLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ThemeFadeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun colorsFadeBetweenThemes() {
        var dark by mutableStateOf(false)
        var background = Color.Unspecified
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FarvaterTheme(darkTheme = dark, style = UiStyle.IOS) { background = Ios.colors.background }
        }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(IosLight.background, background)

        dark = true
        rule.mainClock.advanceTimeBy(170)
        assertNotEquals(IosLight.background, background)
        assertNotEquals(IosDark.background, background)

        rule.mainClock.advanceTimeBy(400)
        assertEquals(IosDark.background, background)
    }
}

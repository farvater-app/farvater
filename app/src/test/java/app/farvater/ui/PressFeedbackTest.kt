package app.farvater.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.UiStyle
import app.farvater.ui.components.ConnectButton
import app.farvater.ui.components.ColorOsConnect
import app.farvater.ui.components.WinterHero
import app.farvater.ui.components.ConnectMode
import app.farvater.ui.components.WitchHero
import app.farvater.ui.ios.ColorOsSwitch
import app.farvater.ui.theme.FarvaterTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class PressFeedbackTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var view: View

    @After fun restoreMotion() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun pixels(): Bitmap {
        lateinit var result: Bitmap
        rule.runOnIdle {
            result = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(result))
        }
        return result
    }

    @Test fun keyboardFocusIsVisibleWithoutRectangularIndication() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val kind = mutableStateOf(0)
        val requester = androidx.compose.ui.focus.FocusRequester()
        lateinit var focus: androidx.compose.ui.focus.FocusManager
        val keyboard = object : androidx.compose.ui.input.InputModeManager {
            override val inputMode = androidx.compose.ui.input.InputMode.Keyboard
            override fun requestInputMode(inputMode: androidx.compose.ui.input.InputMode) = inputMode == this.inputMode
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            focus = androidx.compose.ui.platform.LocalFocusManager.current
            val modifier = Modifier.focusRequester(requester)
            androidx.compose.runtime.key(kind.value) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInputModeManager provides keyboard) {
                FarvaterTheme(style = UiStyle.COLOROS, darkTheme = true) {
                    when (kind.value) {
                        0 -> ConnectButton(ConnectMode.Idle, {}, modifier)
                        1 -> ColorOsConnect(ConnectMode.Idle, {}, modifier)
                        2 -> WitchHero(ConnectMode.Idle, {}, modifier)
                        3 -> WinterHero(ConnectMode.Idle, {}, modifier)
                        else -> ColorOsSwitch(false, {}, modifier)
                    }
                }
                }
            }
        }
        for (index in 0..4) {
            rule.runOnIdle { kind.value = index }
            rule.mainClock.advanceTimeBy(1500)
            rule.runOnIdle { focus.clearFocus(force = true) }
            rule.mainClock.advanceTimeByFrame()
            val before = pixels()
            rule.runOnIdle { assertTrue("$index: focus request", requester.requestFocus()) }
            rule.mainClock.advanceTimeBy(300)
            val after = pixels()
            rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)).assertExists()
            assertFalse("$index: focus must change the visible figure", before.sameAs(after))
            before.recycle()
            after.recycle()
        }
    }

    @Test fun cauldronPressLeavesSurroundingSceneUntouched() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val dark = mutableStateOf(false)
        var clicks = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = UiStyle.WITCH, darkTheme = dark.value) { WitchHero(ConnectMode.Idle, { clicks++ }) }
        }
        for (night in listOf(false, true)) {
            rule.runOnIdle { dark.value = night }
            rule.mainClock.advanceTimeBy(1500)
            val button = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            val bounds = button.fetchSemanticsNode().boundsInRoot
            val before = pixels()
            button.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(1000)
            val after = pixels()
            for ((x, y) in listOf(bounds.left.toInt() + 1 to bounds.top.toInt() + 1,
                bounds.right.toInt() - 2 to bounds.top.toInt() + 1,
                bounds.left.toInt() + 1 to bounds.bottom.toInt() - 2)) {
                assertEquals("$night scene $x/$y", before.getPixel(x, y), after.getPixel(x, y))
            }
            assertFalse("The cauldron itself must react", before.sameAs(after))
            assertEquals(if (night) 1 else 0, clicks)
            button.performTouchInput { up() }
            rule.mainClock.advanceTimeBy(300)
            assertEquals(if (night) 2 else 1, clicks)
            before.recycle()
            after.recycle()
        }
    }

    @Test fun switchPressDoesNotPaintSquareAroundCapsule() {
        val dark = mutableStateOf(false)
        var changes = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = UiStyle.COLOROS, darkTheme = dark.value) {
                Box(Modifier.background(Color.Magenta)) { ColorOsSwitch(false, { changes++ }) }
            }
        }
        for (night in listOf(false, true)) {
            rule.runOnIdle { dark.value = night }
            rule.mainClock.advanceTimeBy(1500)
            val button = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            val bounds = button.fetchSemanticsNode().boundsInRoot
            button.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(1000)
            val bitmap = pixels()
            assertEquals(Color.Magenta.toArgb(), bitmap.getPixel(bounds.left.toInt() + 2, bounds.top.toInt() + 2))
            assertEquals(Color.Magenta.toArgb(), bitmap.getPixel(bounds.right.toInt() - 3, bounds.bottom.toInt() - 3))
            button.performTouchInput { up() }
            rule.mainClock.advanceTimeBy(300)
            assertEquals(if (night) 2 else 1, changes)
            bitmap.recycle()
        }
    }
}

package app.farvater.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.farvater.data.UiStyle
import app.farvater.ui.components.ConnectMode
import app.farvater.ui.components.WitchCatTaps
import app.farvater.ui.components.WitchHero
import app.farvater.ui.theme.FarvaterTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru", application = Application::class)
class WitchEasterEggTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @After fun restoreAnimations() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test fun slowOrIncompleteTapsDoNotTrigger() {
        val taps = WitchCatTaps()
        repeat(4) { assertFalse(taps.tap(it * 100L)) }
        assertFalse(taps.tap(2_000))
        repeat(3) { assertFalse(taps.tap(2_100L + it * 100L)) }
        assertTrue(taps.tap(2_500))
        assertFalse(taps.tap(2_600))
    }

    @Test fun fiveTapsPurrWithoutConnectingOrAddingTalkBackButton() = verify(ConnectMode.Idle, false, 1)

    @Test fun connectingIgnoresCatTaps() = verify(ConnectMode.Connecting, false, 0)

    @Test fun reducedMotionOnlyPurrsAndKeepsEveryPixel() = verify(ConnectMode.Idle, true, 1)

    private fun verify(mode: ConnectMode, still: Boolean, expectedPurrs: Int) {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (still) 0f else 1f)
        var toggles = 0
        var purrs = 0
        lateinit var view: View
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticFeedbackType == HapticFeedbackType.Confirm) purrs++
            }
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                FarvaterTheme(style = UiStyle.WITCH) {
                    Box(Modifier.size(360.dp, 236.dp)) { WitchHero(mode, { toggles++ }) }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        fun capture(): Bitmap {
            lateinit var image: Bitmap
            rule.runOnIdle {
                image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
            }
            return image
        }
        val before = capture()
        rule.onRoot().performTouchInput {
            repeat(5) { click(Offset(width * 294f / 360f, height * 170f / 236f)) }
        }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(expectedPurrs, purrs)
        assertEquals(0, toggles)
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(1)
        if (still) assertTrue(before.sameAs(capture()))
        rule.onRoot().performTouchInput { click(Offset(width / 2f, height * 177f / 236f)) }
        assertEquals(1, toggles)
    }
}

package app.farvater.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.UiStyle
import app.farvater.ui.components.ConnectMode
import app.farvater.ui.components.OneUiArcMotion
import app.farvater.ui.components.OneUiConnect
import app.farvater.ui.components.rememberOneUiArcMotion
import app.farvater.ui.ios.powerSave
import app.farvater.ui.theme.FarvaterTheme
import app.farvater.ui.theme.Ios
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class OneUiMotionTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @After fun restoreMotionSettings() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        powerSave = false
    }

    @Test fun connectingArcContinuesFromItsCurrentAngleAndLength() {
        val mode = mutableStateOf(ConnectMode.Connecting)
        lateinit var motion: OneUiArcMotion
        rule.mainClock.autoAdvance = false
        rule.setContent { motion = rememberOneUiArcMotion(mode.value, false) }
        for (target in listOf(ConnectMode.Connected, ConnectMode.Error)) {
            rule.runOnIdle { mode.value = ConnectMode.Connecting }
            applyChanges()
            rule.mainClock.advanceTimeBy(600)
            val start = motion.startAngle
            val sweep = motion.sweep.value
            assertEquals(96f, sweep, 0.01f)
            rule.runOnIdle { mode.value = target }
            applyChanges()
            val delta = abs(((motion.startAngle - start + 540f) % 360f) - 180f)
            assertTrue("$target resets the start angle by $delta", delta < 15f)
            assertTrue("$target erases the existing arc", motion.sweep.value >= sweep)
            assertTrue("$target fills the circle instantly", motion.sweep.value < 360f)
            rule.mainClock.advanceTimeBy(300)
            assertTrue("$target has no intermediate length", motion.sweep.value > sweep && motion.sweep.value < 360f)
            rule.mainClock.advanceTimeBy(600)
            assertEquals(360f, motion.sweep.value, 0.01f)
            rule.runOnIdle { mode.value = ConnectMode.Idle }
            applyChanges()
            assertTrue("disconnect erases the circle instantly", motion.sweep.value > 0f)
            rule.mainClock.advanceTimeBy(150)
            assertTrue(motion.sweep.value > 0f && motion.sweep.value < 360f)
            rule.mainClock.advanceTimeBy(400)
            assertEquals(0f, motion.sweep.value, 0.01f)
        }
    }

    @Test fun firstConnectedFrameDoesNotLoseTheVisibleArc() {
        lateinit var view: View
        var tint = Color.Transparent
        val mode = mutableStateOf(ConnectMode.Connecting)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = UiStyle.ONEUI, dynamicColor = false) {
                tint = Ios.colors.tint
                OneUiConnect(mode.value, {})
            }
        }
        applyChanges()
        rule.mainClock.advanceTimeBy(353)
        val bounds = rule.onNodeWithContentDescription("Отменить подключение").fetchSemanticsNode().boundsInRoot
        val before = coloredArcPixels(view, bounds, tint)
        assertTrue("busy arc is missing", before > 100)
        rule.runOnIdle { mode.value = ConnectMode.Connected }
        applyChanges()
        val first = coloredArcPixels(view, bounds, tint)
        assertTrue("first connected frame is empty: $before -> $first", first >= before * 0.85f)
        rule.mainClock.advanceTimeBy(96)
        assertTrue("arc shrank after connecting", coloredArcPixels(view, bounds, tint) >= first)
    }

    @Test fun systemReducedMotionKeepsScenesStatic() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        checkStaticScenes()
    }

    @Test fun powerSavingKeepsScenesStatic() {
        powerSave = true
        checkStaticScenes()
    }

    private fun checkStaticScenes() {
        lateinit var view: View
        val mode = mutableStateOf(ConnectMode.Connecting)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = UiStyle.ONEUI, dynamicColor = false) { OneUiConnect(mode.value, {}) }
        }
        for (target in listOf(ConnectMode.Connecting, ConnectMode.Connected, ConnectMode.Error, ConnectMode.Idle)) {
            rule.runOnIdle { mode.value = target }
            applyChanges()
            rule.mainClock.advanceTimeBy(64)
            val before = capture(view)
            rule.mainClock.advanceTimeBy(1500)
            val after = capture(view)
            assertTrue("$target still animates", before.sameAs(after))
            before.recycle()
            after.recycle()
        }
    }

    private fun coloredArcPixels(view: View, bounds: Rect, tint: Color): Int {
        val pixels = capture(view)
        val color = tint.toArgb()
        val red = color shr 16 and 255
        val green = color shr 8 and 255
        val blue = color and 255
        val inner = bounds.width * 0.4f
        val outer = bounds.width * 0.51f
        var count = 0
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                val dx = x - bounds.center.x
                val dy = y - bounds.center.y
                val radiusSquared = dx * dx + dy * dy
                if (radiusSquared !in inner * inner..outer * outer) continue
                val pixel = pixels.getPixel(x, y)
                val dr = (pixel shr 16 and 255) - red
                val dg = (pixel shr 8 and 255) - green
                val db = (pixel and 255) - blue
                if (dr * dr + dg * dg + db * db <= 1600) count++
            }
        }
        pixels.recycle()
        return count
    }

    private fun capture(view: View): Bitmap {
        lateinit var bitmap: Bitmap
        rule.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun applyChanges() {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }
}

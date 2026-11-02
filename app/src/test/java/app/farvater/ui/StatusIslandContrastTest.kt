package app.farvater.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import app.farvater.data.UiStyle
import app.farvater.ui.components.ConnectMode
import app.farvater.ui.components.StatusIsland
import app.farvater.ui.theme.FarvaterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class StatusIslandContrastTest {
    @get:Rule val rule = createComposeRule()

    @Test fun opaqueBackgroundSurvivesEveryStateAndHyperCollapseInBothThemes() {
        val mode = mutableStateOf(ConnectMode.Idle)
        val style = mutableStateOf(UiStyle.HARMONY)
        val dark = mutableStateOf(false)
        lateinit var view: View
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = style.value, darkTheme = dark.value, dynamicColor = false) {
                StatusIsland(mode.value, "Test", null, 0, null)
            }
        }
        for (currentStyle in listOf(UiStyle.HARMONY, UiStyle.HYPER, UiStyle.ORIGIN, UiStyle.OXYGEN)) {
            for (night in listOf(false, true)) {
                for (state in listOf(ConnectMode.Idle, ConnectMode.Connecting, ConnectMode.Connected, ConnectMode.Error)) {
                    rule.runOnIdle {
                        style.value = currentStyle
                        dark.value = night
                        mode.value = state
                        Snapshot.sendApplyNotifications()
                    }
                    rule.mainClock.advanceTimeByFrame()
                    for (time in listOf(32L, 64L, 224L, 2000L, 4000L)) {
                        rule.mainClock.advanceTimeBy(time)
                        rule.waitForIdle()
                        val description = when (state) {
                            ConnectMode.Idle -> "VPN выключен"
                            ConnectMode.Connecting -> "Подключение"
                            ConnectMode.Connected -> "Подключено, Test"
                            else -> "Ошибка подключения"
                        }
                        val bounds = rule.onNode(hasContentDescription(description)).fetchSemanticsNode().boundsInRoot
                        rule.runOnIdle {
                            val pixels = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                            view.draw(Canvas(pixels))
                            val x = bounds.center.x.toInt().coerceIn(0, pixels.width - 1)
                            val y = (bounds.top + bounds.height * 0.1f).toInt().coerceIn(0, pixels.height - 1)
                            assertEquals("$currentStyle/$night/$state/$time", android.graphics.Color.BLACK, pixels.getPixel(x, y))
                            pixels.recycle()
                        }
                    }
                }
            }
        }
    }
}

package app.farvater.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.UiStyle
import app.farvater.ui.components.ConnectMode
import app.farvater.ui.components.ColorOsConnect
import app.farvater.ui.ios.IosLargeTitle
import app.farvater.ui.theme.FarvaterTheme
import app.farvater.ui.theme.colorOsColors
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
class ColorOsStyleTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @After fun restoreAnimations() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test fun largeTextKeepsTitlesAndTabsComplete() {
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, 2f)) {
                FarvaterTheme(style = UiStyle.COLOROS) {
                    FarvaterFrame(Tab.Home, {}) { IosLargeTitle("Длинный заголовок") }
                }
            }
        }
        listOf("Длинный заголовок", "Фарватер", "Узлы", "Источники", "Настройки").forEach { title ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithText(title).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("$title size=${layouts.single().size} lines=${layouts.single().lineCount} width=${layouts.single().didOverflowWidth} height=${layouts.single().didOverflowHeight} paragraphWidth=${layouts.single().multiParagraph.width} maxIntrinsic=${layouts.single().multiParagraph.intrinsics.maxIntrinsicWidth} constraints=${layouts.single().layoutInput.constraints} font=${layouts.single().layoutInput.density}", layouts.single().hasVisualOverflow)
        }
    }

    @Test fun bothPalettesKeepTextReadable() {
        for (dark in listOf(false, true)) {
            val c = colorOsColors(dark)
            for (background in listOf(c.background, c.cell, c.cellRaised, c.fill)) {
                for (text in listOf(c.label, c.secondaryLabel, c.tertiaryLabel, c.tint, c.green, c.red, c.blue,
                    c.teal, c.indigo, c.purple, c.gray, c.yellow, c.pink)) {
                    assertTrue("$dark: $text on $background = ${contrast(text, background)}", contrast(text, background) >= 4.5f)
                }
            }
        }
    }

    @Test fun sceneExposesAllConnectionActions() {
        rule.mainClock.autoAdvance = false
        val mode = mutableStateOf(ConnectMode.Idle)
        var clicks = 0
        rule.setContent { FarvaterTheme(style = UiStyle.COLOROS) { ColorOsConnect(mode.value, { clicks++ }) } }
        val labels = listOf("Подключиться", "Остановить", "Отменить подключение", "Отключиться", "Повторить подключение")
        ConnectMode.entries.forEachIndexed { index, value ->
            rule.runOnIdle { mode.value = value }
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            rule.mainClock.advanceTimeByFrame()
            val button = rule.onNodeWithContentDescription(labels[index])
            button.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
            val bounds = button.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= 48 && bounds.height >= 48)
        }
        assertEquals(5, clicks)
    }

    @Test fun reducedMotionFreezesSnowAndLights() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        lateinit var view: View
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = UiStyle.COLOROS) { ColorOsConnect(ConnectMode.Searching, {}) }
        }
        rule.mainClock.advanceTimeBy(500)
        fun capture(): Bitmap {
            lateinit var bitmap: Bitmap
            rule.runOnIdle {
                bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
            }
            return bitmap
        }
        val before = capture()
        rule.mainClock.advanceTimeBy(12_000)
        assertTrue(before.sameAs(capture()))
    }

    private fun contrast(a: Color, b: Color) =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}

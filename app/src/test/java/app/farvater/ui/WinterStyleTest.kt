package app.farvater.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.compositeOver
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
import app.farvater.ui.components.winterLampPower
import app.farvater.ui.components.winterBackgroundColors
import app.farvater.ui.components.winterSnowColor
import app.farvater.ui.components.WinterHero
import app.farvater.ui.ios.powerSave
import app.farvater.ui.ios.IosLargeTitle
import app.farvater.ui.theme.FarvaterTheme
import app.farvater.ui.theme.winterColors
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
class WinterStyleTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @After fun restoreAnimations() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        powerSave = false
    }

    @Test fun largeTextKeepsTitlesAndTabsComplete() {
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, 2f)) {
                FarvaterTheme(style = UiStyle.WINTER) {
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
            val c = winterColors(dark)
            val scene = winterBackgroundColors(dark)
            for (background in listOf(c.background, c.cell, c.cellRaised, c.fill) + scene + scene.map { winterSnowColor(dark).compositeOver(it) }) {
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
        rule.setContent { FarvaterTheme(style = UiStyle.WINTER) { WinterHero(mode.value, { clicks++ }) } }
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
            FarvaterTheme(style = UiStyle.WINTER) { WinterHero(ConnectMode.Searching, {}) }
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

    @Test fun lightsFillFromLowerBranchesToStar() {
        val power = { index: Int, progress: Float -> winterLampPower(ConnectMode.Connected, progress, 0f, index, false) }
        assertEquals(0f, power(0, 0f))
        assertEquals(1f, power(0, 0.25f))
        assertEquals(0f, power(35, 0.25f))
        assertEquals(1f, power(41, 1f))
    }

    @Test fun snowFramesDoNotRecomposeContentAndLiveReductionStopsThem() {
        lateinit var view: View
        var compositions = 0
        var contentDraws = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = UiStyle.WINTER, darkTheme = true) {
                FarvaterFrame(Tab.Home, {}) {
                    androidx.compose.runtime.SideEffect { compositions++ }
                    androidx.compose.material3.Text("Содержимое списка", modifier = androidx.compose.ui.Modifier.drawWithContent {
                        contentDraws++
                        drawContent()
                    })
                }
            }
        }
        fun capture(): Bitmap {
            lateinit var bitmap: Bitmap
            rule.runOnIdle {
                bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
            }
            return bitmap
        }
        rule.mainClock.advanceTimeBy(500)
        val before = capture()
        val initialCompositions = compositions
        val initialDraws = contentDraws
        capture()
        val forcedCaptureDraws = contentDraws - initialDraws
        rule.mainClock.advanceTimeBy(1_000)
        assertFalse("Snow must move", before.sameAs(capture()))
        assertEquals("Snow must not recompose list content", initialCompositions, compositions)
        assertEquals("Snow must not add content draws beyond forced bitmap captures", initialDraws + 2 * forcedCaptureDraws, contentDraws)
        rule.runOnIdle { Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        rule.mainClock.advanceTimeBy(500)
        val frozen = capture()
        rule.mainClock.advanceTimeBy(2_000)
        assertTrue("System reduction must freeze the running scene", frozen.sameAs(capture()))
    }

    @Test fun powerSavingFreezesRunningScene() {
        lateinit var view: View
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = UiStyle.WINTER) { WinterHero(ConnectMode.Connecting, {}) }
        }
        rule.mainClock.advanceTimeBy(300)
        rule.runOnIdle { powerSave = true }
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
        rule.mainClock.advanceTimeBy(2_000)
        assertTrue(before.sameAs(capture()))
    }

    private fun contrast(a: Color, b: Color) =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}

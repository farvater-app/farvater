package app.farvater.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.material3.Text
import app.farvater.ui.ios.IosLargeTitle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.farvater.data.UiStyle
import app.farvater.ui.components.ConnectMode
import app.farvater.ui.components.WitchHero
import app.farvater.ui.theme.FarvaterTheme
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosColors
import app.farvater.ui.theme.WitchColors
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class WitchStyleTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @After fun restoreAnimations() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test fun largeTextKeepsTitlesAndTabsComplete() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FarvaterTheme(style = UiStyle.WITCH) {
                    FarvaterFrame(Tab.Home, {}) { IosLargeTitle("Длинный заголовок") { Text("Выключен") } }
                }
            }
        }
        listOf("Длинный заголовок", "Фарватер", "Узлы", "Источники", "Настройки").forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("$text: ${layout.size}, lines ${layout.lineCount}, width ${layout.didOverflowWidth}, height ${layout.didOverflowHeight}, paragraph ${layout.multiParagraph.height}, style ${layout.layoutInput.style}, density ${layout.layoutInput.density}", layout.hasVisualOverflow)
        }
    }

    @Test fun lightSystemKeepsWitchDarkAndTextReadable() {
        lateinit var colors: IosColors
        rule.setContent { FarvaterTheme(darkTheme = false, style = UiStyle.WITCH) { colors = Ios.colors } }
        assertTrue(colors.dark)
        assertEquals(WitchColors, colors)
        listOf(colors.background, colors.cell, colors.cellRaised, colors.fill).forEach { bg ->
            listOf(colors.label, colors.secondaryLabel, colors.tertiaryLabel).forEach { fg ->
                assertTrue("$fg on $bg", contrast(fg, bg) >= 4.5f)
            }
        }
    }

    @Test fun cauldronExposesEveryActionAndCallsToggle() {
        var mode by mutableStateOf(ConnectMode.Idle)
        var clicks = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { FarvaterTheme(style = UiStyle.WITCH) { WitchHero(mode, { clicks++ }) } }
        val labels = listOf("Подключиться", "Остановить", "Отменить подключение", "Отключиться", "Повторить подключение")
        ConnectMode.entries.forEachIndexed { index, state ->
            rule.runOnIdle { mode = state }
            rule.mainClock.advanceTimeByFrame()
            val node = rule.onNodeWithContentDescription(labels[index])
            node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= 48f && bounds.height >= 48f)
        }
        assertEquals(5, clicks)
    }

    @Test fun disabledAnimationsFreezeScene() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        lateinit var view: View
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = UiStyle.WITCH) {
                Box(Modifier.size(360.dp, 236.dp)) { WitchHero(ConnectMode.Connected, {}) }
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
        rule.mainClock.advanceTimeBy(12_000)
        val after = capture()
        assertTrue(before.sameAs(after))
    }

    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}

package app.farvater.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.farvater.data.UiStyle
import app.farvater.ui.components.ConnectMode
import app.farvater.ui.components.OxygenConnect
import app.farvater.ui.ios.IosButton
import app.farvater.ui.ios.IosLargeTitle
import app.farvater.ui.ios.IosSegmented
import app.farvater.ui.theme.FarvaterTheme
import app.farvater.ui.theme.IosLight
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp-xxhdpi", application = Application::class)
class AccessibilityRegressionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun filtersExposeRoleAndSelection() {
        rule.setContent { FarvaterTheme(style = UiStyle.IOS) { IosSegmented(listOf("Все", "Живые"), 1, {}) } }
        rule.onNodeWithText("Живые").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    }

    @Test
    fun hiddenNodeCountIsNotClipped() {
        val subtitle = "120 в списке, отвечают 9, режим ЧС, скрыто небезопасных 1234, скрыто дублей 5678"
        rule.setContent {
            FarvaterTheme(style = UiStyle.IOS) {
                IosLargeTitle("Узлы", subtitle, Modifier.width(320.dp))
            }
        }
        val layout = layout(subtitle)
        assertFalse(layout.hasVisualOverflow)
        assertEquals(subtitle.length, layout.getLineEnd(layout.lineCount - 1))
    }

    @Test
    fun hiddenNodeCountFitsOneUiWithLargeFont() {
        val subtitle = "120 в списке, отвечают 9, режим ЧС, скрыто небезопасных 1234, скрыто дублей 5678"
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                FarvaterTheme(style = UiStyle.ONEUI) { IosLargeTitle("Узлы", subtitle, Modifier.width(320.dp)) }
            }
        }
        val layout = layout(subtitle)
        assertFalse(layout.hasVisualOverflow)
        assertEquals(subtitle.length, layout.getLineEnd(layout.lineCount - 1))
    }

    @Test
    fun lightSecondaryTextAndFilledButtonsMeetContrast() {
        rule.setContent {
            FarvaterTheme(darkTheme = false, style = UiStyle.IOS) {
                Column {
                    IosLargeTitle("Настройки", "Подпись")
                    IosButton("Установить", {})
                }
            }
        }
        assertTrue(contrast(layout("Подпись").layoutInput.style.color, IosLight.background) >= 4.5f)
        assertTrue(contrast(layout("Установить").layoutInput.style.color, IosLight.tint) >= 4.5f)
    }

    @Test
    fun oxygenIdlePowerGlyphIsVisibleOnWhiteThumb() {
        lateinit var view: View
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(darkTheme = true, style = UiStyle.OXYGEN) { OxygenConnect(ConnectMode.Idle, {}) }
        }
        val bounds = rule.onNodeWithContentDescription("Подключиться").fetchSemanticsNode().boundsInRoot
        lateinit var pixels: Bitmap
        rule.runOnIdle {
            pixels = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(pixels))
        }
        val cx = (bounds.left + bounds.width * 48 / 272).toInt()
        val cy = bounds.center.y.toInt()
        val radius = (bounds.height * 15 / 96).toInt()
        var visible = 0
        for (x in cx - radius..cx + radius) for (y in cy - radius..cy + radius) {
            if (contrast(Color(pixels.getPixel(x, y)), Color.White) >= 3f) visible++
        }
        assertTrue("Нет контрастного значка питания", visible > 20)
    }

    private fun layout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}

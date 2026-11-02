package app.farvater.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.farvater.data.UiStyle
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSection
import app.farvater.ui.theme.FarvaterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w320dp-h852dp", application = Application::class)
class IosValueLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun longCoreVersionKeepsTitleAndValueCompleteInEveryStyle() {
        val style = mutableStateOf(UiStyle.IOS)
        val dark = mutableStateOf(false)
        val fontScale = mutableStateOf(1f)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val title = mutableStateOf("Ядро Xray")
        val version = "Xray Lib v40, Xray-core v26.10.21"
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale.value),
                LocalLayoutDirection provides direction.value,
            ) {
                FarvaterTheme(darkTheme = dark.value, style = style.value, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        IosSection {
                            IosRow(title.value, Modifier.testTag("row"), icon = Icons.Rounded.Memory, value = version)
                        }
                    }
                }
            }
        }
        for (currentStyle in UiStyle.entries) {
            for (currentDark in listOf(false, true)) {
                for (currentScale in listOf(1f, 1.3f, 2f)) {
                    for (rtl in listOf(false, true)) {
                        rule.runOnIdle {
                            style.value = currentStyle
                            dark.value = currentDark
                            fontScale.value = currentScale
                            direction.value = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                            title.value = if (rtl) "هستهٔ Xray" else "Ядро Xray"
                        }
                        val case = "$currentStyle dark=$currentDark font=$currentScale rtl=$rtl"
                        val rowBounds = rule.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
                        for (text in listOf(title.value, version)) {
                            val node = rule.onNodeWithText(text, useUnmergedTree = true)
                            node.assertIsDisplayed()
                            val layouts = mutableListOf<TextLayoutResult>()
                            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                            val layout = layouts.single()
                            assertFalse("$case: $text size=${layout.size} lines=${layout.lineCount} " +
                                "width=${layout.didOverflowWidth} height=${layout.didOverflowHeight} " +
                                "intrinsic=${layout.multiParagraph.intrinsics.maxIntrinsicWidth} constraints=${layout.layoutInput.constraints}",
                                layout.hasVisualOverflow)
                            val bounds = node.fetchSemanticsNode().boundsInRoot
                            assertTrue("$case: $text outside row", bounds.left >= rowBounds.left && bounds.right <= rowBounds.right &&
                                bounds.top >= rowBounds.top && bounds.bottom <= rowBounds.bottom)
                        }
                    }
                }
            }
        }
    }

    @Test fun shortValuesKeepTheSingleLineLayout() {
        val style = mutableStateOf(UiStyle.IOS)
        rule.setContent {
            FarvaterTheme(style = style.value, dynamicColor = false) {
                Box(Modifier.width(320.dp)) {
                    IosSection {
                        IosRow("Версия", icon = Icons.Rounded.Memory, value = "1.0.31")
                    }
                }
            }
        }
        for (currentStyle in UiStyle.entries) {
            rule.runOnIdle { style.value = currentStyle }
            val title = rule.onNodeWithText("Версия")
            val value = rule.onNodeWithText("1.0.31")
            for (node in listOf(title, value)) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(currentStyle.name, 1, layouts.single().lineCount)
            }
            assertEquals(currentStyle.name, title.fetchSemanticsNode().boundsInRoot.center.y,
                value.fetchSemanticsNode().boundsInRoot.center.y, 1f)
            assertTrue(currentStyle.name, value.fetchSemanticsNode().boundsInRoot.left > title.fetchSemanticsNode().boundsInRoot.right)
        }
    }
}

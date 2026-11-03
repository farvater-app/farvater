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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.UiStyle
import app.farvater.ui.components.ConnectMode
import app.farvater.ui.components.FlymeConnect
import app.farvater.ui.ios.IosLargeTitle
import app.farvater.ui.ios.IosSegmented
import app.farvater.ui.ios.IosSwitch
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.powerSave
import app.farvater.ui.theme.FarvaterTheme
import app.farvater.ui.theme.flymeColors
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
class FlymeStyleTest {
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
                FarvaterTheme(style = UiStyle.FLYME) {
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
            val c = flymeColors(dark)
            for (background in listOf(c.background, c.cell, c.cellRaised, c.fill)) {
                for (text in listOf(c.label, c.secondaryLabel, c.tertiaryLabel, c.tint, c.green, c.red, c.blue,
                    c.teal, c.indigo, c.purple, c.gray, c.yellow, c.pink)) {
                    assertTrue("$dark: $text on $background = ${contrast(text, background)}", contrast(text, background) >= 4.5f)
                }
            }
        }
    }

    @Test fun filterTargetsRemainAtLeast48Dp() {
        var density = 1f
        rule.setContent {
            density = androidx.compose.ui.platform.LocalDensity.current.density
            FarvaterTheme(style = UiStyle.FLYME) {
                IosSegmented(listOf("Все", "Живые", "Reality", "XHTTP"), 0, {})
            }
        }
        listOf("Все", "Живые", "Reality", "XHTTP").forEach { title ->
            val bounds = rule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= 48 * density && bounds.height >= 48 * density)
        }
    }

    @Test fun eachSwitchActionHasOneHapticFeedback() {
        val standalone = mutableStateOf(false)
        val row = mutableStateOf(false)
        var feedback = 0
        var changes = 0
        val haptics = object : androidx.compose.ui.hapticfeedback.HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
                feedback++
            }
        }
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalHapticFeedback provides haptics) {
                FarvaterTheme(style = UiStyle.FLYME) {
                    androidx.compose.foundation.layout.Column {
                        IosSwitch(standalone.value, { standalone.value = it; changes++ }, androidx.compose.ui.Modifier.testTag("standalone"))
                        IosRow("Строка", checked = row.value, onCheckedChange = { row.value = it; changes++ })
                    }
                }
            }
        }
        rule.onNodeWithTag("standalone").performClick()
        assertTrue(standalone.value)
        assertEquals(1, changes)
        assertEquals(1, feedback)
        rule.onNodeWithText("Строка").performClick()
        assertTrue(row.value)
        assertEquals(2, changes)
        assertEquals(2, feedback)
    }

    @Test fun sceneExposesAllConnectionActions() {
        rule.mainClock.autoAdvance = false
        val mode = mutableStateOf(ConnectMode.Idle)
        var clicks = 0
        rule.setContent { FarvaterTheme(style = UiStyle.FLYME) { FlymeConnect(mode.value, { clicks++ }) } }
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

    @Test fun connectionAndDisconnectionHaveIntermediateFrames() {
        lateinit var view: View
        val mode = mutableStateOf(ConnectMode.Idle)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = UiStyle.FLYME) { FlymeConnect(mode.value, {}) }
        }
        applyChanges()
        rule.mainClock.advanceTimeBy(600)
        for (target in listOf(ConnectMode.Connected, ConnectMode.Idle)) {
            val before = capture(view)
            rule.runOnIdle { mode.value = target }
            applyChanges()
            rule.mainClock.advanceTimeBy(176)
            val intermediate = capture(view)
            rule.mainClock.advanceTimeBy(600)
            val after = capture(view)
            assertFalse("$target: no transition", intermediate.sameAs(before))
            assertFalse("$target: jumped to final frame", intermediate.sameAs(after))
        }
    }

    @Test fun searchingAndConnectingMoveTheBeacon() {
        lateinit var view: View
        val mode = mutableStateOf(ConnectMode.Searching)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = UiStyle.FLYME) { FlymeConnect(mode.value, {}) }
        }
        for (target in listOf(ConnectMode.Searching, ConnectMode.Connecting)) {
            rule.runOnIdle { mode.value = target }
            applyChanges()
            rule.mainClock.advanceTimeBy(160)
            val before = capture(view)
            rule.mainClock.advanceTimeBy(320)
            assertFalse("$target: busy indicator is static", before.sameAs(capture(view)))
        }
    }

    @Test fun reducedMotionFreezesOrbAndTransitions() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertStaticScene()
    }

    @Test fun powerSavingFreezesOrbAndTransitions() {
        powerSave = true
        assertStaticScene()
    }

    private fun assertStaticScene() {
        lateinit var view: View
        val mode = mutableStateOf(ConnectMode.Searching)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = UiStyle.FLYME) { FlymeConnect(mode.value, {}) }
        }
        applyChanges()
        rule.mainClock.advanceTimeBy(500)
        val before = capture(view)
        rule.mainClock.advanceTimeBy(12_000)
        assertTrue(before.sameAs(capture(view)))
        for (target in listOf(ConnectMode.Connected, ConnectMode.Idle)) {
            rule.runOnIdle { mode.value = target }
            applyChanges()
            rule.mainClock.advanceTimeBy(64)
            val first = capture(view)
            rule.mainClock.advanceTimeBy(600)
            assertTrue("$target still animates", first.sameAs(capture(view)))
        }
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

    private fun contrast(a: Color, b: Color) =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}

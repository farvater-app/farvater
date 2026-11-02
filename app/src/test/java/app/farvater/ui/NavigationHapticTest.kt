package app.farvater.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.farvater.data.UiStyle
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
class NavigationHapticTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyStyleTicksOnceWhenChangingTabsAndNeverWhenReselecting() {
        val style = mutableStateOf(UiStyle.IOS)
        val selected = mutableStateOf(Tab.Home)
        val feedback = mutableListOf<HapticFeedbackType>()
        val selections = mutableListOf<Tab>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                feedback += hapticFeedbackType
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                FarvaterTheme(style = style.value) {
                    FarvaterFrame(selected.value, {
                        selected.value = it
                        selections += it
                    }) {}
                }
            }
        }
        val titles = mapOf(Tab.Home to "Фарватер", Tab.Servers to "Узлы",
            Tab.Sources to "Источники", Tab.Settings to "Настройки")
        for (ui in UiStyle.entries) {
            rule.runOnIdle {
                style.value = ui
                selected.value = Tab.Home
                feedback.clear()
                selections.clear()
            }
            rule.onNodeWithText(titles.getValue(Tab.Home), ignoreCase = true).performClick().assertIsSelected()
            assertEquals("$ui: reselecting home", emptyList<HapticFeedbackType>(), feedback)
            listOf(Tab.Servers, Tab.Sources, Tab.Settings, Tab.Home).forEachIndexed { index, tab ->
                val target = rule.onNodeWithText(titles.getValue(tab), ignoreCase = true)
                target.performClick().assertIsSelected()
                assertEquals("$ui/$tab: changing tab",
                    List(index + 1) { HapticFeedbackType.SegmentTick }, feedback)
                target.performClick().assertIsSelected()
                assertEquals("$ui/$tab: reselecting tab",
                    List(index + 1) { HapticFeedbackType.SegmentTick }, feedback)
            }
            assertEquals("$ui: selection callbacks", listOf(Tab.Home, Tab.Servers, Tab.Servers,
                Tab.Sources, Tab.Sources, Tab.Settings, Tab.Settings, Tab.Home, Tab.Home), selections)
        }
    }
}

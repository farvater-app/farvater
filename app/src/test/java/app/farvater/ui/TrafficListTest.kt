package app.farvater.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.farvater.data.DayTraffic
import app.farvater.ui.screens.TrafficPage
import app.farvater.ui.theme.FarvaterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class TrafficListTest {
    @get:Rule val rule = createComposeRule()

    @Test fun zeroDaysStayOutOfListWithoutChangingPeriodTotal() {
        val today = LocalDate.of(2026, 11, 2)
        rule.setContent { FarvaterTheme {
            TrafficPage(listOf(DayTraffic(today.toEpochDay() - 2, 1024), DayTraffic(today.toEpochDay(), 2048)), today)
        } }
        rule.onNodeWithText("3 КБ").assertExists()
        rule.onNodeWithText("1 КБ").assertExists()
        rule.onNodeWithText("2 КБ").assertExists()
        rule.onAllNodesWithText("0 Б").assertCountEquals(0)
        rule.onNodeWithText("30 дней").performClick()
        rule.onNodeWithText("3 КБ").assertExists()
        rule.onAllNodesWithText("0 Б").assertCountEquals(0)
    }
}

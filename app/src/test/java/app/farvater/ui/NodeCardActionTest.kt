package app.farvater.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.farvater.core.parser.LinkParser
import app.farvater.data.*
import app.farvater.ui.screens.ServersScreen
import app.farvater.ui.theme.FarvaterTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class NodeCardActionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun serviceBadgesSelectTheNodeAndFavoriteRemainsIndependent() {
        val node = LinkParser.parse("vless://test@badge.example.test:443?security=tls#Test")!!
        val target = ServiceChecks.builtIn.first { it.kind == ServiceKind.YOUTUBE }
        val state = UiState(nodes = listOf(node), settings = AppSettings(serviceChecksEnabled = true,
            checkedServices = setOf(ServiceKind.YOUTUBE)), serviceChecks = mapOf(
            ServiceChecks.key("ANY", node.id, target) to ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, System.currentTimeMillis())))
        var selected = 0
        var favorites = 0
        rule.setContent { FarvaterTheme {
            ServersScreen(state, { selected++ }, {}, {}, {}, {}, onToggleFavorite = { favorites++ })
        } }
        val badge = rule.onNodeWithContentDescription("YouTube: страница ответила или получен ожидаемый код", useUnmergedTree = true)
        badge.performScrollTo().performTouchInput { click(center) }
        assertEquals(1, selected)
        rule.onNodeWithContentDescription("В избранное: Test", useUnmergedTree = true).performTouchInput { click(center) }
        assertEquals(1, favorites)
        assertEquals(1, selected)
    }

    @Test fun holdingAnUnknownBadgeShowsItsReasonWithoutSelectingTheNode() {
        val node = LinkParser.parse("vless://test@badge.example.test:443?security=tls#Test")!!
        val target = ServiceChecks.builtIn.first { it.kind == ServiceKind.GEMINI }
        val state = UiState(nodes = listOf(node), settings = AppSettings(serviceChecksEnabled = true,
            checkedServices = setOf(ServiceKind.GEMINI)), serviceChecks = mapOf(
            ServiceChecks.key("ANY", node.id, target) to ServiceCheck(ServiceVerdict.UNKNOWN, ServiceReason.TIMEOUT, System.currentTimeMillis())))
        var selected = 0
        var tested = false
        rule.setContent { FarvaterTheme {
            ServersScreen(state, { selected++ }, {}, {}, {}, {}, onTestNode = { tested = true })
        } }
        val message = "Gemini: время ожидания ответа истекло"
        rule.onNodeWithContentDescription(message, useUnmergedTree = true)
            .performScrollTo().performTouchInput { longClick(center) }
        rule.onNodeWithText("Результаты проверки сервисов").assertIsDisplayed()
        rule.onNodeWithText(message).assertIsDisplayed()
        rule.onNodeWithText("Gemini: сервис сообщил об ограничении региона").assertDoesNotExist()
        captureScreenRoboImage(File(System.getProperty("farvater.screenshotDir"), "service-details-node-menu.png").absolutePath)
        assertEquals(0, selected)
        assertFalse(tested)
        rule.onNodeWithText("Проверить").performScrollTo().performClick()
        org.junit.Assert.assertTrue(tested)
    }
}

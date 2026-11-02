package app.farvater.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.farvater.core.parser.LinkParser
import app.farvater.data.*
import app.farvater.ui.screens.ServersScreen
import app.farvater.ui.theme.FarvaterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
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
}

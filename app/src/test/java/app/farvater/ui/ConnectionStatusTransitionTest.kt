package app.farvater.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.farvater.core.parser.LinkParser
import app.farvater.data.UiStyle
import app.farvater.ui.components.ConnectMode
import app.farvater.ui.components.winterStarPower
import app.farvater.ui.screens.HomeScreen
import app.farvater.ui.theme.FarvaterTheme
import app.farvater.vpn.Traffic
import app.farvater.vpn.VpnState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class ConnectionStatusTransitionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun changingConnectionNeverKeepsThePreviousStatusOrProtection() {
        val node = LinkParser.parse("vless://test@transition.example.test:443?security=tls#Test")!!
        val vpn = mutableStateOf<VpnState>(VpnState.Connected(node, 0))
        val state = mutableStateOf(UiState())
        rule.mainClock.autoAdvance = false
        rule.setContent { FarvaterTheme(style = UiStyle.WINTER) {
            HomeScreen(state.value, vpn.value, Traffic(), emptyList(), {}, {}, {}, {})
        } }
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithText("Трафик идёт через выбранный узел").assertExists()
        repeat(2) {
            rule.runOnIdle { state.value = state.value.copy(preflight = node); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            for (elapsed in listOf(16L, 44L, 60L, 180L)) {
                rule.mainClock.advanceTimeBy(elapsed)
                rule.onNodeWithText("Подключаюсь").assertExists()
                rule.onNodeWithText("Подключено").assertDoesNotExist()
                rule.onNodeWithText("Трафик идёт через выбранный узел").assertDoesNotExist()
                rule.onNodeWithText("Защита").assertDoesNotExist()
            }
            rule.runOnIdle { state.value = state.value.copy(preflight = null); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithText("Подключено").assertExists()
            rule.onNodeWithText("Подключаюсь").assertDoesNotExist()
        }
    }

    @Test fun winterStarRemainsBrightWhileConnectingAndWhenLightsStartFilling() {
        for (mode in listOf(ConnectMode.Searching, ConnectMode.Connecting, ConnectMode.Connected)) {
            for (still in listOf(false, true)) {
                repeat(80) { frame ->
                    assertTrue("$mode still=$still frame=$frame", winterStarPower(mode, frame / 80f, frame / 10f, still) >= 0.69f)
                }
            }
        }
        assertTrue(winterStarPower(ConnectMode.Idle, 0f, 0f, false) == 0f)
    }
}

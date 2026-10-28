package app.farvater.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.farvater.core.parser.LinkParser
import app.farvater.ui.screens.HomeScreen
import app.farvater.ui.screens.ServersScreen
import app.farvater.ui.theme.FarvaterTheme
import app.farvater.vpn.Traffic
import app.farvater.vpn.VpnState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class AccessibilityExtrasTest {
    @get:Rule val rule = createComposeRule()
    private val node = LinkParser.parse("trojan://test@example.com:443#Первый")!!

    @Test fun groupAnnouncesStateAndChangesItAfterClick() {
        rule.setContent {
            var collapsed by remember { mutableStateOf(emptySet<String>()) }
            FarvaterTheme(dynamicColor = false) {
                ServersScreen(state = UiState(nodes = listOf(node), collapsedGroups = collapsed), onSelect = {},
                    onTestAll = {}, onCancelTest = {}, onDeleteManual = {}, onCopied = {},
                    onToggleGroup = { collapsed = if (it in collapsed) collapsed - it else collapsed + it })
            }
        }
        val expanded = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Развёрнуто")
        rule.onNode(expanded).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Свёрнуто"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        rule.onNodeWithText("Первый").assertDoesNotExist()
    }

    @Test fun onlyConnectionStatusIsAPoliteLiveRegion() = connectionStatusLiveRegion(app.farvater.data.UiStyle.IOS)

    @Test fun oneUiConnectionStatusIsAPoliteLiveRegion() = connectionStatusLiveRegion(app.farvater.data.UiStyle.ONEUI)

    private fun connectionStatusLiveRegion(style: app.farvater.data.UiStyle) {
        rule.setContent {
            FarvaterTheme(dynamicColor = false, style = style) {
                HomeScreen(state = UiState(nodes = listOf(node)), vpn = VpnState.Idle, traffic = Traffic(), history = emptyList(),
                    onToggle = {}, onFindWorking = {}, onCancelTest = {}, onOpenServers = {})
            }
        }
        assertEquals(1, rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).fetchSemanticsNodes().size)
    }
}

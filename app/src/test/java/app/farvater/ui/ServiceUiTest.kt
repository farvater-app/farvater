package app.farvater.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.farvater.core.parser.LinkParser
import app.farvater.data.*
import app.farvater.ui.screens.ServiceSettings
import app.farvater.ui.screens.ServersScreen
import app.farvater.ui.theme.FarvaterTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class ServiceUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun servicesAreOptInAndCustomUrlRejectsLocalAddresses() {
        val settings = mutableStateOf(AppSettings())
        rule.setContent { FarvaterTheme(dynamicColor = false) { ServiceSettings(settings.value) { settings.value = it(settings.value) } } }
        rule.onNodeWithText("ChatGPT").assertDoesNotExist()
        rule.onNodeWithText("Проверять страницы сервисов").performClick()
        assertTrue(settings.value.serviceChecksEnabled)
        rule.onNodeWithText("ChatGPT").performClick()
        assertEquals(setOf(ServiceKind.CHATGPT), settings.value.checkedServices)
        rule.onNodeWithText("Добавить или изменить адрес").performClick()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("https://router.lan/")
        rule.onNodeWithText("Сохранить").performClick()
        rule.onNodeWithText("Проверьте HTTPS-адрес", substring = true).assertExists()
        assertEquals("", settings.value.serviceCustomUrl)
        rule.onNode(hasSetTextAction() and hasText("https://router.lan/")).performTextReplacement("https://service.example.test/status")
        rule.onNodeWithText("Сохранить").performClick()
        assertEquals("https://service.example.test/status", settings.value.serviceCustomUrl)
        assertTrue(ServiceKind.CUSTOM in settings.value.checkedServices)
    }

    @Test
    fun filledCustomFieldsKeepAccessibleNames() {
        rule.setContent { FarvaterTheme(dynamicColor = false) {
            ServiceSettings(AppSettings(serviceChecksEnabled = true, serviceCustomUrl = "https://service.example.test/status")) {}
        } }
        rule.onNodeWithText("Добавить или изменить адрес").performClick()
        rule.onNode(hasSetTextAction() and hasContentDescription("HTTPS-адрес")).assertTextContains("https://service.example.test/status")
        rule.onNode(hasSetTextAction() and hasContentDescription("Код ответа: 200–599")).assertTextContains("200")
    }

    @Test
    @Config(qualifiers = "fa-w393dp-h852dp")
    fun customDialogKeepsPersianActionsCompleteAtDoubleFontSize() {
        org.robolectric.RuntimeEnvironment.setFontScale(2f)
        try {
            rule.setContent { FarvaterTheme(dynamicColor = false) {
                ServiceSettings(AppSettings(serviceChecksEnabled = true, serviceCustomUrl = "https://service.example.test/status")) {}
            } }
            rule.onNodeWithText("افزودن یا ویرایش نشانی").performClick()
            listOf("ذخیره", "لغو").forEach { label ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                rule.onNodeWithText(label).assertIsDisplayed().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse(label, layouts.single().hasVisualOverflow)
                assertEquals(label, 1, layouts.single().lineCount)
            }
            rule.onNode(hasSetTextAction() and hasContentDescription("کد پاسخ: ۲۰۰ تا ۵۹۹")).performScrollTo().assertIsDisplayed()
        } finally { org.robolectric.RuntimeEnvironment.setFontScale(1f) }
    }

    @Test
    fun resetClearsSearchAndFavoriteFilterTogether() {
        val nodes = listOf("First", "Second").mapIndexed { index, name ->
            LinkParser.parse("vless://test@reset$index.example.test:443?security=tls#$name")!!
        }
        val state = UiState(nodes = nodes, settings = AppSettings(favoriteNodeIds = setOf(nodes.first().id)))
        rule.setContent { FarvaterTheme(dynamicColor = false) { ServersScreen(state, {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("Избранные").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("No matching node")
        rule.onNodeWithText("Сбросить фильтры списка").performClick()
        rule.onNodeWithText("First").assertExists()
        rule.onNodeWithText("Second").assertExists()
    }

    @Test
    fun newlyReceivedResultAppearsBeforeMinuteTimerTicks() {
        val node = LinkParser.parse("vless://test@fresh.example.test:443?security=tls#Fresh")!!
        val target = ServiceChecks.builtIn.first { it.kind == ServiceKind.CHATGPT }
        val state = mutableStateOf(UiState(nodes = listOf(node), settings = AppSettings(serviceChecksEnabled = true,
            checkedServices = setOf(ServiceKind.CHATGPT))))
        rule.setContent { FarvaterTheme(dynamicColor = false) { ServersScreen(state.value, {}, {}, {}, {}, {}) } }
        rule.waitForIdle()
        val clockBefore = rule.mainClock.currentTime
        rule.runOnIdle {
            state.value = state.value.copy(serviceChecks = mapOf(ServiceChecks.key("ANY", node.id, target) to
                ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, System.currentTimeMillis())))
        }
        rule.waitForIdle()
        assertTrue(rule.mainClock.currentTime - clockBefore < 60_000)
        rule.onNodeWithContentDescription("ChatGPT: страница ответила или получен ожидаемый код").assertExists()
    }

    @Test
    fun filterIncludesOnlyFreshPositiveResultsAndBadgesHaveDescriptions() {
        val nodes = listOf("Positive", "Rejected", "Unknown", "Expired").mapIndexed { index, name ->
            LinkParser.parse("vless://test@node$index.example.test:443?security=tls#$name")!!
        }
        val target = ServiceChecks.builtIn.first { it.kind == ServiceKind.CHATGPT }
        val now = System.currentTimeMillis()
        val checks = nodes.mapIndexed { index, node ->
            ServiceChecks.key("ANY", node.id, target) to ServiceCheck(
                listOf(ServiceVerdict.RESPONDED, ServiceVerdict.REJECTED, ServiceVerdict.UNKNOWN, ServiceVerdict.RESPONDED)[index],
                ServiceReason.PAGE, if (index == 3) now - ServiceChecks.TTL_MS else now)
        }.toMap()
        val state = UiState(nodes = nodes, settings = AppSettings(serviceChecksEnabled = true, checkedServices = setOf(ServiceKind.CHATGPT)), serviceChecks = checks)
        rule.setContent { FarvaterTheme(dynamicColor = false) { ServersScreen(state, {}, {}, {}, {}, {}) } }
        rule.onNodeWithContentDescription("ChatGPT: страница ответила или получен ожидаемый код").assertExists()
        rule.onNodeWithText("Подходит для…").performClick()
        rule.onNodeWithText("ChatGPT").performClick()
        rule.onNodeWithText("Positive").assertExists()
        listOf("Rejected", "Unknown", "Expired").forEach { rule.onNodeWithText(it).assertDoesNotExist() }
    }
}

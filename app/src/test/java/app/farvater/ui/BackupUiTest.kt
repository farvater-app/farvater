package app.farvater.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import app.farvater.ui.screens.BackupPage
import app.farvater.ui.theme.FarvaterTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru", application = Application::class)
class BackupUiTest {
    @get:Rule val rule = createComposeRule()
    @org.junit.After fun restoreFont() { org.robolectric.RuntimeEnvironment.setFontScale(1f) }

    @Test
    fun exportWarnsAboutSecretsAndCanBeCancelled() {
        var writes = 0
        rule.setContent {
            FarvaterTheme(dynamicColor = false) { BackupPage(false, null, { writes++ }, {}, {}, {}) }
        }
        rule.onNodeWithText("Сохранить в файл").performClick()
        rule.onNodeWithText("Файл не зашифрован.", substring = true).assertExists()
        rule.onNodeWithText("Отмена").performClick()
        assertEquals(0, writes)
        rule.onNodeWithText("Файл не зашифрован.", substring = true).assertDoesNotExist()
    }

    @Test
    fun previewStatesReplacementAndRequiresConfirmation() {
        var restores = 0
        rule.setContent {
            FarvaterTheme(dynamicColor = false) { BackupPage(false, 2 to 3, {}, {}, { restores++ }, {}) }
        }
        assertEquals(0, restores)
        rule.onNodeWithText("Подписок: 2. Ручных узлов: 3.", substring = true).assertExists()
        rule.onNodeWithText("будут заменены", substring = true).assertExists()
        rule.onAllNodes(androidx.compose.ui.test.hasText("Восстановить") and androidx.compose.ui.test.hasClickAction())[0].performClick()
        assertEquals(1, restores)
    }
    @Test
    fun largeFontConfirmationKeepsRestoreActionVisible() {
        org.robolectric.RuntimeEnvironment.setFontScale(2f)
        var restores = 0
        rule.setContent {
            FarvaterTheme(dynamicColor = false) { BackupPage(false, 2 to 3, {}, {}, { restores++ }, {}) }
        }
        rule.onAllNodes(androidx.compose.ui.test.hasText("Восстановить") and androidx.compose.ui.test.hasClickAction())[0]
            .assertIsDisplayed().performClick()
        assertEquals(1, restores)
    }

    @Test
    @Config(qualifiers = "ru-w393dp-h852dp")
    fun doubleFontProvidesAnAccessibleWayToReadTheEntireWarning() {
        org.robolectric.RuntimeEnvironment.setFontScale(2f)
        try {
            rule.setContent { FarvaterTheme(dynamicColor = false) { BackupPage(false, 3 to 12, {}, {}, {}, {}) } }
            val next = rule.onNodeWithText("Читать дальше")
            next.assertExists()
            next.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            val warning = rule.onNodeWithText("Перед восстановлением отключите VPN.", substring = true)
            warning.assert(hasAnyAncestor(hasScrollAction()))
            repeat(8) {
                if (rule.onAllNodesWithText("Читать дальше").fetchSemanticsNodes().isNotEmpty()) next.performClick()
                rule.waitForIdle()
            }
            val range = rule.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(range.value() > 0f)
            assertEquals(range.maxValue(), range.value(), 1f)
            next.assertIsNotEnabled()
            rule.onNodeWithText("Отмена").assertIsDisplayed().assertHasClickAction()
            rule.onNode(hasText("Восстановить") and hasClickAction()).assertIsDisplayed()
        } finally { org.robolectric.RuntimeEnvironment.setFontScale(1f) }
    }

    @Test
    @Config(qualifiers = "fa-w393dp-h852dp")
    fun persianHeadingStaysWholeAtDoubleFont() {
        org.robolectric.RuntimeEnvironment.setFontScale(2f)
        try {
            rule.setContent { FarvaterTheme(dynamicColor = false) { BackupPage(false, 3 to 12, {}, {}, {}, {}) } }
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNode(hasText("بازیابی") and !hasClickAction())
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertFalse(layouts.single().hasVisualOverflow)
        } finally { org.robolectric.RuntimeEnvironment.setFontScale(1f) }
    }

    @Test
    @Config(qualifiers = "ru-w393dp-h852dp")
    fun securityTransferRequiresSeparateConsentAndListsEachChange() {
        org.robolectric.RuntimeEnvironment.setFontScale(2f)
        var consent: Boolean? = null
        val changes = app.farvater.data.BackupSecurityChanges(addedBypassApps = setOf("org.example.browser"),
            safeMode = false, encryptedDns = false, autoUpdates = false, sendHwid = true, directRuServices = true)
        rule.setContent { FarvaterTheme(dynamicColor = false) {
            BackupPage(false, 1 to 2, {}, {}, { consent = it }, {}, changes)
        } }
        rule.onNode(hasText("Восстановить") and hasClickAction()).performClick()
        assertEquals(false, consent)
        val toggle = rule.onNodeWithText("Перенести настройки безопасности")
        toggle.performScrollTo().assertIsOff()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        toggle.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().lineCount <= 3)
        assertFalse(layouts.single().hasVisualOverflow)
        for (text in listOf("org.example.browser", "Выключить: Только защищённые узлы", "Выключить: Зашифрованный DNS",
            "Выключить: Автоматическая проверка обновлений", "Включить: Отправлять HWID", "Включить: Российские сервисы мимо VPN")) {
            rule.onNodeWithText(text, substring = true).assertExists()
        }
        toggle.performClick().assertIsOn()
        rule.onNode(hasText("Восстановить") and hasClickAction()).performClick()
        assertEquals(true, consent)
    }

}

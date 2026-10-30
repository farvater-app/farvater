package app.farvater.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.farvater.data.AppSettings
import app.farvater.ui.screens.SourcesScreen
import app.farvater.ui.theme.FarvaterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class CatalogUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun failedCatalogueOffersOwnSubscription() {
        val source = SourceUi("public", "Example", "Author", "", "https://example.test/", description = "",
            community = true, enabled = true, nodeCount = 0, updatedAt = 0,
            error = "Unavailable", loading = false, networkUnavailable = true)
        rule.setContent { FarvaterTheme(dynamicColor = false) {
            SourcesScreen(UiState(settings = AppSettings(communityEnabled = true), sources = listOf(source)), {}, { _, _ -> }, {}, {}, {}, {})
        } }
        rule.onNodeWithText("Каталог рассчитан на Россию").performClick()
        rule.onNodeWithText("Вставьте ссылку, которую дал ваш провайдер или автор подписки.").assertExists()
    }

    @Test
    @Config(qualifiers = "en-w393dp-h852dp")
    fun englishExplainsCatalogueEvenBeforeFirstDownload() {
        rule.setContent { FarvaterTheme(dynamicColor = false) { SourcesScreen(UiState(), {}, { _, _ -> }, {}, {}, {}, {}) } }
        rule.onNodeWithText("This catalogue is intended for Russia").assertExists()
    }
}

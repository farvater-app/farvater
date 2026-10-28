package app.farvater.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.farvater.core.parser.LinkParser
import app.farvater.data.Prefs
import app.farvater.data.UiStyle
import app.farvater.ui.screens.ServersScreen
import app.farvater.ui.theme.FarvaterTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class FavoritesTest {
    @get:Rule val rule = createComposeRule()

    private val first = LinkParser.parse("trojan://test@example.com:443#Первый")!!
    private val second = LinkParser.parse("trojan://test@example.org:443#Второй")!!

    @Test fun starDoesNotConnectAndFilterShowsOnlyFavorites() {
        var selected = 0
        rule.setContent {
            var settings by remember { mutableStateOf(app.farvater.data.AppSettings()) }
            FarvaterTheme(style = UiStyle.IOS) {
                ServersScreen(
                    state = UiState(settings = settings, nodes = listOf(first, second)),
                    onSelect = { selected++ }, onTestAll = {}, onCancelTest = {}, onDeleteManual = {}, onCopied = {},
                    onToggleFavorite = { id -> settings = settings.copy(favoriteNodeIds = settings.favoriteNodeIds + id) },
                )
            }
        }
        rule.onNodeWithContentDescription("В избранное: Первый").performClick()
        rule.onNodeWithContentDescription("Убрать из избранного: Первый").assertIsOn()
        rule.onNodeWithText("Избранные").performClick().assertIsOn()
        rule.onNodeWithText("Первый").assertIsDisplayed()
        rule.onNodeWithText("Второй").assertDoesNotExist()
        assertEquals(0, selected)
    }

    @Test fun favoritesSurviveRestartAndSubscriptionRename() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("farvater", 0).edit().clear().commit()
        val prefs = Prefs(context)
        assertTrue(prefs.settings.favoriteNodeIds.isEmpty())
        prefs.settings = prefs.settings.copy(favoriteNodeIds = setOf(first.id))
        val renamed = LinkParser.parse("trojan://test@example.com:443#Новое имя")!!
        assertEquals(first.id, renamed.id)
        assertTrue(renamed.id in Prefs(context).settings.favoriteNodeIds)
    }
}

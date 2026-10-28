package app.farvater

import android.app.Application
import android.app.LocaleManager
import android.content.res.Resources
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.AppLanguages
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class AppLanguagesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun clear() {
        context.getSharedPreferences("farvater", 0).edit().clear().commit()
    }

    @Test fun legacyChoicePersistsAndKeepsMachineLocale() {
        val before = Locale.getDefault()
        AppLanguages.set(context, "fa")
        assertEquals("fa", AppLanguages.selected(context))
        val localized = AppLanguages.wrap(context)
        assertEquals("fa", localized.resources.configuration.locales[0].language)
        assertEquals(1, localized.resources.configuration.layoutDirection)
        assertEquals("زبان", localized.getString(R.string.settings_language))
        assertEquals(before, Locale.getDefault())
        assertEquals("fa", AppLanguages.wrap(context.createConfigurationContext(context.resources.configuration)).resources.configuration.locales[0].language)
        AppLanguages.set(context, "en")
        assertEquals("Language", AppLanguages.wrap(localized).getString(R.string.settings_language))
        assertEquals(0, AppLanguages.wrap(localized).resources.configuration.layoutDirection)
    }

    @Test fun russianPluralFormsAndPersianNumbersAreLocalized() {
        val ru = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ru"))
        }).resources
        assertEquals("1 узел", ru.getQuantityString(R.plurals.nodes_count, 1, 1))
        assertEquals("2 узла", ru.getQuantityString(R.plurals.nodes_count, 2, 2))
        assertEquals("5 узлов", ru.getQuantityString(R.plurals.nodes_count, 5, 5))
        assertEquals("21 узел", ru.getQuantityString(R.plurals.nodes_count, 21, 21))
        val fa = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("fa"))
        }).resources
        assertEquals("۲ گره", fa.getQuantityString(R.plurals.nodes_count, 2, 2))
    }

    @Test fun unsupportedLanguagesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { AppLanguages.set(context, "de") }
    }

    @Test fun systemChoiceUsesSupportedLanguageOrRussian() {
        AppLanguages.set(context, "")
        val system = Resources.getSystem().configuration.locales
        val expected = (0 until system.size()).map { system[it].language }.firstOrNull { it in AppLanguages.supported } ?: "ru"
        assertEquals(expected, AppLanguages.wrap(context).resources.configuration.locales[0].language)
    }

    @Test @Config(sdk = [35]) fun systemAndAppPickersShareSelection() {
        val manager = context.getSystemService(LocaleManager::class.java)
        AppLanguages.set(context, "fa")
        assertEquals("fa", manager.applicationLocales.toLanguageTags())
        manager.applicationLocales = LocaleList.forLanguageTags("en")
        assertEquals("en", AppLanguages.selected(context))
        assertEquals("Language", AppLanguages.wrap(context).getString(R.string.settings_language))
        AppLanguages.set(context, "")
        assertTrue(manager.applicationLocales.isEmpty)
    }

    @Test @Config(sdk = [35]) fun migrationDoesNotOverrideLaterSystemChoice() {
        context.getSharedPreferences("farvater", 0).edit().putString("app_language", "fa").commit()
        AppLanguages.migrate(context)
        assertEquals("fa", AppLanguages.selected(context))
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.forLanguageTags("en")
        AppLanguages.migrate(context)
        assertEquals("en", AppLanguages.selected(context))
    }
}

package app.farvater

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.LEGAL_VERSION
import app.farvater.data.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru", application = Application::class)
class LegalDocumentsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun packagedPolicyMatchesConsentRevisionAndReleaseDate() {
        val policy = app.assets.open("privacy.txt").bufferedReader().use { it.readText() }
        assertTrue(policy.contains("Редакция $LEGAL_VERSION. Действует с 30 октября 2026 года."))
        assertTrue(policy.contains("не больше 60 суточных записей"))
        assertTrue(policy.contains("избранные узлы"))
        assertTrue(policy.contains("Поделиться приложением"))
        assertFalse(policy.contains("Если дата на телефоне была переведена назад"))
        listOf("terms.txt", "licenses.txt").forEach { name ->
            assertTrue(app.assets.open(name).bufferedReader().use { it.readText() }.isNotBlank())
        }
    }

    @Test fun existingConsentRequiresNewRevisionWithoutLosingOtherSettings() {
        val store = app.getSharedPreferences("farvater", 0)
        store.edit().clear().putString("settings", "{\"onboardingDone\":true,\"acceptedLegalVersion\":3,\"communityEnabled\":false}").commit()
        val settings = Prefs(app).settings
        assertTrue(settings.onboardingDone)
        assertFalse(settings.communityEnabled)
        assertTrue(settings.acceptedLegalVersion < LEGAL_VERSION)
        Prefs(app).settings = settings.copy(acceptedLegalVersion = LEGAL_VERSION)
        assertEquals(LEGAL_VERSION, Prefs(app).settings.acceptedLegalVersion)
        assertFalse(Prefs(app).settings.communityEnabled)
    }
}

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
        assertTrue(policy.contains("Редакция $LEGAL_VERSION. Действует с 2 ноября 2026 года."))
        assertTrue(policy.contains("не больше 60 суточных записей"))
        assertTrue(policy.contains("избранные узлы"))
        assertTrue(policy.contains("Поделиться приложением"))
        assertFalse(policy.contains("Если дата на телефоне была переведена назад"))
        listOf("terms.txt", "licenses.txt").forEach { name ->
            assertTrue(app.assets.open(name).bufferedReader().use { it.readText() }.isNotBlank())
        }
    }

    @Test fun oneNewRevisionCoversBackgroundChecksAndLocalDiagnostics() {
        assertEquals(4 + 1, LEGAL_VERSION)
        val policy = app.assets.open("privacy.txt").bufferedReader().use { it.readText() }
        val terms = app.assets.open("terms.txt").bufferedReader().use { it.readText() }
        assertTrue(terms.contains("Редакция 3. Действует с 2 ноября 2026 года."))
        listOf(
            "www.gstatic.com/generate_204",
            "cp.cloudflare.com/generate_204",
            "www.apple.com/library/test/success.html",
            "Ранее сохранённый выбор Firefox заменяется адресом Google",
            "Начатая вами проверка продолжается при уходе из приложения",
            "Её можно остановить в приложении или уведомлении",
            "Обычная проверка ограничена 30 минутами",
            "серия запросов страниц сервисов — 90 секундами",
            "Android и прошивка могут завершить фоновую работу раньше",
            "Отдельная фоновая служба завершает начатую вами проверку",
            "может удерживать процессор включённым",
            "снимается при его завершении или отмене",
            "если уведомления запрещены",
            "тайм-аут, подтверждённый отказ в соединении, ошибка TLS или ошибка соединения",
            "если установить причину не удалось, она отмечается как неизвестная",
            "Текст исключения, IP-адреса, ссылки и ключи узлов в эту диагностическую запись не добавляются",
            "идентификаторы неответивших узлов последней завершённой полной проверки",
            "время проверки и режим сети",
            "Отменённая или незавершённая проверка не заменяет предыдущий результат",
            "Автоматический выбор по-прежнему исключает узел после двух последовательных неудач",
            "Эта запись не передаётся разработчику и не входит в резервную копию",
        ).forEach { assertTrue(it, policy.contains(it)) }
        assertFalse(policy.contains("detectportal.firefox.com"))
        assertFalse(policy.contains("При уходе из приложения проверка останавливается"))
    }

    @Test fun existingConsentRequiresNewRevisionWithoutLosingOtherSettings() {
        val store = app.getSharedPreferences("farvater", 0)
        store.edit().clear().putString("settings", "{\"onboardingDone\":true,\"acceptedLegalVersion\":4,\"communityEnabled\":false}").commit()
        val settings = Prefs(app).settings
        assertTrue(settings.onboardingDone)
        assertFalse(settings.communityEnabled)
        assertTrue(settings.acceptedLegalVersion < LEGAL_VERSION)
        Prefs(app).settings = settings.copy(acceptedLegalVersion = LEGAL_VERSION)
        assertEquals(LEGAL_VERSION, Prefs(app).settings.acceptedLegalVersion)
        assertFalse(Prefs(app).settings.communityEnabled)
    }
}

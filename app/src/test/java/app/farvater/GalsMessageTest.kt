package app.farvater

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GalsMessageTest {
    @Test
    fun unsuccessfulTelegramProbeDescribesHttpsWithoutInferringCountry() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        for (language in listOf("ru", "en", "fa")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
            val message = context.getString(R.string.gals_partial)
            assertTrue(message, message.contains("HTTPS"))
            assertFalse(message, listOf("Росс", "Russia", "روسیه").any(message::contains))
        }
    }
}

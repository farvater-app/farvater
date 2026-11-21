package app.farvater

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.BackupArchive
import app.farvater.data.ProbeMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import app.farvater.data.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru")
class PrefsRegressionTest {
    @Test
    fun manualChecksUseHttpsForFreshLegacyAndRestoredSettings() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val storage = context.getSharedPreferences("farvater", 0)
        val https = ProbeMethod.HTTPS
        for ((saved, expected) in listOf(null to https, "AUTO" to https, "TCP" to ProbeMethod.TCP)) {
            storage.edit().clear().apply()
            val document = if (saved == null) "{}" else """{"probeMethod":"$saved"}"""
            storage.edit().putString("settings", document).commit()
            assertEquals(expected, Prefs(context).settings.probeMethod)
            val archive = BackupArchive(settings = Json.parseToJsonElement(document).jsonObject)
            assertEquals(expected, archive.decodedSettings().probeMethod)
        }
    }

    @Test
    fun explicitlyDisabledSourceStaysDisabledAfterRestart() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("farvater", 0).edit().clear().commit()
        val first = Prefs(app)
        assertTrue("kfwl-wl" in first.enabledSources)
        first.enabledSources = first.enabledSources - "kfwl-wl"
        assertFalse("kfwl-wl" in Prefs(app).enabledSources)
    }
}

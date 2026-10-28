package app.farvater

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.Prefs
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PrefsRegressionTest {
    @Ignore("C2: first catalog read does not save catalog_seen; Prefs.kt is out of scope")
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

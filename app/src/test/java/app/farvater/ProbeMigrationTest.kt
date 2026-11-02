package app.farvater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.BackupArchive
import app.farvater.data.Prefs
import app.farvater.data.ProbeUrls
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ProbeMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val storage get() = context.getSharedPreferences("farvater", 0)

    @Before fun reset() { storage.edit().clear().commit() }

    @Test fun existingFirefoxSelectionMigratesAndPersistsWithoutChangingOtherSettings() {
        storage.edit().putString("settings", """{"testUrl":"https://detectportal.firefox.com/success.txt","mtu":1400,"safeMode":false}""").commit()
        assertEquals(ProbeUrls.GOOGLE, Prefs(context).settings.testUrl)
        assertEquals(ProbeUrls.GOOGLE, Prefs(context).settings.testUrl)
        assertEquals(1400, Prefs(context).settings.mtu)
        assertFalse(Prefs(context).settings.safeMode)
        assertFalse(storage.getString("settings", "")!!.contains("firefox"))
        assertEquals(setOf(ProbeUrls.GOOGLE, ProbeUrls.CLOUDFLARE, ProbeUrls.APPLE), ProbeUrls.all)
    }

    @Test fun oldFirefoxBackupRestoresWithDefaultProbe() {
        val archive = BackupArchive.read("""{"schema":1,"appVersion":"1.0.30","settings":{"testUrl":"https://detectportal.firefox.com/success.txt","mtu":1400}}""".byteInputStream())
        val prefs = Prefs(context)
        prefs.restoreBackup(archive)
        assertEquals(ProbeUrls.GOOGLE, Prefs(context).settings.testUrl)
        assertEquals(1400, Prefs(context).settings.mtu)
    }

    @Test fun currentChoicesRemainUnchanged() {
        for (url in ProbeUrls.all) {
            storage.edit().putString("settings", """{"testUrl":"$url"}""").commit()
            assertEquals(url, Prefs(context).settings.testUrl)
        }
    }
}

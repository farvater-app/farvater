package app.farvater

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.farvater.vpn.AutomationAccess
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class AutomationAccessTest {
    @Test fun generationRotationRevocationAndStorage() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AutomationAccess.revoke(app)
        assertNull(AutomationAccess.authorize(app, "x".repeat(43)))
        val key = AutomationAccess.issue(app)
        val first = AutomationAccess.authorize(app, key)
        assertEquals(32, first?.size)
        val stored = java.io.File(app.noBackupFilesDir, "vpn-automation.sha256").readBytes()
        assertArrayEquals(first, stored)
        assertFalse(stored.contentEquals(key.toByteArray()))
        assertNull(AutomationAccess.authorize(app, key + "!"))
        val second = AutomationAccess.issue(app)
        assertNotEquals(key, second)
        assertNull(AutomationAccess.authorize(app, key))
        assertNotNull(AutomationAccess.authorize(app, second))
        AutomationAccess.revoke(app)
        assertNull(AutomationAccess.authorize(app, second))
    }
}

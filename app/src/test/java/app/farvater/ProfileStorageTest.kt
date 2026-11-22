package app.farvater

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.parser.LinkParser
import app.farvater.data.Prefs
import app.farvater.data.SubscriptionRepository
import app.farvater.data.UserSource
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class ProfileStorageTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val marker = "fixture-storage-secret"
    private val node = LinkParser.parse("vless://$marker@node.example.test:443?security=tls")!!

    @Test fun aNewInstallationCanRetryAfterKeystoreBecomesAvailable() {
        val prefs = Prefs(context)
        val disk = context.getSharedPreferences("farvater", 0)
        app.farvater.storage.TestDeviceKey.unavailable = true
        try {
            assertFalse(prefs.prepareProtectedData())
            assertTrue(prefs.hwid.isEmpty())
            assertFalse(prefs.saveSettings(prefs.settings.copy(acceptedLegalVersion = app.farvater.data.LEGAL_VERSION)))
            assertFalse(prefs.saveUserSources(listOf(UserSource("user-test", "https://example.test/$marker", "test"))))
            assertFalse(disk.contains("sealed.v1.user_sources"))
            assertFalse(disk.contains("hwid"))
            assertFalse(disk.contains("sealed.v1.hwid"))
            assertEquals(0, prefs.settings.acceptedLegalVersion)
        } finally {
            app.farvater.storage.TestDeviceKey.unavailable = false
        }
        assertTrue(prefs.prepareProtectedData())
        val id = prefs.hwid
        assertTrue(id.isNotEmpty())
        assertEquals(id, Prefs(context).hwid)
        assertFalse(disk.contains("hwid"))
    }

    @Test fun reopenedSelectionsAndSubscriptionsRoundTripWithoutPlaintext() {
        val prefs = Prefs(context)
        val source = UserSource("user-0123456789", "https://example.test/$marker", "test")
        prefs.selectNode(node)
        prefs.userSources = listOf(source)
        assertEquals(node, Prefs(context).lastNode)
        assertEquals(listOf(source), Prefs(context).userSources)
        assertFalse(context.getSharedPreferences("farvater", 0).all.values.any { marker in it.toString() })
    }

    @Test fun oldSubscriptionCacheKeepsItsContentsAndFreshnessAfterMigration() {
        val old = File(context.filesDir, "subs/upgrade.txt")
        old.parentFile!!.mkdirs()
        old.writeText(node.raw)
        val stamp = 1700000000000L
        assertTrue(old.setLastModified(stamp))
        val snapshot = SubscriptionRepository(context).loadCached("upgrade")!!
        assertEquals(stamp, snapshot.updatedAt)
        assertEquals(node.raw, snapshot.nodes.single().raw)
        assertFalse(old.parentFile!!.listFiles()!!.any { marker in it.readBytes().toString(Charsets.ISO_8859_1) })
        assertEquals(stamp, SubscriptionRepository(context).loadCached("upgrade")!!.updatedAt)
    }

    @Test fun corruptedProtectedSelectionCannotFallBackToAPlaintextDuplicate() {
        val prefs = Prefs(context)
        prefs.selectNode(node)
        val disk = context.getSharedPreferences("farvater", 0)
        disk.edit().putString("sealed.v1.last_node", "AA==").putString("last_node", node.raw).commit()
        val reopened = Prefs(context)
        assertFalse(reopened.prepareProtectedData())
        assertTrue(reopened.protectedDataUnavailable)
        assertNull(reopened.lastNode)
        assertEquals("AA==", disk.getString("sealed.v1.last_node", null))
    }

    @Test fun damagedProtectedSettingsAndSourcesRecoverOnTheSamePreferencesInstance() {
        val source = UserSource("user-test", "https://example.test/$marker", "test")
        val prefs = Prefs(context)
        val hwid = prefs.hwid
        prefs.userSources = listOf(source)
        val disk = context.getSharedPreferences("farvater", 0)
        val saved = listOf("hwid", "user_sources").associate { "sealed.v1.$it" to disk.getString("sealed.v1.$it", null) }
        for (key in saved.keys) disk.edit().putString(key, "AA==").commit()
        val reopened = Prefs(context)
        assertFalse(reopened.prepareProtectedData())
        assertTrue(reopened.hwid.isEmpty())
        assertTrue(reopened.userSources.isEmpty())
        reopened.userSources = emptyList()
        for ((key, value) in saved) {
            assertEquals("AA==", disk.getString(key, null))
            disk.edit().putString(key, value).commit()
        }
        assertTrue(reopened.prepareProtectedData())
        assertEquals(hwid, reopened.hwid)
        assertEquals(listOf(source), reopened.userSources)
    }

    @Test fun unreadableRoutingSettingsCannotSilentlyUseTheDefaultRoutes() {
        val prefs = context.getSharedPreferences("farvater", 0)
        app.farvater.data.ProfileStorage.put(prefs.edit(), "settings", "{").commit()
        val reopened = Prefs(context)
        assertFalse(reopened.prepareProtectedData())
        assertTrue(reopened.protectedDataUnavailable)
        assertTrue(prefs.contains("sealed.v1.settings"))
    }

    @Test fun encryptedFieldsAuthenticateTheirLocationAndContents() {
        val original = marker.toByteArray()
        val first = app.farvater.storage.ProfileCipher.seal("pref/settings", original)
        val second = app.farvater.storage.ProfileCipher.seal("pref/settings", original)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(original, app.farvater.storage.ProfileCipher.open("pref/settings", first))
        assertThrows(java.security.GeneralSecurityException::class.java) {
            app.farvater.storage.ProfileCipher.open("file/settings", first)
        }
        second[second.lastIndex] = (second.last().toInt() xor 1).toByte()
        assertThrows(java.security.GeneralSecurityException::class.java) {
            app.farvater.storage.ProfileCipher.open("pref/settings", second)
        }
    }

    @Test fun unreadableManualArchiveCannotBeExportedOrOverwrittenByDeletion() {
        val repo = SubscriptionRepository(context)
        repo.appendManual(listOf(node))
        val file = context.filesDir.resolve("subs/manual.txt.sealed")
        val damaged = byteArrayOf(0, 1, 2)
        file.writeBytes(damaged)
        assertThrows(java.security.GeneralSecurityException::class.java) { repo.manualForBackup() }
        assertThrows(java.security.GeneralSecurityException::class.java) { repo.removeManual(node.id) }
        assertArrayEquals(damaged, file.readBytes())
    }

    @Test fun unreadableSelectionKeepsItsIdentifierAndCiphertextTogether() {
        val prefs = Prefs(context)
        prefs.selectNode(node)
        val disk = context.getSharedPreferences("farvater", 0)
        disk.edit().putString("sealed.v1.last_node", "AA==").commit()
        val reopened = Prefs(context)
        assertFalse(reopened.prepareProtectedData())
        assertFalse(reopened.selectNode(node.copy(id = "second")))
        assertEquals(node.id, reopened.selectedNodeId)
        assertEquals("AA==", disk.getString("sealed.v1.last_node", null))
    }

    @Test fun manualLinksCanBeEditedAndReopenedWithoutAReadableCopy() {
        val repo = SubscriptionRepository(context)
        assertEquals(1, repo.appendManual(listOf(node)))
        assertEquals(node.raw, SubscriptionRepository(context).loadManual().nodes.single().raw)
        assertFalse(context.filesDir.resolve("subs").listFiles().orEmpty().any {
            marker in it.readBytes().toString(Charsets.ISO_8859_1)
        })
        repo.removeManual(node.id)
        assertTrue(SubscriptionRepository(context).loadManual().nodes.isEmpty())
    }
}

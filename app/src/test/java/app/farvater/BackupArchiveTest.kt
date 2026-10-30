package app.farvater

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.model.ProxyNode
import app.farvater.core.parser.LinkParser
import app.farvater.data.AppSettings
import app.farvater.data.BackupArchive
import app.farvater.data.ProbeUrls
import app.farvater.data.Prefs
import app.farvater.data.SubscriptionRepository
import app.farvater.data.UserSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class, qualifiers = "fa")
class BackupArchiveTest {
    private lateinit var app: Application
    private lateinit var prefs: Prefs
    private lateinit var repo: SubscriptionRepository
    private val link = "vless://00000000-0000-0000-0000-000000000000@node.example.test:443?security=tls"
    private val node get() = LinkParser.parse(link)!!

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("farvater", 0).edit().clear().commit()
        File(app.filesDir, "subs/manual.txt").delete()
        prefs = Prefs(app)
        repo = SubscriptionRepository(app)
    }

    @Test
    fun roundTripRestoresAllDataAndKeepsLocalConsentAndIdentity() {
        prefs.settings = AppSettings(mtu = 1400, favoriteNodeIds = setOf(node.id), acceptedLegalVersion = 4, onboardingDone = true)
        val url = "https://subscription.example.test/test"
        prefs.userSources = listOf(UserSource("user-" + LinkParser.nodeId(url).take(10), url, "test"))
        repo.appendManual(listOf(node))
        val archive = BackupArchive.create(prefs, repo.loadManual().nodes, "fa")
        val text = archive.encode()
        assertFalse(text.contains("acceptedLegalVersion"))
        assertFalse(text.contains("onboardingDone"))
        assertFalse(text.contains("hwid"))
        assertFalse(text.contains("last_node"))
        val decoded = BackupArchive.read(ByteArrayInputStream(text.toByteArray()))
        val hwid = prefs.hwid
        prefs.settings = AppSettings(acceptedLegalVersion = 3, onboardingDone = true)
        prefs.userSources = emptyList()
        prefs.selectNode(node)
        prefs.restoreBackup(decoded)
        val reopened = Prefs(app)
        assertEquals(1400, reopened.settings.mtu)
        assertEquals(setOf(node.id), reopened.settings.favoriteNodeIds)
        assertEquals(3, reopened.settings.acceptedLegalVersion)
        assertTrue(reopened.settings.onboardingDone)
        assertEquals(hwid, reopened.hwid)
        assertNull(reopened.lastNode)
        assertEquals(archive.subscriptions, reopened.userSources)
        assertEquals(listOf(node.id), SubscriptionRepository(app).loadManual().nodes.map { it.id })
    }

    @Test
    fun restoredManualNodesAreAuthoritativeIncludingEmptyAndFurtherEdits() {
        repo.appendManual(listOf(node))
        val empty = BackupArchive.create(prefs, emptyList(), "")
        prefs.restoreBackup(empty)
        assertTrue(repo.loadManual().nodes.isEmpty())
        repo.appendManual(listOf(node))
        assertEquals(1, SubscriptionRepository(app).loadManual().nodes.size)
        repo.removeManual(node.id)
        assertTrue(SubscriptionRepository(app).loadCached(ProxyNode.MANUAL_SOURCE)!!.nodes.isEmpty())
        assertTrue(File(app.filesDir, "subs/manual.txt").readText().contains(link))
    }

    @Test
    fun rejectedArchivesDoNotChangeExistingData() {
        repo.appendManual(listOf(node))
        val valid = BackupArchive.create(prefs, listOf(node), "")
        val invalid = listOf(
            valid.copy(schema = 2),
            valid.copy(language = "invalid"),
            valid.copy(manualNodes = listOf(link, link)),
            valid.copy(manualNodes = listOf("vless://test@node.example.test:0")),
            valid.copy(manualNodes = listOf("ss://unknown:test@node.example.test:443")),
            valid.copy(settings = JsonObject(valid.settings + ("mtu" to JsonPrimitive(1)))),
            valid.copy(settings = JsonObject(valid.settings + ("uiStyle" to JsonPrimitive("FUTURE")))),
            valid.copy(settings = JsonObject(valid.settings + ("acceptedLegalVersion" to JsonPrimitive(999)))),
            valid.copy(subscriptions = listOf(UserSource("../../escape", "https://example.test/", "test"))),
            valid.copy(subscriptions = listOf(UserSource("user-0000000000", "http://localhost/", "test"))),
        )
        val before = app.getSharedPreferences("farvater", 0).all
        for (archive in invalid) {
            assertThrows(Exception::class.java) { prefs.restoreBackup(archive) }
            assertEquals(before, app.getSharedPreferences("farvater", 0).all)
            assertEquals(listOf(node.id), repo.loadManual().nodes.map { it.id })
        }
    }

    @Test
    fun readRejectsOversizedMalformedAndDeeplyNestedFiles() {
        for (bytes in listOf(ByteArray(BackupArchive.MAX_BYTES + 1), "{broken".toByteArray(),
            ("[".repeat(65) + "]".repeat(65)).toByteArray())) {
            assertThrows(Exception::class.java) { BackupArchive.read(ByteArrayInputStream(bytes)) }
        }
    }

    @Test
    fun failedCommitRestoresMemoryAndReopenedStore() {
        repo.appendManual(listOf(node))
        val archive = BackupArchive.create(prefs, emptyList(), "fa").let {
            it.copy(settings = JsonObject(it.settings + ("mtu" to JsonPrimitive(1400))))
        }
        val before = app.getSharedPreferences("farvater", 0).all
        val failing = Prefs(interceptCommit { editor, count ->
            val result = editor.commit()
            if (count == 1) false else result
        })
        assertThrows(Exception::class.java) { failing.restoreBackup(archive) }
        assertEquals(before, app.getSharedPreferences("farvater", 0).all)
        assertEquals(8500, failing.settings.mtu)
        assertEquals(8500, Prefs(app).settings.mtu)
        assertEquals(listOf(node.id), SubscriptionRepository(app).loadManual().nodes.map { it.id })
    }

    @Test
    fun interruptionAtCommitBoundaryLeavesAWholeGeneration() {
        for (afterCommit in listOf(false, true)) {
            setup()
            repo.appendManual(listOf(node))
            val archive = BackupArchive.create(prefs, emptyList(), "fa").let {
                it.copy(settings = JsonObject(it.settings + ("mtu" to JsonPrimitive(1400))))
            }
            val interrupted = Prefs(interceptCommit { editor, _ ->
                if (afterCommit) editor.commit()
                throw SimulatedProcessDeath()
            })
            assertThrows(SimulatedProcessDeath::class.java) { interrupted.restoreBackup(archive) }
            val reopened = Prefs(app)
            assertEquals(if (afterCommit) 1400 else 8500, reopened.settings.mtu)
            assertEquals(if (afterCommit) 0 else 1, SubscriptionRepository(app).loadManual().nodes.size)
            assertEquals(if (afterCommit) "fa" else null, app.getSharedPreferences("farvater", 0).getString("backup_language", null))
        }
    }

    @Test
    fun restoreKeepsCurrentSecuritySettingsWithoutSeparateConsent() {
        prefs.settings = AppSettings(safeMode = false, encryptedDns = false, autoUpdates = false,
            sendHwid = true, directRuServices = true, bypassApps = setOf("org.example.browser"), mtu = 1400)
        val archive = BackupArchive.create(prefs, emptyList(), "")
        val current = AppSettings(sendHwid = false, directRuServices = false, bypassApps = setOf("org.example.kept"))
        prefs.settings = current
        prefs.restoreBackup(archive)
        val restored = Prefs(app).settings
        assertEquals(current.bypassApps, restored.bypassApps)
        assertEquals(current.safeMode, restored.safeMode)
        assertEquals(current.encryptedDns, restored.encryptedDns)
        assertEquals(current.autoUpdates, restored.autoUpdates)
        assertEquals(current.sendHwid, restored.sendHwid)
        assertEquals(current.directRuServices, restored.directRuServices)
        assertEquals(1400, restored.mtu)
    }

    @Test
    fun archiveRejectsProbeUrlsOutsideBuiltInChoices() {
        val valid = BackupArchive.create(prefs, emptyList(), "")
        for (url in listOf("http://example.test/probe", "https://probe.example.test/test", "http://127.0.0.1/")) {
            val invalid = valid.copy(settings = JsonObject(valid.settings + ("testUrl" to JsonPrimitive(url))))
            assertThrows(url, Exception::class.java) { invalid.validated() }
        }
    }

    @Test
    fun newerSettingsFieldsAreIgnoredButKnownFieldsStayStrict() {
        val archive = BackupArchive.create(prefs, emptyList(), "").let {
            it.copy(appVersion = "1.0.99", settings = JsonObject(it.settings + mapOf(
                "futureOption" to JsonObject(mapOf("enabled" to JsonPrimitive(true))), "mtu" to JsonPrimitive(1400))))
        }
        val decoded = BackupArchive.read(archive.encode().byteInputStream())
        prefs.restoreBackup(decoded)
        assertEquals(1400, prefs.settings.mtu)
        val invalid = archive.copy(settings = JsonObject(archive.settings + ("mtu" to JsonPrimitive("invalid"))))
        assertThrows(Exception::class.java) { BackupArchive.read(invalid.encode().byteInputStream()) }
    }

    @Test
    fun explicitConsentAppliesExactlyTheReviewedSecurityChanges() {
        val incoming = AppSettings(safeMode = false, encryptedDns = false, autoUpdates = false,
            sendHwid = true, directRuServices = true, bypassApps = setOf("org.example.browser"))
        prefs.settings = incoming
        val archive = BackupArchive.create(prefs, emptyList(), "")
        prefs.settings = AppSettings(sendHwid = false, directRuServices = false, bypassApps = setOf("org.example.kept"))
        val changes = archive.securityChanges(prefs.settings)
        assertEquals(setOf("org.example.browser"), changes.addedBypassApps)
        assertEquals(setOf("org.example.kept"), changes.removedBypassApps)
        assertEquals(false, changes.safeMode)
        assertEquals(false, changes.encryptedDns)
        assertEquals(false, changes.autoUpdates)
        assertEquals(true, changes.sendHwid)
        assertEquals(true, changes.directRuServices)
        assertThrows(Exception::class.java) { prefs.restoreBackup(archive, true) }
        prefs.restoreBackup(archive, true, changes)
        assertEquals(incoming, Prefs(app).settings)
    }

    @Test
    fun changedSecurityStateInvalidatesEarlierConsentWithoutPartialWrites() {
        prefs.settings = AppSettings(safeMode = false)
        val archive = BackupArchive.create(prefs, emptyList(), "")
        prefs.settings = AppSettings()
        val reviewed = archive.securityChanges(prefs.settings)
        prefs.settings = prefs.settings.copy(bypassApps = setOf("org.example.changed"))
        val before = app.getSharedPreferences("farvater", 0).all
        assertThrows(Exception::class.java) { prefs.restoreBackup(archive, true, reviewed) }
        assertEquals(before, app.getSharedPreferences("farvater", 0).all)
    }

    @Test
    fun everyBuiltInProbeRemainsRestorable() {
        val archive = BackupArchive.create(prefs, emptyList(), "")
        for (url in ProbeUrls.all) {
            archive.copy(settings = JsonObject(archive.settings + ("testUrl" to JsonPrimitive(url)))).validated()
        }
    }

    private class SimulatedProcessDeath : Error()

    private fun interceptCommit(commit: (SharedPreferences.Editor, Int) -> Boolean): Context {
        val original = app.getSharedPreferences("farvater", 0)
        var count = 0
        val wrapped = java.lang.reflect.Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            if (method.name != "edit") method.invoke(original, *(args ?: emptyArray())) else {
                val editor = original.edit()
                java.lang.reflect.Proxy.newProxyInstance(
                    SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java),
                ) { proxy, action, values ->
                    if (action.name == "commit") commit(editor, ++count)
                    else action.invoke(editor, *(values ?: emptyArray())).let { if (it === editor) proxy else it }
                }
            }
        } as SharedPreferences
        return object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "farvater") wrapped else super.getSharedPreferences(name, mode)
        }
    }
}

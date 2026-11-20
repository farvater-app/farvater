package app.farvater

import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import app.farvater.data.AppSettings
import app.farvater.data.GalsGroup
import app.farvater.data.GalsGroups
import app.farvater.data.GalsPolicy
import app.farvater.data.ServiceKind
import app.farvater.data.ProbeUrls
import app.farvater.core.model.NetModeChoice
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class GalsPolicyTest {
    @Test
    fun legacySettingsKeepTheExistingGlobalPolicy() {
        val settings = Json.decodeFromString<AppSettings>("{}")
        assertTrue(settings.galsGroups.isEmpty())
        assertNull(settings.activeGalsGroupId)
        assertTrue(GalsPolicy.from(settings).accepts(node("first")))
    }

    @Test
    fun missingGroupAndEmptySelectedScopeNeverExpandToAllSources() {
        assertFalse(GalsPolicy.from(AppSettings(activeGalsGroupId = "missing")).accepts(node("first")))
        val group = GalsGroup("group", "Group", allSources = false)
        assertFalse(policy(group).accepts(node("first")))
        assertTrue(policy(group.copy(sourceIds = setOf("first"))).accepts(node("first")))
        assertFalse(policy(group.copy(sourceIds = setOf("first"))).accepts(node("second")))
    }

    @Test
    fun transportFilteringNormalizesKnownAliasesAndDoesNotMistakeHy2ForTcp() {
        val group = GalsGroup("group", "Group", transports = setOf("xhttp"))
        assertTrue(policy(group).accepts(node("first", "splithttp")))
        assertTrue(policy(group).accepts(node("first", "XHTTP")))
        assertFalse(policy(group).accepts(node("first", "unknown")))
        assertFalse(policy(group).accepts(node("first", "tcp")))
        assertTrue(policy(group.copy(transports = setOf("tcp"))).accepts(node("first", "raw")))
        assertFalse(policy(group.copy(transports = setOf("tcp"))).accepts(node("first").copy(protocol = Protocol.HYSTERIA2)))
    }

    @Test
    fun renamingAGroupDoesNotChangeItsEffectiveBehavior() {
        val group = GalsGroup("group", "Old name")
        assertTrue(policy(group).sameBehavior(policy(group.copy(name = "New name"))))
        assertFalse(policy(group).sameBehavior(policy(group.copy(intervalSeconds = 30))))
        assertFalse(policy(group).sameBehavior(policy(group.copy(sourceIds = setOf("first"), allSources = false))))
    }

    @Test
    fun changingProbeOrConnectionSettingsInvalidatesTheCapturedPolicy() {
        val settings = AppSettings()
        val before = GalsPolicy.from(settings)
        assertFalse(before.sameBehavior(GalsPolicy.from(settings.copy(testUrl = ProbeUrls.CLOUDFLARE))))
        assertFalse(before.sameBehavior(GalsPolicy.from(settings.copy(encryptedDns = false))))
        assertFalse(before.sameBehavior(GalsPolicy.from(settings.copy(netMode = NetModeChoice.BLACK))))
    }

    @Test
    fun invalidSettingsAreRejectedBeforeTheyCanScheduleNetworkRequests() {
        val group = GalsGroup("group", "Group")
        val invalid = listOf(
            group.copy(name = ""), group.copy(intervalSeconds = 1), group.copy(transports = setOf("unknown")),
            group.copy(target = ServiceKind.CUSTOM, customUrl = "http://example.test/"),
            group.copy(target = ServiceKind.CUSTOM, customUrl = "https://127.0.0.1/"),
            group.copy(target = ServiceKind.CUSTOM, customUrl = "https://example.test/", expectedCode = 0),
        )
        invalid.forEach { assertFalse(it.toString(), GalsGroups.valid(it)) }
        assertTrue(GalsGroups.valid(group.copy(target = ServiceKind.CUSTOM, customUrl = "https://example.test/", expectedCode = 204)))
        val duplicate = AppSettings(galsGroups = listOf(group, group.copy(name = "Duplicate")), activeGalsGroupId = group.id)
        assertFalse(GalsPolicy.from(duplicate).accepts(node("first")))
    }

    private fun policy(group: GalsGroup) = GalsPolicy.from(AppSettings(galsGroups = listOf(group), activeGalsGroupId = group.id))
    private fun node(source: String, transport: String = "tcp") = ProxyNode("node", Protocol.VLESS, "Node",
        "node.example.test", 443, "test", params = mapOf("type" to transport), raw = "vless://test", sourceId = source)
}

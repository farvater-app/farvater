package app.farvater

import app.farvater.core.model.VlessEncryption
import app.farvater.core.parser.LinkParser
import app.farvater.core.parser.XrayJsonParser
import app.farvater.core.xray.XrayConfigBuilder
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class VlessEncryptionTest {
    private val key = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { (it + 1).toByte() })
    private val encryption = "mlkem768x25519plus.native.0rtt.$key"

    private fun config(value: String = encryption, stream: String = "", extra: String = "") =
        """{"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"node.example.test","port":443,"users":[{"id":"00000000-0000-0000-0000-000000000001","encryption":"$value"}]}]}$stream}$extra]}"""

    @Test fun nativeEncryptionWorksWithoutTlsInLinksAndJson() {
        val link = LinkParser.parse("vless://id@node.example.test:443?security=none&encryption=$encryption")!!
        val json = XrayJsonParser.parse(config(), "manual")!!.single()
        for (node in listOf(link, json, json.copy(params = emptyMap()))) {
            assertNull(node.securityIssue)
            assertTrue(node.tags.contains("VLESS Encryption"))
            assertTrue(XrayConfigBuilder.build(node, false).contains(encryption))
        }
        assertEquals(encryption, json.params["encryption"])
    }

    @Test fun supportedModesAndKeysAreRecognised() {
        val mlkem = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(1184))
        for (mode in listOf("native", "xorpub", "random")) for (rtt in listOf("0rtt", "1rtt")) {
            for (payload in listOf(key, mlkem, "$key.$mlkem", "100-35-35.$key", "100-35-35.100-0-10.50-100-200.$key")) {
                assertEquals(payload, VlessEncryption.Kind.SUPPORTED, VlessEncryption.classify("mlkem768x25519plus.$mode.$rtt.$payload"))
            }
        }
    }

    @Test fun malformedOrUnknownEncryptionIsNeverTrusted() {
        val badMlkem = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(1184) { -1 })
        for (value in listOf("unknown", "MLKEM768x25519plus.native.0rtt.$key", "mlkem768x25519plus.bad.0rtt.$key",
            "mlkem768x25519plus.native.2rtt.$key", "mlkem768x25519plus.native.0rtt.",
            "mlkem768x25519plus.native.0rtt.100-35-35", "mlkem768x25519plus.native.0rtt.100-1-1.$key",
            "mlkem768x25519plus.native.0rtt.$key.100-35-35", "mlkem768x25519plus.native.0rtt.$badMlkem",
            "$encryption=", encryption.dropLast(1), "$encryption.!", "mlkem768x25519plus.native.0rtt.100-65554-65554.$key")) {
            assertEquals(value, VlessEncryption.Kind.UNKNOWN, VlessEncryption.classify(value))
            assertNotNull(XrayJsonParser.parse(config(value), "manual")!!.single().securityIssue)
        }
        for (value in listOf(null, "", "none")) assertEquals(VlessEncryption.Kind.NONE, VlessEncryption.classify(value))
    }

    @Test fun certificateAndNestedHopWarningsSurviveEncryption() {
        val stream = """, "streamSettings":{"security":"tls","tlsSettings":{"allowInsecure":true}}"""
        val node = XrayJsonParser.parse(config(stream = stream), "manual")!!.single()
        assertEquals("Без проверки сертификата", node.securityIssue)
        assertFalse(XrayConfigBuilder.build(node, false).contains("allowInsecure"))
        val hop = """, {"tag":"hop","protocol":"vless","settings":{"address":"hop.example.test","port":443,"id":"id","encryption":"none"}}"""
        val chain = XrayJsonParser.parse(config(stream = """, "proxySettings":{"tag":"hop"}""", extra = hop), "manual")!!.single()
        assertEquals("Без шифрования", chain.securityIssue)
    }

    @Test fun downloadTransportKeepsVlessEncryptionAssessment() {
        val stream = """, "streamSettings":{"network":"xhttp","xhttpSettings":{"extra":{"downloadSettings":{"network":"xhttp","security":"none"}}}}"""
        assertNull(XrayJsonParser.parse(config(stream = stream), "manual")!!.single().securityIssue)
        assertEquals("Без шифрования", XrayJsonParser.parse(config("none", stream), "manual")!!.single().securityIssue)
    }

    @Test fun flatAccountOverridesDecoyNestedAccountsExactlyAsCoreDoes() {
        for (actual in listOf("none", encryption)) for (nested in listOf("vnext", "users")) {
            val decoy = if (actual == "none") encryption else "none"
            val nestedValue = if (nested == "vnext") """[{"address":"decoy.example.test","port":443,"users":[{"id":"decoy","encryption":"$decoy"}]}]"""
                else """[{"id":"decoy","encryption":"$decoy"}]"""
            val raw = """{"outbounds":[{"protocol":"vless","settings":{"address":"actual.example.test","port":8443,"id":"actual","encryption":"$actual","$nested":$nestedValue}}]}"""
            val node = XrayJsonParser.parse(raw, "manual")!!.single()
            assertEquals("actual.example.test", node.address)
            assertEquals("actual", node.secret)
            assertEquals(actual == "none", node.isInsecure)
            assertEquals(actual != "none", node.tags.contains("VLESS Encryption"))
        }
    }

    @Test fun legacyWarningsRequireOriginalSourceWithoutChangingIdentity() {
        val fresh = XrayJsonParser.parse(config(), "source")!!.single()
        val legacy = fresh.copy(params = mapOf("jsonSecurityIssue" to "Без шифрования"))
        assertEquals("Обновите источник для проверки защиты", legacy.securityIssue)
        assertEquals(legacy.id, fresh.id)
        assertEquals(legacy.raw, fresh.raw)
        assertNull(fresh.securityIssue)
        assertEquals("Без проверки сертификата", fresh.copy(params = mapOf("jsonSecurityIssue" to "Без проверки сертификата")).securityIssue)
    }
}

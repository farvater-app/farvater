package app.farvater

import app.farvater.core.parser.LinkParser
import app.farvater.core.parser.SubscriptionParser
import org.junit.Assert.*
import org.junit.Test

class UnsupportedNodesTest {
    @Test
    fun deeplyNestedXhttpIsRejectedWithoutLosingFollowingNode() {
        val nested = "{\"network\":\"xhttp\",\"xhttpSettings\":{\"extra\":{\"downloadSettings\":".repeat(10_000) +
            "{}" + "}}}".repeat(10_000)
        val extra = java.net.URLEncoder.encode("{\"downloadSettings\":$nested}", "UTF-8")
        val good = "vless://test@node.example.test:443?security=tls"
        val parsed = SubscriptionParser.parse("$good&type=xhttp&extra=$extra\n$good", "test")
        assertEquals(1, parsed.nodes.size)
        assertEquals(1, parsed.skipped["unsupported_nesting"])
        val raw = """{"outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"node.example.test","port":443,"users":[{"id":"test"}]}]},"streamSettings":$nested}]}"""
        assertEquals(1, SubscriptionParser.parse(raw, "test").skipped["unsupported_nesting"])
    }

    @Test
    fun shadowsocksDirectSettingsOverrideInactiveServers() {
        for ((direct, inactive, accepted) in listOf(
            Triple("aes-128-gcm", "unknown", true), Triple("unknown", "aes-128-gcm", false),
        )) {
            val raw = """{"outbounds":[{"protocol":"shadowsocks","settings":{"address":"direct.example.test","port":443,"method":"$direct","password":"test","servers":[{"address":"inactive.example.test","port":8443,"method":"$inactive","password":"other"}]}}]}"""
            val parsed = SubscriptionParser.parse(raw, "test")
            assertEquals(accepted, parsed.nodes.isNotEmpty())
            if (accepted) {
                assertEquals("direct.example.test", parsed.nodes.single().address)
                assertEquals("aes-128-gcm", parsed.nodes.single().params["method"])
            } else assertEquals(1, parsed.skipped["unsupported_ss_method"])
        }
    }

    @Test
    fun shadowsocksRejectsMissingAndUnknownMethodsWithReason() {
        for (method in listOf("", "made-up", "aes-256-cfb")) {
            val link = "ss://$method:test@node.example.test:443"
            assertNull(LinkParser.parse(link))
            val parsed = SubscriptionParser.parse(link, "test")
            assertEquals(1, parsed.skipped["unsupported_ss_method"])
        }
    }

    @Test
    fun realityRequiresKeyButAllowsEmptyShortId() {
        val link = "vless://test@node.example.test:443?security=reality"
        for (suffix in listOf("", "&pbk=", "&pbk=%20")) {
            assertNull(LinkParser.parse(link + suffix))
            assertEquals(1, SubscriptionParser.parse(link + suffix, "test").skipped["missing_reality_key"])
        }
        assertNotNull(LinkParser.parse("$link&pbk=test&sid="))
    }

    @Test
    fun hysteriaRejectsUnknownObfuscationWithReason() {
        val link = "hy2://test@node.example.test:443?obfs=unknown&obfs-password=test"
        assertNull(LinkParser.parse(link))
        assertEquals(1, SubscriptionParser.parse(link, "test").skipped["unsupported_hysteria_obfs"])
        assertNotNull(LinkParser.parse(link.replace("unknown", "salamander")))
    }

    @Test
    fun jsonValidatesEveryServerAndOutbound() {
        val valid = """{"tag":"proxy","protocol":"trojan","settings":{"servers":[{"address":"node.example.test","port":443,"password":"test"}]},"streamSettings":{"security":"tls"}}"""
        val invalid = """{"tag":"next","protocol":"shadowsocks","settings":{"servers":[{"address":"other.example.test","port":443,"method":"aes-128-gcm","password":"test"},{"address":"other.example.test","port":443,"method":"unknown","password":"test"}]}}"""
        val parsed = SubscriptionParser.parse("""[{"outbounds":[$valid,$invalid]},{"outbounds":[$valid]}]""", "test")
        assertEquals(1, parsed.nodes.size)
        assertEquals(1, parsed.skipped["unsupported_ss_method"])
    }

    @Test
    fun jsonRealityRequiresKeyAndAcceptsPasswordAlias() {
        val raw = """{"outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"node.example.test","port":443,"users":[{"id":"test"}]}]},"streamSettings":{"security":"reality","realitySettings":{}}}]}"""
        assertTrue(SubscriptionParser.parse(raw, "test").nodes.isEmpty())
        assertEquals(1, SubscriptionParser.parse(raw, "test").skipped["missing_reality_key"])
        for (key in listOf("publicKey", "password")) {
            assertEquals(1, SubscriptionParser.parse(raw.replace("\"realitySettings\":{}", "\"realitySettings\":{\"$key\":\"test\",\"shortId\":\"\"}"), "test").nodes.size)
        }
    }

    @Test
    fun jsonHysteriaRejectsUnknownObfuscation() {
        val raw = """{"outbounds":[{"protocol":"hysteria","settings":{"address":"node.example.test","port":443,"version":2},"streamSettings":{"network":"hysteria","security":"tls","hysteriaSettings":{"auth":"test","obfs":"unknown","obfs-password":"test"}}}]}"""
        assertTrue(SubscriptionParser.parse(raw, "test").nodes.isEmpty())
        assertEquals(1, SubscriptionParser.parse(raw, "test").skipped["unsupported_hysteria_obfs"])
    }
}

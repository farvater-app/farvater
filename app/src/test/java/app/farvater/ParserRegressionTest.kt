package app.farvater

import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import app.farvater.core.model.onePerEndpoint
import app.farvater.core.parser.LinkParser
import app.farvater.core.parser.SubscriptionParser
import app.farvater.core.xray.XrayConfigBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.random.Random

class ParserRegressionTest {
    @Test
    fun nodeIdsRemainCompatibleWithPersistedIds() {
        val seeds = listOf("", " abc ", "узел\n東京", "\u0000", "a".repeat(10000)) +
            (0..2047).map { "vless://test@node$it.example.test:443?security=tls" }
        for (seed in seeds) {
            val expected = MessageDigest.getInstance("SHA-1").digest(seed.trim().toByteArray())
                .take(8).joinToString("") { "%02x".format(it) }
            assertEquals(seed, expected, LinkParser.nodeId(seed))
        }
        assertEquals("a9993e364706816a", LinkParser.nodeId("abc"))
    }

    @Test
    fun endpointSelectionPreservesOrderIdentityAndFirstSelectedDuplicate() {
        val a = node("a")
        val selected = a.copy(id = "selected", name = "first")
        val repeated = selected.copy(name = "second")
        val other = a.copy(id = "other", port = 8443)
        val json = a.copy(id = "json", raw = "{}")
        assertEquals(listOf(selected, other, json), listOf(a, other, selected, repeated, json).onePerEndpoint("selected"))
        assertEquals(listOf(a, other, json), listOf(a, other, selected, json).onePerEndpoint("missing"))
        assertTrue(emptyList<ProxyNode>().onePerEndpoint(null).isEmpty())
    }

    @Test
    fun endpointSelectionMatchesPreviousAlgorithmOnMixedLists() {
        val random = Random(136)
        repeat(300) {
            val nodes = List(random.nextInt(1, 200)) { index ->
                node("id${random.nextInt(30)}").copy(
                    protocol = Protocol.entries[random.nextInt(Protocol.entries.size)],
                    name = "row$index",
                    address = "node${random.nextInt(5)}.example.test".let { if (random.nextBoolean()) it.uppercase() else it },
                    port = 443 + random.nextInt(2),
                    raw = if (random.nextInt(5) == 0) "{}" else "vless://test",
                )
            }
            val selected = if (random.nextBoolean()) nodes.random(random).id else null
            val expected = nodes.groupBy { if (it.isJson) it.id else Triple(it.protocol, it.address.lowercase(), it.port) }
                .values.map { same -> same.firstOrNull { it.id == selected } ?: same.first() }
            assertEquals(expected, nodes.onePerEndpoint(selected))
        }
    }

    @Test
    fun malformedLinksDoNotThrowOrHideFollowingValidNode() {
        val malformed = listOf("vless://", "vless://user@host:0", "vless://user@host:65536", "vless://user@[broken:443",
            "vmess://%%%", "vmess://e30=", "ss://%%%", "hy2://user@host:not-a-port")
        malformed.forEach { assertNull(it, LinkParser.parse(it)) }
        val parsed = SubscriptionParser.parse((malformed + validLink).joinToString("\n"), "test")
        assertEquals(1, parsed.nodes.size)
        assertEquals(malformed.size, parsed.skipped.values.sum())
    }

    @Test
    fun malformedJsonAndMetadataDoNotDiscardValidLines() {
        val parsed = SubscriptionParser.parse("\uFEFF# profile-update-interval: invalid\n{broken\n$validLink", "test",
            mapOf("subscription-userinfo" to "upload=bad; download=7; total=no; expire=?"))
        assertEquals(1, parsed.nodes.size)
        assertNull(parsed.updateIntervalHours)
        assertEquals(7L, parsed.usage?.used)
        assertTrue(SubscriptionParser.parse("[null,42,{},[]]", "test").nodes.isEmpty())
    }

    @Ignore("S1: generated direct/dns-out match a provider balancer prefix; network config is out of scope")
    @Test
    fun balancerCannotSelectGeneratedDirectOutbound() {
        for (tag in listOf("d", "di", "b")) {
            val node = SubscriptionParser.parse(config(tag), "test").nodes.single()
            val built = Json.parseToJsonElement(XrayConfigBuilder.build(node, false)).jsonObject
            val selectors = built["routing"]!!.jsonObject["balancers"]?.jsonArray.orEmpty()
                .flatMap { it.jsonObject["selector"]!!.jsonArray.map { prefix -> prefix.jsonPrimitive.content } }
            val directTags = built["outbounds"]!!.jsonArray.filter {
                it.jsonObject["protocol"]!!.jsonPrimitive.content in setOf("freedom", "blackhole", "dns")
            }.map { it.jsonObject["tag"]!!.jsonPrimitive.content }
            assertFalse("$tag selects a non-proxy outbound", directTags.any { candidate -> selectors.any(candidate::startsWith) })
        }
    }

    @Ignore("S2: nested allowInsecure in XHTTP extra; network config is out of scope")
    @Test
    fun xhttpLinkDoesNotPassNestedAllowInsecure() {
        val extra = URLEncoder.encode("""{"downloadSettings":{"security":"tls","tlsSettings":{"allowInsecure":true}}}""", "UTF-8")
        val node = LinkParser.parse("$validLink&type=xhttp&extra=$extra")!!
        assertFalse(XrayConfigBuilder.build(node, false).contains("allowInsecure"))
    }

    @Ignore("S2: nested allowInsecure in JSON XHTTP; network config is out of scope")
    @Test
    fun jsonDoesNotPassNestedAllowInsecure() {
        val input = config("proxy").replace("\"security\":\"tls\"", """"security":"tls","network":"xhttp","xhttpSettings":{"extra":{"downloadSettings":{"security":"tls","tlsSettings":{"allowInsecure":true}}}}""")
        val node = SubscriptionParser.parse(input, "test").nodes.single()
        assertFalse(XrayConfigBuilder.build(node, false).contains("allowInsecure"))
    }

    @Ignore("S3: JSON security classification ignores secondary proxy outbounds")
    @Test
    fun mixedBalancerIsMarkedInsecure() {
        val insecure = """,{"tag":"proxy-plain","protocol":"vless","settings":{"vnext":[{"address":"plain.example.test","port":443,"users":[{"id":"test"}]}]},"streamSettings":{"security":"none"}}"""
        val node = SubscriptionParser.parse(config("proxy", insecure), "test").nodes.single()
        assertTrue(node.isInsecure)
    }

    private fun config(tag: String, extra: String = "") = """{"outbounds":[{"tag":"$tag","protocol":"trojan","settings":{"servers":[{"address":"secure.example.test","port":443,"password":"test"}]},"streamSettings":{"security":"tls"}}$extra],"routing":{"rules":[{"balancerTag":"pool"}],"balancers":[{"tag":"pool","selector":["$tag"]}]}}"""

    private fun node(id: String) = ProxyNode(id, Protocol.VLESS, id, "node.example.test", 443, "test", raw = validLink)

    private val validLink = "vless://00000000-0000-0000-0000-000000000000@node.example.test:443?security=tls"
}

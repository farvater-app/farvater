package app.farvater

import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import app.farvater.core.model.onePerEndpoint
import app.farvater.core.parser.LinkParser
import app.farvater.core.parser.SubscriptionParser
import app.farvater.core.xray.XrayConfigBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.random.Random

class ParserRegressionTest {
    @Test
    fun trojanJsonWithoutTlsIsUnsafeIncludingSavedAndBalancedNodes() {
        val plain = """{"tag":"plain","protocol":"trojan","settings":{"servers":[{"address":"node.example.test","port":443,"password":"test"}]}}"""
        val secure = plain.replace("plain", "proxy").dropLast(1) + """, "streamSettings":{"security":"tls"}}"""
        for (raw in listOf("""{"outbounds":[$plain]}""",
            """{"outbounds":[$secure,$plain],"routing":{"rules":[{"balancerTag":"pool"}],"balancers":[{"tag":"pool","selector":["proxy","plain"]}]}}""")) {
            val node = SubscriptionParser.parse(raw, "public").nodes.single()
            assertEquals("Без шифрования", node.securityIssue)
            assertEquals("Без шифрования", node.copy(params = node.params - "jsonSecurityIssue").securityIssue)
        }
        assertNull(LinkParser.parse("trojan://test@node.example.test:443")!!.securityIssue)
        assertNull(SubscriptionParser.parse("""{"outbounds":[$secure]}""", "public").nodes.single().securityIssue)
    }

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

    @Test
    fun xhttpLinkDoesNotPassNestedAllowInsecure() {
        val extra = URLEncoder.encode("""{"downloadSettings":{"security":"tls","tlsSettings":{"allowInsecure":true}}}""", "UTF-8")
        val node = LinkParser.parse("$validLink&type=xhttp&extra=$extra")!!
        assertFalse(XrayConfigBuilder.build(node, false).contains("allowInsecure"))
    }

    @Test
    fun jsonDoesNotPassNestedAllowInsecure() {
        val input = config("proxy").replace("\"security\":\"tls\"", """"security":"tls","network":"xhttp","xhttpSettings":{"extra":{"downloadSettings":{"security":"tls","tlsSettings":{"allowInsecure":true}}}}""")
        val node = SubscriptionParser.parse(input, "test").nodes.single()
        assertFalse(XrayConfigBuilder.build(node, false).contains("allowInsecure"))
    }

    @Test
    fun mixedBalancerIsMarkedInsecure() {
        val insecure = """,{"tag":"proxy-plain","protocol":"vless","settings":{"vnext":[{"address":"plain.example.test","port":443,"users":[{"id":"test"}]}]},"streamSettings":{"security":"none"}}"""
        val node = SubscriptionParser.parse(config("proxy", insecure), "test").nodes.single()
        assertTrue(node.isInsecure)
    }

    @Test
    fun recursiveCertificateSanitizingCoversCaseArraysSavedNodesAndTestConfig() {
        val extras = """{"downloadSettings":{"security":"tls","tlsSettings":{"AllowInsecure":true,"pinnedPeerCertSha256":"test-pin","verifyPeerCertByName":"node.example.test"}},"items":[{"ALLOWINSECURE":true},[{"allowInsecure":false}]],"hysteriaSettings":{"masquerade":{"InSeCuRe":true,"url":"https://node.example.test"}}}"""
        val input = config("proxy").replace("\"security\":\"tls\"", """"security":"tls","network":"xhttp","xhttpSettings":{"extra":$extras}""")
        val parsed = SubscriptionParser.parse(input, "test").nodes.single()
        assertNoInsecureKeys(Json.parseToJsonElement(parsed.raw))
        val saved = parsed.copy(raw = input, params = emptyMap())
        val link = LinkParser.parse("$validLink&type=xhttp&extra=${URLEncoder.encode(extras, "UTF-8")}")!!
        for (node in listOf(parsed, saved, link)) {
            for (forTest in listOf(false, true)) {
                val built = XrayConfigBuilder.build(node, false, forTest = forTest)
                assertNoInsecureKeys(Json.parseToJsonElement(built))
                assertTrue(built.contains("\"pinnedPeerCertSha256\":\"test-pin\""))
                assertTrue(built.contains("\"verifyPeerCertByName\":\"node.example.test\""))
            }
        }
    }

    @Test
    fun legitimateBalancerPrefixesKeepAllProxyCandidates() {
        for (prefix in listOf("proxy", "de", "nl-", "d", "")) {
            val tag = if (prefix == "d") "de-1" else "${prefix}one"
            val input = config(tag, ",${proxy("${tag}-two")}")
                .replace("\"selector\":[\"$tag\"]", "\"selector\":[\"$prefix\"]")
            val node = SubscriptionParser.parse(input, "test").nodes.single()
            for (encryptedDns in listOf(false, true)) {
                val built = Json.parseToJsonElement(XrayConfigBuilder.build(node, false, encryptedDns = encryptedDns)).jsonObject
                assertEquals(listOf(tag, "$tag-two"), built["routing"]!!.jsonObject["balancers"]!!.jsonArray.single()
                    .jsonObject["selector"]!!.jsonArray.map { it.jsonPrimitive.content })
                assertSafeBalancers(built)
            }
        }
    }

    @Test
    fun savedMultipleBalancersAreCheckedAgainstFinalOutbounds() {
        val input = """{"outbounds":[${proxy("proxy")},${proxy("d")},${proxy("nl-one")},{"tag":"proxy-service","protocol":"freedom"}],"routing":{"balancers":[{"tag":"unsafe-provider","selector":["proxy"]},{"tag":"unsafe-generated","selector":["d"]},{"tag":"safe","selector":["nl-"],"fallbackTag":"direct"},{"tag":"empty-list","selector":[]},{"tag":"empty-prefix","selector":[""]}]}}"""
        val saved = node("saved").copy(raw = input)
        val built = Json.parseToJsonElement(XrayConfigBuilder.build(saved, false)).jsonObject
        assertSafeBalancers(built)
        val balancers = built["routing"]!!.jsonObject["balancers"]!!.jsonArray
        assertEquals(listOf("safe"), balancers.map { it.jsonObject["tag"]!!.jsonPrimitive.content })
        assertNull(balancers.single().jsonObject["fallbackTag"])
        assertTrue(built["routing"]!!.jsonObject["rules"]!!.jsonArray.none { "balancerTag" in it.jsonObject })
        assertFalse(XrayConfigBuilder.build(saved, false, forTest = true).contains("\"balancers\""))
    }

    @Test
    fun providerServiceTagsCannotBecomeBalancerCandidates() {
        for (service in listOf("freedom", "blackhole", "dns", "loopback")) {
            val input = config("proxy", """,{"tag":"proxy-service","protocol":"$service"},{"tag":"direct","protocol":"freedom"},{"tag":"block","protocol":"blackhole"},{"tag":"dns-out","protocol":"dns"}""")
            val node = SubscriptionParser.parse(input, "test").nodes.single()
            assertSafeBalancers(Json.parseToJsonElement(XrayConfigBuilder.build(node, false)).jsonObject)
        }
    }

    @Test
    fun observerSelectorsArePreservedAndDoNotEnterTrafficSelectors() {
        for (observer in listOf("observatory", "burstObservatory")) {
            val input = config("de-one").dropLast(1) + """, "$observer":{"subjectSelector":["d"]}}"""
            val node = SubscriptionParser.parse(input, "test").nodes.single()
            val built = Json.parseToJsonElement(XrayConfigBuilder.build(node, false)).jsonObject
            assertEquals("d", built[observer]!!.jsonObject["subjectSelector"]!!.jsonArray.single().jsonPrimitive.content)
            assertSafeBalancers(built)
        }
    }

    @Test
    fun everySelectedProxyAndFallbackUsesExistingSecurityClassification() {
        val cases = listOf(
            proxy("proxy-plain", security = "none") to "Без шифрования",
            """{"tag":"proxy-plain","protocol":"vless","settings":{"vnext":[{"address":"node.example.test","port":443,"users":[{"id":"test"}]}]}}""" to "Без шифрования",
            """{"tag":"proxy-plain","protocol":"vmess","settings":{"vnext":[{"address":"node.example.test","port":443,"users":[{"id":"test","security":"none"}]}]}}""" to "Без шифрования",
            """{"tag":"proxy-plain","protocol":"shadowsocks","settings":{"servers":[{"address":"node.example.test","port":443,"method":"aes-128-cfb","password":"test"}]}}""" to "Устаревшее шифрование",
            proxy("proxy-plain").replace("\"security\":\"tls\"", """"security":"tls","tlsSettings":{"AllowInsecure":true}""") to "Без проверки сертификата",
        )
        for ((outbound, issue) in cases) {
            val node = SubscriptionParser.parse(config("proxy", ",$outbound"), "test").nodes.single()
            assertEquals(issue, node.securityIssue)
            assertEquals(issue, Json.decodeFromString<ProxyNode>(Json.encodeToString(node)).securityIssue)
            val fallback = config("main", ",$outbound").replace("\"selector\":[\"main\"]", "\"selector\":[\"main\"],\"fallbackTag\":\"proxy-plain\"")
            assertEquals(issue, SubscriptionParser.parse(fallback, "test").nodes.single().securityIssue)
        }
    }

    @Test
    fun savedNodesAndNestedDialerChainsAreClassifiedWithoutMigration() {
        val plain = proxy("relay", "none")
        for (chain in listOf(
            """"sockopt":{"dialerProxy":"relay"}""",
            """"network":"xhttp","xhttpSettings":{"extra":{"downloadSettings":{"sockopt":{"dialerProxy":"relay"}}}}""",
        )) {
            val input = config("proxy", ",$plain").replace("\"security\":\"tls\"", """"security":"tls",$chain""")
            val parsed = SubscriptionParser.parse(input, "test").nodes.single()
            assertEquals("Без шифрования", parsed.securityIssue)
            val saved = parsed.copy(params = parsed.params - "jsonSecurityIssue")
            assertEquals("Без шифрования", saved.securityIssue)
            val restored = Json.decodeFromString<ProxyNode>(Json.encodeToString(saved))
            assertEquals(saved.id, restored.id)
            assertEquals(saved.raw, restored.raw)
            assertTrue(restored.isInsecure)
            assertFalse(Json.encodeToString(saved).contains("jsonSecurityIssue"))
        }
    }

    @Test
    fun unusedProxiesDoNotHideSafeNodeAndCyclesTerminate() {
        val unused = proxy("unused", "none")
        val input = config("proxy", ",$unused")
        assertFalse(SubscriptionParser.parse(input, "test").nodes.single().isInsecure)
        val cycle = config("proxy", ",${proxy("relay")}")
            .replace("\"security\":\"tls\"", """"security":"tls","sockopt":{"dialerProxy":"relay"}""")
        assertFalse(SubscriptionParser.parse(cycle, "test").nodes.single().isInsecure)
    }

    @Test
    fun secondaryServersUsersAndBalancersCannotHideInsecureProxy() {
        val mixedUsers = """{"tag":"proxy-vmess","protocol":"vmess","settings":{"vnext":[{"address":"node.example.test","port":443,"users":[{"id":"test","security":"auto"},{"id":"test2","security":"zero"}]}]}}"""
        assertTrue(SubscriptionParser.parse(config("proxy", ",$mixedUsers"), "test").nodes.single().isInsecure)
        val saved = node("saved").copy(raw = """{"outbounds":[${proxy("proxy")},${proxy("plain", "none")}],"routing":{"balancers":[{"tag":"one","selector":["proxy"]},{"tag":"two","selector":["plain"]}]}}""")
        assertTrue(saved.isInsecure)
        val mainShadowsocks = """{"tag":"proxy","protocol":"shadowsocks","settings":{"servers":[{"address":"node.example.test","port":443,"password":"test","method":"aes-128-gcm"}]},"streamSettings":{"sockopt":{"dialerProxy":"plain"}}}"""
        val input = """{"outbounds":[$mainShadowsocks,${proxy("plain", "none") }]}"""
        assertTrue(SubscriptionParser.parse(input, "test").nodes.single().isInsecure)
    }

    @Test
    fun nullAliasCannotHideNonProxyTagFromBalancerChecks() {
        for (service in listOf("freedom", "blackhole", "dns", "loopback")) {
            val input = config("proxy", """,{"tag":"proxy-leak","Tag":null,"protocol":"$service","Protocol":null}""")
            val parsed = SubscriptionParser.parse(input, "test").nodes.single()
            for (node in listOf(parsed, parsed.copy(raw = input))) {
                val built = Json.parseToJsonElement(XrayConfigBuilder.build(node, false)).jsonObject
                assertSafeBalancers(built)
                if (built["outbounds"]!!.jsonArray.any { it.jsonObject["tag"]?.jsonPrimitive?.content == "proxy-leak" }) {
                    assertTrue(built["routing"]!!.jsonObject["balancers"]?.jsonArray.orEmpty().isEmpty())
                }
            }
        }
    }

    @Test
    fun mergedCaseAliasesCannotHideMainSecurityOrDialerChain() {
        val plain = proxy("relay", "none")
        val streams = listOf(
            """"streamSettings":{"security":"tls","sockopt":{"dialerProxy":"relay"}},"StreamSettings":{"network":"tcp"}""",
            """"streamSettings":{"security":"tls","sockopt":{"dialerProxy":"relay"}},"StreamSettings":{"sockopt":{"tcpFastOpen":true}}""",
            """"streamSettings":{"security":"tls","sockopt":{"dialerProxy":"relay"},"Sockopt":{"tcpFastOpen":true}}""",
            """"streamSettings":{"security":"none"},"StreamSettings":{"network":"tcp"}""",
            """"streamSettings":{"security":"none","Security":null}""",
        )
        for (stream in streams) {
            val input = config("proxy", ",$plain").replace("\"streamSettings\":{\"security\":\"tls\"}", stream)
            val parsed = SubscriptionParser.parse(input, "test").nodes.single()
            assertEquals(stream, "Без шифрования", parsed.securityIssue)
            assertEquals(stream, "Без шифрования", parsed.copy(raw = input, params = parsed.params - "jsonSecurityIssue").securityIssue)
        }
        val cleared = config("proxy", ",$plain").replace("\"streamSettings\":{\"security\":\"tls\"}", """"streamSettings":{"security":"tls","sockopt":{"dialerProxy":"relay"}},"StreamSettings":{"sockopt":null}""")
        assertFalse(SubscriptionParser.parse(cleared, "test").nodes.single().isInsecure)
    }

    @Test
    fun httpHeadersAreNotDialerChainsAndIgnoredDownloadSettingsStayUnused() {
        val plain = proxy("relay", "none")
        val transports = listOf(
            """"network":"ws","wsSettings":{"headers":{"dialerProxy":"relay"}}""",
            """"network":"xhttp","xhttpSettings":{"extra":{"headers":{"dialerProxy":"relay","sockopt":{"dialerProxy":"relay"}}}}""",
            """"network":"xhttp","xhttpSettings":{"downloadSettings":{"sockopt":{"dialerProxy":"relay"}},"extra":{"headers":{"test":"value"}}}""",
        )
        for (transport in transports) {
            val input = config("proxy", ",$plain").replace("\"security\":\"tls\"", """"security":"tls",$transport""")
            assertFalse(transport, SubscriptionParser.parse(input, "test").nodes.single().isInsecure)
        }
        val chain = config("proxy", ",$plain").replace("\"security\":\"tls\"", """"security":"tls","network":"ws","sockopt":{"dialerProxy":"relay"}""")
        assertTrue(SubscriptionParser.parse(chain, "test").nodes.single().isInsecure)
    }

    @Test
    fun nullableTransportAliasesSelectOnlyTheActiveDownloadChain() {
        val plain = proxy("relay", "none")
        val download = """"xhttpSettings":{"extra":{"downloadSettings":{"sockopt":{"dialerProxy":"relay"}}}}"""
        val active = config("proxy", ",$plain").replace("\"security\":\"tls\"", """"security":"tls","method":"tcp","Method":null,"network":"xhttp",$download""")
        val inactive = config("proxy", ",$plain").replace("\"security\":\"tls\"", """"security":"tls","network":"xhttp","Network":null,$download""")
        for ((input, insecure) in listOf(active to true, inactive to false)) {
            val parsed = SubscriptionParser.parse(input, "test").nodes.single()
            assertEquals(insecure, parsed.isInsecure)
            assertEquals(insecure, parsed.copy(raw = input, params = parsed.params - "jsonSecurityIssue").isInsecure)
        }
    }

    private fun assertNoInsecureKeys(value: JsonElement, parent: String = "") {
        when (value) {
            is JsonObject -> value.forEach { (key, child) ->
                assertFalse(key.equals("allowInsecure", true))
                assertFalse(parent.equals("masquerade", true) && key.equals("insecure", true))
                assertNoInsecureKeys(child, key)
            }
            is JsonArray -> value.forEach { assertNoInsecureKeys(it, parent) }
            else -> Unit
        }
    }

    private fun assertSafeBalancers(config: JsonObject) {
        val proxies = setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria")
        val outputs = config.getValue("outbounds").jsonArray.map { it.jsonObject }
        for (balancer in config["routing"]?.jsonObject?.get("balancers")?.jsonArray.orEmpty()) {
            val selectors = balancer.jsonObject.getValue("selector").jsonArray.map { it.jsonPrimitive.content }
            assertTrue(selectors.isNotEmpty())
            for (output in outputs.filter { it.getValue("protocol").jsonPrimitive.content !in proxies }) {
                val tag = output.getValue("tag").jsonPrimitive.content
                assertFalse("$tag selected by $selectors", selectors.any(tag::startsWith))
            }
        }
    }

    private fun proxy(tag: String, security: String = "tls") = """{"tag":"$tag","protocol":"trojan","settings":{"servers":[{"address":"node.example.test","port":443,"password":"test"}]},"streamSettings":{"security":"$security"}}"""

    private fun config(tag: String, extra: String = "") = """{"outbounds":[{"tag":"$tag","protocol":"trojan","settings":{"servers":[{"address":"secure.example.test","port":443,"password":"test"}]},"streamSettings":{"security":"tls"}}$extra],"routing":{"rules":[{"balancerTag":"pool"}],"balancers":[{"tag":"pool","selector":["$tag"]}]}}"""

    private fun node(id: String) = ProxyNode(id, Protocol.VLESS, id, "node.example.test", 443, "test", raw = validLink)

    private val validLink = "vless://00000000-0000-0000-0000-000000000000@node.example.test:443?security=tls"
}

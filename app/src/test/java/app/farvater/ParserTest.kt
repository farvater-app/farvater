package app.farvater

import app.farvater.core.model.Protocol
import app.farvater.core.model.onePerEndpoint
import app.farvater.core.parser.LinkParser
import app.farvater.core.parser.SubscriptionParser
import app.farvater.core.xray.XrayConfigBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ParserTest {
    private val vless = "vless://11111111-2222-3333-4444-555555555555@95.163.1.2:443" +
        "?security=reality&sni=ok.ru&pbk=AbC-dEf_123&sid=ab12&fp=chrome&type=xhttp&path=%2Fapi&flow=" +
        "#%F0%9F%87%B7%F0%9F%87%BA%20Moscow%201"

    @Test fun vlessReality() {
        val n = LinkParser.parse(vless)!!
        assertEquals(Protocol.VLESS, n.protocol)
        assertEquals("95.163.1.2", n.address)
        assertEquals(443, n.port)
        assertEquals("reality", n.security)
        assertEquals("xhttp", n.transport)
        assertEquals("/api", n.params["path"])
        assertEquals("AbC-dEf_123", n.params["pbk"])
        assertTrue(n.name.endsWith("Moscow 1"))
    }

    @Test fun sameServerDifferentNameHasSameId() {
        val a = LinkParser.parse(vless)!!
        val b = LinkParser.parse(vless.substringBefore('#') + "#other")!!
        assertEquals(a.id, b.id)
    }

    @Test fun onePerEndpointKeepsSelected() {
        val a = LinkParser.parse(vless)!!
        val b = LinkParser.parse(vless.replace("fp=chrome", "fp=firefox"))!!
        val c = LinkParser.parse("trojan://pass@[2001:db8::1]:8443?sni=vk.com#t")!!
        assertEquals(listOf(a, c), listOf(a, b, c).onePerEndpoint(null))
        assertEquals(listOf(b, c), listOf(a, b, c).onePerEndpoint(b.id))
    }

    @Test fun ipv6Host() {
        val n = LinkParser.parse("trojan://pass@[2001:db8::1]:8443?sni=vk.com#t")!!
        assertEquals("2001:db8::1", n.address)
        assertEquals(8443, n.port)
    }

    @Test fun vmessBase64() {
        val json = """{"v":"2","ps":"vm","add":"1.2.3.4","port":"8080","id":"abc","net":"ws","path":"/ws","tls":"tls","sni":"ya.ru"}"""
        val link = "vmess://" + Base64.getEncoder().encodeToString(json.toByteArray())
        val n = LinkParser.parse(link)!!
        assertEquals(Protocol.VMESS, n.protocol)
        assertEquals(8080, n.port)
        assertEquals("ws", n.transport)
        assertEquals("tls", n.security)
    }

    @Test fun vmessSameConfigDifferentNameHasSameId() {
        fun link(name: String) = "vmess://" + Base64.getEncoder().encodeToString(
            """{"v":"2","ps":"$name","add":"1.2.3.4","port":"8080","id":"abc","net":"ws"}""".toByteArray(),
        )
        assertEquals(LinkParser.parse(link("a"))!!.id, LinkParser.parse(link("b"))!!.id)
    }

    @Test fun shadowsocksSip002() {
        val user = Base64.getUrlEncoder().withoutPadding().encodeToString("chacha20-ietf-poly1305:secret".toByteArray())
        val n = LinkParser.parse("ss://$user@5.6.7.8:8388#ss")!!
        assertEquals("chacha20-ietf-poly1305", n.params["method"])
        assertEquals("secret", n.secret)
    }

    @Test fun garbageIsNull() {
        assertNull(LinkParser.parse("vless://broken"))
        assertNull(LinkParser.parse("https://example.com"))
    }

    @Test fun base64SubscriptionWithMetaAndStub() {
        val body = "#profile-title: base64:" + Base64.getEncoder().encodeToString("Тест".toByteArray()) + "\n" +
            "#profile-update-interval: 2\n" +
            "vless://x@0.0.0.0:1#Подписка устарела\n" + vless + "\n" + vless + "\n"
        val encoded = Base64.getEncoder().encodeToString(body.toByteArray())
        val s = SubscriptionParser.parse(encoded, "src")
        assertEquals("Тест", s.title)
        assertEquals(2, s.updateIntervalHours)
        assertEquals(1, s.nodes.size)
        assertEquals(listOf("Подписка устарела"), s.notices)
    }

    @Test fun multilineAnnounceIsKeptWhole() {
        val body = "#profile-title: РКП\n#announce: ⚠️ - Конфиг без шифрования трафика.\n" +
            "#announce: 🏴 - Конфиг с неизвестным выходным трафиком.\n" + vless + "\n"
        val s = SubscriptionParser.parse(body, "src")
        assertEquals("⚠️ - Конфиг без шифрования трафика.\n🏴 - Конфиг с неизвестным выходным трафиком.", s.announce)
    }

    @Test fun announceHeaderWinsOverBody() {
        val s = SubscriptionParser.parse("#announce: из тела\n$vless", "src", mapOf("Announce" to "из заголовка"))
        assertEquals("из заголовка", s.announce)
    }

    @Test fun xrayConfigProtectsLocalProxyAndDns() {
        val cfg = XrayConfigBuilder.build(LinkParser.parse(vless)!!, directRuServices = false)
        assertTrue(cfg.contains("\"auth\":\"password\""))
        assertTrue(cfg.contains(app.farvater.core.xray.LocalProxyAuth.pass))
        assertTrue(cfg.contains("https://1.1.1.1/dns-query"))
        assertTrue(cfg.contains("\"dns-out\""))
        val plain = XrayConfigBuilder.build(LinkParser.parse(vless)!!, directRuServices = false, encryptedDns = false)
        assertTrue(!plain.contains("dns-out"))
    }

    @Test fun insecureNodesAreRecognised() {
        assertEquals("Без шифрования", LinkParser.parse("vless://id@1.2.3.4:443?security=none#x")!!.securityIssue)
        assertEquals("Без проверки сертификата", LinkParser.parse("trojan://p@1.2.3.4:443?allowInsecure=1#x")!!.securityIssue)
        val weak = Base64.getEncoder().encodeToString("rc4-md5:pw".toByteArray())
        assertEquals("Устаревшее шифрование", LinkParser.parse("ss://$weak@1.2.3.4:8388#x")!!.securityIssue)
        assertNull(LinkParser.parse(vless)!!.securityIssue)
        assertNull(LinkParser.parse("hysteria2://p@hy.example.com:443/?insecure=1&pinSHA256=AB:CD#x")!!.securityIssue)
    }

    @Test fun hysteria2Links() {
        val n = LinkParser.parse("hysteria2://pa%40ss@hy.example.com:443,20000-30000/?sni=hy.example.com#hy")!!
        assertEquals(Protocol.HYSTERIA2, n.protocol)
        assertEquals("hy.example.com", n.address)
        assertEquals(443, n.port)
        assertEquals("pa@ss", n.secret)
        assertEquals("443,20000-30000", n.params["mport"])
        assertEquals(443, LinkParser.parse("hy2://p@hy.example.com/?sni=x#n")!!.port)
        assertEquals(20000, LinkParser.parse("hy2://p@[2001:db8::1]:20000-30000/#n")!!.port)
    }

    @Test fun hysteria2Config() {
        val link = "hysteria2://pass@hy.example.com:443,20000-30000/?sni=hy.example.com&obfs=salamander&obfs-password=ob&pinSHA256=AB:CD#hy"
        val cfg = Json.parseToJsonElement(XrayConfigBuilder.build(LinkParser.parse(link)!!, directRuServices = false)).jsonObject
        val out = cfg["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("hysteria", out["protocol"]!!.jsonPrimitive.content)
        val stream = out["streamSettings"]!!.jsonObject
        assertEquals("hysteria", stream["network"]!!.jsonPrimitive.content)
        assertEquals("pass", stream["hysteriaSettings"]!!.jsonObject["auth"]!!.jsonPrimitive.content)
        assertEquals("AB:CD", stream["tlsSettings"]!!.jsonObject["pinnedPeerCertSha256"]!!.jsonPrimitive.content)
        val masks = stream["finalmask"]!!.jsonObject["udp"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(listOf("salamander", "udphop"), masks)
    }

    @Test fun insecureFlagNeverReachesXray() {
        val cfg = XrayConfigBuilder.build(LinkParser.parse("trojan://p@1.2.3.4:443?allowInsecure=1#x")!!, directRuServices = false)
        assertTrue("allowInsecure" !in cfg)
    }

    @Test fun unknownSchemesAreCounted() {
        val s = SubscriptionParser.parse("$vless\ntuic://a@1.2.3.4:443#t\ntuic://b@1.2.3.5:443#t\nwireguard://x@1.2.3.6:51820", "src")
        assertEquals(1, s.nodes.size)
        assertEquals(mapOf("tuic" to 2, "wireguard" to 1), s.skipped)
    }

    @Test fun xrayConfigHasRealityAndXhttp() {
        val cfg = XrayConfigBuilder.build(LinkParser.parse(vless)!!, directRuServices = true)
        assertTrue(cfg.contains("\"realitySettings\""))
        assertTrue(cfg.contains("\"xhttpSettings\""))
        assertTrue(cfg.contains("domain:gosuslugi.ru"))
    }

    private val remnawaveJson = """[
        {"remarks":"Германия","outbounds":[
            {"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"de.example.com","port":443,"users":[{"id":"u1","encryption":"none","flow":"xtls-rprx-vision"}]}]},
             "streamSettings":{"network":"tcp","security":"reality","realitySettings":{"serverName":"ok.ru","publicKey":"pk","shortId":"ab","fingerprint":"chrome"}}},
            {"tag":"direct","protocol":"freedom"},{"tag":"block","protocol":"blackhole"}],
         "routing":{"rules":[{"type":"field","ip":["geoip:private"],"outboundTag":"direct"}]}},
        {"remarks":"Автовыбор","outbounds":[
            {"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"de.example.com","port":443,"users":[{"id":"u1","encryption":"none"}]}]},
             "streamSettings":{"network":"tcp","security":"reality","realitySettings":{"serverName":"ok.ru","publicKey":"pk","shortId":"ab"}}},
            {"tag":"proxy-2","protocol":"trojan","settings":{"servers":[{"address":"nl.example.com","port":443,"password":"p"}]},
             "streamSettings":{"network":"tcp","security":"tls","tlsSettings":{"serverName":"nl.example.com","allowInsecure":true}}},
            {"tag":"direct","protocol":"freedom"}],
         "routing":{"balancers":[{"tag":"auto","selector":["proxy"],"strategy":{"type":"leastPing"}}],
                    "rules":[{"type":"field","network":"tcp,udp","balancerTag":"auto"}]},
         "observatory":{"subjectSelector":["proxy"],"probeUrl":"https://www.gstatic.com/generate_204"}},
        {"remarks":"Обход","outbounds":[
            {"tag":"proxy","protocol":"trojan","settings":{"servers":[{"address":"de.example.com","port":8443,"password":"p"}]},
             "streamSettings":{"network":"tcp","security":"tls","sockopt":{"dialerProxy":"relay"}}},
            {"tag":"relay","protocol":"vless","settings":{"vnext":[{"address":"95.163.1.2","port":443,"users":[{"id":"u1","encryption":"none"}]}]},
             "streamSettings":{"network":"tcp","security":"reality","realitySettings":{"serverName":"ok.ru","publicKey":"pk","shortId":"ab"}}}]},
        {"remarks":"Подписка истекла","outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"0.0.0.0","port":1,"users":[{"id":"x"}]}]}}]}
    ]"""

    private fun tags(cfg: String) =
        Json.parseToJsonElement(cfg).jsonObject["outbounds"]!!.jsonArray.map { it.jsonObject["tag"]!!.jsonPrimitive.content }

    @Test fun xrayJsonSubscription() {
        val s = SubscriptionParser.parse(remnawaveJson, "src", mapOf("profile-title" to "NoName"))
        assertEquals("NoName", s.title)
        assertEquals(listOf("Германия", "Автовыбор", "Обход"), s.nodes.map { it.name })
        assertEquals(listOf("Подписка истекла"), s.notices)
        val de = s.nodes[0]
        assertEquals(Protocol.VLESS, de.protocol)
        assertEquals("de.example.com", de.address)
        assertEquals(443, de.port)
        assertEquals("ok.ru", de.params["sni"])
        assertEquals(listOf("VLESS", "Reality"), de.tags)
        assertNull(de.securityIssue)
        assertEquals(3, s.nodes.map { it.id }.toSet().size)
        assertEquals(s.nodes, s.nodes.onePerEndpoint(null))
    }

    @Test fun xrayJsonBalancerAndChainReachXray() {
        val nodes = SubscriptionParser.parse(remnawaveJson, "src").nodes
        val auto = XrayConfigBuilder.build(nodes[1], directRuServices = true)
        assertEquals(listOf("proxy", "proxy-2", "direct", "block", "dns-out"), tags(auto))
        val routing = Json.parseToJsonElement(auto).jsonObject["routing"]!!.jsonObject
        assertEquals("auto", routing["rules"]!!.jsonArray.last().jsonObject["balancerTag"]!!.jsonPrimitive.content)
        val selector = routing["balancers"]!!.jsonArray[0].jsonObject["selector"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("proxy", "proxy-2"), selector)
        assertTrue(auto.contains("\"observatory\""))
        assertTrue("allowInsecure" !in auto)
        assertEquals(listOf("proxy", "relay", "direct", "block", "dns-out"), tags(XrayConfigBuilder.build(nodes[2], directRuServices = false)))
        assertTrue("routing" !in XrayConfigBuilder.build(nodes[2], directRuServices = false, forTest = true))
    }

    @Test fun xrayJsonBalancerNeverPicksDirectOutbound() {
        val json = """[{"remarks":"a","outbounds":[
            {"tag":"p","protocol":"trojan","settings":{"servers":[{"address":"a.example.com","port":443,"password":"x"}]},"streamSettings":{"security":"tls"}},
            {"tag":"p-free","protocol":"freedom"}],
            "routing":{"balancers":[{"tag":"b","selector":["p"]}],"rules":[{"type":"field","network":"tcp,udp","balancerTag":"b"}]}}]"""
        val node = SubscriptionParser.parse(json, "src").nodes.single()
        assertTrue("balancerTag" !in XrayConfigBuilder.build(node, directRuServices = false))
    }

    @Test fun xrayJsonNodesSurviveManualFile() {
        val nodes = SubscriptionParser.parse(remnawaveJson, "src").nodes
        val manual = SubscriptionParser.parse(nodes.joinToString("\n") { it.raw } + "\n" + vless, "manual").nodes
        assertEquals(nodes.map { it.id } + LinkParser.parse(vless)!!.id, manual.map { it.id })
        assertEquals(nodes.map { it.name }, manual.take(3).map { it.name })
    }
}

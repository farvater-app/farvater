package app.farvater

import app.farvater.core.parser.LinkParser
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.data.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RoutingPolicyTest {
    @Test fun validatesWithoutResolvingAndNormalizesDomainsAndNetworks() {
        assertEquals("xn--e1afmkfd.xn--p1ai", RouteRule.parse("Пример.рф.", RouteAction.BLOCK)?.target)
        assertEquals("2001:db8::/32", RouteRule.parse("2001:db8::/32", RouteAction.VPN)?.target)
        for (bad in listOf("https://example.test/","127.1","300.0.0.1","*","host:80","[::1]#x","fd00::1%wlan0","10.0.0.1/33","example.test/16"))
            assertNull(bad, RouteRule.parse(bad, RouteAction.DIRECT))
    }

    @Test fun rejectedProviderBalancerKeepsExplicitVpnAheadOfDirect() {
        val raw = """{"outbounds":[{"tag":"di","protocol":"trojan","settings":{"servers":[{"address":"vpn.example.test","port":443,"password":"test"}]},"streamSettings":{"security":"tls"}}],"routing":{"balancers":[{"tag":"pool","selector":["di"]}],"rules":[{"balancerTag":"pool"}]}}"""
        val node = app.farvater.core.parser.XrayJsonParser.parse(raw, "manual")!!.single()
        val routes = listOf(RouteRule("private.example.test", RouteAction.VPN), RouteRule("example.test", RouteAction.DIRECT))
        val cfg = Json.parseToJsonElement(XrayConfigBuilder.build(node, false, encryptedDns = false, routeRules = routes)).jsonObject
        val rules = cfg.getValue("routing").jsonObject.getValue("rules").jsonArray
        assertEquals("di", rules[0].jsonObject["outboundTag"]?.jsonPrimitive?.content)
        assertEquals("direct", rules[1].jsonObject["outboundTag"]?.jsonPrimitive?.content)
    }

    @Test fun customRulesPrecedePresetsAndNeverMatchTheHealthProbe() {
        val rules = listOf(RouteRule("sub.example.test", RouteAction.VPN), RouteRule("example.test", RouteAction.BLOCK), RouteRule("192.0.2.0/24", RouteAction.DIRECT))
        val node = LinkParser.parse("vless://test@node.example.test:443?security=tls")!!
        val config = Json.parseToJsonElement(XrayConfigBuilder.build(node, true, routeRules = rules, probePort = 19001)).jsonObject
        val items = config.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
        assertEquals("dns-out", items[0]["outboundTag"]?.jsonPrimitive?.content)
        assertEquals(listOf("proxy", "block", "direct"), items.slice(1..3).map { it.getValue("outboundTag").jsonPrimitive.content })
        assertTrue(items.slice(1..3).all { it.getValue("inboundTag").jsonArray.single().jsonPrimitive.content == "socks" })
        assertEquals("domain:example.test", items[2].getValue("domain").jsonArray.single().jsonPrimitive.content)
        assertEquals("192.0.2.0/24", items[3].getValue("ip").jsonArray.single().jsonPrimitive.content)
    }
}

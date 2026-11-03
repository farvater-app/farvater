package app.farvater

import app.farvater.core.parser.LinkParser
import app.farvater.core.parser.XrayJsonParser
import app.farvater.core.xray.LocalProxyAuth
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.engine.ServiceProbe
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SocksBindingTest {
    @Test fun importedListenersCannotOverrideApplicationSocks() {
        for (listen in listOf("0.0.0.0", "::", "192.0.2.10", "127.0.0.2")) {
            val raw = """{"inbounds":[{"listen":"$listen","port":9999,"protocol":"socks","settings":{"auth":"noauth"}},{"listen":"$listen","port":9998,"protocol":"http"}],"outbounds":[{"protocol":"vless","settings":{"address":"node.example.test","port":443,"id":"id","encryption":"none"},"streamSettings":{"security":"tls"}}]}"""
            val parsed = XrayJsonParser.parse(raw, "manual")!!.single()
            for (node in listOf(parsed, parsed.copy(raw = raw))) {
                verify(XrayConfigBuilder.build(node, false), XrayConfigBuilder.SOCKS_PORT)
                verify(ServiceProbe.configuration(node, null, 12345), 12345)
                assertNull(Json.parseToJsonElement(XrayConfigBuilder.build(node, false, forTest = true)).jsonObject["inbounds"])
            }
        }
    }

    @Test fun uriListenersAreAlsoLocalAndAuthenticated() {
        val node = LinkParser.parse("vless://id@node.example.test:443?security=tls&listen=0.0.0.0&port=9999")!!
        verify(XrayConfigBuilder.build(node, true), XrayConfigBuilder.SOCKS_PORT)
    }

    private fun verify(config: String, port: Int) {
        val inbound = Json.parseToJsonElement(config).jsonObject.getValue("inbounds").jsonArray.single().jsonObject
        assertEquals("127.0.0.1", inbound.getValue("listen").jsonPrimitive.content)
        assertEquals("socks", inbound.getValue("protocol").jsonPrimitive.content)
        assertEquals(port, inbound.getValue("port").jsonPrimitive.int)
        val settings = inbound.getValue("settings").jsonObject
        assertEquals("password", settings.getValue("auth").jsonPrimitive.content)
        val account = settings.getValue("accounts").jsonArray.single().jsonObject
        assertEquals(LocalProxyAuth.user, account.getValue("user").jsonPrimitive.content)
        assertEquals(LocalProxyAuth.pass, account.getValue("pass").jsonPrimitive.content)
        assertEquals(port == XrayConfigBuilder.SOCKS_PORT, settings.getValue("udp").jsonPrimitive.boolean)
    }
}

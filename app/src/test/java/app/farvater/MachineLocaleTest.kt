package app.farvater

import app.farvater.core.model.NodeProfile
import app.farvater.core.parser.LinkParser
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.engine.TunBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.Locale

class MachineLocaleTest {
    @Test fun persianLocaleKeepsConfigsAndIdsAscii() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ROOT)
            val expected = configs()
            val expectedIds = links().map { LinkParser.parse(it)!!.id }
            Locale.setDefault(Locale.forLanguageTag("fa-IR"))
            val actual = configs()
            assertEquals(expected, actual)
            assertEquals(expectedIds, links().map { LinkParser.parse(it)!!.id })
            actual.forEach { config ->
                assertFalse("Машинный конфиг содержит локализованную цифру", config.any { it.isDigit() && it !in '0'..'9' })
            }
            val hev = TunBridge.buildConfig(8500)
            assertTrue(hev.contains("mtu: 8500"))
            assertTrue(hev.contains("port: ${XrayConfigBuilder.SOCKS_PORT}"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun configs(): List<String> = buildList {
        for (link in links()) {
            val node = LinkParser.parse(link)!!
            for (test in listOf(false, true)) for (direct in listOf(false, true)) {
                add(XrayConfigBuilder.build(node, directRuServices = direct, forTest = test))
                add(XrayConfigBuilder.build(node, directRuServices = direct, forTest = test,
                    encryptedDns = false, profile = NodeProfile(mux = true, fragment = true)))
            }
        }
        add(TunBridge.buildConfig(1280))
        add(TunBridge.buildConfig(8500))
    }

    private fun links(): List<String> = listOf(
        "vless://11111111-2222-3333-4444-555555555555@node.example.test:443?security=reality&sni=example.test&pbk=AbC-dEf_123&sid=ab12&type=xhttp&path=%2Fapi#Test1",
        "vless://11111111-2222-3333-4444-555555555555@node.example.test:8443?security=tls&sni=example.test&type=ws#Test2",
        "trojan://test-password@[2001:db8::1]:8443?sni=example.test#Test3",
        "ss://" + Base64.getEncoder().encodeToString("aes-128-gcm:test-password".toByteArray()) + "@node.example.test:8388#Test4",
        "hysteria2://test-password@node.example.test:443?sni=example.test#Test5",
        "vmess://" + Base64.getEncoder().encodeToString(
            """{"v":"2","ps":"Test6","add":"node.example.test","port":"8443","id":"11111111-2222-3333-4444-555555555555","net":"grpc","tls":"tls","sni":"example.test"}""".toByteArray(),
        ),
    )
}

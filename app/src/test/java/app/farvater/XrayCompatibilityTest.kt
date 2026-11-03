package app.farvater

import app.farvater.core.model.NodeProfile
import app.farvater.core.parser.LinkParser
import app.farvater.core.parser.XrayJsonParser
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.engine.ServiceProbe
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class XrayCompatibilityTest {
    @Test
    fun generatedConfigurationsAreAcceptedByRealCore() {
        val binary = System.getenv("FARVATER_XRAY_BINARY")
        if (System.getenv("CI") == "true") assertFalse("CI requires pinned Xray", binary.isNullOrBlank())
        assumeTrue("Requires locally verified Xray binary", !binary.isNullOrBlank())
        val version = ProcessBuilder(binary!!, "version").redirectErrorStream(true).start()
        assertTrue(version.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, version.exitValue())
        assertTrue(version.inputStream.bufferedReader().readText().startsWith("Xray 26.10.31 "))
        val directory = File(System.getenv("FARVATER_XRAY_CONFIG_DIR"))
        directory.mkdirs()
        val uuid = "00000000-0000-0000-0000-000000000001"
        val links = linkedMapOf(
            "vless-encryption" to "vless://$uuid@node.example.test:443?encryption=mlkem768x25519plus.native.0rtt.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "vless-tls" to "vless://$uuid@node.example.test:443?security=tls",
            "vless-reality-empty-sid" to "vless://$uuid@node.example.test:443?security=reality&sni=example.test&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "vless-xhttp" to "vless://$uuid@node.example.test:443?security=tls&type=xhttp&path=%2Ftest&mode=auto",
            "vless-ws" to "vless://$uuid@node.example.test:443?security=tls&type=ws&path=%2Ftest",
            "vless-grpc" to "vless://$uuid@node.example.test:443?security=tls&type=grpc&serviceName=test",
            "vless-httpupgrade" to "vless://$uuid@node.example.test:443?security=tls&type=httpupgrade&path=%2Ftest",
            "trojan" to "trojan://example-password@node.example.test:443?security=tls",
            "ss" to "ss://chacha20-ietf-poly1305:example-password@node.example.test:443",
            "hysteria2" to "hysteria2://example-password@node.example.test:443?obfs=salamander&obfs-password=example-obfs&sni=example.test",
        )
        val vmess = """{"v":"2","add":"node.example.test","port":"443","id":"$uuid","aid":"0","net":"tcp","tls":"tls","scy":"auto"}"""
        links["vmess"] = "vmess://" + java.util.Base64.getEncoder().encodeToString(vmess.toByteArray())
        links["ss2022"] = "ss://2022-blake3-aes-128-gcm:AAAAAAAAAAAAAAAAAAAAAA%3D%3D@node.example.test:443"
        val configurations = linkedMapOf<String, String>()
        links.forEach { (name, link) ->
            val node = requireNotNull(LinkParser.parse(link)) { name }
            configurations[name] = XrayConfigBuilder.build(node, false)
            configurations["$name-probe"] = ServiceProbe.configuration(node, null, XrayConfigBuilder.SOCKS_PORT)
        }
        val jsonInputs = mapOf(
            "ss-direct-json" to """{"outbounds":[{"tag":"proxy","protocol":"shadowsocks","settings":{"address":"node.example.test","port":443,"method":"chacha20-poly1305","password":"example-password"}}]}""",
            "reality-json" to """{"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"node.example.test","port":443,"users":[{"id":"$uuid","encryption":"none"}]}]},"streamSettings":{"security":"reality","realitySettings":{"serverName":"example.test","publicKey":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","shortId":"","fingerprint":"chrome"}}}]}""",
        )
        jsonInputs.forEach { (name, input) ->
            val node = requireNotNull(XrayJsonParser.parse(input, "manual")).single()
            configurations[name] = XrayConfigBuilder.build(node, false)
            configurations["$name-probe"] = ServiceProbe.configuration(node, null, XrayConfigBuilder.SOCKS_PORT)
        }
        val vless = requireNotNull(LinkParser.parse(links.getValue("vless-tls")))
        configurations["vless-fragment"] = XrayConfigBuilder.build(vless, false, profile = NodeProfile(fragment = true))
        configurations["vless-mux"] = XrayConfigBuilder.build(vless, true, profile = NodeProfile(mux = true))
        for ((name, config) in configurations) {
            val file = File(directory, "$name.json").apply { writeText(config) }
            val log = File(directory, "$name.log")
            val process = ProcessBuilder(binary!!, "run", "-test", "-config", file.absolutePath)
                .redirectErrorStream(true).redirectOutput(log).start()
            if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Xray timeout: $name") }
            assertEquals("$name: ${log.readText()}", 0, process.exitValue())
        }
    }
}

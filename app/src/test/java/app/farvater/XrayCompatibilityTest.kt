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
    fun activeProbeCannotReportADirectResponseWhenTunnelFails() {
        val binary = System.getenv("FARVATER_XRAY_BINARY")
        assumeTrue("Requires locally verified Xray binary", !binary.isNullOrBlank())
        val loopback = java.net.InetAddress.getByName("127.0.0.1")
        val http = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(loopback, 0), 1)
        val hits = java.util.concurrent.atomic.AtomicInteger()
        http.createContext("/") { exchange ->
            hits.incrementAndGet()
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        http.start()
        val configFile = File.createTempFile("farvater-routing-", ".json")
        try {
            java.net.ServerSocket(0, 1, loopback).use { offlineNode ->
                val attempted = java.util.concurrent.CountDownLatch(1)
                kotlin.concurrent.thread(isDaemon = true) {
                    runCatching { offlineNode.accept().use { attempted.countDown() } }
                }
                val node = LinkParser.parse("vless://00000000-0000-0000-0000-000000000001@127.0.0.1:${offlineNode.localPort}?encryption=none")!!
                val auth = app.farvater.core.xray.LocalProxyAuth
                val probeAddress = app.farvater.engine.NodeTester.tunnelClient.proxy!!.address() as java.net.InetSocketAddress
                assertEquals(auth.probePort, probeAddress.port)
                configFile.writeText(XrayConfigBuilder.build(node, true, encryptedDns = false, probePort = auth.probePort,
                    automaticRoutes = false, routeRules = listOf(
                        app.farvater.data.RouteRule("localhost", app.farvater.data.RouteAction.BLOCK),
                        app.farvater.data.RouteRule("127.0.0.1", app.farvater.data.RouteAction.DIRECT),
                        app.farvater.data.RouteRule("127.0.0.0/8", app.farvater.data.RouteAction.VPN))))
                val xray = ProcessBuilder(binary!!, "run", "-config", configFile.absolutePath)
                    .redirectErrorStream(true).redirectOutput(File("/dev/null")).start()
                try {
                    fun request(port: Int, host: String = "127.0.0.1"): Int {
                        val curl = ProcessBuilder("curl", "--silent", "--noproxy", "", "--max-time", "3", "--output", "/dev/null",
                            "--write-out", "%{http_code}", "--proxy", "socks5h://127.0.0.1:$port",
                            "--proxy-user", "${auth.user}:${auth.pass}", "http://$host:${http.address.port}/").start()
                        try {
                            assertTrue(curl.waitFor(5, TimeUnit.SECONDS))
                            return curl.inputStream.bufferedReader().readText().toIntOrNull() ?: 0
                        } finally { curl.destroyForcibly() }
                    }
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    var status = request(auth.port)
                    while (status == 0 && xray.isAlive && System.nanoTime() < deadline) {
                        Thread.sleep(30)
                        status = request(auth.port)
                    }
                    assertEquals(204, status)
                    assertEquals(0, request(auth.port, "localhost"))
                    assertEquals(0, request(auth.probePort))
                    assertTrue(attempted.await(2, TimeUnit.SECONDS))
                    assertEquals(1, hits.get())
                } finally {
                    xray.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
                }
            }
        } finally {
            http.stop(0)
            configFile.delete()
        }
    }

    @Test
    fun generatedConfigurationsAreAcceptedByRealCore() {
        val binary = System.getenv("FARVATER_XRAY_BINARY")
        if (System.getenv("CI") == "true") assertFalse("CI requires pinned Xray", binary.isNullOrBlank())
        assumeTrue("Requires locally verified Xray binary", !binary.isNullOrBlank())
        val version = ProcessBuilder(binary!!, "version").redirectErrorStream(true).start()
        assertTrue(version.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, version.exitValue())
        assertTrue(version.inputStream.bufferedReader().readText().startsWith("Xray 26.11.9 "))
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

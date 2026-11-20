package app.farvater

import app.farvater.data.GalsGroup
import app.farvater.data.GalsStrategy
import app.farvater.data.ProbeUrls
import app.farvater.data.ServiceKind
import app.farvater.data.ServiceVerdict
import app.farvater.engine.GalsProbe
import app.farvater.engine.DohProbe
import app.farvater.core.model.NodeProfile
import app.farvater.core.parser.LinkParser
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class GalsProbeTest {
    private val custom = GalsGroup("target", "Target", target = ServiceKind.CUSTOM,
        customUrl = "probe.example.test/check", expectedCode = 204)

    @Test fun latencyUsesOnlyTheNormalizedSelectedTarget() = runBlocking {
        val urls = mutableListOf<String>()
        val client = client { url -> urls += url; 204 to "" }
        val measured = GalsProbe.measure(client, custom, ProbeUrls.GOOGLE, null)
        assertTrue(measured.usable)
        assertTrue(measured.delayMs > 0)
        assertEquals(0L, measured.speedBps)
        assertEquals(ServiceVerdict.RESPONDED, measured.target?.verdict)
        assertTrue(urls.isNotEmpty())
        assertTrue(urls.all { it == "https://probe.example.test/check" })
    }

    @Test fun ipTargetWithoutDnsDoesNotDependOnAnUnusedOrdinaryProbeUrl() = runBlocking {
        val result = GalsProbe.measure(client { 204 to "" },
            custom.copy(customUrl = "https://8.8.8.8/check"), "unused", null)
        assertTrue(result.usable)
    }

    @Test fun liveProbeUsesTheExistingConnectionAndRejectsAChangedSession() = runBlocking {
        val node = LinkParser.parse("vless://test@vpn.example.test:443?security=tls", "manual")!!
        val profile = NodeProfile(fingerprint = "firefox")
        var requests = 0
        val client = client { requests++; 204 to "" }
        try {
            VpnBus.state.value = VpnState.Connected(node, 1)
            val success = GalsProbe.liveWithClient(node, profile, custom, ProbeUrls.GOOGLE, client, checkDns = false)
            assertEquals(profile, success.profile)
            assertTrue(success.delayMs > 0)
            assertEquals(2, requests)
            val changed = GalsProbe.liveWithClient(node, profile, custom, ProbeUrls.GOOGLE, client {
                VpnBus.state.value = VpnState.Connected(node, 2)
                204 to ""
            })
            assertNull(changed.profile)
            assertTrue(changed.deferred)
        } finally { VpnBus.state.value = VpnState.Idle }
    }

    @Test fun liveProbeDoesNotStartAnIndependentConnectionWhenTheTunnelIsMissing() = runBlocking {
        val node = LinkParser.parse("vless://test@vpn.example.test:443?security=tls", "manual")!!
        VpnBus.state.value = VpnState.Idle
        val result = GalsProbe.liveWithClient(node, null, custom, ProbeUrls.GOOGLE,
            client { fail("A missing tunnel must not send a request"); 204 to "" })
        assertNull(result.profile)
        assertTrue(result.deferred)
    }

    @Test fun liveTargetResponseCannotHideBrokenEncryptedDns() = runBlocking {
        val node = LinkParser.parse("vless://test@vpn.example.test:443?security=tls", "manual")!!
        try {
            VpnBus.state.value = VpnState.Connected(node, 1)
            val result = GalsProbe.liveWithClient(node, null, custom, ProbeUrls.GOOGLE,
                client { url -> if (url.startsWith("https://probe.example.test/")) 204 to "" else 503 to "" })
            assertNull(result.profile)
            assertTrue(result.dnsBlocked)
        } finally { VpnBus.state.value = VpnState.Idle }
    }

    @Test fun rejectedTargetNeverTriggersSpeedDownloads() = runBlocking {
        val urls = mutableListOf<String>()
        val measured = GalsProbe.measure(client { url -> urls += url; 403 to "" },
            custom.copy(strategy = GalsStrategy.THROUGHPUT), ProbeUrls.GOOGLE, null)
        assertFalse(measured.usable)
        assertEquals(ServiceVerdict.REJECTED, measured.target?.verdict)
        assertTrue(urls.isNotEmpty())
        assertTrue(urls.all { it == "https://probe.example.test/check" })
    }

    @Test fun throughputMeasuresSpeedOnlyAfterTargetAcceptsConnection() = runBlocking {
        val urls = mutableListOf<String>()
        val client = client { url ->
            urls += url
            if (url.startsWith("https://probe.example.test/")) 204 to "" else 200 to "x".repeat(256 * 1024)
        }
        val measured = GalsProbe.measure(client, custom.copy(strategy = GalsStrategy.THROUGHPUT), ProbeUrls.GOOGLE, null)
        assertTrue(measured.usable)
        assertTrue(measured.speedBps > 0)
        assertEquals("https://probe.example.test/check", urls.first())
        assertTrue(urls.any { it.startsWith("https://speed.cloudflare.com/") })
        assertFalse(urls.any { it == ProbeUrls.GOOGLE || "telegram" in it })
    }

    @Test fun unknownServicePageCannotPassAsAvailable() = runBlocking {
        val urls = mutableListOf<String>()
        val measured = GalsProbe.measure(client { url -> urls += url; 200 to "<title>Access check</title>" },
            custom.copy(target = ServiceKind.CHATGPT), ProbeUrls.GOOGLE, null)
        assertFalse(measured.usable)
        assertEquals(ServiceVerdict.UNKNOWN, measured.target?.verdict)
        assertTrue(urls.all { it == "https://chatgpt.com/" })
    }

    @Test fun ordinaryLatencyHasNoTelegramOrThroughputRequirement() = runBlocking {
        val urls = mutableListOf<String>()
        val measured = GalsProbe.measure(client { url -> urls += url; 204 to "" },
            GalsGroup("ordinary", "Ordinary"), ProbeUrls.CLOUDFLARE, null)
        assertTrue(measured.usable)
        assertNull(measured.target)
        assertEquals(0L, measured.speedBps)
        assertEquals(listOf(ProbeUrls.CLOUDFLARE, ProbeUrls.CLOUDFLARE), urls)
    }

    @Test fun invalidGroupDoesNotSendRequests() = runBlocking {
        var requests = 0
        val measured = GalsProbe.measure(client { requests++; 204 to "" },
            custom.copy(customUrl = "https://127.0.0.1/"), ProbeUrls.GOOGLE, null)
        assertFalse(measured.usable)
        assertEquals(0, requests)
    }

    @Test fun encryptedDnsChecksTheChosenHostInsteadOfTelegram() = runBlocking {
        assertDnsTarget(custom, ProbeUrls.GOOGLE, "probe.example.test")
    }

    @Test fun literalIpTargetUsesTheConfiguredOrdinaryProbeForDns() = runBlocking {
        assertDnsTarget(custom.copy(customUrl = "https://198.51.100.1/check"), ProbeUrls.CLOUDFLARE, "cp.cloudflare.com")
    }

    private suspend fun assertDnsTarget(group: GalsGroup, probeUrl: String, name: String) {
        val questions = mutableListOf<ByteArray>()
        val expected = DohProbe.wire(name)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val query = chain.request().url.queryParameter("dns")
            val body = if (query == null) ByteArray(0) else {
                val wire = java.util.Base64.getUrlDecoder().decode(query)
                questions += wire
                val matches = wire.contentEquals(expected)
                val reply = wire.copyOf().apply {
                    this[2] = 0x81.toByte()
                    this[3] = (if (matches) 0x80 else 0x83).toByte()
                    this[7] = (if (matches) 1 else 0).toByte()
                }
                if (!matches) reply else reply + byteArrayOf(0xc0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 203.toByte(), 0, 113, 1)
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (query == null) 204 else 200)
                .message("test").body(body.toResponseBody()).build()
        }.build()
        val measured = GalsProbe.measure(client, group, probeUrl, listOf("google"))
        assertTrue(measured.usable)
        assertEquals("google", measured.dns)
        assertEquals(1, questions.size)
        assertArrayEquals(expected, questions.single())
    }

    private fun client(answer: (String) -> Pair<Int, String>) = OkHttpClient.Builder().addInterceptor { chain ->
        val (code, body) = answer(chain.request().url.toString())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body(body.toResponseBody("text/html".toMediaType())).build()
    }.build()
}

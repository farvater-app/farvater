package app.farvater

import app.farvater.core.parser.LinkParser
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.data.AppSettings
import app.farvater.data.ServiceCheck
import app.farvater.data.ServiceChecks
import app.farvater.data.ServiceKind
import app.farvater.data.ServiceReason
import app.farvater.data.ServiceTarget
import app.farvater.data.ServiceVerdict
import app.farvater.engine.ServiceProbe
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ServiceProbeTest {
    @Test
    fun disabledByDefaultAndInvalidCustomAddressesNeverBecomeTargets() {
        assertTrue(ServiceChecks.targets(AppSettings()).isEmpty())
        for (url in listOf("http://example.test/", "https://localhost/", "https://127.0.0.1/", "https://10.0.0.1/",
            "https://[::1]/", "https://[fc00::1]/", "https://router.lan/", "https://user:secret@example.test/", "https://example.test/#secret")) {
            assertNull(url, ServiceChecks.customUrl(url))
            assertTrue(ServiceChecks.targets(AppSettings(serviceChecksEnabled = true, checkedServices = setOf(ServiceKind.CUSTOM), serviceCustomUrl = url)).isEmpty())
        }
        assertNotNull(ServiceChecks.customUrl("https://service.example.test/status"))
    }

    @Test
    fun captchaErrorsAndUnknownHtmlAreNotSuccesses() = runBlocking {
        val target = ServiceChecks.builtIn.first { it.kind == ServiceKind.CHATGPT }
        assertEquals(ServiceVerdict.RESPONDED, ServiceProbe.request(client(200, "<title>ChatGPT</title>"), target).verdict)
        assertEquals(ServiceVerdict.UNKNOWN, ServiceProbe.request(client(200, "<title>ChatGPT</title>", challenge = true), target).verdict)
        assertEquals(ServiceVerdict.UNKNOWN, ServiceProbe.request(client(200, "<title>Access denied | ChatGPT</title>"), target).verdict)
        assertEquals(ServiceVerdict.UNKNOWN, ServiceProbe.request(client(429, ""), target).verdict)
        assertEquals(ServiceVerdict.UNKNOWN, ServiceProbe.request(client(503, ""), target).verdict)
        val forbidden = ServiceProbe.request(client(403, ""), target)
        assertEquals(ServiceVerdict.REJECTED, forbidden.verdict)
        assertEquals(ServiceReason.HTTP_ERROR, forbidden.reason)
    }

    @Test
    fun onlyExplicitOfficialClaudeRedirectMeansRegionRejection() = runBlocking {
        val target = ServiceChecks.builtIn.first { it.kind == ServiceKind.CLAUDE }
        val official = ServiceProbe.request(client(302, "", location = "https://claude.com/app-unavailable-in-region"), target)
        assertEquals(ServiceReason.REGION, official.reason)
        assertEquals(ServiceVerdict.REJECTED, official.verdict)
        for (location in listOf("https://evil.example.test/app-unavailable-in-region", "http://claude.com/app-unavailable-in-region")) {
            assertEquals(ServiceVerdict.UNKNOWN, ServiceProbe.request(client(302, "", location), target).verdict)
        }
        assertEquals(ServiceVerdict.RESPONDED, ServiceProbe.request(client(200, "<title>Claude</title><script>unavailable-in-region</script>"), target).verdict)
    }

    @Test
    fun customTestMatchesExactStatusAndDoesNotFollowRedirect() = runBlocking {
        var calls = 0
        val client = client(302, "", "https://other.example.test/") { calls++ }
        val target = ServiceTarget(ServiceKind.CUSTOM, "https://example.test/", 302)
        assertEquals(ServiceVerdict.RESPONDED, ServiceProbe.request(client, target).verdict)
        assertEquals(1, calls)
        assertEquals(ServiceVerdict.REJECTED, ServiceProbe.request(client, target.copy(expectedCode = 200)).verdict)
    }

    @Test
    fun cacheExpiresAndChangingUrlOrCodeInvalidatesItsKey() {
        val target = ServiceTarget(ServiceKind.CUSTOM, "https://example.test/", 200)
        val checked = ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.STATUS, 10_000)
        assertFalse(ServiceChecks.fresh(checked, 10_000 + ServiceChecks.TTL_MS))
        assertFalse(ServiceChecks.fresh(checked, 9_999))
        assertFalse(ServiceChecks.due(checked, 10_000 + ServiceChecks.RETRY_MS - 1))
        assertTrue(ServiceChecks.due(checked, 10_000 + ServiceChecks.RETRY_MS))
        assertNotEquals(target.key, target.copy(expectedCode = 204).key)
        assertNotEquals(target.key, target.copy(url = "https://example.test/other").key)
        assertNotEquals(ServiceChecks.key("WHITE", "node", target), ServiceChecks.key("BLACK", "node", target))
    }

    @Test
    fun probeConfigDoesNotBypassPrivateDestinationsOrLogUrls() {
        val node = LinkParser.parse("vless://00000000-0000-0000-0000-000000000000@node.example.test:443?security=tls")!!
        val config = ServiceProbe.configuration(node, null, XrayConfigBuilder.SOCKS_PORT)
        assertFalse(config.contains("\"outboundTag\":\"direct\""))
        assertTrue(config.contains("\"loglevel\":\"none\""))
        assertTrue(config.contains("\"auth\":\"password\""))
    }

    @Test
    fun completedResponseSurvivesTimeoutOfFollowingService() = runBlocking {
        val targets = ServiceChecks.builtIn.take(2)
        val saved = mutableMapOf<String, ServiceCheck>()
        kotlinx.coroutines.withTimeoutOrNull(100) {
            ServiceProbe.checkTargets(targets, { target ->
                if (target == targets.last()) kotlinx.coroutines.awaitCancellation()
                ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, System.currentTimeMillis())
            }, { target, result -> saved[target.key] = result })
        }
        assertEquals(ServiceVerdict.RESPONDED, saved[targets.first().key]?.verdict)
        assertFalse(saved.containsKey(targets.last().key))
    }

    private fun client(code: Int, html: String, location: String = "", challenge: Boolean = false, onCall: () -> Unit = {}) =
        OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            onCall()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .header("Location", location).header("cf-mitigated", if (challenge) "challenge" else "")
                .body(html.toResponseBody("text/html".toMediaType())).build()
        }.build()
}

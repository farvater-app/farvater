package app.farvater

import app.farvater.data.AppSettings
import app.farvater.data.ServiceChecks
import app.farvater.data.ServiceKind
import org.junit.Assert.*
import org.junit.Test

class ServiceChecksTest {
    @Test
    fun customAddressWithoutSchemeUsesHttpsAndPreservesPathAndQuery() {
        for ((input, expected) in listOf(
            "service.example.test" to "https://service.example.test/",
            "service.example.test/status?check=1" to "https://service.example.test/status?check=1",
            "service.example.test/status?next=https://other.example.test/" to "https://service.example.test/status?next=https://other.example.test/",
            " service.example.test:8443/status " to "https://service.example.test:8443/status",
            "HTTPS://service.example.test/status" to "https://service.example.test/status",
        )) {
            assertEquals(input, expected, ServiceChecks.customUrl(input)?.toString())
        }
    }

    @Test
    fun implicitHttpsDoesNotPermitLocalAddressesCredentialsOrOtherSchemes() {
        for (input in listOf(
            "", " ", "http://service.example.test/", "ftp://service.example.test/",
            "localhost", "127.0.0.1", "10.0.0.1", "192.168.0.1", "[::1]", "[fc00::1]",
            "router.local", "router.lan", "router.home.arpa", "user:secret@service.example.test",
            "service.example.test/#secret", "service.example.test/\npath", "\tservice.example.test",
            "//service.example.test", "\\service.example.test", "service.example.test/" + "x".repeat(2048),
        )) {
            assertNull(input, ServiceChecks.customUrl(input))
        }
    }

    @Test
    fun normalizedCustomTargetKeepsExpectedStatusAndRejectsInvalidStatuses() {
        val settings = AppSettings(serviceChecksEnabled = true, checkedServices = setOf(ServiceKind.CUSTOM),
            serviceCustomUrl = "service.example.test/status", serviceExpectedCode = 204)
        val target = ServiceChecks.targets(settings).single()
        assertEquals("https://service.example.test/status", target.url)
        assertEquals(204, target.expectedCode)
        assertNotEquals(target.key, ServiceChecks.targets(settings.copy(serviceExpectedCode = 200)).single().key)
        for (status in listOf(199, 600)) {
            assertTrue(ServiceChecks.targets(settings.copy(serviceExpectedCode = status)).isEmpty())
        }
    }
}

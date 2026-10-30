package app.farvater

import app.farvater.engine.Pathfinder
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class PathfinderHttpTest {
    @Test
    fun downloadUsesThirdHostWhenFirstTwoFail() = runBlocking {
        val hosts = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            hosts += chain.request().url.host
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (hosts.size < 3) 503 else 200).message("test")
                .body(if (hosts.size < 3) "".toResponseBody() else ByteArray(256 * 1024).toResponseBody()).build()
        }.build()
        assertNotNull(Pathfinder.measureSpeed(client))
        assertEquals(3, hosts.distinct().size)
    }

    @Test
    fun telegramRejectsHttpErrorsAndForeignRedirects() = runBlocking {
        for ((code, location) in listOf(403 to "", 429 to "", 500 to "", 302 to "https://other.example.test/")) {
            val client = client(code, location)
            assertFalse("HTTP $code $location", Pathfinder.reachable(client, "https://api.telegram.org/"))
        }
        assertTrue(Pathfinder.reachable(client(302, "https://core.telegram.org/bots"), "https://api.telegram.org/"))
    }

    private fun client(code: Int, location: String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .header("Location", location).body("".toResponseBody()).build()
    }.build()
}

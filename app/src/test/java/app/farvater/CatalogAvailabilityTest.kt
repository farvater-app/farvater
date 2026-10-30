package app.farvater

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.SubscriptionRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru")
class CatalogAvailabilityTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val valid = "vless://test@node.example.test:443?security=tls#Example"

    @Test fun failedPrimaryTriesMirrorAndClearsError() = runBlocking {
        val calls = mutableListOf<String>()
        val repo = repository { url ->
            calls += url
            if (url.contains("primary")) throw IOException("Unavailable")
            200 to valid
        }
        val result = repo.fetch("catalog-mirrors", listOf("https://primary.example.test/", "https://mirror.example.test/"), false)
        assertEquals(2, calls.size)
        assertEquals(1, result.nodes.size)
        assertNull(result.error)
        assertFalse(result.networkUnavailable)
    }

    @Test fun failedMirrorsKeepCachedNodesAndReportUnavailable() = runBlocking {
        var fail = false
        val repo = repository { if (fail) throw IOException("Unavailable") else 200 to valid }
        val urls = listOf("https://primary.example.test/", "https://mirror.example.test/")
        repo.fetch("catalog-cached", urls, false)
        fail = true
        val result = repo.fetch("catalog-cached", urls, false)
        assertEquals(1, result.nodes.size)
        assertNotNull(result.error)
        assertTrue(result.networkUnavailable)
    }

    @Test fun invalidContentIsNotMisreportedAsNetworkFailure() = runBlocking {
        val repo = repository { 200 to "<html>Not a subscription</html>" }
        val result = repo.fetch("catalog-html", listOf("https://primary.example.test/"), false)
        assertFalse(result.networkUnavailable)
        assertTrue(result.nodes.isEmpty())
        assertNotNull(result.error)
    }

    private fun repository(reply: (String) -> Pair<Int, String>): SubscriptionRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (status, body) = reply(chain.request().url.toString())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("test")
                .body(body.toResponseBody("text/plain".toMediaType())).build()
        }.build()
        return SubscriptionRepository(application).also { repo ->
            SubscriptionRepository::class.java.getDeclaredField("baseClient").apply { isAccessible = true }.set(repo, client)
        }
    }
}

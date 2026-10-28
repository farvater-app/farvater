package app.farvater

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.parser.LinkParser
import app.farvater.data.SubscriptionRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SubscriptionConcurrencyTest {
    @Test
    fun removalWaitsForImportTransactionAndPreservesNewNodes() {
        val repo = SubscriptionRepository(ApplicationProvider.getApplicationContext<Application>())
        val old = LinkParser.parse("vless://test@old.example.test:443?security=tls")!!
        val added = LinkParser.parse("vless://test@new.example.test:443?security=tls")!!
        repo.appendManual(listOf(old))
        val executor = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val completed = CountDownLatch(1)
        try {
            val removal = synchronized(repo) {
                val future = executor.submit {
                    entered.countDown()
                    repo.removeManual(old.id)
                    completed.countDown()
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertFalse(completed.await(200, TimeUnit.MILLISECONDS))
                repo.appendManual(listOf(added))
                future
            }
            removal.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(added.id), repo.loadManual().nodes.map { it.id })
        } finally {
            executor.shutdownNow()
        }
    }
}

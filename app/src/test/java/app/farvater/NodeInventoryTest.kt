package app.farvater

import android.app.Application
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.model.ProxyNode
import app.farvater.core.catalog.BuiltInCatalog
import app.farvater.core.parser.LinkParser
import app.farvater.data.SubscriptionRepository
import app.farvater.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NodeInventoryTest {
    private lateinit var app: Application
    private lateinit var repo: SubscriptionRepository
    private val source = "user-test"

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("farvater", 0).edit().clear().commit()
        File(app.filesDir, "subs").deleteRecursively()
        prepareRuntime(app)
        repo = App.repo
    }

    @Test fun manualMutationsPublishTheSameNodesThatArePersisted() {
        val node = LinkParser.parse(link("manual"))!!
        repo.appendManual(listOf(node))
        assertEquals(listOf(node), repo.snapshots.value[ProxyNode.MANUAL_SOURCE]?.nodes)
        repo.removeManual(node.id)
        assertTrue(repo.snapshots.value.getValue(ProxyNode.MANUAL_SOURCE).nodes.isEmpty())
        assertTrue(repo.loadManual().nodes.isEmpty())
    }

    @Test fun cacheCanBeLoadedWithoutConstructingAViewModel() = runBlocking {
        intercept { link("cached") }
        val loaded = repo.fetch(source, listOf("https://sub.example.test/new"), false, isCurrent = { true })
        val reopened = SubscriptionRepository(app)
        reopened.loadSnapshot(source)
        assertEquals(loaded.nodes, reopened.snapshots.value[source]?.nodes)
    }

    @Test fun manualRefreshStillLoadsADisabledCatalogSource() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        intercept { calls.incrementAndGet(); link("manual-refresh") }
        val vm = MainViewModel(app)
        try {
            val initialization = vm.viewModelScope.coroutineContext[Job]!!.children.toList()
            initialization.forEach { it.cancel() }
            await { initialization.all { it.isCompleted } }
            App.prefs.settings = App.prefs.settings.copy(communityEnabled = true)
            App.prefs.enabledSources = emptySet()
            val id = BuiltInCatalog.sources.first().id
            vm.refreshSource(id)
            await { repo.snapshots.value[id]?.nodes?.isNotEmpty() == true }
            assertEquals(1, calls.get())
            assertFalse(id in App.prefs.enabledSources)
        } finally { vm.viewModelScope.cancel() }
    }

    @Test fun removedSourceCannotBeRestoredByALateFetch() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        intercept { started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); link("removed") }
        val request = async(Dispatchers.IO) { repo.fetch(source, listOf("https://sub.example.test/old"), false, isCurrent = { true }) }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repo.deleteCache(source)
        } finally { release.countDown() }
        request.await()
        assertNull(repo.snapshots.value[source])
        assertNull(repo.loadCached(source))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun deletionOrResetRejectsFetchStillWaitingForIo() = kotlinx.coroutines.test.runTest {
        for (reset in listOf(false, true)) {
            repo = SubscriptionRepository(app, kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
            var requests = 0
            intercept { requests++; link("queued") }
            val request = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                repo.fetch(source, listOf("https://sub.example.test/queued"), false, isCurrent = { true })
            }
            if (reset) repo.resetInventory() else repo.deleteCache(source)
            testScheduler.advanceUntilIdle()
            request.await()
            assertEquals(0, requests)
            assertNull(repo.snapshots.value[source])
            assertNull(repo.loadCached(source))
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun queuedCallerRechecksWhetherTheSourceIsStillRequested() = kotlinx.coroutines.test.runTest {
        repo = SubscriptionRepository(app, kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        var requested = true
        var requests = 0
        intercept { requests++; link("withdrawn") }
        val request = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            repo.fetch(source, listOf("https://sub.example.test/queued"), false, isCurrent = { requested })
        }
        requested = false
        testScheduler.advanceUntilIdle()
        request.await()
        assertEquals(0, requests)
        assertNull(repo.snapshots.value[source])
        assertNull(repo.loadCached(source))
    }

    @Test fun newestFetchWinsInMemoryAndOnDisk() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        intercept { path ->
            if (path == "/old") {
                started.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
                link("old")
            } else link("new")
        }
        val previous = async(Dispatchers.IO) { repo.fetch(source, listOf("https://sub.example.test/old"), false, isCurrent = { true }) }
        val current = try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repo.fetch(source, listOf("https://sub.example.test/new"), false, isCurrent = { true })
        } finally { release.countDown() }
        previous.await()
        assertEquals(current.nodes, repo.snapshots.value[source]?.nodes)
        assertEquals(current.nodes, SubscriptionRepository(app).loadCached(source)?.nodes)
    }

    @Test fun cancelledFetchDoesNotWriteOrPublishItsResponse() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        intercept { started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); link("cancelled") }
        val request = async(Dispatchers.IO) { repo.fetch(source, listOf("https://sub.example.test/old"), false, isCurrent = { true }) }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            request.cancel()
        } finally { release.countDown() }
        request.join()
        assertNull(repo.snapshots.value[source])
        assertNull(repo.loadCached(source))
    }

    private fun intercept(body: (String) -> String) {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test")
                .body(body(chain.request().url.encodedPath).toResponseBody()).build()
        }.build()
        SubscriptionRepository::class.java.getDeclaredField("baseClient").apply { isAccessible = true }.set(repo, client)
    }

    private fun link(name: String) = "vless://00000000-0000-0000-0000-000000000001@$name.example.test:443?security=tls"

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
            Thread.sleep(5)
        }
        assertTrue(condition())
    }
}

package app.farvater

import android.app.Application
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.UpdateAsset
import app.farvater.data.UpdateInfo
import app.farvater.data.UpdateRepository
import app.farvater.ui.MainViewModel
import app.farvater.ui.UiState
import app.farvater.ui.UpdateStage
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class, qualifiers = "ru")
@LooperMode(LooperMode.Mode.PAUSED)
class UpdateStateTest {
    private lateinit var vm: MainViewModel
    private lateinit var state: MutableStateFlow<UiState>
    private val checkStarted = CountDownLatch(1)
    private val releaseCheck = CountDownLatch(1)
    private val downloadStarted = CountDownLatch(1)
    private val releaseDownload = CountDownLatch(1)
    private val downloads = AtomicInteger()
    private val version = BuildConfig.VERSION_CODE + 10
    private val asset = UpdateAsset("https://updates.example.test/app.apk", "0".repeat(64), 1)

    @Before
    fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        prepareRuntime(app)
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.encodedPath.endsWith("/update.json")) {
                checkStarted.countDown()
                if (!releaseCheck.await(8, TimeUnit.SECONDS)) throw IOException("bounded manifest fixture timed out")
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body("""{"versionCode":$version,"versionName":"audit-refreshed","assets":{"universal":{"url":"${asset.url}","sha256":"${asset.sha256}","size":1}}}""".toResponseBody())
                    .build()
            } else {
                downloads.incrementAndGet()
                downloadStarted.countDown()
                releaseDownload.await(8, TimeUnit.SECONDS)
                throw IOException("bounded download fixture stopped")
            }
        }.build()
        App::class.java.getDeclaredField("updates").apply { isAccessible = true }
            .set(null, UpdateRepository(app, client))
        vm = MainViewModel(app)
        val initialization = vm.viewModelScope.coroutineContext[Job]!!.children.toList()
        initialization.forEach { it.cancel() }
        awaitCondition("initialization coroutines did not stop") { initialization.all { it.isCompleted } }
        @Suppress("UNCHECKED_CAST")
        val flow = MainViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
            .get(vm) as MutableStateFlow<UiState>
        state = flow
        state.value = state.value.copy(
            update = UpdateInfo(version, "audit-existing", assets = mapOf("universal" to asset)),
            updateStage = UpdateStage.Idle,
            showUpdate = true,
        )
    }

    @After
    fun finish() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        releaseCheck.countDown()
        releaseDownload.countDown()
        if (::vm.isInitialized) {
            val job = vm.viewModelScope.coroutineContext[Job]!!
            awaitCondition("audit coroutines did not stop") { job.isCompleted }
        }
    }

    @Test
    fun staleAutomaticCheckCannotClearAnActiveDownloadGuard() {
        vm.checkUpdates(manual = false)
        awaitCondition("automatic manifest request did not start") { checkStarted.count == 0L }
        val checks = vm.viewModelScope.coroutineContext[Job]!!.children.toList()
        vm.installUpdate()
        awaitCondition("APK download did not start") { downloadStarted.count == 0L }
        val downloading = state.value.updateStage
        assertTrue("fixture must have an active download", downloading is UpdateStage.Downloading)
        assertEquals(1, downloads.get())

        releaseCheck.countDown()
        awaitCondition("automatic manifest check did not finish") { checks.all { it.isCompleted } }

        assertEquals("a late manifest result must preserve the active download and duplicate-launch guard",
            downloading, state.value.updateStage)
        vm.installUpdate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("a second install click must not start another APK request", 1, downloads.get())
    }

    @Test
    fun automaticChecksDoNotOverlap() {
        vm.checkUpdates(manual = false)
        awaitCondition("automatic manifest request did not start") { checkStarted.count == 0L }
        vm.checkUpdates(manual = false)
        vm.checkUpdates(manual = true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, vm.viewModelScope.coroutineContext[Job]!!.children.count())
        assertEquals(UpdateStage.Idle, state.value.updateStage)
    }

    @Test
    fun staleAutomaticCheckCannotReplaceDownloadFailure() {
        vm.checkUpdates(manual = false)
        awaitCondition("automatic manifest request did not start") { checkStarted.count == 0L }
        val checks = vm.viewModelScope.coroutineContext[Job]!!.children.toList()
        val update = state.value.update
        vm.installUpdate()
        awaitCondition("APK download did not start") { downloadStarted.count == 0L }
        releaseDownload.countDown()
        awaitCondition("download failure was not reported") { state.value.updateStage is UpdateStage.Failed }
        val failure = state.value.updateStage
        releaseCheck.countDown()
        awaitCondition("automatic manifest check did not finish") { checks.all { it.isCompleted } }
        assertEquals(failure, state.value.updateStage)
        assertEquals(update, state.value.update)
    }

    @Test
    fun failedDownloadCanBeRetried() {
        releaseDownload.countDown()
        vm.installUpdate()
        awaitCondition("first download did not fail") { state.value.updateStage is UpdateStage.Failed }
        assertEquals(1, downloads.get())
        vm.installUpdate()
        awaitCondition("retry did not finish") {
            downloads.get() == 2 && state.value.updateStage is UpdateStage.Failed
        }
    }

    private fun awaitCondition(message: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!predicate() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue(message, predicate())
    }
}

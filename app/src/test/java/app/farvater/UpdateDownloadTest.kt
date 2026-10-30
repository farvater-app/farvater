package app.farvater

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.UpdateAsset
import app.farvater.data.UpdateInfo
import app.farvater.data.UpdateRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class, qualifiers = "ru")
@Suppress("DEPRECATION")
class UpdateDownloadTest {
    private lateinit var app: Application
    private lateinit var target: File
    private val bytes = ByteArray(100_000) { (it % 127).toByte() }
    private val calls = mutableListOf<String>()
    private val info get() = UpdateInfo(10031, "1.0.31", assets = mapOf("universal" to
        UpdateAsset("https://updates.example.test/app.apk", hash(bytes), bytes.size.toLong())))

    @Before
    fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        File(app.cacheDir, "updates").deleteRecursively()
        target = File(app.cacheDir, "updates/farvater-10031.apk")
        val installed = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES)
        installed.signatures = arrayOf(Signature(byteArrayOf(1, 2, 3)))
        shadowOf(app.packageManager).installPackage(installed)
        archive()
    }

    private fun archive(packageName: String = app.packageName, version: Int = 10031, signer: Byte = 1) {
        shadowOf(app.packageManager).setPackageArchiveInfo(target.path, PackageInfo().apply {
            this.packageName = packageName
            versionCode = version
            signatures = arrayOf(Signature(byteArrayOf(signer, 2, 3)))
        })
    }

    @Test
    fun tunnelFailureFallsBackToDirect() = runBlocking {
        val progress = mutableListOf<Float>()
        val result = repository().download(info, true) { progress += it }
        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        assertEquals(listOf("tunnel", "direct"), calls)
        assertArrayEquals(bytes, result.getOrThrow().readBytes())
        assertEquals(1f, progress.last())
    }

    @Test
    fun bothPathsFailWithUsefulAdvice() = runBlocking {
        val result = repository(directCode = 503).download(info, true) {}
        assertTrue(result.isFailure)
        assertEquals(listOf("tunnel", "direct"), calls)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, message.contains("другой узел") && message.contains("без VPN"))
        assertFalse(target.exists())
    }

    @Test
    fun substitutedFileFromDirectPathIsRejected() = runBlocking {
        val result = repository(directBytes = "substituted".toByteArray()).download(info, true) {}
        assertEquals(listOf("tunnel", "direct"), calls)
        assertEquals(app.getString(R.string.error_checksum), result.exceptionOrNull()?.message)
        assertFalse(target.exists())
    }

    @Test
    fun unsuccessfulTunnelResponseFallsBack() = runBlocking {
        val repo = UpdateRepository(app, client("direct", 200, bytes), client("tunnel", 503, bytes))
        assertTrue(repo.download(info, true) {}.isSuccess)
        assertEquals(listOf("tunnel", "direct"), calls)
    }

    @Test
    fun tunnelSuccessDoesNotTryDirect() = runBlocking {
        val repo = UpdateRepository(app, client("direct", 0, bytes), client("tunnel", 200, bytes))
        assertTrue(repo.download(info, true) {}.isSuccess)
        assertEquals(listOf("tunnel"), calls)
    }

    @Test
    fun withoutVpnOnlyDirectIsUsed() = runBlocking {
        assertTrue(repository().download(info, false) {}.isSuccess)
        assertEquals(listOf("direct"), calls)
    }

    @Test
    fun secondPathStillChecksPackageVersionAndSigner() = runBlocking {
        for ((packageName, version, signer) in listOf(
            Triple("example.other", 10031, 1.toByte()),
            Triple(app.packageName, 10030, 1.toByte()),
            Triple(app.packageName, 10031, 4.toByte()),
        )) {
            archive(packageName, version, signer)
            val error = repository().download(info, true) {}.exceptionOrNull()
            assertNotNull(error)
            assertTrue(error!!.message in listOf(app.getString(R.string.error_wrong_app),
                app.getString(R.string.error_wrong_version), app.getString(R.string.error_wrong_signer)))
            assertFalse(target.exists())
        }
    }

    @Test
    fun integrityFailureDoesNotStartAnotherPath() = runBlocking {
        val repo = UpdateRepository(app, client("direct", 200, bytes), client("tunnel", 200, byteArrayOf(1)))
        assertEquals(app.getString(R.string.error_checksum), repo.download(info, true) {}.exceptionOrNull()?.message)
        assertEquals(listOf("tunnel"), calls)
        assertFalse(target.exists())
    }

    @Test
    fun partialFileIsRemovedAndProgressRestartsBeforeDirectAttempt() = runBlocking {
        val progress = mutableListOf<Float>()
        var reads = 0
        val tunnel = OkHttpClient.Builder().addInterceptor { chain ->
            calls += "tunnel"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test")
                .body(streamBody(object : Source {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        if (reads++ > 0) throw IOException("synthetic interrupted body")
                        sink.write(ByteArray(8192) { 9 })
                        return 8192
                    }
                    override fun close() = Unit
                    override fun timeout() = Timeout.NONE
                })).build()
        }.build()
        val direct = OkHttpClient.Builder().addInterceptor { chain ->
            calls += "direct"
            assertFalse(target.exists())
            assertEquals(0f, progress.last())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test")
                .body(bytes.toResponseBody()).build()
        }.build()
        val result = UpdateRepository(app, direct, tunnel).download(info, true) { progress += it }
        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        assertEquals(2, progress.count { it == 0f })
        assertTrue(progress.any { it > 0f && it < 1f })
        assertEquals(1f, progress.last())
        assertArrayEquals(bytes, target.readBytes())
    }

    @Test
    fun cancellationClosesBlockedCallAndDeletesPartialFileWithoutFallback() = runBlocking {
        val entered = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        val tunnel = OkHttpClient.Builder().addInterceptor { chain ->
            calls += "tunnel"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test")
                .body(streamBody(object : Source {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        entered.countDown()
                        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                        while (!chain.call().isCanceled() && System.nanoTime() < end) Thread.sleep(5)
                        cancelled.set(chain.call().isCanceled())
                        throw IOException("synthetic cancelled body")
                    }
                    override fun close() { closed.set(true) }
                    override fun timeout() = Timeout.NONE
                })).build()
        }.build()
        val repo = UpdateRepository(app, client("direct", 200, bytes), tunnel)
        var returned = false
        val job = launch(Dispatchers.Default) {
            repo.download(info, true) {}
            returned = true
        }
        assertTrue(withContext(Dispatchers.IO) { entered.await(3, TimeUnit.SECONDS) })
        job.cancelAndJoin()
        assertFalse(returned)
        assertTrue(cancelled.get())
        assertTrue(closed.get())
        assertFalse(target.exists())
        assertEquals(listOf("tunnel"), calls)
    }

    private fun streamBody(input: Source) = object : ResponseBody() {
        override fun contentType() = null
        override fun contentLength() = bytes.size.toLong()
        override fun source() = input.buffer()
    }

    private fun repository(directCode: Int = 200, directBytes: ByteArray = bytes) = UpdateRepository(
        app,
        client("direct", directCode, directBytes),
        client("tunnel", 0, bytes),
    )

    private fun client(name: String, code: Int, body: ByteArray) = OkHttpClient.Builder().addInterceptor { chain ->
        calls += name
        if (code == 0) throw IOException("synthetic network failure")
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body(body.toResponseBody()).build()
    }.build()

    private fun hash(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
        .joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
}

package app.farvater

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.UpdateAsset
import app.farvater.data.UpdateInfo
import app.farvater.data.UpdateRepository
import app.farvater.data.UpdateSizeLimits
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class, qualifiers = "ru")
@Suppress("DEPRECATION")
class UpdateSizeLimitTest {
    private lateinit var app: Application
    private lateinit var target: File
    private val limits = UpdateSizeLimits(manifestBytes = 4096, apkBytes = 8192)
    private val bodies = mutableListOf<CountingBody>()
    private var directCalls = 0
    private var largestPartialFile = 0L

    @Before
    fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        File(app.cacheDir, "updates").deleteRecursively()
        target = File(app.cacheDir, "updates/farvater-10031.apk")
        val installed = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES)
        installed.signatures = arrayOf(Signature(byteArrayOf(1, 2, 3)))
        shadowOf(app.packageManager).installPackage(installed)
        shadowOf(app.packageManager).setPackageArchiveInfo(target.path, PackageInfo().apply {
            packageName = app.packageName
            versionCode = 10031
            signatures = arrayOf(Signature(byteArrayOf(1, 2, 3)))
        })
    }

    @Test
    fun advertisedManifestOverLimitIsClosedWithoutReading() = runBlocking {
        val repo = repository(limits.manifestBytes + 1, ByteArray(32))
        assertSizeError(repo.check(false))
        assertEquals(3, bodies.size)
        assertTrue(bodies.all { it.readBytes == 0L && it.closed })
    }

    @Test
    fun unknownLengthManifestStopsBeforeParsingWithoutReadingWholeBody() = runBlocking {
        val data = ByteArray(32 * 1024) { ' '.code.toByte() }
        val repo = repository(-1, data)
        assertSizeError(repo.check(false))
        assertTrue(bodies.all { it.readBytes in (limits.manifestBytes + 1)..(limits.manifestBytes + 8192) })
        assertTrue(bodies.all { it.readBytes < data.size && it.closed })
    }

    @Test
    fun manifestExactlyAtLimitIsAcceptedWithUnknownLength() = runBlocking {
        val json = "{\"versionCode\":10031,\"versionName\":\"1.0.31\"}"
        val bytes = json.padEnd(limits.manifestBytes.toInt()).toByteArray()
        val info = repository(-1, bytes).check(false).getOrThrow()
        assertEquals(10031, info?.versionCode)
        assertEquals(1, bodies.size)
        assertTrue(bodies.single().closed)
    }

    @Test
    fun advertisedApkOverLimitDoesNotReadOrRetryDirect() = runBlocking {
        val repo = repository(limits.apkBytes + 1, ByteArray(32))
        assertSizeError(repo.download(info(), true) {})
        assertEquals(0, directCalls)
        assertEquals(1, bodies.size)
        assertEquals(0L, bodies.single().readBytes)
        assertTrue(bodies.single().closed)
        assertFalse(target.exists())
    }

    @Test
    fun metadataCannotIncreaseApkLimit() = runBlocking {
        val repo = repository(-1, ByteArray(32))
        assertSizeError(repo.download(info(size = limits.apkBytes + 1), true) {})
        assertTrue(bodies.isEmpty())
        assertEquals(0, directCalls)
        assertFalse(target.exists())
    }

    @Test
    fun unknownLengthApkStopsBeforeOversizedWriteAndDeletesPartialFile() = runBlocking {
        val data = ByteArray(32 * 1024) { 1 }
        val repo = repository(-1, data)
        assertSizeError(repo.download(info(size = 1), true) {})
        assertEquals(0, directCalls)
        assertEquals(1, bodies.size)
        assertTrue(bodies.single().readBytes < data.size)
        assertTrue(bodies.single().closed)
        assertTrue(largestPartialFile in 1..limits.apkBytes)
        assertFalse(target.exists())
    }

    @Test
    fun unknownLengthApkAtLimitStillChecksAndAcceptsAuthenticPackage() = runBlocking {
        val data = ByteArray(limits.apkBytes.toInt()) { (it % 127).toByte() }
        val hash = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        val repo = repository(-1, data)
        val result = repo.download(info(hash), true) {}
        assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
        assertArrayEquals(data, result.getOrThrow().readBytes())
        assertEquals(0, directCalls)
        assertTrue(bodies.single().closed)
    }

    private fun info(hash: String = "wrong", size: Long = 0) = UpdateInfo(
        10031, "1.0.31", assets = mapOf("universal" to UpdateAsset("https://updates.example.test/app.apk", hash, size)),
    )

    private fun assertSizeError(result: Result<*>) {
        assertTrue(result.isFailure)
        assertEquals(app.getString(R.string.error_update_size), result.exceptionOrNull()?.message)
    }

    private fun repository(length: Long, data: ByteArray): UpdateRepository {
        fun client(direct: Boolean) = OkHttpClient.Builder().addInterceptor { chain ->
            if (direct) directCalls++
            val body = CountingBody(length, data).also(bodies::add)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("test").body(body).build()
        }.build()
        return UpdateRepository(app, client(true), client(false), limits)
    }

    private inner class CountingBody(private val length: Long, private val bytes: ByteArray) : ResponseBody() {
        var readBytes = 0L
        var closed = false
        private val input = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                largestPartialFile = maxOf(largestPartialFile, target.length())
                if (readBytes == bytes.size.toLong()) return -1
                val count = minOf(byteCount, 1024, bytes.size - readBytes).toInt()
                sink.write(bytes, readBytes.toInt(), count)
                readBytes += count
                return count.toLong()
            }
            override fun timeout() = Timeout.NONE
            override fun close() { closed = true }
        }.buffer()
        override fun contentLength() = length
        override fun contentType() = null
        override fun source() = input
    }
}

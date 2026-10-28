package app.farvater

import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.ApkSharing
import app.farvater.data.LocalizedIOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru", application = Application::class)
class ApkSharingTest {
    @Before fun resetFileProviderPathsBetweenRobolectricApplications() {
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun apk(vararg architectures: String): File = File.createTempFile("installed-", ".apk", app.cacheDir).also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("test fixture".toByteArray())
            zip.closeEntry()
            architectures.forEach {
                zip.putNextEntry(ZipEntry("lib/$it/libgojni.so"))
                zip.write(byteArrayOf(0, 1, 2))
                zip.closeEntry()
            }
        }
    }

    private fun context(file: File, splits: Array<String>? = null) = object : ContextWrapper(app) {
        override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(super.getApplicationInfo()).apply {
            sourceDir = file.absolutePath
            splitSourceDirs = splits
        }
    }

    @Test fun inspectsActualApkArchitectures() = runBlocking {
        assertEquals(listOf("arm64-v8a"), ApkSharing.architectures(context(apk("arm64-v8a"))))
        assertEquals(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"), ApkSharing.architectures(context(apk("x86_64", "x86", "armeabi-v7a", "arm64-v8a"))))
    }

    @Test fun sharesOnlyInstalledBytesWithReadPermission() = runBlocking {
        val source = apk("arm64-v8a")
        File(app.filesDir, "private-test-data").writeText("private settings must stay here")
        val intent = ApkSharing.prepare(context(source))
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("content", uri.scheme)
        assertEquals("${app.packageName}.files", uri.authority)
        assertEquals("farvater-${BuildConfig.VERSION_NAME}.apk", uri.lastPathSegment)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertArrayEquals(source.readBytes(), app.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("arm64-v8a"))
        assertFalse(File(app.cacheDir, "share").listFiles().orEmpty().any { it.extension == "part" })
    }

    @Test fun splitInstallationIsRejectedInsteadOfSharingIncompleteBase() {
        val failure = assertThrows(LocalizedIOException::class.java) {
            runBlocking { ApkSharing.prepare(context(apk("arm64-v8a"), arrayOf("split_config.arm64_v8a.apk"))) }
        }
        assertEquals(R.string.share_apk_split, failure.textId)
    }

    @Test fun missingNativeLibraryIsNotCalledUniversal() {
        assertThrows(LocalizedIOException::class.java) { ApkSharing.readArchitectures(apk()) }
    }

    @Test fun filenameStaysAsciiAndCannotEscapeDirectoryInPersianLocale() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fa-IR"))
            assertEquals("farvater-1.0.30-beta1.apk", ApkSharing.fileName("1.0.30-beta1"))
            val name = ApkSharing.fileName("../../bad/۱۲۳")
            assertFalse(name.contains('/'))
            assertTrue(name.all { it.code < 128 })
        } finally {
            Locale.setDefault(before)
        }
    }
}

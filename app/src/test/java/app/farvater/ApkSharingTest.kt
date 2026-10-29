package app.farvater

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.ApkProvider
import app.farvater.data.ApkSharing
import app.farvater.data.LocalizedIOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru", application = Application::class)
class ApkSharingTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun apk(vararg architectures: String): File = File.createTempFile("installed-", ".apk", app.filesDir).also { file ->
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

    private fun context(file: File, splits: Array<String>? = null): Application = app.apply {
        applicationInfo.sourceDir = file.absolutePath
        applicationInfo.splitSourceDirs = splits
    }

    @Test @Config(sdk = [26]) fun sharingWorksOnAndroidEight() = runBlocking {
        val source = apk("armeabi-v7a")
        val cacheBefore = app.cacheDir.walkTopDown().toList().toSet()
        val intent = ApkSharing.prepare(context(source))
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("content", intent.clipData!!.getItemAt(0).uri.scheme)
        assertEquals(cacheBefore, app.cacheDir.walkTopDown().toList().toSet())
        assertArrayEquals(source.readBytes(), app.contentResolver.openInputStream(intent.clipData!!.getItemAt(0).uri)!!.use { it.readBytes() })
    }

    @Test fun inspectsActualApkArchitectures() = runBlocking {
        assertEquals(listOf("arm64-v8a"), ApkSharing.architectures(context(apk("arm64-v8a"))))
        assertEquals(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"), ApkSharing.architectures(context(apk("x86_64", "x86", "armeabi-v7a", "arm64-v8a"))))
    }

    @Test fun sharesOnlyInstalledBytesWithReadPermission() = runBlocking {
        val source = apk("arm64-v8a")
        File(app.filesDir, "private-test-data").writeText("private settings must stay here")
        val cacheBefore = app.cacheDir.walkTopDown().toList().toSet()
        val intent = ApkSharing.prepare(context(source))
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("content", uri.scheme)
        assertEquals("${app.packageName}.apk", uri.authority)
        assertEquals("farvater-${BuildConfig.VERSION_NAME}.apk", uri.lastPathSegment)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertArrayEquals(source.readBytes(), app.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("arm64-v8a"))
        assertEquals(cacheBefore, app.cacheDir.walkTopDown().toList().toSet())
        assertFalse(File(app.cacheDir, "share").exists())
        assertEquals(intent.type, app.contentResolver.getType(uri))
        app.contentResolver.query(uri, null, null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            assertEquals(ApkSharing.fileName(BuildConfig.VERSION_NAME), it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
            assertEquals(source.length(), it.getLong(it.getColumnIndexOrThrow(OpenableColumns.SIZE)))
        }
    }

    @Test fun splitInstallationIsRejectedInsteadOfSharingIncompleteBase() {
        val failure = assertThrows(LocalizedIOException::class.java) {
            runBlocking { ApkSharing.prepare(context(apk("arm64-v8a"), arrayOf("split_config.arm64_v8a.apk"))) }
        }
        assertEquals(R.string.share_apk_split, failure.textId)
    }

    @Test fun providerRejectsOtherFilesAndEveryWriteMode() {
        context(apk("arm64-v8a"))
        val provider = org.robolectric.Robolectric.buildContentProvider(ApkProvider::class.java).create().get()
        val own = ApkProvider.uri(app)
        val invalid = listOf(
            "content://${app.packageName}.apk/../files/private-test-data",
            "content://${app.packageName}.apk/%2e%2e%2fprivate-test-data",
            "content://${app.packageName}.apk/another.apk",
            "content://${app.packageName}.files/${own.lastPathSegment}",
            "$own?path=/data/private",
            "$own#fragment",
            "file://${app.applicationInfo.sourceDir}",
        )
        invalid.forEach { raw ->
            val uri = Uri.parse(raw)
            assertThrows(FileNotFoundException::class.java) { provider.openFile(uri, "r") }
            assertThrows(FileNotFoundException::class.java) { provider.getType(uri) }
            assertThrows(FileNotFoundException::class.java) { provider.query(uri, null, null, null, null) }
        }
        listOf("w", "wt", "wa", "rw", "rwt").forEach { mode ->
            assertThrows(FileNotFoundException::class.java) { provider.openFile(own, mode) }
        }
        assertThrows(UnsupportedOperationException::class.java) { provider.insert(own, null) }
        assertThrows(UnsupportedOperationException::class.java) { provider.update(own, null, null, null) }
        assertThrows(UnsupportedOperationException::class.java) { provider.delete(own, null, null) }
        val info = app.packageManager.resolveContentProvider("${app.packageName}.apk", 0)!!
        assertFalse(info.exported)
        assertTrue(info.grantUriPermissions)
    }

    @Test fun providerAlsoRejectsSplitInstallation() {
        context(apk("arm64-v8a"), arrayOf("split.apk"))
        val provider = org.robolectric.Robolectric.buildContentProvider(ApkProvider::class.java).create().get()
        assertThrows(LocalizedIOException::class.java) { provider.openFile(ApkProvider.uri(app), "r") }
    }

    @Test fun legacyCacheIsRemovedWithoutTouchingOtherFiles() = runBlocking {
        File(app.cacheDir, "share/nested").mkdirs()
        File(app.cacheDir, "share/nested/old.apk").writeText("old")
        val other = File(app.cacheDir, "keep").apply { writeText("keep") }
        ApkSharing.clearLegacyCache(app)
        assertFalse(File(app.cacheDir, "share").exists())
        assertEquals("keep", other.readText())
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

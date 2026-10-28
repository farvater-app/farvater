package app.farvater

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.UpdateInfo
import app.farvater.data.UpdateRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.InvocationTargetException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 27, 28, 35], application = Application::class, qualifiers = "ru")
@Suppress("DEPRECATION")
class UpdateCompatibilityTest {
    private lateinit var app: Application
    private lateinit var repo: UpdateRepository
    private lateinit var archive: PackageInfo
    private lateinit var apk: File

    @Before
    fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        repo = UpdateRepository(app)
        apk = File(app.cacheDir, "test.apk")
        val installed = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES)
        installed.signatures = arrayOf(Signature(byteArrayOf(1, 2, 3)))
        shadowOf(app.packageManager).installPackage(installed)
        archive = PackageInfo().apply {
            packageName = app.packageName
            versionCode = 1030
            signatures = installed.signatures
        }
    }

    @Test
    fun acceptsMatchingPackageVersionAndSigner() = verify()

    @Test
    fun rejectsWrongVersion() {
        archive.versionCode = 1029
        rejects()
    }

    @Test
    fun rejectsDifferentPackage() {
        archive.packageName = "example.other"
        rejects()
    }

    @Test
    fun rejectsDifferentSigner() {
        archive.signatures = arrayOf(Signature(byteArrayOf(4, 5, 6)))
        rejects()
    }

    private fun rejects() {
        val error = runCatching { verify() }.exceptionOrNull()
        assertTrue(error is InvocationTargetException && error.cause is IllegalStateException)
    }

    private fun verify() {
        shadowOf(app.packageManager).setPackageArchiveInfo(apk.path, archive)
        UpdateRepository::class.java.getDeclaredMethod("verifyApk", File::class.java, UpdateInfo::class.java).apply {
            isAccessible = true
        }.invoke(repo, apk, UpdateInfo(1030, "test"))
    }
}

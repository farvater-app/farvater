package app.farvater.ui

import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.farvater.ui.screens.ShareAppPage
import app.farvater.ui.theme.FarvaterTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru", application = Application::class)
class ShareAppUiTest {
    @Before fun resetFileProviderPathsBetweenRobolectricApplications() {
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    @get:Rule val rule = createComposeRule()

    @Test fun opensSystemChooserOnlyAfterExplicitShareTap() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val source = File.createTempFile("installed-ui-", ".apk", app.cacheDir)
        ZipOutputStream(source.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libgojni.so"))
            zip.write(byteArrayOf(0, 1))
            zip.closeEntry()
        }
        rule.setContent {
            val base = LocalContext.current
            val context = remember(base) {
                object : ContextWrapper(base) {
                    override fun getApplicationInfo() = ApplicationInfo(super.getApplicationInfo()).apply {
                        sourceDir = source.absolutePath
                        splitSourceDirs = null
                    }
                }
            }
            CompositionLocalProvider(LocalContext provides context) {
                FarvaterTheme(dynamicColor = false) { ShareAppPage() }
            }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("APK для arm64-v8a.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(shadowOf(app).nextStartedActivity)
        rule.onNodeWithText("Поделиться").performClick()
        var chooser: Intent? = null
        rule.waitUntil(5_000) {
            rule.waitForIdle()
            chooser = chooser ?: shadowOf(app).nextStartedActivity
            chooser != null
        }
        assertEquals(Intent.ACTION_CHOOSER, chooser!!.action)
        val send = chooser!!.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/vnd.android.package-archive", send.type)
    }
}

package app.farvater.ui

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FormatBytesTest {
    private fun context(language: String): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        })
    }

    @Test fun terabytesStartAt1024GigabytesAndKeepRussianDecimalPoint() {
        val ru = context("ru")
        val tb = 1L shl 40
        assertEquals("1024.00 ГБ", formatBytes(ru, tb - 1))
        assertEquals("1.00 ТБ", formatBytes(ru, tb))
        assertEquals("1.17 ТБ", formatBytes(ru, 1200L * (1L shl 30)))
        assertEquals("8388608.00 ТБ", formatBytes(ru, Long.MAX_VALUE))
        assertEquals("0 Б", formatBytes(ru, -1))
        assertEquals("48.06 ГБ", formatBytes(ru, (48.06 * (1L shl 30)).toLong()))
    }

    @Test fun terabytesUseEnglishAndPersianResources() {
        assertEquals("1.17 TB", formatBytes(context("en"), 1200L * (1L shl 30)))
        assertEquals("۱٫۱۷ ترابایت", formatBytes(context("fa"), 1200L * (1L shl 30)))
    }
}

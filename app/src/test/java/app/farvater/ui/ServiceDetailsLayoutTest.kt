package app.farvater.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.*
import app.farvater.ui.components.ServiceCheckDetails
import app.farvater.ui.ios.IosSection
import app.farvater.ui.theme.FarvaterTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w320dp-h640dp", application = Application::class)
class ServiceDetailsLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun reasonsRemainReadableInEveryStyleThemeAndLanguageAtLargeFont() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val contexts = listOf("ru", "en", "fa").associateWith { language ->
            app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            })
        }
        val style = mutableStateOf(UiStyle.IOS)
        val dark = mutableStateOf(false)
        val language = mutableStateOf("ru")
        val scale = mutableStateOf(1f)
        val now = System.currentTimeMillis()
        val reasons = listOf(ServiceReason.CORE, ServiceReason.TIMEOUT, ServiceReason.CHALLENGE, ServiceReason.UNEXPECTED)
        val checks = ServiceChecks.builtIn.zip(reasons).associate { (target, reason) ->
            ServiceChecks.key("ANY", "node", target) to ServiceCheck(ServiceVerdict.UNKNOWN, reason, now)
        }
        lateinit var view: View
        rule.setContent {
            view = LocalView.current
            val context = contexts.getValue(language.value)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalLayoutDirection provides if (language.value == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(LocalDensity.current.density, scale.value),
            ) {
                FarvaterTheme(style = style.value, darkTheme = dark.value, dynamicColor = false) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        IosSection {
                            Column(Modifier.padding(16.dp)) {
                                ServiceCheckDetails("node", "ANY", ServiceChecks.builtIn, checks, now)
                            }
                        }
                    }
                }
            }
        }
        val directory = File(System.getProperty("farvater.screenshotDir"), "service-details").apply { mkdirs() }
        for (ui in UiStyle.entries) for (night in listOf(false, true)) for (locale in contexts.keys) {
            for (font in listOf(1f, 2f)) {
                rule.runOnIdle { style.value = ui; dark.value = night; language.value = locale; scale.value = font }
                for (target in ServiceChecks.builtIn) {
                    val name = when (target.kind) {
                        ServiceKind.GEMINI -> "Gemini"
                        ServiceKind.CHATGPT -> "ChatGPT"
                        ServiceKind.CLAUDE -> "Claude"
                        else -> "YouTube"
                    }
                    val node = rule.onNode(hasText(name, substring = true))
                    node.performScrollTo().assertIsDisplayed()
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    assertFalse("$ui/$night/$locale/$font/$name: size=${layout.size} width=${layout.didOverflowWidth} height=${layout.didOverflowHeight} lines=${layout.lineCount} constraints=${layout.layoutInput.constraints}", layout.hasVisualOverflow)
                }
                if (font == 1f && locale in listOf("ru", "fa")) {
                    rule.onNode(hasText("Gemini", substring = true)).performScrollTo()
                    rule.runOnIdle {
                        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                        view.draw(Canvas(bitmap))
                        File(directory, "$ui-$night-$locale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                }
            }
        }
    }
}

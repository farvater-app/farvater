package app.farvater.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.UiStyle
import app.farvater.ui.components.ConnectButton
import app.farvater.ui.components.ConnectMode
import app.farvater.ui.components.OriginConnect
import app.farvater.ui.ios.powerSave
import app.farvater.ui.theme.FarvaterTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp", application = Application::class)
class AnimationLifecycleTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val styles = listOf(UiStyle.IOS, UiStyle.MATERIAL, UiStyle.ROSKOMPOZOR, UiStyle.ORIGIN)

    @After fun restoreMotionSettings() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        powerSave = false
    }

    @Test fun connectedRingsKeepMovingAndResumeAfterBackground() {
        lateinit var view: View
        val owner = Owner()
        val style = mutableStateOf(UiStyle.IOS)
        val dark = mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                FarvaterTheme(style = style.value, darkTheme = dark.value, dynamicColor = false) {
                    key(style.value, dark.value) { Scene(style.value) }
                }
            }
        }
        for (currentStyle in styles) {
            for (currentDark in listOf(false, true)) {
                rule.runOnIdle { style.value = currentStyle; dark.value = currentDark }
                applyChanges()
                rule.mainClock.advanceTimeBy(13_000)
                val case = "$currentStyle dark=$currentDark"
                val afterThirteenSeconds = capture(view)
                rule.mainClock.advanceTimeBy(420)
                assertFalse("$case stopped in foreground", sameFrame(view, afterThirteenSeconds))
                rule.runOnIdle {
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                }
                rule.mainClock.advanceTimeBy(100)
                val stopped = capture(view)
                rule.mainClock.advanceTimeBy(10_500)
                assertTrue("$case keeps animating in background", sameFrame(view, stopped))
                rule.runOnIdle {
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                    owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }
                rule.mainClock.advanceTimeBy(100)
                val resumed = capture(view)
                rule.mainClock.advanceTimeBy(420)
                assertFalse("$case did not resume", sameFrame(view, resumed))
            }
        }
    }

    @Test fun reducedMotionKeepsConnectedScenesStatic() {
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        checkStaticScenes()
    }

    @Test fun powerSavingKeepsConnectedScenesStatic() {
        powerSave = true
        checkStaticScenes()
    }

    private fun checkStaticScenes() {
        lateinit var view: View
        val style = mutableStateOf(UiStyle.IOS)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            FarvaterTheme(style = style.value, dynamicColor = false) {
                key(style.value) { Scene(style.value) }
            }
        }
        for (currentStyle in styles) {
            rule.runOnIdle { style.value = currentStyle }
            applyChanges()
            rule.mainClock.advanceTimeBy(1500)
            val before = capture(view)
            rule.mainClock.advanceTimeBy(13_000)
            assertTrue("$currentStyle ignores reduced motion", sameFrame(view, before))
        }
    }

    @Composable private fun Scene(style: UiStyle) {
        if (style == UiStyle.ORIGIN) OriginConnect(ConnectMode.Connected, {})
        else ConnectButton(ConnectMode.Connected, {})
    }

    private fun capture(view: View): Bitmap {
        lateinit var bitmap: Bitmap
        rule.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun sameFrame(view: View, before: Bitmap): Boolean {
        val after = capture(view)
        return before.sameAs(after).also { before.recycle(); after.recycle() }
    }

    private fun applyChanges() {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
}

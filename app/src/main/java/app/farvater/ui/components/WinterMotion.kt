package app.farvater.ui.components

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.farvater.ui.ios.powerSave

@Composable
internal fun rememberWinterStill(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun disabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val reduced = remember(resolver) { mutableStateOf(disabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced.value = disabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced.value || powerSave
}

@Composable
internal fun rememberWinterClock(still: Boolean): State<Float> {
    val owner = LocalLifecycleOwner.current
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(still, owner) {
        clock.floatValue = 0f
        if (still) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val start = withInfiniteAnimationFrameMillis { it }
            var previous = start
            while (true) withInfiniteAnimationFrameMillis { now ->
                if (now - previous >= 32) {
                    clock.floatValue = (now - start) / 1000f
                    previous = now
                }
            }
        }
    }
    return clock
}

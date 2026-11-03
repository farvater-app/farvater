package app.farvater.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.farvater.ui.theme.Ios

internal val LocalShellMotionDisabled = staticCompositionLocalOf { false }

@Composable
internal fun <T> shellSpring(): FiniteAnimationSpec<T> =
    if (LocalShellMotionDisabled.current) snap() else spring(dampingRatio = if (Ios.flyme) 0.95f else 0.86f, stiffness = 500f)

package app.farvater

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.farvater.data.ThemeMode
import app.farvater.data.UiStyle
import app.farvater.data.UpdateWorker
import app.farvater.net.ImportLink
import app.farvater.ui.MainViewModel
import app.farvater.ui.FarvaterRoot
import app.farvater.ui.UiEvent
import app.farvater.ui.theme.FarvaterTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onVpnPermissionResult(it.resultCode == RESULT_OK)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { event ->
                    when (event) {
                        is UiEvent.RequestVpnPermission -> vpnPermission.launch(event.intent)
                        is UiEvent.StartActivity -> runCatching { startActivity(event.intent) }
                    }
                }
            }
        }

        if (savedInstanceState == null) handleDeepLink(intent)
        setContent {
            val settings by remember { vm.state.map { it.settings }.distinctUntilChanged() }
                .collectAsStateWithLifecycle(vm.state.value.settings)
            val dark = settings.uiStyle == UiStyle.ROSKOMPOZOR || when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) { applySystemBars(dark) }
            FarvaterTheme(darkTheme = dark, style = settings.uiStyle, dynamicColor = settings.dynamicColor) {
                FarvaterRoot(vm)
            }
        }
    }

    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateWorker.EXTRA_OPEN_UPDATE, false) == true) vm.openUpdate()
        val uri = intent?.data ?: return
        if (uri.scheme == "farvater" && uri.host == "import") {
            val link = runCatching { uri.getQueryParameter("url") }.getOrNull()
            ImportLink.sanitize(link)?.let(vm::requestImport) ?: vm.rejectImport()
        }
    }
}

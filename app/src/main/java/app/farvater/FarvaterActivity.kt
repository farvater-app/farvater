package app.farvater

import app.farvater.data.AppLanguages
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
import app.farvater.data.AppIcons
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

class FarvaterActivity : ComponentActivity() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguages.wrap(base))
    }

    private val vm: MainViewModel by viewModels()
    private var ready = false
    private var initialLink = false

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (ready) vm.onVpnPermissionResult(it.resultCode == RESULT_OK)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }
        initialLink = savedInstanceState == null
        openProfiles()
    }

    private fun openProfiles() {
        lifecycleScope.launch {
            val available = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { App.prefs.prepareProtectedData() }
            if (available) {
                ready = true
                openInterface()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) vm.onForeground()
            } else {
                android.app.AlertDialog.Builder(this@FarvaterActivity)
                    .setMessage(R.string.profile_storage_unavailable)
                    .setPositiveButton(R.string.action_try_again) { _, _ -> openProfiles() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
                    .setOnCancelListener { finish() }
                    .show()
            }
        }
    }

    private fun openInterface() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { event ->
                    when (event) {
                        UiEvent.ApplyBackupLanguage -> app.farvater.data.AppLanguages.applyBackupLanguage(this@FarvaterActivity)
                        is UiEvent.RequestVpnPermission -> vpnPermission.launch(event.intent)
                        is UiEvent.StartActivity -> runCatching { startActivity(event.intent) }
                    }
                }
            }
        }

        if (initialLink) handleDeepLink(intent)
        val initialSettings = vm.state.value.settings
        setContent {
            val settings by remember { vm.state.map { it.settings }.distinctUntilChanged() }
                .collectAsStateWithLifecycle(initialSettings)
            val dark = settings.uiStyle in setOf(UiStyle.ROSKOMPOZOR, UiStyle.WITCH) || when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) { applySystemBars(dark) }
            FarvaterTheme(darkTheme = dark, style = settings.uiStyle, dynamicColor = settings.dynamicColor,
                accent = settings.accent, font = settings.font) {
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
        lifecycleScope.launch { app.farvater.data.AppLanguages.applyBackupLanguage(this@FarvaterActivity) }
        if (ready) vm.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppIcons.applyPending(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (ready) handleDeepLink(intent) else initialLink = true
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

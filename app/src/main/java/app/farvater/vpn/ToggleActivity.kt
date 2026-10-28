package app.farvater.vpn

import app.farvater.data.AppLanguages
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.farvater.App
import app.farvater.FarvaterActivity

class ToggleActivity : ComponentActivity() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguages.wrap(base))
    }

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) App.prefs.lastNode?.let { FarvaterVpnService.start(this, it) }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        when (VpnBus.state.value) {
            is VpnState.Connected, is VpnState.Connecting -> FarvaterVpnService.stop(this)
            else -> {
                val node = App.prefs.lastNode
                val request = VpnService.prepare(this)
                when {
                    node == null -> startActivity(Intent(this, FarvaterActivity::class.java))
                    request != null -> return consent.launch(request)
                    else -> FarvaterVpnService.start(this, node)
                }
            }
        }
        finish()
    }
}

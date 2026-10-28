package app.farvater

import app.farvater.data.localizedString
import app.farvater.data.AppLanguages
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.farvater.core.xray.LocalProxyAuth
import app.farvater.data.Prefs
import app.farvater.data.SubscriptionRepository
import app.farvater.data.UpdateRepository
import app.farvater.data.UpdateWorker
import app.farvater.engine.XrayEngine
import app.farvater.ui.ios.powerSave
import app.farvater.vpn.FarvaterVpnService
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnWidget
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.net.Authenticator
import java.net.PasswordAuthentication

class App : Application() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguages.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppLanguages.migrate(this)
        instance = this
        prefs = Prefs(this)
        repo = SubscriptionRepository(this)
        XrayEngine.init(this)
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? =
                if (requestingProtocol?.startsWith("SOCKS") == true &&
                    (requestingHost == "127.0.0.1" || requestingSite?.isLoopbackAddress == true) &&
                    LocalProxyAuth.isLive(requestingPort)
                ) {
                    PasswordAuthentication(LocalProxyAuth.user, LocalProxyAuth.pass.toCharArray())
                } else {
                    null
                }
        })
        updates = UpdateRepository(this)
        refreshChannels()
        UpdateWorker.schedule(this)
        MainScope().launch { VpnBus.state.collect { VpnWidget.refresh(this@App, it) } }
        val power = getSystemService(PowerManager::class.java)
        powerSave = power.isPowerSaveMode
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    powerSave = power.isPowerSaveMode
                }
            },
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguages.refresh(this)
    }

    fun refreshChannels() {
        getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(NotificationChannel(FarvaterVpnService.CHANNEL, localizedString(R.string.settings_connection), NotificationManager.IMPORTANCE_LOW))
            createNotificationChannel(NotificationChannel(UpdateWorker.CHANNEL, localizedString(R.string.settings_updates), NotificationManager.IMPORTANCE_HIGH))
            deleteNotificationChannel("updates")
        }
    }

    companion object {
        lateinit var instance: App
            private set
        lateinit var prefs: Prefs
            private set
        lateinit var repo: SubscriptionRepository
            private set
        lateinit var updates: UpdateRepository
            private set
    }
}

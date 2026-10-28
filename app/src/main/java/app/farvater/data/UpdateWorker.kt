package app.farvater.data

import app.farvater.data.localizedString
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.farvater.App
import app.farvater.FarvaterActivity
import app.farvater.R
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnState
import java.util.concurrent.TimeUnit

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        checkAndNotify(applicationContext)
        return Result.success()
    }

    companion object {
        const val CHANNEL = "updates_alert"
        const val EXTRA_OPEN_UPDATE = "app.farvater.OPEN_UPDATE"
        private const val NOTIFICATION_ID = 21

        suspend fun checkAndNotify(context: Context) {
            if (!App.prefs.settings.autoUpdates) return
            val result = App.updates.check(viaTunnel = VpnBus.state.value is VpnState.Connected)
            if (result.isSuccess) App.prefs.lastUpdateCheck = System.currentTimeMillis()
            val info = result.getOrNull() ?: return
            if (App.prefs.notifiedVersion >= info.versionCode) return
            App.prefs.notifiedVersion = info.versionCode
            notify(context, info)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("updates", ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun notify(context: Context, info: UpdateInfo) {
            val open = PendingIntent.getActivity(
                context, 3,
                Intent(context, FarvaterActivity::class.java).putExtra(EXTRA_OPEN_UPDATE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = info.notes.take(3).joinToString("\n").ifBlank { context.localizedString(R.string.notification_update_hint) }
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(if (info.critical) context.localizedString(R.string.notification_critical_update, info.versionName) else context.localizedString(R.string.update_available, info.versionName))
                .setContentText(text.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }
}

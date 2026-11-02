package app.farvater.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import app.farvater.FarvaterActivity
import app.farvater.R
import app.farvater.data.localizedString
import app.farvater.engine.NodeCheckCoordinator
import app.farvater.engine.NodeCheckFailure
import app.farvater.engine.NodeCheckWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NodeCheckService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session = 0L
    private var lastStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, localizedString(R.string.settings_testing), NotificationManager.IMPORTANCE_LOW))
        scope.launch {
            NodeCheckCoordinator.state.collect { work ->
                if (session == 0L) return@collect
                if (!work.active) finish() else if (work.session == session) {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(work))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val requested = intent?.getLongExtra(EXTRA_SESSION, 0L) ?: 0L
        if (intent?.action == ACTION_STOP) {
            NodeCheckCoordinator.cancel(requested)
            if (requested == session) finish()
            return START_NOT_STICKY
        }
        if (!NodeCheckCoordinator.isCurrent(requested)) {
            if (!NodeCheckCoordinator.state.value.active) finish()
            return START_NOT_STICKY
        }
        if (session != requested) releaseWakeLock()
        session = requested
        try {
            val notification = notification(NodeCheckCoordinator.state.value)
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIFICATION_ID, notification)
            if (wakeLock?.isHeld != true) {
                wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "farvater:node-check").apply {
                    setReferenceCounted(false)
                    acquire(NodeCheckCoordinator.MAX_DURATION_MS + 5_000)
                }
            }
            NodeCheckCoordinator.ready(session)
        } catch (_: RuntimeException) {
            NodeCheckCoordinator.fail(session, NodeCheckFailure.START)
            finish()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        NodeCheckCoordinator.fail(session, NodeCheckFailure.TIMEOUT)
        finish()
    }

    override fun onDestroy() {
        NodeCheckCoordinator.cancel(session)
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun finish() {
        val work = NodeCheckCoordinator.state.value
        if (work.active && work.session != session) return
        if (!stopSelfResult(lastStartId)) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun notification(work: NodeCheckWork): Notification {
        val open = PendingIntent.getActivity(this, 20, Intent(this, FarvaterActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 21, Intent(this, NodeCheckService::class.java).setAction(ACTION_STOP)
            .putExtra(EXTRA_SESSION, work.session), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val progress = work.progress
        val done = progress?.done ?: work.serviceDone
        val total = progress?.total ?: work.serviceTotal
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(localizedString(if (work.services) R.string.services_running else R.string.state_testing))
            .setContentText(localizedString(R.string.checked_progress, done, total))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, localizedString(R.string.stop_testing), stop).build())
            .setProgress(total, done, total == 0)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .build()
    }

    companion object {
        const val EXTRA_SESSION = "check_session"
        const val ACTION_STOP = "app.farvater.action.STOP_CHECK"
        private const val CHANNEL = "node_checks"
        private const val NOTIFICATION_ID = 20
    }
}

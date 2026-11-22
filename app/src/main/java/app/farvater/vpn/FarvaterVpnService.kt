package app.farvater.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import app.farvater.R
import app.farvater.data.localizedString
import app.farvater.engine.CorePayload
import app.farvater.engine.TunBridge
import app.farvater.engine.XrayEngine
import java.util.concurrent.Executors

class FarvaterVpnService : VpnService() {
    private val main = Handler(Looper.getMainLooper())
    private class Command(val intent: Intent?, val id: Int) {
        var acknowledged = false
    }

    private val pending = ArrayDeque<Command>()
    private val awaitingReceipt = ArrayDeque<Command>()
    private var control: ITunnelControl? = null
    private var bound = false
    private var destroyed = false
    private var foreground = false
    @Volatile private var latestStart = 0
    internal var endpoint = Endpoint()
        private set

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (destroyed) return
            control = ITunnelControl.Stub.asInterface(service)
            dispatch()
        }
        override fun onServiceDisconnected(name: ComponentName) = reconnectController()
        override fun onBindingDied(name: ComponentName) {
            if (destroyed) return
            reconnectController()
            if (bound) unbindService(this)
            bound = bindController()
            if (!bound) lostController()
        }
        override fun onNullBinding(name: ComponentName) = lostController()
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(TunnelCoordinator.CHANNEL, localizedString(R.string.settings_connection), NotificationManager.IMPORTANCE_LOW))
        bound = bindController()
    }

    private fun bindController(): Boolean = bindService(Intent(this, TunnelCoordinator::class.java), connection,
        Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStart = startId
        if (!foreground && intent?.action != TunnelCoordinator.ACTION_STOP) {
            showForeground(Notification.Builder(this, TunnelCoordinator.CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_arrow)
                .setContentTitle(localizedString(R.string.app_name))
                .setContentText(localizedString(R.string.tunnel_starting))
                .setOngoing(true).build())
        }
        if (!bound || destroyed) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        pending.addLast(Command(intent, startId))
        dispatch()
        return if (intent?.action == TunnelCoordinator.ACTION_STOP && !intent.hasExtra(TunnelCoordinator.EXTRA_GRANT))
            START_REDELIVER_INTENT else START_STICKY
    }

    private fun dispatch() {
        val target = control ?: return
        try {
            while (pending.isNotEmpty()) {
                val command = pending.removeFirst()
                awaitingReceipt.addLast(command)
                target.command(endpoint, command.intent, command.id)
            }
        } catch (_: android.os.DeadObjectException) {
            reconnectController()
        } catch (_: android.os.RemoteException) {
            lostController()
        }
    }

    private fun reconnectController() {
        if (destroyed || control == null) return
        control = null
        endpoint.retire()
        endpoint = Endpoint()
        while (awaitingReceipt.isNotEmpty()) {
            val command = awaitingReceipt.removeLast()
            command.acknowledged = false
            pending.addFirst(command)
        }
        if (pending.isEmpty() && latestStart > 0) pending.addLast(Command(null, latestStart))
    }

    private fun lostController() {
        control = null
        pending.clear()
        awaitingReceipt.clear()
        endpoint.retire()
        stopSelf()
    }

    private fun showForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) startForeground(TunnelCoordinator.NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(TunnelCoordinator.NOTIFICATION_ID, n)
        foreground = true
    }

    override fun onRevoke() {
        main.post {
            runCatching { control?.ended(endpoint, true) }
            endpoint.retire()
            stopSelf()
        }
    }

    override fun onDestroy() {
        destroyed = true
        runCatching { control?.ended(endpoint, false) }
        if (bound) unbindService(connection)
        endpoint.retire()
        pending.clear()
        awaitingReceipt.clear()
        control = null
        super.onDestroy()
    }

    internal inner class Endpoint : IVpnEndpoint.Stub() {
        @Volatile private var retired = false

        fun retire() {
            if (retired) return
            retired = true
            cleanup.execute { releaseOwner() }
        }

        private fun releaseOwner() {
            synchronized(nativeLock) {
                if (owner !== this) return
                val bridgeStopped = runCatching { TunBridge.stop() }.isSuccess
                XrayEngine.stop()
                if (bridgeStopped && !XrayEngine.isRunning) owner = null
                else cleanup.schedule({ releaseOwner() }, 5, java.util.concurrent.TimeUnit.SECONDS)
            }
        }

        override fun startCore(configuration: Bundle): Int {
            val text = CorePayload.receive(configuration)
            return synchronized(nativeLock) {
                if (retired) return@synchronized R.string.vpn_interface_failed
                if (owner !== this) {
                    TunBridge.stop()
                    XrayEngine.stop()
                    if (XrayEngine.isRunning) return@synchronized R.string.vpn_interface_failed
                }
                owner = this
                val failure = XrayEngine.start(text).exceptionOrNull()
                when (failure) {
                    null -> 0
                    is app.farvater.data.LocalizedError -> failure.textId
                    else -> R.string.import_invalid
                }
            }
        }

        override fun establish(label: String, mtu: Int, ipv6: Boolean, excluded: Array<String>): ParcelFileDescriptor? {
            return synchronized(nativeLock) {
                if (retired || owner !== this) return@synchronized null
                val builder = Builder().setSession(label).setMtu(mtu)
                    .addAddress("10.10.14.1", 30).addRoute("0.0.0.0", 0).addDnsServer("1.1.1.1")
                if (ipv6) builder.addAddress("fc00::1", 126).addRoute("::", 0)
                try {
                    applyAppExclusions(packageName, excluded.toSet()) { builder.addDisallowedApplication(it) }
                } catch (error: android.content.pm.PackageManager.NameNotFoundException) {
                    throw IllegalStateException("Cannot exclude the VPN process", error)
                }
                if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)
                builder.establish()
            }
        }

        override fun startBridge(tun: ParcelFileDescriptor, configuration: ByteArray): Boolean = tun.use {
            synchronized(nativeLock) { !retired && owner === this && TunBridge.start(it.fd, configuration).isSuccess }
        }

        override fun stopCore() { synchronized(nativeLock) { if (owner === this) XrayEngine.stop() } }
        override fun stopBridge() { synchronized(nativeLock) { if (owner === this) TunBridge.stop() } }
        override fun counters(): LongArray? = synchronized(nativeLock) { if (!retired && owner === this) TunBridge.stats() else null }
        override fun vpnPolicy(): Int = if (Build.VERSION.SDK_INT < 29) 0 else
            (if (isAlwaysOn) 1 else 0) or (if (isLockdownEnabled) 2 else 0)

        override fun foreground(notification: Notification) {
            main.post { if (!destroyed && !retired) showForeground(notification) }
        }

        override fun received(startId: Int) {
            main.post {
                if (destroyed || retired || endpoint !== this@Endpoint) return@post
                val command = awaitingReceipt.firstOrNull { it.id == startId } ?: return@post
                command.acknowledged = true
                while (awaitingReceipt.firstOrNull()?.acknowledged == true) awaitingReceipt.removeFirst()
            }
        }

        override fun finishStart(startId: Int) {
            main.post {
                if (!destroyed && !retired && endpoint === this@Endpoint && latestStart == startId && stopSelfResult(startId)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    foreground = false
                }
            }
        }
    }

    private companion object {
        val nativeLock = Any()
        var owner: Endpoint? = null
        val cleanup = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "farvater-core-cleanup").apply { isDaemon = true } }
    }
}

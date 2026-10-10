package app.farvater.vpn

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.farvater.App
import app.farvater.MainActivity
import app.farvater.R
import app.farvater.core.catalog.RuApps
import app.farvater.core.model.ProxyNode
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.data.DayTraffic
import app.farvater.data.UpdateWorker
import app.farvater.engine.TunBridge
import app.farvater.engine.XrayEngine
import app.farvater.ui.formatBytes
import app.farvater.ui.formatSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class FarvaterVpnService : VpnService() {
    companion object {
        const val ACTION_START = "app.farvater.action.START"
        const val ACTION_STOP = "app.farvater.action.STOP"
        const val EXTRA_RESTART = "app.farvater.extra.RESTART"
        const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 7
        private const val PROBE_EVERY = 60
        private const val UPDATE_GAP_MS = 15 * 60 * 1000L

        fun start(context: Context, node: ProxyNode, restart: Boolean = false) {
            App.prefs.selectNode(node)
            context.startForegroundService(
                Intent(context, FarvaterVpnService::class.java).setAction(ACTION_START).putExtra(EXTRA_RESTART, restart),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FarvaterVpnService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    private var monitor: Job? = null
    private var lastNotificationText: String? = null
    private var lastUpdateAttempt = 0L
    @Volatile private var dayTraffic: DayTraffic? = null

    private val screenOn = MutableStateFlow(true)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn.value = intent.action != Intent.ACTION_SCREEN_OFF
        }
    }

    override fun onCreate() {
        super.onCreate()
        screenOn.value = getSystemService(PowerManager::class.java).isInteractive
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private val probeClient by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayConfigBuilder.SOCKS_PORT)))
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        val node = App.prefs.lastNode
        startForegroundCompat(notification(node?.name ?: "Подключение", "Поднимаю туннель"))
        if (node == null) {
            fail("Сначала выберите сервер в приложении", null)
            return START_NOT_STICKY
        }
        val restart = intent?.getBooleanExtra(EXTRA_RESTART, false) ?: false
        scope.launch { startVpn(node, restart) }
        return START_STICKY
    }

    private fun startVpn(node: ProxyNode, restart: Boolean) {
        synchronized(this) {
            val current = VpnBus.state.value
            if (!restart && tun != null && current is VpnState.Connected && current.node == node) {
                updateNotification(node.name, "Подключено")
                return
            }
            connectLocked(node)
        }
    }

    private fun connectLocked(node: ProxyNode) {
        val previous = tun
        tun = null
        teardown()
        VpnBus.state.value = VpnState.Connecting(node)
        val settings = App.prefs.settings

        fun abort(message: String) {
            runCatching { previous?.close() }
            fail(message, node)
        }

        val config = XrayConfigBuilder.build(node, settings.directRuServices, encryptedDns = settings.encryptedDns)
        XrayEngine.start(config).onFailure { return abort("Ядро не запустилось: ${it.message}") }

        val builder = Builder()
            .setSession(node.name)
            .setMtu(settings.mtu)
            .addAddress("10.10.14.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
        runCatching { builder.addDisallowedApplication(packageName) }
        val bypass = if (settings.directRuServices) settings.bypassApps + RuApps.packages else settings.bypassApps
        bypass.forEach { pkg -> runCatching { builder.addDisallowedApplication(pkg) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val fd = builder.establish() ?: return abort("Нет разрешения на VPN")
        tun = fd
        runCatching { previous?.close() }
        TunBridge.start(this, fd.fd, settings.mtu).onFailure { return fail("TUN не запустился: ${it.message}", node) }

        VpnBus.state.value = VpnState.Connected(node, System.currentTimeMillis())
        updateNotification(node.name, "Подключено")
        monitor = scope.launch { monitorLoop(node) }
    }

    private suspend fun monitorLoop(node: ProxyNode) {
        var base = TunBridge.stats()?.takeIf { it.size >= 4 }?.let { longArrayOf(it[1], it[3]) }
        var lastTx = -1L
        var lastRx = -1L
        var counted = 0L
        var day = App.prefs.dayTraffic
        dayTraffic = day
        var tick = 0
        var failures = 0
        val history = ArrayDeque<Long>()
        while (currentCoroutineContext().isActive) {
            if (!screenOn.value) {
                VpnBus.traffic.value = VpnBus.traffic.value.copy(upBps = 0, downBps = 0)
                App.prefs.dayTraffic = day
                screenOn.first { it }
                lastTx = -1L
                lastRx = -1L
                tick = PROBE_EVERY - 3
                maybeCheckUpdates()
            }
            delay(1000)
            tick++
            TunBridge.stats()?.takeIf { it.size >= 4 }?.let { s ->
                val b = base ?: longArrayOf(s[1], s[3]).also { base = it }
                val upTotal = (s[1] - b[0]).coerceAtLeast(0)
                val downTotal = (s[3] - b[1]).coerceAtLeast(0)
                val today = LocalDate.now().toEpochDay()
                if (day.day != today) day = DayTraffic(today, 0)
                day = day.copy(bytes = day.bytes + (upTotal + downTotal - counted).coerceAtLeast(0))
                counted = upTotal + downTotal
                dayTraffic = day
                var up = 0L
                var down = 0L
                if (lastTx >= 0) {
                    up = (s[1] - lastTx).coerceAtLeast(0)
                    down = (s[3] - lastRx).coerceAtLeast(0)
                    history.addLast(up + down)
                    if (history.size > 60) history.removeFirst()
                    VpnBus.speedHistory.value = history.toList()
                }
                VpnBus.traffic.value = Traffic(up, down, upTotal, downTotal, day.bytes)
                lastTx = s[1]
                lastRx = s[3]
            }
            if (tick % 30 == 0) App.prefs.dayTraffic = day
            if (tick % 5 == 0) {
                val t = VpnBus.traffic.value
                if (failures >= VpnBus.STALL_CHECKS) {
                    updateNotification(node.name, "Узел не отвечает")
                } else {
                    val speed = "↓ ${formatSpeed(t.downBps)}   ↑ ${formatSpeed(t.upBps)}"
                    updateNotification(
                        node.name,
                        speed,
                        sub = "сегодня ${formatBytes(t.today)}",
                        details = "$speed\nЗа сеанс ${formatBytes(t.upTotal + t.downTotal)}, за сегодня ${formatBytes(t.today)}",
                    )
                }
            }
            if (tick % PROBE_EVERY == 0) {
                val ok = probe()
                if (!currentCoroutineContext().isActive) return
                failures = if (ok) 0 else failures + 1
                VpnBus.failedChecks.value = failures
                maybeCheckUpdates()
            }
        }
    }

    private fun maybeCheckUpdates() {
        val now = System.currentTimeMillis()
        if (now - maxOf(App.prefs.lastUpdateCheck, lastUpdateAttempt) < UPDATE_GAP_MS) return
        lastUpdateAttempt = now
        scope.launch { UpdateWorker.checkAndNotify(applicationContext) }
    }

    private fun probe(): Boolean = runCatching {
        val request = Request.Builder().url(App.prefs.settings.testUrl).build()
        probeClient.newCall(request).execute().use { it.code == 204 || it.code == 200 }
    }.getOrDefault(false)

    private fun teardown() {
        monitor?.cancel()
        monitor = null
        dayTraffic?.let { App.prefs.dayTraffic = it }
        dayTraffic = null
        TunBridge.stop()
        XrayEngine.stop()
        runCatching { tun?.close() }
        tun = null
        VpnBus.traffic.value = Traffic()
        VpnBus.speedHistory.value = emptyList()
        VpnBus.failedChecks.value = 0
    }

    private fun stopVpn() {
        synchronized(this) { teardown() }
        VpnBus.state.value = VpnState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String, node: ProxyNode?) {
        teardown()
        VpnBus.state.value = VpnState.Failed(message, node)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() = stopVpn()

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        synchronized(this) { teardown() }
        scope.cancel()
        if (VpnBus.state.value !is VpnState.Failed) VpnBus.state.value = VpnState.Idle
        super.onDestroy()
    }

    private fun notification(title: String, text: String, sub: String? = null, details: String? = null): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, FarvaterVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null as Icon?, "Отключить", stop).build())
            .apply {
                if (sub != null) setSubText(sub)
                if (details != null) setStyle(Notification.BigTextStyle().bigText(details))
            }
            .build()
    }

    private fun updateNotification(title: String, text: String, sub: String? = null, details: String? = null) {
        val key = "$title\n$text\n$sub\n$details"
        if (key == lastNotificationText) return
        lastNotificationText = key
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, text, sub, details))
    }

    private fun startForegroundCompat(n: Notification) {
        lastNotificationText = null
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }
}

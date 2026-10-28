package app.farvater.vpn

import app.farvater.data.userMessage
import app.farvater.data.localizedString
import app.farvater.data.AppLanguages
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.farvater.App
import app.farvater.FarvaterActivity
import app.farvater.R
import app.farvater.core.catalog.RuApps
import app.farvater.core.model.NetMode
import app.farvater.core.model.NetModeChoice
import app.farvater.core.model.ProxyNode
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.data.DayTraffic
import app.farvater.data.UpdateWorker
import app.farvater.engine.DohProbe
import app.farvater.engine.Failover
import app.farvater.engine.ModeStep
import app.farvater.engine.ModeSwitch
import app.farvater.engine.NetModeDetector
import app.farvater.engine.NodeTester
import app.farvater.engine.TunBridge
import app.farvater.engine.XrayEngine
import app.farvater.ui.formatBytes
import app.farvater.ui.formatSpeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class FarvaterVpnService : VpnService() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguages.wrap(base))
    }

    companion object {
        const val ACTION_START = "app.farvater.action.START"
        const val ACTION_STOP = "app.farvater.action.STOP"
        const val EXTRA_RESTART = "app.farvater.extra.RESTART"
        const val EXTRA_CHECK_MODE = "app.farvater.extra.CHECK_MODE"
        private const val ACTION_CHECK_MODE = "app.farvater.action.CHECK_MODE"
        private const val EXTRA_MODE = "app.farvater.extra.MODE"
        const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 7
        private const val PROBE_EVERY = 60
        private val EARLY_PROBES = setOf(5, 15, 30)
        private const val UPDATE_GAP_MS = 15 * 60 * 1000L
        private const val NETWORK_SETTLE_MS = 4_000L
        private val lifecycleQueue = Executors.newSingleThreadExecutor { task ->
            Thread(task, "farvater-vpn").apply { isDaemon = true }
        }.asCoroutineDispatcher()
        private var engineOwner: FarvaterVpnService? = null

        fun start(context: Context, node: ProxyNode, restart: Boolean = false, checkMode: Boolean = false) {
            App.prefs.selectNode(node)
            context.startForegroundService(
                Intent(context, FarvaterVpnService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_RESTART, restart)
                    .putExtra(EXTRA_CHECK_MODE, checkMode),
            )
        }

        fun recheckMode(context: Context, known: NetMode? = null) {
            runCatching {
                context.startService(
                    Intent(context, FarvaterVpnService::class.java)
                        .setAction(ACTION_CHECK_MODE)
                        .putExtra(EXTRA_MODE, known?.name),
                )
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FarvaterVpnService::class.java).setAction(ACTION_STOP))
        }
    }

    private val lifecycleDispatcher: CoroutineDispatcher = lifecycleQueue
    private val commandLock = Any()
    private var latestStartId = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    private var monitor: Job? = null
    private var lastNotificationText: String? = null
    private var lastUpdateAttempt = 0L
    @Volatile private var stopped = false
    @Volatile private var startGeneration = 0L
    @Volatile private var modeJob: Job? = null
    @Volatile private var lastModeSwitch = 0L
    @Volatile private var failoverJob: Job? = null
    @Volatile private var lastFailover = 0L
    private val failedBackups = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var dayTraffic: DayTraffic? = null

    private val trafficDay: () -> Long = { LocalDate.now().toEpochDay() }
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = synchronized(commandLock) {
        latestStartId = startId
        if (intent?.action == ACTION_STOP) {
            App.prefs.vpnWasOn = false
            stopVpn()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CHECK_MODE) {
            val vpn = VpnBus.state.value
            if (vpn is VpnState.Connected || vpn is VpnState.Connecting) {
                val hint = intent.getStringExtra(EXTRA_MODE)?.let { runCatching { NetMode.valueOf(it) }.getOrNull() }
                checkMode(replace = hint != null, known = hint)
                return START_STICKY
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val node = App.prefs.lastNode
        startForegroundCompat(notification(node?.name ?: localizedString(R.string.settings_connection), localizedString(R.string.tunnel_starting)))
        val restart = intent?.getBooleanExtra(EXTRA_RESTART, false) ?: false
        val fromSystem = intent?.action != ACTION_START || intent?.getBooleanExtra(EXTRA_CHECK_MODE, false) == true
        val generation = ++startGeneration
        stopped = false
        modeJob?.cancel()
        failoverJob?.cancel()
        scope.launch(lifecycleDispatcher) {
            synchronized(this@FarvaterVpnService) {
                if (stopped || generation != startGeneration) return@launch
                engineOwner = this@FarvaterVpnService
                if (node == null) {
                    fail(localizedString(R.string.choose_node_first), null, generation)
                    return@launch
                }
                startVpn(node, restart, generation = generation)
                if (fromSystem && isCurrent(generation)) checkMode(NETWORK_SETTLE_MS)
            }
        }
        return START_STICKY
    }

    private fun isCurrent(generation: Long) = !stopped && generation == startGeneration && scope.isActive

    private fun startVpn(node: ProxyNode, restart: Boolean, fallback: ProxyNode? = null, generation: Long = startGeneration) {
        synchronized(this) {
            if (!isCurrent(generation)) return
            if (fallback != null) {
                val current = when (val state = VpnBus.state.value) {
                    is VpnState.Connected -> state.node
                    is VpnState.Connecting -> state.node
                    else -> null
                }
                if (current != fallback || !App.prefs.allowedForAuto(node) || !App.prefs.replaceSelection(fallback.id, node)) return
            }
            engineOwner = this
            val current = VpnBus.state.value
            if (!restart && tun != null && monitor?.isActive == true && current is VpnState.Connected && current.node == node) {
                updateNotification(node.name, localizedString(R.string.state_connected))
                return
            }
            connectLocked(node, fallback, generation)
        }
    }

    private fun connectLocked(node: ProxyNode, fallback: ProxyNode? = null, generation: Long = startGeneration) {
        val previous = tun
        tun = null
        teardown()
        VpnBus.state.value = VpnState.Connecting(node)
        val settings = App.prefs.settings

        fun cancelled(): Boolean {
            if (isCurrent(generation)) return false
            runCatching { previous?.close() }
            teardown()
            return true
        }

        fun abort(message: String) {
            if (cancelled()) return
            if (fallback != null && App.prefs.replaceSelection(node.id, fallback)) {
                tun = previous
                return connectLocked(fallback, generation = generation)
            }
            runCatching { previous?.close() }
            fail(message, node, generation)
        }

        val config = XrayConfigBuilder.build(
            node,
            settings.directRuServices,
            encryptedDns = settings.encryptedDns,
            profile = App.prefs.profileFor(node.id),
        )
        XrayEngine.start(config).onFailure { return abort(localizedString(R.string.xray_start_failed, it.userMessage(this))) }

        if (cancelled()) return

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

        val fd = builder.establish() ?: return abort(localizedString(R.string.vpn_permission_missing))
        tun = fd
        runCatching { previous?.close() }
        TunBridge.start(this, fd.fd, settings.mtu).onFailure {
            if (cancelled()) return
            if (fallback != null && App.prefs.replaceSelection(node.id, fallback)) {
                return connectLocked(fallback, generation = generation)
            }
            return fail(localizedString(R.string.tun_start_failed, it.userMessage(this)), node, generation)
        }

        if (cancelled()) return
        synchronized(commandLock) {
            if (!isCurrent(generation)) return
            VpnBus.state.value = VpnState.Connected(node, System.currentTimeMillis())
            App.prefs.vpnWasOn = true
            updateNotification(node.name, localizedString(R.string.state_connected))
            monitor = scope.launch { monitorLoop(node) }
            if (networkCallback == null) watchNetwork()
        }
    }

    private suspend fun monitorLoop(node: ProxyNode) = coroutineScope {
        var base = TunBridge.stats()?.takeIf { it.size >= 4 }?.let { longArrayOf(it[1], it[3]) }
        var lastTx = -1L
        var lastRx = -1L
        var counted = 0L
        var day = App.prefs.dayTraffic
        dayTraffic = day
        var tick = 0
        var failures = 0
        var lastProbe = 0
        var check: Job? = null
        val outcome = AtomicReference<Boolean?>(null)
        val history = ArrayDeque<Long>()
        fun countTraffic(s: LongArray): Pair<Long, Long> {
            val b = base ?: longArrayOf(s[1], s[3]).also { base = it }
            val upTotal = (s[1] - b[0]).coerceAtLeast(0)
            val downTotal = (s[3] - b[1]).coerceAtLeast(0)
            val today = trafficDay()
            if (day.day != today) {
                App.prefs.dayTraffic = day
                day = App.prefs.trafficForDay(today)
            }
            day = day.copy(bytes = day.bytes + (upTotal + downTotal - counted).coerceAtLeast(0))
            counted = upTotal + downTotal
            dayTraffic = day
            return upTotal to downTotal
        }
        while (currentCoroutineContext().isActive) {
            if (!screenOn.value) {
                VpnBus.traffic.value = VpnBus.traffic.value.copy(upBps = 0, downBps = 0)
                App.prefs.dayTraffic = day
                if (withTimeoutOrNull(30_000) { screenOn.first { it } } != true) {
                    TunBridge.stats()?.takeIf { it.size >= 4 }?.let { s ->
                        val (upTotal, downTotal) = countTraffic(s)
                        VpnBus.traffic.value = Traffic(0, 0, upTotal, downTotal, day.bytes)
                    }
                    App.prefs.dayTraffic = day
                    continue
                }
                lastTx = -1L
                lastRx = -1L
                tick = PROBE_EVERY - 3
                maybeCheckUpdates()
            }
            delay(1000)
            tick++
            TunBridge.stats()?.takeIf { it.size >= 4 }?.let { s ->
                val (upTotal, downTotal) = countTraffic(s)
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
                    updateNotification(node.name, localizedString(R.string.node_unresponsive))
                } else {
                    val speed = "↓ ${formatSpeed(AppLanguages.wrap(this@FarvaterVpnService), t.downBps)}   ↑ ${formatSpeed(AppLanguages.wrap(this@FarvaterVpnService), t.upBps)}"
                    updateNotification(
                        node.name,
                        speed,
                        sub = localizedString(R.string.notification_today, formatBytes(AppLanguages.wrap(this@FarvaterVpnService), t.today)),
                        details = localizedString(R.string.notification_traffic, speed, formatBytes(AppLanguages.wrap(this@FarvaterVpnService), t.upTotal + t.downTotal), formatBytes(AppLanguages.wrap(this@FarvaterVpnService), t.today)),
                    )
                }
            }
            if (check?.isActive != true && Failover.probeDue(tick, lastProbe, failures, PROBE_EVERY, EARLY_PROBES, VpnBus.STALL_CHECKS)) {
                lastProbe = tick
                check = launch { outcome.set(probe(node)) }
            }
            outcome.getAndSet(null)?.let { ok ->
                if (!currentCoroutineContext().isActive) return@coroutineScope
                failures = if (ok) 0 else failures + 1
                if (ok) failedBackups.clear()
                VpnBus.failedChecks.value = failures
                if (ModeSwitch.recheckAfter(failures, VpnBus.STALL_CHECKS)) {
                    if (App.prefs.settings.autoSwitch) failover(node) else checkMode()
                }
                maybeCheckUpdates()
            }
        }
    }

    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var current: Network? = null

            override fun onAvailable(network: Network) {
                val changed = current != null && current != network
                current = network
                if (changed) checkMode(NETWORK_SETTLE_MS, replace = true)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }.onSuccess { networkCallback = callback }
    }

    private fun unwatchNetwork() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
    }

    private fun checkMode(delayMs: Long = 0, replace: Boolean = false, known: NetMode? = null) {
        if (modeJob?.isActive == true) {
            if (!replace) return
            modeJob?.cancel()
        }
        val generation = startGeneration
        modeJob = scope.launch {
            delay(delayMs)
            var hint = known
            repeat(ModeSwitch.ATTEMPTS) {
                val settings = App.prefs.settings
                val auto = settings.netMode == NetModeChoice.AUTO
                val mode = if (auto) hint ?: NetModeDetector.detect() else NetMode.valueOf(settings.netMode.name)
                hint = null
                if (auto && mode != null) {
                    App.prefs.lastNetMode = mode.name
                    VpnBus.netMode.tryEmit(mode)
                }
                if (!settings.modeNodes) return@launch
                val current = when (val s = VpnBus.state.value) {
                    is VpnState.Connected -> s.node
                    is VpnState.Connecting -> s.node
                    else -> return@launch
                }
                val target = mode?.let { App.prefs.nodeForMode(it) }?.takeIf { App.prefs.allowedForAuto(it) }
                when (val step = ModeSwitch.step(mode, target?.id, current.id, System.currentTimeMillis(), lastModeSwitch)) {
                    ModeStep.Done -> return@launch
                    is ModeStep.Wait -> delay(step.ms)
                    ModeStep.Switch -> {
                        val next = target ?: return@launch
                        if (!runInterruptible { NodeTester.test(next, settings.testUrl) }.alive) return@launch
                        ensureActive()
                        if (App.prefs.nodeForMode(mode ?: return@launch)?.id != next.id) return@launch
                        withContext(lifecycleDispatcher) {
                            ensureActive()
                            if (!isCurrent(generation) || App.prefs.nodeForMode(mode)?.id != next.id) return@withContext
                            val before = VpnBus.state.value
                            startVpn(next, restart = true, fallback = current, generation = generation)
                            if (VpnBus.state.value != before) lastModeSwitch = System.currentTimeMillis()
                        }
                        return@launch
                    }
                }
            }
        }
    }

    private fun failover(stalled: ProxyNode) {
        if (failoverJob?.isActive == true) return
        val generation = startGeneration
        failoverJob = scope.launch {
            checkMode()
            while (true) {
                val job = modeJob ?: break
                if (!job.isActive) break
                job.join()
            }
            val current = (VpnBus.state.value as? VpnState.Connected)?.node ?: return@launch
            if (current.id != stalled.id || VpnBus.failedChecks.value < VpnBus.STALL_CHECKS) return@launch
            if (System.currentTimeMillis() - lastFailover < Failover.GAP_MS) return@launch
            val backups = App.prefs.backupNodes
            val choice = App.prefs.settings.netMode
            val mode = if (choice == NetModeChoice.AUTO) App.prefs.lastNetMode else choice.name
            if (backups.mode != "ANY" && mode != null && backups.mode != mode) return@launch
            val url = App.prefs.settings.testUrl
            for (next in Failover.order(backups.nodes, current, failedBackups).filter { App.prefs.allowedForAuto(it) }.take(Failover.TRIES)) {
                updateNotification(current.name, localizedString(R.string.notification_testing_next, next.name))
                val alive = runInterruptible { NodeTester.test(next, url) }.alive
                ensureActive()
                if (!alive) {
                    failedBackups += next.id
                    continue
                }
                val still = (VpnBus.state.value as? VpnState.Connected)?.node
                if (still?.id != current.id || VpnBus.failedChecks.value < VpnBus.STALL_CHECKS) return@launch
                withContext(lifecycleDispatcher) {
                    ensureActive()
                    if (!isCurrent(generation) || VpnBus.failedChecks.value < VpnBus.STALL_CHECKS) return@withContext
                    startVpn(next, restart = true, fallback = current, generation = generation)
                    if ((VpnBus.state.value as? VpnState.Connected)?.node?.id == next.id) {
                        lastFailover = System.currentTimeMillis()
                        failedBackups.clear()
                        val known = mode?.let { m -> runCatching { NetMode.valueOf(m) }.getOrNull() }
                        if (known != null && App.prefs.settings.modeNodes) App.prefs.rememberForMode(known, next)
                    }
                }
                return@launch
            }
        }
    }

    private fun maybeCheckUpdates() {
        val now = System.currentTimeMillis()
        if (now - maxOf(App.prefs.lastUpdateCheck, lastUpdateAttempt) < UPDATE_GAP_MS) return
        lastUpdateAttempt = now
        scope.launch { UpdateWorker.checkAndNotify(applicationContext) }
    }

    private suspend fun probe(node: ProxyNode): Boolean {
        val web = NodeTester.throughTunnel(node, App.prefs.settings.testUrl) != null
        if (!web || !App.prefs.settings.encryptedDns) return web
        return DohProbe.firstAnswering(NodeTester.tunnelClient, XrayConfigBuilder.dnsOrder(App.prefs.profileFor(node.id)?.dns)) != null
    }

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

    private fun stopVpn(): Unit = synchronized(commandLock) {
        stopped = true
        val generation = ++startGeneration
        val startId = latestStartId
        unwatchNetwork()
        modeJob?.cancel()
        failoverJob?.cancel()
        monitor?.cancel()
        scope.launch(NonCancellable + lifecycleDispatcher) {
            synchronized(this@FarvaterVpnService) {
                if (generation != startGeneration) return@launch
                if (engineOwner === this@FarvaterVpnService) teardown()
                synchronized(commandLock) {
                    if (generation != startGeneration) return@launch
                    VpnBus.state.value = VpnState.Idle
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        }
    }

    private fun fail(message: String, node: ProxyNode?, generation: Long) {
        teardown()
        synchronized(commandLock) {
            if (!isCurrent(generation)) return
            App.prefs.vpnWasOn = false
            VpnBus.state.value = VpnState.Failed(message, node)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(latestStartId)
        }
    }

    override fun onRevoke(): Unit = synchronized(commandLock) {
        App.prefs.vpnWasOn = false
        stopVpn()
    }

    override fun onDestroy() {
        synchronized(commandLock) {
            stopped = true
            startGeneration++
        }
        runCatching { unregisterReceiver(screenReceiver) }
        unwatchNetwork()
        scope.cancel()
        scope.launch(NonCancellable + lifecycleDispatcher) {
            synchronized(this@FarvaterVpnService) {
                if (engineOwner !== this@FarvaterVpnService) return@launch
                teardown()
                engineOwner = null
                if (VpnBus.state.value !is VpnState.Failed) VpnBus.state.value = VpnState.Idle
            }
        }
        super.onDestroy()
    }

    private fun notification(title: String, text: String, sub: String? = null, details: String? = null): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, FarvaterActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
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
            .addAction(Notification.Action.Builder(null as Icon?, localizedString(R.string.notification_disconnect), stop).build())
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

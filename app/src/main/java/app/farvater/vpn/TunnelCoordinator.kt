package app.farvater.vpn

import app.farvater.core.xray.LocalProxyAuth
import android.os.IBinder
import app.farvater.data.localizedString
import app.farvater.data.AppLanguages
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.farvater.App
import app.farvater.FarvaterActivity
import app.farvater.data.notificationIcon
import app.farvater.R
import app.farvater.data.effectiveBypassApps
import app.farvater.core.model.NetMode
import app.farvater.core.model.NetModeChoice
import app.farvater.core.model.ProxyNode
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.data.UpdateWorker
import app.farvater.engine.DohProbe
import app.farvater.engine.BackupCooldowns
import app.farvater.engine.ProbeNetwork
import app.farvater.engine.AutomaticContext
import app.farvater.data.GalsPolicy
import app.farvater.data.Prefs
import app.farvater.engine.GalsMonitor
import app.farvater.engine.GalsProbe
import app.farvater.engine.ProbeLane
import app.farvater.engine.AutomaticReason
import app.farvater.engine.Failover
import app.farvater.engine.ModeStep
import app.farvater.engine.ModeSwitch
import app.farvater.engine.NetModeDetector
import app.farvater.engine.NodeTester
import app.farvater.engine.TunBridge
import app.farvater.ui.formatBytes
import app.farvater.ui.formatSpeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class TunnelCoordinator : android.app.Service() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguages.wrap(base))
    }

    companion object {
        const val ACTION_START = "app.farvater.action.START"
        const val ACTION_STOP = "app.farvater.action.STOP"
        const val EXTRA_RESTART = "app.farvater.extra.RESTART"
        const val EXTRA_CHECK_MODE = "app.farvater.extra.CHECK_MODE"
        private const val ACTION_CHECK_MODE = "app.farvater.action.CHECK_MODE"
        internal const val EXTRA_GRANT = "automation-grant"
        @Volatile private var instance = java.lang.ref.WeakReference<TunnelCoordinator>(null)
        fun lockdownStatus(): Boolean? = if (Build.VERSION.SDK_INT >= 29) instance.get()?.policy()?.let { it and 2 != 0 } else null

        private fun externalAllowed(context: Context, active: Boolean): Boolean {
            if (active) return App.prefs.lastNode != null && VpnService.prepare(context) == null &&
                App.prefs.settings.acceptedLegalVersion >= app.farvater.data.LEGAL_VERSION
            val service = instance.get()
            return Build.VERSION.SDK_INT < 29 || service == null || service.policy() == 0
        }

        internal fun external(context: Context, active: Boolean, grant: ByteArray): Boolean {
            if (!runCatching { externalAllowed(context, active) }.getOrDefault(false)) return false
            val state = VpnBus.state.value
            val intended = VpnBus.request.value?.active ?: (state is VpnState.Connected || state is VpnState.Connecting)
            if (active == intended) {
                if (!active) {
                    VpnBus.stopEpoch.incrementAndGet()
                    VpnBus.automaticEpoch.incrementAndGet()
                }
                return true
            }
            val action = if (active) ACTION_START else ACTION_STOP
            send(context, Intent(context, FarvaterVpnService::class.java).setAction(action).putExtra(EXTRA_GRANT, grant), active)
            return true
        }

        private const val EXTRA_MODE = "app.farvater.extra.MODE"
        private const val EXTRA_AUTOMATIC = "app.farvater.extra.AUTOMATIC"
        private const val EXTRA_REQUEST = "app.farvater.extra.REQUEST"
        const val CHANNEL = "vpn"
        internal const val NOTIFICATION_ID = 7
        private const val PROBE_EVERY = 60
        private val EARLY_PROBES = setOf(5, 15, 30)
        private const val UPDATE_GAP_MS = 15 * 60 * 1000L
        private const val NETWORK_SETTLE_MS = 4_000L
        private val lifecycleQueue = Executors.newSingleThreadExecutor { task ->
            Thread(task, "farvater-vpn").apply { isDaemon = true }
        }.asCoroutineDispatcher()
        private var engineOwner: TunnelCoordinator? = null
        private val requestIds = java.util.concurrent.atomic.AtomicLong()
        private val requestLock = Any()

        private fun request(active: Boolean, automatic: AutomaticContext? = null): VpnRequest = synchronized(requestLock) {
            if (!active) VpnBus.stopEpoch.incrementAndGet()
            VpnRequest(requestIds.incrementAndGet(), active, automatic).also { VpnBus.request.value = it }
        }

        private fun send(context: Context, intent: Intent, active: Boolean, automatic: AutomaticContext? = null) {
            val (previous, requested) = synchronized(requestLock) {
                val pending = VpnBus.request.value
                if (automatic != null && (pending != null || !automatic.sameCommandGeneration())) return
                pending to request(active, automatic)
            }
            intent.putExtra(EXTRA_REQUEST, requested.id)
            try {
                if (active) context.startForegroundService(intent) else context.startService(intent)
            } catch (error: RuntimeException) {
                VpnBus.request.compareAndSet(requested, previous)
                throw error
            }
        }

        private fun requestFrom(intent: Intent?, active: Boolean): VpnRequest {
            val id = intent?.getLongExtra(EXTRA_REQUEST, 0L) ?: 0L
            return if (id == 0L) request(active) else VpnRequest(id, active)
        }

        fun start(context: Context, node: ProxyNode, restart: Boolean = false, checkMode: Boolean = false) {
            VpnBus.automaticEpoch.incrementAndGet()
            if (!App.prefs.selectNode(node)) {
                if (VpnBus.state.value !is VpnState.Connected) VpnBus.state.value =
                    VpnState.Failed(context.getString(R.string.profile_storage_unavailable), node)
                return
            }
            send(
                context,
                Intent(context, FarvaterVpnService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_RESTART, restart)
                    .putExtra(EXTRA_CHECK_MODE, checkMode),
                active = true,
            )
        }

        fun startAutomatic(context: Context, checked: AutomaticContext, restart: Boolean) {
            if (!checked.valid(App.prefs, App.repo)) return
            val intent = Intent(context, FarvaterVpnService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_AUTOMATIC, true).putExtra(EXTRA_RESTART, restart)
            send(context, intent, active = true, automatic = checked)
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
            send(context, Intent(context, FarvaterVpnService::class.java).setAction(ACTION_STOP), active = false)
        }
    }

    @Volatile internal var endpoint: IVpnEndpoint? = null
    private var death: IBinder.DeathRecipient? = null
    @Volatile private var engineEndpoint: IVpnEndpoint? = null
    private var account: LocalProxyAuth.Account? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val commands = object : ITunnelControl.Stub() {
        override fun command(remote: IVpnEndpoint, intent: Intent?, startId: Int) {
            main.post {
                if (!attach(remote)) return@post
                onStartCommand(intent, 0, startId)
                if (intent?.action == ACTION_STOP || intent?.action == ACTION_START) {
                    scope.launch(lifecycleDispatcher) {
                        if (App.prefs.flushWrites()) runCatching { remote.received(startId) }
                    }
                } else runCatching { remote.received(startId) }
            }
        }

        override fun ended(remote: IVpnEndpoint, revoked: Boolean) {
            main.post {
                detach(remote, revoked)
            }
        }
    }

    private fun attach(remote: IVpnEndpoint): Boolean {
        val binder = remote.asBinder()
        if (endpoint?.asBinder() == binder && death != null) return true
        endpoint?.let { detach(it, false) }
        val recipient = IBinder.DeathRecipient { main.post { detach(remote, false) } }
        return try {
            binder.linkToDeath(recipient, 0)
            death = recipient
            endpoint = remote
            true
        } catch (_: android.os.RemoteException) {
            stopVpn()
            false
        }
    }

    private fun detach(remote: IVpnEndpoint, revoked: Boolean) {
        if (endpoint?.asBinder() != remote.asBinder()) return
        death?.let { runCatching { remote.asBinder().unlinkToDeath(it, 0) } }
        death = null
        endpoint = null
        if (revoked) onRevoke() else if (VpnBus.state.value !is VpnState.Failed) stopVpn()
    }
    override fun onBind(intent: Intent?): android.os.IBinder = commands

    private fun policy(): Int? = runCatching { endpoint?.vpnPolicy() }.getOrNull()

    private fun finishStart(id: Int) {
        runCatching { endpoint?.finishStart(id) }
    }

    private val lifecycleDispatcher: CoroutineDispatcher = lifecycleQueue
    private val commandLock = Any()
    private var latestStartId = 0
    private var activeRequest: VpnRequest? = null
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
    private val failedBackups = BackupCooldowns()
    @Volatile private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var trafficSession: SessionTraffic? = null

    private val trafficDay: () -> Long = { LocalDate.now().toEpochDay() }
    private val screenOn = MutableStateFlow(true)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn.value = intent.action != Intent.ACTION_SCREEN_OFF
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = java.lang.ref.WeakReference(this)
        screenOn.value = getSystemService(PowerManager::class.java).isInteractive
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = synchronized(commandLock) {
        latestStartId = startId
        if (intent == null && !App.prefs.vpnWasOn) {
            finishStart(startId)
            return START_NOT_STICKY
        }
        if (intent?.hasExtra(EXTRA_GRANT) == true) {
            val pending = VpnBus.request.value
            val id = intent.getLongExtra(EXTRA_REQUEST, 0L)
            val active = intent.action == ACTION_START
            val grant = AutomationAccess.current(this)
            if (pending?.id != id || grant == null ||
                !java.security.MessageDigest.isEqual(grant, intent.getByteArrayExtra(EXTRA_GRANT)) ||
                !runCatching { externalAllowed(this, active) }.getOrDefault(false)) {
                if (pending?.id == id) VpnBus.request.compareAndSet(pending, null)
                if (VpnBus.request.value == null && VpnBus.state.value !is VpnState.Connected && VpnBus.state.value !is VpnState.Connecting) {
                    if (active) {
                        startForegroundCompat(notification(localizedString(R.string.app_name), localizedString(R.string.automation_inactive)))
                    }
                    finishStart(startId)
                }
                val live = VpnBus.state.value is VpnState.Connected || VpnBus.state.value is VpnState.Connecting
                return if (!stopped && (VpnBus.request.value?.active ?: live)) START_STICKY else START_NOT_STICKY
            }
            if (!active) VpnBus.automaticEpoch.incrementAndGet()
        }
        if (intent?.action == ACTION_STOP) {
            activeRequest = requestFrom(intent, active = false)
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
            finishStart(startId)
            return START_NOT_STICKY
        }
        val automatic = intent?.getBooleanExtra(EXTRA_AUTOMATIC, false) == true
        val incoming = if (automatic) {
            val id = intent?.getLongExtra(EXTRA_REQUEST, 0L)
            VpnBus.request.value?.takeIf { it.id == id && it.automatic != null }
        } else requestFrom(intent, active = true)
        if (incoming == null) {
            if (VpnBus.request.value == null && VpnBus.state.value !is VpnState.Connected &&
                VpnBus.state.value !is VpnState.Connecting) {
                startForegroundCompat(notification(localizedString(R.string.settings_connection), localizedString(R.string.node_test_incomplete)))
                finishStart(startId)
            }
            return START_NOT_STICKY
        }
        activeRequest = incoming
        val checked = incoming.automatic
        val knownNode = checked?.node ?: App.prefs.loadedLastNode
        val title = knownNode?.let { App.prefs.loadedSettings?.nodeLabel(it) } ?: localizedString(R.string.settings_connection)
        startForegroundCompat(notification(title, localizedString(R.string.tunnel_starting)))
        val restart = intent?.getBooleanExtra(EXTRA_RESTART, false) ?: false
        val fromSystem = intent?.action != ACTION_START || intent?.getBooleanExtra(EXTRA_CHECK_MODE, false) == true
        val generation = ++startGeneration
        stopped = false
        modeJob?.cancel()
        failoverJob?.cancel()
        scope.launch(lifecycleDispatcher) {
            synchronized(this@TunnelCoordinator) {
                if (stopped || generation != startGeneration) return@launch
                if (checked != null && !currentAutomatic(incoming)) {
                    dismissAutomatic(incoming, generation)
                    return@launch
                }
                engineOwner = this@TunnelCoordinator
                if (!App.prefs.prepareProtectedData()) {
                    fail(localizedString(R.string.profile_storage_unavailable), knownNode, generation)
                    return@launch
                }
                val node = knownNode ?: App.prefs.lastNode
                if (node == null) {
                    fail(localizedString(R.string.choose_node_first), null, generation)
                    return@launch
                }
                if (checked != null) connectLocked(node, generation = generation, request = incoming)
                else startVpn(node, restart, generation = generation)
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
                if (!GalsPolicy.from(App.prefs.settings).legacy) return
                val current = when (val state = VpnBus.state.value) {
                    is VpnState.Connected -> state.node
                    is VpnState.Connecting -> state.node
                    else -> null
                }
                if (current != fallback || !availableBackup(node) || !App.prefs.replaceSelection(fallback.id, node)) return
            }
            engineOwner = this
            val current = VpnBus.state.value
            if (!restart && engineEndpoint?.asBinder() == endpoint?.asBinder() && tun != null && monitor?.isActive == true && current is VpnState.Connected && current.node == node) {
                updateNotification(App.prefs.settings.nodeLabel(node), localizedString(R.string.state_connected))
                synchronized(commandLock) {
                    if (isCurrent(generation)) VpnBus.request.compareAndSet(activeRequest, null)
                }
                return
            }
            connectLocked(node, fallback, generation)
        }
    }

    private fun currentAutomatic(request: VpnRequest): Boolean =
        VpnBus.request.value === request && request.automatic?.valid(App.prefs, App.repo) == true

    private fun dismissAutomatic(request: VpnRequest, generation: Long): Unit = synchronized(commandLock) {
        if (!isCurrent(generation)) return@synchronized
        VpnBus.request.compareAndSet(request, null)
        val connected = VpnBus.state.value as? VpnState.Connected
        if (connected != null) {
            updateNotification(App.prefs.settings.nodeLabel(connected.node), localizedString(R.string.state_connected))
        } else {
            VpnBus.state.value = VpnState.Idle
            App.prefs.vpnWasOn = false
            if (VpnBus.request.value == null) {
                finishStart(latestStartId)
            }
        }
    }

    private fun connectLocked(node: ProxyNode, fallback: ProxyNode? = null, generation: Long = startGeneration,
        request: VpnRequest? = null) {
        if (request != null && !currentAutomatic(request)) return dismissAutomatic(request, generation)
        val previous = tun
        tun = null
        teardown()
        VpnBus.state.value = VpnState.Connecting(node)
        val settings = App.prefs.settings
        if (App.prefs.protectedDataUnavailable) {
            runCatching { previous?.close() }
            return fail(localizedString(R.string.profile_storage_unavailable), node, generation)
        }

        fun cancelled(): Boolean {
            if (isCurrent(generation) && (request == null || currentAutomatic(request))) return false
            runCatching { previous?.close() }
            teardown()
            if (request != null) dismissAutomatic(request, generation)
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

        val credentials = LocalProxyAuth.Account()
        val config = try {
            XrayConfigBuilder.build(
                node, settings.directRuServices, account = credentials,
                probePort = app.farvater.core.xray.LocalProxyAuth.probePort,
                encryptedDns = settings.encryptedDns,
                automaticRoutes = settings.automaticRoutes,
                routeRules = settings.routeRules,
                profile = if (request == null) App.prefs.profileFor(node.id) else request.automatic?.connectionProfile,
            )
        } catch (error: RuntimeException) {
            return abort(localizedString(R.string.xray_start_failed, localizedString(R.string.import_invalid)))
        }
        val remote = endpoint ?: return abort(localizedString(R.string.vpn_interface_failed))
        engineEndpoint = remote
        val error = runCatching { app.farvater.engine.CorePayload.send(config, remote::startCore) }
            .getOrDefault(R.string.vpn_interface_failed)
        if (error != 0) return abort(localizedString(R.string.xray_start_failed, localizedString(error)))
        if (cancelled()) return
        account = credentials
        val alive = { endpoint?.asBinder() == remote.asBinder() && remote.asBinder().isBinderAlive }
        LocalProxyAuth.open(LocalProxyAuth.port, credentials, alive)
        LocalProxyAuth.open(LocalProxyAuth.probePort, credentials, alive)
        val fd = try {
            remote.establish(settings.nodeLabel(node), settings.mtu, settings.routeRules.any { ':' in it.target },
                settings.effectiveBypassApps.toTypedArray())
        } catch (_: SecurityException) {
            return abort(localizedString(R.string.vpn_permission_missing))
        } catch (_: Exception) {
            return abort(localizedString(R.string.vpn_interface_failed))
        } ?: return abort(localizedString(R.string.vpn_permission_missing))
        tun = fd
        runCatching { previous?.close() }
        if (cancelled()) return
        val bridge = runCatching {
            ParcelFileDescriptor.dup(fd.fileDescriptor).use { remote.startBridge(it, TunBridge.buildConfig(settings.mtu, credentials).toByteArray()) }
        }
        if (bridge.getOrDefault(false) != true) {
            if (cancelled()) return
            if (fallback != null && App.prefs.replaceSelection(node.id, fallback)) {
                return connectLocked(fallback, generation = generation)
            }
            return fail(localizedString(R.string.tun_start_failed, localizedString(R.string.error_tun_config)), node, generation)
        }

        if (cancelled()) return
        val traffic = newTraffic(remote)
        val committed = synchronized(commandLock) {
            if (!isCurrent(generation)) return
            if (request != null && (VpnBus.request.value !== request ||
                    request.automatic?.commit(App.prefs, App.repo) != true)) return@synchronized false
            VpnBus.state.value = VpnState.Connected(node, System.currentTimeMillis())
            VpnBus.request.compareAndSet(activeRequest, null)
            App.prefs.vpnWasOn = true
            updateNotification(App.prefs.settings.nodeLabel(node), localizedString(R.string.state_connected))
            trafficSession = traffic
            monitor = scope.launch { monitorLoop(node, traffic) }
            if (networkCallback == null) watchNetwork()
            true
        }
        if (!committed) {
            teardown()
            request?.let { dismissAutomatic(it, generation) }
        }
    }

    internal fun newTraffic(remote: IVpnEndpoint) = SessionTraffic(
        { runCatching { remote.counters() }.getOrNull() }, trafficDay, SystemClock::elapsedRealtime,
        App.prefs::trafficForDay, { App.prefs.dayTraffic = it },
    )

    internal suspend fun monitorLoop(node: ProxyNode, session: SessionTraffic) = coroutineScope {
        var tick = 0
        var failures = 0
        val groupMonitor = GalsMonitor(applicationContext, App.prefs, App.repo)
        var lastGroupCheck = 0
        var lastProbe = 0
        var check: Job? = null
        val outcome = AtomicReference<Boolean?>(null)
        val history = ArrayDeque<Long>()
        while (currentCoroutineContext().isActive) {
            delay(1000)
            tick++
            val visible = screenOn.value
            val monitoring = currentCoroutineContext()
            synchronized(session) {
                if (!monitoring.isActive) return@coroutineScope
                if (!visible) {
                    session.screenOff()
                    val traffic = VpnBus.traffic.value
                    if (traffic.upBps != 0L || traffic.downBps != 0L)
                        VpnBus.traffic.value = traffic.copy(upBps = 0, downBps = 0)
                }
                if (visible || tick % 30 == 0) {
                    session.sample(visible, flush = tick % 30 == 0)?.let { traffic ->
                        VpnBus.traffic.value = traffic
                        if (visible) {
                            history.addLast(traffic.upBps + traffic.downBps)
                            if (history.size > 60) history.removeFirst()
                            VpnBus.speedHistory.value = history.toList()
                        }
                    }
                }
            }
            if (visible && tick % 5 == 0) {
                val t = VpnBus.traffic.value
                if (failures >= VpnBus.STALL_CHECKS) {
                    updateNotification(App.prefs.settings.nodeLabel(node), localizedString(R.string.node_unresponsive))
                } else {
                    val speed = "↓ ${formatSpeed(AppLanguages.wrap(this@TunnelCoordinator), t.downBps)}   ↑ ${formatSpeed(AppLanguages.wrap(this@TunnelCoordinator), t.upBps)}"
                    updateNotification(
                        App.prefs.settings.nodeLabel(node),
                        speed,
                        sub = localizedString(R.string.notification_today, formatBytes(AppLanguages.wrap(this@TunnelCoordinator), t.today)),
                        details = localizedString(R.string.notification_traffic, speed, formatBytes(AppLanguages.wrap(this@TunnelCoordinator), t.upTotal + t.downTotal), formatBytes(AppLanguages.wrap(this@TunnelCoordinator), t.today)),
                    )
                }
            }
            val policy = GalsPolicy.from(App.prefs.settings)
            if (check?.isActive != true) {
                if (policy.legacy && Failover.probeDue(tick, lastProbe, failures, PROBE_EVERY, EARLY_PROBES, VpnBus.STALL_CHECKS)) {
                    lastProbe = tick
                    check = launch { outcome.set(probe(node)) }
                } else if (!policy.legacy && policy.group != null &&
                    (tick == 5 || tick - lastGroupCheck >= policy.group.intervalSeconds)) {
                    lastGroupCheck = tick
                    val session = VpnBus.state.value as? VpnState.Connected
                    if (session?.node == node) check = launch {
                        val ok = groupMonitor.check(session) { startAutomatic(applicationContext, it, restart = true) }
                        if (policy.sameBehavior(GalsPolicy.from(App.prefs.settings))) outcome.set(ok)
                    }
                }
            }
            outcome.getAndSet(null)?.let { ok ->
                if (!currentCoroutineContext().isActive) return@coroutineScope
                failures = if (ok) 0 else failures + 1
                if (ok) failedBackups.clear()
                VpnBus.failedChecks.value = failures
                if (policy.legacy && ModeSwitch.recheckAfter(failures, VpnBus.STALL_CHECKS)) {
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

            private var observed = false

            override fun onLost(network: Network) {
                if (networkCallback !== this) return
                if (current == network) {
                    current = null
                    VpnBus.networkEpoch.incrementAndGet()
                }
            }

            override fun onAvailable(network: Network) {
                if (networkCallback !== this) return
                val changed = observed && current != network
                observed = true
                current = network
                if (changed) {
                    VpnBus.networkEpoch.incrementAndGet()
                    checkMode(NETWORK_SETTLE_MS, replace = true)
                }
            }
        }
        networkCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback) }.onFailure { networkCallback = null }
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
                        val policy = GalsPolicy.from(settings)
                        if (!policy.legacy) {
                            val group = policy.group ?: return@launch
                            if (next.sourceId !in App.repo.snapshots.value) App.repo.loadSnapshot(next.sourceId)
                            val checked = AutomaticContext.capture(ProbeNetwork(applicationContext), next, App.prefs, App.repo, mode, policy,
                                AutomaticReason.MODE_SWITCH) ?: return@launch
                            val result = GalsProbe.findInLane(next, checked.profile, mode == NetMode.BLACK, settings.encryptedDns,
                                group, settings.testUrl, 2, ProbeLane.RECOVERY)
                            val profile = result.profile ?: return@launch
                            ensureActive()
                            synchronized(Prefs.selectionLock) {
                                if (!isCurrent(generation) || App.prefs.lastNode != current || !checked.valid(App.prefs, App.repo) ||
                                    App.prefs.nodeForMode(mode) != next || VpnBus.request.value != null) return@launch
                                startAutomatic(applicationContext, checked.withProbeResult(profile), restart = true)
                                lastModeSwitch = System.currentTimeMillis()
                            }
                            return@launch
                        }
                        if (!runInterruptible { NodeTester.testRecovery(next, settings.testUrl, App.prefs.profileFor(next.id)) }.alive) return@launch
                        ensureActive()
                        if (App.prefs.nodeForMode(mode ?: return@launch)?.id != next.id) return@launch
                        withContext(lifecycleDispatcher) {
                            ensureActive()
                            if (!isCurrent(generation) || !policy.sameBehavior(GalsPolicy.from(App.prefs.settings)) || App.prefs.nodeForMode(mode) != next) return@withContext
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

    private fun availableBackup(node: ProxyNode): Boolean {
        if (!App.prefs.allowedForAuto(node)) return false
        val snapshot = App.repo.snapshots.value[node.sourceId] ?: App.repo.loadCached(node.sourceId)
        return snapshot == null || node in snapshot.nodes
    }

    private fun failover(stalled: ProxyNode) {
        if (failoverJob?.isActive == true) return
        val generation = startGeneration
        failoverJob = scope.launch {
            checkMode()
            val current = (VpnBus.state.value as? VpnState.Connected)?.node ?: return@launch
            if (current.id != stalled.id || VpnBus.failedChecks.value < VpnBus.STALL_CHECKS) return@launch
            if (System.currentTimeMillis() - lastFailover < Failover.GAP_MS) return@launch
            val backups = App.prefs.backupNodes
            val choice = App.prefs.settings.netMode
            val mode = if (choice == NetModeChoice.AUTO) App.prefs.lastNetMode else choice.name
            if (backups.mode != "ANY" && mode != null && backups.mode != mode) return@launch
            val url = App.prefs.settings.testUrl
            for (next in Failover.order(backups.nodes, current, failedBackups.excluded(SystemClock.elapsedRealtime())).filter(::availableBackup).take(Failover.TRIES)) {
                updateNotification(App.prefs.settings.nodeLabel(current), localizedString(R.string.notification_testing_next, App.prefs.settings.nodeLabel(next)))
                val result = runInterruptible { NodeTester.testRecovery(next, url, App.prefs.profileFor(next.id)) }
                ensureActive()
                if (!result.evaluated) return@launch
                if (!result.alive) {
                    failedBackups.failed(next.id, SystemClock.elapsedRealtime())
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
        trafficSession?.let { synchronized(it) { it.close() } }
        trafficSession = null
        account?.let {
            LocalProxyAuth.close(LocalProxyAuth.port, it)
            LocalProxyAuth.close(LocalProxyAuth.probePort, it)
        }
        account = null
        engineEndpoint?.let {
            runCatching { it.stopBridge() }
            runCatching { it.stopCore() }
        }
        engineEndpoint = null
        runCatching { tun?.close() }
        tun = null
        VpnBus.traffic.value = Traffic()
        VpnBus.speedHistory.value = emptyList()
        VpnBus.failedChecks.value = 0
    }

    private fun stopVpn(): Unit = synchronized(commandLock) {
        stopped = true
        val generation = ++startGeneration
        val request = activeRequest
        unwatchNetwork()
        modeJob?.cancel()
        failoverJob?.cancel()
        monitor?.cancel()
        scope.launch(NonCancellable + lifecycleDispatcher) {
            synchronized(this@TunnelCoordinator) {
                if (generation != startGeneration) return@launch
                if (engineOwner === this@TunnelCoordinator) teardown()
                synchronized(commandLock) {
                    if (generation != startGeneration) return@launch
                    VpnBus.state.value = VpnState.Idle
                    VpnBus.request.compareAndSet(request, null)
                    finishStart(latestStartId)
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
            VpnBus.request.compareAndSet(activeRequest, null)
            finishStart(latestStartId)
        }
    }

    fun onRevoke(): Unit = synchronized(commandLock) {
        activeRequest = request(active = false)
        App.prefs.vpnWasOn = false
        stopVpn()
    }

    override fun onDestroy() {
        if (instance.get() === this) instance.clear()
        death?.let { runCatching { endpoint?.asBinder()?.unlinkToDeath(it, 0) } }
        death = null
        endpoint = null
        val request = synchronized(commandLock) {
            stopped = true
            startGeneration++
            activeRequest
        }
        runCatching { unregisterReceiver(screenReceiver) }
        unwatchNetwork()
        scope.cancel()
        scope.launch(NonCancellable + lifecycleDispatcher) {
            synchronized(this@TunnelCoordinator) {
                if (engineOwner !== this@TunnelCoordinator) {
                    VpnBus.request.compareAndSet(request, null)
                    return@launch
                }
                teardown()
                engineOwner = null
                if (VpnBus.state.value !is VpnState.Failed) VpnBus.state.value = VpnState.Idle
                VpnBus.request.compareAndSet(request, null)
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
            .setSmallIcon(App.prefs.loadedSettings?.notificationIcon ?: R.drawable.ic_notification_arrow)
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
        val key = "${App.prefs.settings.notificationIcon}\n$title\n$text\n$sub\n$details"
        if (key == lastNotificationText) return
        lastNotificationText = key
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, text, sub, details))
    }

    private fun startForegroundCompat(n: Notification) {
        lastNotificationText = null
        runCatching { endpoint?.foreground(n) }
    }
}

internal fun applyAppExclusions(ownPackage: String, packages: Set<String>, exclude: (String) -> Unit) {
    exclude(ownPackage)
    for (pkg in packages) {
        if (pkg == ownPackage) continue
        try {
            exclude(pkg)
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            continue
        }
    }
}

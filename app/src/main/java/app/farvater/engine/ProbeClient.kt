package app.farvater.engine

import android.app.ActivityManager
import android.content.ContentProviderClient
import android.content.Context
import android.os.Binder
import android.os.Bundle
import app.farvater.BuildConfig
import java.io.File

internal object ProbeClient {
    @Volatile private var context: Context? = null
    private val requests = java.util.concurrent.Semaphore(7, true)
    private val recoveryRequests = java.util.concurrent.Semaphore(1, true)
    fun init(application: Context) { context = application.applicationContext }

    private fun installed(name: String) = context?.applicationInfo?.nativeLibraryDir?.let { File(it, name).isFile } == true
    val isAvailable get() = installed("libgojni.so")
    val bridgeInstalled get() = installed("libhev-socks5-tunnel.so")
    val version: String? get() = if (isAvailable) BuildConfig.XRAY_CORE_VERSION else null
    val measurementParallelism get() = if (context?.getSystemService(ActivityManager::class.java)?.isLowRamDevice != false) 1 else 7

    private fun acquire(): ContentProviderClient? = context?.let {
        it.contentResolver.acquireUnstableContentProviderClient(it.packageName + ".probes")
    }

    private class Handle(val client: ContentProviderClient, val owner: Binder, val server: android.os.IBinder)

    fun alive(value: Any?): Boolean = (value as? Handle)?.server?.isBinderAlive == true

    fun startProbe(config: String, lane: ProbeLane = ProbeLane.NORMAL): Any? {
        val client = acquire() ?: return null
        val owner = Binder()
        var started = false
        try {
            val answer = CorePayload.send(config) {
                it.putBinder("owner", owner)
                it.putBoolean("recovery", lane == ProbeLane.RECOVERY)
                client.call("start", null, it)
            }
            if (answer?.getBoolean("busy") == true) throw ProbeDeferredException()
            val server = answer?.getBinder("server") ?: return null
            started = answer.getBoolean("started")
            return if (started) Handle(client, owner, server) else null
        } catch (_: android.os.RemoteException) {
            return null
        } finally {
            if (!started) client.close()
        }
    }

    fun stopProbe(value: Any?) {
        val handle = value as? Handle ?: return
        handle.client.use { client ->
            runCatching { client.call("stop", null, Bundle().apply { putBinder("owner", handle.owner) }) }
        }
    }

    fun measureDelayDetailed(config: String, url: String) = measure(config, url, false)
    fun measureRecovery(config: String, url: String) = measure(config, url, true)

    private fun measure(config: String, url: String, recovery: Boolean): DelayMeasurement {
        val slots = if (recovery) recoveryRequests else requests
        try {
            if (!slots.tryAcquire(4, java.util.concurrent.TimeUnit.SECONDS)) return DelayMeasurement(-1, TestFailure.BUSY)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return DelayMeasurement(-1, TestFailure.STALE)
        }
        return try {
            acquire()?.use { client ->
                CorePayload.send(config) {
                    it.putBoolean("recovery", recovery)
                    client.call("measure", url, it)
                }?.let { DelayMeasurement(it.getLong("ms", -1), TestFailure.valueOf(it.getString("reason")!!)) }
            } ?: DelayMeasurement(-1, TestFailure.STALE)
        } catch (_: android.os.RemoteException) {
            DelayMeasurement(-1, TestFailure.STALE)
        } catch (_: IllegalStateException) {
            DelayMeasurement(-1, TestFailure.STALE)
        } catch (_: java.io.IOException) {
            DelayMeasurement(-1, TestFailure.BUSY)
        } finally {
            slots.release()
        }
    }
}

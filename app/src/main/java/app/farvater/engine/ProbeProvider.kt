package app.farvater.engine

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.RemoteException

class ProbeProvider : ContentProvider() {
    private class Running(val core: NativeProbeHandle<Any>, val death: IBinder.DeathRecipient)
    private val running = HashMap<IBinder, Running>()
    private val server = Binder()
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(Binder.getCallingUid() == Process.myUid())
        val data = requireNotNull(extras)
        val out = Bundle()
        val lane = if (data.getBoolean("recovery")) ProbeLane.RECOVERY else ProbeLane.NORMAL
        when (method) {
            "measure" -> {
                val config = CorePayload.receive(data)
                val url = requireNotNull(arg)
                val result = if (lane == ProbeLane.RECOVERY) XrayEngine.measureRecovery(config, url)
                    else XrayEngine.measureDelayDetailed(config, url)
                out.putLong("ms", result.delayMs)
                out.putString("reason", result.reason.name)
            }
            "start" -> {
                val owner = requireNotNull(data.getBinder("owner"))
                try {
                    val core = XrayEngine.startProbe(CorePayload.receive(data), lane) ?: return out
                    val death = IBinder.DeathRecipient { close(owner, core) }
                    val registered = synchronized(running) {
                        if (owner in running) false
                        else try {
                            owner.linkToDeath(death, 0)
                            running[owner] = Running(core, death)
                            true
                        } catch (_: RemoteException) {
                            false
                        }
                    }
                    if (registered) {
                        out.putBinder("server", server)
                        out.putBoolean("started", true)
                    } else XrayEngine.releaseProbe(core)
                } catch (_: ProbeDeferredException) {
                    out.putBoolean("busy", true)
                }
            }
            "stop" -> close(requireNotNull(data.getBinder("owner")))
            else -> throw IllegalArgumentException("Unknown probe operation")
        }
        return out
    }

    private fun close(owner: IBinder, expected: NativeProbeHandle<Any>? = null) {
        val probe = synchronized(running) {
            val current = running[owner] ?: return
            if (expected != null && current.core !== expected) return
            running.remove(owner)
            current
        }
        runCatching { owner.unlinkToDeath(probe.death, 0) }
        XrayEngine.releaseProbe(probe.core)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
}

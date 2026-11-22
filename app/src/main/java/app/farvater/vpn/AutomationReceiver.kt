package app.farvater.vpn

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

internal object AutomationAccess {
    private val random = SecureRandom()
    private val keyFormat = Regex("[A-Za-z0-9_-]{43}")
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "vpn-automation.sha256"))
    private fun digest(key: String) = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.US_ASCII))

    @Synchronized fun current(context: Context): ByteArray? = try {
        file(context).readFully().takeIf { it.size == 32 }
    } catch (_: IOException) { null }

    @Synchronized fun issue(context: Context): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val key = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        val store = file(context)
        val stream = store.startWrite()
        try {
            stream.write(digest(key))
            store.finishWrite(stream)
        } catch (e: IOException) {
            store.failWrite(stream)
            throw e
        }
        if (!MessageDigest.isEqual(current(context), digest(key))) throw IOException("Automation key persistence failed")
        return key
    }

    @Synchronized fun revoke(context: Context) { file(context).delete() }

    fun authorize(context: Context, key: String?): ByteArray? {
        if (key == null || key.length != 43 || !keyFormat.matches(key)) return null
        return current(context)?.takeIf { MessageDigest.isEqual(it, digest(key)) }
    }
}

class AutomationReceiver : BroadcastReceiver() {
    companion object {
        const val CONNECT = "app.farvater.automation.CONNECT"
        const val DISCONNECT = "app.farvater.automation.DISCONNECT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (isOrderedBroadcast) resultCode = Activity.RESULT_CANCELED
        if (intent.component != ComponentName(context, AutomationReceiver::class.java)) return
        val connect = when (intent.action) {
            CONNECT -> true
            DISCONNECT -> false
            else -> return
        }
        val accepted = runCatching {
            val grant = AutomationAccess.authorize(context, intent.getStringExtra("key")) ?: return
            TunnelCoordinator.external(context, connect, grant)
        }.getOrDefault(false)
        if (accepted && isOrderedBroadcast) resultCode = Activity.RESULT_OK
    }
}

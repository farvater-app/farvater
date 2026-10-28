package app.farvater.engine

import app.farvater.R
import app.farvater.data.LocalizedStateException
import android.content.Context
import app.farvater.core.xray.LocalProxyAuth
import app.farvater.core.xray.XrayConfigBuilder
import hev.sockstun.TProxyService
import java.io.File

object TunBridge {
    val isAvailable: Boolean by lazy {
        runCatching { System.loadLibrary("hev-socks5-tunnel") }.isSuccess
    }

    private var running = false

    @Synchronized
    fun start(context: Context, fd: Int, mtu: Int): Result<Unit> = runCatching {
        if (!isAvailable) throw LocalizedStateException(R.string.error_tun_missing)
        val config = File(context.filesDir, "hev.yml")
        config.writeText(buildConfig(mtu))
        if (!TProxyService.TProxyStartService(config.absolutePath, fd)) throw LocalizedStateException(R.string.error_tun_config)
        running = true
    }

    internal fun buildConfig(mtu: Int): String =
        """
            tunnel:
              mtu: $mtu
            socks5:
              port: ${XrayConfigBuilder.SOCKS_PORT}
              address: 127.0.0.1
              udp: 'udp'
              username: '${LocalProxyAuth.user}'
              password: '${LocalProxyAuth.pass}'
            misc:
              task-stack-size: 81920
              log-level: warn
            """.trimIndent()

    @Synchronized
    fun stop() {
        if (!running) return
        runCatching { TProxyService.TProxyStopService() }
        running = false
    }

    fun stats(): LongArray? =
        if (running) runCatching { TProxyService.TProxyGetStats() }.getOrNull() else null
}

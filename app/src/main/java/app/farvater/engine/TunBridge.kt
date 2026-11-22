package app.farvater.engine

import app.farvater.R
import app.farvater.data.LocalizedStateException
import app.farvater.core.xray.LocalProxyAuth
import app.farvater.core.xray.XrayConfigBuilder
import hev.sockstun.TProxyService

object TunBridge {
    val isAvailable: Boolean by lazy {
        runCatching { System.loadLibrary("hev-socks5-tunnel") }.isSuccess
    }

    private var running = false

    @Synchronized
    fun start(fd: Int, config: ByteArray): Result<Unit> = runCatching {
        if (!isAvailable) throw LocalizedStateException(R.string.error_tun_missing)
        if (!TProxyService.TProxyStartServiceFromConfig(config, fd)) throw LocalizedStateException(R.string.error_tun_config)
        running = true
    }

    internal fun buildConfig(mtu: Int, account: LocalProxyAuth.Account = LocalProxyAuth.defaultAccount): String =
        """
            tunnel:
              mtu: $mtu
            socks5:
              port: ${XrayConfigBuilder.SOCKS_PORT}
              address: 127.0.0.1
              udp: 'udp'
              username: '${account.user}'
              password: '${account.pass}'
            misc:
              task-stack-size: 81920
              log-level: warn
            """.trimIndent()

    @Synchronized
    fun stop() {
        if (!running) return
        check(TProxyService.TProxyStopService())
        running = false
    }

    @Synchronized
    fun stats(): LongArray? =
        if (running) runCatching { TProxyService.TProxyGetStats() }.getOrNull() else null
}

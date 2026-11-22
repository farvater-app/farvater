package app.farvater.core.xray

import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

object LocalProxyAuth {
    private val random = SecureRandom()
    class Account {
        val user: String = token(12)
        val pass: String = token(24)
    }
    private class Listener(val account: Account, val alive: () -> Boolean)
    private val live = ConcurrentHashMap<Int, Listener>()
    val defaultAccount = Account()

    val port: Int = freePort()
    val probePort: Int = generateSequence { freePort() }.first { it != port }

    fun open(port: Int, account: Account, alive: () -> Boolean) {
        live[port] = Listener(account, alive)
    }

    fun close(port: Int, account: Account) {
        live.computeIfPresent(port) { _, current -> current.takeUnless { it.account === account } }
    }

    fun account(port: Int): Account? = live[port]?.takeIf { it.alive() }?.account

    private fun freePort(): Int = runCatching {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    }.getOrDefault(20000 + random.nextInt(40000))

    private fun token(length: Int): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(length) { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }
}

package app.farvater.core.xray

import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom

object LocalProxyAuth {
    private val random = SecureRandom()

    val user: String = token(12)
    val pass: String = token(24)
    val port: Int = freePort()

    private fun freePort(): Int = runCatching {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    }.getOrDefault(20000 + random.nextInt(40000))

    private fun token(length: Int): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(length) { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }
}

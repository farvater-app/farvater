package app.farvater.engine

import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.net.await
import app.farvater.net.orDefault
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

object DohProbe {
    private const val NAME = "telegram.org"
    private const val MAX_BODY = 4096L

    val query: String = Base64.getUrlEncoder().withoutPadding().encodeToString(wire(NAME))

    suspend fun answers(client: OkHttpClient, server: String): Boolean = orDefault(false) {
        val quick = client.newBuilder().callTimeout(5, TimeUnit.SECONDS).followRedirects(false).build()
        val request = Request.Builder()
            .url("${XrayConfigBuilder.dohUrl(server)}?dns=$query")
            .header("Accept", "application/dns-message")
            .build()
        quick.newCall(request).await { response ->
            val source = response.body?.source() ?: return@await false
            source.request(MAX_BODY)
            response.isSuccessful && answered(source.buffer.readByteArray(minOf(source.buffer.size, MAX_BODY)))
        }
    }

    suspend fun firstAnswering(client: OkHttpClient, servers: List<String>): String? = servers.firstOrNull { answers(client, it) }

    fun answered(body: ByteArray): Boolean {
        if (body.size < 12) return false
        val reply = body[2].toInt() and 0x80 != 0
        val ok = body[3].toInt() and 0x0F == 0
        val count = ((body[6].toInt() and 0xFF) shl 8) or (body[7].toInt() and 0xFF)
        return reply && ok && count > 0
    }

    fun wire(name: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        name.split('.').filter { it.isNotEmpty() }.forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(byteArrayOf(0, 0, 1, 0, 1))
        return out.toByteArray()
    }
}

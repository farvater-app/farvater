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

    suspend fun answers(client: OkHttpClient, server: String, name: String = NAME): Boolean = orDefault(false) {
        val question = if (name == NAME) query else Base64.getUrlEncoder().withoutPadding().encodeToString(wire(name))
        val quick = client.newBuilder().callTimeout(5, TimeUnit.SECONDS).followRedirects(false).build()
        val request = Request.Builder()
            .url("${XrayConfigBuilder.dohUrl(server)}?dns=$question")
            .header("Accept", "application/dns-message")
            .build()
        quick.newCall(request).await { response ->
            val source = response.body?.source() ?: return@await false
            source.request(MAX_BODY)
            response.isSuccessful && answered(source.buffer.readByteArray(minOf(source.buffer.size, MAX_BODY)))
        }
    }

    suspend fun firstAnswering(client: OkHttpClient, servers: List<String>, name: String = NAME): String? =
        servers.firstOrNull { answers(client, it, name) }

    fun answered(body: ByteArray): Boolean {
        if (body.size < 12) return false
        val reply = body[2].toInt() and 0x80 != 0
        val ok = body[3].toInt() and 0x0F == 0
        val count = ((body[6].toInt() and 0xFF) shl 8) or (body[7].toInt() and 0xFF)
        return reply && ok && count > 0
    }

    fun wire(name: String): ByteArray {
        val normalized = name.removeSuffix(".")
        require(normalized.length in 1..253 && normalized.all { it.code in 33..126 })
        val labels = normalized.split('.')
        require(labels.all { it.length in 1..63 })
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        labels.forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(byteArrayOf(0, 0, 1, 0, 1))
        return out.toByteArray()
    }
}

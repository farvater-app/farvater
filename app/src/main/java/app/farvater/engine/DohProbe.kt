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
    private const val RESPONSE_FLAGS_MASK = 0xfa0f
    private const val STANDARD_RESPONSE = 0x8000

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
            response.isSuccessful && !source.request(MAX_BODY + 1) && answered(source.buffer.readByteArray(), name)
        }
    }

    suspend fun firstAnswering(client: OkHttpClient, servers: List<String>, name: String = NAME): String? =
        servers.firstOrNull { answers(client, it, name) }

    fun answered(body: ByteArray, name: String = NAME): Boolean {
        if (body.size !in 12..MAX_BODY.toInt()) return false
        val packet = Packet(body)
        if (packet.word(0) != 0 || packet.word(2) and RESPONSE_FLAGS_MASK != STANDARD_RESPONSE || packet.word(4) != 1) return false
        val question = packet.name(12) ?: return false
        val expected = name.removeSuffix(".").lowercase()
        if (question.value != expected || packet.word(question.end) != 1 || packet.word(question.end + 2) != 1) return false
        val addresses = mutableSetOf<String>()
        val aliases = mutableMapOf<String, String>()
        var offset = question.end + 4
        repeat(packet.word(6)) {
            val owner = packet.name(offset) ?: return false
            val data = owner.end + 10
            if (data > body.size) return false
            val end = data + packet.word(owner.end + 8)
            if (end > body.size) return false
            if (packet.word(owner.end + 2) == 1) {
                when (packet.word(owner.end)) {
                    1 -> if (end - data == 4) addresses += owner.value else return false
                    5 -> {
                        val alias = packet.name(data) ?: return false
                        if (alias.end != end) return false
                        aliases[owner.value] = alias.value
                    }
                }
            }
            offset = end
        }
        var target = expected
        val seen = mutableSetOf<String>()
        while (seen.add(target)) {
            if (target in addresses) return true
            target = aliases[target] ?: return false
        }
        return false
    }

    private data class Name(val value: String, val end: Int)

    private class Packet(val bytes: ByteArray) {
        fun word(i: Int): Int = if (i + 1 < bytes.size) ((bytes[i].toInt() and 255) shl 8) or
            (bytes[i + 1].toInt() and 255) else -1

        fun name(start: Int): Name? {
            val labels = StringBuilder()
            var cursor = start
            var end = -1
            repeat(256) {
                val size = bytes.getOrNull(cursor)?.toInt()?.and(255) ?: return null
                when {
                    size == 0 -> return Name(labels.toString().lowercase(), if (end < 0) cursor + 1 else end)
                    size >= 192 -> {
                        val pointer = word(cursor)
                        if (pointer < 0 || (pointer and 0x3fff) >= cursor) return null
                        if (end < 0) end = cursor + 2
                        cursor = pointer and 0x3fff
                    }
                    size <= 63 -> {
                        if (cursor + 1 + size > bytes.size || labels.length + size + (if (labels.isEmpty()) 0 else 1) > 253) return null
                        if (labels.isNotEmpty()) labels.append('.')
                        for (i in cursor + 1..cursor + size) {
                            val octet = bytes[i].toInt() and 255
                            if (octet !in 33..126 || octet == '.'.code) return null
                            labels.append(octet.toChar())
                        }
                        cursor += 1 + size
                    }
                    else -> return null
                }
            }
            return null
        }
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

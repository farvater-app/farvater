package app.farvater.core.model

import java.util.Base64

object VlessEncryption {
    enum class Kind { NONE, SUPPORTED, UNKNOWN }

    fun classify(value: String?): Kind {
        if (value.isNullOrEmpty() || value == "none") return Kind.NONE
        if (value.length > 65536) return Kind.UNKNOWN
        val parts = value.split('.')
        if (parts.size < 4 || parts[0] != "mlkem768x25519plus" ||
            parts[1] !in setOf("native", "xorpub", "random") || parts[2] !in setOf("0rtt", "1rtt")) return Kind.UNKNOWN
        val payload = parts.drop(3)
        val padding = payload.takeWhile { it.length < 20 }
        val keys = payload.drop(padding.size)
        if (keys.isEmpty() || !validPadding(padding)) return Kind.UNKNOWN
        if (keys.any { !validKey(it) }) return Kind.UNKNOWN
        return Kind.SUPPORTED
    }

    private fun validKey(text: String): Boolean {
        if (text.length !in setOf(43, 1579) || text.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '-' && it != '_' }) return false
        val key = runCatching { Base64.getUrlDecoder().decode(text) }.getOrNull() ?: return false
        if (key.size == 32) return true
        if (key.size != 1184) return false
        for (i in 0 until 1152 step 3) {
            val a = key[i].toInt() and 255
            val b = key[i + 1].toInt() and 255
            val c = key[i + 2].toInt() and 255
            if ((a or ((b and 15) shl 8)) >= 3329 || ((b shr 4) or (c shl 4)) >= 3329) return false
        }
        return true
    }

    private fun validPadding(parts: List<String>): Boolean {
        var total = 0L
        for ((index, part) in parts.withIndex()) {
            val fields = part.split('-')
            if (fields.size < 3) return false
            val values = fields.take(3).map { it.toIntOrNull() ?: return false }
            if (index == 0 && (values[0] < 100 || values[1] < 35 || values[2] < 35)) return false
            if (index % 2 == 0) total += maxOf(values[1], values[2])
        }
        return total <= 65553
    }
}

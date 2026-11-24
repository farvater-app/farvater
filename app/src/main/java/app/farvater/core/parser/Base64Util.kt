package app.farvater.core.parser

import java.util.Base64

internal object Base64Util {
    fun decodeToString(input: String): String? {
        val clean = input.filterNot { it.isWhitespace() }
        if (clean.isEmpty()) return null
        val normalized = clean.replace('-', '+').replace('_', '/').trimEnd('=')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return try {
            String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

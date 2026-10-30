package app.farvater.core.parser

internal object JsonDepth {
    fun supported(text: String, limit: Int = 64): Boolean {
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in text) {
            if (quoted) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> quoted = false
                }
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > limit) return false
                '}', ']' -> depth--
            }
        }
        return true
    }
}

package app.farvater.data

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
enum class RouteAction { VPN, DIRECT, BLOCK }

@Serializable
data class RouteRule(val target: String, val action: RouteAction) {
    val isIp: Boolean get() = ':' in target || target.substringBefore('/').all { it.isDigit() || it == '.' }

    companion object {
        const val LIMIT = 128
        private val label = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
        fun parse(input: String, action: RouteAction): RouteRule? {
            val text = input.trim()
            if (text.length !in 1..253 || text.any { it.isWhitespace() || it.isISOControl() || it in "%?#@[]\\" }) return null
            val host = text.substringBefore('/').removeSuffix(".")
            if (host.isEmpty()) return null
            val canonical = runCatching { HttpUrl.Builder().scheme("https").host(host).build().host }.getOrNull() ?: return null
            val ipv6 = ':' in canonical
            val numeric = canonical.all { it in '0'..'9' || it == '.' }
            if (!ipv6 && numeric && (canonical != host || canonical.split('.').size != 4 || canonical.split('.').any {
                    it.toIntOrNull() !in 0..255 || (it.length > 1 && it.startsWith('0'))
                })) return null
            if (!ipv6 && !numeric && canonical.split('.').any { !label.matches(it) }) return null
            if ('/' !in text) return RouteRule(canonical, action)
            if (!ipv6 && !numeric) return null
            val suffix = text.substringAfter('/')
            val prefix = suffix.toIntOrNull() ?: return null
            if (suffix != prefix.toString() || prefix !in 0..(if (ipv6) 128 else 32)) return null
            return RouteRule("$canonical/$prefix", action)
        }

        fun valid(rules: List<RouteRule>): Boolean = rules.size <= LIMIT &&
            rules.distinctBy { it.target }.size == rules.size && rules.all { parse(it.target, it.action) == it }
    }
}

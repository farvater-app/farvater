package app.farvater.data

import app.farvater.net.SafeHttp
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import java.net.InetAddress
import java.security.MessageDigest

@Serializable
enum class ServiceKind { GEMINI, CHATGPT, CLAUDE, YOUTUBE, CUSTOM }

@Serializable
enum class ServiceVerdict { RESPONDED, REJECTED, UNKNOWN }

@Serializable
enum class ServiceReason { PAGE, STATUS, REGION, CHALLENGE, HTTP_ERROR, UNEXPECTED, NETWORK }

@Serializable
data class ServiceCheck(val verdict: ServiceVerdict, val reason: ServiceReason, val at: Long)

data class ServiceTarget(val kind: ServiceKind, val url: String, val expectedCode: Int = 200) {
    val key: String = MessageDigest.getInstance("SHA-256")
        .digest("${kind.name}\n$url\n$expectedCode".toByteArray()).joinToString("") { "%02x".format(it) }
}

object ServiceChecks {
    const val TTL_MS = 24 * 60 * 60 * 1000L
    const val RETRY_MS = 30 * 60 * 1000L
    const val MAX_NODES = 10
    private val explicitScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    val builtIn = listOf(
        ServiceTarget(ServiceKind.GEMINI, "https://gemini.google.com/app?hl=en"),
        ServiceTarget(ServiceKind.CHATGPT, "https://chatgpt.com/"),
        ServiceTarget(ServiceKind.CLAUDE, "https://claude.ai/login"),
        ServiceTarget(ServiceKind.YOUTUBE, "https://www.youtube.com/?hl=en"),
    )

    fun customUrl(value: String): HttpUrl? {
        if (value.length > 2048 || value.any(Char::isISOControl)) return null
        val input = value.trim()
        if (input.startsWith('/') || input.startsWith('\\')) return null
        val normalized = if (explicitScheme.containsMatchIn(input)) input else "https://$input"
        if (normalized.length > 2048) return null
        val url = SafeHttp.parse(normalized) ?: return null
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null) return null
        val host = url.host.trimEnd('.')
        if ('.' !in host && ':' !in host) return null
        if (listOf(".local", ".lan", ".internal", ".home", ".home.arpa").any(host::endsWith)) return null
        if (':' in host || host.all { it.isDigit() || it == '.' }) {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
            if (SafeHttp.isLocalAddress(address) || address.isSiteLocalAddress) return null
            val bytes = address.address
            if (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) return null
            if (bytes.size == 4) {
                val first = bytes[0].toInt() and 255
                val second = bytes[1].toInt() and 255
                if (first == 0 || first >= 224 || first == 100 && second in 64..127 || first == 198 && second in 18..19) return null
            }
        }
        return url
    }

    fun targets(settings: AppSettings): List<ServiceTarget> {
        if (!settings.serviceChecksEnabled) return emptyList()
        return builtIn.filter { it.kind in settings.checkedServices } +
            if (ServiceKind.CUSTOM in settings.checkedServices && settings.serviceExpectedCode in 200..599) {
                listOfNotNull(customUrl(settings.serviceCustomUrl)?.let { ServiceTarget(ServiceKind.CUSTOM, it.toString(), settings.serviceExpectedCode) })
            } else emptyList()
    }

    fun key(mode: String, nodeId: String, target: ServiceTarget): String = "$mode/$nodeId/${target.key}"

    fun fresh(check: ServiceCheck?, now: Long = System.currentTimeMillis()): Boolean =
        check != null && now - check.at in 0 until TTL_MS

    fun due(check: ServiceCheck?, now: Long = System.currentTimeMillis()): Boolean =
        check == null || now - check.at !in 0 until RETRY_MS

    fun prune(history: Map<String, ServiceCheck>, now: Long = System.currentTimeMillis()): Map<String, ServiceCheck> =
        history.filterValues { fresh(it, now) }.entries.sortedBy { it.value.at }.takeLast(3000).associate { it.key to it.value }
}

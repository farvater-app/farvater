package app.farvater.net

import app.farvater.core.parser.SubscriptionLimitException

object ImportLink {
    private val schemes = setOf("http", "https", "vless", "vmess", "trojan", "ss", "hysteria2", "hy2")

    fun endpoint(url: String): String? = SafeHttp.parse(url)?.let {
        val host = if (':' in it.host) "[${it.host}]" else it.host
        if (it.port == okhttp3.HttpUrl.defaultPort(it.scheme)) host else "$host:${it.port}"
    }

    fun preview(text: String): String? {
        endpoint(text)?.let { return it }
        val nodes = try {
            app.farvater.core.parser.SubscriptionParser.parse(text, app.farvater.core.model.ProxyNode.MANUAL_SOURCE).nodes
        } catch (_: SubscriptionLimitException) {
            return null
        }
        return nodes.map { node ->
            val host = if (':' in node.address && !node.address.startsWith("[")) "[${node.address}]" else node.address
            "${node.protocol.title} · $host:${node.port}"
        }.distinct().joinToString("\n").ifBlank { null }
    }

    fun sanitize(raw: String?): String? {
        val text = raw?.trim() ?: return null
        if (text.isEmpty() || text.length > SafeHttp.MAX_URL_LENGTH) return null
        if (text.any { it == '\n' || it == '\r' || it.isISOControl() }) return null
        val scheme = text.substringBefore("://", "").lowercase()
        if (scheme !in schemes) return null
        if (scheme.startsWith("http") && SafeHttp.parse(text) == null) return null
        return text
    }
}

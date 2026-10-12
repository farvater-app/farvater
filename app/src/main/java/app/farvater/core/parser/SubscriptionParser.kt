package app.farvater.core.parser

import app.farvater.core.model.ProxyNode

data class Usage(val used: Long, val total: Long, val expire: Long)

data class ParsedSubscription(
    val title: String?,
    val updateIntervalHours: Int?,
    val announce: String?,
    val nodes: List<ProxyNode>,
    val notices: List<String>,
    val skipped: Map<String, Int> = emptyMap(),
    val usage: Usage? = null,
)

object SubscriptionParser {
    fun parse(body: String, sourceId: String, headers: Map<String, String> = emptyMap()): ParsedSubscription {
        var text = body.trim().removePrefix("\uFEFF")
        val json = XrayJsonParser.parse(text, sourceId)
        if (json == null && !text.contains("://")) {
            Base64Util.decodeToString(text)?.takeIf { it.contains("://") }?.let { text = it }
        }

        val meta = headers.mapKeys { it.key.lowercase() }.toMutableMap()
        val nodes = LinkedHashMap<String, ProxyNode>()
        val notices = mutableListOf<String>()
        val skipped = HashMap<String, Int>()
        val announceLines = mutableListOf<String>()

        fun add(node: ProxyNode) {
            if (node.address == "0.0.0.0" || node.address == "127.0.0.1") notices += node.name else nodes.putIfAbsent(node.id, node)
        }

        json?.forEach(::add)
        if (json == null) text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            if (line.startsWith("{")) {
                XrayJsonParser.parse(line, sourceId)?.forEach(::add)
                return@forEach
            }
            if (line.startsWith("#")) {
                val kv = line.removePrefix("#").trim()
                val key = kv.substringBefore(':', "").trim().lowercase()
                if (key == "announce") {
                    maybeBase64(kv.substringAfter(':').trim()).takeIf { it.isNotBlank() }?.let(announceLines::add)
                } else if (key.isNotEmpty() && ' ' !in key) {
                    meta.putIfAbsent(key, kv.substringAfter(':').trim())
                }
                return@forEach
            }
            val node = LinkParser.parse(line, sourceId)
            if (node == null) {
                val scheme = line.substringBefore("://", "").lowercase()
                if (scheme.isNotEmpty() && scheme.length <= 16) skipped[scheme] = (skipped[scheme] ?: 0) + 1
                return@forEach
            }
            add(node)
        }

        return ParsedSubscription(
            title = meta["profile-title"]?.let(::maybeBase64),
            updateIntervalHours = meta["profile-update-interval"]?.trim()?.toIntOrNull(),
            announce = (meta["announce"]?.let(::maybeBase64) ?: announceLines.joinToString("\n")).takeIf { it.isNotBlank() },
            nodes = nodes.values.toList(),
            notices = notices,
            skipped = skipped,
            usage = usage(meta["subscription-userinfo"]),
        )
    }

    private fun usage(value: String?): Usage? {
        val parts = value.orEmpty().split(';').mapNotNull { part ->
            val key = part.substringBefore('=', "").trim().lowercase()
            part.substringAfter('=').trim().substringBefore('.').toLongOrNull()?.takeIf { key.isNotEmpty() }?.let { key to it }
        }.toMap()
        val usage = Usage((parts["upload"] ?: 0) + (parts["download"] ?: 0), parts["total"] ?: 0, parts["expire"] ?: 0)
        return usage.takeIf { it.used > 0 || it.total > 0 || it.expire > 0 }
    }

    private fun maybeBase64(value: String): String =
        if (value.startsWith("base64:")) Base64Util.decodeToString(value.removePrefix("base64:")) ?: value else value
}

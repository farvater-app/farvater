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
        val budget = SubscriptionBudget(body, headers)
        var text = body.trim().removePrefix("\uFEFF")
        val skipped = HashMap<String, Int>()
        fun rejected(reason: String) { skipped[reason] = (skipped[reason] ?: 0) + 1 }
        val json = XrayJsonParser.parseSubscription(text, sourceId, ::rejected, budget)
        if (json == null && !text.contains("://")) {
            budget.base64Input(text)
            Base64Util.decodeToString(text)?.takeIf { it.contains("://") }?.let {
                budget.decodedBody(it)
                text = it
            }
        }

        val meta = headers.mapKeys { it.key.lowercase() }.toMutableMap()
        val nodes = LinkedHashMap<String, ProxyNode>()
        val notices = mutableListOf<String>()
        val announceLines = mutableListOf<String>()

        fun add(node: ProxyNode) {
            if (node.address == "0.0.0.0" || node.address == "127.0.0.1") {
                budget.retain(node)
                notices += node.name
            } else if (node.id !in nodes) {
                budget.retain(node)
                nodes[node.id] = node
            }
        }

        json?.forEach(::add)
        if (json == null) text.lineSequence().forEach { rawLine ->
            budget.scanEntry(rawLine.length)
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("{")) {
                XrayJsonParser.parseSubscription(line, sourceId, ::rejected, budget)?.forEach(::add)
                return@forEach
            }
            if (line.startsWith("#")) {
                val kv = line.removePrefix("#").trim()
                val key = kv.substringBefore(':', "").trim().lowercase()
                val value = kv.substringAfter(':', "").trim()
                if (key == "announce") {
                    budget.metadata(key, value)
                    maybeBase64(value).takeIf { it.isNotBlank() }?.let(announceLines::add)
                } else if (key.isNotEmpty() && ' ' !in key) {
                    budget.metadata(key, value)
                    meta.putIfAbsent(key, value)
                }
                return@forEach
            }
            var unsupported = false
            val node = LinkParser.parseSubscriptionLine(line, sourceId, budget) { reason ->
                unsupported = true
                rejected(reason)
            }
            if (node == null) {
                val scheme = line.substringBefore("://", "").lowercase()
                if (!unsupported && scheme.isNotEmpty() && scheme.length <= 16) rejected(scheme)
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

    internal fun checkNodes(nodes: Sequence<ProxyNode>) = SubscriptionBudget.checkNodes(nodes)

    internal fun checkBackupJson(text: String) =
        SubscriptionBudget(text, emptyMap()).checkJson(text, SubscriptionBudget.MAX_BACKUP_JSON_CONTAINER_ENTRIES)

    internal const val MAX_NODES = SubscriptionBudget.MAX_NODES
    internal const val MAX_SCANNED_ENTRIES = SubscriptionBudget.MAX_SCANNED_ENTRIES
    internal const val MAX_LINK_CHARS = SubscriptionBudget.MAX_LINK_CHARS
    internal const val MAX_QUERY_PARAMETERS = SubscriptionBudget.MAX_QUERY_PARAMETERS
}

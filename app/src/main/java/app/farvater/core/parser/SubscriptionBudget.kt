package app.farvater.core.parser

import app.farvater.core.model.ProxyNode
import java.io.IOException

class SubscriptionLimitException : IOException("Subscription parsing budget exceeded")

internal class SubscriptionBudget(body: String, headers: Map<String, String>) {
    private var scannedEntries = 0
    private var retainedNodes = 0
    private var retainedChars = 0L
    private var metadataEntries = 0
    private var metadataChars = 0L
    private var queryParameters = 0

    init {
        check(body.length <= MAX_INPUT_CHARS)
        headers.forEach { (key, value) -> metadata(key, value) }
    }

    fun decodedBody(text: String) {
        check(text.length <= MAX_INPUT_CHARS)
    }

    fun base64Input(text: String) {
        check(text.length <= MAX_BASE64_INPUT_CHARS)
    }

    fun scanEntry(length: Int? = null) {
        check(++scannedEntries <= MAX_SCANNED_ENTRIES)
        if (length != null) check(length <= MAX_LINK_CHARS)
        queryParameters = 0
    }

    fun metadata(key: String, value: String) {
        check(++metadataEntries <= MAX_METADATA_ENTRIES)
        check(key.length <= MAX_METADATA_FIELD_CHARS && value.length <= MAX_METADATA_FIELD_CHARS)
        metadataChars += key.length.toLong() + value.length
        check(metadataChars <= MAX_METADATA_CHARS)
    }

    fun queryParameter() {
        check(++queryParameters <= MAX_QUERY_PARAMETERS)
    }

    fun retain(node: ProxyNode) {
        check(++retainedNodes <= MAX_NODES)
        val chars = node.raw.length.toLong() + node.name.length + node.address.length + node.secret.length +
            node.params.entries.sumOf { (key, value) -> key.length.toLong() + value.length }
        check(chars <= MAX_LINK_CHARS && retainedChars <= MAX_RETAINED_CHARS - chars)
        retainedChars += chars
    }

    fun checkJson(text: String, maxContainerEntries: Int = MAX_JSON_CONTAINER_ENTRIES) {
        var depth = 0
        var quoted = false
        var escaped = false
        var stringChars = 0
        var scalarChars = 0
        var tokens = 0
        val commas = IntArray(MAX_JSON_DEPTH + 1)

        fun token() {
            check(++tokens <= MAX_JSON_TOKENS)
            scalarChars = 0
        }

        for (char in text) {
            if (quoted) {
                check(++stringChars <= MAX_JSON_STRING_CHARS)
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> quoted = false
                }
                continue
            }
            when (char) {
                '"' -> {
                    quoted = true
                    stringChars = 0
                    token()
                }
                '{', '[' -> {
                    token()
                    check(++depth <= MAX_JSON_DEPTH)
                    commas[depth] = 0
                }
                '}', ']' -> {
                    token()
                    depth--
                }
                ',' -> {
                    token()
                    if (depth in 1..MAX_JSON_DEPTH) check(++commas[depth] < maxContainerEntries)
                }
                ':' -> token()
                else -> if (!char.isWhitespace()) check(++scalarChars <= MAX_JSON_STRING_CHARS)
            }
        }
    }

    private fun check(condition: Boolean) {
        if (!condition) throw SubscriptionLimitException()
    }

    companion object {
        const val MAX_INPUT_CHARS = 8 * 1024 * 1024
        const val MAX_BASE64_INPUT_CHARS = 2 * 1024 * 1024
        const val MAX_SCANNED_ENTRIES = 2_000
        const val MAX_NODES = 1_000
        const val MAX_LINK_CHARS = 256 * 1024
        const val MAX_RETAINED_CHARS = 8L * 1024 * 1024
        const val MAX_QUERY_PARAMETERS = 128
        const val MAX_METADATA_ENTRIES = 64
        const val MAX_METADATA_FIELD_CHARS = 8 * 1024
        const val MAX_METADATA_CHARS = 64L * 1024
        const val MAX_JSON_DEPTH = 64
        const val MAX_JSON_CONTAINER_ENTRIES = 1_024
        const val MAX_BACKUP_JSON_CONTAINER_ENTRIES = 5_000
        const val MAX_JSON_STRING_CHARS = MAX_LINK_CHARS
        const val MAX_JSON_TOKENS = 100_000

        fun checkNodes(nodes: Sequence<ProxyNode>) {
            val budget = SubscriptionBudget("", emptyMap())
            nodes.forEach(budget::retain)
        }
    }
}

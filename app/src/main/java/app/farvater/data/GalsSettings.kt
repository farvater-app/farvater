package app.farvater.data

import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import kotlinx.serialization.Serializable

@Serializable
enum class GalsStrategy { LATENCY, THROUGHPUT }

@Serializable
data class GalsGroup(
    val id: String,
    val name: String,
    val allSources: Boolean = true,
    val sourceIds: Set<String> = emptySet(),
    val strategy: GalsStrategy = GalsStrategy.LATENCY,
    val target: ServiceKind? = null,
    val customUrl: String = "",
    val expectedCode: Int = 200,
    val intervalSeconds: Int = 60,
    val transports: Set<String> = emptySet(),
    val periodicOptimization: Boolean = false,
)

object GalsGroups {
    const val MAX_GROUPS = 32
    val intervals = setOf(30, 60, 120, 300)
    val transports = setOf("tcp", "ws", "grpc", "xhttp", "httpupgrade", "kcp", "quic", "h2", "hysteria2")
    private val identifier = Regex("[A-Za-z0-9_-]{1,64}")

    fun valid(group: GalsGroup): Boolean = with(group) {
        identifier.matches(id) && name.isNotBlank() && name.length <= 80 && name.none(Char::isISOControl) &&
            sourceIds.size <= 200 && sourceIds.all(identifier::matches) && intervalSeconds in intervals &&
            transports.all { it in GalsGroups.transports } && expectedCode in 200..599 &&
            (customUrl.isEmpty() && target != ServiceKind.CUSTOM || ServiceChecks.customUrl(customUrl) != null)
    }

    fun validate(groups: List<GalsGroup>, active: String?, sources: Set<String>) {
        require(groups.size <= MAX_GROUPS && groups.map { it.id }.distinct().size == groups.size)
        require(groups.all { valid(it) && it.sourceIds.all(sources::contains) })
        require(active == null || groups.any { it.id == active })
    }

    fun transport(node: ProxyNode): String = if (node.protocol == Protocol.HYSTERIA2) "hysteria2" else when (val value = node.transport) {
        "mkcp" -> "kcp"
        "raw" -> "tcp"
        "splithttp" -> "xhttp"
        "http", "http2" -> "h2"
        else -> value
    }
}

class GalsPolicy private constructor(val group: GalsGroup?, private val valid: Boolean,
    private val settings: AppSettings) {
    val legacy: Boolean get() = group == null && valid

    fun accepts(node: ProxyNode): Boolean = valid && (group == null ||
        (group.allSources || node.sourceId in group.sourceIds) &&
        (group.transports.isEmpty() || GalsGroups.transport(node) in group.transports))

    fun sameBehavior(other: GalsPolicy): Boolean = valid == other.valid &&
        group?.copy(name = "") == other.group?.copy(name = "") && settings.testUrl == other.settings.testUrl &&
        settings.excludedAutoNodeIds == other.settings.excludedAutoNodeIds && settings.encryptedDns == other.settings.encryptedDns && settings.netMode == other.settings.netMode

    companion object {
        fun from(settings: AppSettings): GalsPolicy {
            val id = settings.activeGalsGroupId ?: return GalsPolicy(null, true, settings)
            if (settings.galsGroups.size > GalsGroups.MAX_GROUPS) return GalsPolicy(null, false, settings)
            val group = settings.galsGroups.singleOrNull { it.id == id } ?: return GalsPolicy(null, false, settings)
            return GalsPolicy(group, GalsGroups.valid(group), settings)
        }
    }
}

package app.farvater.core.model

import app.farvater.core.parser.XrayJsonParser
import kotlinx.serialization.Serializable

@Serializable
enum class Protocol(val title: String) {
    VLESS("VLESS"),
    VMESS("VMess"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks"),
    HYSTERIA2("Hysteria2"),
}

@Serializable
data class ProxyNode(
    val id: String,
    val protocol: Protocol,
    val name: String,
    val address: String,
    val port: Int,
    val secret: String,
    val params: Map<String, String> = emptyMap(),
    val raw: String,
    val sourceId: String = MANUAL_SOURCE,
) {
    val security: String get() = params["security"].orEmpty().lowercase()
    val transport: String get() = params["type"].orEmpty().ifBlank { "tcp" }.lowercase()

    val securityIssue: String?
        get() = protocolSecurityIssue ?: jsonSecurityIssue

    private val protocolSecurityIssue: String?
        get() = when {
            (params["allowInsecure"] == "1" || params["insecure"] == "1") && pinnedCert == null -> "Без проверки сертификата"
            protocol == Protocol.VLESS && (security.isBlank() || security == "none") -> "Без шифрования"
            protocol == Protocol.TROJAN && security == "none" -> "Без шифрования"
            protocol == Protocol.VMESS && params["scy"].orEmpty().lowercase() in setOf("none", "zero") -> "Без шифрования"
            protocol == Protocol.SHADOWSOCKS -> {
                val method = params["method"].orEmpty().lowercase()
                when {
                    method in setOf("", "none", "plain", "table") -> "Без шифрования"
                    method !in AEAD_METHODS && !method.startsWith("2022-blake3-") -> "Устаревшее шифрование"
                    else -> null
                }
            }
            else -> null
        }

    private val jsonSecurityIssue by lazy {
        if (isJson) params["jsonSecurityIssue"] ?: XrayJsonParser.securityIssue(raw) else null
    }

    val isInsecure: Boolean get() = securityIssue != null

    val isJson: Boolean get() = raw.startsWith("{")

    val pinnedCert: String? get() = (params["pcs"] ?: params["pinSHA256"])?.takeIf { it.isNotBlank() }

    val tags: List<String>
        get() = buildList {
            add(protocol.title)
            when (security) {
                "reality" -> add("Reality")
                "tls" -> add("TLS")
            }
            if (transport != "tcp" && transport != "raw") add(transport.uppercase())
        }

    companion object {
        const val MANUAL_SOURCE = "manual"

        private val AEAD_METHODS = setOf(
            "aes-128-gcm", "aes-192-gcm", "aes-256-gcm",
            "chacha20-ietf-poly1305", "chacha20-poly1305", "xchacha20-ietf-poly1305", "xchacha20-poly1305",
        )
    }
}

fun List<ProxyNode>.onePerEndpoint(keepId: String?): List<ProxyNode> {
    val chosen = LinkedHashMap<Any, ProxyNode>()
    for (node in this) {
        val key = if (node.isJson) node.id else Triple(node.protocol, node.address.lowercase(), node.port)
        val previous = chosen[key]
        if (previous == null || (node.id == keepId && previous.id != keepId)) chosen[key] = node
    }
    return chosen.values.toList()
}

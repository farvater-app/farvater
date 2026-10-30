package app.farvater.core.parser

import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode

internal object NodeSupport {
    private val shadowsocksMethods = setOf(
        "aes-128-gcm", "aead_aes_128_gcm", "aes-256-gcm", "aead_aes_256_gcm",
        "chacha20-poly1305", "aead_chacha20_poly1305", "chacha20-ietf-poly1305",
        "xchacha20-poly1305", "aead_xchacha20_poly1305", "xchacha20-ietf-poly1305",
        "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
    )

    fun reason(node: ProxyNode): String? = reason(node.protocol, node.params)
        ?: XrayJsonParser.linkDownloadSupportIssue(node.protocol, node.params)

    fun reason(protocol: Protocol, params: Map<String, String>): String? = when {
        protocol == Protocol.SHADOWSOCKS && params["method"]?.lowercase() !in shadowsocksMethods -> "unsupported_ss_method"
        else -> streamReason(protocol, params)
    }

    fun streamReason(protocol: Protocol, params: Map<String, String>): String? = when {
        params["security"].equals("reality", true) && params["pbk"].isNullOrBlank() -> "missing_reality_key"
        protocol == Protocol.HYSTERIA2 && !params["obfs"].isNullOrEmpty() && params["obfs"] != "salamander" -> "unsupported_hysteria_obfs"
        else -> null
    }
}

package app.farvater.core.model

import kotlinx.serialization.Serializable

@Serializable
data class NodeProfile(
    val fingerprint: String? = null,
    val mux: Boolean = false,
    val fragment: Boolean = false,
)

val ProxyNode.canMux: Boolean
    get() = !isJson && protocol != Protocol.HYSTERIA2 && protocol != Protocol.SHADOWSOCKS &&
        params["flow"].isNullOrBlank() && transport !in setOf("xhttp", "splithttp")

val ProxyNode.canFragment: Boolean
    get() = !isJson && protocol != Protocol.HYSTERIA2 && (security == "tls" || security == "reality" || (protocol == Protocol.TROJAN && security.isBlank()))

val ProxyNode.usesFingerprint: Boolean
    get() = !isJson && protocol != Protocol.HYSTERIA2 && (security == "tls" || security == "reality" || (protocol == Protocol.TROJAN && security.isBlank()))

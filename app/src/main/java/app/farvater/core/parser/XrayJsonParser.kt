package app.farvater.core.parser

import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object XrayJsonParser {
    private val PROTOCOLS = mapOf(
        "vless" to Protocol.VLESS,
        "vmess" to Protocol.VMESS,
        "trojan" to Protocol.TROJAN,
        "shadowsocks" to Protocol.SHADOWSOCKS,
        "hysteria" to Protocol.HYSTERIA2,
    )
    private val RESERVED_TAGS = setOf("direct", "block", "dns-out")

    fun parse(text: String, sourceId: String): List<ProxyNode>? {
        if (!text.startsWith("[") && !text.startsWith("{")) return null
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return null
        val configs = (root as? JsonArray)?.filterIsInstance<JsonObject>() ?: listOfNotNull(root as? JsonObject)
        return configs.mapNotNull { node(it, sourceId) }
    }

    private fun node(config: JsonObject, sourceId: String): ProxyNode? {
        val original = config.array("outbounds").filterIsInstance<JsonObject>()
            .filter { it.str("tag") !in RESERVED_TAGS && it.str("protocol") != "dns" }
        val main = original.firstOrNull { it.str("tag") == "proxy" && it.str("protocol") in PROTOCOLS }
            ?: original.firstOrNull { it.str("protocol") in PROTOCOLS }
            ?: return null
        val protocol = PROTOCOLS.getValue(main.str("protocol")!!)
        val settings = main.obj("settings") ?: JsonObject(emptyMap())
        val server = (settings.array("vnext").firstOrNull() ?: settings.array("servers").firstOrNull()) as? JsonObject ?: settings
        val user = server.array("users").firstOrNull() as? JsonObject ?: server
        val stream = main.obj("streamSettings")
        val tls = stream?.obj("tlsSettings")
        val reality = stream?.obj("realitySettings")
        val address = server.str("address") ?: return null
        val port = server.int("port") ?: return null

        val outbounds = (listOf(main) + original.filter { it !== main }).map(::withoutInsecure)
        val proxyTags = outbounds.filter { it.str("protocol") in PROTOCOLS }.mapNotNull { it.str("tag") }
        val routing = config.obj("routing")
        val balancerTag = routing?.array("rules")?.filterIsInstance<JsonObject>()
            ?.lastOrNull { it.str("balancerTag") != null }?.str("balancerTag")
        val balancer = routing?.array("balancers")?.filterIsInstance<JsonObject>()?.firstOrNull { it.str("tag") == balancerTag }
        val prefixes = balancer?.array("selector")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val selected = proxyTags.filter { tag -> prefixes.any { tag.startsWith(it) } }
        val leaks = outbounds.any { o -> o.str("protocol") !in PROTOCOLS && selected.any { o.str("tag").orEmpty().startsWith(it) } }

        val body = buildJsonObject {
            put("outbounds", JsonArray(outbounds))
            if (balancer != null && selected.isNotEmpty() && !leaks) {
                putJsonObject("routing") {
                    putJsonArray("balancers") {
                        addJsonObject {
                            put("tag", balancerTag)
                            putJsonArray("selector") { selected.forEach { add(it) } }
                            balancer["strategy"]?.let { put("strategy", it) }
                            balancer.str("fallbackTag")?.takeIf { it in proxyTags }?.let { put("fallbackTag", it) }
                        }
                    }
                }
                config["observatory"]?.let { put("observatory", it) }
                config["burstObservatory"]?.let { put("burstObservatory", it) }
            }
        }
        val name = config.str("remarks")?.trim().orEmpty().ifEmpty { "$address:$port" }
        val params = buildMap {
            stream?.str("security")?.let { put("security", it) }
            stream?.str("network")?.takeIf { protocol != Protocol.HYSTERIA2 }?.let { put("type", it) }
            (tls ?: reality)?.str("serverName")?.let { put("sni", it) }
            reality?.str("publicKey")?.let { put("pbk", it) }
            user.str("flow")?.let { put("flow", it) }
            server.str("method")?.let { put("method", it) }
            user.str("security")?.let { put("scy", it) }
            if (tls?.bool("allowInsecure") == true) put("allowInsecure", "1")
            tls?.str("pinnedPeerCertSha256")?.let { put("pcs", it) }
        }
        return ProxyNode(
            id = LinkParser.nodeId(body.toString()),
            protocol = protocol,
            name = name,
            address = address,
            port = port,
            secret = user.str("id") ?: user.str("password") ?: stream?.obj("hysteriaSettings")?.str("auth").orEmpty(),
            params = params,
            raw = JsonObject(mapOf("remarks" to JsonPrimitive(name)) + body).toString(),
            sourceId = sourceId,
        )
    }

    private fun withoutInsecure(outbound: JsonObject): JsonObject {
        val stream = outbound.obj("streamSettings") ?: return outbound
        val tls = stream.obj("tlsSettings")?.takeIf { "allowInsecure" in it } ?: return outbound
        return JsonObject(outbound + ("streamSettings" to JsonObject(stream + ("tlsSettings" to JsonObject(tls - "allowInsecure")))))
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "1") }
}

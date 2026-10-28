package app.farvater.core.parser

import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
            .filter { it.str("tag") !in RESERVED_TAGS && it.str("protocol")?.lowercase() != "dns" }
        val main = original.firstOrNull { it.str("tag") == "proxy" && it.str("protocol")?.lowercase() in PROTOCOLS }
            ?: original.firstOrNull { it.str("protocol")?.lowercase() in PROTOCOLS }
            ?: return null
        val protocol = PROTOCOLS.getValue(main.str("protocol")?.lowercase()!!)
        val settings = main.obj("settings") ?: JsonObject(emptyMap())
        val server = (settings.array("vnext").firstOrNull() ?: settings.array("servers").firstOrNull()) as? JsonObject ?: settings
        val user = server.array("users").firstOrNull() as? JsonObject ?: server
        val stream = main.obj("streamSettings")
        val tls = stream?.obj("tlsSettings")
        val reality = stream?.obj("realitySettings")
        val address = server.str("address") ?: return null
        val port = server.int("port") ?: return null

        val ordered = listOf(main) + original.filter { it !== main }
        val outbounds = ordered.map { withoutInsecure(it) as JsonObject }
        val routing = config.obj("routing")
        val balancerTag = routing?.array("rules")?.filterIsInstance<JsonObject>()
            ?.lastOrNull { it.str("balancerTag") != null }?.str("balancerTag")
        val balancer = routing?.array("balancers")?.filterIsInstance<JsonObject>()?.firstOrNull { it.str("tag") == balancerTag }
        val safeBalancer = balancer?.let { safeBalancers(listOf(it), outbounds).firstOrNull() }

        val body = buildJsonObject {
            put("outbounds", JsonArray(outbounds))
            if (safeBalancer != null) {
                putJsonObject("routing") {
                    putJsonArray("rules") {
                        addJsonObject {
                            put("type", "field")
                            put("network", "tcp,udp")
                            put("balancerTag", balancerTag)
                        }
                    }
                    putJsonArray("balancers") {
                        addJsonObject {
                            put("tag", balancerTag)
                            put("selector", safeBalancer.getValue("selector"))
                            balancer["strategy"]?.let { put("strategy", it) }
                            safeBalancer["fallbackTag"]?.let { put("fallbackTag", it) }
                        }
                    }
                }
                config["observatory"]?.let { put("observatory", it) }
                config["burstObservatory"]?.let { put("burstObservatory", it) }
            }
        }
        val name = config.str("remarks")?.trim().orEmpty().ifEmpty { "$address:$port" }
        val params = buildMap {
            put("security", stream?.str("security") ?: "none")
            stream?.str("network")?.takeIf { protocol != Protocol.HYSTERIA2 }?.let { put("type", it) }
            (tls ?: reality)?.str("serverName")?.let { put("sni", it) }
            reality?.str("publicKey")?.let { put("pbk", it) }
            user.str("flow")?.let { put("flow", it) }
            server.str("method")?.let { put("method", it) }
            user.str("security")?.let { put("scy", it) }
            if (tls?.bool("allowInsecure") == true) put("allowInsecure", "1")
            tls?.str("pinnedPeerCertSha256")?.let { put("pcs", it) }
            securityIssue(JsonObject(body + ("outbounds" to JsonArray(ordered))))?.let { put("jsonSecurityIssue", it) }
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

    internal fun withoutInsecure(value: JsonElement, parent: String = ""): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.filterKeys {
            !it.equals("allowInsecure", ignoreCase = true) &&
                !(parent.equals("masquerade", ignoreCase = true) && it.equals("insecure", ignoreCase = true))
        }.mapValues { (key, child) -> withoutInsecure(child, key) })
        is JsonArray -> JsonArray(value.map { withoutInsecure(it, parent) })
        else -> value
    }

    internal fun safeBalancers(balancers: List<JsonElement>, outbounds: List<JsonElement>): List<JsonObject> {
        val outputs = outbounds.filterIsInstance<JsonObject>()
        val proxyTags = outputs.filter { it.str("protocol")?.lowercase() in PROTOCOLS }.mapNotNull { it.str("tag") }
        return balancers.filterIsInstance<JsonObject>().mapNotNull { balancer ->
            val prefixes = balancer.array("selector").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val selected = proxyTags.filter { tag -> prefixes.any(tag::startsWith) }.distinct()
            val leaks = outputs.any { output ->
                output.str("protocol")?.lowercase() !in PROTOCOLS && selected.any { output.str("tag").orEmpty().startsWith(it) }
            }
            if (selected.isEmpty() || leaks) return@mapNotNull null
            JsonObject(balancer.filterKeys { !it.equals("selector", true) && !it.equals("fallbackTag", true) } + buildMap {
                put("selector", JsonArray(selected.map(::JsonPrimitive)))
                balancer.str("fallbackTag")?.takeIf { tag ->
                    tag in proxyTags && outputs.none { it.str("tag") == tag && it.str("protocol")?.lowercase() !in PROTOCOLS }
                }?.let { put("fallbackTag", JsonPrimitive(it)) }
            })
        }
    }

    internal fun securityIssue(raw: String): String? =
        (runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject)?.let(::securityIssue)

    private fun securityIssue(config: JsonObject): String? {
        val outbounds = config.array("outbounds").filterIsInstance<JsonObject>()
        val pending = ArrayDeque<JsonObject>()
        outbounds.firstOrNull()?.let(pending::add)
        for (balancer in config.obj("routing")?.array("balancers").orEmpty().filterIsInstance<JsonObject>()) {
            val prefixes = balancer.array("selector").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            outbounds.filterTo(pending) { output ->
                output.str("tag")?.let { tag -> prefixes.any(tag::startsWith) || tag == balancer.str("fallbackTag") } == true
            }
        }
        val visited = mutableSetOf<JsonObject>()
        while (pending.isNotEmpty()) {
            val output = pending.removeFirst()
            if (!visited.add(output)) continue
            val protocol = PROTOCOLS[output.str("protocol")?.lowercase()]
            if (protocol != null) {
                val settings = output.obj("settings") ?: JsonObject(emptyMap())
                val servers = (settings.array("vnext") + settings.array("servers")).filterIsInstance<JsonObject>()
                    .ifEmpty { listOf(settings) }
                val stream = output.obj("streamSettings")
                val tls = stream?.obj("tlsSettings")
                for (server in servers) {
                    val users = server.array("users").filterIsInstance<JsonObject>().ifEmpty { listOf(server) }
                    for (user in users) {
                        val params = buildMap {
                            put("security", stream?.str("security") ?: "none")
                            user.str("security")?.let { put("scy", it) }
                            server.str("method")?.let { put("method", it) }
                            if (tls?.bool("allowInsecure") == true) put("allowInsecure", "1")
                            tls?.str("pinnedPeerCertSha256")?.let { put("pcs", it) }
                        }
                        ProxyNode("", protocol, "", "", 0, "", params, "").securityIssue?.let { return it }
                    }
                }
            }
            val tags = buildSet {
                output.obj("proxySettings")?.str("tag")?.let(::add)
                fun visit(stream: JsonObject) {
                    stream.obj("sockopt")?.str("dialerProxy")?.let(::add)
                    if (stream.str("security")?.lowercase() == "tls") {
                        stream.obj("tlsSettings")?.obj("echSockopt")?.str("dialerProxy")?.let(::add)
                    }
                    val network = (stream.value("method") as? JsonPrimitive)?.contentOrNull
                        ?: (stream.value("network") as? JsonPrimitive)?.contentOrNull
                    if (network?.lowercase() in setOf("xhttp", "splithttp")) {
                        val xhttp = stream.obj("xhttpSettings") ?: stream.obj("splithttpSettings")
                        val extra = if (xhttp?.value("extra") != null) xhttp.obj("extra") else xhttp
                        extra?.obj("downloadSettings")?.let(::visit)
                    }
                }
                output.obj("streamSettings")?.let(::visit)
            }
            outbounds.filterTo(pending) { it.str("tag") in tags }
        }
        return null
    }

    private fun JsonObject.values(key: String): List<JsonElement> = entries.filter { it.key.equals(key, true) }.map { it.value }
    private fun JsonObject.value(key: String): JsonElement? = values(key).lastOrNull()
    private fun JsonObject.scalar(key: String): JsonPrimitive? = values(key).lastOrNull { it != JsonNull } as? JsonPrimitive
    private fun JsonObject.obj(key: String): JsonObject? {
        if (key.lowercase() in RAW_OBJECTS) return value(key) as? JsonObject
        return values(key).fold(null as JsonObject?) { previous, next ->
            if (next is JsonObject) mergeFields(previous ?: JsonObject(emptyMap()), next) else null
        }
    }

    private fun mergeFields(previous: JsonObject, next: JsonObject): JsonObject {
        val fields = linkedMapOf<String, JsonElement>()
        for ((key, value) in previous.entries.asSequence() + next.entries.asSequence()) {
            val name = key.lowercase()
            val old = fields[name]
            fields[name] = when {
                value is JsonObject && old is JsonObject && name !in RAW_OBJECTS -> mergeFields(old, value)
                value == JsonNull && old is JsonPrimitive && old != JsonNull && name !in setOf("method", "network") -> old
                else -> value
            }
        }
        return JsonObject(fields)
    }

    private val RAW_OBJECTS = setOf("settings", "extra", "proxysettings")

    private fun JsonObject.array(key: String): List<JsonElement> = (value(key) as? JsonArray).orEmpty()
    private fun JsonObject.str(key: String): String? = scalar(key)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(key: String): Int? = scalar(key)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
    private fun JsonObject.bool(key: String): Boolean? = scalar(key)?.let { it.booleanOrNull ?: (it.contentOrNull == "1") }
}

package app.farvater.core.parser

import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URLDecoder
import java.security.MessageDigest

object LinkParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(line: String, sourceId: String = ProxyNode.MANUAL_SOURCE): ProxyNode? {
        val raw = line.trim()
        if (raw.isEmpty() || raw.startsWith("#")) return null
        val scheme = raw.substringBefore("://", "").lowercase()
        return runCatching {
            when (scheme) {
                "vless" -> parseUriStyle(raw, Protocol.VLESS, sourceId)
                "trojan" -> parseUriStyle(raw, Protocol.TROJAN, sourceId)
                "hysteria2", "hy2" -> parseUriStyle(raw, Protocol.HYSTERIA2, sourceId)
                "vmess" -> parseVmess(raw, sourceId)
                "ss" -> parseShadowsocks(raw, sourceId)
                else -> null
            }
        }.getOrNull()
    }

    private fun parseUriStyle(raw: String, protocol: Protocol, sourceId: String): ProxyNode? {
        val body = raw.substringAfter("://")
        val (beforeFragment, fragment) = splitOnce(body, '#')
        val (beforeQuery, query) = splitOnce(beforeFragment, '?')
        val at = beforeQuery.lastIndexOf('@')
        if (at <= 0) return null
        val secret = pctDecode(beforeQuery.substring(0, at))
        val authority = beforeQuery.substring(at + 1).trimEnd('/')
        val (hostPort, hop) = if (protocol == Protocol.HYSTERIA2) hysteriaPorts(authority) else authority to null
        val (host, port) = parseHostPort(hostPort) ?: return null
        val params = parseQuery(query).let { q -> if (hop != null && "mport" !in q) q + ("mport" to hop) else q }
        return ProxyNode(
            id = nodeId(raw.substringBefore('#')),
            protocol = protocol,
            name = displayName(fragment, host, port),
            address = host,
            port = port,
            secret = secret,
            params = params,
            raw = raw,
            sourceId = sourceId,
        )
    }

    private fun hysteriaPorts(authority: String): Pair<String, String?> {
        val colon = authority.lastIndexOf(':')
        if (colon < 0 || authority.endsWith(']')) return "$authority:443" to null
        val ports = authority.substring(colon + 1)
        if (ports.all(Char::isDigit)) return authority to null
        return authority.substring(0, colon + 1) + ports.substringBefore(',').substringBefore('-') to ports
    }

    private fun parseVmess(raw: String, sourceId: String): ProxyNode? {
        val payload = Base64Util.decodeToString(raw.substringAfter("://").substringBefore('#')) ?: return null
        val obj = json.parseToJsonElement(payload).jsonObject
        fun s(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

        val host = s("add")
        val port = s("port").toIntOrNull() ?: return null
        val id = s("id")
        if (host.isBlank() || id.isBlank() || port !in 1..65535) return null
        val tls = s("tls").lowercase()
        val params = buildMap {
            put("type", s("net").ifBlank { "tcp" })
            put("security", if (tls == "tls" || tls == "reality") tls else "none")
            mapOf(
                "headerType" to s("type"), "host" to s("host"), "path" to s("path"),
                "sni" to s("sni"), "alpn" to s("alpn"), "fp" to s("fp"),
                "aid" to s("aid"), "scy" to s("scy"),
            ).forEach { (k, v) -> if (v.isNotBlank() && v != "none") put(k, v) }
        }
        return ProxyNode(
            id = nodeId(JsonObject(obj - "ps").toString()),
            protocol = Protocol.VMESS,
            name = s("ps").ifBlank { "$host:$port" },
            address = host,
            port = port,
            secret = id,
            params = params,
            raw = raw,
            sourceId = sourceId,
        )
    }

    private fun parseShadowsocks(raw: String, sourceId: String): ProxyNode? {
        val (beforeFragment, fragment) = splitOnce(raw.substringAfter("://"), '#')
        val main = beforeFragment.substringBefore('?').trimEnd('/')
        val at = main.lastIndexOf('@')
        val credentials: String
        val hostPort: String
        if (at > 0) {
            val userInfo = main.substring(0, at)
            credentials = Base64Util.decodeToString(userInfo)?.takeIf { ':' in it } ?: pctDecode(userInfo)
            hostPort = main.substring(at + 1)
        } else {
            val decoded = Base64Util.decodeToString(main) ?: return null
            val a = decoded.lastIndexOf('@')
            if (a <= 0) return null
            credentials = decoded.substring(0, a)
            hostPort = decoded.substring(a + 1)
        }
        if (':' !in credentials) return null
        val (host, port) = parseHostPort(hostPort) ?: return null
        return ProxyNode(
            id = nodeId(raw.substringBefore('#')),
            protocol = Protocol.SHADOWSOCKS,
            name = displayName(fragment, host, port),
            address = host,
            port = port,
            secret = credentials.substringAfter(':'),
            params = mapOf("method" to credentials.substringBefore(':')),
            raw = raw,
            sourceId = sourceId,
        )
    }

    private fun splitOnce(s: String, delimiter: Char): Pair<String, String> {
        val i = s.indexOf(delimiter)
        return if (i < 0) s to "" else s.substring(0, i) to s.substring(i + 1)
    }

    private fun parseHostPort(s: String): Pair<String, Int>? {
        val host: String
        val portStr: String
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end < 0) return null
            host = s.substring(1, end)
            portStr = s.substring(end + 1).removePrefix(":")
        } else {
            host = s.substringBeforeLast(':', "")
            portStr = s.substringAfterLast(':', "")
        }
        val port = portStr.toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        return host to port
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val k = pctDecode(pair.substringBefore('='))
            val v = pctDecode(pair.substringAfter('=', ""))
            if (k.isBlank()) null else k to v
        }.toMap()

    private fun pctDecode(s: String): String =
        runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    private fun displayName(fragment: String, host: String, port: Int): String =
        pctDecode(fragment).trim().ifBlank { "$host:$port" }

    internal fun nodeId(seed: String): String =
        MessageDigest.getInstance("SHA-1").digest(seed.trim().toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
}

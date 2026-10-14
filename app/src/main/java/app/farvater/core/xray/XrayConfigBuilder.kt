package app.farvater.core.xray

import app.farvater.core.model.NodeProfile
import app.farvater.core.model.Protocol
import app.farvater.core.model.canFragment
import app.farvater.core.model.canMux
import app.farvater.core.model.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object XrayConfigBuilder {
    val SOCKS_PORT: Int get() = LocalProxyAuth.port

    private val DIRECT_DOMAINS = listOf(
        "gosuslugi.ru", "gov.ru", "mos.ru", "nalog.ru",
        "sberbank.ru", "sber.ru", "vtb.ru", "tbank.ru", "tinkoff.ru", "alfabank.ru", "gazprombank.ru",
        "yandex.ru", "ya.ru", "yandex.net", "vk.com", "vk.ru", "userapi.com", "ok.ru", "mail.ru", "max.ru",
        "ozon.ru", "wildberries.ru", "wb.ru", "avito.ru", "2gis.ru",
    )
    private val PRIVATE_CIDRS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "169.254.0.0/16",
    )

    private val ENCRYPTED_DNS = listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query")

    fun build(
        node: ProxyNode,
        directRuServices: Boolean,
        forTest: Boolean = false,
        encryptedDns: Boolean = true,
        profile: NodeProfile? = null,
        socksPort: Int = SOCKS_PORT,
    ): String {
        val provider = if (node.isJson) Json.parseToJsonElement(node.raw).jsonObject else null
        val tuned = profile?.takeIf { provider == null }
        val fragment = tuned?.fragment == true && node.canFragment
        val proxies = provider?.get("outbounds")?.jsonArray ?: listOf(outbound(node, tuned, fragment))
        val balancers = provider?.get("routing")?.jsonObject?.get("balancers")?.jsonArray
        return buildJsonObject {
            putJsonObject("log") { put("loglevel", "warning") }
            if (!forTest) {
                putJsonArray("inbounds") {
                    addJsonObject {
                        put("tag", "socks")
                        put("listen", "127.0.0.1")
                        put("port", socksPort)
                        put("protocol", "socks")
                        putJsonObject("settings") {
                            put("auth", "password")
                            putJsonArray("accounts") {
                                addJsonObject {
                                    put("user", LocalProxyAuth.user)
                                    put("pass", LocalProxyAuth.pass)
                                }
                            }
                            put("udp", true)
                        }
                        putJsonObject("sniffing") {
                            put("enabled", true)
                            putJsonArray("destOverride") { add("http"); add("tls"); add("quic") }
                            put("routeOnly", true)
                        }
                    }
                }
                putJsonObject("dns") {
                    putJsonArray("servers") {
                        if (encryptedDns) ENCRYPTED_DNS.forEach { add(it) } else { add("1.1.1.1"); add("8.8.8.8") }
                    }
                    put("queryStrategy", "UseIPv4")
                }
            }
            putJsonArray("outbounds") {
                proxies.forEach { add(it) }
                if (fragment) addJsonObject {
                    put("tag", FRAGMENT_TAG)
                    put("protocol", "freedom")
                    putJsonObject("settings") {
                        putJsonObject("fragment") {
                            put("packets", "tlshello")
                            put("length", "100-200")
                            put("interval", "10-20")
                        }
                    }
                }
                addJsonObject { put("tag", "direct"); put("protocol", "freedom") }
                addJsonObject { put("tag", "block"); put("protocol", "blackhole") }
                if (!forTest && encryptedDns) addJsonObject { put("tag", "dns-out"); put("protocol", "dns") }
            }
            if (!forTest) {
                putJsonObject("routing") {
                    put("domainStrategy", "AsIs")
                    putJsonArray("rules") {
                        if (encryptedDns) addJsonObject {
                            put("type", "field")
                            putJsonArray("inboundTag") { add("socks") }
                            put("port", "53")
                            put("outboundTag", "dns-out")
                        }
                        addJsonObject {
                            put("type", "field")
                            putJsonArray("ip") { PRIVATE_CIDRS.forEach { add(it) } }
                            put("outboundTag", "direct")
                        }
                        if (directRuServices) addJsonObject {
                            put("type", "field")
                            putJsonArray("domain") { DIRECT_DOMAINS.forEach { add("domain:$it") } }
                            put("outboundTag", "direct")
                        }
                        balancers?.firstOrNull()?.jsonObject?.get("tag")?.let { tag ->
                            addJsonObject {
                                put("type", "field")
                                put("network", "tcp,udp")
                                put("balancerTag", tag)
                            }
                        }
                    }
                    balancers?.let { put("balancers", it) }
                }
                provider?.get("observatory")?.let { put("observatory", it) }
                provider?.get("burstObservatory")?.let { put("burstObservatory", it) }
            }
        }.toString()
    }

    private const val FRAGMENT_TAG = "fragment"

    private fun outbound(n: ProxyNode, profile: NodeProfile?, fragment: Boolean): JsonObject =
        withTuning(baseOutbound(n, profile?.fingerprint, fragment), profile?.mux == true && n.canMux)

    private fun withTuning(outbound: JsonObject, mux: Boolean): JsonObject = if (!mux) outbound else JsonObject(
        outbound + ("mux" to buildJsonObject {
            put("enabled", true)
            put("concurrency", 8)
            put("xudpConcurrency", 8)
            put("xudpProxyUDP443", "reject")
        }),
    )

    private fun baseOutbound(n: ProxyNode, fingerprint: String?, fragment: Boolean): JsonObject = when (n.protocol) {
        Protocol.VLESS -> buildJsonObject {
            put("tag", "proxy")
            put("protocol", "vless")
            putJsonObject("settings") {
                putJsonArray("vnext") {
                    addJsonObject {
                        put("address", n.address)
                        put("port", n.port)
                        putJsonArray("users") {
                            addJsonObject {
                                put("id", n.secret)
                                put("encryption", n.params["encryption"].nb() ?: "none")
                                n.params["flow"].nb()?.let { put("flow", it) }
                            }
                        }
                    }
                }
            }
            put("streamSettings", stream(n, fingerprint, fragment))
        }

        Protocol.VMESS -> buildJsonObject {
            put("tag", "proxy")
            put("protocol", "vmess")
            putJsonObject("settings") {
                putJsonArray("vnext") {
                    addJsonObject {
                        put("address", n.address)
                        put("port", n.port)
                        putJsonArray("users") {
                            addJsonObject {
                                put("id", n.secret)
                                put("alterId", n.params["aid"]?.toIntOrNull() ?: 0)
                                put("security", n.params["scy"].nb() ?: "auto")
                            }
                        }
                    }
                }
            }
            put("streamSettings", stream(n, fingerprint, fragment))
        }

        Protocol.TROJAN -> buildJsonObject {
            put("tag", "proxy")
            put("protocol", "trojan")
            putJsonObject("settings") {
                putJsonArray("servers") {
                    addJsonObject {
                        put("address", n.address)
                        put("port", n.port)
                        put("password", n.secret)
                    }
                }
            }
            put("streamSettings", stream(n, fingerprint, fragment))
        }

        Protocol.SHADOWSOCKS -> buildJsonObject {
            put("tag", "proxy")
            put("protocol", "shadowsocks")
            putJsonObject("settings") {
                putJsonArray("servers") {
                    addJsonObject {
                        put("address", n.address)
                        put("port", n.port)
                        put("method", n.params["method"].orEmpty())
                        put("password", n.secret)
                    }
                }
            }
            putJsonObject("streamSettings") { put("network", "tcp") }
        }

        Protocol.HYSTERIA2 -> buildJsonObject {
            put("tag", "proxy")
            put("protocol", "hysteria")
            putJsonObject("settings") {
                put("version", 2)
                put("address", n.address)
                put("port", n.port)
            }
            put("streamSettings", stream(n, fingerprint, fragment))
        }
    }

    private fun stream(n: ProxyNode, fingerprint: String?, fragment: Boolean): JsonObject = buildJsonObject {
        val p = n.params
        val hysteria = n.protocol == Protocol.HYSTERIA2
        val network = if (hysteria) "hysteria" else n.transport
        val security = when {
            hysteria -> "tls"
            n.security.isNotBlank() -> n.security
            n.protocol == Protocol.TROJAN -> "tls"
            else -> "none"
        }
        val sni = p["sni"].nb() ?: p["peer"].nb() ?: p["host"].nb()
        val path = p["path"].nb()
        val host = p["host"].nb()

        put("network", network)
        put("security", security)
        if (fragment) putJsonObject("sockopt") { put("dialerProxy", FRAGMENT_TAG) }

        when (security) {
            "tls" -> putJsonObject("tlsSettings") {
                sni?.let { put("serverName", it) }
                if (!hysteria) put("fingerprint", fingerprint ?: p["fp"].nb() ?: "chrome")
                (p["alpn"].nb() ?: "h3".takeIf { hysteria })?.let { alpn ->
                    putJsonArray("alpn") { alpn.split(',').forEach { add(it.trim()) } }
                }
                n.pinnedCert?.let { put("pinnedPeerCertSha256", it) }
            }
            "reality" -> putJsonObject("realitySettings") {
                put("serverName", sni.orEmpty())
                put("fingerprint", fingerprint ?: p["fp"].nb() ?: "chrome")
                put("publicKey", p["pbk"].orEmpty())
                put("shortId", p["sid"].orEmpty())
                p["spx"].nb()?.let { put("spiderX", it) }
                p["pqv"].nb()?.let { put("mldsa65Verify", it) }
            }
        }

        when (network) {
            "hysteria" -> {
                putJsonObject("hysteriaSettings") {
                    put("version", 2)
                    put("auth", n.secret)
                }
                val obfs = p["obfs-password"].nb()?.takeIf { (p["obfs"].nb() ?: "salamander") == "salamander" }
                val hop = p["mport"].nb()
                if (obfs != null || hop != null) putJsonObject("finalmask") {
                    putJsonArray("udp") {
                        obfs?.let {
                            addJsonObject {
                                put("type", "salamander")
                                putJsonObject("settings") { put("password", it) }
                            }
                        }
                        hop?.let {
                            addJsonObject {
                                put("type", "udphop")
                                putJsonObject("settings") {
                                    put("mode", "intervallocal,intervalremote")
                                    put("remotePorts", it)
                                }
                            }
                        }
                    }
                }
            }
            "ws" -> putJsonObject("wsSettings") {
                path?.let { put("path", it) }
                host?.let { put("host", it) }
            }
            "httpupgrade" -> putJsonObject("httpupgradeSettings") {
                path?.let { put("path", it) }
                host?.let { put("host", it) }
            }
            "xhttp", "splithttp" -> putJsonObject("xhttpSettings") {
                path?.let { put("path", it) }
                host?.let { put("host", it) }
                p["mode"].nb()?.let { put("mode", it) }
                p["extra"].nb()?.let { extra ->
                    runCatching { Json.parseToJsonElement(extra).jsonObject }.getOrNull()?.let { put("extra", it) }
                }
            }
            "grpc" -> putJsonObject("grpcSettings") {
                put("serviceName", p["serviceName"].nb() ?: path.orEmpty())
                if (p["mode"] == "multi") put("multiMode", true)
                p["authority"].nb()?.let { put("authority", it) }
            }
            "tcp", "raw" -> if (p["headerType"] == "http") putJsonObject("tcpSettings") {
                putJsonObject("header") {
                    put("type", "http")
                    putJsonObject("request") {
                        putJsonArray("path") { add(path ?: "/") }
                        putJsonObject("headers") {
                            putJsonArray("Host") { host?.split(',')?.forEach { add(it.trim()) } }
                        }
                    }
                }
            }
        }
    }

    private fun String?.nb(): String? = this?.takeIf { it.isNotBlank() }
}

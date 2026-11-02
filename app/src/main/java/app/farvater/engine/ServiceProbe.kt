package app.farvater.engine

import app.farvater.core.model.NodeProfile
import app.farvater.core.model.ProxyNode
import app.farvater.core.xray.LocalProxyAuth
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.data.ServiceCheck
import app.farvater.data.ServiceKind
import app.farvater.data.ServiceReason
import app.farvater.data.ServiceTarget
import app.farvater.data.ServiceVerdict
import app.farvater.net.await
import app.farvater.net.orDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

object ServiceProbe {
    private val gate = Semaphore(1)
    private val base = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val titles = mapOf(ServiceKind.GEMINI to setOf("Gemini", "Google Gemini"), ServiceKind.CHATGPT to setOf("ChatGPT"),
        ServiceKind.CLAUDE to setOf("Claude", "Claude / Login"), ServiceKind.YOUTUBE to setOf("YouTube"))

    suspend fun check(node: ProxyNode, profile: NodeProfile?, targets: List<ServiceTarget>, onResult: suspend (ServiceTarget, ServiceCheck) -> Unit): Unit = gate.withPermit {
        withContext(Dispatchers.IO) {
            val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            var probe: Any? = null
            try {
                val config = configuration(node, profile, port)
                probe = ProbeLaunch.await({ XrayEngine.startProbe(config) }, XrayEngine::stopProbe)
                coroutineContext.ensureActive()
                if (probe == null) return@withContext
                LocalProxyAuth.open(port)
                val client = base.newBuilder().proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))).build()
                checkTargets(targets, { request(client, it) }, onResult)
            } finally {
                LocalProxyAuth.close(port)
                XrayEngine.stopProbe(probe)
            }
        }
    }

    internal suspend fun checkTargets(targets: List<ServiceTarget>, fetch: suspend (ServiceTarget) -> ServiceCheck,
        onResult: suspend (ServiceTarget, ServiceCheck) -> Unit) {
        for (target in targets) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            onResult(target, fetch(target))
        }
    }

    internal fun configuration(node: ProxyNode, profile: NodeProfile?, port: Int): String {
        val original = Json.parseToJsonElement(XrayConfigBuilder.build(node, false, encryptedDns = false,
            profile = profile, socksPort = port)).jsonObject
        val routing = original.getValue("routing").jsonObject
        val rules = routing.getValue("rules").jsonArray.filter {
            it.jsonObject["outboundTag"]?.jsonPrimitive?.content != "direct"
        }
        return JsonObject(original + mapOf(
            "log" to JsonObject(mapOf("loglevel" to JsonPrimitive("none"))),
            "routing" to JsonObject(routing + ("rules" to JsonArray(rules))),
        )).toString()
    }

    internal suspend fun request(client: OkHttpClient, target: ServiceTarget): ServiceCheck =
        orDefault(result(ServiceVerdict.UNKNOWN, ServiceReason.NETWORK)) {
            var url = target.url.toHttpUrl()
            repeat(4) {
                var next: HttpUrl? = null
                val checked = client.newCall(Request.Builder().url(url).header("Accept-Language", "en")
                    .header("User-Agent", "Farvater/${app.farvater.BuildConfig.VERSION_NAME}").build()).await { response ->
                    if (response.header("cf-mitigated").equals("challenge", true)) {
                        result(ServiceVerdict.UNKNOWN, ServiceReason.CHALLENGE)
                    } else if (target.kind != ServiceKind.CUSTOM && response.isRedirect) {
                        val location = response.header("Location")?.let(url::resolve)
                        if (target.kind == ServiceKind.CLAUDE && location != null &&
                            location.isHttps && location.host in setOf("claude.com", "www.anthropic.com") &&
                            location.encodedPath == "/app-unavailable-in-region" && location.port == 443 &&
                            location.username.isEmpty() && location.password.isEmpty()) {
                            result(ServiceVerdict.REJECTED, ServiceReason.REGION)
                        } else if (location != null && allowedRedirect(target, location)) {
                            next = location
                            null
                        } else result(ServiceVerdict.UNKNOWN, ServiceReason.UNEXPECTED)
                    } else classify(target, response)
                }
                if (checked != null) return@orDefault checked
                url = next ?: return@orDefault result(ServiceVerdict.UNKNOWN, ServiceReason.UNEXPECTED)
            }
            result(ServiceVerdict.UNKNOWN, ServiceReason.UNEXPECTED)
        }

    private fun allowedRedirect(target: ServiceTarget, url: HttpUrl): Boolean {
        val hosts = when (target.kind) {
            ServiceKind.GEMINI -> setOf("gemini.google.com")
            ServiceKind.CHATGPT -> setOf("chatgpt.com", "www.chatgpt.com")
            ServiceKind.CLAUDE -> setOf("claude.ai")
            ServiceKind.YOUTUBE -> setOf("www.youtube.com")
            ServiceKind.CUSTOM -> emptySet()
        }
        return url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.host in hosts
    }

    internal fun classify(target: ServiceTarget, response: Response): ServiceCheck {
        if (response.header("cf-mitigated").equals("challenge", true)) return result(ServiceVerdict.UNKNOWN, ServiceReason.CHALLENGE)
        if (target.kind == ServiceKind.CUSTOM) {
            return result(if (response.code == target.expectedCode) ServiceVerdict.RESPONDED else ServiceVerdict.REJECTED, ServiceReason.STATUS)
        }
        if (response.code == 429 || response.code >= 500) return result(ServiceVerdict.UNKNOWN, ServiceReason.HTTP_ERROR)
        if (response.code in setOf(401, 403, 451)) return result(ServiceVerdict.REJECTED, ServiceReason.HTTP_ERROR)
        if (response.code != 200) return result(ServiceVerdict.UNKNOWN, ServiceReason.UNEXPECTED)
        val type = response.body?.contentType()
        if (type?.type != "text" || type.subtype != "html") return result(ServiceVerdict.UNKNOWN, ServiceReason.UNEXPECTED)
        val source = response.body?.source() ?: return result(ServiceVerdict.UNKNOWN, ServiceReason.UNEXPECTED)
        val heading = readTitle(source).filterNot { it in "\u200e\u200f\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069" }
        return if (titles[target.kind]?.let { it.any { expected -> heading.trim().equals(expected, true) } } == true) {
            result(ServiceVerdict.RESPONDED, ServiceReason.PAGE)
        } else result(ServiceVerdict.UNKNOWN, ServiceReason.UNEXPECTED)
    }

    private fun readTitle(source: BufferedSource): String {
        val bounded = object : ForwardingSource(source) {
            var remaining = 1024 * 1024L
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (remaining == 0L) return -1L
                return super.read(sink, minOf(byteCount, remaining)).also { if (it > 0) remaining -= it }
            }
        }
        val opening = "<title"
        val closing = "</title>"
        val heading = StringBuilder(300)
        var openingMatch = 0
        var closingMatch = 0
        var stage = 0
        val chars = CharArray(8192)
        InputStreamReader(bounded.buffer().inputStream(), Charsets.UTF_8).use { reader ->
            while (true) {
                val count = reader.read(chars)
                if (count < 0) break
                for (index in 0 until count) {
                    val char = chars[index]
                    val lower = char.lowercaseChar()
                    openingMatch = if (lower == opening[openingMatch]) openingMatch + 1 else if (lower == '<') 1 else 0
                    if (openingMatch == opening.length) {
                        openingMatch = 0
                        heading.setLength(0)
                        stage = 1
                        continue
                    }
                    when (stage) {
                        1 -> if (char == '>') stage = 2
                        2 -> when {
                            char == '<' -> { stage = 3; closingMatch = 1 }
                            heading.length == 300 -> stage = 0
                            else -> heading.append(char)
                        }
                        3 -> if (lower == closing[closingMatch]) {
                            closingMatch++
                            if (closingMatch == closing.length) {
                                if (heading.isNotEmpty()) return heading.toString()
                                stage = 0
                            }
                        } else stage = 0
                    }
                }
            }
        }
        return ""
    }

    private fun result(verdict: ServiceVerdict, reason: ServiceReason) = ServiceCheck(verdict, reason, System.currentTimeMillis())
}

package app.farvater.net

import app.farvater.R
import app.farvater.data.LocalizedIOException
import app.farvater.data.LocalizedHostException
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.InetAddress

object SafeHttp {
    const val MAX_REDIRECTS = 5
    const val MAX_URL_LENGTH = 4096

    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun isLocalAddress(address: InetAddress): Boolean =
        address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress || address.isMulticastAddress

    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.').removePrefix("[").removeSuffix("]")
        if (h.isEmpty() || h == "localhost" || h.endsWith(".localhost")) return true
        if (!h.contains(':') && !ipv4.matches(h)) return false
        return runCatching { isLocalAddress(InetAddress.getByName(h)) }.getOrDefault(true)
    }

    fun parse(url: String): HttpUrl? {
        if (url.length > MAX_URL_LENGTH) return null
        val parsed = url.trim().toHttpUrlOrNull() ?: return null
        return parsed.takeUnless { isLocalHost(it.host) }
    }

    val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (isLocalHost(hostname)) throw LocalizedHostException(R.string.error_local_host, hostname)
            return Dns.SYSTEM.lookup(hostname).filterNot(::isLocalAddress).ifEmpty {
                throw LocalizedHostException(R.string.error_local_host, hostname)
            }
        }
    }

    fun redirectTarget(from: HttpUrl, location: String?): HttpUrl {
        val next = location?.let { from.resolve(it) } ?: throw LocalizedIOException(R.string.error_redirect_no_address)
        if (from.isHttps && !next.isHttps) throw LocalizedIOException(R.string.error_redirect_downgrade)
        if (isLocalHost(next.host)) throw LocalizedIOException(R.string.error_redirect_local)
        return next
    }

    fun execute(client: OkHttpClient, url: HttpUrl, headers: Map<String, String>, privateHeaders: Map<String, String> = emptyMap()): Response {
        var current = url
        var keepPrivate = true
        repeat(MAX_REDIRECTS + 1) {
            val request = Request.Builder()
                .url(current)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .apply { if (keepPrivate) privateHeaders.forEach { (k, v) -> header(k, v) } }
                .build()
            val response = client.newCall(request).execute()
            if (!response.isRedirect) return response
            val location = response.header("Location")
            response.close()
            val next = redirectTarget(current, location)
            if (next.host != url.host || next.scheme != url.scheme || next.port != url.port) keepPrivate = false
            current = next
        }
        throw LocalizedIOException(R.string.error_redirect_limit)
    }

    fun OkHttpClient.Builder.hardened(): OkHttpClient.Builder =
        dns(dns).followRedirects(false).followSslRedirects(false)
}

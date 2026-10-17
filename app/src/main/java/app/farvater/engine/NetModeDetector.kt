package app.farvater.engine

import app.farvater.core.model.NetMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
import java.util.concurrent.TimeUnit

object NetModeDetector {
    private const val SAMPLE = 64 * 1024
    private const val ENOUGH = 32 * 1024

    private val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .build()

    private val foreign = listOf(
        "https://dl.google.com/chrome/install/latest/chrome_installer.exe",
        "https://speed.cloudflare.com/__down?bytes=$SAMPLE",
        "https://raw.githubusercontent.com/farvater-app/farvater/main/README.md",
    )
    private val domestic = listOf(
        "https://ya.ru/",
        "https://vk.com/",
        "https://www.gosuslugi.ru/",
    )

    suspend fun detect(): NetMode? = when {
        anyPasses(foreign, ::downloads) -> NetMode.BLACK
        anyPasses(domestic, ::reachable) -> NetMode.WHITE
        else -> null
    }

    private suspend fun anyPasses(urls: List<String>, check: (String) -> Boolean): Boolean = channelFlow {
        urls.forEach { url -> launch { send(runInterruptible(Dispatchers.IO) { check(url) }) } }
    }.firstOrNull { it } != null

    private fun reachable(url: String): Boolean = runCatching {
        client.newCall(Request.Builder().url(url).head().build()).execute().use { true }
    }.getOrDefault(false)

    private fun downloads(url: String): Boolean = runCatching {
        val request = Request.Builder().url(url).header("Range", "bytes=0-${SAMPLE - 1}").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val source = response.body?.byteStream() ?: return@use false
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (total < ENOUGH) {
                val n = source.read(buffer)
                if (n < 0) break
                total += n
            }
            total >= ENOUGH
        }
    }.getOrDefault(false)
}

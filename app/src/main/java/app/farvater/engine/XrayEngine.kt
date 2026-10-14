package app.farvater.engine

import android.content.Context
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

object XrayEngine {
    private const val LIB = "libv2ray.Libv2ray"
    private const val CALLBACK = "libv2ray.CoreCallbackHandler"

    private var controller: Any? = null
    var lastError: String? = null
        private set

    val isAvailable: Boolean by lazy { runCatching { Class.forName(LIB) }.isSuccess }

    val version: String? by lazy {
        if (!isAvailable) null
        else runCatching { libMethod("checkVersionX")?.invoke(null) as? String }.getOrNull()
    }

    private val measureMethod: Method? by lazy { libMethod("measureOutboundDelay") }

    private fun libMethod(name: String): Method? =
        runCatching { Class.forName(LIB).methods.firstOrNull { it.name == name } }.getOrNull()

    private val ready = CountDownLatch(1)

    fun init(context: Context) {
        val dir = context.filesDir.absolutePath
        thread(name = "xray-init") {
            if (isAvailable) runCatching {
                val m = libMethod("initCoreEnv") ?: libMethod("initV2Env")
                if (m != null && m.parameterCount == 2) m.invoke(null, dir, "")
            }
            ready.countDown()
        }
    }

    val isRunning: Boolean get() = controller != null

    @Synchronized
    fun start(config: String): Result<Unit> = runCatching {
        ready.await()
        check(isAvailable) { "ядро Xray не встроено — добавьте libv2ray.aar в app/libs" }
        stop()
        controller = launch(config)
        lastError = null
    }.onFailure { lastError = (it.cause ?: it).message }

    fun startProbe(config: String): Any? = runCatching {
        ready.await()
        if (!isAvailable) null else launch(config)
    }.getOrNull()

    fun stopProbe(probe: Any?) {
        val c = probe ?: return
        runCatching { c.javaClass.methods.first { it.name == "stopLoop" }.invoke(c) }
    }

    private fun launch(config: String): Any {
        val handlerClass = Class.forName(CALLBACK)
        val handler = Proxy.newProxyInstance(handlerClass.classLoader, arrayOf(handlerClass)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "FarvaterCoreCallback"
                else -> when (method.returnType) {
                    java.lang.Long.TYPE -> 0L
                    java.lang.Integer.TYPE -> 0
                    java.lang.Boolean.TYPE -> false
                    else -> null
                }
            }
        }
        val ctrl = libMethod("newCoreController")?.invoke(null, handler)
            ?: error("newCoreController недоступен")
        val startLoop = ctrl.javaClass.methods.first { it.name == "startLoop" }
        when (startLoop.parameterCount) {
            1 -> startLoop.invoke(ctrl, config)
            2 -> startLoop.invoke(ctrl, config, 0)
            else -> error("неизвестная сигнатура startLoop")
        }
        return ctrl
    }

    @Synchronized
    fun stop() {
        val c = controller ?: return
        runCatching { c.javaClass.methods.first { it.name == "stopLoop" }.invoke(c) }
        controller = null
    }

    fun measureDelay(config: String, url: String): Long {
        val m = measureMethod ?: return -1
        return runCatching {
            ready.await()
            (m.invoke(null, config, url) as Number).toLong()
        }.getOrDefault(-1L)
    }
}

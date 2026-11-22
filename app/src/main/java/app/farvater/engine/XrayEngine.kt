package app.farvater.engine

import app.farvater.R
import app.farvater.data.LocalizedStateException
import android.content.Context
import android.app.ActivityManager
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object XrayEngine {
    private const val LIB = "libv2ray.Libv2ray"
    private const val CALLBACK = "libv2ray.CoreCallbackHandler"

    @Volatile private var controller: Any? = null

    val isAvailable: Boolean by lazy { runCatching { Class.forName(LIB) }.isSuccess }

    val version: String? by lazy {
        if (!isAvailable) null
        else runCatching { libMethod("checkVersionX")?.invoke(null) as? String }.getOrNull()
    }

    private val measureMethod: Method? by lazy { libMethod("measureOutboundDelay") }

    private const val MEASURE_TIMEOUT_MS = 8000L

    @Volatile private var probeBudget = NativeProbeBudget(2)
    val measurementParallelism: Int get() = probeBudget.measurementLimit
    private val measurers by lazy { NativeMeasurements(probeBudget) }

    private fun libMethod(name: String): Method? =
        runCatching { Class.forName(LIB).methods.firstOrNull { it.name == name } }.getOrNull()

    private val ready = CountDownLatch(1)
    private val initializing = java.util.concurrent.atomic.AtomicBoolean()
    @Volatile private var initialized = false

    fun init(context: Context) {
        if (!initializing.compareAndSet(false, true)) return
        val app = context.applicationContext
        thread(name = "xray-init") {
            try {
                val memory = app.getSystemService(ActivityManager::class.java)
                val lowRam = memory?.isLowRamDevice != false
                probeBudget = NativeProbeBudget(if (lowRam) 2 else 8)
                if (!isAvailable) return@thread
                Class.forName("go.Seq").getMethod("setContext", Context::class.java).invoke(null, app)
                requireNotNull(libMethod("configureMobileRuntime")).invoke(null, (memory?.memoryClass ?: 96).toLong(), lowRam)
                requireNotNull(libMethod("initCoreEnv")).invoke(null, app.filesDir.absolutePath, "")
                initialized = true
            } catch (_: Exception) {
                initialized = false
            } finally {
                ready.countDown()
            }
        }
    }

    val isRunning: Boolean get() = controller != null

    @Synchronized
    fun start(config: String): Result<Unit> = runCatching {
        ready.await()
        if (!initialized) throw LocalizedStateException(R.string.error_xray_missing)
        check(stopCurrent())
        try {
            controller = launch(config) { controller = it }
        } catch (failure: Throwable) {
            stopCurrent()
            throw failure
        }
    }

    private val cleanup = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "farvater-probe-release").apply { isDaemon = true }
    }

    internal fun releaseProbe(core: NativeProbeHandle<*>) {
        core.release { retry -> cleanup.schedule(retry, 5, TimeUnit.SECONDS) }
    }

    internal fun startProbe(config: String, lane: ProbeLane): NativeProbeHandle<Any>? {
        var lease: NativeProbeBudget.Lease? = null
        var created: Any? = null
        return try {
            ready.await()
            if (!initialized) return null
            val acquired = probeBudget.acquire(lane, 4_000) ?: throw ProbeDeferredException()
            lease = acquired
            NativeProbeHandle(launch(config) { created = it }, acquired, ::stopController)
        } catch (error: Exception) {
            val partial = created
            val acquired = lease
            if (partial != null && acquired != null) releaseProbe(NativeProbeHandle(partial, acquired, ::stopController))
            else acquired?.close()
            if (error is ProbeDeferredException) throw error
            if (error is InterruptedException) Thread.currentThread().interrupt()
            null
        }
    }

    private fun stopController(controller: Any) {
        controller.javaClass.methods.first { it.name == "stopLoop" }.invoke(controller)
    }

    private fun launch(config: String, onCreated: (Any) -> Unit = {}): Any {
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
            ?: throw LocalizedStateException(R.string.error_core_controller)
        onCreated(ctrl)
        val startLoop = ctrl.javaClass.methods.first { it.name == "startLoop" }
        when (startLoop.parameterCount) {
            1 -> startLoop.invoke(ctrl, config)
            2 -> startLoop.invoke(ctrl, config, 0)
            else -> throw LocalizedStateException(R.string.error_core_signature)
        }
        return ctrl
    }

    @Synchronized
    fun stop() {
        stopCurrent()
    }

    private fun stopCurrent(): Boolean {
        val current = controller ?: return true
        if (runCatching { stopController(current) }.isFailure) return false
        controller = null
        return true
    }

    fun measureDelay(config: String, url: String): Long = measureDelayDetailed(config, url).delayMs

    fun measureDelayDetailed(config: String, url: String): DelayMeasurement {
        return measure(config, url, ProbeLane.NORMAL)
    }

    fun measureRecovery(config: String, url: String): DelayMeasurement = measure(config, url, ProbeLane.RECOVERY)

    private fun measure(config: String, url: String, lane: ProbeLane): DelayMeasurement {
        val m = measureMethod ?: return DelayMeasurement(-1)
        try { ready.await() } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return DelayMeasurement(-1)
        }
        if (!initialized) return DelayMeasurement(-1)
        return measurers.measure(lane) {
            (m.invoke(null, config, url) as Number).toLong()
        }
    }

    internal fun awaitDelay(task: Future<Long>): DelayMeasurement = try {
        DelayMeasurement(task.get(MEASURE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
    } catch (error: Exception) {
        task.cancel(true)
        if (error is InterruptedException) Thread.currentThread().interrupt()
        DelayMeasurement(-1, TestFailure.from(error))
    }
}

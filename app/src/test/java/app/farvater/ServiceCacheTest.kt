package app.farvater

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ServiceCacheTest {
    @Test
    fun pruningCannotOverwriteConcurrentFreshResult() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val health = app.getSharedPreferences("farvater_health", 0)
        val expired = ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, 1)
        health.edit().clear().putString("service_checks", Json.encodeToString(mapOf("old" to expired))).commit()
        val read = CountDownLatch(1)
        val release = CountDownLatch(1)
        val once = AtomicBoolean()
        val wrapped = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            val result = method.invoke(health, *(args ?: emptyArray()))
            if (method.name == "getString" && args?.get(0) == "service_checks" && once.compareAndSet(false, true)) {
                read.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            result
        } as SharedPreferences
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "farvater_health") wrapped else super.getSharedPreferences(name, mode)
        }
        val prefs = Prefs(context)
        val reader = thread { prefs.serviceChecks }
        assertTrue(read.await(5, TimeUnit.SECONDS))
        val fresh = ServiceCheck(ServiceVerdict.RESPONDED, ServiceReason.PAGE, System.currentTimeMillis())
        val writer = thread { prefs.serviceChecks = mapOf("new" to fresh) }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (writer.isAlive && writer.state != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield()
        release.countDown()
        reader.join(5000)
        writer.join(5000)
        assertFalse(reader.isAlive || writer.isAlive)
        assertEquals(fresh, prefs.serviceChecks["new"])
        assertFalse(ServiceChecks.due(prefs.serviceChecks["new"]))
    }
}

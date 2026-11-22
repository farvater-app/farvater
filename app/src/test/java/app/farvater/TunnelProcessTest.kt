package app.farvater

import android.app.Application
import android.content.ComponentName
import android.net.VpnService
import androidx.test.core.app.ApplicationProvider
import app.farvater.vpn.TunnelCoordinator
import app.farvater.vpn.FarvaterVpnService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TunnelProcessTest {
    @Test fun shortConfigurationsDoNotAllocateAnIpcPipe() {
        val config = "{\"active\":true}"
        val result = app.farvater.engine.CorePayload.send(config) { bundle ->
            assertFalse(bundle.containsKey("pipe"))
            app.farvater.engine.CorePayload.receive(bundle)
        }
        assertEquals(config, result)
    }

    @Test fun aLargeUtf8ConfigurationIsTransferredOutsideTheBinderTransaction() {
        val config = "маршрут/🙂/".repeat(120_000)
        val result = app.farvater.engine.CorePayload.send(config) { payload ->
            assertFalse(payload.containsKey("text"))
            val reader = requireNotNull(payload.getParcelable<android.os.ParcelFileDescriptor>("pipe"))
            // Robolectric models a pipe with a regular file whose first read can return EOF immediately.
            val expected = config.toByteArray().size.toLong()
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (reader.statSize < expected && System.nanoTime() < deadline) Thread.sleep(5)
            assertEquals(expected, reader.statSize)
            app.farvater.engine.CorePayload.receive(payload)
        }
        assertEquals(config, result)
    }

    @Test fun truncatedConfigurationCannotReachTheCore() {
        val file = java.io.File.createTempFile("incomplete-config", ".json")
        try {
            file.writeText("{}")
            val payload = android.os.Bundle().apply {
                putInt("chars", 10_000)
                putParcelable("pipe", android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY))
            }
            assertThrows(java.io.IOException::class.java) { app.farvater.engine.CorePayload.receive(payload) }
        } finally {
            file.delete()
        }
    }

    @Test fun aStopSurvivesCoordinatorDeathAndNewerCommandsReplayInOrder() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val receiver = mutableListOf<Pair<String?, Int>>()
        val control = object : app.farvater.vpn.ITunnelControl.Stub() {
            override fun command(endpoint: app.farvater.vpn.IVpnEndpoint, intent: android.content.Intent?, startId: Int) {
                receiver += intent?.action to startId
            }
            override fun ended(endpoint: app.farvater.vpn.IVpnEndpoint, revoked: Boolean) = Unit
        }
        val bind = android.content.Intent(app, TunnelCoordinator::class.java)
        val shadow = org.robolectric.Shadows.shadowOf(app)
        shadow.setComponentNameAndServiceForBindServiceForIntent(bind, bind.component!!, control)
        val service = org.robolectric.Robolectric.buildService(FarvaterVpnService::class.java).create()
        try {
            val vpn = service.get()
            val main = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            main.idle()
            val connection = shadow.boundServiceConnections.single()
            val stop = android.content.Intent().setAction(TunnelCoordinator.ACTION_STOP)
            vpn.onStartCommand(stop, 0, 17)
            val old = vpn.endpoint
            connection.onServiceDisconnected(bind.component!!)
            old.finishStart(17)
            old.received(17)
            main.idle()
            assertFalse(org.robolectric.Shadows.shadowOf(vpn).isStoppedBySelf)
            connection.onServiceConnected(bind.component!!, control)
            assertEquals(listOf(stop.action to 17, stop.action to 17), receiver)
            vpn.endpoint.received(17)
            main.idle()
            connection.onServiceDisconnected(bind.component!!)
            connection.onServiceConnected(bind.component!!, control)
            assertEquals(null to 17, receiver.last())
            vpn.endpoint.received(17)
            main.idle()

            receiver.clear()
            val start = android.content.Intent().setAction(TunnelCoordinator.ACTION_START)
            vpn.onStartCommand(stop, 0, 18)
            vpn.onStartCommand(start, 0, 19)
            vpn.endpoint.received(19)
            main.idle()
            connection.onServiceDisconnected(bind.component!!)
            connection.onServiceConnected(bind.component!!, control)
            val commands = listOf(stop.action to 18, start.action to 19)
            assertEquals(commands + commands, receiver)
        } finally { service.destroy() }
    }

    @Test fun probeProviderIsPrivateAndSharesTheEngineProcess() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val info = app.packageManager.resolveContentProvider(app.packageName + ".probes", 0)!!
        val owner = app.packageManager.getServiceInfo(ComponentName(app, TunnelCoordinator::class.java), 0)
        assertEquals(app.packageName + ":vpn", info.processName)
        assertFalse(info.exported)
        assertFalse(owner.exported)
        assertNotEquals(info.processName, owner.processName)
    }

    @Test fun androidStartsTheVpnInItsOwnPrivateProcess() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val service = app.packageManager.getServiceInfo(ComponentName(app, FarvaterVpnService::class.java), 0)
        assertEquals(app.packageName + ":vpn", service.processName)
        assertEquals("android.permission.BIND_VPN_SERVICE", service.permission)
        assertFalse(service.exported)
        assertTrue(VpnService::class.java.isAssignableFrom(FarvaterVpnService::class.java))
    }
}

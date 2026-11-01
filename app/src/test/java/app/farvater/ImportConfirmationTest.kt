package app.farvater

import android.app.Application
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.farvater.ui.MainViewModel
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ImportConfirmationTest {
    @Test fun replacementCannotBeAcceptedOrDismissedByOldDialog() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        prepareRuntime(app)
        val vm = MainViewModel(app)
        val first = "vless://test@first.example.test:443?security=tls"
        val second = "vless://test@second.example.test:443?security=tls"
        try {
            vm.requestImport(first)
            vm.requestImport(second)
            vm.confirmImport(first)
            vm.dismissImport(first)
            assertEquals(second, vm.state.value.pendingImport)
            assertTrue(App.repo.loadManual().nodes.isEmpty())
            vm.confirmImport(second)
            assertNull(vm.state.value.pendingImport)
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (App.repo.loadManual().nodes.isEmpty() && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertEquals(listOf("second.example.test"), App.repo.loadManual().nodes.map { it.address })
            vm.confirmImport(first)
            vm.confirmImport(second)
            assertNull(vm.state.value.pendingImport)
            assertEquals(1, App.repo.loadManual().nodes.size)
        } finally {
            vm.viewModelScope.cancel()
        }
    }
}

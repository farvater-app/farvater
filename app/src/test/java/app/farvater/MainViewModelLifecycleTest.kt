package app.farvater

import android.app.Application
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.model.NodeProfile
import app.farvater.core.model.NetMode
import app.farvater.core.catalog.BuiltInCatalog
import app.farvater.core.model.ProxyNode
import app.farvater.core.parser.LinkParser
import app.farvater.core.parser.SubscriptionParser
import app.farvater.data.BackupNodes
import app.farvater.data.SourceSnapshot
import app.farvater.data.SubscriptionRepository
import app.farvater.data.UpdateInfo
import app.farvater.ui.GalsStep
import app.farvater.ui.MainViewModel
import app.farvater.ui.TestProgress
import app.farvater.ui.UiState
import app.farvater.ui.UpdateStage
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [MainViewModelLifecycleTest.Repository::class],
    instrumentedPackages = ["app.farvater.data.SubscriptionRepository"])
class MainViewModelLifecycleTest {
    private lateinit var vm: MainViewModel
    private lateinit var state: MutableStateFlow<UiState>

    @Before
    fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        prepareRuntime(app)
        Repository.afterRemoval = null
        vm = MainViewModel(app)
        @Suppress("UNCHECKED_CAST")
        val flow = field("_state").get(vm) as MutableStateFlow<UiState>
        state = flow
    }

    @After
    fun finish() {
        vm.viewModelScope.cancel()
        Repository.afterRemoval = null
    }

    @Test
    fun closingAndReopeningActiveDownloadKeepsDuplicateLaunchGuard() {
        state.value = state.value.copy(showUpdate = true, update = UpdateInfo(1030, "test"), updateStage = UpdateStage.Downloading(0.4f))
        vm.dismissUpdate()
        assertFalse(state.value.showUpdate)
        assertEquals(UpdateStage.Downloading(0.4f), state.value.updateStage)
        vm.openUpdate()
        vm.installUpdate()
        assertEquals(UpdateStage.Downloading(0.4f), state.value.updateStage)
        assertNull(state.value.message)
    }

    @Test
    fun dismissingActiveCheckDoesNotAllowAnotherCheck() {
        state.value = state.value.copy(showUpdate = true, updateStage = UpdateStage.Checking)
        vm.dismissUpdate()
        assertEquals(UpdateStage.Checking, state.value.updateStage)
    }

    @Test
    fun dismissingFailedUpdateAllowsRetry() {
        state.value = state.value.copy(showUpdate = true, updateStage = UpdateStage.Failed("test"))
        vm.dismissUpdate()
        assertEquals(UpdateStage.Idle, state.value.updateStage)
    }

    @Test
    fun cancellingSearchCancelsBothJobsAndClearsProgress() {
        val test = Job()
        val path = Job()
        field("testJob").set(vm, test)
        field("pathJob").set(vm, path)
        state.value = state.value.copy(progress = TestProgress(1, 10, 1), pathfinding = GalsStep("test", NodeProfile()),
            gals = true, sensing = true, refining = true)
        vm.cancelTest()
        assertTrue(test.isCancelled)
        assertTrue(path.isCancelled)
        assertNull(state.value.progress)
        assertNull(state.value.pathfinding)
        assertFalse(state.value.gals || state.value.sensing || state.value.refining)
        vm.cancelTest()
        assertNull(state.value.progress)
    }

    @Test
    fun cancellationAfterFileRemovalStillClearsSavedSelectionAndBackups() {
        val node = LinkParser.parse("vless://test@delete.example.test:443?security=tls")!!
        App.repo.appendManual(listOf(node))
        App.prefs.selectNode(node)
        App.prefs.rememberForMode(NetMode.BLACK, node)
        App.prefs.backupNodes = BackupNodes(nodes = listOf(node))
        state.value = state.value.copy(nodes = listOf(node), selectedId = node.id, savedNode = node)
        val parent = vm.viewModelScope.coroutineContext[Job]!!
        Repository.afterRemoval = { vm.viewModelScope.cancel() }
        vm.deleteManualNode(node)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!parent.isCompleted && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue(parent.isCompleted)
        assertTrue(App.repo.loadManual().nodes.isEmpty())
        assertNull(App.prefs.lastNode)
        assertNull(App.prefs.selectedNodeId)
        assertTrue(App.prefs.modeNodes.isEmpty())
        assertTrue(App.prefs.backupNodes.nodes.isEmpty())
    }

    @Test
    fun safeModeHidesMixedJsonOnlyInCommunitySources() {
        vm.viewModelScope.cancel()
        val source = BuiltInCatalog.sources.first { it.mode.fits(NetMode.BLACK) }.id
        val input = """{"outbounds":[{"tag":"proxy","protocol":"trojan","settings":{"servers":[{"address":"node.example.test","port":443,"password":"test"}]},"streamSettings":{"security":"tls"}},{"tag":"proxy-plain","protocol":"vless","settings":{"vnext":[{"address":"plain.example.test","port":443,"users":[{"id":"test"}]}]}}],"routing":{"rules":[{"balancerTag":"pool"}],"balancers":[{"tag":"pool","selector":["proxy"]}]}}"""
        val publicNode = SubscriptionParser.parse(input, source).nodes.single()
        val manual = publicNode.copy(id = "manual-json", sourceId = ProxyNode.MANUAL_SOURCE)
        App.prefs.settings = App.prefs.settings.copy(communityEnabled = true, safeMode = true)
        App.prefs.enabledSources = setOf(source)
        @Suppress("UNCHECKED_CAST")
        val snapshots = field("snapshots").get(vm) as MutableStateFlow<Map<String, SourceSnapshot>>
        snapshots.value = listOf(publicNode, manual).associate { node ->
            node.sourceId to SourceSnapshot(node.sourceId, null, null, emptyList(), listOf(node), 0)
        }
        val rebuild = MainViewModel::class.java.getDeclaredMethod("rebuildLocked").apply { isAccessible = true }
        rebuild.invoke(vm)
        assertEquals(listOf(manual), state.value.nodes)
        assertEquals(1, state.value.hiddenInsecure)
        assertFalse(App.prefs.allowedForAuto(publicNode))
        assertTrue(App.prefs.allowedForAuto(manual))
        App.prefs.settings = App.prefs.settings.copy(safeMode = false)
        rebuild.invoke(vm)
        assertEquals(setOf(publicNode, manual), state.value.nodes.toSet())
        assertTrue(App.prefs.allowedForAuto(publicNode))
    }

    @Test
    fun deletionFromCancelledViewModelUpdatesEveryLiveViewModel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val node = LinkParser.parse("vless://test@delete.example.test:443?security=tls")!!
        App.repo.appendManual(listOf(node))
        App.prefs.selectNode(node)
        val written = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        Repository.afterRemoval = {
            written.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        state.value = state.value.copy(nodes = listOf(node), selectedId = node.id, savedNode = node)
        @Suppress("UNCHECKED_CAST")
        val snapshots = field("snapshots").get(vm) as MutableStateFlow<Map<String, SourceSnapshot>>
        snapshots.value = mapOf(ProxyNode.MANUAL_SOURCE to App.repo.loadManual())
        vm.deleteManualNode(node)
        assertTrue(written.await(5, TimeUnit.SECONDS))
        vm.viewModelScope.cancel()
        val replacements = List(2) { MainViewModel(app) }
        try {
            awaitCondition { replacements.all { it.state.value.nodes.size == 1 } }
            release.countDown()
            awaitCondition { replacements.all { it.state.value.nodes.isEmpty() && it.state.value.selectedId == null } }
            assertNull(App.prefs.lastNode)
        } finally {
            release.countDown()
            replacements.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun awaitCondition(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!predicate() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue(predicate())
    }

    private fun field(name: String) = MainViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Implements(value = SubscriptionRepository::class, isInAndroidSdk = false)
    class Repository {
        @RealObject lateinit var real: SubscriptionRepository

        @Implementation
        fun removeManual(nodeId: String) {
            Shadow.directlyOn<Any, SubscriptionRepository>(real, SubscriptionRepository::class.java, "removeManual", ClassParameter.from(String::class.java, nodeId))
            afterRemoval?.invoke()
        }

        companion object { var afterRemoval: (() -> Unit)? = null }
    }
}

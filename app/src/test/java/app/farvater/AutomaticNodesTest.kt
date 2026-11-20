package app.farvater

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.farvater.core.model.NetMode
import app.farvater.core.model.ProxyNode
import app.farvater.core.model.SourceMode
import app.farvater.core.parser.LinkParser
import app.farvater.data.GalsGroup
import app.farvater.data.GalsPolicy
import app.farvater.data.ServiceKind
import app.farvater.data.SourceConfig
import app.farvater.data.SourceSnapshot
import app.farvater.data.UserSource
import app.farvater.engine.AutomaticNodes
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AutomaticNodesTest {
    private val first = "user-first"
    private val second = "user-second"
    private val group = GalsGroup("group", "Group", allSources = false, sourceIds = setOf(second))

    @Before fun setup() {
        prepareRuntime(ApplicationProvider.getApplicationContext<Application>())
        App.prefs.userSources = listOf(
            UserSource(first, "https://first.example.test/sub", "First"),
            UserSource(second, "https://second.example.test/sub", "Second"),
        )
        App.prefs.settings = App.prefs.settings.copy(galsGroups = listOf(group), activeGalsGroupId = group.id)
    }

    @Test fun filteringBeforeDeduplicationPreservesTheAllowedSource() {
        val a = node(first)
        val b = node(second)
        assertEquals(a.id, b.id)
        install(a, b)
        assertEquals(listOf(b), AutomaticNodes.candidates(App.prefs, App.repo, NetMode.BLACK))
        assertNull(AutomaticNodes.resolve(a, App.prefs, App.repo, NetMode.BLACK))
        assertEquals(b, AutomaticNodes.resolve(b, App.prefs, App.repo, NetMode.BLACK))
    }

    @Test fun excludedNodeStaysInItsSourceAndCannotBePickedByEitherAutoPolicy() {
        val candidate = node(second)
        install(candidate)
        for (groupId in listOf(group.id, null)) {
            App.prefs.settings = App.prefs.settings.copy(activeGalsGroupId = groupId, excludedAutoNodeIds = setOf(candidate.id))
            assertTrue(App.prefs.eligibleSource(candidate))
            assertNull(AutomaticNodes.resolve(candidate, App.prefs, App.repo, NetMode.BLACK))
            assertTrue(AutomaticNodes.candidates(App.prefs, App.repo, NetMode.BLACK).isEmpty())
            App.prefs.selectNode(candidate)
            assertEquals(candidate, App.prefs.lastNode)
        }
    }

    @Test fun sameIdDoesNotAuthorizeAnOutdatedConfiguration() {
        val old = node(second)
        install(old.copy(params = old.params + ("type" to "ws")))
        assertNull(AutomaticNodes.resolve(old, App.prefs, App.repo, NetMode.BLACK))
    }

    @Test fun changedTargetInvalidatesAPreviousPolicyButRenamingDoesNot() {
        val candidate = node(second)
        install(candidate)
        val before = GalsPolicy.from(App.prefs.settings)
        App.prefs.settings = App.prefs.settings.copy(galsGroups = listOf(group.copy(name = "Renamed")))
        assertEquals(candidate, AutomaticNodes.resolve(candidate, App.prefs, App.repo, NetMode.BLACK, before))
        App.prefs.settings = App.prefs.settings.copy(galsGroups = listOf(group.copy(target = ServiceKind.YOUTUBE)))
        assertNull(AutomaticNodes.resolve(candidate, App.prefs, App.repo, NetMode.BLACK, before))
    }

    @Test fun sourceRemovalAndModeMismatchOverrideAnOldInventoryEntry() {
        val candidate = node(second)
        install(candidate)
        App.prefs.sourceConfigs = mapOf(second to SourceConfig(mode = SourceMode.WHITE))
        assertNull(AutomaticNodes.resolve(candidate, App.prefs, App.repo, NetMode.BLACK))
        assertEquals(candidate, AutomaticNodes.resolve(candidate, App.prefs, App.repo, NetMode.WHITE))
        App.prefs.userSources = emptyList()
        assertNull(AutomaticNodes.resolve(candidate, App.prefs, App.repo, NetMode.WHITE))
    }

    @Test fun missingInventoryNeverAuthorizesASavedNode() {
        assertNull(AutomaticNodes.resolve(node(second), App.prefs, App.repo, NetMode.BLACK))
    }

    private fun install(vararg nodes: ProxyNode) {
        App.repo.snapshots.value = nodes.groupBy { it.sourceId }.mapValues { (id, list) ->
            SourceSnapshot(id, null, null, emptyList(), list, 1)
        }
    }

    private fun node(source: String) = LinkParser.parse(
        "vless://00000000-0000-0000-0000-000000000001@node.example.test:443?security=tls", source)!!
}

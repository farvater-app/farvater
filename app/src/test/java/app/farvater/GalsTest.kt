package app.farvater

import app.farvater.core.model.NodeProfile
import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import app.farvater.core.xray.XrayConfigBuilder
import app.farvater.engine.DohProbe
import app.farvater.engine.Failover
import app.farvater.core.model.NetMode
import app.farvater.engine.GalsPlan
import app.farvater.engine.ModeStep
import app.farvater.engine.ModeSwitch
import app.farvater.engine.Pathfinder
import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalsTest {
    private fun node(id: String, address: String = "$id.example.net", type: String = "tcp", security: String = "reality") = ProxyNode(
        id = id,
        protocol = Protocol.VLESS,
        name = id,
        address = address,
        port = 443,
        secret = "00000000-0000-0000-0000-000000000000",
        params = mapOf("type" to type, "security" to security, "sni" to "example.com", "pbk" to "key", "sid" to "ab"),
        raw = "vless://$id",
    )

    private fun alive(id: String, delay: Long, at: Long = 1_000, method: TestMethod = TestMethod.REAL) = id to TestResult(id, delay, method, at)

    @Test
    fun dohQueryIsDnsWireFormat() {
        val wire = DohProbe.wire("telegram.org")
        assertArrayEquals(byteArrayOf(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0), wire.copyOfRange(0, 12))
        assertEquals(8, wire[12].toInt())
        assertEquals("telegram", String(wire, 13, 8))
        assertEquals(3, wire[21].toInt())
        assertEquals("org", String(wire, 22, 3))
        assertArrayEquals(byteArrayOf(0, 0, 1, 0, 1), wire.copyOfRange(25, 30))
        assertFalse(DohProbe.query.contains('='))
        assertFalse(DohProbe.query.contains('+'))
        assertFalse(DohProbe.query.contains('/'))
    }

    @Test
    fun dohAnswerNeedsReplyWithoutErrorAndRecords() {
        val question = DohProbe.wire("telegram.org")
        val headerAndQuestion = question.copyOf().apply {
            this[2] = 0x81.toByte()
            this[3] = 0x80.toByte()
            this[7] = 1
        }
        val record = byteArrayOf(0xc0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 192.toByte(), 0, 2, 1)
        val reply = headerAndQuestion + record
        assertTrue(DohProbe.answered(reply))
        assertFalse(DohProbe.answered(reply, "example.org"))
        assertFalse(DohProbe.answered(headerAndQuestion))
        assertFalse(DohProbe.answered(reply.copyOf(reply.size - 1)))
        assertFalse(DohProbe.answered(reply.copyOf().apply { this[3] = 0x83.toByte() }))
        assertFalse(DohProbe.answered(reply.copyOf().apply { this[2] = 0x83.toByte() }))
        assertFalse(DohProbe.answered(reply.copyOf().apply { this[7] = 0 }))
        assertFalse(DohProbe.answered(question))
        val alias = byteArrayOf(3, 119, 119, 119, 0xc0.toByte(), 12)
        val cname = byteArrayOf(0xc0.toByte(), 12, 0, 5, 0, 1, 0, 0, 0, 60, 0, 6) + alias
        val aliasAddress = alias + record.copyOfRange(2, record.size)
        assertTrue(DohProbe.answered(headerAndQuestion.copyOf().apply { this[7] = 2 } + cname + aliasAddress))
        assertFalse(DohProbe.answered(headerAndQuestion + aliasAddress))
    }

    @Test
    fun dnsOrderPutsWorkingServerFirst() {
        assertEquals(listOf("google", "cloudflare", "quad9"), XrayConfigBuilder.dnsOrder(null))
        assertEquals(listOf("quad9", "google", "cloudflare"), XrayConfigBuilder.dnsOrder("quad9"))
        assertEquals(listOf("google", "cloudflare", "quad9"), XrayConfigBuilder.dnsOrder("unknown"))
    }

    @Test
    fun configAsksWorkingDohFirstOneAtATime() {
        val cfg = XrayConfigBuilder.build(node("a"), directRuServices = false, profile = NodeProfile(dns = "cloudflare"))
        val cloudflare = cfg.indexOf("https://1.1.1.1/dns-query")
        val google = cfg.indexOf("https://8.8.8.8/dns-query")
        val quad9 = cfg.indexOf("https://9.9.9.9/dns-query")
        assertTrue(cloudflare in 0 until google && google < quad9)
        assertFalse(cfg.contains("enableParallelQuery"))
        assertTrue(cfg.contains("\"timeoutMs\":2500"))
        assertTrue(cfg.contains("\"serveStale\":true"))
        val plain = XrayConfigBuilder.build(node("a"), directRuServices = false, encryptedDns = false)
        assertFalse(plain.contains("dns-query"))
        assertTrue(plain.contains("\"address\":\"9.9.9.9\""))
    }

    @Test
    fun savedDnsIsNotAnExtraProfileVariant() {
        val variants = Pathfinder.variants(node("a"), NodeProfile(fingerprint = "firefox", dns = "quad9"), ordinaryBlocking = false)
        assertTrue(variants.all { it.dns == null })
        assertEquals(NodeProfile(fingerprint = "firefox"), variants.first())
        assertEquals(variants.size, variants.distinct().size)
    }

    @Test
    fun galsSwitchesOnlyToClearlyFasterNode() {
        assertEquals("b", GalsPlan.fastest(mapOf("a" to 400_000L, "b" to 900_000L, "c" to 300_000L)))
        assertNull(GalsPlan.fastest(emptyMap()))
        assertTrue(GalsPlan.worthSwitching(currentSpeed = 400_000, bestSpeed = 900_000))
        assertFalse(GalsPlan.worthSwitching(currentSpeed = 400_000, bestSpeed = 550_000))
        assertFalse(GalsPlan.worthSwitching(currentSpeed = 400_000, bestSpeed = 400_000))
    }

    @Test
    fun scoutPrefersFreshFastRealResults() {
        val nodes = listOf(node("old"), node("tcp"), node("slow"), node("fast"), node("dead"), node("tried"))
        val results = mapOf(
            alive("old", 10, at = 1),
            alive("tcp", 20, method = TestMethod.TCP),
            alive("slow", 400),
            alive("fast", 120),
            alive("dead", 30),
            alive("tried", 15),
        )
        val next = GalsPlan.next(nodes, results, dead = setOf("dead"), tried = setOf("tried"), failedKinds = emptySet(), failedHosts = emptySet(), since = 100)
        assertEquals("fast", next?.id)
        assertNull(GalsPlan.next(nodes, emptyMap(), emptySet(), emptySet(), emptySet(), emptySet(), since = 0))
    }

    @Test
    fun scoutMovesAwayFromBlockedKindAndHost() {
        val reality = node("reality", type = "tcp")
        val sameHost = node("same", address = "blocked.example.net", type = "xhttp")
        val xhttp = node("xhttp", type = "xhttp")
        val results = mapOf(alive("reality", 50), alive("same", 60), alive("xhttp", 200))
        val next = GalsPlan.next(
            listOf(reality, sameHost, xhttp), results,
            dead = emptySet(), tried = emptySet(),
            failedKinds = setOf(GalsPlan.kind(node("x", type = "tcp"))),
            failedHosts = setOf("blocked.example.net"),
            since = 0,
        )
        assertEquals("xhttp", next?.id)
    }

    @Test
    fun fallbackTakesNodeWithDataAndNeverAFrozenOne() {
        val nodes = listOf(node("frozen"), node("noTelegram"), node("untried"))
        val results = mapOf(alive("frozen", 20), alive("noTelegram", 300), alive("untried", 90))
        val withData = GalsPlan.fallback(nodes, results, dead = emptySet(), tried = setOf("frozen", "noTelegram"), partial = mapOf("noTelegram" to 500_000L))
        assertEquals("noTelegram", withData?.id)
        val untried = GalsPlan.fallback(nodes, results, dead = emptySet(), tried = setOf("frozen", "noTelegram"), partial = emptyMap())
        assertEquals("untried", untried?.id)
        assertNull(GalsPlan.fallback(nodes, results, dead = setOf("untried"), tried = setOf("frozen", "noTelegram"), partial = emptyMap()))
        assertEquals("untried", GalsPlan.fallback(nodes, results, dead = emptySet(), tried = setOf("frozen", "noTelegram"), partial = mapOf("gone" to 1L))?.id)
    }

    @Test
    fun fragmentIsTriedEarlyUnderOrdinaryBlocking() {
        val tls = node("tls", type = "ws", security = "tls")
        val variants = Pathfinder.variants(tls, null, ordinaryBlocking = true)
        assertEquals(NodeProfile(fragment = true), variants[1])
        assertFalse(Pathfinder.variants(tls, null, ordinaryBlocking = false).any { it.fragment })
    }

    @Test
    fun onlyTheMainProxyCarriesUdp() {
        val main = XrayConfigBuilder.build(node("a"), directRuServices = false)
        val probe = XrayConfigBuilder.build(node("a"), directRuServices = false, socksPort = XrayConfigBuilder.SOCKS_PORT + 1)
        assertTrue(main.contains("\"udp\":true"))
        assertTrue(probe.contains("\"udp\":false"))
    }

    @Test
    fun modeSwitchWaitsRetriesAndSwitches() {
        val now = 10_000_000L
        assertEquals(ModeStep.Wait(ModeSwitch.RETRY_MS), ModeSwitch.step(null, "w", "k", now, 0))
        assertEquals(ModeStep.Done, ModeSwitch.step(NetMode.WHITE, null, "k", now, 0))
        assertEquals(ModeStep.Done, ModeSwitch.step(NetMode.WHITE, "k", "k", now, 0))
        assertEquals(ModeStep.Wait(ModeSwitch.GAP_MS - 1_000), ModeSwitch.step(NetMode.WHITE, "w", "k", now, now - 1_000))
        assertEquals(ModeStep.Switch, ModeSwitch.step(NetMode.WHITE, "w", "k", now, now - ModeSwitch.GAP_MS))
    }

    @Test
    fun stalledNodeRechecksWithGrowingPauses() {
        assertFalse(ModeSwitch.recheckAfter(2, 3))
        assertTrue(ModeSwitch.recheckAfter(3, 3))
        assertFalse(ModeSwitch.recheckAfter(4, 3))
        assertTrue(ModeSwitch.recheckAfter(6, 3))
        assertFalse(ModeSwitch.recheckAfter(9, 3))
        assertTrue(ModeSwitch.recheckAfter(12, 3))
        assertTrue(ModeSwitch.recheckAfter(24, 3))
    }

    @Test
    fun failoverPrefersSameSubscriptionThenFastest() {
        val a = node("a").copy(sourceId = "paid")
        val b = node("b").copy(sourceId = "public")
        val c = node("c").copy(sourceId = "paid")
        val d = node("d").copy(sourceId = "paid")
        val results = mapOf(alive("a", 50), alive("b", 20), alive("c", 90), alive("d", 10, method = TestMethod.TCP))
        val backups = Failover.pick(listOf(a, b, c, d), results, dead = setOf("c"))
        assertEquals(listOf("b", "a", "d"), backups.map { it.id })
        assertEquals(listOf("d", "b"), Failover.order(backups, current = a).map { it.id })
    }

    @Test
    fun probesComeFasterAfterAFailure() {
        val early = setOf(5, 15, 30)
        assertTrue(Failover.probeDue(tick = 60, lastProbe = 30, failures = 0, every = 60, early = early, stall = 3))
        assertFalse(Failover.probeDue(tick = 70, lastProbe = 60, failures = 0, every = 60, early = early, stall = 3))
        assertTrue(Failover.probeDue(tick = 70, lastProbe = 60, failures = 1, every = 60, early = early, stall = 3))
        assertFalse(Failover.probeDue(tick = 65, lastProbe = 60, failures = 1, every = 60, early = early, stall = 3))
        assertFalse(Failover.probeDue(tick = 70, lastProbe = 60, failures = 3, every = 60, early = early, stall = 3))
    }

    @Test
    fun failoverMixesInOtherSourcesAndSkipsTriedOnes() {
        val current = node("cur").copy(sourceId = "paid")
        val paid = (1..6).map { node("p$it").copy(sourceId = "paid") }
        val public = listOf(node("c1").copy(sourceId = "catalog"), node("c2").copy(sourceId = "catalog"))
        val order = Failover.order(paid + public, current)
        assertEquals(listOf("p1", "p2", "c1", "c2", "p3"), order.take(Failover.TRIES).map { it.id })
        val next = Failover.order(paid + public, current, skip = setOf("p1", "p2", "c1"))
        assertEquals(listOf("p3", "p4", "c2"), next.take(3).map { it.id })
    }
}

package app.farvater

import app.farvater.core.parser.SubscriptionLimitException
import app.farvater.core.parser.SubscriptionParser
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SubscriptionBudgetTest {
    @Test
    fun rawAndBase64SubscriptionsRejectOneNodeBeyondTheLimit() {
        val accepted = (0 until SubscriptionParser.MAX_NODES).joinToString("\n", transform = ::link)
        assertEquals(SubscriptionParser.MAX_NODES, SubscriptionParser.parse(accepted, "test").nodes.size)
        val acceptedBase64 = Base64.getEncoder().encodeToString(accepted.toByteArray())
        assertEquals(SubscriptionParser.MAX_NODES, SubscriptionParser.parse(acceptedBase64, "test").nodes.size)

        val oversized = "$accepted\n${link(SubscriptionParser.MAX_NODES)}"
        assertThrows(SubscriptionLimitException::class.java) { SubscriptionParser.parse(oversized, "test") }
        val oversizedBase64 = Base64.getEncoder().encodeToString(oversized.toByteArray())
        assertThrows(SubscriptionLimitException::class.java) { SubscriptionParser.parse(oversizedBase64, "test") }
    }

    @Test
    fun jsonArraysAndJsonLinesShareTheNodeLimit() {
        val configs = (0 until SubscriptionParser.MAX_NODES).map(::config)
        val acceptedArray = configs.joinToString(prefix = "[", postfix = "]")
        assertEquals(SubscriptionParser.MAX_NODES, SubscriptionParser.parse(acceptedArray, "test").nodes.size)
        assertThrows(SubscriptionLimitException::class.java) {
            SubscriptionParser.parse(configs.plus(config(SubscriptionParser.MAX_NODES)).joinToString(prefix = "[", postfix = "]"), "test")
        }

        val acceptedLines = configs.joinToString("\n")
        assertEquals(SubscriptionParser.MAX_NODES, SubscriptionParser.parse(acceptedLines, "test").nodes.size)
        assertThrows(SubscriptionLimitException::class.java) {
            SubscriptionParser.parse("$acceptedLines\n${config(SubscriptionParser.MAX_NODES)}", "test")
        }
    }

    @Test
    fun entryLengthAndQueryCountAreBoundedBeforeParsing() {
        val acceptedParams = listOf("security=tls") +
            List(SubscriptionParser.MAX_QUERY_PARAMETERS - 1) { "p$it=value" }
        assertEquals(1, SubscriptionParser.parse("vless://id@node.example.test:443?${acceptedParams.joinToString("&")}", "test").nodes.size)

        val extraParam = acceptedParams + "overflow=value"
        assertThrows(SubscriptionLimitException::class.java) {
            SubscriptionParser.parse("vless://id@node.example.test:443?${extraParam.joinToString("&")}", "test")
        }
        assertThrows(SubscriptionLimitException::class.java) {
            SubscriptionParser.parse("vless://id@node.example.test:443#" + "x".repeat(SubscriptionParser.MAX_LINK_CHARS), "test")
        }
        val tooManyEntries = List(SubscriptionParser.MAX_SCANNED_ENTRIES + 1) { "unsupported://$it" }.joinToString("\n")
        assertThrows(SubscriptionLimitException::class.java) { SubscriptionParser.parse(tooManyEntries, "test") }
    }

    private fun link(index: Int) =
        "vless://id-$index@node-$index.example.test:443?security=tls#node-$index"

    private fun config(index: Int) = """{"remarks":"node-$index","outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"node-$index.example.test","port":443,"users":[{"id":"id-$index"}]}]},"streamSettings":{"security":"tls"}}]}"""
}

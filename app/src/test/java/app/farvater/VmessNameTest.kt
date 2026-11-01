package app.farvater

import app.farvater.core.parser.LinkParser
import app.farvater.core.parser.SubscriptionParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder
import java.util.Base64

class VmessNameTest {
    private fun payload(name: String = "", pretty: Boolean = false): String {
        val obj = JsonObject(mapOf("add" to JsonPrimitive("vmess.example.test"), "port" to JsonPrimitive(443),
            "id" to JsonPrimitive("00000000-0000-0000-0000-000000000001"), "ps" to JsonPrimitive(name),
            "net" to JsonPrimitive("tcp"), "tls" to JsonPrimitive("tls")))
        return Json { prettyPrint = pretty }.encodeToString(JsonObject.serializer(), obj)
    }

    private fun link(name: String = "", pretty: Boolean = false) =
        "vmess://" + Base64.getEncoder().encodeToString(payload(name, pretty).toByteArray())

    @Test fun jsonNamePreservesUtf8EmojiAndLiteralPercent() {
        val name = "Москва 東京 تهران 🎄 + 100%20"
        assertEquals(name, LinkParser.parse(link(name))!!.name)
    }

    @Test fun emptyNameFallsBackToAddress() {
        assertEquals("vmess.example.test:443", LinkParser.parse(link("  "))!!.name)
    }

    @Test fun fragmentSuppliesMissingNameAndKeepsLiteralPlus() {
        assertEquals("Фрагмент + 🌲", LinkParser.parse(link() + "#" + URLEncoder.encode("Фрагмент + 🌲", "UTF-8").replace("+", "%20"))!!.name)
        assertEquals("A+B", LinkParser.parse(link() + "#A+B")!!.name)
        assertEquals("JSON", LinkParser.parse(link("JSON") + "#Fragment")!!.name)
    }

    @Test fun whitespaceInsideDecodedJsonDoesNotLoseName() {
        assertEquals("Indented", SubscriptionParser.parse(link("Indented", pretty = true), "test").nodes.single().name)
    }

    @Test fun unpaddedAndUrlSafeBase64KeepName() {
        val name = "東京 🚀🦊"
        val bytes = payload(name).toByteArray()
        for (encoder in listOf(Base64.getEncoder().withoutPadding(), Base64.getUrlEncoder().withoutPadding())) {
            assertEquals(name, LinkParser.parse("vmess://" + encoder.encodeToString(bytes))!!.name)
        }
    }

    @Test fun remarksQueryIsNotPartOfBase64Json() {
        val base = link()
        assertEquals("Query + name", LinkParser.parse("$base?remarks=Query%20%2B%20name")!!.name)
        assertEquals("Fragment", LinkParser.parse("$base?remarks=Query#Fragment")!!.name)
        assertEquals("JSON", LinkParser.parse(link("JSON") + "?remarks=Query")!!.name)
        assertEquals(LinkParser.parse(base)!!.id, LinkParser.parse("$base?remarks=Another#Name")!!.id)
    }

    @Test fun shadowrocketAuthorityKeepsNameAndTransport() {
        val encoded = Base64.getEncoder().encodeToString("auto:00000000-0000-0000-0000-000000000001@vmess.example.test:443".toByteArray())
        val node = LinkParser.parse("vmess://$encoded?remarks=Shadow%20Rocket&obfs=websocket&obfsParam=cdn.example.test&path=%2Fws&tls=1&peer=tls.example.test")!!
        assertEquals("Shadow Rocket", node.name)
        assertEquals("ws", node.transport)
        assertEquals("tls", node.security)
        assertEquals("cdn.example.test", node.params["host"])
        assertEquals("tls.example.test", node.params["sni"])
        assertEquals("/ws", node.params["path"])
        assertEquals("auto", node.params["scy"])
    }

    @Test fun malformedQueryAndPayloadDoNotThrowOrDropNextNode() {
        val subscription = "vmess://invalid?remarks=bad\n" + link("Valid") + "?remarks=%zz"
        assertEquals("Valid", SubscriptionParser.parse(subscription, "test").nodes.single().name)
    }
}

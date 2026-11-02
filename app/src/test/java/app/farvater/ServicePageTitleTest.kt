package app.farvater

import app.farvater.engine.ServicePageTitle
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException

class ServicePageTitleTest {
    @Test
    fun titleMustHaveAnExactTagName() {
        for (html in listOf(
            "<titlex>Google Gemini</title>",
            "<title-other>Google Gemini</title>",
            "<title=other>Google Gemini</title>",
            "<title>Google Gemini</titlex>",
        )) assertEquals(html, "", read(html))
        assertEquals("Google Gemini", read("<TiTlE data-title='ignored'>Google Gemini</TiTlE >"))
    }

    @Test
    fun scriptStyleAndOtherRawTextCannotSupplyThePageTitle() {
        for (tag in listOf("script", "style", "textarea", "xmp", "iframe", "noembed", "noframes", "noscript")) {
            val hidden = "<$tag data-value='>'><title>Google Gemini</title></$tag>"
            assertEquals(tag, "", read(hidden))
            assertEquals(tag, "Access denied", read("$hidden<title>Access denied</title>"))
        }
    }

    @Test
    fun commentsTemplatesAndAttributeValuesCannotSupplyThePageTitle() {
        for (hidden in listOf(
            "<!-- <title>Google Gemini</title> -->",
            "<!-- <title>Google Gemini</title> --!>",
            "<template><title>Google Gemini</title></template>",
            "<template><template><title>Google Gemini</title></template><title>Gemini</title></template>",
            "<template><script>const text = '</template><title>Google Gemini</title>';</script></template>",
            "<meta content='<title>Google Gemini</title>'>",
            "<meta content=\"<title>Google Gemini</title>\">",
        )) {
            assertEquals(hidden, "", read(hidden))
            assertEquals(hidden, "Access denied", read("$hidden<title>Access denied</title>"))
        }
    }

    @Test
    fun rawTextOnlyEndsAtAnExactClosingTag() {
        assertEquals("Access denied", read("<ScRiPt> </scriptx><title>Google Gemini</title> </sCrIpT ><title>Access denied</title>"))
        assertEquals("", read("<script> <title>Google Gemini</title>"))
        assertEquals("", read("<!-- <title>Google Gemini</title>"))
        assertEquals("", read("<template><title>Google Gemini</title>"))
        assertEquals("Access denied", read("<script>value < other </script><title>Access denied</title>"))
    }

    @Test
    fun malformedTitlesStillAllowALaterValidTitle() {
        for (prefix in listOf("<title></title>", "<title>" + "x".repeat(301) + "</title>",
            "<title>unfinished", "<title".repeat(100_000))) {
            assertEquals("Google Gemini", read("$prefix<title>Google Gemini</title>"))
        }
        assertEquals("x".repeat(300), read("<title>" + "x".repeat(300) + "</title>"))
    }

    @Test
    fun utf8AndEveryTokenCanCrossReadBoundaries() {
        val html = "<!-- <title>False</title> --><template><script>'</template>'</script></template>" +
            "<script data-value='>'><title>False</title></script>" +
            "<title data-value='>'>\u200eGoogle Gemini\u200f</title>"
        for (padding in 8170..8200) assertEquals("padding=$padding", "\u200eGoogle Gemini\u200f", read(" ".repeat(padding) + html))
        val source = object : ForwardingSource(Buffer().writeUtf8(html)) {
            override fun read(sink: Buffer, byteCount: Long): Long = super.read(sink, minOf(1L, byteCount))
        }.buffer()
        assertEquals("\u200eGoogle Gemini\u200f", ServicePageTitle.read(source))
    }

    @Test
    fun titleSearchIsBoundedToOneMebibyte() {
        val limit = 1024 * 1024L
        var consumed = 0L
        val source = object : ForwardingSource(Buffer().writeUtf8(" ".repeat(limit.toInt()) + "<title>Google Gemini</title>")) {
            override fun read(sink: Buffer, byteCount: Long): Long = super.read(sink, byteCount).also { if (it > 0) consumed += it }
        }.buffer()
        assertEquals("", ServicePageTitle.read(source))
        assertEquals(limit, consumed)
        assertEquals("Google Gemini", read(" ".repeat(780_000) + "<title>Google Gemini</title>"))
    }

    @Test
    fun completeTitleDoesNotWaitForThePageTail() {
        val html = "<script>const fake = '<title>False</title>';</script><title>Google Gemini</title>"
        var consumed = 0L
        val source = object : ForwardingSource(Buffer().writeUtf8(html + " ".repeat(200_000))) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (consumed >= html.length) throw SocketTimeoutException("Slow page tail")
                return super.read(sink, minOf(byteCount, html.length - consumed)).also { if (it > 0) consumed += it }
            }
        }.buffer()
        assertEquals("Google Gemini", ServicePageTitle.read(source))
        assertEquals(html.length.toLong(), consumed)
    }

    private fun read(html: String): String = ServicePageTitle.read(Buffer().writeUtf8(html))
}

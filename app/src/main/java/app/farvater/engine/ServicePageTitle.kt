package app.farvater.engine

import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.InputStreamReader

internal object ServicePageTitle {
    fun read(source: BufferedSource): String {
        val bounded = object : ForwardingSource(source) {
            var remaining = 1024 * 1024L
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (remaining == 0L) return -1L
                return super.read(sink, minOf(byteCount, remaining)).also { if (it > 0) remaining -= it }
            }
        }
        val parser = Parser()
        val chars = CharArray(8192)
        InputStreamReader(bounded.buffer().inputStream(), Charsets.UTF_8).use { reader ->
            while (true) {
                val count = reader.read(chars)
                if (count < 0) break
                for (index in 0 until count) parser.accept(chars[index])?.let { return it }
            }
        }
        return ""
    }

    private enum class State { TEXT, OPEN, NAME, ATTRIBUTES, DECLARATION, COMMENT, BOGUS }

    private class Parser {
        private var state = State.TEXT
        private val name = StringBuilder(32)
        private val heading = StringBuilder(300)
        private var longName = false
        private var closing = false
        private var quote: Char? = null
        private var capturing = false
        private var templates = 0
        private var declaration = 0
        private var commentDashes = 0
        private var commentBang = false
        private var rawClosing = ""
        private var rawMatch = 0

        fun accept(char: Char): String? {
            when (state) {
                State.TEXT -> if (rawClosing.isNotEmpty()) {
                    raw(char)
                } else if (char == '<') {
                    begin()
                } else if (capturing) {
                    if (heading.length < 300) heading.append(char) else discardHeading()
                }
                State.OPEN -> when {
                    char == '<' -> begin()
                    char == '/' -> {
                        closing = true
                        state = State.NAME
                    }
                    char == '!' -> {
                        discardHeading()
                        declaration = 0
                        state = State.DECLARATION
                    }
                    char == '?' -> {
                        discardHeading()
                        state = State.BOGUS
                    }
                    char.lowercaseChar() in 'a'..'z' -> {
                        name.append(char.lowercaseChar())
                        state = State.NAME
                    }
                    else -> {
                        discardHeading()
                        state = State.TEXT
                    }
                }
                State.NAME -> when {
                    char == '>' -> return finish()
                    char == '<' -> begin()
                    char.isHtmlSpace() || char == '/' -> state = State.ATTRIBUTES
                    else -> if (name.length < 32) name.append(char.lowercaseChar()) else longName = true
                }
                State.ATTRIBUTES -> when {
                    quote != null -> if (char == quote) quote = null
                    char == '\'' || char == '"' -> quote = char
                    char == '>' -> return finish()
                    char == '<' -> if (rawClosing.isEmpty()) begin() else {
                        state = State.TEXT
                        raw(char)
                    }
                }
                State.DECLARATION -> if (char == '-') {
                    declaration++
                    if (declaration == 2) {
                        commentDashes = 0
                        commentBang = false
                        state = State.COMMENT
                    }
                } else {
                    quote = char.takeIf { it == '\'' || it == '"' }
                    state = if (char == '>') State.TEXT else State.BOGUS
                }
                State.COMMENT -> when {
                    char == '>' && (commentDashes >= 2 || commentBang) -> state = State.TEXT
                    char == '-' -> {
                        commentDashes = (commentDashes + 1).coerceAtMost(2)
                        commentBang = false
                    }
                    char == '!' && commentDashes >= 2 -> {
                        commentBang = true
                        commentDashes = 0
                    }
                    else -> {
                        commentDashes = 0
                        commentBang = false
                    }
                }
                State.BOGUS -> when {
                    quote != null -> if (char == quote) quote = null
                    char == '\'' || char == '"' -> quote = char
                    char == '>' -> state = State.TEXT
                }
            }
            return null
        }

        private fun raw(char: Char) {
            if (rawMatch == rawClosing.length) {
                if (char == '>' || char.isHtmlSpace() || char == '/') {
                    name.setLength(0)
                    name.append(rawClosing.substring(2))
                    closing = true
                    longName = false
                    quote = null
                    rawMatch = 0
                    if (char == '>') finish() else state = State.ATTRIBUTES
                    return
                }
                rawMatch = 0
            }
            rawMatch = if (char.lowercaseChar() == rawClosing[rawMatch]) rawMatch + 1 else if (char == '<') 1 else 0
        }

        private fun begin() {
            name.setLength(0)
            longName = false
            closing = false
            quote = null
            state = State.OPEN
        }

        private fun finish(): String? {
            state = State.TEXT
            val tag = if (longName) "" else name.toString()
            if (rawClosing.isNotEmpty()) {
                if (closing && rawClosing == "</$tag") rawClosing = ""
                return null
            }
            if (tag == "title" && templates == 0) {
                if (closing) {
                    if (capturing && heading.isNotEmpty()) return heading.toString()
                    discardHeading()
                } else {
                    heading.setLength(0)
                    capturing = true
                }
            } else {
                discardHeading()
                if (tag == "template") {
                    if (closing) templates = (templates - 1).coerceAtLeast(0) else templates++
                } else if (!closing && tag in rawTags) {
                    rawClosing = "</$tag"
                    rawMatch = 0
                }
            }
            return null
        }

        private fun discardHeading() {
            capturing = false
            heading.setLength(0)
        }
    }

    private fun Char.isHtmlSpace(): Boolean = this == ' ' || this == '\t' || this == '\r' || this == '\n' || this == '\u000c'

    private val rawTags = setOf("script", "style", "textarea", "xmp", "iframe", "noembed", "noframes", "noscript")
}

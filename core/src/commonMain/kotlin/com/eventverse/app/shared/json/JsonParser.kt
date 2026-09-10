package com.eventverse.app.shared.json

/**
 * Thrown when input is not well-formed JSON. Carries the offset so malformed
 * payloads coming from the database or an HTTP client are diagnosable.
 */
class JsonParseException(message: String, val offset: Int) :
    IllegalArgumentException("$message (at offset $offset)")

/**
 * Recursive-descent JSON parser with no external dependencies, usable from every
 * Kotlin Multiplatform target.
 *
 * Handles arbitrary whitespace, nesting, all RFC 8259 string escapes (including
 * `\uXXXX` and surrogate pairs) and any key ordering — which regex-based extraction
 * could not.
 */
object JsonParser {

    /** Parses any JSON value. Throws [JsonParseException] on malformed input. */
    fun parse(text: String): JsonValue {
        val state = Cursor(text)
        state.skipWhitespace()
        val value = state.readValue()
        state.skipWhitespace()
        if (!state.isAtEnd) state.fail("Unexpected trailing content")
        return value
    }

    /** Parses and requires a JSON object at the root. */
    fun parseObject(text: String): JsonValue.Obj =
        parse(text) as? JsonValue.Obj ?: throw JsonParseException("Expected a JSON object at root", 0)

    /** Parses and requires a JSON array at the root. */
    fun parseArray(text: String): List<JsonValue> =
        (parse(text) as? JsonValue.Arr)?.items
            ?: throw JsonParseException("Expected a JSON array at root", 0)

    /** Lenient variant returning null instead of throwing — for tolerant read paths. */
    fun parseObjectOrNull(text: String?): JsonValue.Obj? {
        if (text.isNullOrBlank()) return null
        return runCatching { parseObject(text) }.getOrNull()
    }

    private class Cursor(private val text: String) {
        var index: Int = 0

        val isAtEnd: Boolean get() = index >= text.length

        fun fail(message: String): Nothing = throw JsonParseException(message, index)

        fun skipWhitespace() {
            while (index < text.length) {
                when (text[index]) {
                    ' ', '\t', '\n', '\r' -> index++
                    else -> return
                }
            }
        }

        fun readValue(): JsonValue {
            if (isAtEnd) fail("Unexpected end of input")
            return when (val char = text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonValue.Str(readString())
                't' -> readLiteral("true").let { JsonValue.Bool(true) }
                'f' -> readLiteral("false").let { JsonValue.Bool(false) }
                'n' -> readLiteral("null").let { JsonValue.Null }
                else -> if (char == '-' || char in '0'..'9') readNumber() else fail("Unexpected character '$char'")
            }
        }

        private fun readLiteral(literal: String) {
            if (!text.startsWith(literal, index)) fail("Expected literal '$literal'")
            index += literal.length
        }

        private fun readObject(): JsonValue.Obj {
            expect('{')
            val entries = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (peek() == '}') {
                index++
                return JsonValue.Obj(entries)
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail("Expected a quoted object key")
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                entries[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return JsonValue.Obj(entries)
                    }
                    else -> fail("Expected ',' or '}' in object")
                }
            }
        }

        private fun readArray(): JsonValue.Arr {
            expect('[')
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (peek() == ']') {
                index++
                return JsonValue.Arr(items)
            }
            while (true) {
                skipWhitespace()
                items.add(readValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return JsonValue.Arr(items)
                    }
                    else -> fail("Expected ',' or ']' in array")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (isAtEnd) fail("Unterminated string")
                when (val char = text[index]) {
                    '"' -> {
                        index++
                        return sb.toString()
                    }
                    '\\' -> {
                        index++
                        if (isAtEnd) fail("Unterminated escape sequence")
                        when (val escaped = text[index]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append(8.toChar())
                            'f' -> sb.append(12.toChar())
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (index + 4 >= text.length) fail("Truncated unicode escape")
                                val hex = text.substring(index + 1, index + 5)
                                val code = hex.toIntOrNull(16) ?: fail("Invalid unicode escape '\\u$hex'")
                                sb.append(code.toChar())
                                index += 4
                            }
                            else -> fail("Unsupported escape '\\$escaped'")
                        }
                        index++
                    }
                    else -> {
                        sb.append(char)
                        index++
                    }
                }
            }
        }

        private fun readNumber(): JsonValue.Num {
            val start = index
            if (peek() == '-') index++
            while (!isAtEnd && text[index] in '0'..'9') index++
            if (!isAtEnd && text[index] == '.') {
                index++
                while (!isAtEnd && text[index] in '0'..'9') index++
            }
            if (!isAtEnd && (text[index] == 'e' || text[index] == 'E')) {
                index++
                if (!isAtEnd && (text[index] == '+' || text[index] == '-')) index++
                while (!isAtEnd && text[index] in '0'..'9') index++
            }
            val raw = text.substring(start, index)
            if (raw.isEmpty() || raw == "-") fail("Malformed number")
            return JsonValue.Num(raw)
        }

        private fun peek(): Char? = if (isAtEnd) null else text[index]

        private fun expect(char: Char) {
            if (peek() != char) fail("Expected '$char'")
            index++
        }
    }
}

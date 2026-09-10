package com.eventverse.app.shared.json

/**
 * Compact JSON serialiser for [JsonValue].
 */
object JsonWriter {

    /** Control characters expressed by code point so the source stays plain ASCII. */
    private val BACKSPACE = 8.toChar()
    private val FORM_FEED = 12.toChar()
    private val FIRST_PRINTABLE = 32.toChar()

    fun write(value: JsonValue): String = StringBuilder().apply { appendValue(this, value) }.toString()

    private fun appendValue(sb: StringBuilder, value: JsonValue) {
        when (value) {
            is JsonValue.Null -> sb.append("null")
            is JsonValue.Bool -> sb.append(if (value.value) "true" else "false")
            is JsonValue.Num -> sb.append(value.raw)
            is JsonValue.Str -> appendQuoted(sb, value.value)
            is JsonValue.Arr -> {
                sb.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) sb.append(',')
                    appendValue(sb, item)
                }
                sb.append(']')
            }
            is JsonValue.Obj -> {
                sb.append('{')
                var first = true
                value.entries.forEach { (key, entry) ->
                    if (!first) sb.append(',')
                    first = false
                    appendQuoted(sb, key)
                    sb.append(':')
                    appendValue(sb, entry)
                }
                sb.append('}')
            }
        }
    }

    /** Escapes a string and wraps it in quotes, per RFC 8259. */
    fun appendQuoted(sb: StringBuilder, raw: String) {
        sb.append('"')
        for (char in raw) {
            when {
                char == '"' -> sb.append("\\\"")
                char == '\\' -> sb.append("\\\\")
                char == '\n' -> sb.append("\\n")
                char == '\r' -> sb.append("\\r")
                char == '\t' -> sb.append("\\t")
                char == BACKSPACE -> sb.append("\\b")
                char == FORM_FEED -> sb.append("\\f")
                char < FIRST_PRINTABLE -> sb.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                else -> sb.append(char)
            }
        }
        sb.append('"')
    }

    /** Escapes a string without surrounding quotes. */
    fun escape(raw: String): String {
        val sb = StringBuilder()
        appendQuoted(sb, raw)
        return sb.substring(1, sb.length - 1)
    }
}

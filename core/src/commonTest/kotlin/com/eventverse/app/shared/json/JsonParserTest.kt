package com.eventverse.app.shared.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonParserTest {

    @Test
    fun parseObject_withNestingAndWhitespace_shouldReadByKey() {
        val json = """
            {
              "name" : "Gudang Kain" ,
              "active": true,
              "count": 12,
              "ratio": -3.5e2,
              "nested": { "deep": { "value": "ok" } },
              "list": [1, "two", false, null]
            }
        """.trimIndent()

        val root = JsonParser.parseObject(json)

        assertEquals("Gudang Kain", root.string("name"))
        assertEquals(true, root.boolean("active"))
        assertEquals(12, root.int("count"))
        assertEquals(-350.0, root.double("ratio"))
        assertEquals("ok", root.obj("nested")?.obj("deep")?.string("value"))
        assertEquals(4, root.array("list").size)
    }

    @Test
    fun parseObject_shouldBeIndependentOfKeyOrder() {
        val first = JsonParser.parseObject("""{"a":1,"b":2}""")
        val second = JsonParser.parseObject("""{"b":2,"a":1}""")

        assertEquals(first.int("a"), second.int("a"))
        assertEquals(first.int("b"), second.int("b"))
    }

    @Test
    fun parseString_shouldDecodeEveryEscapeSequence() {
        val root = JsonParser.parseObject(
            """{"v":"quote:\" backslash:\\ slash:\/ tab:\t newline:\n unicode:é"}"""
        )

        assertEquals(
            "quote:\" backslash:\\ slash:/ tab:\t newline:\n unicode:é",
            root.string("v")
        )
    }

    @Test
    fun writeThenParse_withControlCharacters_shouldRoundTrip() {
        val raw = "line1\nline2\ttabbed \"quoted\" back\\slash"
        val encoded = jsonObjectOf("v" to jsonOf(raw)).encode()

        assertEquals(raw, JsonParser.parseObject(encoded).string("v"))
    }

    @Test
    fun stringMap_shouldReadFlatStringObject() {
        val root = JsonParser.parseObject("""{"params":{"a":"1","b":"2"},"other":{"c":3}}""")

        assertEquals(mapOf("a" to "1", "b" to "2"), root.stringMap("params"))
        // Non-string values are skipped rather than coerced.
        assertTrue(root.stringMap("other").isEmpty())
    }

    @Test
    fun typedAccessors_shouldReturnNullOnTypeMismatch() {
        val root = JsonParser.parseObject("""{"v":"not-a-number","n":null}""")

        assertNull(root.int("v"))
        assertNull(root.boolean("v"))
        assertNull(root.string("missing"))
        assertNull(root.string("n"))
    }

    @Test
    fun parse_withMalformedInput_shouldFailWithOffset() {
        listOf(
            """{"a":}""",
            """{"a" 1}""",
            """{"a":1,}""",
            """{"a":"unterminated""",
            """[1,2""",
            """{"a":1}trailing"""
        ).forEach { malformed ->
            val failure = assertFailsWith<JsonParseException>("Expected failure for: $malformed") {
                JsonParser.parse(malformed)
            }
            assertTrue(failure.offset >= 0)
        }
    }

    @Test
    fun parseObjectOrNull_shouldSwallowMalformedInput() {
        assertNull(JsonParser.parseObjectOrNull("""{"a":"#"""))
        assertNull(JsonParser.parseObjectOrNull(null))
        assertNull(JsonParser.parseObjectOrNull("   "))
        assertEquals(1, JsonParser.parseObjectOrNull("""{"a":1}""")?.int("a"))
    }

    @Test
    fun encode_shouldPreserveNumberLiterals() {
        val json = """{"big":12345678901234,"precise":0.1234567890123}"""

        assertEquals(json, JsonParser.parseObject(json).encode())
    }

    @Test
    fun parse_emptyContainers_shouldSucceed() {
        assertTrue(JsonParser.parseObject("{}").entries.isEmpty())
        assertTrue(JsonParser.parseArray("[]").isEmpty())
        assertTrue(JsonParser.parseObject("""{"a":[],"b":{}}""").array("a").isEmpty())
    }
}

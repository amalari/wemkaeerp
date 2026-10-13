package com.eventverse.app.shared.pack

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalizedTextCodecTest {

    @Test
    fun `bentuk lama string biasa dibaca sebagai id`() {
        val t = LocalizedTextCodec.decode(jsonOf("Potong"), "$.p")
        assertEquals(LocalizedText.of("Potong"), t)
    }

    @Test
    fun `bentuk baru objek dua bahasa round-trip`() {
        val original = LocalizedText.of("Potong", "Cutting")
        val wire = LocalizedTextCodec.encode(original)
        assertTrue(wire is JsonValue.Obj)
        assertEquals(original, LocalizedTextCodec.decode(wire, "$.p"))
    }

    @Test
    fun `hanya id ditulis sebagai string biasa agar pembaca lama tetap bisa`() {
        assertEquals(jsonOf("Potong"), LocalizedTextCodec.encode(LocalizedText.of("Potong")))
    }

    @Test
    fun `kode bahasa tak dikenal ditolak dengan path`() {
        val e = assertFailsWith<DomainPackDecodeException> {
            LocalizedTextCodec.decode(jsonObjectOf("id" to jsonOf("Potong"), "fr" to jsonOf("Coupe")), "$.p")
        }
        assertTrue(e.path.endsWith(".fr"))
    }

    @Test
    fun `objek tanpa id ditolak`() {
        assertFailsWith<DomainPackDecodeException> {
            LocalizedTextCodec.decode(jsonObjectOf("en" to jsonOf("Cutting")), "$.p")
        }
    }

    @Test
    fun `nilai bukan string ditolak`() {
        assertFailsWith<DomainPackDecodeException> {
            LocalizedTextCodec.decode(jsonObjectOf("id" to jsonOf(5)), "$.p")
        }
        assertFailsWith<DomainPackDecodeException> { LocalizedTextCodec.decode(null, "$.p") }
        assertFailsWith<DomainPackDecodeException> { LocalizedTextCodec.decode(JsonValue.Null, "$.p") }
    }

    @Test
    fun `teks kosong ditolak bukan jatuh ke id`() {
        assertFailsWith<DomainPackDecodeException> {
            LocalizedTextCodec.decode(jsonObjectOf("id" to jsonOf("Potong"), "en" to jsonOf("")), "$.p")
        }
    }

    @Test
    fun `pembaca bahasa en tetap mendapat fallback eksplisit dari teks lama`() {
        val r = LocalizedTextCodec.decode(jsonOf("Potong"), "$.p").resolve(LanguageCode.EN)
        assertTrue(r.isFallback)
    }
}

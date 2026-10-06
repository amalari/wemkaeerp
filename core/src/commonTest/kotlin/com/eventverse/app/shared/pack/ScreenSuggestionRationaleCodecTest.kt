package com.eventverse.app.shared.pack

import com.eventverse.app.domain.pack.GarmentDomainPack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenSuggestionRationaleCodecTest {

    @Test
    fun `rationale ikut round-trip lewat kawat pack`() {
        val pack = GarmentDomainPack.pack
        val decoded = DomainPackCodec.decode(DomainPackCodec.encodeToString(pack))
        assertEquals(pack.screenSuggestions.map { it.rationale }, decoded.screenSuggestions.map { it.rationale })
        assertTrue(decoded.screenSuggestions.all { !it.rationale.isNullOrBlank() })
    }

    @Test
    fun `pack lama tanpa kunci rationale tetap terbaca sebagai null`() {
        val json = DomainPackCodec.encodeToString(GarmentDomainPack.pack).replace(Regex(",\"rationale\":(\"[^\"]*\"|null)"), "")
        assertTrue(!json.contains("\"rationale\""))
        val decoded = DomainPackCodec.decode(json)
        assertTrue(decoded.screenSuggestions.isNotEmpty() && decoded.screenSuggestions.all { it.rationale == null })
    }
}

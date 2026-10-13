package com.eventverse.app.shared.pack

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.i18n.PackLanguages
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.missingTranslations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Jembatan P1/P2: pack lama byte-identik, pack dua bahasa round-trip, klaim `en` dibuktikan validator. */
class PackLocalizationSeamTest {

    private val garment = GarmentDomainPack.pack

    @Test
    fun `pack garment id saja tidak menulis kunci languages dan round-trip identik`() {
        val json = DomainPackCodec.encodeToString(garment)
        assertFalse(json.contains("\"languages\""))
        assertEquals(garment, DomainPackCodec.decode(json))
    }

    @Test
    fun `pack garment id saja tidak punya terjemahan yang hilang`() {
        assertTrue(garment.missingTranslations().isEmpty())
    }

    @Test
    fun `fase dan slot dua bahasa round-trip lewat kawat pack`() {
        val phase = garment.phases.first()
        val slot = garment.slots.first()
        val translated = garment.copy(
            phases = listOf(phase.copy(displayNameText = phase.displayNameText.with(LanguageCode.EN, "Phase EN"))) + garment.phases.drop(1),
            slots = listOf(slot.copy(displayNameText = slot.displayNameText.with(LanguageCode.EN, "Slot EN"))) + garment.slots.drop(1)
        )
        val decoded = DomainPackCodec.decode(DomainPackCodec.encodeToString(translated))
        assertEquals("Phase EN", decoded.phases.first().displayNameText.textOrNull(LanguageCode.EN))
        assertEquals("Slot EN", decoded.slots.first().displayNameText.textOrNull(LanguageCode.EN))
        assertEquals(phase.displayName, decoded.phases.first().displayName)
    }

    @Test
    fun `pack yang mengklaim en tetapi belum lengkap dilaporkan per jalur`() {
        val claimed = garment.copy(languages = PackLanguages(setOf(LanguageCode.ID, LanguageCode.EN)))
        val missing = claimed.missingTranslations()
        assertTrue(missing.any { it.path == "phases[0].displayName" })
        assertTrue(missing.all { it.language == LanguageCode.EN })
        val roundTrip = DomainPackCodec.decode(DomainPackCodec.encodeToString(claimed))
        assertEquals(claimed.languages, roundTrip.languages)
    }

    @Test
    fun `kode bahasa tak dikenal di languages ditolak`() {
        val json = DomainPackCodec.encodeToString(garment).trimEnd().removeSuffix("}") + ",\"languages\":[\"id\",\"fr\"]}"
        assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(json) }
    }

    @Test
    fun `displayNameText yang tidak cocok dengan displayName ditolak`() {
        val phase = garment.phases.first()
        assertFailsWith<IllegalArgumentException> { phase.copy(displayNameText = LocalizedText.of("Lain")) }
    }
}

package com.eventverse.app.shared.pack

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.i18n.PackLanguages
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.missingTranslations
import com.eventverse.app.domain.pack.withoutTranslations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kontrak kawat `menuLabel` (TRD-I18N-003 Track A): opsional, round-trip, ditolak bila rusak, pack lama byte-identik. */
class ModuleMenuLabelCodecTest {

    private val garment = GarmentDomainPack.pack
    private val target = GarmentModules.ORG_CHART

    private fun withLabel(label: LocalizedText) = garment.copy(
        modules = garment.modules.map { if (it.id == target) it.copy(menuLabelText = label) else it }
    )

    @Test
    fun packTanpaMenuLabel_tidakMenulisKunci_dan_roundTripIdentik() {
        val noLabels = garment.copy(modules = garment.modules.map { it.copy(menuLabelText = null) })
        val json = DomainPackCodec.encodeToString(noLabels)
        assertFalse(json.contains("\"menuLabel\""))
        assertEquals(noLabels, DomainPackCodec.decode(json))
    }

    @Test
    fun menuLabelDuaBahasa_roundTrip() {
        val label = LocalizedText.of("Bagan Organisasi", "Org Chart")
        val decoded = DomainPackCodec.decode(DomainPackCodec.encodeToString(withLabel(label)))
        assertEquals(label, decoded.module(target)?.menuLabelText)
    }

    @Test
    fun menuLabelBahasaTakDikenal_ditolak() {
        val json = DomainPackCodec.encodeToString(withLabel(LocalizedText.of("Bagan Organisasi", "Org Chart")))
            .replace("\"en\":\"Org Chart\"", "\"fr\":\"Organigramme\"")
        assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(json) }
    }

    @Test
    fun validator_melaporkanMenuLabelTanpaEn_bilaPackMengklaimEn() {
        val claimed = withLabel(LocalizedText.of("Bagan Organisasi")).copy(languages = PackLanguages(setOf(LanguageCode.ID, LanguageCode.EN)))
        assertTrue(claimed.missingTranslations().any { it.path.endsWith(".menuLabel") && it.language == LanguageCode.EN })
    }

    @Test
    fun withoutTranslations_membuangMenuLabel_agarDrafLamaTetapSahTerhadapPackBawaan() {
        val labelled = withLabel(LocalizedText.of("Bagan Organisasi", "Org Chart"))
        assertNull(labelled.withoutTranslations().module(target)?.menuLabelText)
    }
}

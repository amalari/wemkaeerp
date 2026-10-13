package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.i18n.PackLanguages
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleAction
import com.eventverse.app.domain.pack.ModuleActionCode
import com.eventverse.app.domain.pack.RoleHint
import com.eventverse.app.domain.pack.ScreenSuggestion
import com.eventverse.app.domain.pack.VocabularyKey
import com.eventverse.app.domain.pack.localizedTexts
import com.eventverse.app.domain.pack.missingTranslations
import com.eventverse.app.domain.pack.withoutTranslations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TRD-I18N-002 P3–P5: modul, seksi, aksi, kosakata, label port, usulan layar, dan petunjuk peran dua bahasa.
 * Memakai pack **non-garment** (klinik) supaya bukan hanya bukti untuk konveksi (tenant-variability Kontrak 6).
 */
class PackLocalizationP3P5Test {

    private fun en(id: String, text: String) = LocalizedText.of(id, text)

    /** Pack klinik berisi satu butir dari tiap tipe P3–P5, semuanya dua bahasa. */
    private fun klinikBilingual(): DomainPack {
        val base = InterviewFixtures.klinikPack
        val poli = base.modules.first { it.id.value == "klinik_poli" }
        val port = base.portTypes.first().value
        return base.copy(
            phases = base.phases.map { it.copy(displayNameText = en(it.displayName, "Phase ${it.code.value}"), subtitleText = it.subtitleText?.with(LanguageCode.EN, "Daily flow")) },
            slots = base.slots.map { it.copy(displayNameText = en(it.displayName, "Slot ${it.code.value}")) },
            sections = base.sections.map { it.copy(displayNameText = en(it.displayName, "Section ${it.code.value}")) },
            modules = base.modules.map { it.copy(displayNameText = en(it.displayName, "Module ${it.id.value}"), descriptionText = it.descriptionText?.with(LanguageCode.EN, "Desc ${it.id.value}")) },
            actions = listOf(ModuleAction(ModuleActionCode.ADD, "Daftar", en("Daftar", "Register"))),
            vocabulary = mapOf(VocabularyKey.WORKPLACE to "klinik"),
            vocabularyText = mapOf(VocabularyKey.WORKPLACE to en("klinik", "clinic")),
            portLabels = mapOf(port to "Permintaan Poli"),
            portLabelTexts = mapOf(port to en("Permintaan Poli", "Clinic Request")),
            screenSuggestions = listOf(
                ScreenSuggestion(
                    poli.id, "Antrean Poli", WidgetKind.KANBAN, rationale = "Dipilih karena antrean bergerak.",
                    titleText = en("Antrean Poli", "Clinic Queue"), rationaleText = en("Dipilih karena antrean bergerak.", "Chosen because the queue moves.")
                )
            ),
            roleHints = listOf(RoleHint("perawat", "Perawat", poli.id, en("Perawat", "Nurse"))),
            languages = PackLanguages(setOf(LanguageCode.ID, LanguageCode.EN))
        )
    }

    @Test
    fun `pack non-garment dua bahasa round-trip untuk seluruh tipe P3 sampai P5`() {
        val pack = klinikBilingual()
        val decoded = DomainPackCodec.decode(DomainPackCodec.encodeToString(pack))
        assertEquals(pack, decoded)
        assertEquals("Clinic Queue", decoded.screenSuggestions.single().titleText.textOrNull(LanguageCode.EN))
        assertEquals("Nurse", decoded.roleHints.single().labelText.textOrNull(LanguageCode.EN))
        assertEquals("clinic", decoded.vocabularyText.getValue(VocabularyKey.WORKPLACE).textOrNull(LanguageCode.EN))
        // field String lama tetap teks id
        assertEquals("Perawat", decoded.roleHints.single().label)
        assertEquals("klinik", decoded.term(VocabularyKey.WORKPLACE))
    }

    @Test
    fun `validator memeriksa setiap jalur P3 sampai P5 pada pack non-garment`() {
        val pack = klinikBilingual()
        assertEquals(emptyList(), pack.missingTranslations())
        val paths = pack.localizedTexts().map { it.first }
        listOf("sections[0].displayName", "modules[0].displayName", "actions[0].label", "vocabulary.WORKPLACE",
            "screenSuggestions[0].title", "screenSuggestions[0].rationale", "roleHints[0].label").forEach { assertTrue(it in paths, it) }
        assertTrue(paths.any { it.startsWith("portLabels.") })
        // klaim en pada pack id-saja -> setiap jalur dilaporkan
        val claimedOnly = pack.withoutTranslations().copy(languages = PackLanguages(setOf(LanguageCode.ID, LanguageCode.EN)))
        val missing = claimedOnly.missingTranslations().map { it.path }
        assertTrue("roleHints[0].label" in missing && "actions[0].label" in missing && "screenSuggestions[0].title" in missing)
    }

    @Test
    fun `pack id saja ditulis seperti dulu tanpa objek bahasa dan tanpa kunci languages`() {
        val json = DomainPackCodec.encodeToString(klinikBilingual().withoutTranslations())
        assertFalse(json.contains("\"en\""))
        assertFalse(json.contains("\"languages\""))
        assertEquals(klinikBilingual().withoutTranslations(), DomainPackCodec.decode(json))
    }

    @Test
    fun `bentuk lama string dibaca sebagai teks id untuk modul seksi aksi dan label`() {
        val idOnly = InterviewFixtures.klinikPack.withoutTranslations()
        val decoded = DomainPackCodec.decode(DomainPackCodec.encodeToString(idOnly))
        assertTrue(decoded.modules.all { it.displayNameText.languages == setOf(LanguageCode.ID) })
        assertEquals(idOnly, decoded)
    }

    @Test
    fun `kode bahasa tak dikenal pada modul seksi aksi dan kosakata ditolak dengan path`() {
        val json = DomainPackCodec.encodeToString(klinikBilingual())
        val ex = assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(json.replaceFirst("\"en\":\"Module", "\"fr\":\"Module")) }
        assertTrue(ex.path.startsWith("$.modules["), ex.path)
        assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(json.replace("\"en\":\"Register\"", "\"xx\":\"Register\"")) }
        assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(json.replace("\"en\":\"clinic\"", "\"fr\":\"clinic\"")) }
        assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(json.replace("\"en\":\"Nurse\"", "\"fr\":\"Nurse\"")) }
    }

    @Test
    fun `teks tampilan tanpa id atau kosong ditolak dan kunci kosakata tak dikenal ditolak`() {
        val raw = DomainPackCodec.encodeToString(klinikBilingual())
        assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(raw.replace("\"vocabulary\":{\"WORKPLACE\"", "\"vocabulary\":{\"BUKAN_KUNCI\"")) }
        assertFailsWith<IllegalArgumentException> { LocalizedText.of("Perawat", "  ") }
    }

    @Test
    fun `resolve en pada teks tanpa terjemahan menandai pengembalian ke id dan bukan fallback senyap`() {
        val hint = klinikBilingual().withoutTranslations().roleHints.single()
        val resolved = hint.labelText.resolve(LanguageCode.EN)
        assertEquals("Perawat", resolved.text)
        assertTrue(resolved.isFallback)
    }

    @Test
    fun `salinan label id yang tidak sepasang dengan teks dua bahasanya ditolak`() {
        val pack = klinikBilingual()
        assertFailsWith<IllegalArgumentException> { pack.copy(vocabulary = mapOf(VocabularyKey.WORKPLACE to "poliklinik")) }
        assertFailsWith<IllegalArgumentException> { pack.roleHints.single().copy(label = "Lain") }
        assertFailsWith<IllegalArgumentException> { pack.actions.single().copy(label = "Lain") }
    }

    @Test
    fun `pack garment bawaan punya terjemahan en di seluruh jalur P1 sampai P5`() {
        val garment = GarmentDomainPack.pack
        val texts = garment.localizedTexts()
        assertTrue(texts.size > 100, "jalur terdaftar: ${texts.size}")
        assertTrue(texts.all { (_, t) -> t.has(LanguageCode.EN) })
        listOf("phases[", "slots[", "sections[", "modules[", "actions[", "vocabulary.", "portLabels.", "screenSuggestions[", "roleHints[")
            .forEach { prefix -> assertTrue(texts.any { it.first.startsWith(prefix) }, prefix) }
        // istilah industri baku
        assertTrue(garment.portLabelTexts.getValue("DispatchedShipmentManifest").textOrNull(LanguageCode.EN)!!.contains("Delivery Note"))
        assertTrue(garment.slots.first { it.code.value == "cutting" }.displayNameText.textOrNull(LanguageCode.EN)!!.contains("Cutting"))
    }

    @Test
    fun `teks id pack garment per jalur identik dengan sebelum terjemahan`() {
        val garment = GarmentDomainPack.pack
        val idOnly = garment.withoutTranslations()
        assertEquals(garment.localizedTexts().filter { !it.first.endsWith(".menuLabel") }.map { it.first to it.second.idText }, idOnly.localizedTexts().map { it.first to it.second.idText })
        assertEquals(idOnly, DomainPackCodec.decode(DomainPackCodec.encodeToString(idOnly)))
    }
}

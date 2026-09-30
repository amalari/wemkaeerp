package com.eventverse.app.domain.pack

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.DomainPackDecodeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A4 — chrome `/m/{code}` tidak boleh berbicara kosakata satu vertikal.
 *
 * Fixture-nya **klinik**, bukan konveksi (tenant-variability-rules Kontrak 6). Pack ini juga memakai modul
 * platform bersama (`org_chart`) — dan itulah alasan `actions`/`vocabulary` tinggal di [DomainPack], bukan di
 * [ModuleDefinition]: [DomainPackRegistry.violations] mewajibkan definisi modul bersama **identik** lintas pack,
 * sedangkan kata-kata chrome memang berbeda antar vertikal.
 */
class PackVocabularyTest {

    private val klinik = DomainPack(
        code = DomainPackCode("klinik"),
        displayName = "Klinik & Layanan Kesehatan",
        phases = listOf(PhaseDefinition(PhaseCode("LAYANAN"), 1, "1. Layanan", "Pasien datang", 0xFF2563EB)),
        slots = listOf(
            SlotDefinition(SlotCode("klinik_layanan"), "Layanan", PhaseCode("LAYANAN"), PortType("Kunjungan"), PortType("Rekam"))
        ),
        portTypes = setOf(PortType("Kunjungan"), PortType("Rekam")),
        wiredPortTypes = setOf(PortType("Kunjungan"), PortType("Rekam")),
        sections = listOf(
            // Modul platform bersama (`org_chart`) wajib punya seksi & slotnya di pack pemakainya (B7).
            requireNotNull(GarmentDomainPack.pack.sections.firstOrNull { it.code.value == "GOVERNANCE" }),
            ModuleSection(ModuleSectionCode("LAYANAN"), "Layanan", 2, 0xFF16A34A, 0xFFF0FDF4)
        ),
        modules = listOf(
            ModuleDefinition(
                id = ModuleId("klinik_antrean"), displayName = "Antrean Pasien", description = "Antrean pendaftaran & poli",
                section = ModuleSectionCode("LAYANAN"), kind = ModuleKind.OPERATIONAL, iconKey = "clipboard",
                scopeCapability = ScopeCapability.HIERARCHICAL,
                supportedScopes = setOf(DataScope.OWN_DATA_ONLY, DataScope.ALL_TENANT_DATA),
                slot = SlotCode("klinik_layanan")
            )
        ),
        actions = listOf(
            ModuleAction(ModuleActionCode.ADD, "Tambah Kunjungan"),
            ModuleAction(ModuleActionCode.EDIT, "Ubah Kunjungan"),
            ModuleAction(ModuleActionCode.APPROVE, "Setujui Kunjungan"),
            ModuleAction(ModuleActionCode.DELETE, "Hapus Kunjungan")
        ),
        vocabulary = mapOf(VocabularyKey.WORKPLACE to "klinik", VocabularyKey.DOCUMENT to "Kunjungan")
    )

    /** Kata netral = milik platform. Begitu ia memuat kata satu vertikal, tenant lain melihat kata itu. */
    @Test
    fun neutralWords_neverCarryVerticalVocabulary() {
        val vertical = listOf("pabrik", "spk", "kain", "jahit", "konveksi", "garmen", "makloon", "bordir", "sample")
        val neutral = VocabularyKey.entries.map { it.neutral } + ModuleActionCode.entries.map { it.neutralLabel }
        neutral.forEach { word ->
            vertical.forEach { bad ->
                assertFalse(word.lowercase().contains(bad), "Kata netral '$word' memuat kosakata vertikal '$bad'")
            }
        }
    }

    @Test
    fun packWithoutVocabulary_speaksNeutralWords_notGarment() {
        val bare = klinik.copy(actions = ModuleActionCode.neutral, vocabulary = emptyMap())

        assertEquals("perusahaan", bare.term(VocabularyKey.WORKPLACE))
        assertEquals("dokumen", bare.term(VocabularyKey.DOCUMENT))
        assertEquals("Tambah", bare.actionLabel(ModuleActionCode.ADD))

        val chrome = bare.actions.map { it.label } + VocabularyKey.entries.map { bare.term(it) }
        assertTrue(chrome.none { it.lowercase().contains("pabrik") }, "Pack tanpa vocabulary tidak boleh berbicara 'pabrik': $chrome")
        assertTrue(chrome.none { it.lowercase().contains("spk") }, "…maupun 'SPK': $chrome")
    }

    @Test
    fun declaredVocabularyAndActions_winOverNeutral() {
        assertEquals("klinik", klinik.term(VocabularyKey.WORKPLACE))
        assertEquals("Kunjungan", klinik.term(VocabularyKey.DOCUMENT))
        assertEquals("Tambah Kunjungan", klinik.actionLabel(ModuleActionCode.ADD))
        // Wewenang minimum tetap milik peran aksinya (konsep platform), bukan data pack:
        assertEquals(com.eventverse.app.domain.rbac.AccessLevel.MANAGE, ModuleActionCode.APPROVE.requiredLevel)
    }

    @Test
    fun packReusingPlatformModule_isAllowed_becauseChromeWordsLiveAtPackLevel() {
        val shared = listOf(requireNotNull(GarmentDomainPack.pack.module(GarmentModules.ORG_CHART))) + klinik.modules
        val mixed = klinik.copy(modules = shared)
        assertEquals(emptyList(), DomainPackRegistry.violations(mixed))

        // Bukti mengapa `actions`/`vocabulary` TIDAK boleh menempel di ModuleDefinition: satu perubahan
        // apa pun pada modul bersama sudah membuat pack ini ditolak saat didaftarkan.
        val renamed = shared.first().copy(displayName = "Pegawai Klinik")
        assertTrue(DomainPackRegistry.violations(mixed.copy(modules = listOf(renamed) + shared.drop(1))).isNotEmpty())
    }

    @Test
    fun codec_roundTripsActionsAndVocabulary() {
        assertEquals(klinik, DomainPackCodec.decode(DomainPackCodec.encodeToString(klinik)))
    }

    /** Pack yang tersimpan sebelum A4 tidak punya dua field ini; ia harus tetap terbaca, dengan kata netral. */
    @Test
    fun codec_acceptsPackWithoutNewFields_keepingStoredPacksLoadable() {
        val legacy = DomainPackCodec.encodeToString(klinik)
            .replace(Regex(",\"actions\":\\[[^]]*]"), "")
            .replace(Regex(",\"vocabulary\":\\{[^}]*}"), "")

        val decoded = DomainPackCodec.decode(legacy)
        assertEquals(ModuleActionCode.neutral, decoded.actions)
        assertEquals("perusahaan", decoded.term(VocabularyKey.WORKPLACE))
    }

    @Test
    fun codec_rejectsUnknownVocabularyKey_andUnknownActionCode() {
        val json = DomainPackCodec.encodeToString(klinik)

        val unknownKey = assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(json.replace("\"WORKPLACE\"", "\"MESIN\"")) }
        assertTrue(unknownKey.path.endsWith("vocabulary.MESIN"), "path=${unknownKey.path}")

        val unknownAction = assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(json.replace("\"APPROVE\"", "\"ARSIP\"")) }
        assertTrue(unknownAction.path.contains("actions[2].code"), "path=${unknownAction.path}")
    }

    @Test
    fun duplicateActionOrBlankWord_isRejected() {
        assertFailsWith<IllegalStateException> {
            klinik.copy(actions = listOf(ModuleAction(ModuleActionCode.ADD, "Tambah"), ModuleAction(ModuleActionCode.ADD, "Tambah lagi")))
        }
        assertFailsWith<IllegalArgumentException> { klinik.copy(vocabulary = mapOf(VocabularyKey.WORKPLACE to "  ")) }
        assertFailsWith<IllegalArgumentException> { ModuleAction(ModuleActionCode.ADD, "  ") }
    }

    /**
     * Kata platform lain yang juga tampil di chrome `/m/{code}`: label & deskripsi wewenang/cakupan data.
     * Ini penjaga regresi termurah — dulu "pabrik" bersembunyi di deskripsi `DataScope`, bukan di kode layar.
     */
    @Test
    fun platformAccessWording_isVerticalNeutral() {
        val vertical = listOf("pabrik", "spk", "kain", "jahit", "konveksi", "makloon", "bordir")
        val words = AccessLevel.entries.flatMap { listOf(it.displayName, it.shortDescription) } +
            DataScope.entries.flatMap { listOf(it.displayName, it.shortLabel, it.description) } +
            ScopeCapability.entries.flatMap { listOf(it.displayName, it.shortLabel, it.description) }

        words.forEach { word ->
            vertical.forEach { bad ->
                assertFalse(word.lowercase().contains(bad), "Kata platform '$word' memuat kosakata vertikal '$bad'")
            }
        }
    }
}

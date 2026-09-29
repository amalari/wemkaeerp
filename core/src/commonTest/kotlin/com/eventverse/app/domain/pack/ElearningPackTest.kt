package com.eventverse.app.domain.pack

import com.eventverse.app.domain.rbac.AccessDecisionEngine
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.moduleIds
import com.eventverse.app.domain.rbac.operationalModules
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.ScopeCapability
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.rbac.displayName
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **Bukti akhir B6 (B6g, TRD-PLAT-001 AC-5)**: vertikal non-konveksi — e-learning — mendefinisikan modulnya sebagai
 * **data** dan langsung berlaku di katalog modul, parser kunci tersimpan, dan mesin wewenang, tanpa satu baris kode
 * inti yang menyebut e-learning.
 */
class ElearningPackTest {

    private val tenant = TenantId("ten-kursus")

    @Test
    fun elearningModules_areCatalogued_parsed_andGated_withoutCoreChanges() = ElearningPack.registered {
        val pack = ElearningPack.pack
        assertEquals(listOf("elearning_enrollment", "elearning_grading", "org_chart"), pack.moduleIds.map { it.value })
        assertEquals(listOf("elearning_grading"), pack.operationalModules.map { it.value })
        assertEquals("Penilaian Tugas", ElearningPack.GRADING.displayName)

        // Kunci tersimpan RBAC (NAME) & katalog (code) untuk modul pack baru, lewat parser yang sama.
        assertEquals(ElearningPack.GRADING, ModuleIdCodec.fromStoredName("ELEARNING_GRADING", "t"))
        assertEquals(ElearningPack.ENROLLMENT, ModuleIdCodec.fromCode("elearning_enrollment", "t"))
        assertNull(pack.module(GarmentModules.CRM_SALES), "modul konveksi tidak ada di pack e-learning")

        // Mesin wewenang: jabatan "Tutor" VIEW penilaian, tanpa akses pendaftaran.
        val tutor = CustomRole(RoleId("role-tutor"), tenant, "Tutor", "menilai tugas",
            modulePermissions = mapOf(ElearningPack.GRADING to ModuleAccessConfig(level = AccessLevel.VIEW)))
        val persona = TestingPersona("u1", "Tutor A", tenant, "kursus", null, "", tutor.id, tutor.name)
        val decisions = AccessDecisionEngine.explainAll(persona, listOf(tutor), emptyMap(), modules = pack.moduleIds)
        assertEquals(AccessLevel.VIEW, decisions.getValue(ElearningPack.GRADING).config.level)
        assertEquals(AccessLevel.NONE, decisions.getValue(ElearningPack.ENROLLMENT).config.level)
        assertEquals(setOf("elearning_enrollment", "elearning_grading", "org_chart"), decisions.keys.map { it.value }.toSet())
    }
}

/** Pack e-learning fiktif — hanya data. */
internal object ElearningPack {
    val ENROLLMENT = ModuleId("elearning_enrollment")
    val GRADING = ModuleId("elearning_grading")

    /** Pack data didaftarkan selama [block] (B7: definisi modul dicari lintas pack terdaftar). */
    fun <T> registered(block: () -> T): T {
        DomainPackRegistry.register(pack)
        try { return block() } finally { DomainPackRegistry.unregister(pack.code) }
    }

    private val phases = listOf(
        PhaseDefinition(PhaseCode("ACQUISITION"), 1, "1. Akuisisi", "Pendaftaran peserta", 0xFF2563EB),
        PhaseDefinition(PhaseCode("ASSESSMENT"), 2, "2. Penilaian", "Tugas & ujian", 0xFF16A34A)
    )
    private val ports = setOf("Enrollment", "Submission", "GradedResult").map(::PortType).toSet()
    private val gradingSlot = SlotDefinition(SlotCode("elearning_grading"), "Penilaian", PhaseCode("ASSESSMENT"), PortType("Submission"), PortType("GradedResult"))

    val pack = DomainPack(
        code = DomainPackCode("elearning"),
        displayName = "Kursus & Pelatihan",
        phases = phases,
        slots = listOf(gradingSlot),
        portTypes = ports,
        wiredPortTypes = ports,
        sections = listOf(
            GarmentDomainPack.pack.sections.first { it.code.value == "GOVERNANCE" },
            ModuleSection(ModuleSectionCode("LEARNING"), "Pembelajaran", 2, 0xFF2563EB, 0xFFEFF6FF)
        ),
        modules = listOf(
            ModuleDefinition(ENROLLMENT, "Pendaftaran Peserta", "Pendaftaran kelas", ModuleSectionCode("LEARNING"),
                ModuleKind.FOUNDATION, "clipboard", ScopeCapability.GLOBAL_ONLY, setOf(DataScope.ALL_TENANT_DATA), null),
            ModuleDefinition(GRADING, "Penilaian Tugas", "Menilai tugas peserta", ModuleSectionCode("LEARNING"),
                ModuleKind.OPERATIONAL, "check_circle", ScopeCapability.HIERARCHICAL,
                setOf(DataScope.OWN_DATA_ONLY, DataScope.SUBORDINATE_DATA, DataScope.ALL_TENANT_DATA), SlotCode("elearning_grading")),
            // Modul platform dipakai bersama: definisinya wajib identik dengan pack lain (B7 FR-1).
            requireNotNull(GarmentDomainPack.pack.module(GarmentModules.ORG_CHART))
        )
    )
}

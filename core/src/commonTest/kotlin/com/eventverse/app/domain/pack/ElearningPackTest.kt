package com.eventverse.app.domain.pack

import com.eventverse.app.domain.rbac.AccessDecisionEngine
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModules
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
    fun elearningModules_areCatalogued_parsed_andGated_withoutCoreChanges() = DomainPackRegistry.withSoleActivePackForTest(ElearningPack.pack) {
        assertEquals(listOf("enrollment", "grading", "org_chart"), BusinessModules.entries.map { it.value })
        assertEquals(listOf("grading"), BusinessModules.operational.map { it.value })
        assertEquals("Penilaian Tugas", ElearningPack.GRADING.displayName)

        // Kunci tersimpan RBAC (NAME) & katalog (code) untuk modul pack baru, lewat parser yang sama.
        assertEquals(ElearningPack.GRADING, ModuleIdCodec.fromStoredName("GRADING", "t"))
        assertEquals(ElearningPack.ENROLLMENT, ModuleIdCodec.fromCode("enrollment", "t"))
        assertNull(ModuleIdCodec.standardOrNull("crm_sales"), "modul konveksi tidak ada di pack e-learning")

        // Mesin wewenang: jabatan "Tutor" VIEW penilaian, tanpa akses pendaftaran.
        val tutor = CustomRole(RoleId("role-tutor"), tenant, "Tutor", "menilai tugas",
            modulePermissions = mapOf(ElearningPack.GRADING to ModuleAccessConfig(level = AccessLevel.VIEW)))
        val persona = TestingPersona("u1", "Tutor A", tenant, "kursus", null, "", tutor.id, tutor.name)
        val decisions = AccessDecisionEngine.explainAll(persona, listOf(tutor), emptyMap())
        assertEquals(AccessLevel.VIEW, decisions.getValue(ElearningPack.GRADING).config.level)
        assertEquals(AccessLevel.NONE, decisions.getValue(ElearningPack.ENROLLMENT).config.level)
        assertEquals(setOf("enrollment", "grading", "org_chart"), decisions.keys.map { it.value }.toSet())
    }
}

/** Pack e-learning fiktif — hanya data. */
internal object ElearningPack {
    val ENROLLMENT = ModuleId("enrollment")
    val GRADING = ModuleId("grading")
    private val ORG_CHART = ModuleId("org_chart")

    private val phases = listOf(
        PhaseDefinition(PhaseCode("ACQUISITION"), 1, "1. Akuisisi", "Pendaftaran peserta", 0xFF2563EB),
        PhaseDefinition(PhaseCode("ASSESSMENT"), 2, "2. Penilaian", "Tugas & ujian", 0xFF16A34A)
    )
    private val ports = setOf("Enrollment", "Submission", "GradedResult").map(::PortType).toSet()
    private val gradingSlot = SlotDefinition(SlotCode("grading"), "Penilaian", PhaseCode("ASSESSMENT"), PortType("Submission"), PortType("GradedResult"))

    val pack = DomainPack(
        code = DomainPackCode("elearning"),
        displayName = "Kursus & Pelatihan",
        phases = phases,
        slots = listOf(gradingSlot),
        portTypes = ports,
        wiredPortTypes = ports,
        sections = listOf(
            ModuleSection(ModuleSectionCode("SYSTEM"), "Sistem", 1, 0xFF2563EB, 0xFFEFF6FF),
            ModuleSection(ModuleSectionCode("LEARNING"), "Pembelajaran", 2, 0xFF2563EB, 0xFFEFF6FF)
        ),
        modules = listOf(
            ModuleDefinition(ENROLLMENT, "Pendaftaran Peserta", "Pendaftaran kelas", ModuleSectionCode("LEARNING"),
                ModuleKind.FOUNDATION, "clipboard", ScopeCapability.GLOBAL_ONLY, setOf(DataScope.ALL_TENANT_DATA), null),
            ModuleDefinition(GRADING, "Penilaian Tugas", "Menilai tugas peserta", ModuleSectionCode("LEARNING"),
                ModuleKind.OPERATIONAL, "check_circle", ScopeCapability.HIERARCHICAL,
                setOf(DataScope.OWN_DATA_ONLY, DataScope.SUBORDINATE_DATA, DataScope.ALL_TENANT_DATA), SlotCode("grading")),
            ModuleDefinition(ORG_CHART, "Struktur Lembaga", "Struktur organisasi", ModuleSectionCode("SYSTEM"),
                ModuleKind.GOVERNANCE, "users", ScopeCapability.GLOBAL_ONLY, setOf(DataScope.ALL_TENANT_DATA), null)
        )
    )
}

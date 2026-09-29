package com.eventverse.app.presentation.navigation

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSection
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Bukti akhir B6 di klien (B6g)**: modul pack e-learning — yang tidak punya satu pun layar khusus — muncul di menu
 * sesuai wewenang dan terbuka lewat `/m/{code}`, tanpa `AppNavScreen` baru maupun perubahan kode inti.
 */
class ElearningNavMenuTest {

    private val grading = ModuleId("grading")
    private val enrollment = ModuleId("enrollment")
    private val ports = setOf("Enrollment", "Submission", "GradedResult").map(::PortType).toSet()
    private val pack = DomainPack(
        code = DomainPackCode("elearning"), displayName = "Kursus & Pelatihan",
        phases = listOf(PhaseDefinition(PhaseCode("ASSESSMENT"), 1, "Penilaian", "", 0xFF16A34A)),
        slots = listOf(SlotDefinition(SlotCode("grading"), "Penilaian", PhaseCode("ASSESSMENT"), PortType("Submission"), PortType("GradedResult"))),
        portTypes = ports, wiredPortTypes = ports,
        sections = listOf(ModuleSection(ModuleSectionCode("LEARNING"), "Pembelajaran", 1)),
        modules = listOf(
            ModuleDefinition(enrollment, "Pendaftaran Peserta", "", ModuleSectionCode("LEARNING"), ModuleKind.FOUNDATION,
                "clipboard", ScopeCapability.GLOBAL_ONLY, setOf(DataScope.ALL_TENANT_DATA), null),
            ModuleDefinition(grading, "Penilaian Tugas", "", ModuleSectionCode("LEARNING"), ModuleKind.OPERATIONAL,
                "check_circle", ScopeCapability.HIERARCHICAL, setOf(DataScope.OWN_DATA_ONLY, DataScope.ALL_TENANT_DATA), SlotCode("grading"))
        )
    )

    @Test
    fun tutorWithGradingView_seesGradingViaGenericRoute_andNothingElse() = DomainPackRegistry.withSoleActivePackForTest(pack) {
        val menu = buildNavMenu(mapOf(grading to ModuleAccessConfig(level = AccessLevel.VIEW)), auditView = false)

        assertEquals(listOf("Pembelajaran"), menu.map { it.title })
        val entry = menu.single().entries.single()
        assertEquals(AppNavScreen.MODULE, entry.screen)
        assertEquals("/m/grading", entry.route)
        assertEquals("Penilaian Tugas", entry.title)
        assertEquals(grading, entry.module)
        assertEquals("/m/grading", firstAccessibleEntry(menu)?.route, "pendaratan pun bisa ke modul tanpa layar khusus")
    }

    @Test
    fun withoutAccess_moduleIsHidden_butAuditViewShowsItLocked() = DomainPackRegistry.withSoleActivePackForTest(pack) {
        assertTrue(buildNavMenu(emptyMap(), auditView = false).isEmpty(), "tanpa wewenang: tidak ada menu sama sekali")
        val audit = buildNavMenu(emptyMap(), auditView = true).single().entries
        assertEquals(listOf("/m/enrollment", "/m/grading"), audit.map { it.route })
        assertTrue(audit.all { it.locked })
    }

    @Test
    fun genericPath_resolvesOnlyModulesOfTheActivePack() = DomainPackRegistry.withSoleActivePackForTest(pack) {
        assertEquals(grading, moduleFromGenericPath("/m/grading"))
        assertEquals(grading, moduleFromGenericPath("/m/grading?tab=1"))
        assertNull(moduleFromGenericPath("/m/crm_sales"), "modul konveksi tidak ada di pack e-learning")
    }
}

package com.eventverse.app.domain.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Invariant pack diuji dengan **vertikal non-konveksi** (fixture e-learning), membuktikan bentuk
 * pack tidak diam-diam mengasumsikan garmen (tenant-variability-rules Kontrak 6).
 */
class DomainPackInvariantTest {

    private fun phase(code: String, order: Int) = PhaseDefinition(PhaseCode(code), order, code, "", 0xFF2563EB)
    private fun slot(code: String, phase: String, input: String, output: String) =
        SlotDefinition(SlotCode(code), code, PhaseCode(phase), PortType(input), PortType(output))

    private val ports = setOf("Lead", "Enrollment", "CourseModule", "Submission", "GradedResult").map(::PortType).toSet()

    private fun elearning(
        phases: List<PhaseDefinition> = listOf(phase("ACQUISITION", 1), phase("DELIVERY", 2), phase("ASSESSMENT", 3)),
        slots: List<SlotDefinition> = listOf(
            slot("enrollment", "ACQUISITION", "Lead", "Enrollment"),
            slot("course_delivery", "DELIVERY", "Enrollment", "CourseModule"),
            slot("grading", "ASSESSMENT", "Submission", "GradedResult")
        ),
        wired: Set<PortType> = ports
    ) = DomainPack(DomainPackCode("elearning"), "E-Learning", phases, slots, ports, wired)

    @Test
    fun elearningPack_isValid_andOrdersPhases() {
        val pack = elearning(phases = listOf(phase("ASSESSMENT", 3), phase("ACQUISITION", 1), phase("DELIVERY", 2)))
        assertEquals(listOf("ACQUISITION", "DELIVERY", "ASSESSMENT"), pack.orderedPhases.map { it.code.value })
        assertEquals(PhaseCode("ASSESSMENT"), pack.slot(SlotCode("grading"))?.phase)
    }

    @Test
    fun slotPointingToUnknownPhase_isRejected() {
        assertFailsWith<IllegalArgumentException> { elearning(slots = listOf(slot("x", "CERTIFICATION", "Lead", "Enrollment"))) }
    }

    @Test
    fun slotUsingUnregisteredPort_isRejected() {
        assertFailsWith<IllegalArgumentException> { elearning(slots = listOf(slot("x", "DELIVERY", "Lead", "Certificate"))) }
    }

    @Test
    fun duplicatePhaseCodeOrOrder_isRejected() {
        assertFailsWith<IllegalStateException> { elearning(phases = listOf(phase("A", 1), phase("A", 2)), slots = emptyList()) }
        assertFailsWith<IllegalStateException> { elearning(phases = listOf(phase("A", 1), phase("B", 1)), slots = emptyList()) }
    }

    @Test
    fun wiredPortOutsideVocabulary_isRejected() {
        assertFailsWith<IllegalStateException> { elearning(wired = ports + PortType("Certificate")) }
    }

    @Test
    fun blankOrMalformedCodes_areRejected() {
        assertFailsWith<IllegalArgumentException> { PhaseCode("") }
        assertFailsWith<IllegalArgumentException> { SlotCode("order ingestion") }
        assertFailsWith<IllegalArgumentException> { PortType("1Bundle") }
    }

    private val learning = ModuleSection(ModuleSectionCode("LEARNING"), "Pembelajaran", 1, 0xFF2563EB, 0xFFEFF6FF)

    private fun module(id: String, section: String = "LEARNING", kind: com.eventverse.app.domain.rbac.ModuleKind = com.eventverse.app.domain.rbac.ModuleKind.OPERATIONAL, slot: String? = "grading") =
        ModuleDefinition(ModuleId(id), id, "uji", ModuleSectionCode(section), kind, "clipboard",
            com.eventverse.app.domain.rbac.ScopeCapability.HIERARCHICAL, setOf(com.eventverse.app.domain.rbac.DataScope.OWN_DATA_ONLY), slot?.let(::SlotCode))

    private fun withModules(vararg m: ModuleDefinition) =
        elearning().copy(sections = listOf(learning), modules = m.toList()).let {
            DomainPack(it.code, it.displayName, it.phases, it.slots, it.portTypes, it.wiredPortTypes, it.sections, it.modules)
        }

    @Test
    fun elearningModule_isValid_andLookedUpById() {
        val pack = withModules(module("grading_desk"))
        assertEquals("GRADING_DESK", pack.module(ModuleId("grading_desk"))?.id?.storedName)
    }

    @Test
    fun moduleInvariants_rejectUnknownSectionOrSlot_duplicates_andSlotOnNonOperational() {
        assertFailsWith<IllegalArgumentException> { withModules(module("x", section = "SALES")) }
        assertFailsWith<IllegalArgumentException> { withModules(module("x", slot = "sewing")) }
        assertFailsWith<IllegalStateException> { withModules(module("x"), module("x")) }
        assertFailsWith<IllegalArgumentException> { module("x", kind = com.eventverse.app.domain.rbac.ModuleKind.GOVERNANCE, slot = "grading") }
        assertFailsWith<IllegalArgumentException> { ModuleId("Grading Desk") }
    }
}

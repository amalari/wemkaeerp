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
}

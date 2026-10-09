package com.eventverse.app.domain.orgchart

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.pack.VocabularyKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StarterOrgChartPolicyTest {

    private fun dataPack(code: String, vocabulary: Map<VocabularyKey, String> = emptyMap()) = DomainPack(
        code = DomainPackCode(code),
        displayName = code,
        phases = listOf(PhaseDefinition(PhaseCode("LAYANAN"), 1, "1. Layanan", "Pasien datang", 0xFF2563EB)),
        slots = listOf(SlotDefinition(SlotCode("slot_a"), "Layanan", PhaseCode("LAYANAN"), PortType("A"), PortType("B"))),
        portTypes = setOf(PortType("A"), PortType("B")),
        wiredPortTypes = setOf(PortType("A"), PortType("B")),
        vocabulary = vocabulary
    )

    @Test
    fun garmentPack_hasStarterOrgChart() {
        assertTrue(StarterOrgChartPolicy.isAvailableFor(GarmentDomainPack.pack))
    }

    @Test
    fun dataPack_klinik_hasNoStarterOrgChart() {
        assertFalse(StarterOrgChartPolicy.isAvailableFor(dataPack("klinik")))
    }

    @Test
    fun packWithoutBlueprintsOrModules_hasNoStarterOrgChart() {
        assertFalse(StarterOrgChartPolicy.isAvailableFor(dataPack("kosong")))
    }

    @Test
    fun garmentLookalike_withOtherCode_hasNoStarterOrgChart() {
        assertFalse(StarterOrgChartPolicy.isAvailableFor(GarmentDomainPack.pack.copy(code = DomainPackCode("garment_lain"))))
    }

    @Test
    fun unavailableMessage_usesPackVocabulary_orNeutralWord() {
        val klinik = dataPack("klinik", mapOf(VocabularyKey.WORKPLACE to "klinik"))
        val message = StarterOrgChartPolicy.unavailableMessage(klinik)
        assertTrue("belum punya contoh bagan organisasi" in message)
        assertTrue("klinik" in message)
        assertTrue(VocabularyKey.WORKPLACE.neutral in StarterOrgChartPolicy.unavailableMessage(dataPack("x")))
    }
}

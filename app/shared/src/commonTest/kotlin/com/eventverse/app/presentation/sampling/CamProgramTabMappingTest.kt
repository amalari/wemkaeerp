package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.StageSectionNames
import com.eventverse.app.presentation.sampling.components.CamPartTab
import com.eventverse.app.presentation.sampling.components.parseCamSections
import com.eventverse.app.presentation.sampling.components.serializeCamSections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CamProgramTabMappingTest {

    @Test
    fun parseCamSections_withEmptyList_shouldReturnDefaultTabs() {
        val (tabs, note) = parseCamSections(emptyList())

        assertEquals(4, tabs.size)
        assertEquals(listOf("Depan", "Belakang", "Lengan", "Kerah"), tabs.map { it.name })
        assertEquals("", note)
        assertTrue(tabs.all { it.program.isEmpty() })
        assertTrue(tabs.all { it.feederInstructions.isEmpty() })
        assertTrue(tabs.all { it.tenselities.isEmpty() })
    }

    @Test
    fun serializeAndParse_roundTrip_shouldPreserveAllTaggableDataAndOrder() {
        val tabs = listOf(
            CamPartTab(
                id = "tab-0-Depan",
                name = "Depan",
                program = "BIAN-D",
                feederInstructions = listOf(
                    "1 RIB STRIPE 1 PLAY (HITAM)",
                    "SPANDEX 70D (PUTIH)"
                ),
                tenselities = listOf(
                    "1 BS POLY: 14",
                    "BS TARIK: 12"
                )
            ),
            CamPartTab(
                id = "tab-1-Belakang",
                name = "Belakang",
                program = "BIAN-B",
                feederInstructions = listOf("1 RIB STRIPE 1 PLAY (HITAM)"),
                tenselities = listOf("1 BS POLY: 14")
            )
        )
        val formulaNote = "PB & LD : 25 X 8\nP BADAN : 2.94 K"

        val sections = serializeCamSections(tabs, formulaNote)

        // Verifikasi section names
        assertEquals(4, sections.size)
        assertTrue(sections.any { it.section == StageSectionNames.PROGRAM })
        assertTrue(sections.any { it.section == StageSectionNames.FEEDER_INSTRUCTIONS })
        assertTrue(sections.any { it.section == StageSectionNames.TENSELITY })
        assertTrue(sections.any { it.section == StageSectionNames.PATTERN_FORMULAS })

        // Verifikasi gerbang CAM_REQUIRED
        val requiredFilled = sections.filter { it.section in StageSectionNames.CAM_REQUIRED }.all { it.hasFilledRow }
        assertTrue(requiredFilled, "Semua section CAM_REQUIRED harus terisi")

        // Parse kembali
        val (restoredTabs, restoredNote) = parseCamSections(sections)

        assertEquals(2, restoredTabs.size)
        assertEquals("Depan", restoredTabs[0].name)
        assertEquals("BIAN-D", restoredTabs[0].program)
        assertEquals(2, restoredTabs[0].feederInstructions.size)
        assertEquals("1 RIB STRIPE 1 PLAY (HITAM)", restoredTabs[0].feederInstructions[0])
        assertEquals("SPANDEX 70D (PUTIH)", restoredTabs[0].feederInstructions[1])
        assertEquals(2, restoredTabs[0].tenselities.size)
        assertEquals("1 BS POLY: 14", restoredTabs[0].tenselities[0])
        assertEquals("BS TARIK: 12", restoredTabs[0].tenselities[1])

        assertEquals("Belakang", restoredTabs[1].name)
        assertEquals("BIAN-B", restoredTabs[1].program)
        assertEquals(1, restoredTabs[1].feederInstructions.size)
        assertEquals(1, restoredTabs[1].tenselities.size)

        assertEquals(formulaNote, restoredNote)
    }

    @Test
    fun camGating_withoutTenselity_shouldNotPass() {
        val tabs = listOf(
            CamPartTab(
                id = "tab-0-Depan",
                name = "Depan",
                program = "BIAN-D",
                feederInstructions = listOf("1 RIB STRIPE 1 PLAY (HITAM)"),
                tenselities = emptyList() // Kosong!
            )
        )
        val sections = serializeCamSections(tabs, "Catatan formula")

        val requiredFilled = sections.filter { it.section in StageSectionNames.CAM_REQUIRED }.all { it.hasFilledRow }
        assertFalse(requiredFilled, "Gerbang CAM tidak boleh lolos jika Tenselity belum diisi")
    }
}

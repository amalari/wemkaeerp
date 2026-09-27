package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.StageInputRow
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
    fun parseCamSections_withEmptyList_shouldReturnEmptyTabs() {
        val (tabs, note) = parseCamSections(emptyList())

        assertTrue(tabs.isEmpty())
        assertEquals("", note)
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
        assertEquals(7, sections.size)
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
    fun serializeAndParse_withGramasiWaktuAndFinishedMeasurements_shouldRoundTrip() {
        val tabs = listOf(
            CamPartTab(
                id = "tab-0-Depan", name = "Depan", program = "BIAN-D",
                feederInstructions = listOf("F1"), gramasi = "117 GR", waktu = "37 MENIT"
            ),
            CamPartTab(id = "tab-1-Lengan", name = "Lengan", program = "BIAN-L", feederInstructions = listOf("F1"))
        )
        val measurements = listOf(StageInputRow("P BADAN", "55 CM"), StageInputRow("", ""))

        val sheet = parseCamSections(serializeCamSections(tabs, "", measurements))

        assertEquals("117 GR", sheet.tabs[0].gramasi)
        assertEquals("37 MENIT", sheet.tabs[0].waktu)
        assertEquals("", sheet.tabs[1].gramasi)
        assertEquals(measurements, sheet.finishedMeasurements, "Baris kosong yang baru ditambah tidak boleh hilang")
    }

    @Test
    fun camGating_withoutFeeder_shouldNotPass() {
        val tabs = listOf(
            CamPartTab(
                id = "tab-0-Depan",
                name = "Depan",
                program = "BIAN-D",
                feederInstructions = emptyList(), // Kosong!
                tenselities = listOf("1 BS POLY: 14")
            )
        )
        val sections = serializeCamSections(tabs, "Catatan formula")

        val requiredFilled = sections.filter { it.section in StageSectionNames.CAM_REQUIRED }.all { it.hasFilledRow }
        assertFalse(requiredFilled, "Gerbang CAM tidak boleh lolos jika Instruksi Panah belum diisi")
    }

    @Test
    fun camPartTab_isComplete_validation() {
        val tabEmpty = CamPartTab(id = "1", name = "Depan")
        assertFalse(tabEmpty.isComplete, "Tab kosong harus belum lengkap")

        val tabProgOnly = CamPartTab(id = "1", name = "Depan", program = "BIAN-D")
        assertFalse(tabProgOnly.isComplete, "Tab hanya dengan kode program harus belum lengkap")

        val tabFeederOnly = CamPartTab(id = "1", name = "Depan", feederInstructions = listOf("Feeder 1"))
        assertFalse(tabFeederOnly.isComplete, "Tab hanya dengan instruksi panah harus belum lengkap")

        val tabComplete = CamPartTab(
            id = "1",
            name = "Depan",
            program = "BIAN-D",
            feederInstructions = listOf("Feeder 1")
        )
        assertTrue(tabComplete.isComplete, "Tab dengan kode program dan instruksi panah harus lengkap")
    }

    @Test
    fun serializeAndParse_partAddedFromRd_shouldSyncToCamSheet() {
        val initialTabs = listOf(
            CamPartTab(id = "tab-0-Depan", name = "Depan", program = "BIAN-D", feederInstructions = listOf("F1")),
            CamPartTab(id = "tab-1-Belakang", name = "Belakang", program = "BIAN-B", feederInstructions = listOf("F1"))
        )
        // Tambah bagian baru dari R&D (hanya nama bagian, gramasi dan waktu)
        val addedFromRd = initialTabs + CamPartTab(
            id = "tab-2-Kerah",
            name = "Kerah",
            gramasi = "25 GR",
            waktu = "8 MENIT"
        )

        val sections = serializeCamSections(addedFromRd, "Catatan")
        val restored = parseCamSections(sections)

        assertEquals(3, restored.tabs.size)
        val kerahTab = restored.tabs.firstOrNull { it.name == "Kerah" }
        assertTrue(kerahTab != null, "Bagian yang ditambah dari R&D harus muncul di lembar CAM")
        assertEquals("25 GR", kerahTab.gramasi)
        assertEquals("8 MENIT", kerahTab.waktu)
        assertEquals("", kerahTab.program)
    }

    @Test
    fun serializeAndParse_partDeletedFromRdOrCam_shouldBeRemovedFromAllSections() {
        val initialTabs = listOf(
            CamPartTab(id = "tab-0-Depan", name = "Depan", program = "BIAN-D", feederInstructions = listOf("F1"), gramasi = "117 GR", waktu = "37 MENIT"),
            CamPartTab(id = "tab-1-Lengan", name = "Lengan", program = "BIAN-L", feederInstructions = listOf("F2"), gramasi = "45 GR", waktu = "15 MENIT")
        )

        // Hapus tab Lengan (baik di CAM maupun R&D)
        val remainingTabs = initialTabs.filter { it.name != "Lengan" }
        val sections = serializeCamSections(remainingTabs, "")
        val restored = parseCamSections(sections)

        assertEquals(1, restored.tabs.size)
        assertEquals("Depan", restored.tabs[0].name)
        assertTrue(restored.tabs.none { it.name == "Lengan" })
        assertFalse(sections.any { it.rows.any { r -> r.label.contains("Lengan") } })
    }

    @Test
    fun parseCamSections_withOnlyPanelWeightsOrMinutes_shouldRestoreTab() {
        val sections = listOf(
            com.eventverse.app.domain.sampling.StageInputSection(
                section = StageSectionNames.PANEL_WEIGHTS,
                rows = listOf(StageInputRow("Manset", "12 GR"))
            ),
            com.eventverse.app.domain.sampling.StageInputSection(
                section = StageSectionNames.PANEL_MINUTES,
                rows = listOf(StageInputRow("Manset", "5 MENIT"))
            )
        )

        val sheet = parseCamSections(sections)
        assertEquals(1, sheet.tabs.size)
        assertEquals("Manset", sheet.tabs[0].name)
        assertEquals("12 GR", sheet.tabs[0].gramasi)
        assertEquals("5 MENIT", sheet.tabs[0].waktu)
    }
}

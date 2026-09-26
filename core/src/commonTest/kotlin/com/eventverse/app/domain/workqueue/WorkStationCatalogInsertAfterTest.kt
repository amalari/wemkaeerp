package com.eventverse.app.domain.workqueue

import com.eventverse.app.domain.pipeline.ModuleArchetype
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkStationCatalogInsertAfterTest {

    private val bordir = WorkStationSpec(
        code = WorkStationCode("BORDIR"),
        displayName = "Bordir Komputer",
        archetype = ModuleArchetype.CUSTOM_EXTENSION,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 1500L,
        standardMinutesPerPiece = 2.0,
        insertAfterCode = WorkStationCatalog.QC_FINAL.code
    )

    @Test
    fun `line with insertAfterCode should place station between anchor and its successor`() {
        val line = WorkStationCatalog.line(listOf(bordir))

        val qcIndex = line.indexOfFirst { it.code == WorkStationCatalog.QC_FINAL.code }
        assertEquals(WorkStationCode("BORDIR"), line[qcIndex + 1].code)
        assertEquals(WorkStationCatalog.PACKAGING.code, line[qcIndex + 2].code)
    }

    @Test
    fun `nextAfter should route through the inserted station`() {
        // QC_FINAL sekarang bersambung ke BORDIR, lalu BORDIR ke PACKAGING
        val afterQc = WorkStationCatalog.nextAfter(
            current = WorkStationCatalog.QC_FINAL.code,
            customStations = listOf(bordir)
        )
        assertEquals(WorkStationCode("BORDIR"), afterQc)

        val afterBordir = WorkStationCatalog.nextAfter(
            current = WorkStationCode("BORDIR"),
            customStations = listOf(bordir)
        )
        assertEquals(WorkStationCatalog.PACKAGING.code, afterBordir)
    }

    @Test
    fun `insertAfterCode should also be respected under activeStations filter`() {
        // SPK cardigan bordir: semua stasiun aktif termasuk BORDIR
        val line = WorkStationCatalog.line(listOf(bordir))
        val activeStations = line.map { it.code }.toSet()

        val afterQc = WorkStationCatalog.nextAfter(
            current = WorkStationCatalog.QC_FINAL.code,
            activeStations = activeStations,
            customStations = listOf(bordir)
        )
        assertEquals(WorkStationCode("BORDIR"), afterQc)
    }

    @Test
    fun `addition without anchor should still append at end for backward compatibility`() {
        val custom = WorkStationSpec(
            code = WorkStationCode("CUSTOM_EXTRA"),
            displayName = "Proses Ekstra Tanpa Jangkar",
            archetype = ModuleArchetype.CUSTOM_EXTENSION,
            inputTrackingUnit = WorkTrackingUnit.BUNDLE,
            outputTrackingUnit = WorkTrackingUnit.BUNDLE
        )

        val line = WorkStationCatalog.line(listOf(custom))

        assertEquals(WorkStationCode("CUSTOM_EXTRA"), line.last().code)
    }

    @Test
    fun `optional templates should not be part of the builtin line`() {
        val defaultLine = WorkStationCatalog.line()

        assertTrue(defaultLine.none { it.code == WorkStationCatalog.BORDIR.code })
        assertTrue(defaultLine.none { it.code == WorkStationCatalog.SABLON.code })
        assertTrue(defaultLine.none { it.code == WorkStationCatalog.LAUNDRY.code })
        assertTrue(WorkStationCatalog.optionalStations().any { it.code == WorkStationCatalog.BORDIR.code })
    }

    @Test
    fun `chained insertions should nest in request order`() {
        val sablon = WorkStationSpec(
            code = WorkStationCode("SABLON"),
            displayName = "Sablon / Print",
            archetype = ModuleArchetype.CUSTOM_EXTENSION,
            inputTrackingUnit = WorkTrackingUnit.BUNDLE,
            outputTrackingUnit = WorkTrackingUnit.BUNDLE,
            insertAfterCode = WorkStationCode("BORDIR")
        )

        val line = WorkStationCatalog.line(listOf(bordir, sablon))

        val qcIndex = line.indexOfFirst { it.code == WorkStationCatalog.QC_FINAL.code }
        assertEquals(WorkStationCode("BORDIR"), line[qcIndex + 1].code)
        assertEquals(WorkStationCode("SABLON"), line[qcIndex + 2].code)
        assertEquals(WorkStationCatalog.PACKAGING.code, line[qcIndex + 3].code)
    }
}
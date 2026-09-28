package com.eventverse.app.domain.process

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TenantProcessCatalogTest {

    private val tenantId = TenantId("ten-demo-001")

    private fun bordir(
        samplingAnchor: SamplingPipelineStage? = SamplingPipelineStage.LINKING_ASSEMBLY,
        stationAnchor: WorkStationCode? = WorkStationCode("QC_FINAL")
    ) = TenantOptionalProcess(
        processId = "proc-bordir",
        tenantId = tenantId,
        code = "BORDIR",
        displayName = "Bordir Komputer",
        archetype = ModuleArchetype.CUSTOM_EXTENSION,
        samplingAnchorAfter = samplingAnchor?.toStageCode(),
        stationAnchorAfter = stationAnchor,
        executionMode = WorkExecutionMode.IN_HOUSE,
        piecerateTariffIdr = 1500L,
        standardMinutesPerPiece = 2.0
    )

    @Test
    fun `add process when code unique should append to catalog`() {
        val catalog = TenantProcessCatalog(tenantId = tenantId)
            .addProcess(bordir())

        assertEquals(1, catalog.processes.size)
        assertEquals("BORDIR", catalog.findByCode("BORDIR")?.code)
    }

    @Test
    fun `add process when code duplicate should throw exception`() {
        val catalog = TenantProcessCatalog(tenantId = tenantId).addProcess(bordir())

        val anotherBordir = bordir().copy(processId = "proc-bordir-lain")
        val exception = assertFailsWith<IllegalArgumentException> {
            catalog.addProcess(anotherBordir)
        }
        assertTrue(exception.message!!.contains("Duplicate process code"))
    }

    @Test
    fun `add process when processId duplicate should throw exception`() {
        val catalog = TenantProcessCatalog(tenantId = tenantId).addProcess(bordir())

        // Kode beda tapi ID sama — tetap ditolak karena ID harus unik
        val sameIdDifferentCode = bordir().copy(code = "SABLON", displayName = "Sablon")
        val exception = assertFailsWith<IllegalArgumentException> {
            catalog.addProcess(sameIdDifferentCode)
        }
        assertTrue(exception.message!!.contains("Duplicate process ID"))
    }

    @Test
    fun `add process from other tenant should throw exception`() {
        val catalog = TenantProcessCatalog(tenantId = tenantId)

        val foreignProcess = bordir().copy(tenantId = TenantId("ten-lain-001"))
        assertFailsWith<IllegalArgumentException> {
            catalog.addProcess(foreignProcess)
        }
    }

    @Test
    fun `process without any anchor should throw exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            bordir(samplingAnchor = null, stationAnchor = null)
        }
        assertTrue(exception.message!!.contains("minimal satu jangkar"))
    }

    @Test
    fun `remove process when exists should drop it from catalog`() {
        val catalog = TenantProcessCatalog(tenantId = tenantId).addProcess(bordir())

        val updated = catalog.removeProcess("proc-bordir")

        assertTrue(updated.isEmpty)
        assertNull(updated.findProcess("proc-bordir"))
    }

    @Test
    fun `remove process when unknown should throw exception`() {
        val catalog = TenantProcessCatalog(tenantId = tenantId)

        assertFailsWith<IllegalArgumentException> {
            catalog.removeProcess("proc-tidak-ada")
        }
    }

    @Test
    fun `reposition should move both anchors`() {
        val catalog = TenantProcessCatalog(tenantId = tenantId).addProcess(bordir())

        val repositioned = catalog.reposition(
            processId = "proc-bordir",
            samplingAnchorAfter = SamplingPipelineStage.CUCI_SOFTENER.toStageCode(),
            stationAnchorAfter = WorkStationCode("PACKAGING")
        )

        val process = repositioned.findProcess("proc-bordir")!!
        assertEquals(SamplingPipelineStage.CUCI_SOFTENER.toStageCode(), process.samplingAnchorAfter)
        assertEquals(WorkStationCode("PACKAGING"), process.stationAnchorAfter)
    }

    @Test
    fun `reposition when both anchors null should throw exception`() {
        val catalog = TenantProcessCatalog(tenantId = tenantId).addProcess(bordir())

        val exception = assertFailsWith<IllegalArgumentException> {
            catalog.reposition("proc-bordir", samplingAnchorAfter = null, stationAnchorAfter = null)
        }
        assertTrue(exception.message!!.contains("minimal satu jangkar"))
    }

    @Test
    fun `catalog constructor when duplicate codes should throw exception`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            TenantProcessCatalog(
                tenantId = tenantId,
                processes = listOf(bordir(), bordir().copy(processId = "proc-bordir-2"))
            )
        }
        assertTrue(exception.message!!.contains("Duplicate process codes"))
    }

    @Test
    fun `to work station spec should carry insert after anchor and tariff`() {
        val spec = bordir().toWorkStationSpec()

        assertEquals(WorkStationCode("BORDIR"), spec.code)
        assertEquals(WorkStationCode("QC_FINAL"), spec.insertAfterCode)
        assertEquals(1500L, spec.piecerateTariffIdr)
        assertEquals(ModuleArchetype.CUSTOM_EXTENSION, spec.archetype)
    }
}
package com.eventverse.app.domain.process

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.process.usecases.ResolveActiveProcessesQuery
import com.eventverse.app.domain.process.usecases.ResolveActiveProcessesUseCase
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkStationCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolveActiveProcessesTest {

    private val tenantId = TenantId("ten-demo-001")

    private class FakeCatalogRepository : TenantProcessCatalogRepository {
        var stored: TenantProcessCatalog? = null

        override suspend fun findByTenantId(tenantId: TenantId): TenantProcessCatalog? =
            stored?.takeIf { it.tenantId == tenantId }

        override suspend fun save(catalog: TenantProcessCatalog): Result<TenantProcessCatalog> =
            Result.success(catalog.also { stored = it })

        override suspend fun deleteProcess(tenantId: TenantId, processId: String): Result<Unit> =
            runCatching { stored = stored?.removeProcess(processId) }
    }

    private fun bordir(
        samplingAnchor: SamplingPipelineStage? = SamplingPipelineStage.LINKING_ASSEMBLY,
        stationAnchor: WorkStationCode? = WorkStationCode("QC_FINAL")
    ) = TenantOptionalProcess(
        processId = "proc-bordir",
        tenantId = tenantId,
        code = "BORDIR",
        displayName = "Bordir Komputer",
        archetype = ModuleArchetype.CUSTOM_EXTENSION,
        samplingAnchorAfter = samplingAnchor,
        stationAnchorAfter = stationAnchor,
        piecerateTariffIdr = 1500L
    )

    private fun sablon() = TenantOptionalProcess(
        processId = "proc-sablon",
        tenantId = tenantId,
        code = "SABLON",
        displayName = "Sablon / Print",
        archetype = ModuleArchetype.CUSTOM_EXTENSION,
        samplingAnchorAfter = SamplingPipelineStage.FINISHING_QC,
        stationAnchorAfter = null,
        piecerateTariffIdr = 1200L
    )

    @Test
    fun `resolve when no active codes filter should map all processes to routing`() = runTest {
        val repo = FakeCatalogRepository().apply {
            stored = TenantProcessCatalog(tenantId = tenantId)
                .addProcess(bordir())
                .addProcess(sablon())
        }
        val useCase = ResolveActiveProcessesUseCase(repo)

        val routing = useCase(ResolveActiveProcessesQuery(tenantId = tenantId)).getOrThrow()

        // Hanya proses dengan jangkar stasiun yang masuk line workqueue
        assertEquals(1, routing.customStations.size)
        assertEquals(WorkStationCode("BORDIR"), routing.customStations.first().code)
        assertEquals(WorkStationCode("QC_FINAL"), routing.customStations.first().insertAfterCode)

        // Kedua proses punya jangkar sampling, terurut sesuai urutan tahap jangkar
        assertEquals(2, routing.samplingSteps.size)
        assertEquals("BORDIR", routing.samplingSteps.first().code)
        assertEquals("SABLON", routing.samplingSteps.last().code)
    }

    @Test
    fun `resolve when active codes filter should exclude inactive processes`() = runTest {
        val repo = FakeCatalogRepository().apply {
            stored = TenantProcessCatalog(tenantId = tenantId)
                .addProcess(bordir())
                .addProcess(sablon())
        }
        val useCase = ResolveActiveProcessesUseCase(repo)

        // SPK kaos polos: hanya sablon aktif, bordir dilewati
        val routing = useCase(
            ResolveActiveProcessesQuery(
                tenantId = tenantId,
                activeProcessCodes = setOf("SABLON")
            )
        ).getOrThrow()

        assertTrue(routing.customStations.isEmpty())
        assertEquals(1, routing.samplingSteps.size)
        assertEquals("SABLON", routing.samplingSteps.first().code)
    }

    @Test
    fun `resolve when catalog empty should return empty routing`() = runTest {
        val useCase = ResolveActiveProcessesUseCase(FakeCatalogRepository())

        val routing = useCase(ResolveActiveProcessesQuery(tenantId = tenantId)).getOrThrow()

        assertTrue(routing.customStations.isEmpty())
        assertTrue(routing.samplingSteps.isEmpty())
    }
}
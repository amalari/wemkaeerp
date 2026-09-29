package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.usecases.GetTenantPipelineUseCase
import com.eventverse.app.domain.pipeline.usecases.ResetTenantPipelineUseCase
import com.eventverse.app.domain.pipeline.usecases.SaveTenantPipelineUseCase
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FakeTenantPipelineRepository : TenantPipelineRepository {
    private val pipelines = mutableMapOf<TenantId, CustomTenantPipeline>()

    override suspend fun findByTenantId(tenantId: TenantId): CustomTenantPipeline? = pipelines[tenantId]

    override suspend fun save(pipeline: CustomTenantPipeline): Result<CustomTenantPipeline> {
        pipelines[pipeline.tenantId] = pipeline
        return Result.success(pipeline)
    }

    override suspend fun deleteByTenantId(tenantId: TenantId): Result<Unit> {
        pipelines.remove(tenantId)
        return Result.success(Unit)
    }
}

class TenantPipelineUseCaseTest {

    private lateinit var repository: FakeTenantPipelineRepository
    private lateinit var getUseCase: GetTenantPipelineUseCase
    private lateinit var saveUseCase: SaveTenantPipelineUseCase
    private lateinit var resetUseCase: ResetTenantPipelineUseCase

    private val testTenantId = TenantId("ten-test-pipeline")

    @BeforeTest
    fun setUp() {
        repository = FakeTenantPipelineRepository()
        getUseCase = GetTenantPipelineUseCase(repository)
        saveUseCase = SaveTenantPipelineUseCase(repository)
        resetUseCase = ResetTenantPipelineUseCase(repository)
    }

    @Test
    fun getPipeline_whenNotExisting_shouldSynthesizeAndPersistDefaultPreset() = runTest {
        val result = getUseCase(testTenantId, GarmentBlueprints.CMT_MAKLOON)

        assertTrue(result.isSuccess)
        val pipeline = result.getOrThrow()
        assertEquals(testTenantId, pipeline.tenantId)
        assertEquals(GarmentBlueprints.CMT_MAKLOON, pipeline.baseStarterPreset)
        assertTrue(pipeline.nodes.isNotEmpty())
        assertTrue(pipeline.edges.isNotEmpty())

        // Verify repository now stores the pipeline
        val stored = repository.findByTenantId(testTenantId)
        assertNotNull(stored)
        assertEquals(pipeline.pipelineName, stored.pipelineName)
    }

    @Test
    fun savePipeline_withValidNodesAndEdges_shouldSucceed() = runTest {
        val initial = getUseCase(testTenantId, GarmentBlueprints.FOB_FULL_PACKAGE).getOrThrow()

        // Modify node display name
        val updatedNodes = initial.nodes.mapIndexed { idx, node ->
            if (idx == 0) node.copy(customDisplayName = "Meja Negosiasi Sales Custom") else node
        }
        val modifiedPipeline = initial.copy(
            pipelineName = "Alur Kustomisasi PT Megah",
            nodes = updatedNodes
        )

        val saveResult = saveUseCase(modifiedPipeline)
        assertTrue(saveResult.isSuccess)

        val fetched = getUseCase(testTenantId).getOrThrow()
        assertEquals("Alur Kustomisasi PT Megah", fetched.pipelineName)
        assertEquals("Meja Negosiasi Sales Custom", fetched.nodes.first().customDisplayName)
    }

    @Test
    fun savePipeline_withDuplicateNodeIds_shouldFail() = runTest {
        val initial = getUseCase(testTenantId, GarmentBlueprints.FOB_FULL_PACKAGE).getOrThrow()

        val invalidNodes = initial.nodes + initial.nodes.first() // duplicate node ID
        val invalidPipeline = initial.copy(nodes = invalidNodes)

        val result = saveUseCase(invalidPipeline)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Duplicate node IDs") == true)
    }

    @Test
    fun getPipeline_whenStoredRowHasNoNodes_shouldProvisionFromPreset() = runTest {
        // Regression: a row seeded without a topology (as the demo tenants were) is an
        // unprovisioned tenant, not a valid empty workflow. Returning it as-is left the
        // factory canvas with zero modules.
        repository.save(
            CustomTenantPipeline(
                tenantId = testTenantId,
                pipelineName = "Alur Operasional PT WeMade Garmen Ekspor",
                baseStarterPreset = GarmentBlueprints.CMT_MAKLOON,
                nodes = emptyList(),
                edges = emptyList()
            )
        )

        val pipeline = getUseCase(testTenantId, GarmentBlueprints.FOB_FULL_PACKAGE).getOrThrow()

        assertTrue(pipeline.nodes.isNotEmpty(), "Pipeline kosong harus di-provision ulang")
        assertTrue(pipeline.edges.isNotEmpty())
        // The stored row's own preset and curated name win over the caller's fallback.
        assertEquals(GarmentBlueprints.CMT_MAKLOON, pipeline.baseStarterPreset)
        assertEquals("Alur Operasional PT WeMade Garmen Ekspor", pipeline.pipelineName)

        // And the repaired topology is persisted, so the next read is stable.
        val stored = repository.findByTenantId(testTenantId)
        assertNotNull(stored)
        assertEquals(pipeline.nodes.size, stored.nodes.size)
    }

    @Test
    fun getPipeline_whenAlreadyProvisioned_shouldNotOverwriteCustomisations() = runTest {
        val initial = getUseCase(testTenantId, GarmentBlueprints.FOB_FULL_PACKAGE).getOrThrow()
        val renamedNodeId = initial.nodes.first { it.moduleId == "inventory" }.nodeId
        saveUseCase(initial.renameNode(renamedNodeId, "Gudang Kain Roll Impor")).getOrThrow()

        val fetched = getUseCase(testTenantId, GarmentBlueprints.CMT_MAKLOON).getOrThrow()

        assertEquals(
            "Gudang Kain Roll Impor",
            fetched.nodes.first { it.nodeId == renamedNodeId }.customDisplayName
        )
        assertEquals(GarmentBlueprints.FOB_FULL_PACKAGE, fetched.baseStarterPreset)
    }

    @Test
    fun savePipeline_withAllModulesBypassed_shouldFail() = runTest {
        val initial = getUseCase(testTenantId, GarmentBlueprints.FOB_FULL_PACKAGE).getOrThrow()
        val allBypassed = initial.nodes.fold(initial) { acc, node ->
            acc.setNodeBypassed(node.nodeId, true)
        }

        val result = saveUseCase(allBypassed)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("at least one active") == true,
            "Pesan: ${result.exceptionOrNull()?.message}"
        )
    }

    @Test
    fun resetPipeline_shouldRestoreSpecifiedPreset() = runTest {
        // Initial setup as FOB
        getUseCase(testTenantId, GarmentBlueprints.FOB_FULL_PACKAGE)

        // Reset to Brand D2C
        val resetResult = resetUseCase(testTenantId, GarmentBlueprints.BRAND_D2C)
        assertTrue(resetResult.isSuccess)

        val active = getUseCase(testTenantId).getOrThrow()
        assertEquals(GarmentBlueprints.BRAND_D2C, active.baseStarterPreset)
    }
}

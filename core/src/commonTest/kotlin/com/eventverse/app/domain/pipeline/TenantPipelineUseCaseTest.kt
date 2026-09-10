package com.eventverse.app.domain.pipeline

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
        val result = getUseCase(testTenantId, GarmentBusinessPreset.CMT_MAKLOON)

        assertTrue(result.isSuccess)
        val pipeline = result.getOrThrow()
        assertEquals(testTenantId, pipeline.tenantId)
        assertEquals(GarmentBusinessPreset.CMT_MAKLOON, pipeline.baseStarterPreset)
        assertTrue(pipeline.nodes.isNotEmpty())
        assertTrue(pipeline.edges.isNotEmpty())

        // Verify repository now stores the pipeline
        val stored = repository.findByTenantId(testTenantId)
        assertNotNull(stored)
        assertEquals(pipeline.pipelineName, stored.pipelineName)
    }

    @Test
    fun savePipeline_withValidNodesAndEdges_shouldSucceed() = runTest {
        val initial = getUseCase(testTenantId, GarmentBusinessPreset.FOB_FULL_PACKAGE).getOrThrow()

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
        val initial = getUseCase(testTenantId, GarmentBusinessPreset.FOB_FULL_PACKAGE).getOrThrow()

        val invalidNodes = initial.nodes + initial.nodes.first() // duplicate node ID
        val invalidPipeline = initial.copy(nodes = invalidNodes)

        val result = saveUseCase(invalidPipeline)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Duplicate node IDs") == true)
    }

    @Test
    fun resetPipeline_shouldRestoreSpecifiedPreset() = runTest {
        // Initial setup as FOB
        getUseCase(testTenantId, GarmentBusinessPreset.FOB_FULL_PACKAGE)

        // Reset to Brand D2C
        val resetResult = resetUseCase(testTenantId, GarmentBusinessPreset.BRAND_D2C)
        assertTrue(resetResult.isSuccess)

        val active = getUseCase(testTenantId).getOrThrow()
        assertEquals(GarmentBusinessPreset.BRAND_D2C, active.baseStarterPreset)
    }
}

package com.eventverse.app.domain.stageflow

import com.eventverse.app.domain.pipeline.code

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.process.TenantProcessCatalog
import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.stageflow.usecases.AddStageCommand
import com.eventverse.app.domain.stageflow.usecases.AddStageUseCase
import com.eventverse.app.domain.stageflow.usecases.MoveStageUseCase
import com.eventverse.app.domain.stageflow.usecases.RemoveStageUseCase
import com.eventverse.app.domain.stageflow.usecases.RenameStageUseCase
import com.eventverse.app.domain.stageflow.usecases.ResetStageFlowUseCase
import com.eventverse.app.domain.stageflow.usecases.StageHasProcessesException
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Sunting kerangka tahap tenant (TRD-FLOW-001 Tahap 3b). */
class EditTenantStageFlowTest {

    private val tenant = TenantId("ten-edit")
    private val embroidery = IndustryTemplateCode.EMBROIDERY
    private fun code(v: String) = StageCode(v)
    private fun codes(flow: TenantStageFlow) = flow.stages.map { it.code.value }

    private class FakeCatalog(var processes: List<TenantOptionalProcess> = emptyList()) : TenantProcessCatalogRepository {
        override suspend fun findByTenantId(tenantId: TenantId) = TenantProcessCatalog(tenantId, processes)
        override suspend fun save(catalog: TenantProcessCatalog) = Result.success(catalog)
        override suspend fun deleteProcess(tenantId: TenantId, processId: String) = Result.success(Unit)
    }

    private fun process(anchor: String) = TenantOptionalProcess(
        processId = "proc-applique", tenantId = tenant, code = "APPLIQUE", displayName = "Aplikasi Kain",
        archetype = GarmentSlots.CUSTOM_EXTENSION, samplingAnchorAfter = code(anchor)
    )

    // ── Aturan agregat ───────────────────────────────────────────────────────────────────────

    private val flow = IndustryStageTemplates.instantiate(tenant, embroidery)

    @Test
    fun insertAfterEntryAnchor_shouldLandAfterLastEntryAnchor() {
        val stage = StageDefinition(code("APPLIQUE"), "Aplikasi", StageKind.WORK, GarmentSlots.CUSTOM_EXTENSION)
        val edited = flow.insertAfter(code("NEW_INTAKE"), stage)

        assertEquals(listOf("NEW_INTAKE", "FLOW_REVIEW", "APPLIQUE", "DIGITIZING"), codes(edited).take(4))
    }

    @Test
    fun editsOnAnchorsOrAfterExit_shouldBeRejected() {
        val stage = StageDefinition(code("APPLIQUE"), "Aplikasi", StageKind.WORK, GarmentSlots.CUSTOM_EXTENSION)
        assertFailsWith<IllegalArgumentException> { flow.insertAfter(code("STORAGE_HOLDING"), stage) }
        assertFailsWith<IllegalArgumentException> { flow.remove(code("FLOW_REVIEW")) }
        assertFailsWith<IllegalArgumentException> { flow.move(code("ACC_APPROVED"), code("HOOPING")) }
        assertFailsWith<IllegalArgumentException> { flow.insertAfter(code("HOOPING"), flow.stages[3]) }
    }

    @Test
    fun removingOnlyQcOrPackingStage_shouldBeRejected() {
        assertFailsWith<IllegalArgumentException> { flow.remove(code("QC_FINISHING")) }
        assertFailsWith<IllegalArgumentException> { flow.remove(code("PENGEMASAN")) }
        assertEquals(codes(flow) - "THREAD_TRIMMING", codes(flow.remove(code("THREAD_TRIMMING"))))
    }

    @Test
    fun move_shouldReorderWorkStage() {
        val moved = flow.move(code("THREAD_TRIMMING"), code("HOOPING"))
        assertEquals(listOf("DIGITIZING", "HOOPING", "THREAD_TRIMMING", "MACHINE_EMBROIDERY"), codes(moved).subList(2, 6))
    }

    // ── Use case ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun add_shouldProvisionThenInsertAsOptionalDeskWithInheritedUrgency() = runTest {
        val repo = FakeTenantStageFlowRepository()
        val added = AddStageUseCase(repo)(
            AddStageCommand(tenant, code("APPLIQUE"), "Aplikasi Kain", "Aplikasi", GarmentSlots.CUSTOM_EXTENSION,
                afterCode = code("MACHINE_EMBROIDERY"), fallbackTemplate = embroidery)
        ).getOrThrow()

        val stage = assertNotNull(added.find(code("APPLIQUE")))
        assertEquals(StageOrigin.OPTIONAL, stage.origin)
        assertTrue(stage.has(StageTrait.OPERATOR_DESK))
        assertEquals(added.find(code("MACHINE_EMBROIDERY"))?.remainingWorkFactor, stage.remainingWorkFactor)
        assertEquals(added, repo.findByTenantId(tenant))
    }

    @Test
    fun remove_whenCatalogProcessAnchored_shouldRefuseAndNameTheProcess() = runTest {
        val repo = FakeTenantStageFlowRepository().also { it.save(flow) }
        val result = RemoveStageUseCase(repo, FakeCatalog(listOf(process("THREAD_TRIMMING"))))(tenant, code("THREAD_TRIMMING"), embroidery)

        val error = assertIs<StageHasProcessesException>(result.exceptionOrNull())
        assertEquals(listOf("Aplikasi Kain"), error.processNames)
        assertNotNull(repo.findByTenantId(tenant)?.find(code("THREAD_TRIMMING")), "kerangka tidak berubah")
    }

    @Test
    fun renameAndMove_shouldPersist() = runTest {
        val repo = FakeTenantStageFlowRepository().also { it.save(flow) }
        RenameStageUseCase(repo)(tenant, code("HOOPING"), "Pasang Ram", "Ram", embroidery).getOrThrow()
        val moved = MoveStageUseCase(repo)(tenant, code("THREAD_TRIMMING"), code("DIGITIZING"), embroidery).getOrThrow()

        assertEquals("Pasang Ram", moved.find(code("HOOPING"))?.displayName)
        assertEquals("Ram", moved.find(code("HOOPING"))?.shortLabel)
        assertEquals("THREAD_TRIMMING", codes(moved)[3])
    }

    @Test
    fun reset_whenProcessWouldBeOrphaned_shouldRefuse() = runTest {
        val repo = FakeTenantStageFlowRepository().also { it.save(flow) }
        val catalog = FakeCatalog(listOf(process("HOOPING")))

        assertIs<StageHasProcessesException>(ResetStageFlowUseCase(repo, catalog)(tenant, IndustryTemplateCode.KNIT_SWEATER).exceptionOrNull())
        catalog.processes = listOf(process("QC_FINISHING"))
        assertEquals(IndustryTemplateCode.KNIT_SWEATER, ResetStageFlowUseCase(repo, catalog)(tenant, IndustryTemplateCode.KNIT_SWEATER).getOrThrow().template)
    }
}

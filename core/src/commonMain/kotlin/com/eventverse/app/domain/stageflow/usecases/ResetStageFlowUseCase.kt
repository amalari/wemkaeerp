package com.eventverse.app.domain.stageflow.usecases

import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.stageflow.IndustryStageTemplates
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.TenantStageFlow
import com.eventverse.app.domain.stageflow.TenantStageFlowRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Mengganti kerangka tenant dengan salinan segar sebuah template. Proses opsional yang
 * berjangkar pada tahap yang tidak ada di template baru ditolak, dengan alasan yang sama
 * seperti [RemoveStageUseCase].
 */
class ResetStageFlowUseCase(
    private val repository: TenantStageFlowRepository,
    private val processCatalog: TenantProcessCatalogRepository
) {
    suspend operator fun invoke(tenantId: TenantId, template: IndustryTemplateCode): Result<TenantStageFlow> = runCatching {
        val fresh = IndustryStageTemplates.instantiate(tenantId, template)
        val orphaned = processCatalog.findByTenantId(tenantId)?.processes.orEmpty()
            .filter { process -> process.samplingAnchorAfter?.let { fresh.find(it) == null } == true }
        orphaned.firstOrNull()?.samplingAnchorAfter?.let { throw StageHasProcessesException(it, orphaned.map { p -> p.displayName }) }
        repository.save(fresh).getOrThrow()
    }
}

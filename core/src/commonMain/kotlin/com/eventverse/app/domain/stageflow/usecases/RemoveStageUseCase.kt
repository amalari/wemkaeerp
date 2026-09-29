package com.eventverse.app.domain.stageflow.usecases

import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.TenantStageFlow
import com.eventverse.app.domain.stageflow.TenantStageFlowRepository
import com.eventverse.app.domain.tenant.TenantId

/** Tahap masih menjadi jangkar proses opsional katalog — dihapus berarti proses itu hilang diam-diam. */
class StageHasProcessesException(val stage: StageCode, val processNames: List<String>) :
    IllegalStateException("Tahap ${stage.value} masih menjadi jangkar proses: ${processNames.joinToString()}. Pindahkan dulu prosesnya.")

class RemoveStageUseCase(
    private val repository: TenantStageFlowRepository,
    private val processCatalog: TenantProcessCatalogRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        code: StageCode,
        fallbackTemplate: IndustryTemplateCode = IndustryTemplateCode.KNIT_SWEATER
    ): Result<TenantStageFlow> = runCatching {
        val anchored = processCatalog.findByTenantId(tenantId)?.processes.orEmpty().filter { it.samplingAnchorAfter == code }
        if (anchored.isNotEmpty()) throw StageHasProcessesException(code, anchored.map { it.displayName })
        repository.edit(tenantId, fallbackTemplate) { it.remove(code) }
    }
}

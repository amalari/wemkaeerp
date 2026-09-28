package com.eventverse.app.domain.stageflow.usecases

import com.eventverse.app.domain.stageflow.IndustryStageTemplates
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.TenantStageFlow
import com.eventverse.app.domain.stageflow.TenantStageFlowRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Membaca kerangka tahap tenant; tenant yang belum punya di-provision dari [fallbackTemplate]
 * lalu disimpan, sehingga pembacaan berikutnya stabil (pola sama dengan `GetTenantPipelineUseCase`).
 *
 * Default `KNIT_SWEATER` karena seluruh tenant yang ada hari ini berjalan di atas kerangka rajut.
 */
class GetTenantStageFlowUseCase(
    private val repository: TenantStageFlowRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        fallbackTemplate: IndustryTemplateCode = IndustryTemplateCode.KNIT_SWEATER
    ): Result<TenantStageFlow> = runCatching {
        repository.findByTenantId(tenantId)
            ?: repository.save(IndustryStageTemplates.instantiate(tenantId, fallbackTemplate)).getOrThrow()
    }
}

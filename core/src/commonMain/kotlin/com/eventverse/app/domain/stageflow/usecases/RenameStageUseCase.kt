package com.eventverse.app.domain.stageflow.usecases

import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.TenantStageFlow
import com.eventverse.app.domain.stageflow.TenantStageFlowRepository
import com.eventverse.app.domain.tenant.TenantId

class RenameStageUseCase(private val repository: TenantStageFlowRepository) {
    suspend operator fun invoke(
        tenantId: TenantId,
        code: StageCode,
        displayName: String,
        shortLabel: String? = null,
        fallbackTemplate: IndustryTemplateCode = IndustryTemplateCode.KNIT_SWEATER
    ): Result<TenantStageFlow> = runCatching {
        repository.edit(tenantId, fallbackTemplate) { it.rename(code, displayName, shortLabel) }
    }
}

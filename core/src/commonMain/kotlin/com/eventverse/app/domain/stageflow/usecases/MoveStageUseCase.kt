package com.eventverse.app.domain.stageflow.usecases

import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.TenantStageFlow
import com.eventverse.app.domain.stageflow.TenantStageFlowRepository
import com.eventverse.app.domain.tenant.TenantId

class MoveStageUseCase(private val repository: TenantStageFlowRepository) {
    suspend operator fun invoke(
        tenantId: TenantId,
        code: StageCode,
        afterCode: StageCode,
        fallbackTemplate: IndustryTemplateCode = IndustryTemplateCode.KNIT_SWEATER
    ): Result<TenantStageFlow> = runCatching {
        repository.edit(tenantId, fallbackTemplate) { it.move(code, afterCode) }
    }
}

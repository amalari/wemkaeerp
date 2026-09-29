package com.eventverse.app.domain.stageflow.usecases

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.stageflow.StageOrigin
import com.eventverse.app.domain.stageflow.StageTrait
import com.eventverse.app.domain.stageflow.TenantStageFlow
import com.eventverse.app.domain.stageflow.TenantStageFlowRepository
import com.eventverse.app.domain.tenant.TenantId

data class AddStageCommand(
    val tenantId: TenantId,
    val code: StageCode,
    val displayName: String,
    val shortLabel: String,
    val archetype: ModuleArchetype,
    val afterCode: StageCode,
    val isOperatorDesk: Boolean = true,
    val fallbackTemplate: IndustryTemplateCode = IndustryTemplateCode.KNIT_SWEATER
)

class AddStageUseCase(private val repository: TenantStageFlowRepository) {
    suspend operator fun invoke(command: AddStageCommand): Result<TenantStageFlow> = runCatching {
        repository.edit(command.tenantId, command.fallbackTemplate) { flow ->
            // Sisa kerja mewarisi tahap sebelumnya: urgensi tetap monoton tanpa tenant menghitung angka.
            val previous = requireNotNull(flow.find(command.afterCode)) { "Tahap jangkar ${command.afterCode.value} tidak ada di kerangka" }
            flow.insertAfter(
                command.afterCode,
                StageDefinition(
                    code = command.code,
                    displayName = command.displayName.trim(),
                    kind = StageKind.WORK,
                    archetype = command.archetype,
                    traits = if (command.isOperatorDesk) setOf(StageTrait.OPERATOR_DESK) else emptySet(),
                    origin = StageOrigin.OPTIONAL,
                    shortLabel = command.shortLabel.trim().ifBlank { command.displayName.trim() },
                    remainingWorkFactor = previous.remainingWorkFactor
                )
            )
        }
    }
}

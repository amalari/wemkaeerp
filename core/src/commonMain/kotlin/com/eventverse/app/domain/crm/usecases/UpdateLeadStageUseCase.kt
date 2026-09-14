package com.eventverse.app.domain.crm.usecases

import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/** Moves a lead through its pipeline, refusing an illegal transition (see [LeadStage.canTransitionTo]). */
class UpdateLeadStageUseCase(
    private val leadRepository: CrmLeadRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        leadId: LeadId,
        newStage: LeadStage
    ): Result<CrmLead> = runCatching {
        val existing = requireNotNull(leadRepository.findById(tenantId, leadId)) {
            "Lead not found: ${leadId.value}"
        }
        val transitioned = existing.transitionTo(newStage, Clock.System.now()).getOrThrow()
        leadRepository.save(transitioned).getOrThrow()
    }
}

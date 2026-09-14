package com.eventverse.app.domain.crm.usecases

import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/** Soft-archives a lead. Never a hard delete — mirrors the Odoo-style pattern used everywhere else. */
class ArchiveLeadUseCase(
    private val leadRepository: CrmLeadRepository
) {
    suspend operator fun invoke(tenantId: TenantId, leadId: LeadId): Result<CrmLead> = runCatching {
        val existing = requireNotNull(leadRepository.findById(tenantId, leadId)) {
            "Lead not found: ${leadId.value}"
        }
        leadRepository.save(existing.archive(Clock.System.now())).getOrThrow()
    }
}

package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.pipeline.TenantEntitlementGrants
import com.eventverse.app.domain.pipeline.TenantEntitlementRepository
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantId

/**
 * Provisions which modules a specific tenant may run — the billing/platform-side decision.
 *
 * Refuses grants that would leave the tenant's existing pipeline invalid: revoking a module
 * a factory is actively running would otherwise lock it out of editing its own flow, with
 * the failure only surfacing on that tenant's next save.
 */
class SetTenantEntitlementUseCase(
    private val entitlementRepository: TenantEntitlementRepository,
    private val pipelineRepository: TenantPipelineRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        tier: SubscriptionTier,
        grants: TenantEntitlementGrants
    ): Result<TenantModuleEntitlement> = runCatching {
        val resolved = TenantModuleEntitlement.resolve(tier, grants)

        pipelineRepository.findByTenantId(tenantId)?.let { pipeline ->
            if (!pipeline.isEmpty) {
                val violations = resolved.validate(pipeline)
                require(violations.isEmpty()) {
                    "Entitlement ini membuat alur tenant yang sedang berjalan menjadi tidak valid: " +
                        violations.joinToString(" ") +
                        " Nonaktifkan modul terkait pada alur tenant lebih dulu."
                }
            }
        }

        entitlementRepository.save(tenantId, grants).getOrThrow()
        resolved
    }
}

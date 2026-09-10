package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.pipeline.TenantEntitlementRepository
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantId

/**
 * Resolves what a tenant is actually allowed to run: plan defaults from its subscription
 * tier, combined with any grants provisioned specifically for it.
 *
 * Every write path that validates a pipeline must resolve the entitlement through here.
 * Building one from the tier alone silently drops custom plugin grants, which would reject
 * edits from any tenant that runs one.
 */
class GetTenantEntitlementUseCase(
    private val entitlementRepository: TenantEntitlementRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        tier: SubscriptionTier
    ): Result<TenantModuleEntitlement> = runCatching {
        TenantModuleEntitlement.resolve(tier, entitlementRepository.findByTenantId(tenantId))
    }
}

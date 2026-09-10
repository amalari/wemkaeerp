package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.pipeline.TenantEntitlementRepository
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantRepository

/**
 * Changes a tenant's subscription plan — a billing/platform decision.
 *
 * Refuses a downgrade that would leave the tenant's currently running pipeline invalid,
 * mirroring the protection [SetTenantEntitlementUseCase] gives module grants: moving a
 * factory with ten active modules from ENTERPRISE to PRO (nine-module limit) would
 * otherwise lock it out of editing its own flow, with the failure only surfacing on that
 * tenant's next save.
 */
class UpdateTenantTierUseCase(
    private val tenantRepository: TenantRepository,
    private val pipelineRepository: TenantPipelineRepository,
    private val entitlementRepository: TenantEntitlementRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        newTier: SubscriptionTier
    ): Result<Tenant> = runCatching {
        val tenant = tenantRepository.findById(tenantId)
            ?: error("Tenant tidak ditemukan: ${tenantId.value}")

        val grants = entitlementRepository.findByTenantId(tenantId)
        val resolvedEntitlement = TenantModuleEntitlement.resolve(newTier, grants)

        pipelineRepository.findByTenantId(tenantId)?.let { pipeline ->
            if (!pipeline.isEmpty) {
                val violations = resolvedEntitlement.validate(pipeline)
                require(violations.isEmpty()) {
                    "Downgrade paket akan membuat alur tenant yang sedang berjalan menjadi tidak valid: " +
                        violations.joinToString(" ") +
                        " Nonaktifkan modul terkait pada alur tenant lebih dulu."
                }
            }
        }

        tenantRepository.save(tenant.upgradeTier(newTier)).getOrThrow()
    }
}

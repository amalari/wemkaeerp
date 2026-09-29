package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.pack.DomainPack
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
        grants: TenantEntitlementGrants,
        pack: DomainPack,
        autoBypassPipelineModules: Boolean = false
    ): Result<TenantModuleEntitlement> = runCatching {
        val resolved = TenantModuleEntitlement.resolve(tier, grants, pack)

        pipelineRepository.findByTenantId(tenantId)?.let { pipeline ->
            if (!pipeline.isEmpty) {
                var currentPipeline = pipeline
                var pipelineModified = false

                if (autoBypassPipelineModules) {
                    val unpermittedActiveNodes = pipeline.activeNodes.filterNot { resolved.permits(it) }
                    if (unpermittedActiveNodes.isNotEmpty()) {
                        for (node in unpermittedActiveNodes) {
                            currentPipeline = currentPipeline.setNodeBypassed(node.nodeId, isBypassed = true)
                        }
                        pipelineModified = true
                    }
                }

                // Saat modul disambungkan kembali (re-granted), aktifkan kembali node yang sebelumnya di-bypass
                // selama penambahan modul aktif ini tetap memenuhi kuota paket tenant.
                val bypassedNodesToRestore = currentPipeline.bypassedNodes.filter { resolved.permits(it) }
                for (node in bypassedNodesToRestore) {
                    val candidate = currentPipeline.setNodeBypassed(node.nodeId, isBypassed = false)
                    if (resolved.validate(candidate).isEmpty()) {
                        currentPipeline = candidate
                        pipelineModified = true
                    }
                }

                if (pipelineModified) {
                    pipelineRepository.save(currentPipeline).getOrThrow()
                }

                val violations = resolved.validate(currentPipeline)
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

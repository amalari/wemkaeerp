package com.eventverse.app.domain.tenant

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack

import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.stageflow.IndustryTemplateCode

/**
 * Value context representing the active tenant within the current execution scope.
 */
data class TenantContext(
    val tenantId: TenantId,
    val slug: TenantSlug,
    val tier: SubscriptionTier,
    val isAccessible: Boolean,
    /**
     * The tenant's garment business model. Carried here so request handlers provision a new
     * pipeline from the factory's own model instead of falling back to a global default.
     */
    val businessPreset: Blueprint = GarmentBlueprints.DEFAULT,
    /** Template industri — kerangka yang di-provision untuk tenant tanpa kerangka (TRD-FLOW-001). */
    val industryTemplate: IndustryTemplateCode = IndustryTemplateCode.KNIT_SWEATER,
    /** Vertikal tenant (B7). */
    val domainPack: DomainPackCode = GarmentDomainPack.CODE
) {
    /**
     * Pack tenant. Kode yang tidak dikenal proses ini = konfigurasi rusak → gagal keras, **tidak** jatuh ke garment
     * (Kontrak 4). Plugin tenant server menolak request sebelum sampai sini (`TenantPackResolver`).
     */
    val pack: DomainPack get() = requireNotNull(DomainPackRegistry.find(domainPack)) { "Pack ${domainPack.value} tenant ${slug.value} tidak dikenal" }

    /** Which operational modules this tenant's subscription plan grants. */
    val moduleEntitlement: TenantModuleEntitlement get() = TenantModuleEntitlement.forTier(tier, pack)

    companion object {
        fun fromTenant(tenant: Tenant): TenantContext = TenantContext(
            tenantId = tenant.id,
            slug = tenant.slug,
            tier = tenant.tier,
            isAccessible = tenant.isAccessible,
            businessPreset = tenant.businessPreset,
            industryTemplate = tenant.industryTemplate,
            domainPack = tenant.domainPack
        )
    }
}

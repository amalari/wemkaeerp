package com.eventverse.app.domain.tenant

import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.GarmentDomainPack

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.stageflow.IndustryTemplateCode

/**
 * Core domain entity representing a tenant (Factory / Convection business account).
 * Follows DDD immutability rules: mutations return a new copy.
 */
data class Tenant(
    val id: TenantId,
    val slug: TenantSlug,
    val name: TenantName,
    val status: TenantStatus = TenantStatus.TRIAL,
    val tier: SubscriptionTier = SubscriptionTier.PRO,
    val activeMachineCount: Int = 0,
    val businessPreset: Blueprint = GarmentBlueprints.DEFAULT,
    /** Kerangka tahap industri tempat pabrik ini di-provision (TRD-FLOW-001). Sumbu terpisah dari model bisnis. */
    val industryTemplate: IndustryTemplateCode = IndustryTemplateCode.KNIT_SWEATER,
    /** Vertikal tenant (B7). Default = nilai setiap baris lama (`tenants.domain_pack DEFAULT 'garment'`), bukan fallback baca. */
    val domainPack: DomainPackCode = GarmentDomainPack.CODE
) {
    val isAccessible: Boolean
        get() = status.isAccessible

    /** Pack tenant; kode tak dikenal = konfigurasi rusak → gagal keras, tidak jatuh ke garment (Kontrak 4). */
    val pack: com.eventverse.app.domain.pack.DomainPack
        get() = requireNotNull(com.eventverse.app.domain.pack.DomainPackRegistry.find(domainPack)) { "Pack ${domainPack.value} tenant ${slug.value} tidak dikenal" }

    fun canAccessPlatform(): Boolean = isAccessible

    fun activate(): Tenant = copy(status = TenantStatus.ACTIVE)

    fun suspend(): Tenant = copy(status = TenantStatus.SUSPENDED)

    fun markDue(): Tenant = copy(status = TenantStatus.DUE)

    fun markPastDue(): Tenant = copy(status = TenantStatus.PAST_DUE)

    fun upgradeTier(newTier: SubscriptionTier): Tenant = copy(tier = newTier)

    fun updateActiveMachineCount(count: Int): Tenant {
        require(count >= 0) { "Active machine count cannot be negative: $count" }
        require(count <= tier.maxActiveMachines) {
            "Machine count ($count) exceeds tier limit of ${tier.maxActiveMachines} for ${tier.name}"
        }
        return copy(activeMachineCount = count)
    }

    fun canAddMachine(): Boolean = activeMachineCount < tier.maxActiveMachines

    fun updateBusinessPreset(newPreset: Blueprint): Tenant = copy(businessPreset = newPreset)

    fun updateIndustryTemplate(template: IndustryTemplateCode): Tenant = copy(industryTemplate = template)
}

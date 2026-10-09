package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.UnresolvableBlueprintException
import com.eventverse.app.domain.pack.resolveBlueprint

import com.eventverse.app.domain.tenant.*
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory thread-safe implementation of TenantRepository.
 * Pre-seeded with 3 reference companies covering FOB, CMT, and Brand D2C.
 */
class InMemoryTenantRepository : TenantRepository {
    private val tenants = ConcurrentHashMap<TenantId, Tenant>()

    init {
        // 1. Perusahaan FOB (Full Package / OEM)
        val fobTenant = Tenant(
            id = TenantId("ten-demo-001"),
            slug = TenantSlug("wemade-demo"), // keep wemade-demo as default for backward compatibility
            name = TenantName("PT WeMade Garmen Ekspor (FOB)"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.PRO,
            activeMachineCount = 12,
            businessPreset = GarmentBlueprints.FOB_FULL_PACKAGE
        )
        tenants[fobTenant.id] = fobTenant

        // 2. Perusahaan CMT (Cut, Make, Trim / Makloon Jahit)
        val cmtTenant = Tenant(
            id = TenantId("ten-demo-cmt"),
            slug = TenantSlug("cv-berkah-makloon"),
            name = TenantName("CV Berkah Makloon Jahit (CMT)"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.PRO,
            activeMachineCount = 8,
            businessPreset = GarmentBlueprints.CMT_MAKLOON
        )
        tenants[cmtTenant.id] = cmtTenant

        // 3. Perusahaan Brand D2C (Direct-to-Consumer / Distro Mandiri)
        val d2cTenant = Tenant(
            id = TenantId("ten-demo-d2c"),
            slug = TenantSlug("urbanwear-d2c"),
            name = TenantName("UrbanWear Studio Apparel (Brand D2C)"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.PRO,
            activeMachineCount = 15,
            businessPreset = GarmentBlueprints.BRAND_D2C
        )
        tenants[d2cTenant.id] = d2cTenant
    }

    override suspend fun findById(id: TenantId): Tenant? = tenants[id]

    override suspend fun findBySlug(slug: TenantSlug): Tenant? =
        tenants.values.firstOrNull { it.slug == slug }

    override suspend fun save(tenant: Tenant): Result<Tenant> {
        // Aturan tulis yang sama dengan Postgres (TRD-PLAT-008 FR-5): kode starter harus ter-resolve oleh pack tenant,
        // supaya tes berbasis memori tak lolos pada data yang akan ditolak di produksi.
        if (resolveBlueprint(DomainPackRegistry.find(tenant.domainPack), tenant.businessPreset.code) == null) {
            return Result.failure(UnresolvableBlueprintException(tenant.slug.value, tenant.domainPack, tenant.businessPreset.code.value))
        }
        tenants[tenant.id] = tenant
        return Result.success(tenant)
    }

    override suspend fun existsBySlug(slug: TenantSlug): Boolean =
        tenants.values.any { it.slug == slug }

    override suspend fun findAll(): List<Tenant> = tenants.values.toList()
}

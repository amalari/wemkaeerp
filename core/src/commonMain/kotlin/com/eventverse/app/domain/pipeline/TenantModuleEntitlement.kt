package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.rbac.BusinessModules

import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.rbac.moduleIds
import com.eventverse.app.domain.tenant.SubscriptionTier

/**
 * Decides which operational modules a tenant is allowed to run, based on its subscription
 * plan. This is the gate a billing/superadmin flow enforces when handing modules to a
 * specific factory.
 *
 * Kept as a domain value object (no framework, no I/O) so it is enforceable from a use case
 * and equally checkable from the UI to disable controls before a request is even sent.
 */
data class TenantModuleEntitlement(
    val tier: SubscriptionTier,
    /** Seluruh modul pack tenant (B7) — makna "semua modul" untuk sentinel `null` di [toGrants]. */
    val catalog: Set<BusinessModule>,
    /**
     * Built-in modules the plan grants. Defaults to every module of the tenant's pack, so a plan
     * restricts by *count* unless a narrower catalogue is explicitly configured for it.
     */
    val grantedModules: Set<BusinessModule> = catalog,
    /** Custom plugin module ids explicitly provisioned for this tenant. */
    val grantedCustomModuleIds: Set<String> = emptySet()
) {
    val maxActiveModules: Int get() = tier.maxActivePipelineModules

    val allowsCustomPlugins: Boolean get() = tier.allowCustomPluginModules

    /**
     * Apakah tenant ini boleh menjalankan sebuah modul bawaan sama sekali.
     *
     * Berlaku untuk modul tata kelola maupun operasional — inilah gerbang yang membuat menu
     * "Alur Pabrik" benar-benar hilang bagi tenant yang tidak disambungkan ke modul itu.
     */
    fun permitsModule(module: BusinessModule): Boolean = module in grantedModules

    fun permits(node: CustomPipelineNode): Boolean = when {
        // A bypassed module occupies no licence: turning a module off is always allowed.
        node.isBypassed -> true
        node.isCustomPlugin -> allowsCustomPlugins && node.moduleId in grantedCustomModuleIds
        else -> node.standardModule?.let { it in grantedModules } ?: false
    }

    /**
     * Validates a whole topology. Returns every violation instead of only the first, so an
     * operator sees the full picture rather than fixing one module at a time.
     */
    fun validate(pipeline: CustomTenantPipeline): List<String> {
        val violations = mutableListOf<String>()
        val activeNodes = pipeline.activeNodes

        // Kuota paket menghitung modul **produksi** saja. Modul tata kelola (bagan organisasi,
        // matriks wewenang, kanvas alur) tidak pernah menjadi node di sini, jadi penyaringan ini
        // hari ini tidak membuang apa pun — ia ada supaya jaminannya terbaca sebagai aturan, bukan
        // bergantung pada kebetulan bahwa topologi tenant kebetulan tidak memuatnya.
        val billableActiveNodes = activeNodes.filterNot { node ->
            node.standardModule?.isOperational != true
        }

        if (billableActiveNodes.size > maxActiveModules) {
            violations += "Paket ${tier.name} hanya mengizinkan $maxActiveModules modul aktif, " +
                "sedangkan alur ini mengaktifkan ${billableActiveNodes.size} modul."
        }

        activeNodes.filter { it.isCustomPlugin }.forEach { node ->
            when {
                !allowsCustomPlugins ->
                    violations += "Paket ${tier.name} tidak mendukung modul kustom: " +
                        "\"${node.customDisplayName}\" (${node.moduleId})."
                node.moduleId !in grantedCustomModuleIds ->
                    violations += "Modul kustom \"${node.customDisplayName}\" (${node.moduleId}) " +
                        "belum diaktifkan untuk tenant ini."
            }
        }

        activeNodes.filterNot { it.isCustomPlugin }.forEach { node ->
            val module = node.standardModule
            when {
                module == null ->
                    violations += "Modul \"${node.moduleId}\" tidak dikenali sebagai modul bawaan " +
                        "maupun modul kustom terdaftar."
                module !in grantedModules ->
                    violations += "Modul \"${module.displayName}\" tidak termasuk dalam paket ${tier.name}."
            }
        }

        return violations
    }

    fun isSatisfiedBy(pipeline: CustomTenantPipeline): Boolean = validate(pipeline).isEmpty()

    /** Grants an additional custom plugin, e.g. after a superadmin provisions it. */
    fun grantCustomModule(moduleId: String): TenantModuleEntitlement {
        require(moduleId.isNotBlank()) { "Custom module id cannot be blank" }
        return copy(grantedCustomModuleIds = grantedCustomModuleIds + moduleId)
    }

    /** The durable part of this entitlement, for persistence. */
    fun toGrants(): TenantEntitlementGrants = TenantEntitlementGrants(
        grantedModules = grantedModules.takeIf { it != catalog },
        grantedCustomModuleIds = grantedCustomModuleIds
    )

    companion object {
        /** Plan defaults with the full built-in catalogue available. */
        fun forTier(tier: SubscriptionTier, pack: DomainPack): TenantModuleEntitlement =
            TenantModuleEntitlement(tier = tier, catalog = pack.moduleIds.toSet())

        /**
         * Combines plan defaults with a tenant's persisted grants.
         *
         * Custom plugin grants can only come from storage, never from the tier, so an
         * entitlement built without them would reject a plugin the tenant already runs.
         */
        fun resolve(
            tier: SubscriptionTier,
            grants: TenantEntitlementGrants?,
            pack: DomainPack
        ): TenantModuleEntitlement = TenantModuleEntitlement(
            tier = tier,
            catalog = pack.moduleIds.toSet(),
            grantedModules = grants?.grantedModules ?: pack.moduleIds.toSet(),
            grantedCustomModuleIds = grants?.grantedCustomModuleIds ?: emptySet()
        )
    }
}

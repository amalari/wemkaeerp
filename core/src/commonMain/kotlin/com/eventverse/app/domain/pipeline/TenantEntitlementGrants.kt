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

/**
 * Per-tenant overrides on top of what the subscription plan grants by default.
 *
 * Exists because plan tier alone cannot express everything billing decides. A custom plugin
 * module in particular is provisioned to one specific factory, so the grant has to be
 * durable — deriving it from the tier would either forbid every custom module or allow all
 * of them.
 */
data class TenantEntitlementGrants(
    /**
     * Built-in modules this tenant may run. `null` means "whatever the plan tier grants by
     * default", which is the normal case; a non-null set narrows it.
     */
    val grantedModules: Set<BusinessModule>? = null,
    /** Custom plugin module ids explicitly provisioned to this tenant. */
    val grantedCustomModuleIds: Set<String> = emptySet()
) {
    val isEmpty: Boolean get() = grantedModules == null && grantedCustomModuleIds.isEmpty()

    fun grantCustomModule(moduleId: String): TenantEntitlementGrants {
        require(moduleId.isNotBlank()) { "Custom module id cannot be blank" }
        return copy(grantedCustomModuleIds = grantedCustomModuleIds + moduleId)
    }

    fun revokeCustomModule(moduleId: String): TenantEntitlementGrants =
        copy(grantedCustomModuleIds = grantedCustomModuleIds - moduleId)

    /**
     * Menyambung atau memutus satu modul bawaan untuk tenant ini — operasi tunggal di balik toggle
     * pada dialog "Kelola Modul Tenant".
     *
     * Perhatikan penanganan `grantedModules == null`. Null berarti *"apa pun yang diberikan paket"*,
     * bukan *"kosong"*. Memutus satu modul dari keadaan itu karenanya tidak bisa dilakukan dengan
     * pengurangan himpunan; daftarnya harus dipadatkan dulu menjadi seluruh katalog, baru satu modul
     * dikeluarkan. Tanpa langkah itu, toggle pertama pada tenant mana pun akan diam-diam mencabut
     * delapan modul lain sekaligus.
     *
     * [catalog] = seluruh modul pack tenant (B7): "semua" berarti semua modul **vertikal tenant itu**.
     */
    fun withModule(module: BusinessModule, enabled: Boolean, catalog: Set<BusinessModule>): TenantEntitlementGrants {
        val current = grantedModules ?: catalog
        val updated = if (enabled) current + module else current - module
        return copy(
            // Kembali ke null bila hasilnya utuh: menyimpan "semua" sebagai null membuat tenant
            // ikut mewarisi modul baru yang dirilis kemudian, tanpa perlu migrasi data lagi.
            grantedModules = updated.takeIf { it != catalog }
        )
    }

    companion object {
        val NONE = TenantEntitlementGrants()
    }
}

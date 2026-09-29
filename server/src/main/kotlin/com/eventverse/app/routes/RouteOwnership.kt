package com.eventverse.app.routes

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

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.pipeline.ModuleFeatureRegistry
import com.eventverse.app.domain.rbac.BusinessModule

/** Pemilik sebuah route `/api/tenant/…`: modul, fitur di dalam modul, atau layanan platform. */
sealed interface RouteOwner {
    data class Module(val module: BusinessModule) : RouteOwner
    data class Feature(val code: String, val hostModule: BusinessModule) : RouteOwner
    /** Layanan tenant umum yang bukan milik satu modul (info tenant, entitlement, pratinjau tagihan). */
    data class Platform(val reason: String) : RouteOwner
}

/**
 * Siapa pemilik setiap route tenant (module-integration-rules §5). Route baru yang tidak tercatat di
 * sini **maupun** di [ModuleFeatureRegistry] menggagalkan `RouteOwnershipTest` — itulah cara fitur
 * yang lupa didaftarkan ketahuan sebelum merge, bukan setelah kanvas diam-diam tidak memuatnya.
 *
 * Urutan penting: prefix yang lebih spesifik didahulukan.
 */
object RouteOwnership {

    private val moduleRoutes: List<Pair<String, RouteOwner>> = listOf(
        "/api/tenant/pipeline" to RouteOwner.Module(GarmentModules.FACTORY_FLOW),
        "/api/tenant/locations" to RouteOwner.Module(GarmentModules.FACTORY_FLOW),
        "/api/tenant/roles" to RouteOwner.Module(GarmentModules.DYNAMIC_RBAC),
        "/api/tenant/module-assignments" to RouteOwner.Module(GarmentModules.DYNAMIC_RBAC),
        "/api/tenant/departments" to RouteOwner.Module(GarmentModules.ORG_CHART),
        "/api/tenant/employees" to RouteOwner.Module(GarmentModules.ORG_CHART),
        "/api/tenant/crm" to RouteOwner.Module(GarmentModules.CRM_SALES),
        "/api/tenant/deals" to RouteOwner.Module(GarmentModules.CRM_SALES),
        "/api/tenant/sampling" to RouteOwner.Module(GarmentModules.SAMPLING_ORDER),
        "/api/tenant/tech-pack" to RouteOwner.Module(GarmentModules.TECH_PACK_BOM),
        "/api/tenant/costing" to RouteOwner.Module(GarmentModules.COSTING_HPP),
        "/api/tenant/fulfillment" to RouteOwner.Module(GarmentModules.FULFILLMENT),
        "/api/tenant/invoicing" to RouteOwner.Module(GarmentModules.INVOICING),
        "/api/tenant/master-data" to RouteOwner.Module(GarmentModules.MASTER_DATA),
        "/api/tenant/vendor" to RouteOwner.Module(GarmentModules.VENDOR_CONTACTS),
        "/api/tenant/info" to RouteOwner.Platform("info tenant aktif"),
        "/api/tenant/me" to RouteOwner.Platform("wewenang pemanggil sendiri (menu)"),
        "/api/tenant/pack" to RouteOwner.Platform("kosakata vertikal tenant (Domain Pack)"),
        "/api/tenant/entitlement" to RouteOwner.Platform("entitlement paket tenant"),
        "/api/tenant/billing-preview" to RouteOwner.Platform("pratinjau tagihan paket"),
        "/api/tenant/customization-requests" to RouteOwner.Platform("permintaan kustomisasi modul ke tim platform")
    )

    fun ownerOf(path: String): RouteOwner? {
        if (path == "/api/tenant") return RouteOwner.Platform("info tenant")
        ModuleFeatureRegistry.ownerOf(path)?.let { return RouteOwner.Feature(it.code, it.hostModule) }
        return moduleRoutes.firstOrNull { (prefix, _) -> path.startsWith(prefix) }?.second
    }
}

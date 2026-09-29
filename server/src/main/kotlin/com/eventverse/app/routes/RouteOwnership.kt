package com.eventverse.app.routes

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
        "/api/tenant/pipeline" to RouteOwner.Module(BusinessModule.FACTORY_FLOW),
        "/api/tenant/locations" to RouteOwner.Module(BusinessModule.FACTORY_FLOW),
        "/api/tenant/roles" to RouteOwner.Module(BusinessModule.DYNAMIC_RBAC),
        "/api/tenant/module-assignments" to RouteOwner.Module(BusinessModule.DYNAMIC_RBAC),
        "/api/tenant/departments" to RouteOwner.Module(BusinessModule.ORG_CHART),
        "/api/tenant/employees" to RouteOwner.Module(BusinessModule.ORG_CHART),
        "/api/tenant/crm" to RouteOwner.Module(BusinessModule.CRM_SALES),
        "/api/tenant/deals" to RouteOwner.Module(BusinessModule.CRM_SALES),
        "/api/tenant/sampling" to RouteOwner.Module(BusinessModule.SAMPLING_ORDER),
        "/api/tenant/tech-pack" to RouteOwner.Module(BusinessModule.TECH_PACK_BOM),
        "/api/tenant/costing" to RouteOwner.Module(BusinessModule.COSTING_HPP),
        "/api/tenant/fulfillment" to RouteOwner.Module(BusinessModule.FULFILLMENT),
        "/api/tenant/invoicing" to RouteOwner.Module(BusinessModule.INVOICING),
        "/api/tenant/master-data" to RouteOwner.Module(BusinessModule.MASTER_DATA),
        "/api/tenant/vendor" to RouteOwner.Module(BusinessModule.VENDOR_CONTACTS),
        "/api/tenant/info" to RouteOwner.Platform("info tenant aktif"),
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

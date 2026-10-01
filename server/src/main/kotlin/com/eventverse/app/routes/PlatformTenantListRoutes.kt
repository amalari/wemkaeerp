package com.eventverse.app.routes

import com.eventverse.app.domain.tenant.TenantRepository
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /api/admin/tenants` — daftar seluruh tenant untuk konsol platform (`app.<base>/admin`,
 * discovery-M3b). Gate superadmin datang dari `TenantResolutionPlugin.platformRoutePrefixes`
 * (`/api/admin`), sama seperti [adminRoutes]; route ini tidak menambah pintu baru.
 *
 * Sengaja hanya identitas + status: entitlement dan katalog per tenant tetap di
 * `GET /api/admin/tenants/{slug}`, supaya daftar ratusan tenant tidak memuat katalog modul satu per satu.
 */
fun Route.platformTenantListRoutes(tenantRepository: TenantRepository) {
    get("/api/admin/tenants") {
        val rows = tenantRepository.findAll().sortedBy { it.slug.value }.joinToString(",") { t ->
            "{\"id\":\"${t.id.value}\",\"slug\":\"${t.slug.value}\",\"name\":\"${t.name.value.jsonEscape()}\"," +
                "\"status\":\"${t.status.name}\",\"tier\":\"${t.tier.name}\",\"domainPack\":\"${t.domainPack.value}\"}"
        }
        call.respondText("{\"tenants\":[$rows]}", ContentType.Application.Json)
    }
}

private fun String.jsonEscape(): String = replace("\\", "\\\\").replace("\"", "\\\"")

package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.transfer.TenantLocationConfig
import com.eventverse.app.domain.transfer.TenantLocationConfigRepository
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.transfer.TenantLocationCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * Konfigurasi lokasi fisik pabrik dan pemetaan simpul alur ke gedungnya.
 *
 * Membaca butuh `VIEW`, menulis butuh `MANAGE` — bukan `OPERATE`. Ini bukan data harian yang
 * diisi staf; ini topologi yang menentukan kapan gerbang Surat Jalan menutup. Lihat
 * [FactoryFlowAccessGuard] untuk pilihan modul gerbangnya.
 */
fun Route.tenantLocationRoutes(
    repository: TenantLocationConfigRepository,
    roleRepository: RoleRepository? = null,
    moduleAssignmentRepository: ModuleAssignmentRepository? = null
) {
    route("/api/tenant/locations") {

        get {
            val tenant = call.requireLocationTenant() ?: return@get
            val decision = call.factoryFlowDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireFactoryFlowAccess(decision, AccessLevel.VIEW)) return@get

            val config = repository.findByTenantId(tenant.tenantId.value)
                ?: TenantLocationConfig(tenantId = tenant.tenantId.value)
            call.respondLocationJson(TenantLocationCodec.encode(config).encode())
        }

        put {
            val tenant = call.requireLocationTenant() ?: return@put
            val decision = call.factoryFlowDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireFactoryFlowAccess(decision, AccessLevel.MANAGE)) return@put

            val json = JsonParser.parse(call.receiveText()) as? JsonValue.Obj
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            // Tenant diambil dari sesi, bukan dari muatan: klien tidak boleh memilih konfigurasi
            // tenant mana yang sedang ia tulis.
            val config = try {
                TenantLocationCodec.decode(json, tenant.tenantId.value)
            } catch (e: IllegalArgumentException) {
                return@put call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    e.message ?: "Konfigurasi lokasi tidak sah"
                )
            }

            repository.save(config)
            call.respondLocationJson(TenantLocationCodec.encode(config).encode())
        }
    }
}

private suspend fun ApplicationCall.requireLocationTenant(): TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respond(HttpStatusCode.NotFound, "No tenant context found")
    }
    return tenant
}

private suspend fun ApplicationCall.respondLocationJson(json: String) {
    respondText(text = json, contentType = ContentType.Application.Json)
}

package com.eventverse.app.routes

import com.eventverse.app.domain.auth.Permission
import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfig
import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfigRepository
import com.eventverse.app.domain.fulfillment.HandoverRouteRepository
import com.eventverse.app.domain.fulfillment.HandoverRouteSettingsView
import com.eventverse.app.domain.fulfillment.TenantHandoverRoutes
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.infrastructure.ROUTE_IN_USE
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.fulfillment.HandoverRouteSettingsCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
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
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("FulfillmentRouteRoutes")

/**
 * Konfigurasi serah terima sebagai data (TRD-FLOW-003, B3): daftar rute milik tenant
 * (`/routes`) dan mode efektifnya (`/route-settings`).
 *
 * Dipindah keluar dari `FulfillmentTransferRoutes.kt` yang kini hanya melayani perjalanan
 * karung. Gerbangnya dua lapis: gerbang terpusat (`TenantRouteGatePolicy`) menuntut VIEW
 * untuk baca dan OPERATE untuk tulis atas modul FULFILLMENT sebelum body dibaca; kedua PUT
 * di sini menambah syarat MANAGE di handler — topologi menentukan kapan gerbang ACC menutup,
 * dan siapa pun yang bisa menulisnya bisa mematikannya (paritas dengan PUT route-settings lama).
 *
 * Kode tak dikenal DITOLAK (400), tidak pernah dilewati diam-diam (AC-4): dekode ketat dari
 * [HandoverRouteSettingsCodec], keanggotaan rute divalidasi terhadap daftar efektif tenant.
 */
fun Route.fulfillmentRouteRoutes(
    routes: HandoverRouteRepository,
    routeConfigRepository: FulfillmentRouteConfigRepository,
    roleRepository: RoleRepository? = null
) {
    route("/api/tenant/fulfillment/routes") {

        // Baca cukup konteks tenant: daftar rute dipakai layar kerja sebelum operator mengisi apa pun.
        get {
            val tenant = call.requireRouteTenant() ?: return@get
            val effective = tenant.effectiveRoutes(routes)
            call.respondJson(HandoverRouteSettingsCodec.encodeRoutes(effective.routes).encode())
        }

        put {
            val tenant = call.requireRouteTenant() ?: return@put
            if (!call.canManageRoutes(tenant, roleRepository)) {
                return@put call.respond(
                    HttpStatusCode.Forbidden,
                    "Akses ditolak: mengubah daftar rute butuh wewenang kelola modul FULFILLMENT"
                )
            }
            val body = call.routeBody()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Body JSON tidak terbaca")

            // Kode duplikat/tidak sah, label kosong: ditolak di domain — jawabannya 400 (salah isi),
            // bukan 409 (konflik dengan riwayat).
            runCatching {
                val list = TenantHandoverRoutes(tenant.tenantId, HandoverRouteSettingsCodec.decodeRoutes(body))
                routes.save(list)
                list
            }.onSuccess { list ->
                call.respondJson(HandoverRouteSettingsCodec.encodeRoutes(list.routes).encode())
            }.onFailure { failure ->
                val message = failure.message ?: "Gagal menyimpan daftar rute"
                if (ROUTE_IN_USE in message) {
                    call.respond(HttpStatusCode.Conflict, message)
                } else {
                    call.respond(HttpStatusCode.BadRequest, message)
                }
            }
        }
    }

    route("/api/tenant/fulfillment/route-settings") {

        get {
            val tenant = call.requireRouteTenant() ?: return@get
            val effective = tenant.effectiveRoutes(routes)
            val stored = routeConfigRepository.findByTenantId(tenant.tenantId)?.modes ?: emptyMap()
            // Baris mode untuk kode yang sudah tidak ada di daftar tenant adalah sisa konfigurasi:
            // dibaca sebagai tidak disetel (jatuh ke ADMIN_HUB) dan dilaporkan — bukan membuat
            // layar kerja gagal dibuka, dan bukan pula diabaikan tanpa jejak (TRD Monitoring).
            val known = stored.filterKeys { code -> effective.find(code) != null }
            (stored.keys - known.keys).takeIf { it.isNotEmpty() }?.let { stale ->
                log.warn(
                    "Mode untuk rute tanpa entri daftar tenant {} diabaikan: {}",
                    tenant.tenantId.value,
                    stale.joinToString { it.value }
                )
            }
            call.respondJson(HandoverRouteSettingsCodec.encode(HandoverRouteSettingsView.of(effective, known)).encode())
        }

        put {
            val tenant = call.requireRouteTenant() ?: return@put
            if (!call.canManageRoutes(tenant, roleRepository)) {
                return@put call.respond(
                    HttpStatusCode.Forbidden,
                    "Akses ditolak: mengubah pola serah terima butuh wewenang kelola modul FULFILLMENT"
                )
            }
            val body = call.routeBody()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Body JSON tidak terbaca")

            val decoded = runCatching { HandoverRouteSettingsCodec.decodeModes(body) }
                .getOrElse { return@put call.respond(HttpStatusCode.BadRequest, it.message ?: "Body tidak sah") }

            val effective = tenant.effectiveRoutes(routes)
            val unknown = decoded.keys.firstOrNull { effective.find(it) == null }
            if (unknown != null) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    "Rute '${unknown.value}' tidak dikenal tenant ${tenant.slug.value} - daftarkan di /routes lebih dulu"
                )
            }

            runCatching { routeConfigRepository.save(FulfillmentRouteConfig(tenant.tenantId, decoded)) }
                .onSuccess {
                    call.respondJson(
                        HandoverRouteSettingsCodec.encode(HandoverRouteSettingsView.of(effective, decoded)).encode()
                    )
                }
                .onFailure { call.respond(HttpStatusCode.BadRequest, it.message ?: "Gagal menyimpan konfigurasi rute") }
        }
    }
}

/**
 * Daftar rute efektif: salinan tersimpan bila ada, jika tidak template pack — tanpa menyimpan
 * apa pun (D4). `internal` supaya jalur submit di `FulfillmentTransferRoutes` memakai sumber
 * yang sama: satu daftar rute, satu kebenaran.
 */
internal suspend fun TenantContext.effectiveRoutes(routes: HandoverRouteRepository): TenantHandoverRoutes =
    TenantHandoverRoutes.resolve(tenantId, routes.findByTenantId(tenantId), pack.handoverRouteTemplate)

private suspend fun ApplicationCall.requireRouteTenant(): TenantContext? {
    val ctx = tenantContextOrNull
    if (ctx == null) respond(HttpStatusCode.BadRequest, "Konteks tenant tidak ditemukan")
    return ctx
}

private suspend fun ApplicationCall.routeBody(): JsonValue.Obj? =
    runCatching { JsonParser.parse(receiveText()) as? JsonValue.Obj }.getOrNull()

private suspend fun ApplicationCall.respondJson(json: String) =
    respondText(text = json, contentType = ContentType.Application.Json)

/** Syarat tulis di handler: MANAGE — melengkapi gerbang terpusat yang hanya menuntut OPERATE. */
private suspend fun ApplicationCall.canManageRoutes(
    tenant: TenantContext,
    roleRepository: RoleRepository?
): Boolean {
    val principal = callerPrincipalOrNull ?: return false
    if (principal.isPlatformSuperadmin) return true
    if (principal.role.defaultPermissions.contains(Permission.APPROVE_COSTING)) return true
    val customRoleId = principal.customRoleId ?: return false
    val role = roleRepository?.findById(tenant.tenantId, RoleId(customRoleId)) ?: return false
    return role.hasAccess(GarmentModules.FULFILLMENT, AccessLevel.MANAGE) || role.isSystemOwnerRole
}

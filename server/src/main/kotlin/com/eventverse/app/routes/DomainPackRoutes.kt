package com.eventverse.app.routes

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRepository
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.pack.usecases.AssignTenantDomainPackUseCase
import com.eventverse.app.domain.pack.usecases.LockDomainPackUseCase
import com.eventverse.app.domain.pack.usecases.SaveDomainPackDraftUseCase
import com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.DomainPackDecodeException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * Domain Pack per tenant (B7, TRD-PLAT-001-tenant-pack FR-6).
 *
 * - `GET /api/tenant/pack`: kosakata vertikal tenant pemanggil. *Open by design* (seperti `me/access`): isinya nama
 *   modul/fase/port, bukan data tenant, dan setiap anggota butuh untuk menyusun menu & kanvas.
 * - `/api/admin/domain-packs…` dan `PUT /api/admin/tenants/{slug}/domain-pack`: superadmin saja. Plugin tenant sudah
 *   menolak setiap `/api/admin` selain superadmin **sebelum** handler ini jalan (fail-closed).
 */
fun Route.domainPackRoutes(
    tenantRepository: TenantRepository,
    packRepository: DomainPackRepository,
    probe: TenantOperationalDataProbe,
    auditLogRepository: AuditLogRepository
) {
    val saveDraft = SaveDomainPackDraftUseCase(packRepository)
    val lock = LockDomainPackUseCase(packRepository)
    val assign = AssignTenantDomainPackUseCase(tenantRepository, probe, packRepository)

    get("/api/tenant/pack") {
        val tenant = call.tenantContextOrNull ?: return@get call.respond(HttpStatusCode.NotFound, "No tenant context found")
        call.respondText(DomainPackCodec.encodeToString(tenant.pack), ContentType.Application.Json)
    }

    route("/api/admin/domain-packs") {
        get {
            call.respondText(jsonArrayOf(packRepository.findAllEffective().map(::summary)).encode(), ContentType.Application.Json)
        }

        // Simpan draf. Body = dokumen DomainPackCodec; `code` di body wajib sama dengan path.
        put("/{code}") {
            val code = call.parameters["code"].orEmpty()
            val pack = try {
                DomainPackCodec.decode(call.receiveText())
            } catch (e: DomainPackDecodeException) {
                return@put call.respond(HttpStatusCode.BadRequest, e.message ?: "Pack tidak sah")
            }
            if (pack.code.value != code) return@put call.respond(HttpStatusCode.BadRequest, "Kode di body (${pack.code.value}) ≠ path ($code)")
            val owner = call.request.queryParameters["ownerSlug"]?.let { slug ->
                tenantRepository.findAll().firstOrNull { it.slug.value == slug }?.id
                    ?: return@put call.respond(HttpStatusCode.NotFound, "Tenant pemilik '$slug' tidak ditemukan")
            }
            saveDraft(pack, owner)
                .onSuccess { call.respondText(summary(it).encode(), ContentType.Application.Json) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, it.message ?: "Gagal menyimpan pack") }
        }

        post("/{code}/lock") {
            val code = runCatching { DomainPackCode(call.parameters["code"].orEmpty()) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Kode pack tidak sah")
            lock(code)
                .onSuccess { call.respondText(summary(it).encode(), ContentType.Application.Json) }
                .onFailure { call.respond(HttpStatusCode.Conflict, it.message ?: "Gagal mengunci pack") }
        }
    }

    // Tetapkan vertikal tenant. Body: {"code":"klinik"}.
    put("/api/admin/tenants/{slug}/domain-pack") {
        val tenant = call.requireTargetTenant(tenantRepository) ?: return@put
        val actor = call.callerPrincipalOrNull ?: return@put call.respond(HttpStatusCode.Unauthorized, "Authentication required")
        val code = runCatching { JsonParser.parseObject(call.receiveText()).string("code")?.let(::DomainPackCode) }.getOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Body harus {\"code\":\"<kode pack>\"}")
        assign(tenant.id, code)
            .onSuccess { updated ->
                call.recordAudit(auditLogRepository, actor, updated, AuditAction.TENANT_DOMAIN_PACK_ASSIGNED,
                    "Menetapkan vertikal tenant '${updated.slug.value}': ${tenant.domainPack.value} → ${code.value}")
                call.respondText(jsonObjectOf("slug" to jsonOf(updated.slug.value), "domainPack" to jsonOf(updated.domainPack.value)).encode(), ContentType.Application.Json)
            }
            .onFailure { call.respond(HttpStatusCode.Conflict, it.message ?: "Gagal menetapkan pack") }
    }
}

private fun summary(stored: StoredDomainPack): JsonValue.Obj = jsonObjectOf(
    "code" to jsonOf(stored.pack.code.value),
    "displayName" to jsonOf(stored.pack.displayName),
    "version" to jsonOf(stored.version),
    "status" to jsonOf(stored.status.name),
    "ownerTenantId" to jsonOf(stored.ownerTenantId?.value),
    "moduleCount" to jsonOf(stored.pack.modules.size)
)

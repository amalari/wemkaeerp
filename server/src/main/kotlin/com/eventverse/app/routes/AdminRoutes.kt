package com.eventverse.app.routes

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.pipeline.TenantEntitlementRepository
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.pipeline.usecases.GetTenantEntitlementUseCase
import com.eventverse.app.domain.pipeline.usecases.GetTenantModuleCatalogUseCase
import com.eventverse.app.domain.pipeline.usecases.SetTenantEntitlementUseCase
import com.eventverse.app.domain.pipeline.usecases.UpdateTenantTierUseCase
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.plugins.CallerPrincipal
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.routes.dto.AdminDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Platform-administration API: lets a `PLATFORM_SUPERADMIN` provision what a specific
 * tenant is allowed to run, independent of that tenant's own session.
 *
 * [com.eventverse.app.plugins.TenantResolutionPlugin] already restricts every route under
 * `/api/admin` to an authenticated superadmin before any handler here runs — see its
 * `platformRoutePrefixes` — so these handlers only need to resolve the *target* tenant
 * named by the `{slug}` path parameter.
 *
 * Every write here is recorded to [AuditLogRepository]: a superadmin acting on a factory's
 * configuration is invisible from inside that factory's own workspace, so the only way this
 * capability stays accountable is if every use of it leaves a trail.
 */
fun Route.adminRoutes(
    tenantRepository: TenantRepository,
    pipelineRepository: TenantPipelineRepository,
    entitlementRepository: TenantEntitlementRepository,
    auditLogRepository: AuditLogRepository
) {
    val getEntitlementUseCase = GetTenantEntitlementUseCase(entitlementRepository)
    val getModuleCatalogUseCase = GetTenantModuleCatalogUseCase(pipelineRepository)
    val setEntitlementUseCase = SetTenantEntitlementUseCase(entitlementRepository, pipelineRepository)
    val updateTierUseCase = UpdateTenantTierUseCase(tenantRepository, pipelineRepository, entitlementRepository)

    route("/api/admin/tenants/{slug}") {

        // 1. GET the full admin view of one tenant: identity, plan, grants, resolved catalogue.
        get {
            val tenant = call.requireTargetTenant(tenantRepository) ?: return@get

            respondAdminView(call, tenant, getEntitlementUseCase, getModuleCatalogUseCase)
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal memuat data tenant") }
        }

        // 2. PUT which modules this tenant may run, on top of its plan tier.
        put("/entitlement") {
            val tenant = call.requireTargetTenant(tenantRepository) ?: return@put
            val actor = call.callerPrincipalOrNull ?: run {
                // The plugin guarantees this for any request that reaches an /api/admin
                // handler, so this only guards against the plugin being misconfigured.
                call.respond(HttpStatusCode.Unauthorized, "Authentication required")
                return@put
            }

            val grants = AdminDto.readEntitlementGrants(call.receiveText())
            if (grants == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Body harus berisi {\"grantedModules\":[...] atau null,\"grantedCustomModuleIds\":[...]}"
                )
                return@put
            }

            val autoBypass = call.request.queryParameters["autoBypass"]?.toBooleanStrictOrNull() ?: false

            setEntitlementUseCase(tenant.id, tenant.tier, grants, tenant.pack, autoBypassPipelineModules = autoBypass)
                .onSuccess {
                    val bypassNote = if (autoBypass) " (auto-bypass alur aktif)" else ""
                    call.recordAudit(
                        auditLogRepository = auditLogRepository,
                        actor = actor,
                        tenant = tenant,
                        action = AuditAction.TENANT_ENTITLEMENT_UPDATED,
                        summary = "Mengubah entitlement modul tenant '${tenant.slug.value}'$bypassNote: " +
                            "modul bawaan=${grants.grantedModules?.size ?: "semua"}, " +
                            "modul kustom=${grants.grantedCustomModuleIds}"
                    )
                    respondAdminView(call, tenant, getEntitlementUseCase, getModuleCatalogUseCase)
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it, "Gagal mengubah entitlement") }
        }

        // 3. PUT this tenant's subscription plan.
        put("/tier") {
            val tenant = call.requireTargetTenant(tenantRepository) ?: return@put
            val actor = call.callerPrincipalOrNull ?: run {
                // The plugin guarantees this for any request that reaches an /api/admin
                // handler, so this only guards against the plugin being misconfigured.
                call.respond(HttpStatusCode.Unauthorized, "Authentication required")
                return@put
            }

            val tierCode = AdminDto.readTierCode(call.receiveText())
            val newTier = tierCode?.let { code ->
                runCatching { SubscriptionTier.valueOf(code.uppercase()) }.getOrNull()
            }
            if (newTier == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Body harus berisi {\"tier\":\"STARTER\"|\"PRO\"|\"ENTERPRISE\"}"
                )
                return@put
            }

            updateTierUseCase(tenant.id, newTier)
                .onSuccess { updatedTenant ->
                    call.recordAudit(
                        auditLogRepository = auditLogRepository,
                        actor = actor,
                        tenant = updatedTenant,
                        action = AuditAction.TENANT_TIER_UPDATED,
                        summary = "Mengubah paket tenant '${updatedTenant.slug.value}' " +
                            "dari ${tenant.tier.name} ke ${newTier.name}"
                    )
                    respondAdminView(call, updatedTenant, getEntitlementUseCase, getModuleCatalogUseCase)
                }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it, "Gagal mengubah paket tenant") }
        }

        // 4. GET the audit trail for this tenant.
        get("/audit-log") {
            val tenant = call.requireTargetTenant(tenantRepository) ?: return@get

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
            val entries = auditLogRepository.findByTenant(tenant.id, limit)

            call.respondText(
                text = AdminDto.auditLogToJson(entries),
                contentType = ContentType.Application.Json
            )
        }
    }

    // ------------------------------------------------------------------ jam trial (V88)
    // TRIAL sudah ada sejak V1 tapi tanpa tenggat: tenant baru dapat PRO penuh selamanya.
    // Endpoint ini papan pantau + tuas keputusannya; penegakan login menyusul di jalur auth
    // (koordinasi dengan login-split M3).

    get("/api/admin/trials") {
        val now = Clock.System.now()
        val trials = tenantRepository.findAll()
            .filter { it.status == TenantStatus.TRIAL }
            .sortedWith(compareBy<Tenant> { it.trialEndsAt ?: Instant.DISTANT_FUTURE }.thenBy { it.slug.value })

        call.respondText(
            text = "[" + trials.joinToString(",") { t ->
                val remainingDays = t.trialEndsAt?.let { (it - now).inWholeDays }
                val endsAtJson = t.trialEndsAt?.toString() ?: "null"
                val name = t.name.value.replace("\"", "'")
                """{"slug":"${t.slug.value}","name":"$name","tier":"${t.tier.name}",""" +
                    """"trialStarted":${t.trialEndsAt != null},"trialEndsAt":$endsAtJson,""" +
                    """"remainingDays":$remainingDays,"expired":${t.trialExpired(now)}}"""
            } + "]",
            contentType = ContentType.Application.Json
        )
    }

    post("/api/admin/trials/{slug}/extend") {
        val tenant = call.requireTargetTenant(tenantRepository) ?: return@post
        val days = call.request.queryParameters["days"]?.toLongOrNull()?.coerceIn(1, 90) ?: 7L

        runCatching { tenant.extendTrial(days, Clock.System.now()) }
            .onFailure { e ->
                call.respond(HttpStatusCode.Conflict, "Perpanjangan trial gagal: ${e.message}")
            }
            .onSuccess { extended ->
                tenantRepository.save(extended).getOrElse {
                    call.respond(HttpStatusCode.InternalServerError, "Gagal menyimpan perpanjangan trial")
                    return@post
                }
                val principal = call.callerPrincipalOrNull
                if (principal != null) {
                    call.recordAudit(
                        auditLogRepository, principal, extended, AuditAction.TENANT_TRIAL_EXTENDED,
                        "trial tenant '${tenant.slug.value}' diperpanjang $days hari " +
                            "(sampai ${extended.trialEndsAt})"
                    )
                }
                call.respondText(
                    text = """{"slug":"${extended.slug.value}","status":"${extended.status.name}",""" +
                        """"trialEndsAt":"${extended.trialEndsAt}","extendedDays":$days}""",
                    contentType = ContentType.Application.Json
                )
            }
    }
}

// ---------------------------------------------------------------------------
// Shared request/response plumbing
// ---------------------------------------------------------------------------

internal suspend fun ApplicationCall.requireTargetTenant(
    tenantRepository: TenantRepository
): Tenant? {
    val rawSlug = parameters["slug"]?.trim()?.lowercase()
    val tenant = rawSlug
        ?.takeIf { it.isNotBlank() }
        ?.let { slug -> runCatching { TenantSlug(slug) }.getOrNull() }
        ?.let { tenantRepository.findBySlug(it) }

    if (tenant == null) {
        respond(HttpStatusCode.NotFound, "Tenant dengan slug '$rawSlug' tidak ditemukan")
    }
    return tenant
}

private suspend fun respondAdminView(
    call: ApplicationCall,
    tenant: Tenant,
    getEntitlementUseCase: GetTenantEntitlementUseCase,
    getModuleCatalogUseCase: GetTenantModuleCatalogUseCase
): Result<Unit> = runCatching {
    val entitlement = getEntitlementUseCase(tenant.id, tenant.tier, tenant.pack).getOrThrow()
    val modules = getModuleCatalogUseCase(tenant.id, entitlement, tenant.businessPreset).getOrThrow()
    val grants = entitlement.toGrants()

    call.respondText(
        text = AdminDto.tenantAdminViewToJson(tenant, grants, entitlement, modules),
        contentType = ContentType.Application.Json
    )
}

internal suspend fun ApplicationCall.recordAudit(
    auditLogRepository: AuditLogRepository,
    actor: CallerPrincipal,
    tenant: Tenant,
    action: AuditAction,
    summary: String
) {
    auditLogRepository.record(
        AuditLogEntry(
            id = "audit-${tenant.id.value}-${Clock.System.now().toEpochMilliseconds()}",
            actorUserId = actor.userId,
            actorRole = actor.role,
            targetTenantId = tenant.id,
            action = action,
            summary = summary,
            occurredAt = Clock.System.now()
        )
    )
    // A failed audit write is deliberately not surfaced to the caller: the entitlement or
    // tier change already succeeded, and the response has not been sent yet, so failing the
    // whole request here would make a successful change look like it failed.
}

private suspend fun ApplicationCall.respondFailure(
    status: HttpStatusCode,
    cause: Throwable,
    fallbackMessage: String
) {
    respond(status, cause.message ?: fallbackMessage)
}

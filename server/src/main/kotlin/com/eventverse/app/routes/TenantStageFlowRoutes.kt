package com.eventverse.app.routes

import com.eventverse.app.domain.auth.Permission
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.stageflow.IndustryStageTemplates
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.TenantStageFlow
import com.eventverse.app.domain.stageflow.TenantStageFlowRepository
import com.eventverse.app.domain.stageflow.usecases.AddStageCommand
import com.eventverse.app.domain.stageflow.usecases.AddStageUseCase
import com.eventverse.app.domain.stageflow.usecases.GetTenantStageFlowUseCase
import com.eventverse.app.domain.stageflow.usecases.MoveStageUseCase
import com.eventverse.app.domain.stageflow.usecases.RemoveStageUseCase
import com.eventverse.app.domain.stageflow.usecases.RenameStageUseCase
import com.eventverse.app.domain.stageflow.usecases.ResetStageFlowUseCase
import com.eventverse.app.domain.stageflow.usecases.StageHasProcessesException
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.stageflow.TenantStageFlowCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Kerangka tahap alur tenant (TRD-FLOW-001). Tenant tanpa kerangka di-provision dari template
 * industrinya (`tenants.industry_template`, V74).
 *
 * Baca terbuka untuk semua pengguna tenant — papan sampling dan meja operator membutuhkannya.
 * Tulis bergerbang `FACTORY_FLOW` `MANAGE`: kerangka adalah topologi alur, sekelas pemetaan lokasi
 * (lihat [FactoryFlowAccessGuard]); Owner & PPIC boleh, Operator & Sales tidak.
 */
fun Route.tenantStageFlowRoutes(
    repository: TenantStageFlowRepository,
    processCatalogRepository: TenantProcessCatalogRepository,
    roleRepository: RoleRepository? = null,
    moduleAssignmentRepository: ModuleAssignmentRepository? = null
) {
    val getStageFlow = GetTenantStageFlowUseCase(repository)
    val addStage = AddStageUseCase(repository)
    val removeStage = RemoveStageUseCase(repository, processCatalogRepository)
    val moveStage = MoveStageUseCase(repository)
    val renameStage = RenameStageUseCase(repository)
    val resetStageFlow = ResetStageFlowUseCase(repository, processCatalogRepository)

    suspend fun ApplicationCall.manageTenant(): TenantContext? {
        val tenant = tenantContextOrNull ?: run { respond(HttpStatusCode.NotFound, "No tenant context found"); return null }
        val decision = factoryFlowDecision(tenant, roleRepository, moduleAssignmentRepository)
        // Gerbang bersama bersifat fail-open saat wewenang tak bisa dihitung (pemanggil tanpa jabatan
        // kustom maupun divisi). Untuk MENGUBAH kerangka itu terlalu longgar — operator lama tanpa
        // jabatan bisa menulis. Di sini fail-closed: tanpa keputusan RBAC, hanya peran platform yang
        // membawa MANAGE_TENANT (admin/owner tenant, superadmin) yang boleh.
        if (!mayEditWithoutDecision(decision, callerPrincipalOrNull?.role)) {
            respond(HttpStatusCode.Forbidden, "Butuh wewenang Kelola atas Alur Pabrik untuk mengubah kerangka tahap")
            return null
        }
        return tenant.takeIf { requireFactoryFlowAccess(decision, AccessLevel.MANAGE) }
    }

    get("/api/tenant/stage-templates") {
        call.respondText(
            jsonObjectOf(
                "templates" to jsonArrayOf(IndustryTemplateCode.entries.map { template ->
                    jsonObjectOf(
                        "code" to jsonOf(template.name),
                        "displayName" to jsonOf(template.displayName),
                        "stages" to TenantStageFlowCodec.encodeStages(IndustryStageTemplates.stagesOf(template))
                    )
                })
            ).encode(),
            ContentType.Application.Json
        )
    }

    route("/api/tenant/stage-flow") {
        get {
            val tenant = call.tenantContextOrNull ?: return@get call.respond(HttpStatusCode.NotFound, "No tenant context found")
            call.respondFlow(getStageFlow(tenant.tenantId, tenant.industryTemplate))
        }

        post("/stages") {
            val tenant = call.manageTenant() ?: return@post
            val body = call.jsonBody() ?: return@post
            val command = runCatching {
                AddStageCommand(
                    tenantId = tenant.tenantId,
                    code = requireNotNull(StageCode.parseOrNull(body.string("code"))) { "Kode tahap wajib huruf besar/angka/_ (2–48)" },
                    displayName = requireNotNull(body.string("displayName")?.takeIf { it.isNotBlank() }) { "Nama tahap wajib diisi" },
                    shortLabel = body.string("shortLabel").orEmpty(),
                    archetype = ModuleArchetype.fromCode(body.string("archetype")) ?: ModuleArchetype.CUSTOM_EXTENSION,
                    afterCode = requireNotNull(StageCode.parseOrNull(body.string("afterCode"))) { "afterCode wajib diisi" },
                    isOperatorDesk = body.boolean("isOperatorDesk") ?: true,
                    fallbackTemplate = tenant.industryTemplate
                )
            }.getOrElse { return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Permintaan tidak valid") }
            call.respondFlow(addStage(command))
        }

        delete("/stages/{code}") {
            val tenant = call.manageTenant() ?: return@delete
            val code = call.stageCodeParam() ?: return@delete
            call.respondFlow(removeStage(tenant.tenantId, code, tenant.industryTemplate))
        }

        post("/stages/{code}/move") {
            val tenant = call.manageTenant() ?: return@post
            val code = call.stageCodeParam() ?: return@post
            val after = StageCode.parseOrNull(call.jsonBody()?.string("afterCode"))
                ?: return@post call.respond(HttpStatusCode.BadRequest, "afterCode wajib diisi")
            call.respondFlow(moveStage(tenant.tenantId, code, after, tenant.industryTemplate))
        }

        patch("/stages/{code}") {
            val tenant = call.manageTenant() ?: return@patch
            val code = call.stageCodeParam() ?: return@patch
            val body = call.jsonBody() ?: return@patch
            val name = body.string("displayName")?.takeIf { it.isNotBlank() }
                ?: return@patch call.respond(HttpStatusCode.BadRequest, "Nama tahap wajib diisi")
            call.respondFlow(renameStage(tenant.tenantId, code, name, body.string("shortLabel"), tenant.industryTemplate))
        }

        post("/reset") {
            val tenant = call.manageTenant() ?: return@post
            val template = IndustryTemplateCode.parseOrNull(call.jsonBody()?.string("template"))
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Template industri tidak dikenal")
            call.respondFlow(resetStageFlow(tenant.tenantId, template))
        }
    }
}

private suspend fun ApplicationCall.jsonBody(): JsonValue.Obj? =
    (runCatching { JsonParser.parse(receiveText()) }.getOrNull() as? JsonValue.Obj)
        ?: run { respond(HttpStatusCode.BadRequest, "Body JSON tidak valid"); null }

private suspend fun ApplicationCall.stageCodeParam(): StageCode? =
    StageCode.parseOrNull(parameters["code"]) ?: run { respond(HttpStatusCode.BadRequest, "Kode tahap tidak valid"); null }

/** 409 untuk tahap yang masih menjadi jangkar proses; 400 untuk pelanggaran aturan kerangka. */
private suspend fun ApplicationCall.respondFlow(result: Result<TenantStageFlow>) {
    result
        .onSuccess { respondText(TenantStageFlowCodec.encode(it).encode(), ContentType.Application.Json) }
        .onFailure { error ->
            when (error) {
                is StageHasProcessesException -> respondText(
                    jsonObjectOf(
                        "error" to jsonOf(error.message),
                        "code" to jsonOf("STAGE_HAS_PROCESSES"),
                        "processes" to jsonArrayOf(error.processNames.map { jsonOf(it) })
                    ).encode(),
                    ContentType.Application.Json,
                    HttpStatusCode.Conflict
                )
                is IllegalArgumentException -> respond(HttpStatusCode.BadRequest, error.message ?: "Perubahan kerangka ditolak")
                else -> respond(HttpStatusCode.InternalServerError, error.message ?: "Gagal memproses kerangka alur")
            }
        }
}

/**
 * Aturan fail-closed penulisan kerangka saat keputusan RBAC tak bisa dihitung. Dengan keputusan,
 * [requireFactoryFlowAccess] yang menilai; tanpa keputusan, hanya peran yang membawa
 * [Permission.MANAGE_TENANT]. Dipisah murni supaya aturan keamanannya teruji tanpa HTTP.
 */
internal fun mayEditWithoutDecision(decision: AccessDecision?, role: Role?): Boolean =
    decision != null || role?.defaultPermissions?.contains(Permission.MANAGE_TENANT) == true

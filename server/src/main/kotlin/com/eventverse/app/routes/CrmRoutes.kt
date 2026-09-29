package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.ContactRepository
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.usecases.DemoteQualifiedLeadUseCase
import com.eventverse.app.domain.deal.usecases.QualifyLeadUseCase
import com.eventverse.app.domain.invoicing.InvoiceRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.crm.LeadActivityRepository
import com.eventverse.app.domain.crm.usecases.AddLeadActivityUseCase
import com.eventverse.app.domain.crm.usecases.GetLeadActivitiesUseCase
import com.eventverse.app.domain.crm.usecases.ArchiveLeadUseCase
import com.eventverse.app.domain.crm.usecases.CreateLeadUseCase
import com.eventverse.app.domain.crm.usecases.GetLeadFormSchemaUseCase
import com.eventverse.app.domain.crm.usecases.LeadConflictException
import com.eventverse.app.domain.crm.usecases.LeadPatch
import com.eventverse.app.domain.crm.usecases.LeadValidationException
import com.eventverse.app.domain.crm.usecases.ListLeadsUseCase
import com.eventverse.app.domain.crm.usecases.Optional
import com.eventverse.app.domain.crm.usecases.UpdateLeadStageUseCase
import com.eventverse.app.domain.crm.usecases.UpdateLeadUseCase
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.CustomFieldValidationError
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.customfield.usecases.AddCustomFieldDefinitionUseCase
import com.eventverse.app.domain.customfield.usecases.ArchiveCustomFieldDefinitionUseCase
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.PostgresLeadActivityRepository
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.crm.CrmLeadCodec
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * REST surface for CRM Leads. Six enforcement points on every route, in order, matching
 * [CrmAccessGuard]'s KDoc: tenant (RLS + repository predicate) -> entitlement (folded into
 * the access decision) -> level (`requireCrmAccess`) -> read scope (pushed into
 * [CrmLeadRepository.findActive] as SQL, never filtered in memory) -> write scope
 * (`requireReachableOwner`) -> per-row lookup always scoped by tenant.
 */
fun Route.crmRoutes(
    leadRepository: CrmLeadRepository,
    contactRepository: ContactRepository,
    dealRepository: DealRepository,
    customFieldRepository: CustomFieldDefinitionRepository,
    employeeRepository: EmployeeRepository,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    invoiceRepository: InvoiceRepository,
    leadActivityRepository: LeadActivityRepository = PostgresLeadActivityRepository()
) {
    val listLeadsUseCase = ListLeadsUseCase(leadRepository)
    val createLeadUseCase = CreateLeadUseCase(leadRepository, customFieldRepository)
    val updateLeadUseCase = UpdateLeadUseCase(leadRepository, customFieldRepository)
    val updateLeadStageUseCase = UpdateLeadStageUseCase(leadRepository)
    // Qualification replaces the plain stage write when the target stage is QUALIFIED:
    // lead + contact + deal are created in ONE server-side transaction (the route wraps
    // the call in DatabaseFactory.dbQuery) so the three writes commit or fail together.
    val qualifyLeadUseCase = QualifyLeadUseCase(leadRepository, contactRepository, dealRepository)
    // Demosi bersyarat: QUALIFIED -> NEW_LEAD hanya boleh bila deal-nya masih "bersih";
    // kalau ya, deal ikut diarsipkan supaya tidak ada transaksi yatim di balik lead baru.
    val demoteQualifiedLeadUseCase = DemoteQualifiedLeadUseCase(leadRepository, dealRepository, contactRepository, invoiceRepository)
    val archiveLeadUseCase = ArchiveLeadUseCase(leadRepository)
    val getLeadFormSchemaUseCase = GetLeadFormSchemaUseCase(customFieldRepository)
    val addCustomFieldUseCase = AddCustomFieldDefinitionUseCase(customFieldRepository)
    val archiveCustomFieldUseCase = ArchiveCustomFieldDefinitionUseCase(customFieldRepository)
    val addLeadActivityUseCase = AddLeadActivityUseCase(leadActivityRepository)
    val getLeadActivitiesUseCase = GetLeadActivitiesUseCase(leadActivityRepository)
    val getCrmLeadKpiMetricsUseCase = com.eventverse.app.domain.crm.usecases.GetCrmLeadKpiMetricsUseCase()

    route("/api/tenant/crm/leads") {

        get("/metrics") {
            val tenant = call.requireTenant() ?: return@get
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.VIEW)) return@get

            val scope = decision.config.sanitizeFor(GarmentModules.CRM_SALES).scope
            val principal = call.callerPrincipalOrNull
            val viewerEmployeeId = principal?.email
                ?.let { email -> employeeRepository.findByEmail(tenant.tenantId, email) }
                ?.id
            val employees = employeeRepository.findAllByTenant(tenant.tenantId)

            listLeadsUseCase(tenant.tenantId, scope, employees, viewerEmployeeId, principal?.departmentId)
                .onSuccess { leads ->
                    val metrics = getCrmLeadKpiMetricsUseCase(leads, kotlinx.datetime.Clock.System.now())
                    call.respondJson(CrmLeadCodec.encodeKpiMetrics(metrics).encode())
                }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it) }
        }

        get("/schema") {
            val tenant = call.requireTenant() ?: return@get
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.VIEW)) return@get

            getLeadFormSchemaUseCase(tenant.tenantId)
                .onSuccess { fields -> call.respondJson(CrmLeadCodec.encodeSchema(fields)) }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it) }
        }

        get {
            val tenant = call.requireTenant() ?: return@get
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.VIEW)) return@get

            val scope = decision.config.sanitizeFor(GarmentModules.CRM_SALES).scope
            val principal = call.callerPrincipalOrNull
            val viewerEmployeeId = principal?.email
                ?.let { email -> employeeRepository.findByEmail(tenant.tenantId, email) }
                ?.id
            val employees = employeeRepository.findAllByTenant(tenant.tenantId)

            listLeadsUseCase(tenant.tenantId, scope, employees, viewerEmployeeId, principal?.departmentId)
                .onSuccess { leads -> call.respondJson(CrmLeadCodec.encodeLeads(leads)) }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it) }
        }

        post {
            val tenant = call.requireTenant() ?: return@post
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@post

            val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
            val req = CrmLeadCodec.decodeCreateRequest(call.receiveText())
            if (!call.requireReachableOwner(reach, req.ownerEmployeeId)) return@post

            createLeadUseCase(
                tenantId = tenant.tenantId,
                brandName = req.brandName,
                contactPerson = req.contactPerson,
                whatsappNumber = req.whatsappNumber,
                email = req.email,
                stage = req.stage,
                source = req.source,
                estimatedPcs = req.estimatedPcs,
                estimatedValue = req.estimatedValue,
                ownerEmployeeId = req.ownerEmployeeId,
                expectedCloseDate = req.expectedCloseDate,
                productCategory = req.productCategory,
                customValues = req.customValues,
                createdByUserId = call.callerPrincipalOrNull?.userId,
                newId = { "lead-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}-${(100..999).random()}" }
            ).onSuccess { lead ->
                call.respondText(
                    text = CrmLeadCodec.encodeLead(lead).encode(),
                    status = HttpStatusCode.Created,
                    contentType = ContentType.Application.Json
                )
            }.onFailure { call.respondValidationOrFailure(it) }
        }

        route("/{id}") {
            patch {
                val tenant = call.requireTenant() ?: return@patch
                val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
                if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@patch

                val leadId = LeadId(call.parameters["id"]!!)
                val existing = leadRepository.findById(tenant.tenantId, leadId)
                if (existing == null) {
                    call.respond(HttpStatusCode.NotFound, "Lead not found")
                    return@patch
                }

                val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
                if (!call.requireReachableOwner(reach, existing.ownerEmployeeId)) return@patch

                val req = CrmLeadCodec.decodePatchRequest(call.receiveText())
                if (req.ownerEmployeeIdSet && !call.requireReachableOwner(reach, req.ownerEmployeeId)) return@patch

                val patch = LeadPatch(
                    brandName = req.brandName,
                    contactPerson = req.contactPerson,
                    whatsappNumber = req.whatsappNumber,
                    email = req.email,
                    source = req.source,
                    estimatedPcs = req.estimatedPcs,
                    estimatedValue = req.estimatedValue,
                    ownerEmployeeId = if (req.ownerEmployeeIdSet) Optional(req.ownerEmployeeId) else null,
                    expectedCloseDate = if (req.expectedCloseDateSet) Optional(req.expectedCloseDate) else null,
                    customValues = req.customValues
                )

                updateLeadUseCase(tenant.tenantId, leadId, patch, req.expectedUpdatedAt)
                    .onSuccess { lead -> call.respondJson(CrmLeadCodec.encodeLead(lead).encode()) }
                    .onFailure { call.respondValidationOrFailure(it) }
            }

            post("/stage") {
                val tenant = call.requireTenant() ?: return@post
                val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
                if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@post

                val leadId = LeadId(call.parameters["id"]!!)
                val existing = leadRepository.findById(tenant.tenantId, leadId)
                if (existing == null) {
                    call.respond(HttpStatusCode.NotFound, "Lead not found")
                    return@post
                }
                val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
                if (!call.requireReachableOwner(reach, existing.ownerEmployeeId)) return@post

                val body = com.eventverse.app.shared.json.JsonParser.parseObject(call.receiveText())
                val newStage = body.string("stage")?.let { LeadStage.fromCode(it) }
                if (newStage == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing 'stage'")
                    return@post
                }

                if (existing.stage == LeadStage.QUALIFIED &&
                    (newStage == LeadStage.NEW_LEAD || newStage == LeadStage.FOLLOW_UP)
                ) {
                    // Demosi bersyarat di dalam satu transaksi: bila deal-nya masih bersih,
                    // deal ikut diarsipkan; bila sudah ada PO/invoice, ditolak dengan pesan.
                    DatabaseFactory.dbQuery(tenant.tenantId) {
                        demoteQualifiedLeadUseCase(tenant.tenantId, leadId, target = newStage)
                    }.onSuccess { demotion ->
                        call.respondJson(CrmLeadCodec.encodeLead(demotion.lead).encode())
                    }.onFailure { call.respondFailure(HttpStatusCode.Conflict, it) }
                    return@post
                }

                if (newStage == LeadStage.QUALIFIED) {
                    // Qualification is transactional: contact + deal + lead transition all
                    // commit together (nested dbQuery calls join this outer transaction).
                    DatabaseFactory.dbQuery(tenant.tenantId) {
                        qualifyLeadUseCase(tenant.tenantId, leadId, newId = { newId("deal") })
                    }.onSuccess { qualification ->
                        // Lead JSON extended with the deal handshake the client navigates on.
                        val payload = CrmLeadCodec.encodeLead(qualification.lead).entries +
                            mapOf(
                                "dealId" to com.eventverse.app.shared.json.jsonOf(qualification.deal.id.value),
                                "dealAlreadyExisted" to com.eventverse.app.shared.json.jsonOf(qualification.dealAlreadyExisted)
                            )
                        call.respondJson(
                            com.eventverse.app.shared.json.JsonValue.Obj(
                                java.util.LinkedHashMap(payload)
                            ).encode()
                        )
                    }.onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
                    return@post
                }

                updateLeadStageUseCase(tenant.tenantId, leadId, newStage)
                    .onSuccess { lead -> call.respondJson(CrmLeadCodec.encodeLead(lead).encode()) }
                    .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
            }

            delete {
                val tenant = call.requireTenant() ?: return@delete
                val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
                if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@delete

                val leadId = LeadId(call.parameters["id"]!!)
                val existing = leadRepository.findById(tenant.tenantId, leadId)
                if (existing == null) {
                    call.respond(HttpStatusCode.NotFound, "Lead not found")
                    return@delete
                }
                val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
                if (!call.requireReachableOwner(reach, existing.ownerEmployeeId)) return@delete

                archiveLeadUseCase(tenant.tenantId, leadId)
                    .onSuccess { call.respond(HttpStatusCode.NoContent) }
                    .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it) }
            }

            get("/activities") {
                val tenant = call.requireTenant() ?: return@get
                val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
                if (!call.requireCrmAccess(decision, AccessLevel.VIEW)) return@get

                val leadIdStr = call.parameters["id"]
                if (leadIdStr.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Lead ID required")
                    return@get
                }

                getLeadActivitiesUseCase(tenant.tenantId, LeadId(leadIdStr))
                    .onSuccess { activities -> call.respondJson(CrmLeadCodec.encodeActivities(activities)) }
                    .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it) }
            }

            post("/activities") {
                val tenant = call.requireTenant() ?: return@post
                val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
                if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@post

                val leadIdStr = call.parameters["id"]
                if (leadIdStr.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Lead ID required")
                    return@post
                }

                val body = call.receiveText()
                val obj = com.eventverse.app.shared.json.JsonParser.parseObjectOrNull(body)
                val content = obj?.string("content")?.trim()
                if (content.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Catatan aktivitas tidak boleh kosong")
                    return@post
                }

                val principal = call.callerPrincipalOrNull
                val employee = principal?.email?.let { employeeRepository.findByEmail(tenant.tenantId, it) }
                val authorName = employee?.name?.ifBlank { null }
                    ?: principal?.email?.substringBefore('@')?.ifBlank { null }
                    ?: "Sales"

                addLeadActivityUseCase(
                    tenantId = tenant.tenantId,
                    leadId = LeadId(leadIdStr),
                    authorEmployeeId = employee?.id,
                    authorName = authorName,
                    content = content
                )
                    .onSuccess { activity ->
                        call.respondJson(CrmLeadCodec.encodeActivity(activity).encode())
                    }
                    .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
            }
        }
    }

    // Custom field schema management — MANAGE only. "Tenant admins can edit columns" maps to
    // AccessLevel.MANAGE on the module, the same level that already means "hak penuh
    // termasuk approval, hapus data" — no separate canEditSchema authority axis.
    route("/api/tenant/crm/fields") {
        post {
            val tenant = call.requireTenant() ?: return@post
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.MANAGE)) return@post

            val req = CrmLeadCodec.decodeAddFieldRequest(call.receiveText())
            val type = com.eventverse.app.domain.customfield.CustomAttributesCodec.decodeFieldType(req.typeCode, req.config)
            if (type == null) {
                call.respond(HttpStatusCode.BadRequest, "Unknown field type: ${req.typeCode}")
                return@post
            }
            if (type is FieldType.UserRef) {
                // See PostgresCrmLeadRepository's documented gap: custom UserRef fields are
                // not yet wired to custom_field_links. Refuse rather than silently accepting
                // a field whose values would only ever live in the JSONB blob.
                call.respond(HttpStatusCode.BadRequest, "Tipe USER_REF untuk custom field belum didukung pada fase ini")
                return@post
            }

            addCustomFieldUseCase(
                tenantId = tenant.tenantId,
                ownerResource = OwnerResource.CRM_SALES,
                label = req.label,
                type = type,
                isRequired = req.isRequired,
                newId = { "cf-${tenant.tenantId.value}-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}" }
            ).onSuccess { def ->
                call.respondText(
                    text = com.eventverse.app.domain.customfield.CustomAttributesCodec.encodeDefinition(def).encode(),
                    status = HttpStatusCode.Created,
                    contentType = ContentType.Application.Json
                )
            }.onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }

        delete("/{id}") {
            val tenant = call.requireTenant() ?: return@delete
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.MANAGE)) return@delete

            val fieldIdParam = call.parameters["id"]
            if (fieldIdParam.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Field ID required")
                return@delete
            }

            val fieldId = CustomFieldId(fieldIdParam)
            archiveCustomFieldUseCase(tenant.tenantId, fieldId, kotlinx.datetime.Clock.System.now())
                .onSuccess { call.respond(HttpStatusCode.NoContent) }
                .onFailure { call.respondFailure(HttpStatusCode.BadRequest, it) }
        }
    }
}

private suspend fun ApplicationCall.requireTenant(): com.eventverse.app.domain.tenant.TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respond(HttpStatusCode.NotFound, "No tenant context found")
    }
    return tenant
}

private suspend fun ApplicationCall.respondJson(json: String) {
    respondText(text = json, contentType = ContentType.Application.Json)
}

private suspend fun ApplicationCall.respondFailure(status: HttpStatusCode, error: Throwable) {
    respond(status, error.message ?: "Unknown error")
}

/** Surfaces [LeadValidationException] and [LeadConflictException] with the right status codes. */
private suspend fun ApplicationCall.respondValidationOrFailure(error: Throwable) {
    when (error) {
        is LeadValidationException -> respondText(
            text = com.eventverse.app.shared.json.jsonObjectOf(
                "error" to com.eventverse.app.shared.json.jsonOf("VALIDATION"),
                "fields" to com.eventverse.app.shared.json.jsonArrayOf(
                    error.customFieldErrors.map { validationError ->
                        com.eventverse.app.shared.json.jsonObjectOf(
                            "fieldId" to com.eventverse.app.shared.json.jsonOf(validationError.fieldId.value),
                            "message" to com.eventverse.app.shared.json.jsonOf(describeValidationError(validationError))
                        )
                    }
                )
            ).encode(),
            status = HttpStatusCode.BadRequest,
            contentType = ContentType.Application.Json
        )
        is LeadConflictException -> respondText(
            text = CrmLeadCodec.encodeLead(error.current).encode(),
            status = HttpStatusCode.Conflict,
            contentType = ContentType.Application.Json
        )
        else -> respondFailure(HttpStatusCode.BadRequest, error)
    }
}

private fun describeValidationError(error: CustomFieldValidationError): String = when (error) {
    is CustomFieldValidationError.Required -> "\"${error.label}\" wajib diisi"
    is CustomFieldValidationError.TypeMismatch -> "\"${error.label}\" harus bertipe ${error.expected}"
    is CustomFieldValidationError.UnknownOption -> "\"${error.label}\" memilih opsi yang tidak dikenal"
    is CustomFieldValidationError.ArchivedField -> "\"${error.label}\" sudah tidak aktif"
}

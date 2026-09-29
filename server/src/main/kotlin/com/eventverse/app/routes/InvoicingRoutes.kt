package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.RoleRepository

import com.eventverse.app.domain.rbac.ModuleAssignmentRepository

import com.eventverse.app.domain.rbac.BusinessModule

import com.eventverse.app.domain.rbac.AccessLevel

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.domain.invoicing.usecases.*
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.infrastructure.pdf.InvoicePdfRenderer
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.invoicing.InvoiceCodec
import com.eventverse.app.shared.invoicing.InvoicePaymentCodec
import com.eventverse.app.shared.invoicing.InvoiceTemplateCodec
import com.eventverse.app.shared.json.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

fun Route.invoicingRoutes(
    invoiceRepository: InvoiceRepository,
    templateRepository: InvoiceTemplateRepository,
    paymentRepository: InvoicePaymentRepository,
    issuerProfileRepository: InvoiceIssuerProfileRepository,
    samplingOrderRepository: SamplingOrderRepository? = null,
    pdfRenderer: InvoicePdfRenderer = InvoicePdfRenderer(),
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository
) {
    val createInvoiceUseCase = CreateInvoiceUseCase(invoiceRepository, templateRepository, issuerProfileRepository)
    val updateInvoiceDraftUseCase = UpdateInvoiceDraftUseCase(invoiceRepository, templateRepository)
    val issueInvoiceUseCase = IssueInvoiceUseCase(invoiceRepository, templateRepository)
    val recordInvoicePaymentUseCase = RecordInvoicePaymentUseCase(invoiceRepository, paymentRepository)
    val voidInvoiceUseCase = VoidInvoiceUseCase(invoiceRepository, paymentRepository)
    val getInvoiceListUseCase = GetInvoiceListUseCase(invoiceRepository)
    val createSettlementUseCase = CreateSettlementFromDownPaymentUseCase(invoiceRepository)
    val prefillSamplingUseCase = PrefillInvoiceFromSamplingUseCase(invoiceRepository, templateRepository, issuerProfileRepository)

    route("/api/tenant/invoicing") {
        // B5: transaksi invoice = OPERATE; template & profil penerbit (identitas hukum di dokumen) = MANAGE.
        moduleGate(BusinessModule.INVOICING, roleRepository, moduleAssignmentRepository, write = AccessLevel.OPERATE) { method, path ->
            val isConfig = path.contains("/templates") || path.endsWith("/issuer-profile")
            if (method != io.ktor.http.HttpMethod.Get && isConfig) GateRule(AccessLevel.MANAGE, listOf(BusinessModule.INVOICING)) else null
        }

        // ── INVOICES API ──────────────────────────────────────────────────────────

        // GET /api/tenant/invoicing (List & Search)
        get {
            val tenant = call.requireTenant() ?: return@get
            val q = call.request.queryParameters["q"]?.takeIf { it.isNotBlank() }
            val status = call.request.queryParameters["status"]?.let { InvoiceStatus.fromCode(it) }
            val kind = call.request.queryParameters["kind"]?.let { InvoiceKind.fromCode(it) }
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

            val query = InvoiceQuery(
                tenantId = tenant.tenantId,
                status = status,
                kind = kind,
                searchQuery = q,
                page = page,
                pageSize = pageSize
            )

            val result = getInvoiceListUseCase(query)
            if (result.isSuccess) {
                val pageData = result.getOrThrow()
                val json = jsonObjectOf(
                    "items" to jsonArrayOf(pageData.items.map(InvoiceCodec::encode)),
                    "totalCount" to jsonOf(pageData.totalCount),
                    "page" to jsonOf(pageData.page),
                    "pageSize" to jsonOf(pageData.pageSize),
                    "totalPages" to jsonOf(pageData.totalPages)
                ).encode()
                call.respondJson(json)
            } else {
                call.respondFailure(HttpStatusCode.InternalServerError, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // POST /api/tenant/invoicing (Create Draft)
        post {
            val tenant = call.requireTenant() ?: return@post
            val body = call.receiveText()
            val json = JsonParser.parseObjectOrNull(body)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val kind = InvoiceKind.fromCode(json.string("kind"))
            val billTo = json.obj("billTo")?.let(InvoiceCodec::decodeBillTo)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Field 'billTo' wajib diisi")
            val lines = json.objectArray("lines").map(InvoiceCodec::decodeLine)
            val taxRatio = MeasureCodec.decodeRatio(json.obj("taxRatio"))
            val globalDiscount = MeasureCodec.decodeRatio(json.obj("globalDiscount"))
            val currencyStr = json.string("currency") ?: "IDR"
            val currency = runCatching { CurrencyCode.valueOf(currencyStr) }.getOrNull() ?: CurrencyCode.IDR
            val issueDate = DateTimeCodec.parseLocalDateOrFallback(json.string("issueDate"), LocalDate(2026, 3, 1))
            val dueDate = DateTimeCodec.parseLocalDateOrNull(json.string("dueDate"))
            val templateIdStr = json.string("templateId") ?: "tpl-std-id-001"
            val sourceKind = InvoiceSourceKind.fromCode(json.string("sourceKind"))
            val sourceRef = json.string("sourceRef")
            val parentInvoiceId = json.string("parentInvoiceId")?.let { InvoiceId(it) }
            val contractValue = json.obj("contractValue")?.let(MeasureCodec::decodeMoney)
            val notes = json.string("notes") ?: ""
            val terms = json.string("terms") ?: ""
            val caller = call.callerPrincipalOrNull?.userId ?: "system"

            val command = CreateInvoiceCommand(
                tenantId = tenant.tenantId,
                kind = kind,
                billTo = billTo,
                lines = lines,
                taxRatio = taxRatio,
                globalDiscount = globalDiscount,
                currency = currency,
                issueDate = issueDate,
                dueDate = dueDate,
                templateId = InvoiceTemplateId(templateIdStr),
                sourceKind = sourceKind,
                sourceRef = sourceRef,
                parentInvoiceId = parentInvoiceId,
                contractValue = contractValue,
                notes = notes,
                terms = terms,
                createdBy = caller
            )

            val result = createInvoiceUseCase(command)
            if (result.isSuccess) {
                call.respondJson(InvoiceCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // GET /api/tenant/invoicing/{id} (Detail)
        get("/{id}") {
            val tenant = call.requireTenant() ?: return@get
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
            // Pencarian sudah dibatasi tenant di lapisan repository, jadi faktur milik tenant lain
            // tidak pernah sampai ke sini. Perbandingan manual yang dulu ada di bawah baris ini
            // sengaja dihapus: ia hanya ada di rute ini dan terlupa di tujuh rute lainnya.
            val invoice = invoiceRepository.findById(tenant.tenantId, InvoiceId(id))
                ?: return@get call.respond(HttpStatusCode.NotFound, "Invoice tidak ditemukan")

            call.respondJson(InvoiceCodec.encode(invoice).encode())
        }

        // PUT /api/tenant/invoicing/{id} (Update Draft)
        put("/{id}") {
            val tenant = call.requireTenant() ?: return@put
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val body = call.receiveText()
            val json = JsonParser.parseObjectOrNull(body)
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val kind = InvoiceKind.fromCode(json.string("kind"))
            val billTo = json.obj("billTo")?.let(InvoiceCodec::decodeBillTo)
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Field 'billTo' wajib diisi")
            val lines = json.objectArray("lines").map(InvoiceCodec::decodeLine)
            val taxRatio = MeasureCodec.decodeRatio(json.obj("taxRatio"))
            val globalDiscount = MeasureCodec.decodeRatio(json.obj("globalDiscount"))
            val issueDate = DateTimeCodec.parseLocalDateOrFallback(json.string("issueDate"), LocalDate(2026, 3, 1))
            val dueDate = DateTimeCodec.parseLocalDateOrNull(json.string("dueDate"))
            val templateIdStr = json.string("templateId") ?: "tpl-std-id-001"
            val notes = json.string("notes") ?: ""
            val terms = json.string("terms") ?: ""

            val command = UpdateInvoiceDraftCommand(
                tenantId = tenant.tenantId,
                invoiceId = InvoiceId(id),
                billTo = billTo,
                kind = kind,
                lines = lines,
                taxRatio = taxRatio,
                globalDiscount = globalDiscount,
                issueDate = issueDate,
                dueDate = dueDate,
                templateId = InvoiceTemplateId(templateIdStr),
                notes = notes,
                terms = terms
            )

            val result = updateInvoiceDraftUseCase(command)
            if (result.isSuccess) {
                call.respondJson(InvoiceCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // POST /api/tenant/invoicing/{id}/issue (Issue & Freeze Snapshot)
        post("/{id}/issue") {
            val tenant = call.requireTenant() ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")
            val result = issueInvoiceUseCase(IssueInvoiceCommand(tenant.tenantId, InvoiceId(id)))
            if (result.isSuccess) {
                call.respondJson(InvoiceCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // POST /api/tenant/invoicing/{id}/void (Void Invoice)
        post("/{id}/void") {
            val tenant = call.requireTenant() ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")
            val body = call.receiveText()
            val json = JsonParser.parseObjectOrNull(body)
            val reason = json?.string("reason")?.trim() ?: "Dibatalkan oleh pengguna"

            val result = voidInvoiceUseCase(VoidInvoiceCommand(tenant.tenantId, InvoiceId(id), reason = reason))
            if (result.isSuccess) {
                call.respondJson(InvoiceCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // POST /api/tenant/invoicing/{id}/create-settlement (Generate Pelunasan from DP)
        post("/{id}/create-settlement") {
            val tenant = call.requireTenant() ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")
            val caller = call.callerPrincipalOrNull?.userId ?: "system"

            val command = CreateSettlementFromDownPaymentCommand(
                tenantId = tenant.tenantId,
                downPaymentInvoiceId = InvoiceId(id),
                issueDate = Clock.System.now().let { DateTimeCodec.parseLocalDateOrFallback(it.toString().substringBefore('T'), LocalDate(2026, 3, 1)) },
                dueDate = null,
                createdBy = caller
            )

            val result = createSettlementUseCase(command)
            if (result.isSuccess) {
                call.respondJson(InvoiceCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // ── PAYMENTS API ──────────────────────────────────────────────────────────

        // GET /api/tenant/invoicing/{id}/payments (Payment History)
        get("/{id}/payments") {
            val tenant = call.requireTenant() ?: return@get
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
            val payments = paymentRepository.historyFor(tenant.tenantId, InvoiceId(id))
            val json = jsonArrayOf(payments.map(InvoicePaymentCodec::encode)).encode()
            call.respondJson(json)
        }

        // POST /api/tenant/invoicing/{id}/payments (Record Payment)
        post("/{id}/payments") {
            val tenant = call.requireTenant() ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")
            val body = call.receiveText()
            val json = JsonParser.parseObjectOrNull(body)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val amount = MeasureCodec.decodeMoney(json.obj("amount"))
            val method = json.string("method") ?: "TRANSFER"
            val reference = json.string("reference") ?: ""
            val note = json.string("note") ?: ""
            val caller = call.callerPrincipalOrNull?.userId ?: "finance"

            val command = RecordInvoicePaymentCommand(
                tenantId = tenant.tenantId,
                invoiceId = InvoiceId(id),
                amount = amount,
                method = method,
                reference = reference,
                note = note,
                recordedBy = caller
            )

            val result = recordInvoicePaymentUseCase(command)
            if (result.isSuccess) {
                call.respondJson(InvoicePaymentCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }

        // ── PDF EXPORT API ────────────────────────────────────────────────────────

        // GET /api/tenant/invoicing/{id}/pdf (Download / Preview PDF)
        get("/{id}/pdf") {
            val tenant = call.requireTenant() ?: return@get
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
            val invoice = invoiceRepository.findById(tenant.tenantId, InvoiceId(id))
                ?: return@get call.respond(HttpStatusCode.NotFound, "Invoice tidak ditemukan")

            val template = invoice.renderedTemplate
                ?: templateRepository.findById(tenant.tenantId, invoice.templateId)
                ?: templateRepository.findDefault(tenant.tenantId)
                ?: InvoiceTemplateFactory.standardIndonesianInvoice(tenant.tenantId, invoice.createdAt)

            val totalPaid = paymentRepository.totalPaidFor(tenant.tenantId, invoice.id)
            val pdfBytes = pdfRenderer.render(invoice, template, totalPaid)

            val safeFilename = "${invoice.number.value.replace('/', '_')}.pdf"
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, safeFilename).toString()
            )
            call.respondBytes(pdfBytes, ContentType.Application.Pdf)
        }

        // ── TEMPLATES API ─────────────────────────────────────────────────────────

        // GET /api/tenant/invoicing/templates (List)
        get("/templates") {
            val tenant = call.requireTenant() ?: return@get
            val includeArchived = call.request.queryParameters["includeArchived"]?.toBooleanStrictOrNull() ?: false
            val templates = templateRepository.findAllByTenant(tenant.tenantId, includeArchived)
            val json = jsonArrayOf(templates.map(InvoiceTemplateCodec::encode)).encode()
            call.respondJson(json)
        }

        // GET /api/tenant/invoicing/templates/default (Get Default)
        get("/templates/default") {
            val tenant = call.requireTenant() ?: return@get
            val template = templateRepository.findDefault(tenant.tenantId)
                ?: InvoiceTemplateFactory.standardIndonesianInvoice(tenant.tenantId, Clock.System.now())
            call.respondJson(InvoiceTemplateCodec.encode(template).encode())
        }

        // GET /api/tenant/invoicing/templates/{id} (Get Detail)
        get("/templates/{id}") {
            val tenant = call.requireTenant() ?: return@get
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
            val template = templateRepository.findById(tenant.tenantId, InvoiceTemplateId(id))
                ?: return@get call.respond(HttpStatusCode.NotFound, "Template tidak ditemukan")
            call.respondJson(InvoiceTemplateCodec.encode(template).encode())
        }

        // POST /api/tenant/invoicing/templates (Create / Save)
        post("/templates") {
            val tenant = call.requireTenant() ?: return@post
            val body = call.receiveText()
            val json = JsonParser.parseObjectOrNull(body)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val template = InvoiceTemplateCodec.decode(json).copy(tenantId = tenant.tenantId)
            templateRepository.save(template)
            call.respondJson(InvoiceTemplateCodec.encode(template).encode())
        }

        // PUT /api/tenant/invoicing/templates/{id} (Update)
        put("/templates/{id}") {
            val tenant = call.requireTenant() ?: return@put
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val body = call.receiveText()
            val json = JsonParser.parseObjectOrNull(body)
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val template = InvoiceTemplateCodec.decode(json).copy(
                id = InvoiceTemplateId(id),
                tenantId = tenant.tenantId,
                updatedAt = Clock.System.now()
            )
            templateRepository.save(template)
            call.respondJson(InvoiceTemplateCodec.encode(template).encode())
        }

        // POST /api/tenant/invoicing/templates/{id}/set-default
        post("/templates/{id}/set-default") {
            val tenant = call.requireTenant() ?: return@post
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")
            templateRepository.setDefault(tenant.tenantId, InvoiceTemplateId(id))
            call.respond(HttpStatusCode.OK, "Template set as default")
        }

        // DELETE /api/tenant/invoicing/templates/{id} (Archive)
        delete("/templates/{id}") {
            val tenant = call.requireTenant() ?: return@delete
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")
            templateRepository.archive(tenant.tenantId, InvoiceTemplateId(id))
            call.respond(HttpStatusCode.OK, "Template archived")
        }

        // ── ISSUER PROFILE API ────────────────────────────────────────────────────

        // GET /api/tenant/invoicing/issuer-profile
        get("/issuer-profile") {
            val tenant = call.requireTenant() ?: return@get
            val profile = issuerProfileRepository.findByTenantId(tenant.tenantId)
                ?: IssuerProfile(companyName = "PT WeMade Garment")
            call.respondJson(InvoiceCodec.encodeIssuer(profile).encode())
        }

        // PUT /api/tenant/invoicing/issuer-profile
        put("/issuer-profile") {
            val tenant = call.requireTenant() ?: return@put
            val body = call.receiveText()
            val json = JsonParser.parseObjectOrNull(body)
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")

            val profile = InvoiceCodec.decodeIssuer(json)
            issuerProfileRepository.save(tenant.tenantId, profile)
            call.respondJson(InvoiceCodec.encodeIssuer(profile).encode())
        }

        // ── PREFILL FROM SAMPLING API ─────────────────────────────────────────────

        // POST /api/tenant/invoicing/prefill/sampling/{samplingId}
        post("/prefill/sampling/{samplingId}") {
            val tenant = call.requireTenant() ?: return@post
            val samplingId = call.parameters["samplingId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing samplingId")

            val samplingOrder = samplingOrderRepository?.findById(SamplingOrderId(samplingId))
            val styleName = samplingOrder?.styleName ?: "Sample Garment"
            val clientName = samplingOrder?.clientName ?: "Klien"
            val spkNumber = samplingOrder?.spkNumber?.value ?: samplingId

            val defaultTemplate = templateRepository.findDefault(tenant.tenantId)
                ?: InvoiceTemplateFactory.standardIndonesianInvoice(tenant.tenantId, Clock.System.now())

            val caller = call.callerPrincipalOrNull?.userId ?: "sales"

            val command = PrefillSamplingInvoiceCommand(
                tenantId = tenant.tenantId,
                spkNumber = spkNumber,
                styleName = styleName,
                clientName = clientName,
                templateId = defaultTemplate.id,
                issueDate = Clock.System.now().let { DateTimeCodec.parseLocalDateOrFallback(it.toString().substringBefore('T'), LocalDate(2026, 3, 1)) },
                createdBy = caller
            )

            val result = prefillSamplingUseCase(command)
            if (result.isSuccess) {
                call.respondJson(InvoiceCodec.encode(result.getOrThrow()).encode())
            } else {
                call.respondFailure(HttpStatusCode.BadRequest, result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        }
    }
}

private suspend fun ApplicationCall.requireTenant(): TenantContext? {
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

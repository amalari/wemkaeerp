package com.eventverse.app.routes

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.builder.ConfirmSubscriptionPaymentUseCase
import com.eventverse.app.domain.builder.CreateInvoiceCheckoutUseCase
import com.eventverse.app.domain.builder.PaymentGateway
import com.eventverse.app.domain.builder.IssueSubscriptionInvoiceUseCase
import com.eventverse.app.domain.builder.SubscriptionInvoice
import com.eventverse.app.domain.builder.SubscriptionInvoiceId
import com.eventverse.app.domain.builder.SubscriptionInvoiceRepository
import com.eventverse.app.domain.builder.TenantBillingPreviewSource
import com.eventverse.app.domain.builder.print.SubscriptionInvoicePdfDocument
import com.eventverse.app.domain.builder.print.SubscriptionInvoiceSheetLayout
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.infrastructure.auth.PrintTicketService
import com.eventverse.app.infrastructure.pdf.PrintLabels
import com.eventverse.app.infrastructure.pdf.SubscriptionInvoicePdfRenderer
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContext
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock

/**
 * Tagihan langganan platform (FR-M2-5, plan §6). Dibagi dua sisi yang sengaja tidak dicampur:
 *
 * - **Superadmin** menerbitkan invoice & mengonfirmasi pembayaran. Ter-audit
 *   ([AuditAction.BUILDER_INVOICE_ISSUED] / [_PAID]) karena ini uang dan berlaku lintas tenant.
 * - **Tenant** hanya membaca tagihannya sendiri, dengan gerbang Builder yang sama (`MANAGE_BUILDER`),
 *   dan hanya baris milik tenant pemanggil yang keluar — penyaringan dilakukan dari `tenantContext`,
 *   bukan dari parameter query yang bisa dipalsukan.
 *
 * PDF (FR-M2-5b) memakai mekanisme dua langkah yang sama dengan cetakan blueprint: tab browser tidak
 * bisa membawa header `Authorization`, jadi klien menukar sesinya dengan tiket ±60 detik yang
 * cakupannya **hanya satu invoice** dan hanya berkas `.pdf`.
 */
fun Route.builderBillingRoutes(
    invoices: SubscriptionInvoiceRepository,
    billingPreview: TenantBillingPreviewSource,
    auditLog: AuditLogRepository,
    tenants: TenantRepository,
    /** Gateway iPaymu (L1). `null` = belum dikonfigurasi → endpoint checkout menjawab 503. */
    ipaymuGateway: PaymentGateway? = null,
    printTickets: PrintTicketService = PrintTicketService(),
    invoiceRenderer: SubscriptionInvoicePdfRenderer = SubscriptionInvoicePdfRenderer()
) {
    val issue = IssueSubscriptionInvoiceUseCase(billingPreview, invoices)
    val confirm = ConfirmSubscriptionPaymentUseCase(invoices)
    val createCheckout = ipaymuGateway?.let { CreateInvoiceCheckoutUseCase(invoices, it) }

    route("/api/builder/billing/invoices") {
        // ---------------------------------------------------------------- sisi tenant
        get {
            call.gate() ?: return@get
            val own = invoices.findByTenant(call.tenantContext.tenantId)
            call.respondText(
                "{\"invoices\":[" + own.joinToString(",") { invoiceJson(it) } + "]}",
                ContentType.Application.Json
            )
        }


        // ---------------------------------------------------------------- sisi superadmin
        post {
            call.superadminGate() ?: return@post
            val tenantIdRaw = call.request.queryParameters["tenantId"]
            if (tenantIdRaw.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "tenantId wajib diisi")
                return@post
            }
            val period = call.request.queryParameters["period"] ?: currentPeriod()
            val tenantId = TenantId(tenantIdRaw)
            issue(tenantId, period).fold(
                onSuccess = { invoice ->
                    call.auditBilling(
                        auditLog, AuditAction.BUILDER_INVOICE_ISSUED, tenantId,
                        "invoice ${invoice.number} terbit (total ${invoice.totalIdr.amount})"
                    )
                    call.respondText(invoiceJson(invoice), ContentType.Application.Json)
                },
                onFailure = { e ->
                    call.respond(HttpStatusCode.Conflict, "Penerbitan invoice gagal: ${e.message}")
                }
            )
        }

        post("/{id}/confirm") {
            call.superadminGate() ?: return@post
            val id = SubscriptionInvoiceId(call.parameters["id"] ?: "")
            // Catatan pembayaran datang dari luar: dipotong, bukan dipercaya apa adanya.
            val note = call.request.queryParameters["note"]?.take(200)
            confirm(id, note).fold(
                onSuccess = { invoice ->
                    call.auditBilling(
                        auditLog, AuditAction.BUILDER_INVOICE_PAID, invoice.tenantId,
                        "invoice ${invoice.number} lunas"
                    )
                    call.respondText(invoiceJson(invoice), ContentType.Application.Json)
                },
                onFailure = { e -> call.respond(HttpStatusCode.Conflict, "Konfirmasi gagal: ${e.message}") }
            )
        }

        post("/{id}/checkout") {
            // Gerbang superadmin (gate builder): membuat transaksi iPaymu = operasi berbayar
            // lintas tenant; tenant tidak boleh memicunya untuk invoice miliknya sendiri.
            call.superadminGate() ?: return@post
            if (createCheckout == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    "Payment gateway belum dikonfigurasi (isi IPAYMU_VA / IPAYMU_API_KEY / IPAYMU_BASE_URL)"
                )
                return@post
            }
            val id = SubscriptionInvoiceId(call.parameters["id"] ?: "")
            createCheckout(id).fold(
                onSuccess = { result ->
                    call.auditBilling(
                        auditLog, AuditAction.BUILDER_INVOICE_ISSUED, result.invoice.tenantId,
                        "checkout iPaymu invoice ${result.invoice.number} (trx ${result.invoice.ipaymuTrxId})"
                    )
                    call.respondText(
                        "{\"invoice\":${invoiceJson(result.invoice)}," +
                            "\"paymentUrl\":${result.paymentUrl?.let { "\"$it\"" } ?: "null"}}",
                        ContentType.Application.Json
                    )
                },
                onFailure = { e -> call.respond(HttpStatusCode.Conflict, "Checkout gagal: ${e.message}") }
            )
        }

        // ---------------------------------------------------------------- cetak (FR-M2-5b)
        /**
         * Tiket cetak untuk membuka PDF di tab browser. Yang diterbitkan bukan data, melainkan tiket
         * berumur 60 detik yang cakupannya hanya invoice ini.
         */
        post("/{id}/print-ticket") {
            val principal = call.callerPrincipalOrNull
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Autentikasi diperlukan")
            val invoice = call.invoiceForPdf(invoices, printTickets) ?: return@post

            val ticket = printTickets.issue(
                subject = principal.userId,
                tenantId = invoice.tenantId,
                scopePath = invoicePdfScope(invoice.id),
                platformSuperadmin = principal.isPlatformSuperadmin
            )
            call.response.header(HttpHeaders.CacheControl, "private, no-store")
            call.respondText(
                jsonObjectOf("ticket" to jsonOf(ticket)).encode(),
                ContentType.Application.Json
            )
        }

        /**
         * PDF tagihan: Bearer seperti endpoint lain, atau `?ticket=`.
         *
         * Berakhiran `.pdf` **bukan** gaya penulisan: `PrintTicketService.decode` menolak tiket untuk
         * path yang tidak berakhiran `.pdf`, dan berkas inilah yang dibuka tab browser. Nama lain
         * (mis. `/pdf`) membuat setiap tiket yang diterbitkan di atas selalu ditolak plugin sebagai
         * "tiket tidak sah" — persis bug yang tertangkap test rute ini.
         *
         * `Cache-Control: private, no-store` bukan formalitas: berkas ini memuat susunan modul tenant
         * dan total tagihannya, dan salinan di cache proxy bersama adalah cara termudah
         * membocorkannya ke tenant lain.
         */
        get("/{id}/invoice.pdf") {
            val invoice = call.invoiceForPdf(invoices, printTickets) ?: return@get

            // Nama bisnis tenant diambil dari repositori; invoice hanya menyimpan id-nya. Tenant yang
            // sudah dihapus tidak menghalangi unduhan — id-nya dicetak sebagai gantinya, karena tagihan
            // yang tidak bisa diunduh justru masalah bagi kedua pihak.
            val tenantName = runCatching { tenants.findById(invoice.tenantId) }
                .getOrNull()?.name?.value
                ?: invoice.tenantId.value

            val document = SubscriptionInvoicePdfDocument.of(
                invoice = invoice,
                tenantName = tenantName,
                generatedAtLabel = PrintLabels.now(),
                issuedAtLabel = PrintLabels.ofEpochMillis(invoice.issuedAt?.toEpochMilliseconds()),
                paidAtLabel = PrintLabels.ofEpochMillis(invoice.paidAt?.toEpochMilliseconds())
            )

            call.response.header(HttpHeaders.CacheControl, "private, no-store")
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Inline
                    .withParameter(ContentDisposition.Parameters.FileName, "invoice-${invoice.number}.pdf")
                    .toString()
            )
            call.respondBytes(
                invoiceRenderer.render(SubscriptionInvoiceSheetLayout.layout(document)),
                ContentType.Application.Pdf
            )
        }
    }
}

/** Cakupan path tiket: `/api/builder/billing/invoices/{id}` — dipakai penerbit dan pemverifikasi. */
private fun invoicePdfScope(id: SubscriptionInvoiceId): String = "/api/builder/billing/invoices/${id.value}"

/**
 * Menyelesaikan invoice yang dituju **beserta gerbangnya**.
 *
 * `null` berarti sudah dijawab (401/403/404) dan pemanggil cukup `return` — pola yang sama dengan
 * `BlueprintPdfRoutes.mayAccessPdf`, dengan satu tambahan: invoice milik tenant lain dijawab **404,
 * bukan 403**. 403 akan memberi tahu penanya bahwa nomor invoice itu ada di tenant sebelah, dan
 * nomor invoice mudah ditebak (`INV-2026-09-001`).
 */
private suspend fun ApplicationCall.invoiceForPdf(
    invoices: SubscriptionInvoiceRepository,
    printTickets: PrintTicketService
): SubscriptionInvoice? {
    val id = SubscriptionInvoiceId(parameters["id"].orEmpty())
    val principal = callerPrincipalOrNull

    if (principal != null) {
        val visible = if (principal.isPlatformSuperadmin) {
            invoices.findAll()
        } else {
            gate() ?: return null
            invoices.findByTenant(tenantContext.tenantId)
        }
        return visible.firstOrNull { it.id == id } ?: notFoundInvoice()
    }

    // Tab browser tidak bisa membawa Bearer: tiket yang menggantikannya, dan tiket sudah membawa
    // tenant-nya sendiri sehingga pembacaan tetap terfilter.
    val ticket = request.queryParameters[PrintTicketService.QUERY_PARAM]
    if (ticket.isNullOrBlank()) {
        respond(HttpStatusCode.Unauthorized, "Autentikasi diperlukan")
        return null
    }
    val ticketTenant = printTickets.verify(ticket, request.path())
    if (ticketTenant == null || printTickets.verifyUser(ticket, request.path()) == null) {
        respond(HttpStatusCode.Forbidden, "Tiket cetak tidak berlaku untuk dokumen ini.")
        return null
    }
    return invoices.findByTenant(ticketTenant).firstOrNull { it.id == id } ?: notFoundInvoice()
}

private suspend fun ApplicationCall.notFoundInvoice(): SubscriptionInvoice? {
    respond(HttpStatusCode.NotFound, "Invoice tidak ditemukan")
    return null
}

/** Baris + total ikut keluar supaya tagihan bisa diperiksa tanpa memanggil endpoint lain. */
internal fun invoiceJson(invoice: SubscriptionInvoice): String = buildString {
    append("{\"id\":\"${invoice.id.value}\",\"number\":\"${invoice.number}\",")
    append("\"tenantId\":\"${invoice.tenantId.value}\",\"period\":\"${invoice.period}\",")
    append("\"status\":\"${invoice.status.name}\",\"totalIdr\":${invoice.totalIdr.amount},")
    append("\"issuedAt\":${invoice.issuedAt?.toString()?.let { "\"$it\"" } ?: "null"},")
    append("\"paidAt\":${invoice.paidAt?.toString()?.let { "\"$it\"" } ?: "null"},")
    append("\"ipaymuTrxId\":${invoice.ipaymuTrxId?.let { "\"$it\"" } ?: "null"},")
    append("\"lines\":[")
    append(
        invoice.lines.joinToString(",") { line ->
            "{\"moduleId\":\"${line.moduleId}\",\"displayName\":\"${line.displayName}\"," +
                "\"monthlyPrice\":${line.monthlyPrice.amount},\"kind\":\"${line.kind}\"}"
        }
    )
    append("]}")
}

/** Periode berjalan `YYYY-MM` untuk penerbitan tagihan bulan ini (parameter `period` menimpanya). */
internal fun currentPeriod(clock: Clock = Clock.System): String = clock.now().toString().substring(0, 7)

private suspend fun io.ktor.server.application.ApplicationCall.auditBilling(
    auditLog: AuditLogRepository,
    action: AuditAction,
    tenantId: TenantId,
    summary: String
) {
    val principal = callerPrincipalOrNull ?: return
    runCatching {
        auditLog.record(
            AuditLogEntry(
                id = "audit-$tenantId-${Clock.System.now().toEpochMilliseconds()}",
                actorUserId = principal.userId,
                actorRole = principal.role,
                targetTenantId = tenantId,
                action = action,
                summary = summary,
                occurredAt = Clock.System.now()
            )
        )
    }
}

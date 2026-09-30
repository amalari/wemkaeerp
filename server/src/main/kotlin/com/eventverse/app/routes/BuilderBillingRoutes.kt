package com.eventverse.app.routes

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.builder.ConfirmSubscriptionPaymentUseCase
import com.eventverse.app.domain.builder.IssueSubscriptionInvoiceUseCase
import com.eventverse.app.domain.builder.SubscriptionInvoice
import com.eventverse.app.domain.builder.SubscriptionInvoiceId
import com.eventverse.app.domain.builder.SubscriptionInvoiceRepository
import com.eventverse.app.domain.builder.TenantBillingPreviewSource
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
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
 * PDF belum dirender di sini (lihat discovery M2, FR-M2-5b): renderer invoice yang ada terikat pada
 * dokumen penjualan tenant, sedangkan ini dokumen platform. Endpoint mengembalikan JSON lengkap
 * (baris + total + status) sehingga UI dapat mencetak/mengunduh di tahap berikutnya.
 */
fun Route.builderBillingRoutes(
    invoices: SubscriptionInvoiceRepository,
    billingPreview: TenantBillingPreviewSource,
    auditLog: AuditLogRepository
) {
    val issue = IssueSubscriptionInvoiceUseCase(billingPreview, invoices)
    val confirm = ConfirmSubscriptionPaymentUseCase(invoices)

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
    }
}

/** Baris + total ikut keluar supaya tagihan bisa diperiksa tanpa memanggil endpoint lain. */
internal fun invoiceJson(invoice: SubscriptionInvoice): String = buildString {
    append("{\"id\":\"${invoice.id.value}\",\"number\":\"${invoice.number}\",")
    append("\"tenantId\":\"${invoice.tenantId.value}\",\"period\":\"${invoice.period}\",")
    append("\"status\":\"${invoice.status.name}\",\"totalIdr\":${invoice.totalIdr.amount},")
    append("\"issuedAt\":${invoice.issuedAt?.toString()?.let { "\"$it\"" } ?: "null"},")
    append("\"paidAt\":${invoice.paidAt?.toString()?.let { "\"$it\"" } ?: "null"},")
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

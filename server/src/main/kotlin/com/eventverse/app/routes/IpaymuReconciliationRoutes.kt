package com.eventverse.app.routes

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.builder.ReconcileSubscriptionInvoicesUseCase
import com.eventverse.app.domain.builder.SubscriptionInvoice
import com.eventverse.app.plugins.callerPrincipalOrNull
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.datetime.Clock

/**
 * Pemicu rekonsiliasi iPaymu (FR-PAY-3.3 butir 7) — sisi pull yang menutup callback hilang:
 * invoice ISSUED yang faktanya sudah dibayar tapi callback-nya tidak pernah sampai (tunnel
 * mati saat pembayaran) ditanyakan ulang ke API iPaymu dan dilunasi bila terbukti PAID.
 *
 * Path di bawah **`/api/admin`**: plugin resolusi tenant meloloskan prefix ini sebagai
 * administrasi platform — superadmin ter-autentikasi, **tanpa** konteks tenant, karena
 * rekonsiliasi melintasi semua tenant sekaligus. `superadminGate` tetap dipasang sebagai
 * lapisan kedua (fail-closed bila prefix berubah suatu hari).
 *
 * Gateway `null` (kredensial belum diisi) → 503 fail-closed, bukan diam-diam menjawab
 * "tidak ada yang direkonsiliasi". Scheduler otomatis (loop berkala) sengaja **belum**
 * dipasang: titik sambungnya (`Application.kt`) sedang dikerjakan alur lain — endpoint
 * manual ini sudah menutup kasus operasional, dan penjadwalan tinggal menambah satu
 * pemanggil use case yang sama (TRD-PAY-001 §6).
 */
fun Route.ipaymuReconciliationRoutes(
    reconcile: ReconcileSubscriptionInvoicesUseCase?,
    auditLog: AuditLogRepository
) {
    post("/api/admin/billing/reconcile") {
        call.superadminGate() ?: return@post
        if (reconcile == null) {
            call.respondText(
                "Payment gateway belum dikonfigurasi (isi IPAYMU_VA / IPAYMU_API_KEY / IPAYMU_BASE_URL)",
                ContentType.Application.Json,
                HttpStatusCode.ServiceUnavailable
            )
            return@post
        }

        reconcile().fold(
            onSuccess = { summary ->
                summary.confirmedInvoices.forEach { call.auditReconciledPayment(auditLog, it) }
                call.respondText(
                    buildString {
                        append("{\"checked\":${summary.checked}")
                        append(",\"confirmed\":${summary.confirmedInvoices.size}")
                        append(",\"stillPending\":${summary.stillPending}")
                        append(",\"expiredOrFailed\":${summary.expiredOrFailed}")
                        append(",\"failures\":[")
                        append(summary.failures.joinToString(",") { "\"${it.replace("\"", "'")}\"" })
                        append("]}")
                    },
                    ContentType.Application.Json
                )
            },
            onFailure = { e ->
                call.respondText(
                    "Rekonsiliasi gagal: ${e.message}",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        )
    }
}

private suspend fun ApplicationCall.auditReconciledPayment(
    auditLog: AuditLogRepository,
    invoice: SubscriptionInvoice
) {
    val principal = callerPrincipalOrNull ?: return
    runCatching {
        auditLog.record(
            AuditLogEntry(
                id = "audit-${invoice.tenantId.value}-${Clock.System.now().toEpochMilliseconds()}",
                actorUserId = principal.userId,
                actorRole = principal.role,
                targetTenantId = invoice.tenantId,
                action = AuditAction.BUILDER_INVOICE_PAID,
                summary = "invoice ${invoice.number} lunas via rekonsiliasi iPaymu " +
                    "(trx numerik ${invoice.ipaymuTrxNumeric}; callback tidak diterima)",
                occurredAt = Clock.System.now()
            )
        )
    }
}

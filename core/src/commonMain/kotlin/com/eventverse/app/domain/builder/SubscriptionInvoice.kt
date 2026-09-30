package com.eventverse.app.domain.builder

import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline

/** Status invoice langganan platform. Alur maju saja: DRAFT → ISSUED → PAID (atau VOID). */
enum class SubscriptionInvoiceStatus { DRAFT, ISSUED, PAID, VOID }

@JvmInline
value class SubscriptionInvoiceId(val value: String) {
    init { require(value.isNotBlank()) { "SubscriptionInvoiceId kosong" } }
}

/**
 * Satu baris invoice yang **harganya sudah terkunci**.
 *
 * Inilah bedanya tagihan dengan pratinjau: pratinjau (`TenantBillingPreview`) dihitung ulang setiap
 * dipanggil dan ikut berubah bila katalog dinaikkan, sedangkan baris di sini adalah salinan beku.
 * Kenaikan harga katalog berlaku untuk tagihan **berikutnya**, bukan untuk invoice yang sudah
 * diterbitkan — kalau tidak, angka di PDF yang sudah dikirim ke klien bisa berubah setelahnya.
 */
data class SubscriptionInvoiceLine(
    val moduleId: String,
    val displayName: String,
    val monthlyPrice: MoneyIdr,
    val kind: String
) {
    init { require(moduleId.isNotBlank()) { "SubscriptionInvoiceLine.moduleId kosong" } }
}

/**
 * Tagihan langganan bulanan satu tenant (PLAN-builder-console M2, FR-M2-5).
 *
 * `lines` + `totalIdr` adalah **snapshot harga saat diterbitkan** — bukan hasil hitung ulang. Kunci
 * `period` dalam format `YYYY-MM`. Nomor invoice deterministik per tenant (`INV-<periode>-<n>`) supaya
 * dua penerbitan di bulan yang sama tidak mungkin menabrak satu sama lain tanpa terlihat.
 */
data class SubscriptionInvoice(
    val id: SubscriptionInvoiceId,
    val tenantId: TenantId,
    val number: String,
    val period: String,
    val lines: List<SubscriptionInvoiceLine>,
    val totalIdr: MoneyIdr,
    val status: SubscriptionInvoiceStatus = SubscriptionInvoiceStatus.ISSUED,
    val issuedAt: Instant? = null,
    val paidAt: Instant? = null,
    val paidNote: String? = null
) {
    init { require(number.isNotBlank()) { "SubscriptionInvoice.number kosong" } }
    init { require(period.matches(Regex("\\d{4}-\\d{2}"))) { "period harus YYYY-MM, dapat '$period'" } }
    init { require(lines.isNotEmpty()) { "Invoice tanpa baris tidak boleh diterbitkan" } }
    init {
        require(status != SubscriptionInvoiceStatus.PAID || paidAt != null) {
            "Invoice PAID wajib punya waktu bayar"
        }
    }
}

/** Penyimpanan invoice langganan (tabel `builder.subscription_invoices`, V85, RLS per tenant). */
interface SubscriptionInvoiceRepository {
    suspend fun findByTenant(tenantId: TenantId): List<SubscriptionInvoice>

    /** Seluruh invoice lintas tenant — konsol superadmin. */
    suspend fun findAll(): List<SubscriptionInvoice>

    suspend fun save(invoice: SubscriptionInvoice): SubscriptionInvoice
}

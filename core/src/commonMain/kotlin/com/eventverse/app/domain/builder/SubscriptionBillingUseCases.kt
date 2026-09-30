package com.eventverse.app.domain.builder

import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.usecases.TenantBillingPreview
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Sumber harga tagihan. Port sengaja berupa antarmuka fungsional, bukan tipe konkret
 * `GetTenantBillingPreviewUseCase`: yang diuji tagihan adalah **bahwa harga dibekukan saat terbit**,
 * dan itu hanya bisa dibuktikan bila sumber harga dapat berubah di tengah test — sesuatu yang tidak
 * mungkin bila kelas preview (final) dipakai langsung.
 */
fun interface TenantBillingPreviewSource {
    suspend operator fun invoke(tenantId: TenantId): Result<TenantBillingPreview>
}

/**
 * Menerbitkan tagihan bulanan tenant dari **harga yang dibekukan saat penerbitan** (FR-M2-5).
 *
 * Kenapa membekukan, bukan menghitung ulang: [TenantBillingPreviewSource] selalu memakai harga
 * katalog hari ini. Kalau invoice disimpan sebagai "cara menghitung" alih-alih "hasil hitung", maka
 * harga katalog yang naik pekan depan akan mengubah total invoice bulan lalu — angka di PDF yang
 * sudah dikirim ke klien berubah sendiri. Karena itu baris preview **disalin** ke invoice di sini.
 *
 * Dua penolakan yang disengaja:
 *  - **Tenant tanpa baris tagihan** tidak diterbitkan invoice kosong (ambigu: apakah gratis, atau
 *    datanya salah?). Lebih baik gagal terlihat daripada tagihan Rp 0 yang tidak dijelaskan.
 *  - **Periode yang sama dua kali** ditolak selama invoice periode itu belum VOID — nomor ganda
 *    untuk satu bulan adalah gejala bug pemanggil, bukan keadaan yang perlu diakomodasi.
 */
class IssueSubscriptionInvoiceUseCase(
    private val billingPreview: TenantBillingPreviewSource,
    private val invoices: SubscriptionInvoiceRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(tenantId: TenantId, period: String): Result<SubscriptionInvoice> = runCatching {
        require(period.matches(Regex("\\d{4}-\\d{2}"))) { "periode harus YYYY-MM, dapat '$period'" }

        val existing = invoices.findByTenant(tenantId)
        val duplicate = existing.firstOrNull {
            it.period == period && it.status != SubscriptionInvoiceStatus.VOID
        }
        if (duplicate != null) {
            error("Periode $period sudah diterbitkan (${duplicate.number})")
        }

        val preview = billingPreview(tenantId).getOrThrow()
        val lines = preview.lines.map { line ->
            SubscriptionInvoiceLine(
                moduleId = line.moduleId,
                displayName = line.displayName,
                monthlyPrice = line.monthlyPrice,
                kind = line.kind.name
            )
        }
        require(lines.isNotEmpty()) {
            "Tidak ada baris tagihan untuk tenant ini — periksa modul aktif & harga katalog"
        }

        val sequence = existing.count { it.period == period } + 1
        invoices.save(
            SubscriptionInvoice(
                id = SubscriptionInvoiceId("inv-${tenantId.value}-$period-$sequence"),
                tenantId = tenantId,
                number = "INV-$period-${sequence.toString().padStart(3, '0')}",
                period = period,
                lines = lines,
                totalIdr = MoneyIdr.sum(lines.map { it.monthlyPrice }),
                status = SubscriptionInvoiceStatus.ISSUED,
                issuedAt = clock.now()
            )
        )
    }
}

/**
 * Mengonfirmasi pembayaran (manual, oleh superadmin — MVP tanpa payment gateway, plan §8).
 *
 * Idempoten **secara sengaja terlihat**: konfirmasi kedua pada invoice yang sudah PAID tidak
 * mengubah apa pun dan tetap sukses, karena superadmin yang mengklik dua kali (atau jaringan yang
 * mengulang request) tidak boleh mendapat error yang membuatnya mengira uangnya tidak tercatat.
 * Yang ditolak adalah konfirmasi atas invoice VOID — itu keputusan yang sudah dibatalkan.
 */
class ConfirmSubscriptionPaymentUseCase(
    private val invoices: SubscriptionInvoiceRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        invoiceId: SubscriptionInvoiceId,
        note: String? = null
    ): Result<SubscriptionInvoice> = runCatching {
        val invoice = invoices.findAll().firstOrNull { it.id == invoiceId }
            ?: error("Invoice '${invoiceId.value}' tidak ditemukan")

        when (invoice.status) {
            SubscriptionInvoiceStatus.PAID -> invoice
            SubscriptionInvoiceStatus.VOID -> error("Invoice ${invoice.number} sudah dibatalkan")
            SubscriptionInvoiceStatus.DRAFT,
            SubscriptionInvoiceStatus.ISSUED -> invoices.save(
                invoice.copy(
                    status = SubscriptionInvoiceStatus.PAID,
                    paidAt = clock.now(),
                    paidNote = note?.takeIf { it.isNotBlank() }
                )
            )
        }
    }
}

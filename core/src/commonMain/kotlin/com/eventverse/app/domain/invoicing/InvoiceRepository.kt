package com.eventverse.app.domain.invoicing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.template.InvoiceTemplate
import com.eventverse.app.domain.tenant.TenantId

data class InvoiceQuery(
    val tenantId: TenantId,
    val status: InvoiceStatus? = null,
    val kind: InvoiceKind? = null,
    val searchQuery: String? = null,
    val page: Int = 1,
    val pageSize: Int = 20
) {
    init {
        require(page >= 1) { "Halaman harus >= 1." }
        require(pageSize in 1..100) { "Ukuran halaman harus antara 1 s/d 100." }
    }
}

data class InvoicePage(
    val items: List<Invoice>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int
) {
    val totalPages: Int
        get() = if (totalCount == 0L) 1 else ((totalCount + pageSize - 1) / pageSize).toInt()
}

/**
 * ## Kenapa setiap pencarian per-ID menuntut [TenantId]
 *
 * Dokumen faktur diidentifikasi oleh ID yang bisa ditebak atau bocor. Selama `findById(id)` cukup
 * dipanggil dengan ID saja, satu-satunya yang menghalangi tenant A membaca — dan **mengubah** —
 * faktur tenant B adalah ingatan penulis rute untuk menambahkan perbandingan `tenantId` sesudahnya.
 * Ingatan itu terbukti gagal: dari delapan rute ber-`{id}` di modul ini, hanya satu yang
 * memeriksanya.
 *
 * RLS PostgreSQL tidak menutup celah itu di sini, karena dua sebab yang berlaku bersamaan:
 * kueri tanpa tenant berjalan di koneksi platform yang tidak menyetel `app.current_tenant_id`,
 * dan `apply_tenant_rls` tidak pernah memanggil `FORCE ROW LEVEL SECURITY` sehingga pemilik tabel
 * selalu melewatinya.
 *
 * Karena itu tenant menjadi **bagian dari tanda tangan**, bukan pemeriksaan sesudahnya. Rute yang
 * lupa tidak lagi menghasilkan kebocoran senyap; ia tidak bisa dikompilasi.
 */
interface InvoiceRepository {
    suspend fun findById(tenantId: TenantId, id: InvoiceId): Invoice?
    suspend fun findByNumber(tenantId: TenantId, number: InvoiceNumber): Invoice?
    suspend fun search(query: InvoiceQuery): InvoicePage
    suspend fun save(invoice: Invoice)
    suspend fun reserveNextNumber(tenantId: TenantId, kind: InvoiceKind, period: String): InvoiceNumber
    suspend fun deleteDraft(tenantId: TenantId, id: InvoiceId)

    /**
     * True when at least one invoice pointing at [sourceRef] (e.g. a DealId) has reached a
     * financially committed status — ISSUED, PARTIALLY_PAID, or PAID. DRAFT is deliberately
     * ignored (it can still be deleted) and VOID is ignored (it never became a claim).
     */
    suspend fun hasActiveInvoiceForSource(
        tenantId: TenantId,
        sourceKind: InvoiceSourceKind,
        sourceRef: String
    ): Boolean
}

interface InvoiceTemplateRepository {
    suspend fun findById(tenantId: TenantId, id: InvoiceTemplateId): InvoiceTemplate?
    suspend fun findAllByTenant(tenantId: TenantId, includeArchived: Boolean = false): List<InvoiceTemplate>
    suspend fun findDefault(tenantId: TenantId): InvoiceTemplate?
    suspend fun save(template: InvoiceTemplate)
    suspend fun setDefault(tenantId: TenantId, id: InvoiceTemplateId)
    suspend fun archive(tenantId: TenantId, id: InvoiceTemplateId)
}

interface InvoicePaymentRepository {
    suspend fun historyFor(tenantId: TenantId, invoiceId: InvoiceId): List<InvoicePayment>
    suspend fun append(tenantId: TenantId, payment: InvoicePayment)
    suspend fun totalPaidFor(tenantId: TenantId, invoiceId: InvoiceId): Money
}

interface InvoiceIssuerProfileRepository {
    suspend fun findByTenantId(tenantId: TenantId): IssuerProfile?
    suspend fun save(tenantId: TenantId, profile: IssuerProfile)
}

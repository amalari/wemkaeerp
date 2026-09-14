package com.eventverse.app.domain.invoicing

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.invoicing.template.InvoiceTemplate
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Agregat Dokumen Faktur / Invoice.
 *
 * Menerapkan aturan integritas dokumen legal:
 * - Mutasi hanya diperbolehkan selama berstatus [InvoiceStatus.DRAFT].
 * - Saat diterbitkan ([InvoiceStatus.ISSUED]), template tata letak dibekukan ([renderedTemplate])
 *   bersama snapshot profil penerbit ([issuer]) dan klien ([billTo]).
 */
data class Invoice(
    val id: InvoiceId,
    val tenantId: TenantId,
    val number: InvoiceNumber,
    val kind: InvoiceKind,
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    val billTo: BillToParty,
    val issuer: IssuerProfile,
    val lines: List<InvoiceLine> = emptyList(),
    val taxRatio: Ratio = Ratio.ZERO,
    val globalDiscount: Ratio = Ratio.ZERO,
    val currency: CurrencyCode = CurrencyCode.IDR,
    val issueDate: LocalDate,
    val dueDate: LocalDate? = null,
    val templateId: InvoiceTemplateId,
    val renderedTemplate: InvoiceTemplate? = null,
    val sourceKind: InvoiceSourceKind = InvoiceSourceKind.MANUAL,
    val sourceRef: String? = null,
    val parentInvoiceId: InvoiceId? = null,
    val contractValue: Money? = null,
    val notes: String = "",
    val terms: String = "",
    val voidReason: String? = null,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(!taxRatio.isNegative) { "Tarif pajak (PPN) tidak boleh negatif." }
        require(!globalDiscount.isNegative && globalDiscount <= Ratio.ONE) {
            "Diskon global harus berada di rentang 0% s/d 100%."
        }
        if (status != InvoiceStatus.DRAFT) {
            requireNotNull(renderedTemplate) {
                "Invoice ${number.value} berstatus '${status.displayName}' wajib memiliki snapshot template."
            }
        }
        if (parentInvoiceId != null) {
            require(kind == InvoiceKind.SETTLEMENT) {
                "Hanya invoice pelunasan (SETTLEMENT) yang dapat menunjuk invoice DP induk."
            }
        }
    }

    // ── Perhitungan Finansial Presisi ────────────────────────────────────────────────────────
    val subtotal: Money
        get() = lines.fold(Money.zero(currency)) { acc, line -> acc + line.amount }

    val discountAmount: Money
        get() = subtotal * globalDiscount

    val taxableBase: Money
        get() = subtotal - discountAmount

    val taxAmount: Money
        get() = taxableBase * taxRatio

    val total: Money
        get() = taxableBase + taxAmount

    // ── Mutasi Dokumen (Hanya saat DRAFT) ──────────────────────────────────────────────────────
    private fun checkMutable() {
        require(status.isMutable) {
            "Invoice ${number.value} sudah berstatus '${status.displayName}' dan tidak dapat diubah."
        }
    }

    fun addLine(line: InvoiceLine, at: Instant): Invoice {
        checkMutable()
        require(lines.none { it.id == line.id }) { "Baris invoice dengan ID '${line.id.value}' sudah ada." }
        return copy(lines = lines + line, updatedAt = at)
    }

    fun updateLine(line: InvoiceLine, at: Instant): Invoice {
        checkMutable()
        val exists = lines.any { it.id == line.id }
        require(exists) { "Baris invoice dengan ID '${line.id.value}' tidak ditemukan." }
        return copy(lines = lines.map { if (it.id == line.id) line else it }, updatedAt = at)
    }

    fun removeLine(lineId: InvoiceLineId, at: Instant): Invoice {
        checkMutable()
        return copy(lines = lines.filterNot { it.id == lineId }, updatedAt = at)
    }

    fun reorderLines(orderedLineIds: List<InvoiceLineId>, at: Instant): Invoice {
        checkMutable()
        val lineMap = lines.associateBy { it.id }
        val reordered = orderedLineIds.mapNotNull { lineMap[it] }
        require(reordered.size == lines.size) { "Daftar ID baris baru tidak lengkap." }
        val withSortOrders = reordered.mapIndexed { index, line -> line.copy(sortOrder = index) }
        return copy(lines = withSortOrders, updatedAt = at)
    }

    fun updateHeader(
        newBillTo: BillToParty,
        newKind: InvoiceKind,
        newTaxRatio: Ratio,
        newGlobalDiscount: Ratio,
        newIssueDate: LocalDate,
        newDueDate: LocalDate?,
        newNotes: String,
        newTerms: String,
        at: Instant
    ): Invoice {
        checkMutable()
        return copy(
            billTo = newBillTo,
            kind = newKind,
            taxRatio = newTaxRatio,
            globalDiscount = newGlobalDiscount,
            issueDate = newIssueDate,
            dueDate = newDueDate,
            notes = newNotes,
            terms = newTerms,
            updatedAt = at
        )
    }

    fun issue(template: InvoiceTemplate, at: Instant): Invoice {
        checkMutable()
        require(lines.isNotEmpty()) { "Invoice tidak dapat diterbitkan tanpa baris item." }
        require(total.isPositive) { "Invoice tidak dapat diterbitkan dengan total nol atau negatif." }
        return copy(
            status = InvoiceStatus.ISSUED,
            renderedTemplate = template,
            updatedAt = at
        )
    }

    fun void(reason: String, at: Instant): Invoice {
        require(status != InvoiceStatus.VOID) { "Invoice ${number.value} sudah berstatus batal (VOID)." }
        require(reason.isNotBlank()) { "Alasan pembatalan invoice wajib diisi." }
        return copy(
            status = InvoiceStatus.VOID,
            voidReason = reason.trim(),
            updatedAt = at
        )
    }

    fun evaluatePaymentStatus(paidAmount: Money, at: Instant): Invoice {
        if (status == InvoiceStatus.DRAFT || status == InvoiceStatus.VOID) return this
        val newStatus = when {
            paidAmount >= total -> InvoiceStatus.PAID
            paidAmount.isPositive -> InvoiceStatus.PARTIALLY_PAID
            else -> InvoiceStatus.ISSUED
        }
        return if (newStatus != status) copy(status = newStatus, updatedAt = at) else this
    }
}

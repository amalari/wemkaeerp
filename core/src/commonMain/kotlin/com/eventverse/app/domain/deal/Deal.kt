package com.eventverse.app.domain.deal

import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Agregat penjualan yang lahir dari kualifikasi sebuah [com.eventverse.app.domain.crm.CrmLead].
 *
 * Deal adalah pusat gravitasi transaksi: ia memegang [com.eventverse.app.domain.crm.Contact]
 * (pembeli), menerima PO klien, dan menjadi sumber penerbitan invoice
 * (`InvoiceSourceKind.DEAL`). Immutable; mutasi lewat fungsi domain di bawah.
 */
data class Deal(
    val id: DealId,
    val tenantId: TenantId,
    val contactId: com.eventverse.app.domain.crm.ContactId,
    val sourceLeadId: LeadId? = null,
    val title: DealTitle,
    val stage: DealStage = DealStage.OPEN,
    val estimatedValue: MoneyIdr? = null,
    val ownerEmployeeId: OrgNodeId? = null,
    val expectedCloseDate: LocalDate? = null,
    val notes: String = "",
    val createdByUserId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null
) {
    val isArchived: Boolean get() = archivedAt != null

    val isOpen: Boolean get() = stage == DealStage.OPEN

    /** Refuses an illegal transition per [DealStage.canTransitionTo] — the domain invariant. */
    fun transitionTo(newStage: DealStage, now: Instant): Result<Deal> = runCatching {
        require(stage.canTransitionTo(newStage)) {
            "Tidak bisa memindahkan deal dari ${stage.displayName} ke ${newStage.displayName}"
        }
        copy(stage = newStage, updatedAt = now)
    }

    fun reassignOwner(newOwner: OrgNodeId?, now: Instant): Deal =
        copy(ownerEmployeeId = newOwner, updatedAt = now)

    fun archive(now: Instant): Deal = copy(archivedAt = now, updatedAt = now)
}

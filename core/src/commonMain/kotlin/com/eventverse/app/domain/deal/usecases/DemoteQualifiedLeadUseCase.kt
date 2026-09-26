package com.eventverse.app.domain.deal.usecases

import com.eventverse.app.domain.crm.ContactRepository
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.invoicing.InvoiceRepository
import com.eventverse.app.domain.invoicing.InvoiceSourceKind
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Membawa lead QUALIFIED kembali ke NEW_LEAD dengan aturan demosi bersyarat:
 *
 * - Belum ada deal (atau deal sudah diarsipkan) → demosi biasa, tidak ada efek samping.
 * - Deal masih OPEN tanpa PO dan tanpa invoice aktif → demosi diizinkan dan deal
 *   IKUT diarsipkan (event `DealArchivedByLeadDemotion`) supaya tidak ada deal yatim
 *   yang menggantung di balik lead "New Lead".
 * - Deal sudah punya PO, invoice aktif, atau stage-nya sudah lewat OPEN → DITOLAK dengan
 *   pesan eksplisit; pemilik deal harus menyelesaikan/mengarsipkannya secara sadar lewat
 *   modul Deal, bukan diam-diam terpangkas oleh pergerakan kartu di CRM.
 *
 * ATOMISITAS: seperti `QualifyLeadUseCase`, use case ini buta transaksi — route wajib
 * membungkusnya dalam `DatabaseFactory.dbQuery(tenantId)`.
 */
class DemoteQualifiedLeadUseCase(
    private val leadRepository: CrmLeadRepository,
    private val dealRepository: DealRepository,
    private val contactRepository: ContactRepository? = null,
    private val invoiceRepository: InvoiceRepository? = null,
) {
    data class Demotion(
        val lead: CrmLead,
        /** Deal yang ikut diarsipkan; null bila tidak ada deal yang perlu disentuh. */
        val archivedDeal: Deal?,
        /**
         * True bila kontak ikut dihapus. Hanya kontak yang DIBUAT oleh kualifikasi lead ini
         * (sourceLeadId == leadId) DAN tidak lagi ditunjuk deal lain yang dihapus — kontak
         * milik order pertama / manual tidak pernah tersentuh.
         */
        val deletedContact: Boolean
    )

    suspend operator fun invoke(
        tenantId: TenantId,
        leadId: LeadId,
        now: Instant = Clock.System.now(),
        target: LeadStage = LeadStage.NEW_LEAD
    ): Result<Demotion> = runCatching {
        require(target == LeadStage.NEW_LEAD || target == LeadStage.FOLLOW_UP) {
            "Demosi hanya ke New Lead atau Follow Up, bukan ${target.displayName}."
        }
        val lead = requireNotNull(leadRepository.findById(tenantId, leadId)) {
            "Lead tidak ditemukan: ${leadId.value}"
        }
        require(lead.stage == LeadStage.QUALIFIED) {
            "Hanya lead berstatus QUALIFIED yang bisa didemosi ke New Lead."
        }

        val deal = dealRepository.findBySourceLeadId(tenantId, leadId)
        if (deal == null || deal.isArchived) {
            val demoted = lead.transitionTo(target, now).getOrThrow()
            leadRepository.save(demoted).getOrThrow()
            return@runCatching Demotion(demoted, archivedDeal = null, deletedContact = false)
        }

        require(deal.isOpen) {
            "Lead tidak bisa balik ke New Lead: deal \"${deal.title.value}\" sudah berstatus " +
                "${deal.stage.displayName}. Selesaikan atau arsipkan deal lewat modul Deal."
        }

        val purchaseOrders = dealRepository.findPurchaseOrders(tenantId, deal.id)
        require(purchaseOrders.isEmpty()) {
            "Lead tidak bisa balik ke New Lead: deal \"${deal.title.value}\" sudah memiliki " +
                "${purchaseOrders.size} purchase order (${purchaseOrders.joinToString { it.poNumber.value }})."
        }

        val hasActiveInvoice = invoiceRepository
            ?.hasActiveInvoiceForSource(tenantId, InvoiceSourceKind.DEAL, deal.id.value)
            ?: false
        require(!hasActiveInvoice) {
            "Lead tidak bisa balik ke New Lead: deal \"${deal.title.value}\" sudah memiliki invoice terbit."
        }

        val archived = deal.archive(now)
        dealRepository.save(archived).getOrThrow()

        // Contact cleanup — hanya untuk kontak hasil kualifikasi lead INI (bukan kontak
        // warisan order pertama / input manual), dan hanya bila tidak ada deal lain
        // (arsip termasuk — FK-nya tetap menunjuk) yang masih memakainya.
        var deletedContact = false
        val contact = contactRepository?.findById(tenantId, deal.contactId)
        if (contact != null && contact.sourceLeadId == leadId) {
            val referencedByOtherDeal = dealRepository.existsForContact(
                tenantId,
                deal.contactId,
                excludeDealId = deal.id
            )
            if (!referencedByOtherDeal) {
                deletedContact = contactRepository.delete(tenantId, contact.id)
            }
        }

        val demoted = lead.transitionTo(target, now).getOrThrow()
        leadRepository.save(demoted).getOrThrow()

        Demotion(demoted, archivedDeal = archived, deletedContact = deletedContact)
    }
}

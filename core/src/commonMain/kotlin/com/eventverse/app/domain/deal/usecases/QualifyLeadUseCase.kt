package com.eventverse.app.domain.deal.usecases

import com.eventverse.app.domain.crm.BrandName
import com.eventverse.app.domain.crm.Contact
import com.eventverse.app.domain.crm.ContactId
import com.eventverse.app.domain.crm.ContactRepository
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealId
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.deal.DealTitle
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Mengangkat satu CRM lead ke tahap QUALIFIED dan — dalam operasi bisnis yang sama —
 * memastikan ada [Contact] dan [Deal] yang menampungnya.
 *
 * ATOMISITAS: use case ini TIDAK membuka transaksi sendiri (domain layer buta infrastruktur).
 * Route server yang memanggilnya wajib membungkus panggilan ini dalam satu transaksi
 * tenant-scoped (`DatabaseFactory.dbQuery(tenantId)`) supaya lead + contact + deal
 * commit atau gagal bersama.
 *
 * IDEMPOTENSI (pengaman kedua): bila deal untuk `sourceLeadId` ini sudah ada (mis. transaksi
 * sebelumnya commit lalu klien timeout dan retry), use case TIDAK membuat duplikat —
 * ia mengembalikan pasangan contact+deal yang sudah ada dan sekalian merapikan stage lead
 * ke QUALIFIED bila tertinggal.
 */
class QualifyLeadUseCase(
    private val leadRepository: CrmLeadRepository,
    private val contactRepository: ContactRepository,
    private val dealRepository: DealRepository,
) {
    data class Qualification(
        val lead: CrmLead,
        val contact: Contact,
        val deal: Deal,
        /** True when the deal already existed (idempotent replay) and nothing new was created. */
        val dealAlreadyExisted: Boolean
    )

    suspend operator fun invoke(
        tenantId: TenantId,
        leadId: LeadId,
        now: Instant = Clock.System.now(),
        newId: () -> String
    ): Result<Qualification> = runCatching {
        val lead = requireNotNull(leadRepository.findById(tenantId, leadId)) {
            "Lead tidak ditemukan: ${leadId.value}"
        }

        val existingDeal = dealRepository.findBySourceLeadId(tenantId, leadId)
        if (existingDeal != null) {
            val contact = requireNotNull(contactRepository.findById(tenantId, existingDeal.contactId)) {
                "Deal ${existingDeal.id.value} menunjuk contact yang hilang: ${existingDeal.contactId.value}"
            }
            val ensuredLead = if (lead.stage == LeadStage.QUALIFIED) lead else {
                val transitioned = lead.transitionTo(LeadStage.QUALIFIED, now).getOrThrow()
                leadRepository.save(transitioned).getOrThrow()
            }
            return@runCatching Qualification(ensuredLead, contact, existingDeal, dealAlreadyExisted = true)
        }

        val qualified = lead.transitionTo(LeadStage.QUALIFIED, now).getOrThrow()
        leadRepository.save(qualified).getOrThrow()

        val contact = findOrCreateContact(tenantId, qualified, now, newId)

        val deal = Deal(
            id = DealId(newId()),
            tenantId = tenantId,
            contactId = contact.id,
            sourceLeadId = leadId,
            title = DealTitle(qualified.title),
            stage = DealStage.OPEN,
            estimatedValue = qualified.estimatedValue,
            ownerEmployeeId = qualified.ownerEmployeeId,
            expectedCloseDate = qualified.expectedCloseDate,
            createdByUserId = qualified.createdByUserId,
            createdAt = now,
            updatedAt = now
        )
        dealRepository.save(deal).getOrThrow()

        Qualification(qualified, contact, deal, dealAlreadyExisted = false)
    }

    /**
     * Find-or-create by normalised phone number. A lead without a parsable phone still
     * qualifies — the contact is then created phone-less and matched only by this lead.
     */
    private suspend fun findOrCreateContact(
        tenantId: TenantId,
        lead: CrmLead,
        now: Instant,
        newId: () -> String
    ): Contact {
        val phone = lead.whatsappNumber
        if (phone != null) {
            contactRepository.findByPhone(tenantId, phone)?.let { existing ->
                return existing
            }
        }
        val contact = Contact(
            id = ContactId(newId()),
            tenantId = tenantId,
            name = lead.contactPerson,
            brandName = lead.brandName,
            phone = lead.whatsappNumber?.let { WhatsappNumber(it.value) },
            email = lead.email,
            sourceLeadId = lead.id,
            createdAt = now,
            updatedAt = now
        )
        contactRepository.save(contact).getOrThrow()
        return contact
    }
}

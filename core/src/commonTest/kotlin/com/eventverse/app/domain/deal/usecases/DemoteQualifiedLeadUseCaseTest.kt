package com.eventverse.app.domain.deal.usecases

import com.eventverse.app.domain.crm.BrandName
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
import com.eventverse.app.domain.deal.PoNumber
import com.eventverse.app.domain.deal.PoOrigin
import com.eventverse.app.domain.deal.PurchaseOrder
import com.eventverse.app.domain.deal.PurchaseOrderId
import com.eventverse.app.domain.deal.PurchaseOrderLine
import com.eventverse.app.domain.invoicing.Invoice
import com.eventverse.app.domain.invoicing.InvoiceId
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceNumber
import com.eventverse.app.domain.invoicing.InvoicePage
import com.eventverse.app.domain.invoicing.InvoiceQuery
import com.eventverse.app.domain.invoicing.InvoiceRepository
import com.eventverse.app.domain.invoicing.InvoiceSourceKind
import com.eventverse.app.domain.invoicing.InvoiceStatus
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Demosi bersyarat: lead QUALIFIED hanya boleh balik ke NEW_LEAD bila deal-nya masih OPEN
 * tanpa PO dan tanpa invoice aktif — dan bila lolos, deal ikut diarsipkan.
 */
class DemoteQualifiedLeadUseCaseTest {

    private val tenant = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private class StubLeadRepository : CrmLeadRepository {
        val leads = mutableMapOf<String, CrmLead>()
        override suspend fun findById(tenantId: TenantId, id: LeadId): CrmLead? = leads[id.value]
        override suspend fun findActive(tenantId: TenantId, ownerReachIds: Set<OrgNodeId>?): List<CrmLead> =
            leads.values.toList()
        override suspend fun save(lead: CrmLead): Result<CrmLead> {
            leads[lead.id.value] = lead
            return Result.success(lead)
        }
    }

    private class StubDealRepository : DealRepository {
        val deals = mutableMapOf<String, Deal>()
        val purchaseOrders = mutableListOf<PurchaseOrder>()
        override suspend fun findById(tenantId: TenantId, id: DealId): Deal? = deals[id.value]
        override suspend fun findBySourceLeadId(tenantId: TenantId, leadId: LeadId): Deal? =
            deals.values.firstOrNull { it.sourceLeadId == leadId }
        override suspend fun findActive(tenantId: TenantId, ownerReachIds: Set<OrgNodeId>?): List<Deal> =
            deals.values.toList()
        override suspend fun save(deal: Deal): Result<Deal> {
            deals[deal.id.value] = deal
            return Result.success(deal)
        }
        override suspend fun findPurchaseOrders(tenantId: TenantId, dealId: DealId): List<PurchaseOrder> =
            purchaseOrders.filter { it.dealId == dealId }
        override suspend fun findPurchaseOrderById(tenantId: TenantId, poId: PurchaseOrderId): PurchaseOrder? =
            purchaseOrders.firstOrNull { it.id == poId }
        override suspend fun savePurchaseOrder(po: PurchaseOrder): Result<PurchaseOrder> {
            purchaseOrders += po
            return Result.success(po)
        }
        override suspend fun existsForContact(tenantId: TenantId, contactId: ContactId, excludeDealId: DealId?): Boolean =
            deals.values.any { it.contactId == contactId && it.id != excludeDealId }
    }

    /** Records only what the demotion check needs: (sourceKind, sourceRef) -> active? */
    private class StubInvoiceRepository : InvoiceRepository {
        val activeSources = mutableSetOf<String>()
        override suspend fun hasActiveInvoiceForSource(
            tenantId: TenantId,
            sourceKind: InvoiceSourceKind,
            sourceRef: String
        ): Boolean = "${sourceKind.name}::$sourceRef" in activeSources

        override suspend fun findById(tenantId: TenantId, id: InvoiceId): Invoice? = null
        override suspend fun findByNumber(tenantId: TenantId, number: InvoiceNumber): Invoice? = null
        override suspend fun search(query: InvoiceQuery): InvoicePage =
            InvoicePage(emptyList(), 0, query.page, query.pageSize)
        override suspend fun save(invoice: Invoice) = Unit
        override suspend fun reserveNextNumber(tenantId: TenantId, kind: InvoiceKind, period: String) =
            InvoiceNumber("INV/2026/01/0001")
        override suspend fun deleteDraft(tenantId: TenantId, id: InvoiceId) = Unit
    }

    private class StubContactRepository : ContactRepository {
        val contacts = mutableMapOf<String, com.eventverse.app.domain.crm.Contact>()

        private fun key(tenantId: TenantId, id: ContactId) = "${tenantId.value}::${id.value}"

        override suspend fun findById(tenantId: TenantId, id: ContactId) = contacts[key(tenantId, id)]
        override suspend fun findByPhone(tenantId: TenantId, phone: WhatsappNumber) =
            contacts.values.firstOrNull { it.phone?.value == phone.value }
        override suspend fun findActive(tenantId: TenantId) = contacts.values.toList()
        override suspend fun save(contact: com.eventverse.app.domain.crm.Contact): Result<com.eventverse.app.domain.crm.Contact> {
            contacts[key(contact.tenantId, contact.id)] = contact
            return Result.success(contact)
        }
        override suspend fun delete(tenantId: TenantId, id: ContactId): Boolean =
            contacts.remove(key(tenantId, id)) != null
    }

    /** Default wiring: every dependency stubbed, results inspectable via the returned use case. */
    private fun useCase(
        leads: StubLeadRepository,
        deals: StubDealRepository,
        contacts: StubContactRepository = StubContactRepository(),
        invoices: StubInvoiceRepository = StubInvoiceRepository()
    ) = DemoteQualifiedLeadUseCase(leads, deals, contacts, invoices)

    private fun contact(leadId: String = "lead-1") = com.eventverse.app.domain.crm.Contact(
        id = ContactId("contact-1"),
        tenantId = tenant,
        name = "Budi",
        brandName = BrandName("PT Sinar Jaya"),
        phone = WhatsappNumber("6281234567890"),
        sourceLeadId = LeadId(leadId),
        createdAt = now,
        updatedAt = now
    )

    private fun qualifiedLead(id: String = "lead-1") = CrmLead(
        id = LeadId(id),
        tenantId = tenant,
        brandName = BrandName("PT Sinar Jaya"),
        contactPerson = "Budi",
        whatsappNumber = WhatsappNumber("6281234567890"),
        stage = LeadStage.QUALIFIED,
        createdAt = now,
        updatedAt = now
    )

    private fun openDeal(leadId: String = "lead-1", stage: DealStage = DealStage.OPEN) = Deal(
        id = DealId("deal-$leadId"),
        tenantId = tenant,
        contactId = ContactId("contact-1"),
        sourceLeadId = LeadId(leadId),
        title = DealTitle("Pesanan PT Sinar Jaya"),
        stage = stage,
        createdAt = now,
        updatedAt = now
    )

    private fun purchaseOrder(dealId: DealId) = PurchaseOrder(
        id = PurchaseOrderId("po-1"),
        tenantId = tenant,
        dealId = dealId,
        poNumber = PoNumber("PO-2026-001"),
        poDate = LocalDate(2026, 1, 1),
        origin = PoOrigin.MANUAL,
        lines = listOf(PurchaseOrderLine("Kaos polos 100 pcs", 100.0, 25_000L)),
        recordedBy = "tester",
        createdAt = now
    )

    @Test
    fun demote_whenDealClean_shouldArchiveDealAndDemoteLead() = runTest {
        val leads = StubLeadRepository()
        val deals = StubDealRepository()
        leads.save(qualifiedLead())
        deals.save(openDeal())

        val result = useCase(leads, deals)(tenant, LeadId("lead-1"), now)

        assertTrue(result.isSuccess)
        val demotion = result.getOrThrow()
        assertEquals(LeadStage.NEW_LEAD, demotion.lead.stage)
        assertTrue(demotion.archivedDeal?.isArchived == true, "Deal harus ikut diarsipkan")
    }

    @Test
    fun demote_toFollowUp_shouldArchiveDealAndLandInFollowUp() = runTest {
        val leads = StubLeadRepository()
        val deals = StubDealRepository()
        leads.save(qualifiedLead())
        deals.save(openDeal())

        val result = useCase(leads, deals)(tenant, LeadId("lead-1"), now, LeadStage.FOLLOW_UP)

        assertEquals(LeadStage.FOLLOW_UP, result.getOrThrow().lead.stage)
        assertTrue(result.getOrThrow().archivedDeal?.isArchived == true)
    }

    @Test
    fun demote_toUnqualified_shouldBeRejected() = runTest {
        val leads = StubLeadRepository()
        leads.save(qualifiedLead())

        val result = useCase(leads, StubDealRepository())(tenant, LeadId("lead-1"), now, LeadStage.UNQUALIFIED)

        assertTrue(result.isFailure)
    }

    @Test
    fun demote_whenDealHasPurchaseOrder_shouldFail() = runTest {
        val leads = StubLeadRepository()
        val deals = StubDealRepository()
        val deal = openDeal()
        leads.save(qualifiedLead())
        deals.save(deal)
        deals.savePurchaseOrder(purchaseOrder(deal.id))

        val result = useCase(leads, deals)(tenant, LeadId("lead-1"), now)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("purchase order") == true)
        // Tidak ada perubahan: lead tetap QUALIFIED, deal tetap hidup.
        assertEquals(LeadStage.QUALIFIED, leads.leads["lead-1"]?.stage)
        assertTrue(deals.deals["deal-lead-1"]?.isArchived == false)
    }

    @Test
    fun demote_whenDealHasActiveInvoice_shouldFail() = runTest {
        val leads = StubLeadRepository()
        val deals = StubDealRepository()
        val invoices = StubInvoiceRepository()
        val deal = openDeal()
        leads.save(qualifiedLead())
        deals.save(deal)
        invoices.activeSources += "DEAL::${deal.id.value}"

        val result = useCase(leads, deals, invoices = invoices)(tenant, LeadId("lead-1"), now)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("invoice") == true)
    }

    @Test
    fun demote_whenDealProgressedBeyondOpen_shouldFail() = runTest {
        val leads = StubLeadRepository()
        val deals = StubDealRepository()
        leads.save(qualifiedLead())
        deals.save(openDeal(stage = DealStage.IN_PRODUCTION))

        val result = useCase(leads, deals)(tenant, LeadId("lead-1"), now)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Dalam Produksi") == true)
    }

    @Test
    fun demote_whenNoDealExists_shouldJustDemoteLead() = runTest {
        val leads = StubLeadRepository()
        val deals = StubDealRepository()
        leads.save(qualifiedLead())

        val result = useCase(leads, deals)(tenant, LeadId("lead-1"), now)

        assertTrue(result.isSuccess)
        assertEquals(LeadStage.NEW_LEAD, result.getOrThrow().lead.stage)
        assertTrue(result.getOrThrow().archivedDeal == null)
    }

    @Test
    fun demote_whenDealAlreadyArchived_shouldJustDemoteLead() = runTest {
        val leads = StubLeadRepository()
        val deals = StubDealRepository()
        leads.save(qualifiedLead())
        deals.save(openDeal().archive(now))

        val result = useCase(leads, deals)(tenant, LeadId("lead-1"), now)

        assertTrue(result.isSuccess)
        assertEquals(LeadStage.NEW_LEAD, result.getOrThrow().lead.stage)
    }

    @Test
    fun demote_onNonQualifiedLead_shouldFail() = runTest {
        val leads = StubLeadRepository()
        val lead = qualifiedLead().transitionTo(LeadStage.NEW_LEAD, now).getOrThrow()
        leads.save(lead)

        val result = useCase(leads, StubDealRepository())(tenant, LeadId("lead-1"), now)

        assertTrue(result.isFailure)
    }

    // ── Contact lifecycle ───────────────────────────────────────────────────

    @Test
    fun demote_whenContactCreatedByThisLead_shouldDeleteContact() = runTest {
        val leads = StubLeadRepository()
        val deals = StubDealRepository()
        val contacts = StubContactRepository()
        leads.save(qualifiedLead())
        deals.save(openDeal())
        contacts.save(contact(leadId = "lead-1")) // dibuat oleh kualifikasi lead ini

        val result = useCase(leads, deals, contacts)(tenant, LeadId("lead-1"), now)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().deletedContact)
        assertEquals(0, contacts.contacts.size, "Kontak sampah kualifikasi harus ikut terhapus")
    }

    @Test
    fun demote_whenContactOwnedByAnotherLead_shouldKeepContact() = runTest {
        val leads = StubLeadRepository()
        val deals = StubDealRepository()
        val contacts = StubContactRepository()
        leads.save(qualifiedLead())
        deals.save(openDeal())
        // Kontak ini milik order pertama (lead lain) — lead yang didemosi hanya memakainya.
        contacts.save(contact(leadId = "lead-pertama"))

        val result = useCase(leads, deals, contacts)(tenant, LeadId("lead-1"), now)

        assertTrue(result.isSuccess)
        assertTrue(!result.getOrThrow().deletedContact)
        assertEquals(1, contacts.contacts.size, "Kontak milik order lain tidak boleh terhapus")
    }

    @Test
    fun demote_whenContactReferencedByAnotherDeal_shouldKeepContact() = runTest {
        val leads = StubLeadRepository()
        val deals = StubDealRepository()
        val contacts = StubContactRepository()
        val deal = openDeal()
        leads.save(qualifiedLead())
        deals.save(deal)
        contacts.save(contact(leadId = "lead-1"))
        // Deal kedua (order kedua) memakai contact yang sama.
        deals.save(
            deal.copy(id = DealId("deal-lain"), sourceLeadId = LeadId("lead-2"), archivedAt = now)
        )

        val result = useCase(leads, deals, contacts)(tenant, LeadId("lead-1"), now)

        assertTrue(result.isSuccess)
        assertTrue(!result.getOrThrow().deletedContact, "Contact masih dipakai deal lain — tidak boleh dihapus")
        assertEquals(1, contacts.contacts.size)
    }

    @Test
    fun demote_whenContactMissing_shouldStillSucceed() = runTest {
        val leads = StubLeadRepository()
        val deals = StubDealRepository()
        leads.save(qualifiedLead())
        deals.save(openDeal())
        // Kontak tidak ada (mis. dihapus manual) — demosi tidak boleh crash.

        val result = useCase(leads, deals)(tenant, LeadId("lead-1"), now)

        assertTrue(result.isSuccess)
        assertTrue(!result.getOrThrow().deletedContact)
        assertEquals(LeadStage.NEW_LEAD, result.getOrThrow().lead.stage)
    }
}

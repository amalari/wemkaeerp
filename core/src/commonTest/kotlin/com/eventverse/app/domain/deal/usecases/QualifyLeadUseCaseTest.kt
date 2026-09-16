package com.eventverse.app.domain.deal.usecases

import com.eventverse.app.domain.crm.BrandName
import com.eventverse.app.domain.crm.ContactRepository
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure unit test: qualification must produce lead + contact + deal, and MUST be idempotent —
 * a retried qualification returns the existing deal instead of forking a duplicate.
 */
class QualifyLeadUseCaseTest {

    private val tenant = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private class FakeLeadRepository : CrmLeadRepository {
        val leads = mutableMapOf<String, CrmLead>()
        override suspend fun findById(tenantId: TenantId, id: LeadId): CrmLead? = leads[id.value]
        override suspend fun findActive(tenantId: TenantId, ownerReachIds: Set<OrgNodeId>?): List<CrmLead> =
            leads.values.toList()
        override suspend fun save(lead: CrmLead): Result<CrmLead> {
            leads[lead.id.value] = lead
            return Result.success(lead)
        }
    }

    private class FakeContactRepository : ContactRepository {
        val contacts = mutableMapOf<String, com.eventverse.app.domain.crm.Contact>()
        override suspend fun findById(tenantId: TenantId, id: com.eventverse.app.domain.crm.ContactId) =
            contacts[id.value]
        override suspend fun findByPhone(tenantId: TenantId, phone: WhatsappNumber) =
            contacts.values.firstOrNull { it.phone?.value == phone.value }
        override suspend fun findActive(tenantId: TenantId) = contacts.values.toList()
        override suspend fun save(contact: com.eventverse.app.domain.crm.Contact): Result<com.eventverse.app.domain.crm.Contact> {
            contacts[contact.id.value] = contact
            return Result.success(contact)
        }
        override suspend fun delete(tenantId: TenantId, id: com.eventverse.app.domain.crm.ContactId): Boolean =
            contacts.remove(id.value) != null
    }

    private class FakeDealRepository : DealRepository {
        val deals = mutableMapOf<String, com.eventverse.app.domain.deal.Deal>()
        override suspend fun findById(tenantId: TenantId, id: com.eventverse.app.domain.deal.DealId) =
            deals[id.value]
        override suspend fun findBySourceLeadId(tenantId: TenantId, leadId: LeadId) =
            deals.values.firstOrNull { it.sourceLeadId == leadId }
        override suspend fun findActive(tenantId: TenantId, ownerReachIds: Set<OrgNodeId>?) =
            deals.values.toList()
        override suspend fun save(deal: com.eventverse.app.domain.deal.Deal): Result<com.eventverse.app.domain.deal.Deal> {
            deals[deal.id.value] = deal
            return Result.success(deal)
        }
        override suspend fun existsForContact(
            tenantId: TenantId,
            contactId: com.eventverse.app.domain.crm.ContactId,
            excludeDealId: com.eventverse.app.domain.deal.DealId?
        ): Boolean = deals.values.any { it.contactId == contactId && it.id != excludeDealId }
        override suspend fun findPurchaseOrders(tenantId: TenantId, dealId: com.eventverse.app.domain.deal.DealId) =
            emptyList<com.eventverse.app.domain.deal.PurchaseOrder>()
        override suspend fun findPurchaseOrderById(tenantId: TenantId, poId: com.eventverse.app.domain.deal.PurchaseOrderId) =
            null
        override suspend fun savePurchaseOrder(po: com.eventverse.app.domain.deal.PurchaseOrder) =
            Result.success(po)
    }

    private fun lead(id: String = "lead-1") = CrmLead(
        id = LeadId(id),
        tenantId = tenant,
        brandName = BrandName("PT Sinar Jaya"),
        contactPerson = "Budi",
        whatsappNumber = WhatsappNumber("6281234567890"),
        stage = LeadStage.NEW_LEAD,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun invoke_onNewLead_shouldCreateLeadContactAndDeal() = runTest {
        val leads = FakeLeadRepository()
        val contacts = FakeContactRepository()
        val deals = FakeDealRepository()
        val useCase = QualifyLeadUseCase(leads, contacts, deals)
        leads.save(lead())

        val result = useCase(tenant, LeadId("lead-1"), now, newId = { "gen-${kotlin.random.Random.nextInt(1_000_000)}" })

        assertTrue(result.isSuccess)
        val qualification = result.getOrThrow()
        assertEquals(LeadStage.QUALIFIED, qualification.lead.stage)
        assertEquals(1, contacts.contacts.size)
        assertEquals(1, deals.deals.size)
        assertEquals(DealStage.OPEN, qualification.deal.stage)
        assertEquals(qualification.contact.id, qualification.deal.contactId)
        assertEquals(LeadId("lead-1"), qualification.deal.sourceLeadId)
    }

    @Test
    fun invoke_retriedOnSameLead_shouldNotDuplicateContactOrDeal() = runTest {
        val leads = FakeLeadRepository()
        val contacts = FakeContactRepository()
        val deals = FakeDealRepository()
        val useCase = QualifyLeadUseCase(leads, contacts, deals)
        leads.save(lead())

        val first = useCase(tenant, LeadId("lead-1"), now, newId = { "gen-${kotlin.random.Random.nextInt(1_000_000)}" }).getOrThrow()
        val second = useCase(tenant, LeadId("lead-1"), now, newId = { "gen-${kotlin.random.Random.nextInt(1_000_000)}" }).getOrThrow()

        assertTrue(!first.dealAlreadyExisted)
        assertTrue(second.dealAlreadyExisted)
        assertEquals(first.deal.id, second.deal.id)
        assertEquals(first.contact.id, second.contact.id)
        assertEquals(1, contacts.contacts.size, "Contact tidak boleh terduplikasi")
        assertEquals(1, deals.deals.size, "Deal tidak boleh terduplikasi")
    }

    @Test
    fun invoke_onSamePhoneDifferentLead_shouldReuseExistingContact() = runTest {
        val leads = FakeLeadRepository()
        val contacts = FakeContactRepository()
        val deals = FakeDealRepository()
        val useCase = QualifyLeadUseCase(leads, contacts, deals)
        leads.save(lead("lead-1"))
        leads.save(lead("lead-2"))

        val first = useCase(tenant, LeadId("lead-1"), now, newId = { "gen-${kotlin.random.Random.nextInt(1_000_000)}" }).getOrThrow()
        val second = useCase(tenant, LeadId("lead-2"), now, newId = { "gen-${kotlin.random.Random.nextInt(1_000_000)}" }).getOrThrow()

        assertEquals(first.contact.id, second.contact.id, "Nomor telepon sama harus memakai contact yang sama")
        assertTrue(first.deal.id != second.deal.id, "Deal tetap berbeda per lead")
        assertEquals(1, contacts.contacts.size)
    }

    @Test
    fun invoke_onUnknownLead_shouldFail() = runTest {
        val useCase = QualifyLeadUseCase(FakeLeadRepository(), FakeContactRepository(), FakeDealRepository())
        val result = useCase(tenant, LeadId("missing"), now, newId = { "gen-${kotlin.random.Random.nextInt(1_000_000)}" })
        assertTrue(result.isFailure)
    }
}

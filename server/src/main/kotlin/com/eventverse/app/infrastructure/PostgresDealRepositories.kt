package com.eventverse.app.infrastructure

import com.eventverse.app.domain.crm.BrandName
import com.eventverse.app.domain.crm.Contact
import com.eventverse.app.domain.crm.ContactId
import com.eventverse.app.domain.crm.ContactRepository
import com.eventverse.app.domain.crm.LeadId
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
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.CrmContactsTable
import com.eventverse.app.infrastructure.tables.DealPurchaseOrdersTable
import com.eventverse.app.infrastructure.tables.DealsTable
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * PostgreSQL implementations for contacts and deals. Tenant isolation by Row-Level Security
 * (`DatabaseFactory.dbQuery`); `DataScope` enforced as an indexed SQL predicate on
 * `owner_employee_id`, mirroring [PostgresCrmLeadRepository].
 *
 * Every method opens its own `dbQuery`, AND joins the caller's transaction when called from
 * inside one (Exposed nests into the active transaction) — which is exactly what makes
 * `QualifyLeadUseCase` atomic when the route wraps it in a single `dbQuery(tenantId)`.
 */
class PostgresContactRepository : ContactRepository {

    override suspend fun findById(tenantId: TenantId, id: ContactId): Contact? =
        DatabaseFactory.dbQuery(tenantId) {
            CrmContactsTable.selectAll()
                .where { (CrmContactsTable.tenantId eq tenantId.value) and (CrmContactsTable.id eq id.value) }
                .map(::toContact)
                .singleOrNull()
        }

    override suspend fun delete(tenantId: TenantId, id: ContactId): Boolean =
        DatabaseFactory.dbQuery(tenantId) {
            CrmContactsTable.deleteWhere {
                (CrmContactsTable.tenantId eq tenantId.value) and (CrmContactsTable.id eq id.value)
            } > 0
        }

    override suspend fun findByPhone(tenantId: TenantId, phone: WhatsappNumber): Contact? =
        DatabaseFactory.dbQuery(tenantId) {
            CrmContactsTable.selectAll()
                .where { (CrmContactsTable.tenantId eq tenantId.value) and (CrmContactsTable.phone eq phone.value) }
                .map(::toContact)
                .singleOrNull()
        }

    override suspend fun findActive(tenantId: TenantId): List<Contact> =
        DatabaseFactory.dbQuery(tenantId) {
            CrmContactsTable.selectAll()
                .where { CrmContactsTable.tenantId eq tenantId.value }
                .map(::toContact)
                .sortedByDescending { it.updatedAt }
        }

    override suspend fun save(contact: Contact): Result<Contact> = runCatching {
        DatabaseFactory.dbQuery(contact.tenantId) {
            val updatedRows = CrmContactsTable.update(
                { (CrmContactsTable.tenantId eq contact.tenantId.value) and (CrmContactsTable.id eq contact.id.value) }
            ) {
                it[name] = contact.name
                it[brandName] = contact.brandName.value
                it[phone] = contact.phone?.value ?: ""
                it[email] = contact.email
                it[address] = contact.address
                it[taxId] = contact.taxId
                it[sourceLeadId] = contact.sourceLeadId?.value
                it[updatedAt] = contact.updatedAt
            }
            if (updatedRows == 0) {
                CrmContactsTable.insert {
                    it[id] = contact.id.value
                    it[tenantId] = contact.tenantId.value
                    it[name] = contact.name
                    it[brandName] = contact.brandName.value
                    it[phone] = contact.phone?.value ?: ""
                    it[email] = contact.email
                    it[address] = contact.address
                    it[taxId] = contact.taxId
                    it[sourceLeadId] = contact.sourceLeadId?.value
                    it[createdAt] = contact.createdAt
                    it[updatedAt] = contact.updatedAt
                }
            }
            contact
        }
    }

    private fun toContact(row: ResultRow): Contact = Contact(
        id = ContactId(row[CrmContactsTable.id]),
        tenantId = TenantId(row[CrmContactsTable.tenantId]),
        name = row[CrmContactsTable.name],
        brandName = BrandName(row[CrmContactsTable.brandName]),
        phone = row[CrmContactsTable.phone].takeIf { it.isNotBlank() }?.let { WhatsappNumber(it) },
        email = row[CrmContactsTable.email],
        address = row[CrmContactsTable.address],
        taxId = row[CrmContactsTable.taxId],
        sourceLeadId = row[CrmContactsTable.sourceLeadId]?.let { LeadId(it) },
        createdAt = row[CrmContactsTable.createdAt],
        updatedAt = row[CrmContactsTable.updatedAt]
    )
}

class PostgresDealRepository : DealRepository {

    override suspend fun findById(tenantId: TenantId, id: DealId): Deal? =
        DatabaseFactory.dbQuery(tenantId) {
            DealsTable.selectAll()
                .where { (DealsTable.tenantId eq tenantId.value) and (DealsTable.id eq id.value) }
                .map(::toDeal)
                .singleOrNull()
        }

    override suspend fun findBySourceLeadId(tenantId: TenantId, leadId: LeadId): Deal? =
        DatabaseFactory.dbQuery(tenantId) {
            DealsTable.selectAll()
                .where { (DealsTable.tenantId eq tenantId.value) and (DealsTable.sourceLeadId eq leadId.value) }
                .map(::toDeal)
                .singleOrNull()
        }

    override suspend fun findActive(tenantId: TenantId, ownerReachIds: Set<OrgNodeId>?): List<Deal> =
        DatabaseFactory.dbQuery(tenantId) {
            val baseCondition = (DealsTable.tenantId eq tenantId.value) and (DealsTable.archivedAt.isNull())
            if (ownerReachIds != null && ownerReachIds.isEmpty()) {
                // Empty reach (OWN_DATA_ONLY with no employee row) must return zero rows.
                return@dbQuery emptyList()
            }
            val condition = if (ownerReachIds == null) baseCondition
            else baseCondition and (DealsTable.ownerEmployeeId inList ownerReachIds.map { it.value })

            DealsTable.selectAll().where(condition)
                .orderBy(DealsTable.updatedAt, SortOrder.DESC)
                .map(::toDeal)
        }

    override suspend fun save(deal: Deal): Result<Deal> = runCatching {
        DatabaseFactory.dbQuery(deal.tenantId) {
            val updatedRows = DealsTable.update(
                { (DealsTable.tenantId eq deal.tenantId.value) and (DealsTable.id eq deal.id.value) }
            ) {
                it[contactId] = deal.contactId.value
                it[sourceLeadId] = deal.sourceLeadId?.value
                it[title] = deal.title.value
                it[stage] = deal.stage.name
                it[estimatedValueIdr] = deal.estimatedValue?.amount
                it[ownerEmployeeId] = deal.ownerEmployeeId?.value
                it[expectedCloseDate] = deal.expectedCloseDate
                it[notes] = deal.notes
                it[updatedAt] = deal.updatedAt
                it[archivedAt] = deal.archivedAt
            }
            if (updatedRows == 0) {
                DealsTable.insert {
                    it[id] = deal.id.value
                    it[tenantId] = deal.tenantId.value
                    it[contactId] = deal.contactId.value
                    it[sourceLeadId] = deal.sourceLeadId?.value
                    it[title] = deal.title.value
                    it[stage] = deal.stage.name
                    it[estimatedValueIdr] = deal.estimatedValue?.amount
                    it[ownerEmployeeId] = deal.ownerEmployeeId?.value
                    it[expectedCloseDate] = deal.expectedCloseDate
                    it[notes] = deal.notes
                    it[createdByUserId] = deal.createdByUserId
                    it[createdAt] = deal.createdAt
                    it[updatedAt] = deal.updatedAt
                }
            }
            deal
        }
    }

    override suspend fun existsForContact(
        tenantId: TenantId,
        contactId: ContactId,
        excludeDealId: DealId?
    ): Boolean = DatabaseFactory.dbQuery(tenantId) {
        var condition = (DealsTable.tenantId eq tenantId.value) and (DealsTable.contactId eq contactId.value)
        if (excludeDealId != null) {
            condition = condition and (DealsTable.id neq excludeDealId.value)
        }
        DealsTable.selectAll().where(condition).limit(1).empty().not()
    }

    override suspend fun findPurchaseOrders(tenantId: TenantId, dealId: DealId): List<PurchaseOrder> =
        DatabaseFactory.dbQuery(tenantId) {
            DealPurchaseOrdersTable.selectAll()
                .where { (DealPurchaseOrdersTable.tenantId eq tenantId.value) and (DealPurchaseOrdersTable.dealId eq dealId.value) }
                .orderBy(DealPurchaseOrdersTable.createdAt, SortOrder.DESC)
                .map(::toPurchaseOrder)
        }

    override suspend fun findPurchaseOrderById(tenantId: TenantId, poId: PurchaseOrderId): PurchaseOrder? =
        DatabaseFactory.dbQuery(tenantId) {
            DealPurchaseOrdersTable.selectAll()
                .where { (DealPurchaseOrdersTable.tenantId eq tenantId.value) and (DealPurchaseOrdersTable.id eq poId.value) }
                .map(::toPurchaseOrder)
                .singleOrNull()
        }

    override suspend fun savePurchaseOrder(po: PurchaseOrder): Result<PurchaseOrder> = runCatching {
        DatabaseFactory.dbQuery(po.tenantId) {
            val linesJson = jsonArrayOf(po.lines.map { line ->
                com.eventverse.app.shared.json.jsonObjectOf(
                    "description" to com.eventverse.app.shared.json.jsonOf(line.description),
                    "quantity" to com.eventverse.app.shared.json.jsonOf(line.quantity),
                    "unitPriceIdr" to com.eventverse.app.shared.json.jsonOf(line.unitPriceIdr)
                )
            }).encode()
            DealPurchaseOrdersTable.insert {
                it[id] = po.id.value
                it[tenantId] = po.tenantId.value
                it[dealId] = po.dealId.value
                it[poNumber] = po.poNumber.value
                it[poDate] = po.poDate
                it[origin] = po.origin.name
                it[fileName] = po.fileName
                it[mimeType] = po.mimeType
                it[fileSizeBytes] = po.fileSizeBytes
                it[storageKey] = po.storageKey
                it[manualLines] = linesJson
                it[notes] = po.notes
                it[recordedBy] = po.recordedBy
                it[createdAt] = po.createdAt
            }
            po
        }
    }

    private fun toDeal(row: ResultRow): Deal = Deal(
        id = DealId(row[DealsTable.id]),
        tenantId = TenantId(row[DealsTable.tenantId]),
        contactId = ContactId(row[DealsTable.contactId]),
        sourceLeadId = row[DealsTable.sourceLeadId]?.let { LeadId(it) },
        title = DealTitle(row[DealsTable.title]),
        stage = DealStage.fromCode(row[DealsTable.stage]) ?: DealStage.OPEN,
        estimatedValue = row[DealsTable.estimatedValueIdr]?.let { MoneyIdr(it) },
        ownerEmployeeId = row[DealsTable.ownerEmployeeId]?.let { OrgNodeId(it) },
        expectedCloseDate = row[DealsTable.expectedCloseDate],
        notes = row[DealsTable.notes],
        createdByUserId = row[DealsTable.createdByUserId],
        createdAt = row[DealsTable.createdAt],
        updatedAt = row[DealsTable.updatedAt],
        archivedAt = row[DealsTable.archivedAt]
    )

    private fun toPurchaseOrder(row: ResultRow): PurchaseOrder {
        val linesJson = runCatching { JsonParser.parseArray(row[DealPurchaseOrdersTable.manualLines]) }
            .getOrDefault(emptyList())
        val lines = linesJson.mapNotNull { item ->
            val line = item as? JsonValue.Obj ?: return@mapNotNull null
            val description = line.string("description") ?: return@mapNotNull null
            PurchaseOrderLine(
                description = description,
                quantity = line.double("quantity") ?: 1.0,
                unitPriceIdr = line.long("unitPriceIdr") ?: 0L
            )
        }
        return PurchaseOrder(
            id = PurchaseOrderId(row[DealPurchaseOrdersTable.id]),
            tenantId = TenantId(row[DealPurchaseOrdersTable.tenantId]),
            dealId = DealId(row[DealPurchaseOrdersTable.dealId]),
            poNumber = PoNumber(row[DealPurchaseOrdersTable.poNumber]),
            poDate = row[DealPurchaseOrdersTable.poDate],
            origin = PoOrigin.fromCode(row[DealPurchaseOrdersTable.origin]) ?: PoOrigin.MANUAL,
            fileName = row[DealPurchaseOrdersTable.fileName],
            mimeType = row[DealPurchaseOrdersTable.mimeType],
            fileSizeBytes = row[DealPurchaseOrdersTable.fileSizeBytes],
            storageKey = row[DealPurchaseOrdersTable.storageKey],
            lines = lines,
            notes = row[DealPurchaseOrdersTable.notes],
            recordedBy = row[DealPurchaseOrdersTable.recordedBy],
            createdAt = row[DealPurchaseOrdersTable.createdAt]
        )
    }
}

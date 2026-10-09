package com.eventverse.app.infrastructure

import com.eventverse.app.domain.crm.LeadCreationChannel
import com.eventverse.app.domain.crm.BrandName
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadSource
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.CrmLeadActivitiesTable
import com.eventverse.app.infrastructure.tables.CrmLeadsTable
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * PostgreSQL implementation of [CrmLeadRepository]. Tenant isolation enforced by Row-Level
 * Security (`DatabaseFactory.dbQuery`); `DataScope` enforced HERE, as an indexed SQL
 * predicate on `owner_employee_id` — never as an in-memory filter, which is what
 * `OrgChartVisibility` does and would be fatal at more than a few hundred rows.
 *
 * KNOWN GAP (documented, not silently skipped): a tenant-added custom field of type
 * `FieldType.UserRef` is persisted only inside the `custom_attributes` JSONB blob here —
 * there is no dual-write into `custom_field_links` for CUSTOM user-reference fields in this
 * phase. The CORE `owner_employee_id` column (which drives `DataScope`) has its own ordinary
 * FK and does not need the link table. Wiring custom `UserRef` fields to `custom_field_links`
 * is phase-1.5 work, tracked alongside `ChangeFieldTypeUseCase`.
 */
class PostgresCrmLeadRepository : CrmLeadRepository {

    override suspend fun findById(tenantId: TenantId, id: LeadId): CrmLead? =
        DatabaseFactory.dbQuery(tenantId) {
            val lead = CrmLeadsTable.selectAll()
                .where { (CrmLeadsTable.tenantId eq tenantId.value) and (CrmLeadsTable.id eq id.value) }
                .map(::toLead)
                .singleOrNull() ?: return@dbQuery null

            val count = CrmLeadActivitiesTable
                .selectAll()
                .where { (CrmLeadActivitiesTable.tenantId eq tenantId.value) and (CrmLeadActivitiesTable.leadId eq id.value) }
                .count()
                .toInt()

            lead.copy(activityCount = count)
        }

    override suspend fun findActive(tenantId: TenantId, ownerReachIds: Set<OrgNodeId>?): List<CrmLead> =
        DatabaseFactory.dbQuery(tenantId) {
            val baseCondition = (CrmLeadsTable.tenantId eq tenantId.value) and (CrmLeadsTable.archivedAt.isNull())

            val query = if (ownerReachIds == null) {
                CrmLeadsTable.selectAll().where(baseCondition)
            } else if (ownerReachIds.isEmpty()) {
                // An empty reach set (e.g. OWN_DATA_ONLY for a viewer with no employee row)
                // must return zero rows, not "no predicate at all".
                return@dbQuery emptyList()
            } else {
                CrmLeadsTable.selectAll().where {
                    baseCondition and (CrmLeadsTable.ownerEmployeeId inList ownerReachIds.map { it.value })
                }
            }

            val leads = query.orderBy(CrmLeadsTable.updatedAt, SortOrder.DESC).map(::toLead)
            if (leads.isEmpty()) return@dbQuery emptyList()

            val leadIds = leads.map { it.id.value }
            val countColumn = CrmLeadActivitiesTable.id.count()
            val counts = CrmLeadActivitiesTable
                .select(CrmLeadActivitiesTable.leadId, countColumn)
                .where {
                    (CrmLeadActivitiesTable.tenantId eq tenantId.value) and
                    (CrmLeadActivitiesTable.leadId inList leadIds)
                }
                .groupBy(CrmLeadActivitiesTable.leadId)
                .associate { LeadId(it[CrmLeadActivitiesTable.leadId]) to it[countColumn].toInt() }

            leads.map { lead -> lead.copy(activityCount = counts[lead.id] ?: 0) }
        }

    /**
     * Opsi pemilih `RELATION` (TRD-FIELD-001 FR-4): predicate jangkauan + pencarian + `LIMIT` di SQL,
     * tanpa agregat aktivitas (label tidak memakainya). Satu ketikan tidak boleh memuat seluruh lead.
     */
    override suspend fun searchActive(
        tenantId: TenantId,
        ownerReachIds: Set<OrgNodeId>?,
        query: String,
        limit: Int
    ): List<CrmLead> = DatabaseFactory.dbQuery(tenantId) {
        if (ownerReachIds != null && ownerReachIds.isEmpty()) return@dbQuery emptyList()

        var condition: org.jetbrains.exposed.sql.Op<Boolean> =
            (CrmLeadsTable.tenantId eq tenantId.value) and (CrmLeadsTable.archivedAt.isNull())
        if (ownerReachIds != null) {
            condition = condition and (CrmLeadsTable.ownerEmployeeId inList ownerReachIds.map { it.value })
        }
        if (query.isNotBlank()) {
            val pattern = "%$query%"
            condition = condition and (
                (CrmLeadsTable.brandName like pattern) or
                    (CrmLeadsTable.contactPerson like pattern) or
                    (CrmLeadsTable.id like pattern)
                )
        }

        CrmLeadsTable.selectAll()
            .where(condition)
            .orderBy(CrmLeadsTable.updatedAt, SortOrder.DESC)
            .limit(limit)
            .map(::toLead)
    }

    override suspend fun save(lead: CrmLead): Result<CrmLead> = runCatching {
        DatabaseFactory.dbQuery(lead.tenantId) {
            val customAttributesJson = lead.customAttributes.encode()

            val updatedRows = CrmLeadsTable.update(
                { (CrmLeadsTable.tenantId eq lead.tenantId.value) and (CrmLeadsTable.id eq lead.id.value) }
            ) {
                it[brandName] = lead.brandName.value
                it[contactPerson] = lead.contactPerson
                it[whatsappNumber] = lead.whatsappNumber?.value ?: ""
                it[email] = lead.email
                it[stage] = lead.stage.name
                it[leadSource] = lead.source.value
                it[estimatedPcs] = lead.estimatedPcs
                it[estimatedValueIdr] = lead.estimatedValue?.amount
                it[ownerEmployeeId] = lead.ownerEmployeeId?.value
                it[expectedCloseDate] = lead.expectedCloseDate
                it[customAttributes] = customAttributesJson
                it[productCategory] = lead.productCategory.value
                it[lastContactedAt] = lead.lastContactedAt
                it[updatedAt] = lead.updatedAt
                it[archivedAt] = lead.archivedAt
            }

            if (updatedRows == 0) {
                CrmLeadsTable.insert {
                    it[id] = lead.id.value
                    it[tenantId] = lead.tenantId.value
                    it[brandName] = lead.brandName.value
                    it[contactPerson] = lead.contactPerson
                    it[whatsappNumber] = lead.whatsappNumber?.value ?: ""
                    it[email] = lead.email
                    it[stage] = lead.stage.name
                    it[leadSource] = lead.source.value
                    it[estimatedPcs] = lead.estimatedPcs
                    it[estimatedValueIdr] = lead.estimatedValue?.amount
                    it[ownerEmployeeId] = lead.ownerEmployeeId?.value
                    it[expectedCloseDate] = lead.expectedCloseDate
                    it[customAttributes] = customAttributesJson
                    it[productCategory] = lead.productCategory.value
                    it[lastContactedAt] = lead.lastContactedAt
                    it[createdByUserId] = lead.createdByUserId
                    it[createdVia] = lead.createdVia.name
                    it[createdAt] = lead.createdAt
                    it[updatedAt] = lead.updatedAt
                }
            }
            lead
        }
    }

    private fun toLead(row: ResultRow): CrmLead {
        val attrsObj = JsonParser.parseObjectOrNull(row[CrmLeadsTable.customAttributes]) ?: JsonValue.Obj(emptyMap())
        return CrmLead(
            id = LeadId(row[CrmLeadsTable.id]),
            tenantId = TenantId(row[CrmLeadsTable.tenantId]),
            brandName = BrandName(row[CrmLeadsTable.brandName]),
            contactPerson = row[CrmLeadsTable.contactPerson],
            whatsappNumber = row[CrmLeadsTable.whatsappNumber].takeIf { it.isNotBlank() }?.let { WhatsappNumber(it) },
            email = row[CrmLeadsTable.email],
            stage = LeadStage.fromCode(row[CrmLeadsTable.stage]) ?: LeadStage.NEW_LEAD,
            source = LeadSource(row[CrmLeadsTable.leadSource]),
            estimatedPcs = row[CrmLeadsTable.estimatedPcs],
            estimatedValue = row[CrmLeadsTable.estimatedValueIdr]?.let { MoneyIdr(it) },
            ownerEmployeeId = row[CrmLeadsTable.ownerEmployeeId]?.let { OrgNodeId(it) },
            expectedCloseDate = row[CrmLeadsTable.expectedCloseDate],
            productCategory = com.eventverse.app.domain.crm.ProductCategory(row[CrmLeadsTable.productCategory]),
            lastContactedAt = row[CrmLeadsTable.lastContactedAt],
            customAttributes = CustomAttributes.fromJsonValue(attrsObj),
            createdByUserId = row[CrmLeadsTable.createdByUserId],
            createdVia = requireNotNull(LeadCreationChannel.fromCode(row[CrmLeadsTable.createdVia])) { "created_via tak dikenal: ${row[CrmLeadsTable.createdVia]}" },
            createdAt = row[CrmLeadsTable.createdAt],
            updatedAt = row[CrmLeadsTable.updatedAt],
            archivedAt = row[CrmLeadsTable.archivedAt]
        )
    }
}

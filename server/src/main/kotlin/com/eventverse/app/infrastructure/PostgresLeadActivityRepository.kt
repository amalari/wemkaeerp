package com.eventverse.app.infrastructure

import com.eventverse.app.domain.crm.LeadActivity
import com.eventverse.app.domain.crm.LeadActivityId
import com.eventverse.app.domain.crm.LeadActivityRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.CrmLeadActivitiesTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class PostgresLeadActivityRepository : LeadActivityRepository {

    override suspend fun findByLeadId(tenantId: TenantId, leadId: LeadId): List<LeadActivity> =
        DatabaseFactory.dbQuery(tenantId) {
            CrmLeadActivitiesTable.selectAll()
                .where {
                    (CrmLeadActivitiesTable.tenantId eq tenantId.value) and
                    (CrmLeadActivitiesTable.leadId eq leadId.value)
                }
                .orderBy(CrmLeadActivitiesTable.createdAt, SortOrder.DESC)
                .map(::toActivity)
        }

    override suspend fun countByLeadIds(tenantId: TenantId, leadIds: List<LeadId>): Map<LeadId, Int> =
        DatabaseFactory.dbQuery(tenantId) {
            if (leadIds.isEmpty()) return@dbQuery emptyMap()

            val countColumn = CrmLeadActivitiesTable.id.count()
            CrmLeadActivitiesTable
                .select(CrmLeadActivitiesTable.leadId, countColumn)
                .where {
                    (CrmLeadActivitiesTable.tenantId eq tenantId.value) and
                    (CrmLeadActivitiesTable.leadId inList leadIds.map { it.value })
                }
                .groupBy(CrmLeadActivitiesTable.leadId)
                .associate { row ->
                    LeadId(row[CrmLeadActivitiesTable.leadId]) to row[countColumn].toInt()
                }
        }

    override suspend fun save(activity: LeadActivity): Result<LeadActivity> = runCatching {
        DatabaseFactory.dbQuery(activity.tenantId) {
            CrmLeadActivitiesTable.insert {
                it[id] = activity.id.value
                it[tenantId] = activity.tenantId.value
                it[leadId] = activity.leadId.value
                it[authorEmployeeId] = activity.authorEmployeeId?.value
                it[authorName] = activity.authorName
                it[content] = activity.content
                it[createdAt] = activity.createdAt
            }
            activity
        }
    }

    private fun toActivity(row: ResultRow): LeadActivity = LeadActivity(
        id = LeadActivityId(row[CrmLeadActivitiesTable.id]),
        tenantId = TenantId(row[CrmLeadActivitiesTable.tenantId]),
        leadId = LeadId(row[CrmLeadActivitiesTable.leadId]),
        authorEmployeeId = row[CrmLeadActivitiesTable.authorEmployeeId]?.let { OrgNodeId(it) },
        authorName = row[CrmLeadActivitiesTable.authorName],
        content = row[CrmLeadActivitiesTable.content],
        createdAt = row[CrmLeadActivitiesTable.createdAt]
    )
}

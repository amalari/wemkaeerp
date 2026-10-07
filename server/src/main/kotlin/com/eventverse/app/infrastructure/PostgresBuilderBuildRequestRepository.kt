package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.BriefSnapshot
import com.eventverse.app.domain.builder.BuildRequest
import com.eventverse.app.domain.builder.BuildRequestId
import com.eventverse.app.domain.builder.BuildRequestStatus
import com.eventverse.app.domain.builder.BuilderBuildRequestRepository
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.BuildRequestsTable
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** Antrian Pembuatan (tabel V83). Baca tenant difilter `tenant_id` (RLS lapis kedua). */
class PostgresBuilderBuildRequestRepository(private val clock: Clock = Clock.System) :
    BuilderBuildRequestRepository {

    override suspend fun findByTenant(tenantId: TenantId): List<BuildRequest> = DatabaseFactory.dbQuery {
        BuildRequestsTable.selectAll()
            .where { BuildRequestsTable.tenantId eq tenantId.value }
            .orderBy(BuildRequestsTable.createdAt, order = SortOrder.DESC)
            .map(::toRequest)
    }

    override suspend fun findAll(): List<BuildRequest> = DatabaseFactory.dbQuery {
        BuildRequestsTable.selectAll()
            .orderBy(BuildRequestsTable.createdAt, order = SortOrder.DESC)
            .map(::toRequest)
    }

    override suspend fun save(request: BuildRequest): BuildRequest = DatabaseFactory.dbQuery {
        val existing = BuildRequestsTable.selectAll()
            .where { BuildRequestsTable.id eq request.id.value }
            .firstOrNull()
        if (existing == null) {
            BuildRequestsTable.insert {
                it[id] = request.id.value
                it[tenantId] = request.tenantId.value
                it[moduleId] = request.moduleId
                it[reason] = request.reason
                it[status] = request.status.name
                it[quoteId] = request.quoteId
                it[deploymentId] = request.deploymentId
                it[createdAt] = request.createdAt ?: clock.now()
                it[briefMarkdown] = request.brief?.markdown
                it[briefJson] = request.brief?.json
                it[briefAt] = request.brief?.takenAt
                it[briefVersion] = request.briefVersion
                it[supersedes] = request.supersedes?.value
                it[supersededBy] = request.supersededBy?.value
            }
        } else {
            BuildRequestsTable.update({ BuildRequestsTable.id eq request.id.value }) {
                it[status] = request.status.name
                it[quoteId] = request.quoteId
                it[supersededBy] = request.supersededBy?.value
            }
        }
        request
    }

    private fun toRequest(row: ResultRow) = BuildRequest(
        id = BuildRequestId(row[BuildRequestsTable.id]),
        tenantId = TenantId(row[BuildRequestsTable.tenantId]),
        moduleId = row[BuildRequestsTable.moduleId],
        reason = row[BuildRequestsTable.reason],
        status = BuildRequestStatus.valueOf(row[BuildRequestsTable.status]),
        quoteId = row[BuildRequestsTable.quoteId],
        deploymentId = row[BuildRequestsTable.deploymentId],
        createdAt = row[BuildRequestsTable.createdAt],
        briefVersion = row[BuildRequestsTable.briefVersion],
        supersedes = row[BuildRequestsTable.supersedes]?.let(::BuildRequestId),
        supersededBy = row[BuildRequestsTable.supersededBy]?.let(::BuildRequestId),
        brief = row[BuildRequestsTable.briefMarkdown]?.let { md ->
            BriefSnapshot(md, row[BuildRequestsTable.briefJson].orEmpty(), requireNotNull(row[BuildRequestsTable.briefAt]) { "brief_at wajib bila brief ada" })
        }
    )
}

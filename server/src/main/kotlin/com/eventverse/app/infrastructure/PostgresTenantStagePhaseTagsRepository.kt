package com.eventverse.app.infrastructure

import com.eventverse.app.domain.process.StagePhaseTags
import com.eventverse.app.domain.process.TenantStagePhaseTagsRepository
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.TenantStagePhaseTagsTable
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.process.StagePhaseTagsCodec
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** Template tag fase per tenant (tabel V71). Satu baris per tenant, di-upsert. */
class PostgresTenantStagePhaseTagsRepository : TenantStagePhaseTagsRepository {

    override suspend fun findByTenantId(tenantId: TenantId): StagePhaseTags =
        DatabaseFactory.dbQuery(tenantId) {
            TenantStagePhaseTagsTable.selectAll()
                .where { TenantStagePhaseTagsTable.tenantId eq tenantId.value }
                .singleOrNull()
                ?.let { row -> runCatching { StagePhaseTagsCodec.decode(JsonParser.parse(row[TenantStagePhaseTagsTable.tags])) }.getOrNull() }
                ?: StagePhaseTags.DEFAULT
        }

    override suspend fun save(tenantId: TenantId, tags: StagePhaseTags): Result<StagePhaseTags> = runCatching {
        val normalized = tags.normalized
        val json = StagePhaseTagsCodec.encode(normalized).encode()
        DatabaseFactory.dbQuery(tenantId) {
            val now = Clock.System.now()
            val updated = TenantStagePhaseTagsTable.update({ TenantStagePhaseTagsTable.tenantId eq tenantId.value }) {
                it[TenantStagePhaseTagsTable.tags] = json
                it[updatedAt] = now
            }
            if (updated == 0) {
                TenantStagePhaseTagsTable.insert {
                    it[TenantStagePhaseTagsTable.tenantId] = tenantId.value
                    it[TenantStagePhaseTagsTable.tags] = json
                    it[updatedAt] = now
                }
            }
        }
        normalized
    }
}

package com.eventverse.app.infrastructure

import com.eventverse.app.domain.stageflow.TenantStageFlow
import com.eventverse.app.domain.stageflow.TenantStageFlowRepository
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.TenantStageFlowsTable
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.stageflow.TenantStageFlowCodec
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Kerangka tahap per tenant (tabel V72). Satu baris per tenant, di-upsert.
 *
 * Baris yang tidak bisa didekode **melempar**, tidak dianggap kosong: dianggap kosong berarti
 * use case akan mem-provision ulang dan menimpa kerangka yang sudah diubah tenant.
 */
class PostgresTenantStageFlowRepository : TenantStageFlowRepository {

    override suspend fun findByTenantId(tenantId: TenantId): TenantStageFlow? =
        DatabaseFactory.dbQuery(tenantId) {
            TenantStageFlowsTable.selectAll()
                .where { TenantStageFlowsTable.tenantId eq tenantId.value }
                .singleOrNull()
                ?.let { row ->
                    TenantStageFlowCodec.decode(
                        tenantId,
                        jsonObjectOf(
                            "template" to jsonOf(row[TenantStageFlowsTable.templateCode]),
                            "stages" to JsonParser.parse(row[TenantStageFlowsTable.stages])
                        )
                    )
                }
        }

    override suspend fun save(flow: TenantStageFlow): Result<TenantStageFlow> = runCatching {
        val stagesJson = TenantStageFlowCodec.encodeStages(flow.stages).encode()
        DatabaseFactory.dbQuery(flow.tenantId) {
            val now = Clock.System.now()
            val updated = TenantStageFlowsTable.update({ TenantStageFlowsTable.tenantId eq flow.tenantId.value }) {
                it[templateCode] = flow.template.name
                it[stages] = stagesJson
                it[updatedAt] = now
            }
            if (updated == 0) {
                TenantStageFlowsTable.insert {
                    it[tenantId] = flow.tenantId.value
                    it[templateCode] = flow.template.name
                    it[stages] = stagesJson
                    it[updatedAt] = now
                }
            }
        }
        flow
    }
}

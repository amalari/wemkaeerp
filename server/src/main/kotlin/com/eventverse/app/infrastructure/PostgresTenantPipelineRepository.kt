package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.TenantPipelinesTable
import com.eventverse.app.shared.pipeline.PipelineGraphCodec
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * PostgreSQL implementation of [TenantPipelineRepository] using Exposed, with tenant
 * isolation enforced by Row-Level Security (see `DatabaseFactory.dbQuery`).
 *
 * Topology is stored in the `graph_data` JSONB column and (de)serialised by
 * [PipelineGraphCodec], which the Compose client shares. The previous implementation
 * assembled and split that document with `substringAfter`, which broke against JSONB because
 * PostgreSQL normalises and reorders object keys on read.
 */
class PostgresTenantPipelineRepository : TenantPipelineRepository {

    override suspend fun findByTenantId(tenantId: TenantId): CustomTenantPipeline? =
        DatabaseFactory.dbQuery(tenantId) {
            TenantPipelinesTable.selectAll()
                .where { TenantPipelinesTable.tenantId eq tenantId.value }
                .map { toCustomPipeline(it) }
                .singleOrNull()
        }

    override suspend fun save(pipeline: CustomTenantPipeline): Result<CustomTenantPipeline> = runCatching {
        DatabaseFactory.dbQuery(pipeline.tenantId) {
            val graphDataJson = PipelineGraphCodec.encodeGraph(pipeline)

            val updatedRows = TenantPipelinesTable.update(
                { TenantPipelinesTable.tenantId eq pipeline.tenantId.value }
            ) {
                it[pipelineName] = pipeline.pipelineName
                it[basePreset] = pipeline.baseStarterPreset?.code?.value
                it[graphData] = graphDataJson
            }

            // An UPDATE that matched no row means this tenant has no pipeline yet. Deciding
            // from the update result rather than a preceding SELECT removes the race where
            // two concurrent saves both observe "absent" and both attempt an INSERT.
            if (updatedRows == 0) {
                TenantPipelinesTable.insert {
                    it[id] = "pipe-${pipeline.tenantId.value}"
                    it[tenantId] = pipeline.tenantId.value
                    it[pipelineName] = pipeline.pipelineName
                    it[basePreset] = pipeline.baseStarterPreset?.code?.value
                    it[graphData] = graphDataJson
                }
            }
            pipeline
        }
    }

    override suspend fun deleteByTenantId(tenantId: TenantId): Result<Unit> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            TenantPipelinesTable.deleteWhere { TenantPipelinesTable.tenantId eq tenantId.value }
        }
    }

    private fun toCustomPipeline(row: ResultRow): CustomTenantPipeline {
        val graph = PipelineGraphCodec.decodeGraph(row[TenantPipelinesTable.graphData])
        return CustomTenantPipeline(
            tenantId = TenantId(row[TenantPipelinesTable.tenantId]),
            pipelineName = row[TenantPipelinesTable.pipelineName],
            baseStarterPreset = row[TenantPipelinesTable.basePreset]
                ?.let { GarmentBlueprints.fromCodeOrDefault(it) },
            nodes = graph.nodes,
            edges = graph.edges
        )
    }
}

package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pipeline.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.TenantPipelinesTable
import com.eventverse.app.routes.dto.PipelineDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

/**
 * PostgreSQL implementation of TenantPipelineRepository using JetBrains Exposed and RLS.
 */
class PostgresTenantPipelineRepository : TenantPipelineRepository {

    override suspend fun findByTenantId(tenantId: TenantId): CustomTenantPipeline? = DatabaseFactory.dbQuery(tenantId) {
        TenantPipelinesTable.selectAll()
            .where { TenantPipelinesTable.tenantId eq tenantId.value }
            .map { toCustomPipeline(it) }
            .singleOrNull()
    }

    override suspend fun save(pipeline: CustomTenantPipeline): Result<CustomTenantPipeline> = runCatching {
        DatabaseFactory.dbQuery(pipeline.tenantId) {
            val exists = TenantPipelinesTable.selectAll()
                .where { TenantPipelinesTable.tenantId eq pipeline.tenantId.value }
                .count() > 0

            val graphDataJson = "{\"nodes\":${PipelineDto.nodesToJson(pipeline.nodes)},\"edges\":${PipelineDto.edgesToJson(pipeline.edges)}}"
            val pipelineId = "pipe-${pipeline.tenantId.value}"

            if (exists) {
                TenantPipelinesTable.update({ TenantPipelinesTable.tenantId eq pipeline.tenantId.value }) {
                    it[pipelineName] = pipeline.pipelineName
                    it[basePreset] = pipeline.baseStarterPreset?.code
                    it[graphData] = graphDataJson
                }
            } else {
                TenantPipelinesTable.insert {
                    it[id] = pipelineId
                    it[tenantId] = pipeline.tenantId.value
                    it[pipelineName] = pipeline.pipelineName
                    it[basePreset] = pipeline.baseStarterPreset?.code
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
        val tId = TenantId(row[TenantPipelinesTable.tenantId])
        val pName = row[TenantPipelinesTable.pipelineName]
        val presetCode = row[TenantPipelinesTable.basePreset]
        val preset = presetCode?.let { GarmentBusinessPreset.fromCode(it) }
        val rawGraph = row[TenantPipelinesTable.graphData]

        val nodesPart = rawGraph.substringAfter("\"nodes\":", "").substringBefore(",\"edges\":")
        val edgesPart = rawGraph.substringAfter("\"edges\":", "")

        val nodes = if (nodesPart.isNotBlank()) PipelineDto.parseNodes(nodesPart) else emptyList()
        val edges = if (edgesPart.isNotBlank()) PipelineDto.parseEdges(edgesPart) else emptyList()

        return CustomTenantPipeline(
            tenantId = tId,
            pipelineName = pName,
            baseStarterPreset = preset,
            nodes = nodes,
            edges = edges
        )
    }
}

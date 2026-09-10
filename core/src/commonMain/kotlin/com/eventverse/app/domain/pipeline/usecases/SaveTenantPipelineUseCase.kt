package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.pipeline.TenantPipelineRepository

/**
 * UseCase for validating and persisting custom tenant pipeline workflows.
 *
 * Validates that the graph is structurally sound (non-empty, unique node IDs, edges pointing
 * at real nodes) and — when an [TenantModuleEntitlement] is supplied — that the topology
 * stays within what the tenant's subscription plan grants.
 */
class SaveTenantPipelineUseCase(
    private val pipelineRepository: TenantPipelineRepository
) {
    suspend operator fun invoke(
        pipeline: CustomTenantPipeline,
        entitlement: TenantModuleEntitlement? = null
    ): Result<CustomTenantPipeline> = runCatching {
        require(pipeline.nodes.isNotEmpty()) { "Pipeline must contain at least one operational node" }
        require(pipeline.pipelineName.isNotBlank()) { "Pipeline name cannot be blank" }

        val nodeIds = pipeline.nodes.map { it.nodeId }
        val duplicateIds = nodeIds.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        require(duplicateIds.isEmpty()) { "Duplicate node IDs detected in pipeline: $duplicateIds" }

        val duplicateEdgeIds = pipeline.edges
            .groupingBy { it.edgeId }.eachCount().filter { it.value > 1 }.keys
        require(duplicateEdgeIds.isEmpty()) { "Duplicate edge IDs detected in pipeline: $duplicateEdgeIds" }

        // Ensure edges reference valid existing node IDs
        val validNodeIdSet = nodeIds.toSet()
        pipeline.edges.forEach { edge ->
            require(validNodeIdSet.contains(edge.fromNodeId)) {
                "Edge ${edge.edgeId} references non-existent source node: ${edge.fromNodeId}"
            }
            require(validNodeIdSet.contains(edge.toNodeId)) {
                "Edge ${edge.edgeId} references non-existent target node: ${edge.toNodeId}"
            }
        }

        require(pipeline.activeNodes.isNotEmpty()) {
            "Pipeline must keep at least one active (non-bypassed) module"
        }

        if (entitlement != null) {
            val violations = entitlement.validate(pipeline)
            require(violations.isEmpty()) {
                "Konfigurasi alur melebihi paket langganan: ${violations.joinToString(" ")}"
            }
        }

        pipelineRepository.save(pipeline).getOrThrow()
    }
}

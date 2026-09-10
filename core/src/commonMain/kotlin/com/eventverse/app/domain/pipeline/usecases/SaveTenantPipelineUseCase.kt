package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.TenantPipelineRepository

/**
 * UseCase for validating and persisting custom tenant pipeline workflows.
 * Verifies that the graph is structurally sound (nodes not empty, unique node IDs).
 */
class SaveTenantPipelineUseCase(
    private val pipelineRepository: TenantPipelineRepository
) {
    suspend operator fun invoke(pipeline: CustomTenantPipeline): Result<CustomTenantPipeline> = runCatching {
        require(pipeline.nodes.isNotEmpty()) { "Pipeline must contain at least one operational node" }
        require(pipeline.pipelineName.isNotBlank()) { "Pipeline name cannot be blank" }

        val nodeIds = pipeline.nodes.map { it.nodeId }
        val duplicateIds = nodeIds.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        require(duplicateIds.isEmpty()) { "Duplicate node IDs detected in pipeline: $duplicateIds" }

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

        pipelineRepository.save(pipeline).getOrThrow()
    }
}

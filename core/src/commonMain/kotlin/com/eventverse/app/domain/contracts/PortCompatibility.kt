package com.eventverse.app.domain.contracts

import com.eventverse.app.domain.pack.DomainPackRegistry

import com.eventverse.app.domain.pipeline.CustomTenantPipeline

enum class PortMismatchSeverity {
    BLOCKING,
    ADAPTED,
    UNKNOWN_TYPE
}

data class PortMismatch(
    val edgeId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val producedType: String,
    val acceptedTypes: Set<String>,
    val severity: PortMismatchSeverity,
    val message: String
)

object PortCompatibility {
    const val WILDCARD = "AnyOperationalPayload"

    val KNOWN_ADAPTERS: List<PortAdapterDescriptor> = listOf(
        SampleSpecToTechPackAdapter.descriptor
    )

    fun isCompatible(producedType: String, acceptedTypes: Collection<String>): Boolean {
        if (producedType == WILDCARD || acceptedTypes.contains(WILDCARD)) return true
        return acceptedTypes.contains(producedType)
    }

    fun adapterPathFor(from: String, to: String): PortAdapterDescriptor? {
        return KNOWN_ADAPTERS.firstOrNull { it.from == from && it.to == to }
    }

    fun isConnectable(producedType: String, acceptedTypes: Collection<String>): Boolean {
        if (isCompatible(producedType, acceptedTypes)) return true
        return acceptedTypes.any { target -> adapterPathFor(producedType, target) != null }
    }

    fun validate(pipeline: CustomTenantPipeline): List<PortMismatch> {
        val mismatches = mutableListOf<PortMismatch>()
        val nodeMap = pipeline.nodes.associateBy { it.nodeId }

        for (edge in pipeline.edges) {
            val fromNode = nodeMap[edge.fromNodeId] ?: continue
            val toNode = nodeMap[edge.toNodeId] ?: continue

            val producedType = fromNode.archetype.defaultProducedOutputType
            val acceptedTypes = setOf(toNode.archetype.defaultExpectedInputType)

            if (producedType == WILDCARD || acceptedTypes.contains(WILDCARD)) {
                continue
            }

            val pack = DomainPackRegistry.soleActivePack
            if (!pack.isWired(producedType) || !pack.isWired(toNode.archetype.defaultExpectedInputType)) {
                mismatches += PortMismatch(
                    edgeId = edge.edgeId,
                    fromNodeId = fromNode.nodeId,
                    toNodeId = toNode.nodeId,
                    producedType = producedType,
                    acceptedTypes = acceptedTypes,
                    severity = PortMismatchSeverity.UNKNOWN_TYPE,
                    message = "Tipe data port belum terdefinisi secara baku: '$producedType' -> '${toNode.archetype.defaultExpectedInputType}'"
                )
                continue
            }

            if (isCompatible(producedType, acceptedTypes)) {
                continue
            }

            val adapter = adapterPathFor(producedType, toNode.archetype.defaultExpectedInputType)
            if (adapter != null) {
                mismatches += PortMismatch(
                    edgeId = edge.edgeId,
                    fromNodeId = fromNode.nodeId,
                    toNodeId = toNode.nodeId,
                    producedType = producedType,
                    acceptedTypes = acceptedTypes,
                    severity = PortMismatchSeverity.ADAPTED,
                    message = adapter.explanation
                )
            } else {
                mismatches += PortMismatch(
                    edgeId = edge.edgeId,
                    fromNodeId = fromNode.nodeId,
                    toNodeId = toNode.nodeId,
                    producedType = producedType,
                    acceptedTypes = acceptedTypes,
                    severity = PortMismatchSeverity.BLOCKING,
                    message = "Port tidak kompatibel: '${fromNode.customDisplayName}' memproduksi '$producedType', tetapi '${toNode.customDisplayName}' mengharapkan '${toNode.archetype.defaultExpectedInputType}'"
                )
            }
        }
        return mismatches
    }
}

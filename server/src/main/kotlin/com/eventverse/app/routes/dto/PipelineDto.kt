package com.eventverse.app.routes.dto

import com.eventverse.app.domain.pipeline.*
import com.eventverse.app.domain.tenant.TenantId

object PipelineDto {

    fun toJson(pipeline: CustomTenantPipeline): String {
        val presetStr = pipeline.baseStarterPreset?.code?.let { "\"$it\"" } ?: "null"
        val nodesJson = nodesToJson(pipeline.nodes)
        val edgesJson = edgesToJson(pipeline.edges)
        val nameEscaped = escape(pipeline.pipelineName)

        return "{\"tenantId\":\"${pipeline.tenantId.value}\",\"pipelineName\":\"$nameEscaped\",\"baseStarterPreset\":$presetStr,\"nodes\":$nodesJson,\"edges\":$edgesJson}"
    }

    fun nodesToJson(nodes: List<CustomPipelineNode>): String {
        return "[${nodes.joinToString(",") { node ->
            val titleEscaped = escape(node.customDisplayName)
            val paramsJson = "{" + node.customFormulaParameters.entries.joinToString(",") { 
                "\"${escape(it.key)}\":\"${escape(it.value)}\"" 
            } + "}"
            "{\"nodeId\":\"${node.nodeId}\",\"moduleId\":\"${node.moduleId}\",\"customDisplayName\":\"$titleEscaped\",\"archetype\":\"${node.archetype.code}\",\"isBypassed\":${node.isBypassed},\"stepOrderIndex\":${node.stepOrderIndex},\"customFormulaParameters\":$paramsJson}"
        }}]"
    }

    fun edgesToJson(edges: List<CustomPipelineEdge>): String {
        return "[${edges.joinToString(",") { edge ->
            "{\"edgeId\":\"${edge.edgeId}\",\"fromNodeId\":\"${edge.fromNodeId}\",\"toNodeId\":\"${edge.toNodeId}\",\"expectedDataType\":\"${escape(edge.expectedDataType)}\",\"isFeedbackReworkLoop\":${edge.isFeedbackReworkLoop}}"
        }}]"
    }

    fun splitJsonObjects(arrayJson: String): List<String> {
        val list = mutableListOf<String>()
        var depth = 0
        var startIndex = -1
        var inQuotes = false
        var escapeNext = false

        for (i in arrayJson.indices) {
            val c = arrayJson[i]
            if (escapeNext) {
                escapeNext = false
                continue
            }
            if (c == '\\') {
                escapeNext = true
                continue
            }
            if (c == '"') {
                inQuotes = !inQuotes
                continue
            }
            if (!inQuotes) {
                if (c == '{') {
                    if (depth == 0) startIndex = i
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && startIndex != -1) {
                        list.add(arrayJson.substring(startIndex, i + 1))
                        startIndex = -1
                    }
                }
            }
        }
        return list
    }

    fun parseNodes(nodesJson: String): List<CustomPipelineNode> {
        val result = mutableListOf<CustomPipelineNode>()
        val blocks = splitJsonObjects(nodesJson)

        for (block in blocks) {
            val nodeId = extractString(block, "nodeId") ?: continue
            val moduleId = extractString(block, "moduleId") ?: ""
            val title = extractString(block, "customDisplayName") ?: ""
            val archetypeCode = extractString(block, "archetype") ?: "custom_extension"
            val archetype = ModuleArchetype.entries.firstOrNull { it.code == archetypeCode } ?: ModuleArchetype.CUSTOM_EXTENSION
            val isBypassed = extractBoolean(block, "isBypassed") ?: false
            val stepIndex = extractInt(block, "stepOrderIndex") ?: 0

            result.add(
                CustomPipelineNode(
                    nodeId = nodeId,
                    moduleId = moduleId,
                    customDisplayName = title,
                    archetype = archetype,
                    isBypassed = isBypassed,
                    stepOrderIndex = stepIndex
                )
            )
        }
        return result
    }

    fun parseEdges(edgesJson: String): List<CustomPipelineEdge> {
        val result = mutableListOf<CustomPipelineEdge>()
        val blocks = splitJsonObjects(edgesJson)

        for (block in blocks) {
            val edgeId = extractString(block, "edgeId") ?: continue
            val from = extractString(block, "fromNodeId") ?: continue
            val to = extractString(block, "toNodeId") ?: continue
            val expected = extractString(block, "expectedDataType") ?: "StandardPayload"
            val isRework = extractBoolean(block, "isFeedbackReworkLoop") ?: false

            result.add(
                CustomPipelineEdge(
                    edgeId = edgeId,
                    fromNodeId = from,
                    toNodeId = to,
                    expectedDataType = expected,
                    isFeedbackReworkLoop = isRework
                )
            )
        }
        return result
    }

    fun fromJson(tenantId: TenantId, jsonStr: String): CustomTenantPipeline {
        val name = extractString(jsonStr, "pipelineName") ?: "Alur Kerja Kustom"
        val presetCode = extractString(jsonStr, "baseStarterPreset")
        val preset = presetCode?.let { GarmentBusinessPreset.fromCode(it) }

        val nodesPart = jsonStr.substringAfter("\"nodes\":", "").substringBefore(",\"edges\":")
        val edgesPart = jsonStr.substringAfter("\"edges\":", "")

        val nodes = if (nodesPart.isNotBlank()) parseNodes(nodesPart) else emptyList()
        val edges = if (edgesPart.isNotBlank()) parseEdges(edgesPart) else emptyList()

        return CustomTenantPipeline(
            tenantId = tenantId,
            pipelineName = name,
            baseStarterPreset = preset,
            nodes = nodes,
            edges = edges
        )
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

    private fun extractString(json: String, field: String): String? {
        val regex = "\"$field\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractBoolean(json: String, field: String): Boolean? {
        val regex = "\"$field\"\\s*:\\s*(true|false)".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toBoolean()
    }

    private fun extractInt(json: String, field: String): Int? {
        val regex = "\"$field\"\\s*:\\s*([0-9]+)".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }
}

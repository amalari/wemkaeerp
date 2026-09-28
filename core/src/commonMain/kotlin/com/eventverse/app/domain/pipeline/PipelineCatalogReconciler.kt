package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.BusinessModule

/**
 * Brings a tenant's persisted topology up to date with [OperationalModuleCatalog].
 *
 * A pipeline is provisioned once from a preset and then persisted, so a module added to the
 * catalogue afterwards never reached tenants that already existed — the canvas kept drawing
 * the catalogue as it was on the day the tenant was created. This closes that gap without
 * the destructive "reset to preset", which would throw away the tenant's names and bypasses.
 *
 * Every missing module is inserted **bypassed**: syncing makes a module visible and ready to
 * switch on, but never changes what the factory actually runs or what counts against its plan.
 */
object PipelineCatalogReconciler {

    /**
     * Returns [pipeline] itself (same instance) when nothing is missing, so callers can skip a
     * write with a plain identity check.
     */
    fun reconcile(
        pipeline: CustomTenantPipeline,
        grantedModules: Set<BusinessModule>
    ): CustomTenantPipeline {
        val catalogOrder = OperationalModuleCatalog.all.map { it.module }
        val installedCodes = pipeline.nodes.map { it.moduleId }.toSet()
        val missing = catalogOrder.filter { it in grantedModules && it.code !in installedCodes }
        if (missing.isEmpty()) return pipeline

        val ordered = pipeline.orderedNodes.toMutableList()
        missing.forEach { module ->
            ordered.add(insertionIndex(ordered, module, catalogOrder), missingNode(module))
        }

        return pipeline.copy(
            nodes = ordered.mapIndexed { index, node -> node.copy(stepOrderIndex = index + 1) }
        )
    }

    /**
     * Places a module right after the last node that precedes it in catalogue order, so a new
     * sewing-stage module lands among the sewing modules instead of at the end of the flow.
     * Custom plugins have no catalogue position and are stepped over.
     */
    private fun insertionIndex(
        ordered: List<CustomPipelineNode>,
        module: BusinessModule,
        catalogOrder: List<BusinessModule>
    ): Int {
        val position = catalogOrder.indexOf(module)
        val predecessor = ordered.indexOfLast { node ->
            val nodePosition = node.standardModule?.let(catalogOrder::indexOf) ?: -1
            nodePosition in 0 until position
        }
        return predecessor + 1
    }

    private fun missingNode(module: BusinessModule): CustomPipelineNode {
        val specification = OperationalModuleCatalog.specificationFor(module)
        return CustomPipelineNode(
            nodeId = "node-${module.code}",
            moduleId = module.code,
            customDisplayName = module.displayName,
            archetype = specification.archetype,
            isBypassed = true
        )
    }
}

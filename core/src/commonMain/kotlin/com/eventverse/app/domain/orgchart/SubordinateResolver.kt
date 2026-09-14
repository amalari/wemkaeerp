package com.eventverse.app.domain.orgchart

/**
 * Computes which employees fall within a viewer's reach under a hierarchical data scope.
 *
 * Extracted out of [OrgChartVisibility] (which used to keep `subordinateClosure` private)
 * because it is no longer only the org chart's concern: any module declaring
 * [com.eventverse.app.domain.rbac.ScopeCapability.HIERARCHICAL] — CRM Leads included — needs
 * the exact same "self ∪ same department ∪ command-chain closure" reach set to enforce
 * `DataScope.SUBORDINATE_DATA` on its own rows. Copy-pasting the closure into a second file
 * would be the fastest way to end up with two divergent definitions of "bawahan", and a
 * divergence there is a data leak, not a cosmetic bug.
 *
 * Pure domain service: no I/O, no framework. The same reason [OrgChartVisibility] states —
 * this has to run twice (server as the authority, client for a consistent screen) and one
 * function shared by both callers makes the two impossible to diverge.
 */
object SubordinateResolver {

    /**
     * The set of employee ids a viewer with [viewerEmployeeId] and [viewerDepartmentId] may
     * see under `SUBORDINATE_DATA`: themselves, everyone in their own department, and every
     * indirect report down the `reportsToId` chain (which may cross department lines).
     *
     * @param nodes every employee of one tenant (tenant isolation happens above this layer)
     */
    fun reachableEmployeeIds(
        nodes: List<OrgNode>,
        viewerEmployeeId: OrgNodeId?,
        viewerDepartmentId: String?
    ): Set<OrgNodeId> {
        val byDepartment = viewerDepartmentId
            ?.let { deptId -> nodes.filter { it.department?.id?.value == deptId } }
            .orEmpty()

        val byCommandChain = viewerEmployeeId
            ?.let { subordinateClosure(nodes, it) }
            .orEmpty()

        val self = viewerEmployeeId?.let { id -> nodes.filter { it.id == id } }.orEmpty()

        return (self + byDepartment + byCommandChain).map { it.id }.toSet()
    }

    /**
     * Every subordinate of [rootId] all the way down, following [OrgNode.reportsToId].
     *
     * Traversed iteratively with a "visited" set, not recursively. Hierarchy data is
     * hand-typed and can contain cycles (A reports to B, B reports to A) from a data-entry
     * mistake; a naive recursive traversal would hang forever instead of showing a slightly
     * wrong chart.
     */
    fun subordinateClosure(nodes: List<OrgNode>, rootId: OrgNodeId): List<OrgNode> {
        val childrenByParent: Map<String, List<OrgNode>> = nodes
            .mapNotNull { node -> node.reportsToId?.let { it.value to node } }
            .groupBy({ it.first }, { it.second })

        val collected = mutableListOf<OrgNode>()
        val visited = mutableSetOf(rootId.value)
        val queue = ArrayDeque<String>().apply { add(rootId.value) }

        while (queue.isNotEmpty()) {
            val parentId = queue.removeFirst()
            childrenByParent[parentId].orEmpty().forEach { child ->
                if (visited.add(child.id.value)) {
                    collected += child
                    queue.add(child.id.value)
                }
            }
        }

        return collected
    }
}

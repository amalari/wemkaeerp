package com.eventverse.app.domain.orgchart

import com.eventverse.app.domain.tenant.TenantId
import kotlin.jvm.JvmInline

@JvmInline
value class OrgNodeId(val value: String) {
    init {
        require(value.isNotBlank()) { "OrgNodeId cannot be blank" }
    }
}

/**
 * Data entity representing an employee node in the factory organizational tree.
 */
data class OrgNode(
    val id: OrgNodeId,
    val name: String,
    val email: String,
    val department: Department?,
    val level: HierarchyLevel,
    val roleTitle: String,
    val reportsToId: OrgNodeId? = null,
    val phone: String = "",
    val tenantId: TenantId? = null,
    val tierName: String? = null,
    /** Null = karyawan aktif. Non-null = sudah diarsipkan (pola Odoo). Format: ISO-8601 string. */
    val archivedAt: String? = null
) {
    init {
        require(name.isNotBlank()) { "Employee name cannot be blank" }
    }

    /** True jika karyawan sudah diarsipkan dan tidak aktif. */
    val isArchived: Boolean get() = archivedAt != null

    val avatarInitial: String
        get() = name.trim().split("\\s+".toRegex())
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .joinToString("")
            .ifBlank { "WM" }

    companion object {
        /**
         * Standard sample data for a garment factory organization tree.
         * Scoped by tenantId to prevent primary key collision across multi-tenant database.
         */
        fun createSampleEmployees(tenantId: TenantId? = null): List<OrgNode> {
            val prefix = if (tenantId == null || tenantId.value == "ten-demo-001") "" else "${tenantId.value}-"
            val hendraId = OrgNodeId("emp-${prefix}hendra")
            val budiId = OrgNodeId("emp-${prefix}budi")
            val jokoId = OrgNodeId("emp-${prefix}joko")
            val sitiId = OrgNodeId("emp-${prefix}siti")
            val antonId = OrgNodeId("emp-${prefix}anton")
            val rianId = OrgNodeId("emp-${prefix}rian")
            val dediId = OrgNodeId("emp-${prefix}dedi")
            val mayaId = OrgNodeId("emp-${prefix}maya")
            val agusId = OrgNodeId("emp-${prefix}agus")
            val bambangId = OrgNodeId("emp-${prefix}bambang")

            return listOf(
                // 1. Executive / Owner (Direksi berdiri di puncak struktur, TIDAK memiliki divisi)
                OrgNode(
                    id = hendraId,
                    name = "Bpk. Hendra Kusuma",
                    email = "hendra.owner@wemade.id",
                    department = null,
                    level = HierarchyLevel.EXECUTIVE,
                    roleTitle = "Direktur Utama / Owner",
                    reportsToId = null,
                    phone = "081122334455",
                    tenantId = tenantId
                ),
                // 2. Department Heads (Directly reporting to Hendra)
                OrgNode(
                    id = budiId,
                    name = "Budi Santoso",
                    email = "budi.sales@wemade.id",
                    department = Department.SALES,
                    level = HierarchyLevel.HEAD_OF_DEPARTMENT,
                    tierName = "Kepala Divisi",
                    roleTitle = "Head of Sales & Marketing",
                    reportsToId = hendraId,
                    phone = "081234567890",
                    tenantId = tenantId
                ),
                OrgNode(
                    id = jokoId,
                    name = "Joko Susilo",
                    email = "joko.ppic@wemade.id",
                    department = Department.PRODUCTION_PPIC,
                    level = HierarchyLevel.HEAD_OF_DEPARTMENT,
                    tierName = "Kepala Divisi",
                    roleTitle = "Kepala Produksi & PPIC",
                    reportsToId = hendraId,
                    phone = "081398765432",
                    tenantId = tenantId
                ),
                OrgNode(
                    id = sitiId,
                    name = "Siti Rahma",
                    email = "siti.gudang@wemade.id",
                    department = Department.WAREHOUSE,
                    level = HierarchyLevel.HEAD_OF_DEPARTMENT,
                    tierName = "Kepala Divisi",
                    roleTitle = "Kepala Gudang & Logistik",
                    reportsToId = hendraId,
                    phone = "081711223344",
                    tenantId = tenantId
                ),
                OrgNode(
                    id = antonId,
                    name = "Anton Prasetyo",
                    email = "anton.qc@wemade.id",
                    department = Department.QUALITY_CONTROL,
                    level = HierarchyLevel.HEAD_OF_DEPARTMENT,
                    tierName = "Kepala Divisi",
                    roleTitle = "Kepala Quality Control (QC)",
                    reportsToId = hendraId,
                    phone = "081855667788",
                    tenantId = tenantId
                ),
                // 3. Sales Staff (Reporting to Budi Santoso)
                OrgNode(
                    id = rianId,
                    name = "Rian Firmansyah",
                    email = "rian.sales@wemade.id",
                    department = Department.SALES,
                    level = HierarchyLevel.STAFF_OPERATOR,
                    tierName = "Staf Pelaksana / Operator",
                    roleTitle = "Sales Eksekutif Lapangan",
                    reportsToId = budiId,
                    phone = "082111223344",
                    tenantId = tenantId
                ),
                OrgNode(
                    id = dediId,
                    name = "Dedi Kurniawan",
                    email = "dedi.tender@wemade.id",
                    department = Department.SALES,
                    level = HierarchyLevel.STAFF_OPERATOR,
                    tierName = "Staf Pelaksana / Operator",
                    roleTitle = "Sales Tender & Korporat",
                    reportsToId = budiId,
                    phone = "082199887766",
                    tenantId = tenantId
                ),
                OrgNode(
                    id = mayaId,
                    name = "Maya Anggraini",
                    email = "maya.sample@wemade.id",
                    department = Department.SALES,
                    level = HierarchyLevel.STAFF_OPERATOR,
                    tierName = "Staf Pelaksana / Operator",
                    roleTitle = "Admin Sampling & CS",
                    reportsToId = budiId,
                    phone = "082133445566",
                    tenantId = tenantId
                ),
                // 4. Production Tier: Team Lead / Mandor (Reporting to Joko)
                OrgNode(
                    id = agusId,
                    name = "Agus Setiawan",
                    email = "agus.cutting@wemade.id",
                    department = Department.PRODUCTION_PPIC,
                    level = HierarchyLevel.TEAM_LEAD,
                    tierName = "Kepala Tim / Mandor",
                    roleTitle = "Mandor Meja Potong & Pola",
                    reportsToId = jokoId,
                    phone = "085211223344",
                    tenantId = tenantId
                ),
                // 5. Production Tier: Operator (Reporting to Agus)
                OrgNode(
                    id = bambangId,
                    name = "Bambang Wijaya",
                    email = "bambang.sewing@wemade.id",
                    department = Department.PRODUCTION_PPIC,
                    level = HierarchyLevel.STAFF_OPERATOR,
                    tierName = "Operator / Staf",
                    roleTitle = "Operator Jahit & Overdeck",
                    reportsToId = agusId,
                    phone = "085277889900",
                    tenantId = tenantId
                )
            )
        }

        /**
         * Resolves the T-Shape Hierarchy structure for a given focus node.
         * Enforces the rule:
         * - 1 level up to direct superior
         * - Horizontal peer heads
         * - Full division subordinates down
         */
        fun resolveTShapeView(
            nodes: List<OrgNode>,
            focusNode: OrgNode,
            isDraft: Boolean = false,
            successionAction: HeadSuccessionAction = HeadSuccessionAction.DEMOTE_TO_STAFF
        ): TShapeHierarchyResult {
            val superior = if (focusNode.reportsToId != null) {
                nodes.find { it.id == focusNode.reportsToId }
            } else if (focusNode.level != HierarchyLevel.EXECUTIVE) {
                nodes.find { it.level == HierarchyLevel.EXECUTIVE }
            } else null

            return when (focusNode.level) {
                HierarchyLevel.HEAD_OF_DEPARTMENT -> {
                    // Peer heads are heads of OTHER departments
                    val peerHeads = nodes.filter {
                        it.level == HierarchyLevel.HEAD_OF_DEPARTMENT &&
                        it.department != null &&
                        it.department != focusNode.department &&
                        it.id != focusNode.id
                    }

                    // Existing head in the same department (if any)
                    val existingHeadInSameDept = nodes.find {
                        it.level == HierarchyLevel.HEAD_OF_DEPARTMENT &&
                        it.department != null &&
                        it.department == focusNode.department &&
                        it.id != focusNode.id
                    }

                    val existingSubordinates = nodes.filter {
                        it.department != null &&
                        it.department == focusNode.department &&
                        (it.level == HierarchyLevel.TEAM_LEAD || it.level == HierarchyLevel.STAFF_OPERATOR) &&
                        it.id != focusNode.id
                    }

                    val subordinates = if (existingHeadInSameDept != null) {
                        when (successionAction) {
                            HeadSuccessionAction.DEMOTE_TO_STAFF -> {
                                val demotedOldHead = existingHeadInSameDept.copy(
                                    level = HierarchyLevel.STAFF_OPERATOR,
                                    roleTitle = "Staf Senior ${focusNode.department?.shortName ?: ""}",
                                    reportsToId = focusNode.id
                                )
                                listOf(demotedOldHead) + existingSubordinates
                            }
                            HeadSuccessionAction.DEACTIVATE -> existingSubordinates
                        }
                    } else {
                        existingSubordinates
                    }

                    TShapeHierarchyResult(
                        superior = superior,
                        peerHeads = peerHeads,
                        focusNode = focusNode,
                        subordinates = subordinates,
                        peersInDepartment = emptyList(),
                        isDraft = isDraft,
                        orderedDepartmentMembers = listOf(focusNode)
                    )
                }

                HierarchyLevel.TEAM_LEAD -> {
                    val allLeads = nodes.filter {
                        it.department != null &&
                        it.department == focusNode.department &&
                        it.level == HierarchyLevel.TEAM_LEAD
                    }
                    val peersInDept = allLeads.filter { it.id != focusNode.id }
                    val subordinates = nodes.filter {
                        it.reportsToId == focusNode.id || (it.department != null && it.department == focusNode.department && it.level == HierarchyLevel.STAFF_OPERATOR)
                    }
                    val orderedMembers = if (isDraft) {
                        allLeads + focusNode
                    } else {
                        if (allLeads.any { it.id == focusNode.id }) {
                            allLeads.map { if (it.id == focusNode.id) focusNode else it }
                        } else {
                            allLeads + focusNode
                        }
                    }
                    TShapeHierarchyResult(
                        superior = superior,
                        peerHeads = emptyList(),
                        focusNode = focusNode,
                        subordinates = subordinates,
                        peersInDepartment = peersInDept,
                        isDraft = isDraft,
                        orderedDepartmentMembers = orderedMembers
                    )
                }

                HierarchyLevel.STAFF_OPERATOR -> {
                    val allStaffInDept = nodes.filter {
                        it.department != null &&
                        it.department == focusNode.department &&
                        it.level == HierarchyLevel.STAFF_OPERATOR
                    }
                    val peersInDept = allStaffInDept.filter { it.id != focusNode.id }
                    val orderedMembers = if (isDraft) {
                        allStaffInDept + focusNode
                    } else {
                        if (allStaffInDept.any { it.id == focusNode.id }) {
                            allStaffInDept.map { if (it.id == focusNode.id) focusNode else it }
                        } else {
                            allStaffInDept + focusNode
                        }
                    }
                    TShapeHierarchyResult(
                        superior = superior,
                        peerHeads = emptyList(),
                        focusNode = focusNode,
                        subordinates = emptyList(),
                        peersInDepartment = peersInDept,
                        isDraft = isDraft,
                        orderedDepartmentMembers = orderedMembers
                    )
                }

                HierarchyLevel.EXECUTIVE -> {
                    val allExecutives = nodes.filter { it.level == HierarchyLevel.EXECUTIVE }
                    val peerExecutives = allExecutives.filter { it.id != focusNode.id }
                    val allDirectReports = nodes.filter {
                        it.level == HierarchyLevel.HEAD_OF_DEPARTMENT || (it.reportsToId == focusNode.id && it.level != HierarchyLevel.EXECUTIVE)
                    }
                    val orderedMembers = if (isDraft) {
                        allExecutives + focusNode
                    } else {
                        if (allExecutives.any { it.id == focusNode.id }) {
                            allExecutives.map { if (it.id == focusNode.id) focusNode else it }
                        } else {
                            allExecutives + focusNode
                        }
                    }
                    TShapeHierarchyResult(
                        superior = null,
                        peerHeads = emptyList(),
                        focusNode = focusNode,
                        subordinates = allDirectReports,
                        peersInDepartment = peerExecutives,
                        isDraft = isDraft,
                        orderedDepartmentMembers = orderedMembers
                    )
                }
            }
        }
    }
}

/**
 * Result model describing the T-Shape view ready for rendering.
 */
data class TShapeHierarchyResult(
    val superior: OrgNode?,
    val peerHeads: List<OrgNode>,
    val focusNode: OrgNode,
    val subordinates: List<OrgNode>,
    val peersInDepartment: List<OrgNode>,
    val isDraft: Boolean = false,
    val orderedDepartmentMembers: List<OrgNode> = emptyList()
)

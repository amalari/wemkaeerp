package com.eventverse.app.domain.orgchart

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
    val department: Department,
    val level: HierarchyLevel,
    val roleTitle: String,
    val reportsToId: OrgNodeId? = null,
    val phone: String = ""
) {
    init {
        require(name.isNotBlank()) { "Employee name cannot be blank" }
    }

    val avatarInitial: String
        get() = name.trim().split("\\s+".toRegex())
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .joinToString("")
            .ifBlank { "WM" }

    companion object {
        /**
         * Standard sample data for a garment factory organization tree.
         */
        fun createSampleEmployees(): List<OrgNode> {
            val hendraId = OrgNodeId("emp-hendra")
            val budiId = OrgNodeId("emp-budi")
            val jokoId = OrgNodeId("emp-joko")
            val sitiId = OrgNodeId("emp-siti")
            val antonId = OrgNodeId("emp-anton")

            return listOf(
                // 1. Executive / Owner
                OrgNode(
                    id = hendraId,
                    name = "Bpk. Hendra Kusuma",
                    email = "hendra.owner@wemade.id",
                    department = Department.FINANCE_EXECUTIVE,
                    level = HierarchyLevel.EXECUTIVE,
                    roleTitle = "Direktur Utama / Owner",
                    reportsToId = null,
                    phone = "081122334455"
                ),
                // 2. Department Heads (Directly reporting to Hendra)
                OrgNode(
                    id = budiId,
                    name = "Budi Santoso",
                    email = "budi.sales@wemade.id",
                    department = Department.SALES,
                    level = HierarchyLevel.HEAD_OF_DEPARTMENT,
                    roleTitle = "Head of Sales & Marketing",
                    reportsToId = hendraId,
                    phone = "081234567890"
                ),
                OrgNode(
                    id = jokoId,
                    name = "Joko Susilo",
                    email = "joko.ppic@wemade.id",
                    department = Department.PRODUCTION_PPIC,
                    level = HierarchyLevel.HEAD_OF_DEPARTMENT,
                    roleTitle = "Kepala Produksi & PPIC",
                    reportsToId = hendraId,
                    phone = "081398765432"
                ),
                OrgNode(
                    id = sitiId,
                    name = "Siti Rahma",
                    email = "siti.gudang@wemade.id",
                    department = Department.WAREHOUSE,
                    level = HierarchyLevel.HEAD_OF_DEPARTMENT,
                    roleTitle = "Kepala Gudang & Logistik",
                    reportsToId = hendraId,
                    phone = "081711223344"
                ),
                OrgNode(
                    id = antonId,
                    name = "Anton Prasetyo",
                    email = "anton.qc@wemade.id",
                    department = Department.QUALITY_CONTROL,
                    level = HierarchyLevel.HEAD_OF_DEPARTMENT,
                    roleTitle = "Kepala Quality Control (QC)",
                    reportsToId = hendraId,
                    phone = "081855667788"
                ),
                // 3. Sales Staff (Reporting to Budi Santoso)
                OrgNode(
                    id = OrgNodeId("emp-rian"),
                    name = "Rian Firmansyah",
                    email = "rian.sales@wemade.id",
                    department = Department.SALES,
                    level = HierarchyLevel.STAFF_OPERATOR,
                    roleTitle = "Sales Eksekutif Lapangan",
                    reportsToId = budiId,
                    phone = "082111223344"
                ),
                OrgNode(
                    id = OrgNodeId("emp-dedi"),
                    name = "Dedi Kurniawan",
                    email = "dedi.tender@wemade.id",
                    department = Department.SALES,
                    level = HierarchyLevel.STAFF_OPERATOR,
                    roleTitle = "Sales Tender & Korporat",
                    reportsToId = budiId,
                    phone = "082199887766"
                ),
                OrgNode(
                    id = OrgNodeId("emp-maya"),
                    name = "Maya Anggraini",
                    email = "maya.sample@wemade.id",
                    department = Department.SALES,
                    level = HierarchyLevel.STAFF_OPERATOR,
                    roleTitle = "Admin Sampling & CS",
                    reportsToId = budiId,
                    phone = "082133445566"
                ),
                // 4. Production Staff (Reporting to Joko)
                OrgNode(
                    id = OrgNodeId("emp-agus"),
                    name = "Agus Setiawan",
                    email = "agus.cutting@wemade.id",
                    department = Department.PRODUCTION_PPIC,
                    level = HierarchyLevel.STAFF_OPERATOR,
                    roleTitle = "Mandor Meja Potong",
                    reportsToId = jokoId,
                    phone = "085211223344"
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
                        it.department != focusNode.department &&
                        it.id != focusNode.id
                    }

                    // Existing head in the same department (if any)
                    val existingHeadInSameDept = nodes.find {
                        it.level == HierarchyLevel.HEAD_OF_DEPARTMENT &&
                        it.department == focusNode.department &&
                        it.id != focusNode.id
                    }

                    val existingStaff = nodes.filter {
                        it.department == focusNode.department &&
                        it.level == HierarchyLevel.STAFF_OPERATOR &&
                        it.id != focusNode.id
                    }

                    val subordinates = if (existingHeadInSameDept != null) {
                        when (successionAction) {
                            HeadSuccessionAction.DEMOTE_TO_STAFF -> {
                                val demotedOldHead = existingHeadInSameDept.copy(
                                    level = HierarchyLevel.STAFF_OPERATOR,
                                    roleTitle = "Staf Senior ${focusNode.department.shortName}",
                                    reportsToId = focusNode.id
                                )
                                listOf(demotedOldHead) + existingStaff
                            }
                            HeadSuccessionAction.DEACTIVATE -> existingStaff
                        }
                    } else {
                        existingStaff
                    }

                    TShapeHierarchyResult(
                        superior = superior,
                        peerHeads = peerHeads,
                        focusNode = focusNode,
                        subordinates = subordinates,
                        peersInDepartment = emptyList(),
                        isDraft = isDraft
                    )
                }

                HierarchyLevel.STAFF_OPERATOR -> {
                    val peersInDept = nodes.filter {
                        it.department == focusNode.department &&
                        it.level == HierarchyLevel.STAFF_OPERATOR &&
                        it.id != focusNode.id
                    }
                    TShapeHierarchyResult(
                        superior = superior,
                        peerHeads = emptyList(),
                        focusNode = focusNode,
                        subordinates = emptyList(),
                        peersInDepartment = peersInDept,
                        isDraft = isDraft
                    )
                }

                HierarchyLevel.EXECUTIVE -> {
                    val allHeads = nodes.filter { it.level == HierarchyLevel.HEAD_OF_DEPARTMENT }
                    TShapeHierarchyResult(
                        superior = null,
                        peerHeads = emptyList(),
                        focusNode = focusNode,
                        subordinates = allHeads,
                        peersInDepartment = emptyList(),
                        isDraft = isDraft
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
    val isDraft: Boolean = false
)

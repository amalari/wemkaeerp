package com.eventverse.app.domain.orgchart

/**
 * 3-Tier organizational hierarchy level for simplified factory operations.
 */
enum class HierarchyLevel(
    val displayName: String,
    val shortLabel: String,
    val rank: Int
) {
    EXECUTIVE(
        displayName = "Pimpinan / Direksi Pabrik",
        shortLabel = "Direksi",
        rank = 1
    ),
    HEAD_OF_DEPARTMENT(
        displayName = "Kepala Divisi / Supervisor",
        shortLabel = "Kepala Divisi",
        rank = 2
    ),
    STAFF_OPERATOR(
        displayName = "Staf Pelaksana / Operator",
        shortLabel = "Staf",
        rank = 3
    );

    val isExecutive: Boolean get() = this == EXECUTIVE
    val isHead: Boolean get() = this == HEAD_OF_DEPARTMENT
    val isStaff: Boolean get() = this == STAFF_OPERATOR
}

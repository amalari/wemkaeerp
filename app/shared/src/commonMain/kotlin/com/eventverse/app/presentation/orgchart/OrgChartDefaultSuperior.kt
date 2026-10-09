package com.eventverse.app.presentation.orgchart

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.OrgNode

/**
 * Logika penentuan atasan default otomatis berdasarkan hirarki wewenang dan divisi:
 * - STAFF_OPERATOR -> Kepala Tim divisi itu, lalu Kepala Divisi, lalu Direksi.
 * - TEAM_LEAD -> Kepala Divisi dari divisi tersebut, lalu Direksi.
 * - HEAD_OF_DEPARTMENT -> Direksi (Executive).
 * - EXECUTIVE -> null (tidak memiliki atasan).
 */
internal fun resolveDefaultSuperior(
    employees: List<OrgNode>,
    dept: Department?,
    level: HierarchyLevel
): String? {
    return when (level) {
        HierarchyLevel.EXECUTIVE -> null
        HierarchyLevel.HEAD_OF_DEPARTMENT -> {
            employees.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value
        }
        HierarchyLevel.TEAM_LEAD -> {
            employees.find {
                it.department?.id == dept?.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
            }?.id?.value ?: employees.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value
        }
        HierarchyLevel.STAFF_OPERATOR -> {
            employees.find {
                it.department?.id == dept?.id && it.level == HierarchyLevel.TEAM_LEAD
            }?.id?.value
                ?: employees.find {
                    it.department?.id == dept?.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
                }?.id?.value
                ?: employees.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value
        }
    }
}

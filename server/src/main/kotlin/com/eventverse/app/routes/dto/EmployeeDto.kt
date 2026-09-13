package com.eventverse.app.routes.dto

import com.eventverse.app.domain.orgchart.HeadSuccessionAction
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.TShapeHierarchyResult

data class EmployeeDto(
    val id: String,
    val name: String,
    val email: String,
    val department: DepartmentDto?,
    val level: String,
    val roleTitle: String,
    val reportsToId: String?,
    val phone: String,
    val tenantId: String?
) {
    companion object {
        fun toJson(emp: OrgNode): String {
            val nameEscaped = escape(emp.name)
            val emailEscaped = escape(emp.email)
            val titleEscaped = escape(emp.roleTitle)
            val phoneEscaped = escape(emp.phone)
            val deptJson = emp.department?.let { DepartmentDto.toJson(it) } ?: "null"
            val reportsToStr = emp.reportsToId?.value?.let { "\"$it\"" } ?: "null"
            val archivedAtStr = emp.archivedAt?.let { "\"$it\"" } ?: "null"

            return "{\"id\":\"${emp.id.value}\",\"name\":\"$nameEscaped\",\"email\":\"$emailEscaped\",\"department\":$deptJson,\"level\":\"${emp.level.name}\",\"roleTitle\":\"$titleEscaped\",\"reportsToId\":$reportsToStr,\"phone\":\"$phoneEscaped\",\"tenantId\":\"${emp.tenantId?.value ?: ""}\",\"archivedAt\":$archivedAtStr}"
        }

        fun toJsonList(employees: List<OrgNode>): String =
            "[${employees.joinToString(",") { toJson(it) }}]"

        fun toTShapeJson(result: TShapeHierarchyResult): String {
            val focusJson = toJson(result.focusNode)
            val superiorJson = result.superior?.let { toJson(it) } ?: "null"
            val peerHeadsJson = toJsonList(result.peerHeads)
            val subordinatesJson = toJsonList(result.subordinates)
            val peersJson = toJsonList(result.peersInDepartment)
            val orderedMembersJson = toJsonList(result.orderedDepartmentMembers)

            return "{\"focusNode\":$focusJson,\"superior\":$superiorJson,\"peerHeads\":$peerHeadsJson,\"subordinates\":$subordinatesJson,\"peersInDepartment\":$peersJson,\"orderedDepartmentMembers\":$orderedMembersJson,\"isDraft\":${result.isDraft}}"
        }

        private fun escape(s: String): String =
            s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
    }
}

data class CreateEmployeeRequestDto(
    val name: String,
    val email: String,
    val departmentId: String? = null,
    val level: HierarchyLevel,
    val roleTitle: String = "",
    val reportsToId: String? = null,
    val phone: String = "",
    val successionAction: HeadSuccessionAction = HeadSuccessionAction.DEMOTE_TO_STAFF
) {
    companion object {
        fun fromJson(rawJson: String): CreateEmployeeRequestDto {
            val name = extractStringField(rawJson, "name") ?: ""
            val email = extractStringField(rawJson, "email") ?: ""
            val departmentId = extractStringField(rawJson, "departmentId") ?: ""
            val levelStr = extractStringField(rawJson, "level") ?: "STAFF_OPERATOR"
            val level = runCatching { HierarchyLevel.valueOf(levelStr) }.getOrDefault(HierarchyLevel.STAFF_OPERATOR)
            val roleTitle = extractStringField(rawJson, "roleTitle") ?: ""
            val reportsToId = extractStringField(rawJson, "reportsToId")
            val phone = extractStringField(rawJson, "phone") ?: ""
            val successionStr = extractStringField(rawJson, "successionAction") ?: "DEMOTE_TO_STAFF"
            val successionAction = runCatching { HeadSuccessionAction.valueOf(successionStr) }
                .getOrDefault(HeadSuccessionAction.DEMOTE_TO_STAFF)

            return CreateEmployeeRequestDto(
                name = name,
                email = email,
                departmentId = departmentId,
                level = level,
                roleTitle = roleTitle,
                reportsToId = reportsToId,
                phone = phone,
                successionAction = successionAction
            )
        }

        private fun extractStringField(json: String, field: String): String? {
            val regex = "\"$field\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return regex.find(json)?.groupValues?.get(1)
        }
    }
}

data class UpdateEmployeeRequestDto(
    val name: String? = null,
    val email: String? = null,
    val departmentId: String? = null,
    val level: HierarchyLevel? = null,
    val roleTitle: String? = null,
    val reportsToId: String? = null,
    val phone: String? = null,
    val successionAction: HeadSuccessionAction = HeadSuccessionAction.DEMOTE_TO_STAFF
) {
    companion object {
        fun fromJson(rawJson: String): UpdateEmployeeRequestDto {
            val name = extractStringField(rawJson, "name")
            val email = extractStringField(rawJson, "email")
            val departmentId = extractStringField(rawJson, "departmentId")
            val levelStr = extractStringField(rawJson, "level")
            val level = levelStr?.let { runCatching { HierarchyLevel.valueOf(it) }.getOrNull() }
            val roleTitle = extractStringField(rawJson, "roleTitle")
            val reportsToId = extractStringField(rawJson, "reportsToId")
            val phone = extractStringField(rawJson, "phone")
            val successionStr = extractStringField(rawJson, "successionAction") ?: "DEMOTE_TO_STAFF"
            val successionAction = runCatching { HeadSuccessionAction.valueOf(successionStr) }
                .getOrDefault(HeadSuccessionAction.DEMOTE_TO_STAFF)

            return UpdateEmployeeRequestDto(
                name = name,
                email = email,
                departmentId = departmentId,
                level = level,
                roleTitle = roleTitle,
                reportsToId = reportsToId,
                phone = phone,
                successionAction = successionAction
            )
        }

        private fun extractStringField(json: String, field: String): String? {
            val regex = "\"$field\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return regex.find(json)?.groupValues?.get(1)
        }
    }
}

data class EmailConflictResponseDto(
    val email: String,
    val existingEmployeeId: String,
    val existingEmployeeName: String,
    val existingDepartmentName: String,
    val existingRoleTitle: String,
    val isArchived: Boolean,
    val message: String
) {
    fun toJson(): String {
        val eEmail = escape(email)
        val eId = escape(existingEmployeeId)
        val eName = escape(existingEmployeeName)
        val eDept = escape(existingDepartmentName)
        val eRole = escape(existingRoleTitle)
        val eMsg = escape(message)
        return "{\"error\":\"EMAIL_CONFLICT\",\"email\":\"$eEmail\",\"existingEmployeeId\":\"$eId\",\"existingEmployeeName\":\"$eName\",\"existingDepartmentName\":\"$eDept\",\"existingRoleTitle\":\"$eRole\",\"isArchived\":$isArchived,\"message\":\"$eMsg\"}"
    }

    companion object {
        private fun escape(s: String): String =
            s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
    }
}


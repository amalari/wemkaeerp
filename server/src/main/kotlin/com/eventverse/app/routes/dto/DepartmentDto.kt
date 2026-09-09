package com.eventverse.app.routes.dto

import com.eventverse.app.domain.orgchart.Department

data class DepartmentDto(
    val id: String,
    val code: String,
    val displayName: String,
    val shortName: String,
    val colorHex: Long,
    val isCustom: Boolean,
    val tenantId: String?
) {
    companion object {
        fun toJson(dept: Department): String {
            val nameEscaped = escape(dept.displayName)
            val shortEscaped = escape(dept.shortName)
            val archivedAtStr = dept.archivedAt?.let { "\"$it\"" } ?: "null"
            return "{\"id\":\"${dept.id.value}\",\"code\":\"${dept.code}\",\"displayName\":\"$nameEscaped\",\"shortName\":\"$shortEscaped\",\"colorHex\":${dept.colorHex},\"isCustom\":${dept.isCustom},\"tenantId\":\"${dept.tenantId?.value ?: ""}\",\"archivedAt\":$archivedAtStr}"
        }

        fun toJsonList(departments: List<Department>): String =
            "[${departments.joinToString(",") { toJson(it) }}]"

        private fun escape(s: String): String =
            s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
    }
}

data class CreateDepartmentRequestDto(
    val displayName: String,
    val shortName: String = "",
    val colorHex: Long = 0xFF2563EB
) {
    companion object {
        fun fromJson(rawJson: String): CreateDepartmentRequestDto {
            val name = extractStringField(rawJson, "displayName") ?: extractStringField(rawJson, "name") ?: ""
            val short = extractStringField(rawJson, "shortName") ?: ""
            val color = extractLongField(rawJson, "colorHex") ?: 0xFF2563EB
            return CreateDepartmentRequestDto(name, short, color)
        }

        private fun extractStringField(json: String, field: String): String? {
            val regex = "\"$field\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return regex.find(json)?.groupValues?.get(1)
        }

        private fun extractLongField(json: String, field: String): Long? {
            val regex = "\"$field\"\\s*:\\s*([0-9]+)".toRegex()
            return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
        }
    }
}

data class UpdateDepartmentRequestDto(
    val displayName: String? = null,
    val shortName: String? = null,
    val colorHex: Long? = null
) {
    companion object {
        fun fromJson(rawJson: String): UpdateDepartmentRequestDto {
            val name = extractStringField(rawJson, "displayName") ?: extractStringField(rawJson, "name")
            val short = extractStringField(rawJson, "shortName")
            val color = extractLongField(rawJson, "colorHex")
            return UpdateDepartmentRequestDto(name, short, color)
        }

        private fun extractStringField(json: String, field: String): String? {
            val regex = "\"$field\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return regex.find(json)?.groupValues?.get(1)
        }

        private fun extractLongField(json: String, field: String): Long? {
            val regex = "\"$field\"\\s*:\\s*([0-9]+)".toRegex()
            return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
        }
    }
}

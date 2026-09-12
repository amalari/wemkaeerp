package com.eventverse.app.routes.dto

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.infrastructure.ModulePermissionsSerializer

data class RoleDto(
    val id: String,
    val tenantId: String?,
    val name: String,
    val description: String,
    val isSystemDefault: Boolean,
    val userCount: Int,
    val modulePermissions: Map<String, ModuleAccessConfigDto>
) {
    companion object {
        fun fromDomain(role: CustomRole): RoleDto = RoleDto(
            id = role.id.value,
            tenantId = role.tenantId?.value,
            name = role.name,
            description = role.description,
            isSystemDefault = role.isSystemDefault,
            userCount = role.userCount,
            modulePermissions = role.modulePermissions.mapKeys { it.key.name }.mapValues {
                ModuleAccessConfigDto(it.value.level.name, it.value.scope.name)
            }
        )

        fun toJson(role: CustomRole): String {
            val permissionsJson = ModulePermissionsSerializer.toJson(role.modulePermissions)
            val descEscaped = escape(role.description)
            val nameEscaped = escape(role.name)
            val departmentJson = role.departmentId?.let { "\"${escape(it)}\"" } ?: "null"
            return "{\"id\":\"${role.id.value}\",\"tenantId\":\"${role.tenantId?.value ?: ""}\",\"name\":\"$nameEscaped\",\"description\":\"$descEscaped\",\"isSystemDefault\":${role.isSystemDefault},\"userCount\":${role.userCount},\"departmentId\":$departmentJson,\"modulePermissions\":$permissionsJson}"
        }

        fun toJsonList(roles: List<CustomRole>): String =
            "[${roles.joinToString(",") { toJson(it) }}]"

        private fun escape(s: String): String =
            s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
    }
}

data class ModuleAccessConfigDto(
    val level: String,
    val scope: String
)

data class CreateRoleRequestDto(
    val name: String,
    val description: String = "",
    val modulePermissions: Map<BusinessModule, ModuleAccessConfig> = emptyMap(),
    val departmentId: String? = null
) {
    companion object {
        fun fromJson(rawJson: String): CreateRoleRequestDto {
            val name = extractStringField(rawJson, "name") ?: ""
            val desc = extractStringField(rawJson, "description") ?: ""
            val permissions = ModulePermissionsSerializer.fromJson(rawJson)
            val departmentId = extractStringField(rawJson, "departmentId")?.takeIf { it.isNotBlank() }
            return CreateRoleRequestDto(name, desc, permissions, departmentId)
        }

        private fun extractStringField(json: String, field: String): String? {
            val regex = "\"$field\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return regex.find(json)?.groupValues?.get(1)
        }
    }
}

data class UpdateRoleRequestDto(
    val name: String? = null,
    val description: String? = null,
    val modulePermissions: Map<BusinessModule, ModuleAccessConfig>? = null,
    val departmentId: String? = null
) {
    companion object {
        fun fromJson(rawJson: String): UpdateRoleRequestDto {
            val name = extractStringField(rawJson, "name")
            val desc = extractStringField(rawJson, "description")
            val permissions = if (rawJson.contains("\"modulePermissions\"") || rawJson.contains("\"CRM_SALES\"") || rawJson.contains("\"level\"")) {
                ModulePermissionsSerializer.fromJson(rawJson)
            } else null
            // Hanya diisi kalau field-nya benar-benar dikirim. Body tanpa `departmentId` berarti
            // "jangan sentuh divisinya", bukan "lepaskan dari divisinya".
            val departmentId = if (rawJson.contains("\"departmentId\"")) {
                extractStringField(rawJson, "departmentId") ?: ""
            } else null
            return UpdateRoleRequestDto(name, desc, permissions, departmentId)
        }

        private fun extractStringField(json: String, field: String): String? {
            val regex = "\"$field\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return regex.find(json)?.groupValues?.get(1)
        }
    }
}

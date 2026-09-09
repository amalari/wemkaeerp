package com.eventverse.app.infrastructure

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig

/**
 * Robust JSON converter for CustomRole module permissions matrix.
 */
object ModulePermissionsSerializer {

    fun toJson(permissions: Map<BusinessModule, ModuleAccessConfig>): String {
        val entries = permissions.map { (module, config) ->
            "\"${module.name}\":{\"level\":\"${config.level.name}\",\"scope\":\"${config.scope.name}\"}"
        }
        return "{${entries.joinToString(",")}}"
    }

    fun fromJson(rawJson: String?): Map<BusinessModule, ModuleAccessConfig> {
        if (rawJson.isNullOrBlank() || rawJson == "{}") return emptyMap()

        val result = mutableMapOf<BusinessModule, ModuleAccessConfig>()
        val regex = "\"([A-Za-z0-9_]+)\"\\s*:\\s*\\{\\s*\"level\"\\s*:\\s*\"([A-Za-z0-9_]+)\"\\s*,\\s*\"scope\"\\s*:\\s*\"([A-Za-z0-9_]+)\"\\s*\\}".toRegex()

        regex.findAll(rawJson).forEach { match ->
            val moduleKey = match.groupValues[1]
            val levelKey = match.groupValues[2]
            val scopeKey = match.groupValues[3]

            val module = runCatching { BusinessModule.valueOf(moduleKey) }.getOrNull()
            val level = runCatching { AccessLevel.valueOf(levelKey) }.getOrDefault(AccessLevel.NONE)
            val scope = runCatching { DataScope.valueOf(scopeKey) }.getOrDefault(DataScope.ALL_TENANT_DATA)

            if (module != null) {
                result[module] = ModuleAccessConfig(level, scope)
            }
        }

        return result
    }
}

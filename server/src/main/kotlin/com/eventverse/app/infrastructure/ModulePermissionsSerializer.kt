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
            val desks = config.allowedDesks?.takeIf { it.isNotEmpty() }
                ?.let { desks -> ",\"desks\":[${desks.sorted().joinToString(",") { "\"$it\"" }}]}" }
                ?: ""
            "\"${module.name}\":{\"level\":\"${config.level.name}\",\"scope\":\"${config.scope.name}\"$desks}"
        }
        return "{${entries.joinToString(",")}}"
    }

    fun fromJson(rawJson: String?): Map<BusinessModule, ModuleAccessConfig> {
        if (rawJson.isNullOrBlank() || rawJson == "{}") return emptyMap()

        val result = mutableMapOf<BusinessModule, ModuleAccessConfig>()
        // Isi objek modul dicocokkan sebagai satu gumpalan tanpa brace bersarang, lalu field
        // di dalamnya dibaca satu per satu — begini entri lama tanpa "desks" dan entri baru
        // dengan "desks" bisa hidup berdampingan.
        val moduleRegex = "\"([A-Za-z0-9_]+)\"\\s*:\\s*\\{([^{}]*)\\}".toRegex()
        val levelRegex = "\"level\"\\s*:\\s*\"([A-Za-z0-9_]+)\"".toRegex()
        val scopeRegex = "\"scope\"\\s*:\\s*\"([A-Za-z0-9_]+)\"".toRegex()
        val desksRegex = "\"desks\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
        val itemRegex = "\"([^\"]+)\"".toRegex()

        moduleRegex.findAll(rawJson).forEach { match ->
            val moduleKey = match.groupValues[1]
            val body = match.groupValues[2]

            val module = runCatching { BusinessModule.valueOf(moduleKey) }.getOrNull() ?: return@forEach
            val level = levelRegex.find(body)?.groupValues?.get(1)
                ?.let { runCatching { AccessLevel.valueOf(it) }.getOrNull() }
                ?: AccessLevel.NONE
            val scope = scopeRegex.find(body)?.groupValues?.get(1)
                ?.let { runCatching { DataScope.valueOf(it) }.getOrNull() }
                ?: DataScope.ALL_TENANT_DATA
            val desks = desksRegex.find(body)?.groupValues?.get(1)
                ?.let { raw -> itemRegex.findAll(raw).map { item -> item.groupValues[1] }.toSet() }

            result[module] = ModuleAccessConfig(level, scope, allowedDesks = desks)
        }

        return result
    }
}

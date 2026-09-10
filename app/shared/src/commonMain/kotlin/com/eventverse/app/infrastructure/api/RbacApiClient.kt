package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.rbac.*
import com.eventverse.app.domain.tenant.TenantId
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class RbacApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    /**
     * GET /api/tenant/departments
     */
    suspend fun getDepartments(tenantSlug: String): Result<List<Department>> = runCatching {
        val response = httpClient.get(resolveUrl("/api/tenant/departments")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memuat divisi (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        OrgChartApiClient.parseJsonArray(response.bodyAsText()).map { OrgChartApiClient.parseDepartment(it) }
    }

    /**
     * GET /api/tenant/roles
     */
    suspend fun getRoles(tenantSlug: String): Result<List<CustomRole>> = runCatching {
        val response = httpClient.get(resolveUrl("/api/tenant/roles")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memuat jabatan (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        OrgChartApiClient.parseJsonArray(response.bodyAsText()).map { parseCustomRole(it) }
    }

    /**
     * GET /api/tenant/employees?departmentId={id}
     */
    suspend fun getEmployees(tenantSlug: String, departmentId: String? = null): Result<List<OrgNode>> = runCatching {
        val url = if (departmentId != null) {
            resolveUrl("/api/tenant/employees?departmentId=$departmentId")
        } else {
            resolveUrl("/api/tenant/employees")
        }
        val response = httpClient.get(url) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memuat karyawan (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        OrgChartApiClient.parseJsonArray(response.bodyAsText()).map { OrgChartApiClient.parseOrgNode(it) }
    }

    /**
     * POST /api/tenant/roles
     */
    suspend fun createRole(
        tenantSlug: String,
        name: String,
        description: String,
        modulePermissions: Map<BusinessModule, ModuleAccessConfig>
    ): Result<CustomRole> = runCatching {
        val escapedName = OrgChartApiClient.escapeJson(name)
        val escapedDesc = OrgChartApiClient.escapeJson(description)
        val permJson = serializePermissions(modulePermissions)
        val jsonBody = "{\"name\":\"$escapedName\",\"description\":\"$escapedDesc\",\"modulePermissions\":$permJson}"

        val response = httpClient.post(resolveUrl("/api/tenant/roles")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
        }
        if (!response.status.isSuccess()) {
            error("Gagal menambah jabatan (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        parseCustomRole(response.bodyAsText())
    }

    /**
     * PUT /api/tenant/roles/{id}
     */
    suspend fun updateRole(
        tenantSlug: String,
        role: CustomRole
    ): Result<CustomRole> = runCatching {
        val escapedName = OrgChartApiClient.escapeJson(role.name)
        val escapedDesc = OrgChartApiClient.escapeJson(role.description)
        val permJson = serializePermissions(role.modulePermissions)
        val jsonBody = "{\"name\":\"$escapedName\",\"description\":\"$escapedDesc\",\"modulePermissions\":$permJson}"

        val response = httpClient.put(resolveUrl("/api/tenant/roles/${role.id.value}")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memperbarui jabatan (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        parseCustomRole(response.bodyAsText())
    }

    /**
     * DELETE /api/tenant/roles/{id}
     */
    suspend fun deleteRole(tenantSlug: String, roleId: String): Result<Unit> = runCatching {
        val response = httpClient.delete(resolveUrl("/api/tenant/roles/$roleId")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        if (!response.status.isSuccess()) {
            error("Gagal menghapus jabatan (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
    }

    companion object {
        fun parseCustomRole(json: String): CustomRole {
            val id = OrgChartApiClient.extractString(json, "id") ?: "role-${(100..999).random()}"
            val tenantIdStr = OrgChartApiClient.extractString(json, "tenantId")?.takeIf { it.isNotBlank() }
            val name = OrgChartApiClient.extractString(json, "name") ?: ""
            val description = OrgChartApiClient.extractString(json, "description") ?: ""
            val isSystemDefault = OrgChartApiClient.extractBoolean(json, "isSystemDefault") ?: false
            val userCount = OrgChartApiClient.extractLong(json, "userCount")?.toInt() ?: 0

            val permissionsJson = OrgChartApiClient.extractJsonObject(json, "modulePermissions")
            val permissions = parsePermissions(permissionsJson)

            return CustomRole(
                id = RoleId(id),
                tenantId = tenantIdStr?.let { TenantId(it) },
                name = name,
                description = description,
                isSystemDefault = isSystemDefault,
                modulePermissions = permissions,
                userCount = userCount
            )
        }

        fun parsePermissions(rawJson: String?): Map<BusinessModule, ModuleAccessConfig> {
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

        fun serializePermissions(permissions: Map<BusinessModule, ModuleAccessConfig>): String {
            val entries = permissions.map { (module, config) ->
                "\"${module.name}\":{\"level\":\"${config.level.name}\",\"scope\":\"${config.scope.name}\"}"
            }
            return "{${entries.joinToString(",")}}"
        }
    }
}

package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.rbac.BusinessModules

import com.eventverse.app.domain.pack.ModuleIdCodec

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.rbac.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.pipeline.TenantEntitlementGrantsCodec
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
     * GET /api/tenant/entitlement — modul yang disambungkan ke tenant ini.
     *
     * Terpisah dari `GET /api/tenant/pipeline/modules`, yang sengaja hanya membicarakan katalog
     * **operasional** (arketipe, rekomendasi preset, status terpasang di kanvas). Modul tata kelola
     * tidak punya satu pun properti itu, jadi menumpangkannya ke sana berarti melebarkan kontrak
     * katalog demi tiga baris yang tidak pernah memakai isinya.
     *
     * Server selalu mengirim daftar yang sudah dipadatkan; `null` di sini hanya muncul bila server
     * lama yang menjawab, dan diartikan "semua modul" sesuai konvensi codec.
     */
    suspend fun getEntitlement(tenantSlug: String): Result<Set<BusinessModule>> = runCatching {
        val response = httpClient.get(resolveUrl("/api/tenant/entitlement")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memuat entitlement modul (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        TenantEntitlementGrantsCodec.decode(response.bodyAsText()).grantedModules
            ?: BusinessModules.entries.toSet()
    }

    /**
     * GET /api/tenant/me/access — wewenang pengguna yang sedang login, dihitung server (B5). Pengganti
     * menghitung menu dari daftar jabatan & penugasan semua orang, yang kini hanya terbuka untuk admin.
     */
    suspend fun getMyAccess(tenantSlug: String): Result<Map<BusinessModule, AccessDecision>> = runCatching {
        val response = httpClient.get(resolveUrl("/api/tenant/me/access")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memuat wewenang (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        com.eventverse.app.shared.rbac.AccessDecisionCodec.decode(
            com.eventverse.app.shared.json.JsonParser.parseObject(response.bodyAsText())
        )
    }

    /**
     * POST /api/tenant/roles
     */
    suspend fun createRole(
        tenantSlug: String,
        name: String,
        description: String,
        modulePermissions: Map<BusinessModule, ModuleAccessConfig>,
        departmentId: String? = null
    ): Result<CustomRole> = runCatching {
        val escapedName = OrgChartApiClient.escapeJson(name)
        val escapedDesc = OrgChartApiClient.escapeJson(description)
        val permJson = serializePermissions(modulePermissions)
        val deptJson = departmentId?.let { "\"${OrgChartApiClient.escapeJson(it)}\"" } ?: "null"
        val jsonBody = "{\"name\":\"$escapedName\",\"description\":\"$escapedDesc\"," +
            "\"departmentId\":$deptJson,\"modulePermissions\":$permJson}"

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
        val deptJson = role.departmentId?.let { "\"${OrgChartApiClient.escapeJson(it)}\"" } ?: "\"\""
        val jsonBody = "{\"name\":\"$escapedName\",\"description\":\"$escapedDesc\"," +
            "\"departmentId\":$deptJson,\"modulePermissions\":$permJson}"

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

    /**
     * GET /api/tenant/module-assignments
     *
     * Penugasan modul ke divisi — sumbu wewenang kedua di samping jabatan.
     */
    suspend fun getModuleAssignments(
        tenantSlug: String
    ): Result<Map<BusinessModule, List<DepartmentModuleAssignment>>> = runCatching {
        val response = httpClient.get(resolveUrl("/api/tenant/module-assignments")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memuat penugasan modul (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        parseAssignments(response.bodyAsText())
    }

    /**
     * PUT /api/tenant/module-assignments
     */
    suspend fun upsertModuleAssignment(
        tenantSlug: String,
        module: BusinessModule,
        assignment: DepartmentModuleAssignment
    ): Result<Unit> = runCatching {
        val response = httpClient.put(resolveUrl("/api/tenant/module-assignments")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(serializeAssignment(module, assignment))
        }
        if (!response.status.isSuccess()) {
            error("Gagal menyimpan penugasan modul (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
    }

    /**
     * DELETE /api/tenant/module-assignments/{module}/{assignmentKey}
     */
    suspend fun deleteModuleAssignment(
        tenantSlug: String,
        module: BusinessModule,
        assignmentKey: String
    ): Result<Unit> = runCatching {
        val response = httpClient.delete(
            resolveUrl("/api/tenant/module-assignments/${module.name}/$assignmentKey")
        ) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        if (!response.status.isSuccess()) {
            error("Gagal menghapus penugasan modul (HTTP ${response.status.value}): ${response.bodyAsText()}")
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
                userCount = userCount,
                departmentId = OrgChartApiClient.extractString(json, "departmentId")?.takeIf { it.isNotBlank() }
            )
        }

        /**
         * Membaca peta `{"MODUL": [ {...}, ... ]}` dari endpoint penugasan.
         */
        fun parseAssignments(json: String): Map<BusinessModule, List<DepartmentModuleAssignment>> {
            if (json.isBlank() || json == "{}") return emptyMap()

            return BusinessModules.entries.mapNotNull { module ->
                val array = extractJsonArray(json, ModuleIdCodec.storedName(module)) ?: return@mapNotNull null
                val items = OrgChartApiClient.parseJsonArray(array).map { parseAssignment(it) }
                if (items.isEmpty()) null else module to items
            }.toMap()
        }

        /**
         * Mengambil nilai array milik [key], lengkap dengan kurung sikunya.
         *
         * Dipindai per karakter dengan penghitung kedalaman, bukan regex: tiap penugasan memuat
         * array `specificRoleIds` di dalamnya, dan pola non-greedy `\[(.*?)\]` akan berhenti di
         * kurung tutup milik array bersarang itu — memotong daftar di tengah tanpa error.
         */
        private fun extractJsonArray(json: String, key: String): String? {
            val keyIndex = json.indexOf("\"$key\"")
            if (keyIndex < 0) return null

            val start = json.indexOf('[', keyIndex)
            if (start < 0) return null

            var depth = 0
            var inQuotes = false
            var escaped = false

            for (i in start until json.length) {
                val c = json[i]
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inQuotes = !inQuotes
                    inQuotes -> Unit
                    c == '[' -> depth++
                    c == ']' -> {
                        depth--
                        if (depth == 0) return json.substring(start, i + 1)
                    }
                }
            }
            return null
        }

        fun parseAssignment(json: String): DepartmentModuleAssignment = DepartmentModuleAssignment(
            departmentId = OrgChartApiClient.extractString(json, "departmentId") ?: "",
            departmentName = OrgChartApiClient.extractString(json, "departmentName") ?: "",
            accessLevel = OrgChartApiClient.extractString(json, "accessLevel")
                ?.let { runCatching { AccessLevel.valueOf(it) }.getOrNull() }
                ?: AccessLevel.NONE,
            scope = OrgChartApiClient.extractString(json, "scope")
                ?.let { runCatching { DataScope.valueOf(it) }.getOrNull() }
                ?: DataScope.ALL_TENANT_DATA,
            specificRoleIds = "\"specificRoleIds\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
                .find(json)?.groupValues?.get(1)
                ?.let { raw -> "\"([^\"]+)\"".toRegex().findAll(raw).map { it.groupValues[1] }.toSet() }
                ?: emptySet(),
            allowedDesks = parseDeskCodes(json),
            id = OrgChartApiClient.extractString(json, "id") ?: ""
        )

        fun serializeAssignment(module: BusinessModule, assignment: DepartmentModuleAssignment): String {
            val roleIds = assignment.specificRoleIds.sorted()
                .joinToString(",") { "\"${OrgChartApiClient.escapeJson(it)}\"" }
            val desks = assignment.allowedDesks.orEmpty().sorted()
                .joinToString(",") { "\"${OrgChartApiClient.escapeJson(it)}\"" }
            return "{\"module\":\"${module.name}\"," +
                "\"id\":\"${OrgChartApiClient.escapeJson(assignment.id)}\"," +
                "\"departmentId\":\"${OrgChartApiClient.escapeJson(assignment.departmentId)}\"," +
                "\"departmentName\":\"${OrgChartApiClient.escapeJson(assignment.departmentName)}\"," +
                "\"accessLevel\":\"${assignment.accessLevel.name}\"," +
                "\"scope\":\"${assignment.scope.name}\"," +
                "\"specificRoleIds\":[$roleIds]," +
                "\"allowedDesks\":[$desks]}"
        }

        /** Meja dari penugasan; `null` berarti seluruh meja (field kosong/absen). */
        private fun parseDeskCodes(json: String): Set<String>? =
            "\"allowedDesks\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
                .find(json)?.groupValues?.get(1)
                ?.let { raw -> "\"([^\"]+)\"".toRegex().findAll(raw).map { it.groupValues[1] }.toSet() }
                ?.takeIf { it.isNotEmpty() }

        fun parsePermissions(rawJson: String?): Map<BusinessModule, ModuleAccessConfig> {
            if (rawJson.isNullOrBlank() || rawJson == "{}") return emptyMap()
            val result = mutableMapOf<BusinessModule, ModuleAccessConfig>()
            // Gumpalan isi tiap modul dibaca utuh lalu field-nya diurai satu per satu, supaya
            // entri dengan "desks" dan tanpa "desks" bisa diparse dengan aturan yang sama.
            val moduleRegex = "\"([A-Za-z0-9_]+)\"\\s*:\\s*\\{([^{}]*)\\}".toRegex()
            val levelRegex = "\"level\"\\s*:\\s*\"([A-Za-z0-9_]+)\"".toRegex()
            val scopeRegex = "\"scope\"\\s*:\\s*\"([A-Za-z0-9_]+)\"".toRegex()
            val desksRegex = "\"desks\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
            val itemRegex = "\"([^\"]+)\"".toRegex()

            moduleRegex.findAll(rawJson).forEach { match ->
                val moduleKey = match.groupValues[1]
                val body = match.groupValues[2]

                val module = ModuleIdCodec.fromStoredName(moduleKey, "role.modulePermissions") ?: return@forEach
                val level = levelRegex.find(body)?.groupValues?.get(1)
                    ?.let { runCatching { AccessLevel.valueOf(it) }.getOrNull() }
                    ?: AccessLevel.NONE
                val scope = scopeRegex.find(body)?.groupValues?.get(1)
                    ?.let { runCatching { DataScope.valueOf(it) }.getOrNull() }
                    ?: DataScope.ALL_TENANT_DATA
                val desks = desksRegex.find(body)?.groupValues?.get(1)
                    ?.let { raw -> itemRegex.findAll(raw).map { item -> item.groupValues[1] }.toSet() }
                    ?.takeIf { it.isNotEmpty() }

                result[module] = ModuleAccessConfig(level, scope, allowedDesks = desks)
            }
            return result
        }

        fun serializePermissions(permissions: Map<BusinessModule, ModuleAccessConfig>): String {
            val entries = permissions.map { (module, config) ->
                val desks = config.allowedDesks?.takeIf { it.isNotEmpty() }
                    ?.let { desks -> ",\"desks\":[${desks.sorted().joinToString(",") { "\"$it\"" }}]}" }
                    ?: ""
                "\"${module.name}\":{\"level\":\"${config.level.name}\",\"scope\":\"${config.scope.name}\"$desks}"
            }
            return "{${entries.joinToString(",")}}"
        }
    }
}

package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.orgchart.*
import com.eventverse.app.domain.tenant.TenantId
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class OrgChartApiClient(
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
        val text = response.bodyAsText()
        parseJsonArray(text).map { parseDepartment(it) }
    }

    /**
     * POST /api/tenant/departments
     */
    suspend fun createDepartment(
        tenantSlug: String,
        name: String,
        shortName: String,
        colorHex: Long
    ): Result<Department> = runCatching {
        val escapedName = escapeJson(name)
        val escapedShort = escapeJson(shortName)
        val jsonBody = "{\"displayName\":\"$escapedName\",\"shortName\":\"$escapedShort\",\"colorHex\":$colorHex}"

        val response = httpClient.post(resolveUrl("/api/tenant/departments")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
        }
        if (!response.status.isSuccess()) {
            error("Gagal menambah divisi (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        parseDepartment(response.bodyAsText())
    }

    /**
     * GET /api/tenant/employees
     */
    suspend fun getEmployees(
        tenantSlug: String,
        departmentId: String? = null
    ): Result<List<OrgNode>> = runCatching {
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
        val text = response.bodyAsText()
        parseJsonArray(text).map { parseOrgNode(it) }
    }

    /**
     * POST /api/tenant/employees
     */
    suspend fun createEmployee(
        tenantSlug: String,
        employee: OrgNode,
        successionAction: HeadSuccessionAction = HeadSuccessionAction.DEMOTE_TO_STAFF
    ): Result<OrgNode> = runCatching {
        val escapedName = escapeJson(employee.name)
        val escapedEmail = escapeJson(employee.email)
        val escapedTitle = escapeJson(employee.roleTitle)
        val escapedPhone = escapeJson(employee.phone)
        val reportsToStr = employee.reportsToId?.value?.let { "\"${escapeJson(it)}\"" } ?: "null"

        val deptIdStr = employee.department?.id?.value?.let { "\"${escapeJson(it)}\"" } ?: "null"
        val jsonBody = "{\"name\":\"$escapedName\",\"email\":\"$escapedEmail\",\"departmentId\":$deptIdStr,\"level\":\"${employee.level.name}\",\"roleTitle\":\"$escapedTitle\",\"reportsToId\":$reportsToStr,\"phone\":\"$escapedPhone\",\"successionAction\":\"${successionAction.name}\"}"

        val response = httpClient.post(resolveUrl("/api/tenant/employees")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
        }
        if (!response.status.isSuccess()) {
            val responseBody = response.bodyAsText()
            if (response.status == HttpStatusCode.Conflict) {
                val conflictEx = parseEmailConflict(responseBody)
                if (conflictEx != null) throw conflictEx
            }
            error("Gagal menyimpan karyawan (HTTP ${response.status.value}): $responseBody")
        }
        parseOrgNode(response.bodyAsText())
    }

    /**
     * PUT /api/tenant/employees/{id}
     */
    suspend fun updateEmployee(
        tenantSlug: String,
        employee: OrgNode,
        successionAction: HeadSuccessionAction = HeadSuccessionAction.DEMOTE_TO_STAFF
    ): Result<OrgNode> = runCatching {
        val escapedName = escapeJson(employee.name)
        val escapedEmail = escapeJson(employee.email)
        val escapedTitle = escapeJson(employee.roleTitle)
        val escapedPhone = escapeJson(employee.phone)
        val reportsToStr = employee.reportsToId?.value?.let { "\"${escapeJson(it)}\"" } ?: "null"

        val deptIdStr = employee.department?.id?.value?.let { "\"${escapeJson(it)}\"" } ?: "null"
        val jsonBody = "{\"name\":\"$escapedName\",\"email\":\"$escapedEmail\",\"departmentId\":$deptIdStr,\"level\":\"${employee.level.name}\",\"roleTitle\":\"$escapedTitle\",\"reportsToId\":$reportsToStr,\"phone\":\"$escapedPhone\",\"successionAction\":\"${successionAction.name}\"}"

        val response = httpClient.put(resolveUrl("/api/tenant/employees/${employee.id.value}")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
        }
        if (!response.status.isSuccess()) {
            val responseBody = response.bodyAsText()
            if (response.status == HttpStatusCode.Conflict) {
                val conflictEx = parseEmailConflict(responseBody)
                if (conflictEx != null) throw conflictEx
            }
            error("Gagal memperbarui karyawan (HTTP ${response.status.value}): $responseBody")
        }
        parseOrgNode(response.bodyAsText())
    }

    /**
     * DELETE /api/tenant/employees/{id}  — server melakukan soft-archive, bukan hard delete
     */
    suspend fun deleteEmployee(tenantSlug: String, employeeId: String): Result<Unit> = runCatching {
        val response = httpClient.delete(resolveUrl("/api/tenant/employees/$employeeId")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        if (!response.status.isSuccess()) {
            error("Gagal mengarsipkan karyawan (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
    }

    /**
     * GET /api/tenant/employees/archived
     */
    suspend fun getArchivedEmployees(tenantSlug: String): Result<List<OrgNode>> = runCatching {
        val response = httpClient.get(resolveUrl("/api/tenant/employees/archived")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memuat arsip karyawan (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        parseJsonArray(response.bodyAsText()).map { parseOrgNode(it) }
    }

    /**
     * POST /api/tenant/employees/{id}/restore
     */
    suspend fun restoreEmployee(tenantSlug: String, employeeId: String): Result<Unit> = runCatching {
        val response = httpClient.post(resolveUrl("/api/tenant/employees/$employeeId/restore")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memulihkan karyawan (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
    }

    /**
     * PUT /api/tenant/departments/{id} — perbarui nama, nama singkat, dan warna divisi
     */
    suspend fun updateDepartment(
        tenantSlug: String,
        department: Department
    ): Result<Department> = runCatching {
        val escapedName = escapeJson(department.displayName)
        val escapedShort = escapeJson(department.shortName)
        val jsonBody = "{\"displayName\":\"$escapedName\",\"shortName\":\"$escapedShort\",\"colorHex\":${department.colorHex}}"

        val response = httpClient.put(resolveUrl("/api/tenant/departments/${department.id.value}")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memperbarui divisi (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        parseDepartment(response.bodyAsText())
    }

    /**
     * DELETE /api/tenant/departments/{id}  — server melakukan soft-archive, bukan hard delete
     */
    suspend fun deleteDepartment(tenantSlug: String, departmentId: String): Result<Unit> = runCatching {
        val response = httpClient.delete(resolveUrl("/api/tenant/departments/$departmentId")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        if (!response.status.isSuccess()) {
            error("Gagal mengarsipkan divisi (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
    }

    /**
     * GET /api/tenant/departments/archived
     */
    suspend fun getArchivedDepartments(tenantSlug: String): Result<List<Department>> = runCatching {
        val response = httpClient.get(resolveUrl("/api/tenant/departments/archived")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memuat arsip divisi (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        parseJsonArray(response.bodyAsText()).map { parseDepartment(it) }
    }

    /**
     * POST /api/tenant/departments/{id}/restore
     */
    suspend fun restoreDepartment(tenantSlug: String, departmentId: String): Result<Unit> = runCatching {
        val response = httpClient.post(resolveUrl("/api/tenant/departments/$departmentId/restore")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memulihkan divisi (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
    }

    /**
     * POST /api/tenant/employees/restore-presets
     */
    suspend fun restoreEmployeePresets(tenantSlug: String): Result<Unit> = runCatching {
        val response = httpClient.post(resolveUrl("/api/tenant/employees/restore-presets")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memulihkan preset karyawan (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
    }

    /**
     * POST /api/tenant/departments/restore-presets
     */
    suspend fun restoreDepartmentPresets(tenantSlug: String): Result<Unit> = runCatching {
        val response = httpClient.post(resolveUrl("/api/tenant/departments/restore-presets")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        if (!response.status.isSuccess()) {
            error("Gagal memulihkan preset divisi (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
    }

    companion object {
        fun escapeJson(s: String): String =
            s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

        fun parseDepartment(json: String): Department {
            val id = extractString(json, "id") ?: "dept-sales"
            val code = extractString(json, "code") ?: "SALES"
            val displayName = extractString(json, "displayName") ?: extractString(json, "name") ?: "Sales"
            val shortName = extractString(json, "shortName") ?: displayName.take(8)
            val colorHex = extractLong(json, "colorHex") ?: 0xFF2563EB
            val isCustom = extractBoolean(json, "isCustom") ?: false
            val tenantId = extractString(json, "tenantId")?.takeIf { it.isNotBlank() }?.let { TenantId(it) }
            val archivedAt = extractString(json, "archivedAt")?.takeIf { it != "null" && it.isNotBlank() }

            return Department(
                id = DepartmentId(id),
                code = code,
                displayName = displayName,
                shortName = shortName,
                colorHex = colorHex,
                isCustom = isCustom,
                tenantId = tenantId,
                archivedAt = archivedAt
            )
        }

        fun parseOrgNode(json: String): OrgNode {
            val id = extractString(json, "id") ?: "emp-${(100..999).random()}"
            val name = extractString(json, "name") ?: ""
            val email = extractString(json, "email") ?: ""
            val levelStr = extractString(json, "level") ?: "STAFF_OPERATOR"
            val level = runCatching { HierarchyLevel.valueOf(levelStr) }.getOrDefault(HierarchyLevel.STAFF_OPERATOR)
            val roleTitle = extractString(json, "roleTitle") ?: level.displayName
            val reportsToId = extractString(json, "reportsToId")?.takeIf { it.isNotBlank() && it != "null" }?.let { OrgNodeId(it) }
            val phone = extractString(json, "phone") ?: ""
            val tenantId = extractString(json, "tenantId")?.takeIf { it.isNotBlank() }?.let { TenantId(it) }
            val archivedAt = extractString(json, "archivedAt")?.takeIf { it != "null" && it.isNotBlank() }

            val deptJson = extractJsonObject(json, "department")
            val dept = if (deptJson != null && deptJson != "null") parseDepartment(deptJson) else null

            return OrgNode(
                id = OrgNodeId(id),
                name = name,
                email = email,
                department = dept,
                level = level,
                roleTitle = roleTitle,
                reportsToId = reportsToId,
                phone = phone,
                tenantId = tenantId,
                archivedAt = archivedAt
            )
        }

        fun parseJsonArray(json: String): List<String> {
            val trimmed = json.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            if (inner.isBlank()) return emptyList()

            val result = mutableListOf<String>()
            var depth = 0
            var inQuotes = false
            var escape = false
            var start = 0

            for (i in inner.indices) {
                val c = inner[i]
                if (escape) {
                    escape = false
                    continue
                }
                if (c == '\\') {
                    escape = true
                    continue
                }
                if (c == '"') {
                    inQuotes = !inQuotes
                    continue
                }
                if (!inQuotes) {
                    if (c == '{' || c == '[') depth++
                    else if (c == '}' || c == ']') depth--
                    else if (c == ',' && depth == 0) {
                        result.add(inner.substring(start, i).trim())
                        start = i + 1
                    }
                }
            }
            if (start < inner.length) {
                val last = inner.substring(start).trim()
                if (last.isNotBlank()) result.add(last)
            }
            return result
        }

        fun extractJsonObject(json: String, key: String): String? {
            val pattern = "\"$key\"\\s*:\\s*\\{".toRegex()
            val match = pattern.find(json) ?: return null
            val startIndex = match.range.last
            var depth = 0
            var inQuotes = false
            var escape = false
            for (i in startIndex until json.length) {
                val c = json[i]
                if (escape) { escape = false; continue }
                if (c == '\\') { escape = true; continue }
                if (c == '"') { inQuotes = !inQuotes; continue }
                if (!inQuotes) {
                    if (c == '{') depth++
                    else if (c == '}') {
                        depth--
                        if (depth == 0) return json.substring(startIndex, i + 1)
                    }
                }
            }
            return null
        }

        fun extractString(json: String, key: String): String? {
            val topLevelOnly = stripNestedObjects(json)
            val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return regex.find(topLevelOnly)?.groupValues?.get(1)
        }

        fun extractLong(json: String, key: String): Long? {
            val topLevelOnly = stripNestedObjects(json)
            val regex = "\"$key\"\\s*:\\s*([0-9]+)".toRegex()
            return regex.find(topLevelOnly)?.groupValues?.get(1)?.toLongOrNull()
        }

        fun extractBoolean(json: String, key: String): Boolean? {
            val topLevelOnly = stripNestedObjects(json)
            val regex = "\"$key\"\\s*:\\s*(true|false)".toRegex()
            return regex.find(topLevelOnly)?.groupValues?.get(1)?.toBooleanStrictOrNull()
        }

        fun stripNestedObjects(json: String): String {
            val sb = StringBuilder()
            var depth = 0
            var inQuotes = false
            var escape = false
            for (i in json.indices) {
                val c = json[i]
                if (escape) {
                    escape = false
                    if (depth <= 1) sb.append(c)
                    continue
                }
                if (c == '\\') {
                    escape = true
                    if (depth <= 1) sb.append(c)
                    continue
                }
                if (c == '"') {
                    inQuotes = !inQuotes
                    if (depth <= 1) sb.append(c)
                    continue
                }
                if (!inQuotes) {
                    if (c == '{' || c == '[') {
                        depth++
                        if (depth <= 1) sb.append(c)
                    } else if (c == '}' || c == ']') {
                        if (depth <= 1) sb.append(c)
                        depth--
                    } else if (depth <= 1) {
                        sb.append(c)
                    }
                } else if (depth <= 1) {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        fun parseEmailConflict(json: String): EmailConflictException? {
            val error = extractString(json, "error")
            if (error != "EMAIL_CONFLICT") return null
            val email = extractString(json, "email") ?: ""
            val existingId = extractString(json, "existingEmployeeId") ?: ""
            val existingName = extractString(json, "existingEmployeeName") ?: ""
            val existingDept = extractString(json, "existingDepartmentName") ?: ""
            val existingRole = extractString(json, "existingRoleTitle") ?: ""
            val isArchived = extractBoolean(json, "isArchived") ?: false
            val message = extractString(json, "message") ?: "Email sudah digunakan"

            return EmailConflictException(
                email = email,
                existingEmployeeId = existingId,
                existingEmployeeName = existingName,
                existingDepartmentName = existingDept,
                existingRoleTitle = existingRole,
                isArchived = isArchived,
                message = message
            )
        }
    }
}

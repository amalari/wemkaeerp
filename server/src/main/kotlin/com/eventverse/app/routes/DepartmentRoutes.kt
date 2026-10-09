package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.orgchart.usecases.*
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.routes.dto.CreateDepartmentRequestDto
import com.eventverse.app.routes.dto.DepartmentDto
import com.eventverse.app.routes.dto.UpdateDepartmentRequestDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.departmentRoutes(
    departmentRepository: DepartmentRepository,
    employeeRepository: EmployeeRepository? = null,
    roleRepository: RoleRepository? = null,
    moduleAssignmentRepository: ModuleAssignmentRepository? = null
) {
    val getDepartmentsUseCase = GetDepartmentsUseCase(departmentRepository)
    val createDepartmentUseCase = CreateDepartmentUseCase(departmentRepository)
    val updateDepartmentUseCase = UpdateDepartmentUseCase(departmentRepository)
    val archiveDepartmentUseCase = ArchiveDepartmentUseCase(departmentRepository, employeeRepository)
    val restoreDepartmentUseCase = RestoreDepartmentUseCase(departmentRepository)
    val restoreDefaultDepartmentsUseCase = RestoreDefaultDepartmentsUseCase(departmentRepository)

    route("/api/tenant/departments") {
        // B5: baca = VIEW, tulis = MANAGE (fail-closed). TRD-PLAT-011: GET daftar divisi ikut digerbang
        // (sebelumnya dikecualikan dan bergantung pada orgChartDecision yang permisif untuk token tanpa identitas).
        if (roleRepository != null && moduleAssignmentRepository != null) {
            moduleGate(GarmentModules.ORG_CHART, roleRepository, moduleAssignmentRepository)
        }
        get {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@get
            }

            val decision = call.orgChartDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireOrgChartAccess(decision, AccessLevel.VIEW)) return@get

            val result = getDepartmentsUseCase.getAll(tenant.tenantId)
            if (result.isSuccess) {
                val allDepts = result.getOrThrow()
                val scope = decision?.config?.scope ?: DataScope.ALL_TENANT_DATA
                val userDeptId = call.callerPrincipalOrNull?.departmentId
                val filtered = if (scope != DataScope.ALL_TENANT_DATA && !userDeptId.isNullOrBlank()) {
                    allDepts.filter {
                        it.id.value.equals(userDeptId, ignoreCase = true) ||
                        it.code.equals(userDeptId, ignoreCase = true) ||
                        userDeptId.contains(it.code, ignoreCase = true)
                    }.ifEmpty { allDepts }
                } else {
                    allDepts
                }
                call.respondText(DepartmentDto.toJsonList(filtered), contentType = ContentType.Application.Json)
            } else {
                call.respond(HttpStatusCode.InternalServerError, result.exceptionOrNull()?.message ?: "Failed to load departments")
            }
        }

        get("/{id}") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@get
            }
            val deptId = call.parameters["id"]?.let { DepartmentId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing department id")
                return@get
            }

            val result = getDepartmentsUseCase.getById(tenant.tenantId, deptId)
            if (result.isSuccess) {
                val dept = result.getOrThrow()
                if (dept != null) {
                    call.respondText(DepartmentDto.toJson(dept), contentType = ContentType.Application.Json)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Department not found")
                }
            } else {
                call.respond(HttpStatusCode.InternalServerError, result.exceptionOrNull()?.message ?: "Failed to find department")
            }
        }

        post {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@post
            }

            val rawBody = call.receiveText()
            val req = if (rawBody.isNotBlank()) {
                CreateDepartmentRequestDto.fromJson(rawBody)
            } else {
                val params = call.receiveParameters()
                CreateDepartmentRequestDto(
                    displayName = params["displayName"] ?: params["name"] ?: "",
                    shortName = params["shortName"] ?: "",
                    colorHex = params["colorHex"]?.toLongOrNull() ?: 0xFF2563EB
                )
            }

            val result = createDepartmentUseCase(
                CreateDepartmentCommand(
                    tenantId = tenant.tenantId,
                    displayName = req.displayName,
                    shortName = req.shortName,
                    colorHex = req.colorHex
                )
            )

            if (result.isSuccess) {
                val created = result.getOrThrow()
                call.respondText(
                    text = DepartmentDto.toJson(created),
                    status = HttpStatusCode.Created,
                    contentType = ContentType.Application.Json
                )
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    result.exceptionOrNull()?.message ?: "Failed to create department"
                )
            }
        }

        put("/{id}") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@put
            }
            val deptId = call.parameters["id"]?.let { DepartmentId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing department id")
                return@put
            }

            val rawBody = call.receiveText()
            val req = UpdateDepartmentRequestDto.fromJson(rawBody)

            val result = updateDepartmentUseCase(
                UpdateDepartmentCommand(
                    tenantId = tenant.tenantId,
                    id = deptId,
                    displayName = req.displayName,
                    shortName = req.shortName,
                    colorHex = req.colorHex
                )
            )

            if (result.isSuccess) {
                call.respondText(DepartmentDto.toJson(result.getOrThrow()), contentType = ContentType.Application.Json)
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    result.exceptionOrNull()?.message ?: "Failed to update department"
                )
            }
        }

        delete("/{id}") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@delete
            }
            val deptId = call.parameters["id"]?.let { DepartmentId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing department id")
                return@delete
            }

            // Soft archive — data tidak dihapus dari DB (pola Odoo)
            val result = archiveDepartmentUseCase(tenant.tenantId, deptId)
            if (result.isSuccess) {
                call.respondText("{\"success\":true,\"message\":\"Divisi berhasil diarsipkan\"}", contentType = ContentType.Application.Json)
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    result.exceptionOrNull()?.message ?: "Gagal mengarsipkan divisi"
                )
            }
        }

        // GET /api/tenant/departments/archived — daftar divisi yang sudah diarsipkan
        get("/archived") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@get
            }
            val result = departmentRepository.findAllArchived(tenant.tenantId)
            call.respondText(DepartmentDto.toJsonList(result), contentType = ContentType.Application.Json)
        }

        // POST /api/tenant/departments/{id}/restore — pulihkan divisi dari arsip
        post("/{id}/restore") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@post
            }
            val deptId = call.parameters["id"]?.let { DepartmentId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing department id")
                return@post
            }

            val result = restoreDepartmentUseCase(tenant.tenantId, deptId)
            if (result.isSuccess) {
                call.respondText("{\"success\":true,\"message\":\"Divisi berhasil dipulihkan\"}", contentType = ContentType.Application.Json)
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    result.exceptionOrNull()?.message ?: "Gagal memulihkan divisi"
                )
            }
        }

        post("/restore-presets") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@post
            }

            // Defense in depth atas moduleGate di atas (yang hanya terpasang bila repository wewenang disuntik):
            // menimpa seluruh divisi menuntut MANAGE dan jangkauan penuh, sama seperti rute karyawan.
            val decision = call.orgChartDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireOrgChartAccess(decision, AccessLevel.MANAGE)) return@post
            if (employeeRepository != null) {
                val reach = call.orgChartDataReach(
                    decision = decision,
                    allEmployees = GetEmployeesUseCase(employeeRepository).getAll(tenant.tenantId, null).getOrDefault(emptyList())
                )
                if (!reach.isUnrestricted) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        "Memuat ulang template struktur menimpa seluruh divisi, sehingga menuntut " +
                            "jangkauan data Seluruh Data Pabrik."
                    )
                    return@post
                }
            }
            if (!call.requireStarterOrgChart(tenant)) return@post

            val result = restoreDefaultDepartmentsUseCase(tenant.tenantId)
            if (result.isSuccess) {
                call.respondText(DepartmentDto.toJsonList(result.getOrThrow()), contentType = ContentType.Application.Json)
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    result.exceptionOrNull()?.message ?: "Failed to restore default departments"
                )
            }
        }
    }
}

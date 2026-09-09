package com.eventverse.app.routes

import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.orgchart.EmailConflictException
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.orgchart.usecases.*
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.routes.dto.CreateEmployeeRequestDto
import com.eventverse.app.routes.dto.EmailConflictResponseDto
import com.eventverse.app.routes.dto.EmployeeDto
import com.eventverse.app.routes.dto.UpdateEmployeeRequestDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.employeeRoutes(
    employeeRepository: EmployeeRepository,
    departmentRepository: DepartmentRepository
) {
    val getEmployeesUseCase = GetEmployeesUseCase(employeeRepository)
    val getTShapeUseCase = GetEmployeeTShapeHierarchyUseCase(employeeRepository)
    val createEmployeeUseCase = CreateEmployeeUseCase(employeeRepository, departmentRepository)
    val updateEmployeeUseCase = UpdateEmployeeUseCase(employeeRepository, departmentRepository)
    val archiveEmployeeUseCase = ArchiveEmployeeUseCase(employeeRepository)
    val restoreEmployeeUseCase = RestoreEmployeeUseCase(employeeRepository)
    val restoreDefaultEmployeesUseCase = RestoreDefaultEmployeesUseCase(employeeRepository, departmentRepository)

    route("/api/tenant/employees") {
        get {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@get
            }

            val deptFilter = call.request.queryParameters["departmentId"]?.let { DepartmentId(it) }
            val result = getEmployeesUseCase.getAll(tenant.tenantId, deptFilter)

            if (result.isSuccess) {
                call.respondText(EmployeeDto.toJsonList(result.getOrThrow()), contentType = ContentType.Application.Json)
            } else {
                call.respond(HttpStatusCode.InternalServerError, result.exceptionOrNull()?.message ?: "Failed to load employees")
            }
        }

        get("/{id}") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@get
            }
            val empId = call.parameters["id"]?.let { OrgNodeId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing employee id")
                return@get
            }

            val result = getEmployeesUseCase.getById(tenant.tenantId, empId)
            if (result.isSuccess) {
                val emp = result.getOrThrow()
                if (emp != null) {
                    call.respondText(EmployeeDto.toJson(emp), contentType = ContentType.Application.Json)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Employee not found")
                }
            } else {
                call.respond(HttpStatusCode.InternalServerError, result.exceptionOrNull()?.message ?: "Failed to find employee")
            }
        }

        get("/{id}/t-shape") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@get
            }
            val empId = call.parameters["id"]?.let { OrgNodeId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing employee id")
                return@get
            }

            val result = getTShapeUseCase(tenant.tenantId, empId)
            if (result.isSuccess) {
                call.respondText(EmployeeDto.toTShapeJson(result.getOrThrow()), contentType = ContentType.Application.Json)
            } else {
                call.respond(HttpStatusCode.NotFound, result.exceptionOrNull()?.message ?: "Failed to resolve T-Shape hierarchy")
            }
        }

        post {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@post
            }

            val rawBody = call.receiveText()
            val req = CreateEmployeeRequestDto.fromJson(rawBody)

            val result = createEmployeeUseCase(
                CreateEmployeeCommand(
                    tenantId = tenant.tenantId,
                    name = req.name,
                    email = req.email,
                    departmentId = req.departmentId?.takeIf { it.isNotBlank() }?.let { DepartmentId(it) },
                    level = req.level,
                    roleTitle = req.roleTitle,
                    reportsToId = req.reportsToId?.let { OrgNodeId(it) },
                    phone = req.phone,
                    successionAction = req.successionAction
                )
            )

            if (result.isSuccess) {
                val created = result.getOrThrow()
                call.respondText(
                    text = EmployeeDto.toJson(created),
                    status = HttpStatusCode.Created,
                    contentType = ContentType.Application.Json
                )
            } else {
                val ex = result.exceptionOrNull()
                if (ex is EmailConflictException) {
                    val dto = EmailConflictResponseDto(
                        email = ex.email,
                        existingEmployeeId = ex.existingEmployeeId,
                        existingEmployeeName = ex.existingEmployeeName,
                        existingDepartmentName = ex.existingDepartmentName,
                        existingRoleTitle = ex.existingRoleTitle,
                        isArchived = ex.isArchived,
                        message = ex.message ?: ""
                    )
                    call.respondText(
                        text = dto.toJson(),
                        status = HttpStatusCode.Conflict,
                        contentType = ContentType.Application.Json
                    )
                } else {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ex?.message ?: "Failed to create employee"
                    )
                }
            }
        }

        put("/{id}") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@put
            }
            val empId = call.parameters["id"]?.let { OrgNodeId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing employee id")
                return@put
            }

            val rawBody = call.receiveText()
            val req = UpdateEmployeeRequestDto.fromJson(rawBody)

            val result = updateEmployeeUseCase(
                UpdateEmployeeCommand(
                    tenantId = tenant.tenantId,
                    id = empId,
                    name = req.name,
                    email = req.email,
                    departmentId = req.departmentId?.let { DepartmentId(it) },
                    level = req.level,
                    roleTitle = req.roleTitle,
                    reportsToId = req.reportsToId?.let { OrgNodeId(it) },
                    phone = req.phone,
                    successionAction = req.successionAction
                )
            )

            if (result.isSuccess) {
                call.respondText(EmployeeDto.toJson(result.getOrThrow()), contentType = ContentType.Application.Json)
            } else {
                val ex = result.exceptionOrNull()
                if (ex is EmailConflictException) {
                    val dto = EmailConflictResponseDto(
                        email = ex.email,
                        existingEmployeeId = ex.existingEmployeeId,
                        existingEmployeeName = ex.existingEmployeeName,
                        existingDepartmentName = ex.existingDepartmentName,
                        existingRoleTitle = ex.existingRoleTitle,
                        isArchived = ex.isArchived,
                        message = ex.message ?: ""
                    )
                    call.respondText(
                        text = dto.toJson(),
                        status = HttpStatusCode.Conflict,
                        contentType = ContentType.Application.Json
                    )
                } else {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ex?.message ?: "Failed to update employee"
                    )
                }
            }
        }

        delete("/{id}") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@delete
            }
            val empId = call.parameters["id"]?.let { OrgNodeId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing employee id")
                return@delete
            }

            // Soft archive — data tidak dihapus dari DB (pola Odoo)
            val result = archiveEmployeeUseCase(tenant.tenantId, empId)
            if (result.isSuccess) {
                call.respondText("{\"success\":true,\"message\":\"Karyawan berhasil diarsipkan\"}", contentType = ContentType.Application.Json)
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    result.exceptionOrNull()?.message ?: "Gagal mengarsipkan karyawan"
                )
            }
        }

        // GET /api/tenant/employees/archived — daftar karyawan yang sudah diarsipkan
        get("/archived") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@get
            }
            val result = employeeRepository.findAllArchived(tenant.tenantId)
            call.respondText(EmployeeDto.toJsonList(result), contentType = ContentType.Application.Json)
        }

        // POST /api/tenant/employees/{id}/restore — pulihkan karyawan dari arsip
        post("/{id}/restore") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@post
            }
            val empId = call.parameters["id"]?.let { OrgNodeId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing employee id")
                return@post
            }

            val result = restoreEmployeeUseCase(tenant.tenantId, empId)
            if (result.isSuccess) {
                call.respondText("{\"success\":true,\"message\":\"Karyawan berhasil dipulihkan\"}", contentType = ContentType.Application.Json)
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    result.exceptionOrNull()?.message ?: "Gagal memulihkan karyawan"
                )
            }
        }

        post("/restore-presets") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@post
            }

            val result = restoreDefaultEmployeesUseCase(tenant.tenantId)
            if (result.isSuccess) {
                call.respondText(EmployeeDto.toJsonList(result.getOrThrow()), contentType = ContentType.Application.Json)
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    result.exceptionOrNull()?.message ?: "Failed to restore default employees"
                )
            }
        }
    }
}

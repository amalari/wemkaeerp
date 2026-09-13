package com.eventverse.app.routes

import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.orgchart.EmailConflictException
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.orgchart.usecases.*
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.plugins.callerPrincipalOrNull
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
    departmentRepository: DepartmentRepository,
    /**
     * Dipakai untuk menghitung **wewenang dan jangkauan** pemanggil atas modul Bagan Organisasi.
     *
     * Nullable supaya pemasangan route lama dan pengujian yang tidak menyuntikkannya tetap
     * berjalan seperti sebelum penjagaan ini ada — lihat `OrgChartAccessGuard.kt`.
     */
    roleRepository: RoleRepository? = null,
    moduleAssignmentRepository: ModuleAssignmentRepository? = null
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


            val decision = call.orgChartDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireOrgChartAccess(decision, AccessLevel.VIEW)) return@get
            val deptFilter = call.request.queryParameters["departmentId"]?.let { DepartmentId(it) }
            val result = getEmployeesUseCase.getAll(tenant.tenantId, deptFilter)

            if (result.isSuccess) {
                val visible = applyOrgChartScope(
                    employees = result.getOrThrow(),
                    decision = decision,
                    viewerEmail = call.callerPrincipalOrNull?.email,
                    viewerDepartmentId = call.callerPrincipalOrNull?.departmentId
                )
                call.respondText(EmployeeDto.toJsonList(visible), contentType = ContentType.Application.Json)
            } else {
                call.respond(HttpStatusCode.InternalServerError, result.exceptionOrNull()?.message ?: "Failed to load employees")
            }
        }

        get("/{id}") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@get
            }

            val decision = call.orgChartDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireOrgChartAccess(decision, AccessLevel.VIEW)) return@get
            val empId = call.parameters["id"]?.let { OrgNodeId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing employee id")
                return@get
            }

            // GET /employees menyaring dengan benar, tetapi id karyawan berpola (`emp-joko`,
            // `emp-budi`, ...) dan bisa ditebak. Tanpa baris ini, memanggil endpoint ini langsung
            // melewati seluruh penyaringan jangkauan yang barusan diperiksa di atas.
            val reach = call.orgChartDataReach(
                decision = decision,
                allEmployees = getEmployeesUseCase.getAll(tenant.tenantId, null).getOrDefault(emptyList())
            )
            if (!call.requireReachableEmployee(reach, empId.value)) return@get

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

            val decision = call.orgChartDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireOrgChartAccess(decision, AccessLevel.VIEW)) return@get
            val empId = call.parameters["id"]?.let { OrgNodeId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing employee id")
                return@get
            }

            // Sama seperti GET /{id}: fokus T-Shape juga bisa ditebak lewat id, dan hasilnya
            // (superior, peer, subordinate) membawa data karyawan lain di luar jangkauan bila
            // tidak diperiksa di sini.
            val reach = call.orgChartDataReach(
                decision = decision,
                allEmployees = getEmployeesUseCase.getAll(tenant.tenantId, null).getOrDefault(emptyList())
            )
            if (!call.requireReachableEmployee(reach, empId.value)) return@get

            val result = getTShapeUseCase(tenant.tenantId, empId)
            if (result.isSuccess) {
                // Fokusnya sudah tervalidasi lewat requireReachableEmployee di atas; yang tersisa
                // adalah menyaring penumpang gelap di sekitarnya (superior, peerHeads, dst) yang
                // bisa jadi berada di luar jangkauan yang sama.
                val restricted = result.getOrThrow().restrictToReach(reach)
                call.respondText(EmployeeDto.toTShapeJson(restricted), contentType = ContentType.Application.Json)
            } else {
                call.respond(HttpStatusCode.NotFound, result.exceptionOrNull()?.message ?: "Failed to resolve T-Shape hierarchy")
            }
        }

        post {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@post
            }

            val decision = call.orgChartDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireOrgChartAccess(decision, AccessLevel.OPERATE)) return@post

            val rawBody = call.receiveText()
            val req = CreateEmployeeRequestDto.fromJson(rawBody)

            val reach = call.orgChartDataReach(
                decision = decision,
                allEmployees = getEmployeesUseCase.getAll(tenant.tenantId, null).getOrDefault(emptyList())
            )
            if (!call.requireWritableDepartment(reach, req.departmentId?.takeIf { it.isNotBlank() })) return@post

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

            val decision = call.orgChartDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireOrgChartAccess(decision, AccessLevel.OPERATE)) return@put
            val empId = call.parameters["id"]?.let { OrgNodeId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing employee id")
                return@put
            }

            val rawBody = call.receiveText()
            val req = UpdateEmployeeRequestDto.fromJson(rawBody)

            val reach = call.orgChartDataReach(
                decision = decision,
                allEmployees = getEmployeesUseCase.getAll(tenant.tenantId, null).getOrDefault(emptyList())
            )
            if (!call.requireReachableEmployee(reach, empId.value)) return@put
            // Divisi tujuan ikut diperiksa: memindahkan orang ke divisi di luar jangkauan sama
            // saja dengan menulis ke sana, hanya lewat pintu yang berbeda.
            if (!call.requireWritableDepartment(reach, req.departmentId?.takeIf { it.isNotBlank() })) return@put

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

            val decision = call.orgChartDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireOrgChartAccess(decision, AccessLevel.MANAGE)) return@delete
            val reach = call.orgChartDataReach(
                decision = decision,
                allEmployees = getEmployeesUseCase.getAll(tenant.tenantId, null).getOrDefault(emptyList())
            )
            if (!call.requireReachableEmployee(reach, call.parameters["id"].orEmpty())) return@delete
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

            val decision = call.orgChartDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireOrgChartAccess(decision, AccessLevel.MANAGE)) return@get
            val result = employeeRepository.findAllArchived(tenant.tenantId)
            call.respondText(EmployeeDto.toJsonList(result), contentType = ContentType.Application.Json)
        }

        // POST /api/tenant/employees/{id}/restore — pulihkan karyawan dari arsip
        post("/{id}/restore") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@post
            }

            val decision = call.orgChartDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireOrgChartAccess(decision, AccessLevel.MANAGE)) return@post
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

            val decision = call.orgChartDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireOrgChartAccess(decision, AccessLevel.MANAGE)) return@post
            // Memuat ulang template menimpa seluruh bagan, lintas divisi. Tidak ada jangkauan
            // sempit yang masuk akal untuk itu: yang boleh menekannya harus melihat semuanya.
            val reach = call.orgChartDataReach(
                decision = decision,
                allEmployees = getEmployeesUseCase.getAll(tenant.tenantId, null).getOrDefault(emptyList())
            )
            if (!reach.isUnrestricted) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    "Memuat ulang template struktur menimpa seluruh divisi, sehingga menuntut " +
                        "jangkauan data Seluruh Data Pabrik."
                )
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

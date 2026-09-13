package com.eventverse.app.routes

import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.orgchart.EmailConflictException
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.orgchart.OrgChartVisibility
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.usecases.*
import com.eventverse.app.domain.rbac.AccessDecisionEngine
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.tenant.TenantContext
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
     * Dipakai hanya untuk menghitung jangkauan data pemanggil atas modul Bagan Organisasi.
     *
     * Nullable supaya pemasangan route lama dan pengujian yang tidak peduli jangkauan tetap
     * berjalan; bila keduanya null, daftar karyawan dikembalikan utuh seperti sebelumnya.
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

            val deptFilter = call.request.queryParameters["departmentId"]?.let { DepartmentId(it) }
            val result = getEmployeesUseCase.getAll(tenant.tenantId, deptFilter)

            if (result.isSuccess) {
                val visible = call.applyOrgChartScope(
                    employees = result.getOrThrow(),
                    tenant = tenant,
                    roleRepository = roleRepository,
                    moduleAssignmentRepository = moduleAssignmentRepository
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

/**
 * Mempersempit daftar karyawan menurut jangkauan data pemanggil atas modul Bagan Organisasi.
 *
 * Inilah sisi **penentu** dari `ScopeCapability.HIERARCHICAL`. Klien menjalankan penyaringan yang
 * sama supaya layarnya konsisten seketika, tetapi penyaringan yang hanya hidup di klien tidak
 * menyembunyikan apa pun — payload-nya tetap utuh dan terbaca siapa saja yang membuka panel jaringan.
 *
 * Jangkauan dihitung lewat [AccessDecisionEngine], bukan dengan membaca `role.getAccess(...)`
 * langsung. Wewenang datang dari dua sumbu yang disatukan — jabatan dan penugasan divisi — dan
 * menghitung ulang salah satunya di sini akan menjadi aturan kedua yang bisa menyimpang dari yang
 * dipakai menu dan layar.
 */
private suspend fun ApplicationCall.applyOrgChartScope(
    employees: List<OrgNode>,
    tenant: TenantContext,
    roleRepository: RoleRepository?,
    moduleAssignmentRepository: ModuleAssignmentRepository?
): List<OrgNode> {
    val principal = callerPrincipalOrNull ?: return employees

    // Tanpa repository wewenang, tidak ada dasar untuk mempersempit apa pun. Mengembalikan daftar
    // utuh adalah perilaku sebelum fitur ini ada — bukan penurunan keamanan, karena route-nya tetap
    // hanya terjangkau oleh pemanggil yang sudah terautentikasi dan terikat tenant ini.
    if (roleRepository == null || moduleAssignmentRepository == null) return employees

    // Wewenang hanya datang dari dua sumbu: jabatan dan divisi. Pemanggil yang tidak punya keduanya
    // tidak punya jangkauan yang bisa dipersempit, apa pun isi matriksnya — jadi menanyakannya ke
    // database hanya menghasilkan dua query untuk jawaban yang sudah pasti. Ini juga menjaga jalur
    // lama tetap utuh bagi token layanan yang memang tidak membawa identitas pabrik.
    if (principal.customRoleId == null && principal.departmentId == null) return employees

    val role = principal.customRoleId
        ?.let { runCatching { RoleId(it) }.getOrNull() }
        ?.let { roleRepository.findById(tenant.tenantId, it) }

    val persona = TestingPersona(
        userId = principal.userId.ifBlank { "unknown" },
        name = principal.email ?: principal.userId.ifBlank { "unknown" },
        tenantId = tenant.tenantId,
        tenantSlug = tenant.slug.value,
        departmentId = principal.departmentId,
        departmentName = "",
        roleId = role?.id,
        roleTitle = role?.name ?: "",
        // Invarian TestingPersona melarang bypass bagi persona berjabatan: memilih sebuah jabatan
        // berarti minta dilihat persis sebagai jabatan itu. Bypass karenanya hanya untuk akun
        // platform yang memang tidak punya jabatan di pabrik mana pun.
        isOwnerOrSuperAdmin = principal.isPlatformSuperadmin && role == null
    )

    val assignments = moduleAssignmentRepository.findAllByTenant(tenant.tenantId)
    val decision = AccessDecisionEngine.explain(
        persona = persona,
        module = BusinessModule.ORG_CHART,
        role = role,
        assignments = assignments[BusinessModule.ORG_CHART].orEmpty()
    )

    if (decision.config.scope == DataScope.ALL_TENANT_DATA) return employees

    // Baris karyawan milik penonton dicocokkan lewat email: `users` menyimpan divisi dan jabatan
    // sejak V17, tetapi tidak menyimpan tautan ke baris `employees`. Email unik per tenant, jadi
    // pencocokan ini deterministik — dan bila tidak ketemu, penonton memang bukan karyawan terdaftar
    // sehingga hanya sumbu divisi yang berlaku baginya.
    val viewerEmployeeId = principal.email
        ?.let { email -> employees.firstOrNull { it.email.equals(email, ignoreCase = true) }?.id }

    return OrgChartVisibility.visibleTo(
        nodes = employees,
        scope = decision.config.scope,
        viewerEmployeeId = viewerEmployeeId,
        viewerDepartmentId = principal.departmentId
    )
}

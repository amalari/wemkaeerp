package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.rbac.usecases.*
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.routes.dto.CreateRoleRequestDto
import com.eventverse.app.routes.dto.RoleDto
import com.eventverse.app.routes.dto.UpdateRoleRequestDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.rbacRoutes(roleRepository: RoleRepository, moduleAssignmentRepository: com.eventverse.app.domain.rbac.ModuleAssignmentRepository) {
    val getRolesUseCase = GetRolesUseCase(roleRepository)
    val createRoleUseCase = CreateRoleUseCase(roleRepository)
    val updateRoleUseCase = UpdateRoleUseCase(roleRepository)
    val deleteRoleUseCase = DeleteRoleUseCase(roleRepository)
    val restoreDefaultRolesUseCase = RestoreDefaultRolesUseCase(roleRepository)

    route("/api/tenant/roles") {
        // B5: baca = VIEW, tulis = MANAGE (fail-closed). Menu pengguna kini dari GET /api/tenant/me/access, jadi daftar
        // wewenang semua orang hanya untuk admin.
        moduleGate(com.eventverse.app.domain.rbac.BusinessModule.DYNAMIC_RBAC, roleRepository, moduleAssignmentRepository)

        get {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@get
            }
            val result = getRolesUseCase.getAll(tenant.tenantId)
            if (result.isSuccess) {
                call.respondText(RoleDto.toJsonList(result.getOrThrow()), contentType = ContentType.Application.Json)
            } else {
                call.respond(HttpStatusCode.InternalServerError, result.exceptionOrNull()?.message ?: "Failed to load roles")
            }
        }

        get("/{id}") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@get
            }
            val roleId = call.parameters["id"]?.let { RoleId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing role id")
                return@get
            }

            val result = getRolesUseCase.getById(tenant.tenantId, roleId)
            if (result.isSuccess) {
                val role = result.getOrThrow()
                if (role != null) {
                    call.respondText(RoleDto.toJson(role), contentType = ContentType.Application.Json)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Role not found")
                }
            } else {
                call.respond(HttpStatusCode.InternalServerError, result.exceptionOrNull()?.message ?: "Failed to find role")
            }
        }

        post {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@post
            }

            val rawBody = call.receiveText()
            val req = if (rawBody.isNotBlank()) {
                CreateRoleRequestDto.fromJson(rawBody)
            } else {
                val params = call.receiveParameters()
                CreateRoleRequestDto(
                    name = params["name"] ?: "",
                    description = params["description"] ?: "",
                    departmentId = params["departmentId"]?.takeIf { it.isNotBlank() }
                )
            }

            val result = createRoleUseCase(
                CreateRoleCommand(
                    tenantId = tenant.tenantId,
                    name = req.name,
                    description = req.description,
                    modulePermissions = req.modulePermissions,
                    departmentId = req.departmentId
                )
            )

            if (result.isSuccess) {
                val created = result.getOrThrow()
                call.respondText(
                    text = RoleDto.toJson(created),
                    status = HttpStatusCode.Created,
                    contentType = ContentType.Application.Json
                )
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    result.exceptionOrNull()?.message ?: "Failed to create role"
                )
            }
        }

        put("/{id}") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@put
            }
            val roleId = call.parameters["id"]?.let { RoleId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing role id")
                return@put
            }

            val rawBody = call.receiveText()
            val req = UpdateRoleRequestDto.fromJson(rawBody)

            val result = updateRoleUseCase(
                UpdateRoleCommand(
                    tenantId = tenant.tenantId,
                    roleId = roleId,
                    name = req.name,
                    description = req.description,
                    modulePermissions = req.modulePermissions,
                    departmentId = req.departmentId
                )
            )

            if (result.isSuccess) {
                call.respondText(RoleDto.toJson(result.getOrThrow()), contentType = ContentType.Application.Json)
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    result.exceptionOrNull()?.message ?: "Failed to update role"
                )
            }
        }

        delete("/{id}") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@delete
            }
            val roleId = call.parameters["id"]?.let { RoleId(it) } ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing role id")
                return@delete
            }

            val result = deleteRoleUseCase(tenant.tenantId, roleId)
            if (result.isSuccess) {
                call.respondText("{\"success\":true,\"message\":\"Role deleted successfully\"}", contentType = ContentType.Application.Json)
            } else {
                call.respond(
                    HttpStatusCode.BadRequest,
                    result.exceptionOrNull()?.message ?: "Failed to delete role"
                )
            }
        }

        post("/restore-presets") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@post
            }

            val result = restoreDefaultRolesUseCase(tenant.tenantId)
            if (result.isSuccess) {
                call.respondText(RoleDto.toJsonList(result.getOrThrow()), contentType = ContentType.Application.Json)
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    result.exceptionOrNull()?.message ?: "Failed to restore default roles"
                )
            }
        }
    }
}

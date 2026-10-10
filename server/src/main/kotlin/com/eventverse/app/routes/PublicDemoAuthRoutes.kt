package com.eventverse.app.routes

import com.eventverse.app.domain.auth.*
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.infrastructure.auth.JwtTokenService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/**
 * Gerbang login demo. `POST /api/public/auth/demo` menerbitkan token tanpa kredensial (tombol
 * "Demo Mode" dan persona pengujian), jadi di internet terbuka ia adalah pintu belakang ke akun
 * superadmin dan Owner tenant mana pun.
 *
 * **Bawaan mati.** Menyala hanya bila `WEMADE_DEMO_LOGIN` = `on`/`true`/`1` (pola sama dengan
 * `WEMADE_PUBLIC_SIGNUP`). Saat mati, endpoint membalas 404 seolah tidak ada — tanpa membuat user
 * maupun token. Saat menyala pun hanya tenant di [demoTenantSlugs] (bawaan `wemade-demo`;
 * tambahan lewat `WEMADE_DEMO_TENANTS`, daftar dipisah koma) yang boleh dimasuki lewat jalur ini.
 */
data class DemoLoginPolicy(
    val enabled: Boolean = false,
    val demoTenantSlugs: Set<String> = DEFAULT_DEMO_TENANTS
) {
    fun allows(slug: String): Boolean = enabled && slug.lowercase() in demoTenantSlugs

    companion object {
        val DEFAULT_DEMO_TENANTS: Set<String> = setOf("wemade-demo")

        fun fromEnv(env: (String) -> String? = System::getenv): DemoLoginPolicy {
            val extra = env("WEMADE_DEMO_TENANTS").orEmpty().split(',')
                .map { it.trim().lowercase() }.filter { it.isNotBlank() }
            return DemoLoginPolicy(
                enabled = env("WEMADE_DEMO_LOGIN")?.lowercase() in setOf("on", "true", "1"),
                demoTenantSlugs = DEFAULT_DEMO_TENANTS + extra
            )
        }
    }
}

/** Dipasang di dalam `route("/api/public/auth")`. */
fun Route.demoAuthRoutes(
    policy: DemoLoginPolicy,
    jwtTokenService: JwtTokenService,
    repository: TenantRepository,
    userRepo: UserRepository,
    roleRepo: RoleRepository
) {
    post("/demo") {
        if (!policy.enabled) {
            call.respond(HttpStatusCode.NotFound)
            return@post
        }
        val params = runCatching { call.receiveParameters() }.getOrNull()
        fun field(name: String): String? = params?.get(name)?.ifBlank { null }
            ?: call.request.queryParameters[name]?.ifBlank { null }

        val tenantSlug = field("tenantSlug") ?: "wemade-demo"
        if (!policy.allows(tenantSlug)) {
            call.respond(HttpStatusCode.Forbidden, "Tenant '$tenantSlug' bukan tenant demo; login demo ditolak.")
            return@post
        }
        val tenant = repository.findBySlug(TenantSlug(tenantSlug))
        if (tenant == null) {
            call.respond(HttpStatusCode.NotFound, "Tenant dengan slug '$tenantSlug' tidak ditemukan")
            return@post
        }

        val requestedRole = field("role")
        val isSuperAdmin = requestedRole.equals("PLATFORM_SUPERADMIN", ignoreCase = true) ||
            requestedRole.equals("superadmin", ignoreCase = true)

        // Persona pengujian: nama bebas + jabatan rakitan tenant + divisi. Dikenali dari
        // adanya `username`, karena login demo lama tidak pernah mengirimkannya.
        val personaName = field("username")
        if (!isSuperAdmin && personaName != null) {
            resolvePersonaUser(
                userRepo = userRepo,
                roleRepo = roleRepo,
                tenantId = tenant.id,
                personaName = personaName,
                tenantSlug = tenantSlug,
                requestedRoleId = requestedRole,
                departmentId = field("departmentId")
            )
                .onSuccess { personaUser ->
                    val personaToken = jwtTokenService.generateToken(personaUser, tenantSlug)
                    call.respondText(
                        authSessionJson(personaUser, personaToken.value, tenantSlug),
                        contentType = ContentType.Application.Json
                    )
                }
                .onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.message ?: "Gagal menyiapkan persona pengujian")
                }
            return@post
        }

        val user = if (isSuperAdmin) {
            userRepo.findByEmail(EmailAddress("superadmin@wemade.id"))
                ?: User(
                    id = UserId("usr-superadmin-001"),
                    tenantId = tenant.id,
                    username = Username("superadmin_apps"),
                    email = EmailAddress("superadmin@wemade.id"),
                    role = Role.PLATFORM_SUPERADMIN,
                    isActive = true
                ).also { userRepo.save(it) }
        } else {
            // Tanpa fallback email global: akun di luar tenant ini tidak pernah dipinjam.
            userRepo.findAllByTenant(tenant.id).firstOrNull { it.role == Role.TENANT_ADMIN }
                ?: User(
                    id = UserId("usr-owner-001"),
                    tenantId = tenant.id,
                    username = Username("achmad_owner"),
                    email = EmailAddress("student.achmad@gmail.com"),
                    role = Role.TENANT_ADMIN,
                    isActive = true
                ).also { userRepo.save(it) }
        }

        // Token tidak pernah diterbitkan untuk user yang bukan milik tenant yang diminta.
        // Superadmin platform tidak terikat tenant, jadi dikecualikan.
        if (!isSuperAdmin && user.tenantId != tenant.id) {
            call.respond(HttpStatusCode.Forbidden, "Akun demo bukan milik tenant '$tenantSlug'.")
            return@post
        }

        val sessionToken = jwtTokenService.generateToken(user, tenantSlug)
        call.respondText(
            authSessionJson(user, sessionToken.value, tenantSlug),
            contentType = ContentType.Application.Json
        )
    }
}

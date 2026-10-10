package com.eventverse.app.routes

import com.eventverse.app.domain.auth.*
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.HostSurface
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.infrastructure.auth.GoogleAuthService
import com.eventverse.app.infrastructure.auth.JwtTokenService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Auth publik (`/api/public/auth`): Google sign-in, login demo/persona, dan introspeksi sesi.
 *
 * Dipindah utuh dari `Application.kt` (cicilan Ratchet file-size, PLAN-builder-console F1):
 * blok ini satu agregat yang berdiri sendiri dan hanya bergantung pada layar auth — tidak ada
 * alasan ia tinggal di file aplikasi. Perilaku route identik baris-per-baris dengan sebelumnya.
 */
fun Route.publicAuthRoutes(
    googleAuthService: GoogleAuthService,
    authenticateWithGoogleUseCase: AuthenticateWithGoogleUseCase,
    jwtTokenService: JwtTokenService,
    repository: TenantRepository,
    userRepo: UserRepository,
    roleRepo: RoleRepository,
    platformBaseDomain: String? = null
) {
    route("/api/public/auth") {
        get("/google/url") {
            val redirectUri = call.request.queryParameters["redirect_uri"] ?: "http://localhost:8081/api/auth/google/callback"
            val state = call.request.queryParameters["state"]
            val url = googleAuthService.buildAuthorizationUrl(redirectUri, state)
            call.respondText(
                text = "{\"url\":\"$url\",\"clientId\":\"${googleAuthService.clientId}\"}",
                contentType = ContentType.Application.Json
            )
        }

        post("/google") {
            val params = call.receiveParameters()
            val idToken = params["idToken"] ?: ""
            // discovery-M3: di subdomain tenant, host yang menentukan tenant; di `app.` tenant
            // diturunkan dari akun (slug kosong). Slug yang bertentangan dengan host ditolak.
            val requestedSlug = params["tenantSlug"]?.trim()?.ifBlank { null }
            val surface = HostSurface.parse(call.request.host(), platformBaseDomain)
            val hostSlug = (surface as? HostSurface.Tenant)?.slug?.value
            if (hostSlug != null && requestedSlug != null && requestedSlug != hostSlug) {
                call.respond(HttpStatusCode.Forbidden, "Subdomain '$hostSlug' tidak cocok dengan tenant '$requestedSlug'")
                return@post
            }
            val commandSlug = hostSlug ?: requestedSlug

            if (idToken.isBlank() || (commandSlug == null && surface !is HostSurface.Platform)) {
                call.respond(HttpStatusCode.BadRequest, "idToken and tenantSlug are required")
                return@post
            }

            val verifyResult = googleAuthService.verifyIdToken(idToken)
            if (verifyResult.isFailure) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    verifyResult.exceptionOrNull()?.message ?: "Google token verification failed"
                )
                return@post
            }

            val googleProfile = verifyResult.getOrThrow()
            val authResult = authenticateWithGoogleUseCase(
                AuthenticateWithGoogleCommand(googleProfile, commandSlug)
            )

            if (authResult.isSuccess) {
                val user = authResult.getOrThrow()
                val tenantSlug = commandSlug
                    ?: user.tenantId?.let { repository.findById(it)?.slug?.value }
                    ?: ""
                // Fail-closed: akun tenant tanpa slug yang dapat ditentukan tidak boleh mendapat
                // token (dulu terbit dengan tenant_slug null lalu ditolak gerbang di tiap request).
                if (tenantSlug.isBlank() && user.role != Role.PLATFORM_SUPERADMIN) {
                    call.respond(HttpStatusCode.Forbidden, "Tenant akun tidak dapat ditentukan")
                    return@post
                }
                val sessionToken = jwtTokenService.generateToken(user, tenantSlug.ifBlank { null })
                call.respondText(authSessionJson(user, sessionToken.value, tenantSlug), contentType = ContentType.Application.Json)
            } else {
                call.respond(
                    HttpStatusCode.Forbidden,
                    authResult.exceptionOrNull()?.message ?: "Authentication failed"
                )
            }
        }

        post("/demo") {
            val params = runCatching { call.receiveParameters() }.getOrNull()
            val tenantSlug = params?.get("tenantSlug")?.ifBlank { null }
                ?: call.request.queryParameters["tenantSlug"]?.ifBlank { null }
                ?: "wemade-demo"

            val tenant = repository.findBySlug(TenantSlug(tenantSlug))
            if (tenant == null) {
                call.respond(HttpStatusCode.NotFound, "Tenant dengan slug '$tenantSlug' tidak ditemukan")
                return@post
            }

            fun field(name: String): String? = params?.get(name)?.ifBlank { null }
                ?: call.request.queryParameters[name]?.ifBlank { null }

            val requestedRole = field("role")
            val isSuperAdmin = requestedRole.equals("PLATFORM_SUPERADMIN", ignoreCase = true) ||
                requestedRole.equals("superadmin", ignoreCase = true)

            // Persona pengujian: nama bebas + jabatan rakitan tenant + divisi. Dikenali dari
            // adanya `username`, karena login demo lama tidak pernah mengirimkannya.
            val personaName = field("username")
            if (!isSuperAdmin && personaName != null) {
                val personaResult = resolvePersonaUser(
                    userRepo = userRepo,
                    roleRepo = roleRepo,
                    tenantId = tenant.id,
                    personaName = personaName,
                    tenantSlug = tenantSlug,
                    requestedRoleId = requestedRole,
                    departmentId = field("departmentId")
                )

                personaResult
                    .onSuccess { personaUser ->
                        val personaToken = jwtTokenService.generateToken(personaUser, tenantSlug)
                        call.respondText(
                            authSessionJson(personaUser, personaToken.value, tenantSlug),
                            contentType = ContentType.Application.Json
                        )
                    }
                    .onFailure {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            it.message ?: "Gagal menyiapkan persona pengujian"
                        )
                    }
                return@post
            }

            // Query real user from DB for this tenant or create fallback
            val user = if (isSuperAdmin) {
                userRepo.findByEmail(EmailAddress("superadmin@wemade.id"))
                    ?: run {
                        val superadmin = User(
                            id = UserId("usr-superadmin-001"),
                            tenantId = tenant.id,
                            username = Username("superadmin_apps"),
                            email = EmailAddress("superadmin@wemade.id"),
                            role = Role.PLATFORM_SUPERADMIN,
                            isActive = true
                        )
                        userRepo.save(superadmin)
                        superadmin
                    }
            } else {
                userRepo.findAllByTenant(tenant.id)
                    .firstOrNull { it.role == Role.TENANT_ADMIN }
                    ?: userRepo.findByEmail(EmailAddress("student.achmad@gmail.com"))
                    ?: run {
                        val fallback = User(
                            id = UserId("usr-owner-001"),
                            tenantId = tenant.id,
                            username = Username("achmad_owner"),
                            email = EmailAddress("student.achmad@gmail.com"),
                            role = Role.TENANT_ADMIN,
                            isActive = true
                        )
                        userRepo.save(fallback)
                        fallback
                    }
            }

            val sessionToken = jwtTokenService.generateToken(user, tenantSlug)

            call.respondText(
                authSessionJson(user, sessionToken.value, tenantSlug),
                contentType = ContentType.Application.Json
            )
        }

        get("/me") {
            val authHeader = call.request.header("Authorization") ?: ""
            val token = if (authHeader.startsWith("Bearer ")) authHeader.removePrefix("Bearer ").trim() else authHeader.trim()
            if (token.isBlank()) {
                call.respond(HttpStatusCode.Unauthorized, "No token provided")
                return@get
            }

            val verifyResult = jwtTokenService.verifyToken(token)
            if (verifyResult.isFailure) {
                call.respond(HttpStatusCode.Unauthorized, "Token expired or invalid")
                return@get
            }

            val jwt = verifyResult.getOrThrow()
            val userId = jwt.subject ?: ""
            val roleClaim = jwt.getClaim("role").asString()
            val tenantSlug = jwt.getClaim("tenant_slug").asString()?.takeIf { it.isNotBlank() }
            // Tanpa fallback senyap ke "wemade-demo": token tenant-bound tanpa slug ditolak.
            if (tenantSlug == null && roleClaim != Role.PLATFORM_SUPERADMIN.name) {
                call.respond(HttpStatusCode.Unauthorized, "Token tidak memuat tenant")
                return@get
            }
            val username = jwt.getClaim("username").asString() ?: ""
            val email = jwt.getClaim("email").asString() ?: ""
            val roleName = jwt.getClaim("role").asString()
            // Role tak dikenal dulu jatuh ke TENANT_ADMIN. Artinya identitas yang tidak dapat
            // dibaca justru diberi wewenang paling luas — persis kebalikan dari yang aman.
            // Sekarang jatuh ke STAFF, wewenang tersempit yang masih bisa login.
            val role = roleName
                ?.let { name -> runCatching { Role.valueOf(name) }.getOrNull() }
                ?: Role.OPERATOR
            val tenantIdStr = jwt.getClaim("tenant_id").asString()

            val user = userRepo.findById(UserId(userId)) ?: User(
                id = UserId(userId),
                tenantId = tenantIdStr?.let { TenantId(it) },
                username = Username(username),
                email = EmailAddress(email),
                role = role,
                isActive = true,
                // Baca ulang identitas tenant dari token, bukan diturunkan kembali. Inilah yang
                // membuat persona bertahan setelah halaman di-reload.
                departmentId = jwt.getClaim("department_id").asString(),
                customRoleId = jwt.getClaim("custom_role_id").asString()
            )

            call.respondText(
                authSessionJson(user, token, tenantSlug.orEmpty()),
                contentType = ContentType.Application.Json
            )
        }
    }
}

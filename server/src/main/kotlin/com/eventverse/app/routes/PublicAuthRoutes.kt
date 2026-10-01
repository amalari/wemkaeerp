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
            val tenantSlug = jwt.getClaim("tenant_slug").asString() ?: "wemade-demo"
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
                authSessionJson(user, token, tenantSlug),
                contentType = ContentType.Application.Json
            )
        }
    }
}

/**
 * Bentuk JSON sesi terotentikasi yang dipakai seluruh endpoint auth publik.
 *
 * Diangkat jadi satu fungsi karena tiga endpoint (`/demo`, `/google`, `/me`) sebelumnya merakit
 * string yang sama secara terpisah, dan penambahan field identitas tenant harus muncul di
 * ketiganya sekaligus — kalau tidak, client melihat persona hanya di sebagian jalur masuk.
 */
internal fun authSessionJson(user: User, token: String, tenantSlug: String): String {
    val permissionsJson = user.effectivePermissions.joinToString(",") { "\"${it.name}\"" }
    fun nullableJson(value: String?): String = if (value == null) "null" else "\"$value\""

    return "{\"token\":\"$token\",\"user\":{" +
        "\"id\":\"${user.id.value}\"," +
        "\"tenantId\":\"${user.tenantId?.value ?: ""}\"," +
        "\"username\":\"${user.username.value}\"," +
        "\"email\":\"${user.email.value}\"," +
        "\"role\":\"${user.role.name}\"," +
        "\"departmentId\":${nullableJson(user.departmentId)}," +
        "\"customRoleId\":${nullableJson(user.customRoleId)}," +
        "\"permissions\":[$permissionsJson]}," +
        "\"tenantSlug\":\"$tenantSlug\"}"
}

/**
 * Menemukan atau membuat akun untuk sebuah persona pengujian.
 *
 * Tiga hal yang membuat fungsi ini tidak sesederhana "insert user":
 *
 *  1. **Idempoten.** `users.email` UNIQUE dan `uq_tenant_username` UNIQUE. Login persona yang sama
 *     dua kali harus menemukan baris yang sama, bukan menabrak constraint. Karena itu email dan id
 *     diturunkan secara deterministik dari nama persona, bukan diacak.
 *  2. **Dua sumbu identitas.** `Role` platform menentukan izin tingkat sistem; `custom_role_id`
 *     menentukan isi layar. Jabatan tenant dipetakan ke `Role` yang paling mendekati agar izin
 *     sistem tidak melebar, sementara id jabatan aslinya disimpan apa adanya.
 *  3. **Jabatan harus nyata.** Id jabatan yang tidak ada di tenant ini ditolak, bukan diabaikan
 *     diam-diam — persona dengan jabatan hantu akan tampak "tidak punya akses apa pun" dan
 *     dilaporkan sebagai kerusakan.
 */
private suspend fun resolvePersonaUser(
    userRepo: UserRepository,
    roleRepo: RoleRepository,
    tenantId: TenantId,
    personaName: String,
    tenantSlug: String,
    requestedRoleId: String?,
    departmentId: String?
): Result<User> = runCatching {
    val trimmedName = personaName.trim()
    require(trimmedName.isNotBlank()) { "Nama persona tidak boleh kosong" }

    val slug = trimmedName.lowercase()
        .replace("[^a-z0-9]+".toRegex(), "-")
        .trim('-')
        .ifBlank { "anon" }

    val customRole = requestedRoleId
        ?.takeIf { it.isNotBlank() }
        ?.let { roleId ->
            roleRepo.findById(tenantId, com.eventverse.app.domain.rbac.RoleId(roleId))
                ?: error("Jabatan '$roleId' tidak ditemukan pada tenant ini")
        }

    val email = EmailAddress("persona-$tenantSlug-$slug@testing.local")
    val existing = userRepo.findByEmail(email)

    val persona = User(
        id = existing?.id ?: UserId("usr-persona-$slug".take(64)),
        tenantId = tenantId,
        username = Username("persona_${slug.replace('-', '_')}".take(50)),
        email = email,
        role = platformRoleFor(customRole?.name, requestedRoleId),
        isActive = true,
        departmentId = departmentId?.takeIf { it.isNotBlank() } ?: customRole?.departmentId,
        customRoleId = customRole?.id?.value
    )

    userRepo.save(persona).getOrThrow()
}

/**
 * Memetakan jabatan rakitan tenant ke [Role] platform yang paling mendekati.
 *
 * Default-nya sengaja [Role.OPERATOR] — wewenang tersempit. Jabatan yang tidak dikenali sebaiknya
 * membuat persona melihat terlalu sedikit, bukan terlalu banyak: yang pertama dilaporkan penguji,
 * yang kedua lolos tanpa disadari.
 */
private fun platformRoleFor(roleName: String?, roleId: String?): Role {
    val haystack = "${roleName.orEmpty()} ${roleId.orEmpty()}".lowercase()
    return when {
        haystack.contains("owner") || haystack.contains("direktur") -> Role.TENANT_ADMIN
        haystack.contains("sales") || haystack.contains("penjualan") -> Role.SALES
        haystack.contains("ppic") || haystack.contains("produksi") -> Role.PPIC_SUPERVISOR
        haystack.contains("qc") || haystack.contains("quality") -> Role.QC_INSPECTOR
        haystack.contains("gudang") || haystack.contains("warehouse") || haystack.contains("logistik") -> Role.WAREHOUSE
        else -> Role.OPERATOR
    }
}

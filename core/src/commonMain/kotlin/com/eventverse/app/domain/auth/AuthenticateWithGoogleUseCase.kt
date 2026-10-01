package com.eventverse.app.domain.auth

import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.Tenant

data class GoogleUserProfile(
    val email: String,
    val name: String?,
    val googleSubjectId: String,
    val emailVerified: Boolean
)

/**
 * [tenantSlug] `null` = login di permukaan platform (`app.<base>`, [com.eventverse.app.domain.tenant.HostSurface.Platform]):
 * tenant diturunkan dari akun itu sendiri. Aman karena satu email = satu user = satu tenant
 * (`UserRepository.findByEmail`); bila nanti satu email boleh di banyak tenant, jalur ini harus
 * mengembalikan pilihan, bukan menebak.
 */
data class AuthenticateWithGoogleCommand(
    val profile: GoogleUserProfile,
    val tenantSlug: String?
)

/**
 * Domain Use Case to authenticate and authorize a Google Sign-In identity within the target Tenant.
 */
class AuthenticateWithGoogleUseCase(
    private val userRepository: UserRepository,
    private val tenantRepository: TenantRepository
) {
    suspend operator fun invoke(command: AuthenticateWithGoogleCommand): Result<User> = runCatching {
        require(command.profile.emailVerified) { "Email dari Google belum terverifikasi" }
        val emailVo = EmailAddress(command.profile.email)
        val requestedSlug = command.tenantSlug?.trim()?.ifBlank { null }

        if (requestedSlug == null) return@runCatching authenticateOnPlatform(emailVo, command.profile.email)

        val tenant = tenantRepository.findBySlug(TenantSlug(requestedSlug))
            ?: error("Perusahaan / Subdomain '$requestedSlug' tidak ditemukan")
        requireAccessible(tenant)

        val user = userRepository.findByEmail(emailVo)
            ?: error("Akun Google (${command.profile.email}) belum terdaftar di ${tenant.name.value}. Silakan hubungi Admin Pabrik Anda.")

        if (user.role != Role.PLATFORM_SUPERADMIN) {
            require(user.tenantId == tenant.id) {
                "Akun ini tidak memiliki akses ke tenant '${tenant.name.value}'"
            }
        }

        require(user.isActive) { "Akun pengguna (${user.username.value}) sedang dinonaktifkan" }

        user
    }

    /** Tenant diambil dari akun; superadmin (tanpa tenant) tetap boleh masuk platform. */
    private suspend fun authenticateOnPlatform(email: EmailAddress, rawEmail: String): User {
        val user = userRepository.findByEmail(email)
            ?: error("Akun Google ($rawEmail) belum terdaftar. Daftarkan usaha Anda, atau minta undangan dari Admin Pabrik.")
        require(user.isActive) { "Akun pengguna (${user.username.value}) sedang dinonaktifkan" }
        if (user.role == Role.PLATFORM_SUPERADMIN) return user

        val tenantId = user.tenantId ?: error("Akun ini tidak terikat pada perusahaan mana pun")
        val tenant = tenantRepository.findById(tenantId)
            ?: error("Perusahaan untuk akun ini tidak ditemukan")
        requireAccessible(tenant)
        return user
    }

    // TRIAL ikut boleh: tenant hasil daftar publik berstatus TRIAL dan harus bisa login. Dulu hanya
    // ACTIVE, yang mengunci setiap tenant baru dari login Google — sama dengan aturan TenantStatus.isAccessible.
    private fun requireAccessible(tenant: Tenant) = require(tenant.isAccessible) {
        "Akses perusahaan '${tenant.name.value}' sedang nonaktif/ditangguhkan (${tenant.status})"
    }
}

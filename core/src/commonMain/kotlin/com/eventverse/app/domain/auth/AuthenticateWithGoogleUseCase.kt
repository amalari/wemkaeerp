package com.eventverse.app.domain.auth

import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus

data class GoogleUserProfile(
    val email: String,
    val name: String?,
    val googleSubjectId: String,
    val emailVerified: Boolean
)

data class AuthenticateWithGoogleCommand(
    val profile: GoogleUserProfile,
    val tenantSlug: String
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
        val slugVo = TenantSlug(command.tenantSlug)

        val tenant = tenantRepository.findBySlug(slugVo)
            ?: error("Perusahaan / Subdomain '${command.tenantSlug}' tidak ditemukan")

        require(tenant.status == TenantStatus.ACTIVE) {
            "Akses perusahaan '${tenant.name.value}' sedang nonaktif/ditangguhkan (${tenant.status})"
        }

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
}

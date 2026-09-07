package com.eventverse.app.domain.auth

import com.eventverse.app.domain.tenant.TenantId

data class RegisterUserCommand(
    val id: String,
    val tenantId: String?,
    val username: String,
    val email: String,
    val role: Role,
    val customPermissions: Set<Permission> = emptySet()
)

class RegisterUserUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(command: RegisterUserCommand): Result<User> = runCatching {
        val userId = UserId(command.id)
        val tenantId = command.tenantId?.let { TenantId(it) }
        val username = Username(command.username)
        val email = EmailAddress(command.email)

        val existingUser = userRepository.findById(userId)
        if (existingUser != null) {
            error("User with ID '${userId.value}' already exists")
        }

        val existingUsername = userRepository.findByUsername(tenantId, username)
        if (existingUsername != null) {
            error("Username '${username.value}' is already in use")
        }

        val existingEmail = userRepository.findByEmail(email)
        if (existingEmail != null) {
            error("Email '${email.value}' is already registered")
        }

        val user = User(
            id = userId,
            tenantId = tenantId,
            username = username,
            email = email,
            role = command.role,
            customPermissions = command.customPermissions,
            isActive = true
        )

        userRepository.save(user).getOrThrow()
    }
}

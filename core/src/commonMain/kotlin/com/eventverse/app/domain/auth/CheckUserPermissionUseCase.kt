package com.eventverse.app.domain.auth

import com.eventverse.app.domain.tenant.TenantId

data class CheckPermissionQuery(
    val userId: String,
    val permission: Permission
)

class CheckUserPermissionUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(query: CheckPermissionQuery): Result<Boolean> = runCatching {
        val userId = UserId(query.userId)
        val user = userRepository.findById(userId) ?: return@runCatching false
        user.hasPermission(query.permission)
    }
}

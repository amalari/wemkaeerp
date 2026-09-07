package com.eventverse.app.domain.tenant

data class RegisterTenantCommand(
    val id: String,
    val slug: String,
    val name: String,
    val tier: SubscriptionTier = SubscriptionTier.PRO
)

class RegisterTenantUseCase(
    private val tenantRepository: TenantRepository
) {
    suspend operator fun invoke(command: RegisterTenantCommand): Result<Tenant> = runCatching {
        val tenantId = TenantId(command.id)
        val tenantSlug = TenantSlug(command.slug)
        val tenantName = TenantName(command.name)

        if (tenantRepository.existsBySlug(tenantSlug)) {
            error("Subdomain '${tenantSlug.value}' is already taken")
        }

        val existingTenant = tenantRepository.findById(tenantId)
        if (existingTenant != null) {
            error("Tenant with ID '${tenantId.value}' already exists")
        }

        val newTenant = Tenant(
            id = tenantId,
            slug = tenantSlug,
            name = tenantName,
            status = TenantStatus.TRIAL,
            tier = command.tier,
            activeMachineCount = 0
        )

        tenantRepository.save(newTenant).getOrThrow()
    }
}

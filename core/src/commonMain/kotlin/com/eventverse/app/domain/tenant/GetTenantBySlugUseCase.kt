package com.eventverse.app.domain.tenant

data class GetTenantBySlugQuery(
    val slug: String
)

class GetTenantBySlugUseCase(
    private val tenantRepository: TenantRepository
) {
    suspend operator fun invoke(query: GetTenantBySlugQuery): Result<Tenant> = runCatching {
        val tenantSlug = TenantSlug(query.slug)
        tenantRepository.findBySlug(tenantSlug)
            ?: error("Tenant with subdomain '${tenantSlug.value}' was not found")
    }
}

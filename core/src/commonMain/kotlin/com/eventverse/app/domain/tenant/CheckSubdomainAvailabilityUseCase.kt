package com.eventverse.app.domain.tenant

data class CheckSubdomainQuery(
    val slug: String
)

data class SubdomainAvailabilityResult(
    val slug: String,
    val isAvailable: Boolean,
    val reason: String? = null
)

class CheckSubdomainAvailabilityUseCase(
    private val tenantRepository: TenantRepository
) {
    suspend operator fun invoke(query: CheckSubdomainQuery): Result<SubdomainAvailabilityResult> = runCatching {
        val trimmed = query.slug.trim().lowercase()

        val slug = try {
            TenantSlug(trimmed)
        } catch (e: IllegalArgumentException) {
            return@runCatching SubdomainAvailabilityResult(
                slug = trimmed,
                isAvailable = false,
                reason = e.message ?: "Invalid subdomain format"
            )
        }

        val exists = tenantRepository.existsBySlug(slug)
        if (exists) {
            SubdomainAvailabilityResult(
                slug = slug.value,
                isAvailable = false,
                reason = "Subdomain '${slug.value}' is already in use"
            )
        } else {
            SubdomainAvailabilityResult(
                slug = slug.value,
                isAvailable = true,
                reason = null
            )
        }
    }
}

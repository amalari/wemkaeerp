package com.eventverse.app.domain.tenant

import com.eventverse.app.domain.auth.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FakeTenantRepository : TenantRepository {
    private val tenants = mutableMapOf<TenantId, Tenant>()

    override suspend fun findById(id: TenantId): Tenant? = tenants[id]

    override suspend fun findBySlug(slug: TenantSlug): Tenant? =
        tenants.values.find { it.slug == slug }

    override suspend fun save(tenant: Tenant): Result<Tenant> {
        tenants[tenant.id] = tenant
        return Result.success(tenant)
    }

    override suspend fun existsBySlug(slug: TenantSlug): Boolean =
        tenants.values.any { it.slug == slug }

    override suspend fun findAll(): List<Tenant> = tenants.values.toList()
}

class FakeUserRepository : UserRepository {
    private val users = mutableMapOf<UserId, User>()

    override suspend fun findById(id: UserId): User? = users[id]

    override suspend fun findByUsername(tenantId: TenantId?, username: Username): User? =
        users.values.find { it.tenantId == tenantId && it.username == username }

    override suspend fun findByEmail(email: EmailAddress): User? =
        users.values.find { it.email == email }

    override suspend fun save(user: User): Result<User> {
        users[user.id] = user
        return Result.success(user)
    }

    override suspend fun findAllByTenant(tenantId: TenantId): List<User> =
        users.values.filter { it.tenantId == tenantId }
}

class TenantUseCaseTest {

    private lateinit var tenantRepository: FakeTenantRepository
    private lateinit var registerTenantUseCase: RegisterTenantUseCase
    private lateinit var checkSubdomainUseCase: CheckSubdomainAvailabilityUseCase

    @BeforeTest
    fun setup() {
        tenantRepository = FakeTenantRepository()
        registerTenantUseCase = RegisterTenantUseCase(tenantRepository)
        checkSubdomainUseCase = CheckSubdomainAvailabilityUseCase(tenantRepository)
    }

    @Test
    fun register_new_tenant_should_succeed_and_persist() = runTest {
        val command = RegisterTenantCommand(
            id = "ten-001",
            slug = "konveksi-maju",
            name = "Konveksi Maju Jaya",
            tier = SubscriptionTier.PRO
        )

        val result = registerTenantUseCase(command)
        assertTrue(result.isSuccess)

        val tenant = result.getOrThrow()
        assertEquals("konveksi-maju", tenant.slug.value)
        assertEquals(TenantStatus.TRIAL, tenant.status)
        assertEquals(SubscriptionTier.PRO, tenant.tier)

        // Verify persistence
        val fetched = tenantRepository.findBySlug(TenantSlug("konveksi-maju"))
        assertNotNull(fetched)
        assertEquals(tenant.id, fetched.id)
    }

    @Test
    fun register_tenant_with_existing_slug_should_fail() = runTest {
        val command1 = RegisterTenantCommand(
            id = "ten-001",
            slug = "konveksi-maju",
            name = "Konveksi Maju Jaya"
        )
        registerTenantUseCase(command1).getOrThrow()

        val command2 = RegisterTenantCommand(
            id = "ten-002",
            slug = "konveksi-maju",
            name = "Pabrik Maju Lainnya"
        )

        val result = registerTenantUseCase(command2)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("already taken") == true)
    }

    @Test
    fun check_subdomain_availability_for_free_and_taken_slug() = runTest {
        // Free slug
        val freeResult = checkSubdomainUseCase(CheckSubdomainQuery("garment-hebat")).getOrThrow()
        assertTrue(freeResult.isAvailable)

        // Register one
        registerTenantUseCase(RegisterTenantCommand("ten-1", "garment-hebat", "Garment Hebat"))

        // Now taken
        val takenResult = checkSubdomainUseCase(CheckSubdomainQuery("garment-hebat")).getOrThrow()
        assertFalse(takenResult.isAvailable)
        assertTrue(takenResult.reason?.contains("already in use") == true)

        // Invalid format
        val invalidResult = checkSubdomainUseCase(CheckSubdomainQuery("GARMENT HEBAT")).getOrThrow()
        assertFalse(invalidResult.isAvailable)
    }
}

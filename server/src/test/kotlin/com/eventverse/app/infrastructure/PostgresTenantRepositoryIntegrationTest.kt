package com.eventverse.app.infrastructure

import com.eventverse.app.domain.auth.*
import com.eventverse.app.domain.tenant.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PostgresTenantRepositoryIntegrationTest {

    private lateinit var tenantRepo: PostgresTenantRepository
    private lateinit var userRepo: PostgresUserRepository

    @BeforeTest
    fun setup() {
        // Initializes connection to local docker-compose PostgreSQL and runs Flyway migrations
        DatabaseFactory.init()
        tenantRepo = PostgresTenantRepository()
        userRepo = PostgresUserRepository()
    }

    @Test
    fun real_postgres_tenant_crud_lifecycle() = runBlocking {
        val suffix = kotlin.math.abs(System.currentTimeMillis() % 100000).toString()
        val uniqueSlug = "factory-$suffix"
        val tenant = Tenant(
            id = TenantId("ten-$uniqueSlug"),
            slug = TenantSlug(uniqueSlug),
            name = TenantName("PT Pabrik Uji Coba"),
            status = TenantStatus.TRIAL,
            tier = SubscriptionTier.PRO,
            activeMachineCount = 5
        )

        // 1. Save Tenant
        val saveResult = tenantRepo.save(tenant)
        assertTrue(saveResult.isSuccess)

        // 2. Fetch by Slug
        val fetched = tenantRepo.findBySlug(TenantSlug(uniqueSlug))
        assertNotNull(fetched)
        assertEquals(tenant.id, fetched.id)
        assertEquals(5, fetched.activeMachineCount)
        assertEquals(SubscriptionTier.PRO, fetched.tier)

        // 3. Exists Check
        assertTrue(tenantRepo.existsBySlug(TenantSlug(uniqueSlug)))

        // 4. Create User for this Tenant
        val user = User(
            id = UserId("usr-$uniqueSlug"),
            tenantId = tenant.id,
            username = Username("admin_$suffix"),
            email = EmailAddress("admin@$uniqueSlug.com"),
            role = Role.TENANT_ADMIN
        )

        val userSaveResult = userRepo.save(user)
        assertTrue(userSaveResult.isSuccess)

        // 5. Query user by username & tenant
        val fetchedUser = userRepo.findByUsername(tenant.id, Username("admin_$suffix"))
        assertNotNull(fetchedUser)
        assertEquals(user.id, fetchedUser.id)
        assertEquals(Role.TENANT_ADMIN, fetchedUser.role)

        // 6. List users by tenant
        val tenantUsers = userRepo.findAllByTenant(tenant.id)
        assertTrue(tenantUsers.any { it.id == user.id })
    }
}

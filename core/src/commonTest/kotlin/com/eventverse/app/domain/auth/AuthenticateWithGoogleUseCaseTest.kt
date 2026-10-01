package com.eventverse.app.domain.auth

import com.eventverse.app.domain.tenant.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AuthenticateWithGoogleUseCaseTest {

    private lateinit var tenantRepository: FakeTenantRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var authenticateWithGoogleUseCase: AuthenticateWithGoogleUseCase

    @BeforeTest
    fun setup() = runTest {
        tenantRepository = FakeTenantRepository()
        userRepository = FakeUserRepository()
        authenticateWithGoogleUseCase = AuthenticateWithGoogleUseCase(userRepository, tenantRepository)

        // Seed an active tenant
        val tenant = Tenant(
            id = TenantId("ten-demo"),
            slug = TenantSlug("berkah-konveksi"),
            name = TenantName("Konveksi Berkah"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.PRO
        )
        tenantRepository.save(tenant)

        // Seed a registered user in this tenant
        val user = User(
            id = UserId("usr-owner-01"),
            tenantId = TenantId("ten-demo"),
            username = Username("owner_berkah"),
            email = EmailAddress("owner@berkah.com"),
            role = Role.TENANT_ADMIN,
            isActive = true
        )
        userRepository.save(user)
    }

    @Test
    fun authenticate_with_valid_registered_google_user_should_succeed() = runTest {
        val command = AuthenticateWithGoogleCommand(
            profile = GoogleUserProfile(
                email = "owner@berkah.com",
                name = "Owner Berkah",
                googleSubjectId = "google-sub-123456",
                emailVerified = true
            ),
            tenantSlug = "berkah-konveksi"
        )

        val result = authenticateWithGoogleUseCase(command)
        assertTrue(result.isSuccess)

        val user = result.getOrThrow()
        assertEquals("usr-owner-01", user.id.value)
        assertEquals(Role.TENANT_ADMIN, user.role)
        assertTrue(user.hasPermission(Permission.MANAGE_TENANT))
    }

    @Test
    fun authenticate_with_unregistered_email_in_tenant_should_fail() = runTest {
        val command = AuthenticateWithGoogleCommand(
            profile = GoogleUserProfile(
                email = "stranger@gmail.com",
                name = "Stranger",
                googleSubjectId = "google-sub-99999",
                emailVerified = true
            ),
            tenantSlug = "berkah-konveksi"
        )

        val result = authenticateWithGoogleUseCase(command)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(msg.contains("belum terdaftar"), "Expected unregistered message, got: $msg")
    }

    @Test
    fun authenticate_with_nonexistent_tenant_slug_should_fail() = runTest {
        val command = AuthenticateWithGoogleCommand(
            profile = GoogleUserProfile(
                email = "owner@berkah.com",
                name = "Owner Berkah",
                googleSubjectId = "google-sub-123456",
                emailVerified = true
            ),
            tenantSlug = "unknown-factory"
        )

        val result = authenticateWithGoogleUseCase(command)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(msg.contains("tidak ditemukan"), "Expected not found message, got: $msg")
    }

    @Test
    fun authenticate_with_unverified_google_email_should_fail() = runTest {
        val command = AuthenticateWithGoogleCommand(
            profile = GoogleUserProfile(
                email = "owner@berkah.com",
                name = "Owner Berkah",
                googleSubjectId = "google-sub-123456",
                emailVerified = false
            ),
            tenantSlug = "berkah-konveksi"
        )

        val result = authenticateWithGoogleUseCase(command)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(msg.contains("belum terverifikasi"), "Expected unverified message, got: $msg")
    }

    // --- Login di permukaan platform (app.<base>): tenant dari akun, bukan dari kolom slug ---

    private suspend fun seedBordirTrialOwner(status: TenantStatus = TenantStatus.TRIAL) {
        tenantRepository.save(
            Tenant(
                id = TenantId("ten-bordir"),
                slug = TenantSlug("bordir-uji"),
                name = TenantName("Bordir Uji"),
                status = status,
                tier = SubscriptionTier.PRO
            )
        )
        userRepository.save(
            User(
                id = UserId("usr-bordir-owner"),
                tenantId = TenantId("ten-bordir"),
                username = Username("owner_bordir"),
                email = EmailAddress("owner@bordir.id"),
                role = Role.TENANT_ADMIN,
                isActive = true
            )
        )
    }

    private fun platformCommand(email: String) = AuthenticateWithGoogleCommand(
        profile = GoogleUserProfile(email, "Owner", "google-sub-bordir", emailVerified = true),
        tenantSlug = null
    )

    @Test
    fun authenticate_on_platform_without_slug_should_resolve_trial_tenant_from_account() = runTest {
        seedBordirTrialOwner()

        val user = authenticateWithGoogleUseCase(platformCommand("owner@bordir.id")).getOrThrow()

        assertEquals("usr-bordir-owner", user.id.value)
        assertEquals("ten-bordir", user.tenantId?.value)
    }

    @Test
    fun authenticate_on_platform_with_unregistered_email_should_fail() = runTest {
        assertTrue(authenticateWithGoogleUseCase(platformCommand("stranger@gmail.com")).isFailure)
    }

    @Test
    fun authenticate_on_platform_for_suspended_tenant_should_fail() = runTest {
        seedBordirTrialOwner(status = TenantStatus.SUSPENDED)

        assertTrue(authenticateWithGoogleUseCase(platformCommand("owner@bordir.id")).isFailure)
    }

    @Test
    fun authenticate_with_slug_of_trial_tenant_should_succeed() = runTest {
        seedBordirTrialOwner()

        val command = platformCommand("owner@bordir.id").copy(tenantSlug = "bordir-uji")
        assertTrue(authenticateWithGoogleUseCase(command).isSuccess)
    }
}

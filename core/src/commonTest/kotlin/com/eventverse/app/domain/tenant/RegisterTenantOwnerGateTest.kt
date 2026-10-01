package com.eventverse.app.domain.tenant

import com.eventverse.app.domain.auth.EmailAddress
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.User
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.Username
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gerbang satu-owner-per-email (PLAN-builder-console F3): satu email hanya boleh memiliki satu project.
 * Kolaborator tidak terkena — yang dibatasi kepemilikan (`TENANT_ADMIN`), bukan keanggotaan.
 */
class RegisterTenantOwnerGateTest {

    private val tenants = FakeTenantRepository()
    private val users = FakeUserRepository()
    private val register = RegisterTenantUseCase(tenants, users)

    private suspend fun seedOwner(email: String, tenantId: String) {
        users.save(
            User(
                id = UserId("usr-owner"),
                tenantId = TenantId(tenantId),
                username = Username("owner"),
                email = EmailAddress(email),
                role = Role.TENANT_ADMIN
            )
        ).getOrThrow()
    }

    @Test
    fun register_withEmailThatAlreadyOwnsAProject_isRejected() = runTest {
        seedOwner("pemilik@klien.id", tenantId = "ten-pertama")
        val second = register(RegisterTenantCommand("ten-kedua", "kedua", "Project Kedua", ownerEmail = "pemilik@klien.id"))
        assertTrue(second.isFailure, "email pemilik tenant lain tidak boleh mendaftar project kedua")
        assertTrue(tenants.findById(TenantId("ten-kedua")) == null, "tenant tidak boleh tertinggal setelah gerbang menolak")
    }

    @Test
    fun register_withFreshEmail_succeeds() = runTest {
        seedOwner("pemilik@klien.id", tenantId = "ten-pertama")
        val fresh = register(RegisterTenantCommand("ten-kedua", "kedua", "Project Kedua", ownerEmail = "email-baru@klien.id"))
        assertTrue(fresh.isSuccess)
    }

    @Test
    fun register_withoutOwnerEmail_keepsInvitationFlowWorking() = runTest {
        seedOwner("pemilik@klien.id", tenantId = "ten-pertama")
        val invited = register(RegisterTenantCommand("ten-undangan", "undangan", "Project Undangan"))
        assertTrue(invited.isSuccess, "mode undangan M0–M1 tanpa ownerEmail tidak terkena gerbang")
    }

    @Test
    fun platformSuperadminEmail_isNotTreatedAsProjectOwner() = runTest {
        users.save(
            User(
                id = UserId("usr-superadmin"),
                tenantId = null,
                username = Username("superadmin"),
                email = EmailAddress("superadmin@wemakeerp.com"),
                role = Role.PLATFORM_SUPERADMIN
            )
        ).getOrThrow()
        val result = register(RegisterTenantCommand("ten-baru", "baru", "Project Baru", ownerEmail = "superadmin@wemakeerp.com"))
        assertTrue(result.isSuccess, "akun platform bukan pemilik project — tidak boleh memblokir pendaftaran")
    }

    @Test
    fun register_leavesTrialClockUnset_builderIsFreeUntilGoLive() = runTest {
        // V89: builder gratis tanpa batas — jam trial baru dimulai saat deploy pertama sukses.
        val tenant = register(RegisterTenantCommand("ten-trial", "trial", "Project Trial")).getOrThrow()

        assertEquals(TenantStatus.TRIAL, tenant.status)
        assertEquals(null, tenant.trialEndsAt, "registrasi tidak memulai jam trial")
    }
}

package com.eventverse.app.domain.auth

import com.eventverse.app.domain.tenant.FakeUserRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class UserUseCaseTest {

    private lateinit var userRepository: FakeUserRepository
    private lateinit var registerUserUseCase: RegisterUserUseCase
    private lateinit var checkUserPermissionUseCase: CheckUserPermissionUseCase

    @BeforeTest
    fun setup() {
        userRepository = FakeUserRepository()
        registerUserUseCase = RegisterUserUseCase(userRepository)
        checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository)
    }

    @Test
    fun register_new_tenant_user_should_succeed() = runTest {
        val command = RegisterUserCommand(
            id = "usr-01",
            tenantId = "ten-01",
            username = "ppic_supervisor",
            email = "ppic@wemade.id",
            role = Role.PPIC_SUPERVISOR
        )

        val result = registerUserUseCase(command)
        assertTrue(result.isSuccess)

        val user = result.getOrThrow()
        assertEquals("ppic_supervisor", user.username.value)
        assertEquals(Role.PPIC_SUPERVISOR, user.role)

        val permissionCheck = checkUserPermissionUseCase(
            CheckPermissionQuery("usr-01", Permission.APPROVE_SPK)
        )
        assertTrue(permissionCheck.getOrThrow())
    }

    @Test
    fun register_user_with_duplicate_username_in_same_tenant_should_fail() = runTest {
        val command1 = RegisterUserCommand(
            id = "usr-01",
            tenantId = "ten-01",
            username = "operator_1",
            email = "op1@wemade.id",
            role = Role.OPERATOR
        )
        registerUserUseCase(command1).getOrThrow()

        val command2 = RegisterUserCommand(
            id = "usr-02",
            tenantId = "ten-01",
            username = "operator_1",
            email = "op1_duplicate@wemade.id",
            role = Role.OPERATOR
        )

        val result = registerUserUseCase(command2)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("already in use") == true)
    }

    @Test
    fun check_permission_for_unauthorized_action_should_return_false() = runTest {
        val command = RegisterUserCommand(
            id = "usr-op",
            tenantId = "ten-01",
            username = "operator_joko",
            email = "joko@wemade.id",
            role = Role.OPERATOR
        )
        registerUserUseCase(command).getOrThrow()

        val check = checkUserPermissionUseCase(
            CheckPermissionQuery("usr-op", Permission.MANAGE_TENANT)
        )
        assertFalse(check.getOrThrow())
    }
}

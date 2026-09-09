package com.eventverse.app.domain.rbac

import com.eventverse.app.domain.rbac.usecases.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FakeRoleRepository : RoleRepository {
    val storage = mutableMapOf<String, CustomRole>()
    val userCountMap = mutableMapOf<String, Int>()

    override suspend fun findById(tenantId: TenantId, id: RoleId): CustomRole? =
        storage[key(tenantId, id)]

    override suspend fun findAllByTenant(tenantId: TenantId): List<CustomRole> =
        storage.values.filter { it.tenantId == tenantId }

    override suspend fun save(role: CustomRole): Result<CustomRole> {
        val tId = role.tenantId ?: error("TenantId required")
        storage[key(tId, role.id)] = role
        return Result.success(role)
    }

    override suspend fun delete(tenantId: TenantId, id: RoleId): Result<Unit> {
        storage.remove(key(tenantId, id))
        return Result.success(Unit)
    }

    override suspend fun restoreDefaultPresets(tenantId: TenantId): Result<List<CustomRole>> {
        val presets = CustomRole.createFactoryPresets(tenantId)
        presets.forEach { save(it) }
        return Result.success(presets)
    }

    override suspend fun countUsersWithRole(tenantId: TenantId, id: RoleId): Int =
        userCountMap[key(tenantId, id)] ?: 0

    private fun key(tenantId: TenantId, id: RoleId) = "${tenantId.value}:${id.value}"
}

class RoleUseCaseTest {

    private val tenantId = TenantId("ten-test-001")
    private lateinit var repo: FakeRoleRepository
    private lateinit var getRolesUseCase: GetRolesUseCase
    private lateinit var createRoleUseCase: CreateRoleUseCase
    private lateinit var updateRoleUseCase: UpdateRoleUseCase
    private lateinit var deleteRoleUseCase: DeleteRoleUseCase
    private lateinit var restoreDefaultRolesUseCase: RestoreDefaultRolesUseCase

    @BeforeTest
    fun setup() {
        repo = FakeRoleRepository()
        getRolesUseCase = GetRolesUseCase(repo)
        createRoleUseCase = CreateRoleUseCase(repo)
        updateRoleUseCase = UpdateRoleUseCase(repo)
        deleteRoleUseCase = DeleteRoleUseCase(repo)
        restoreDefaultRolesUseCase = RestoreDefaultRolesUseCase(repo)
    }

    @Test
    fun createRole_withValidData_shouldSucceed() = runBlocking {
        val cmd = CreateRoleCommand(
            tenantId = tenantId,
            name = "Mandor Bordir Komputer",
            description = "Koordinator mesin bordir",
            modulePermissions = mapOf(
                BusinessModule.OPERATOR_EXEC to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.SUBORDINATE_DATA)
            )
        )

        val result = createRoleUseCase(cmd)
        assertTrue(result.isSuccess)
        val role = result.getOrThrow()
        assertEquals("Mandor Bordir Komputer", role.name)
        assertFalse(role.isSystemDefault)
        assertEquals(AccessLevel.OPERATE, role.getAccess(BusinessModule.OPERATOR_EXEC).level)
    }

    @Test
    fun createRole_withBlankName_shouldFail() = runBlocking {
        val cmd = CreateRoleCommand(
            tenantId = tenantId,
            name = "   "
        )
        val result = createRoleUseCase(cmd)
        assertTrue(result.isFailure)
    }

    @Test
    fun updateRole_metadataAndPermissions_shouldUpdate() = runBlocking {
        val created = createRoleUseCase(CreateRoleCommand(tenantId, "Koordinator Packing")).getOrThrow()

        val updateResult = updateRoleUseCase(
            UpdateRoleCommand(
                tenantId = tenantId,
                roleId = created.id,
                name = "Koordinator Packing & Ekspedisi",
                description = "Updated description",
                modulePermissions = mapOf(
                    BusinessModule.FULFILLMENT to ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA)
                )
            )
        )

        assertTrue(updateResult.isSuccess)
        val updated = updateResult.getOrThrow()
        assertEquals("Koordinator Packing & Ekspedisi", updated.name)
        assertEquals("Updated description", updated.description)
        assertEquals(AccessLevel.MANAGE, updated.getAccess(BusinessModule.FULFILLMENT).level)
    }

    @Test
    fun deleteRole_systemDefaultRole_shouldFail() = runBlocking {
        restoreDefaultRolesUseCase(tenantId)
        val ownerRole = repo.findAllByTenant(tenantId).find { it.isSystemDefault }
        assertNotNull(ownerRole)

        val result = deleteRoleUseCase(tenantId, ownerRole.id)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("bawaan sistem") == true)
    }

    @Test
    fun deleteRole_customRoleWithAssignedUsers_shouldFail() = runBlocking {
        val created = createRoleUseCase(CreateRoleCommand(tenantId, "Staf Magang")).getOrThrow()
        repo.userCountMap["${tenantId.value}:${created.id.value}"] = 3

        val result = deleteRoleUseCase(tenantId, created.id)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("digunakan oleh 3 pengguna") == true)
    }

    @Test
    fun deleteRole_customRoleWithoutUsers_shouldSucceed() = runBlocking {
        val created = createRoleUseCase(CreateRoleCommand(tenantId, "Staf Magang")).getOrThrow()
        val result = deleteRoleUseCase(tenantId, created.id)
        assertTrue(result.isSuccess)
        assertNull(repo.findById(tenantId, created.id))
    }
}

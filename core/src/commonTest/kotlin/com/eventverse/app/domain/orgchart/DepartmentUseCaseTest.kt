package com.eventverse.app.domain.orgchart

import com.eventverse.app.domain.orgchart.usecases.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FakeDepartmentRepository : DepartmentRepository {
    val storage = mutableMapOf<String, Department>()

    override suspend fun findById(tenantId: TenantId, id: DepartmentId): Department? =
        storage[key(tenantId, id.value)]

    override suspend fun findByCode(tenantId: TenantId, code: String): Department? =
        storage.values.find { it.tenantId == tenantId && it.code == code }

    override suspend fun findAllByTenant(tenantId: TenantId): List<Department> =
        storage.values.filter { it.tenantId == tenantId && !it.isArchived }

    override suspend fun findAllArchived(tenantId: TenantId): List<Department> =
        storage.values.filter { it.tenantId == tenantId && it.isArchived }

    override suspend fun save(tenantId: TenantId, department: Department): Result<Department> {
        val deptWithTenant = if (department.tenantId == null) department.copy(tenantId = tenantId) else department
        storage[key(tenantId, deptWithTenant.id.value)] = deptWithTenant
        return Result.success(deptWithTenant)
    }

    override suspend fun archive(tenantId: TenantId, id: DepartmentId): Result<Unit> {
        val existing = storage[key(tenantId, id.value)] ?: return Result.failure(IllegalStateException("not found"))
        storage[key(tenantId, id.value)] = existing.copy(archivedAt = "archived")
        return Result.success(Unit)
    }

    override suspend fun restore(tenantId: TenantId, id: DepartmentId): Result<Unit> {
        val existing = storage[key(tenantId, id.value)] ?: return Result.failure(IllegalStateException("not found"))
        storage[key(tenantId, id.value)] = existing.copy(archivedAt = null)
        return Result.success(Unit)
    }

    override suspend fun delete(tenantId: TenantId, id: DepartmentId): Result<Unit> {
        storage.remove(key(tenantId, id.value))
        return Result.success(Unit)
    }

    override suspend fun restoreDefaultPresets(tenantId: TenantId): Result<List<Department>> {
        val presets = Department.defaultPresets().map { it.copy(tenantId = tenantId) }
        presets.forEach { save(tenantId, it) }
        return Result.success(presets)
    }

    private fun key(tenantId: TenantId, id: String) = "${tenantId.value}:$id"
}

class DepartmentUseCaseTest {

    private val tenantId = TenantId("ten-test-001")
    private lateinit var deptRepo: FakeDepartmentRepository
    private lateinit var getDepartmentsUseCase: GetDepartmentsUseCase
    private lateinit var createDepartmentUseCase: CreateDepartmentUseCase
    private lateinit var updateDepartmentUseCase: UpdateDepartmentUseCase
    private lateinit var archiveDepartmentUseCase: ArchiveDepartmentUseCase
    private lateinit var restoreDefaultDepartmentsUseCase: RestoreDefaultDepartmentsUseCase

    @BeforeTest
    fun setup() {
        deptRepo = FakeDepartmentRepository()
        getDepartmentsUseCase = GetDepartmentsUseCase(deptRepo)
        createDepartmentUseCase = CreateDepartmentUseCase(deptRepo)
        updateDepartmentUseCase = UpdateDepartmentUseCase(deptRepo)
        archiveDepartmentUseCase = ArchiveDepartmentUseCase(deptRepo)
        restoreDefaultDepartmentsUseCase = RestoreDefaultDepartmentsUseCase(deptRepo)
    }

    @Test
    fun createDepartment_validData_shouldCreateAndPersist() = runBlocking {
        val cmd = CreateDepartmentCommand(
            tenantId = tenantId,
            displayName = "Bordir & Sablon Printing",
            shortName = "Bordir",
            colorHex = 0xFFE11D48
        )

        val result = createDepartmentUseCase(cmd)
        assertTrue(result.isSuccess)
        val dept = result.getOrThrow()
        assertEquals("Bordir & Sablon Printing", dept.displayName)
        assertEquals("Bordir", dept.shortName)
        assertEquals("bordir", dept.code)
        assertTrue(dept.isCustom)
        assertEquals(tenantId, dept.tenantId)
    }

    @Test
    fun updateDepartment_shouldUpdateFields() = runBlocking {
        val created = createDepartmentUseCase(
            CreateDepartmentCommand(tenantId, "Pola Cutting", "Pola", 0xFF0891B2)
        ).getOrThrow()

        val updateResult = updateDepartmentUseCase(
            UpdateDepartmentCommand(
                tenantId = tenantId,
                id = created.id,
                displayName = "Pola, Cutting & Marker",
                shortName = "Cutting"
            )
        )

        assertTrue(updateResult.isSuccess)
        val updated = updateResult.getOrThrow()
        assertEquals("Pola, Cutting & Marker", updated.displayName)
        assertEquals("Cutting", updated.shortName)
    }

    @Test
    fun restoreDefaultPresets_shouldLoad5StandardGarmentDepts() = runBlocking {
        val result = restoreDefaultDepartmentsUseCase(tenantId)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(5, list.size)
        assertTrue(list.any { it.code == "sales" })
        assertTrue(list.any { it.code == "production_ppic" })
        assertTrue(list.any { it.code == "warehouse" })
        assertTrue(list.any { it.code == "qc" })
        assertTrue(list.any { it.code == "finance" || it.code == "finance_executive" })
    }

    @Test
    fun archiveDepartment_existingDept_shouldMarkArchived() = runBlocking {
        val created = createDepartmentUseCase(
            CreateDepartmentCommand(tenantId, "Sample Divisi", "Sample", 0xFF123456)
        ).getOrThrow()

        val archiveResult = archiveDepartmentUseCase(tenantId, created.id)
        assertTrue(archiveResult.isSuccess)
        assertTrue(deptRepo.findById(tenantId, created.id)!!.isArchived)
    }
}

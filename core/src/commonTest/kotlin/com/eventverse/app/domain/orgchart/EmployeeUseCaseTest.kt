package com.eventverse.app.domain.orgchart

import com.eventverse.app.domain.orgchart.usecases.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FakeEmployeeRepository : EmployeeRepository {
    val storage = mutableMapOf<String, OrgNode>()

    override suspend fun findById(tenantId: TenantId, id: OrgNodeId): OrgNode? =
        storage[key(tenantId, id.value)]

    override suspend fun findByEmail(tenantId: TenantId, email: String): OrgNode? =
        storage.values.find { it.tenantId == tenantId && it.email.equals(email, ignoreCase = true) }

    override suspend fun findAllByTenant(tenantId: TenantId): List<OrgNode> =
        storage.values.filter { it.tenantId == tenantId }

    override suspend fun findByDepartment(tenantId: TenantId, departmentId: DepartmentId): List<OrgNode> =
        storage.values.filter { it.tenantId == tenantId && it.department?.id == departmentId && !it.isArchived }

    override suspend fun findAllArchived(tenantId: TenantId): List<OrgNode> =
        storage.values.filter { it.tenantId == tenantId && it.isArchived }

    override suspend fun save(tenantId: TenantId, employee: OrgNode): Result<OrgNode> {
        val empWithTenant = if (employee.tenantId == null) employee.copy(tenantId = tenantId) else employee
        storage[key(tenantId, empWithTenant.id.value)] = empWithTenant
        return Result.success(empWithTenant)
    }

    override suspend fun saveAll(tenantId: TenantId, employees: List<OrgNode>): Result<List<OrgNode>> {
        val list = employees.map { emp ->
            val empWithTenant = if (emp.tenantId == null) emp.copy(tenantId = tenantId) else emp
            storage[key(tenantId, empWithTenant.id.value)] = empWithTenant
            empWithTenant
        }
        return Result.success(list)
    }

    override suspend fun archive(tenantId: TenantId, id: OrgNodeId): Result<Unit> {
        val existing = storage[key(tenantId, id.value)] ?: return Result.failure(IllegalStateException("not found"))
        storage[key(tenantId, id.value)] = existing.copy(archivedAt = "archived")
        return Result.success(Unit)
    }

    override suspend fun restore(tenantId: TenantId, id: OrgNodeId): Result<Unit> {
        val existing = storage[key(tenantId, id.value)] ?: return Result.failure(IllegalStateException("not found"))
        storage[key(tenantId, id.value)] = existing.copy(archivedAt = null)
        return Result.success(Unit)
    }

    override suspend fun delete(tenantId: TenantId, id: OrgNodeId): Result<Unit> {
        storage.remove(key(tenantId, id.value))
        return Result.success(Unit)
    }

    override suspend fun restoreDefaultPresets(
        tenantId: TenantId,
        departments: List<Department>
    ): Result<List<OrgNode>> {
        val deptMap = departments.associateBy { it.code }
        val samples = OrgNode.createSampleEmployees().map { emp ->
            val matchedDept = emp.department?.let { deptMap[it.code] } ?: emp.department
            emp.copy(tenantId = tenantId, department = matchedDept)
        }
        samples.forEach { save(tenantId, it) }
        return Result.success(samples)
    }

    private fun key(tenantId: TenantId, id: String) = "${tenantId.value}:$id"
}

class EmployeeUseCaseTest {

    private val tenantId = TenantId("ten-test-001")
    private lateinit var deptRepo: FakeDepartmentRepository
    private lateinit var empRepo: FakeEmployeeRepository
    private lateinit var getEmployeesUseCase: GetEmployeesUseCase
    private lateinit var getTShapeUseCase: GetEmployeeTShapeHierarchyUseCase
    private lateinit var createEmployeeUseCase: CreateEmployeeUseCase
    private lateinit var updateEmployeeUseCase: UpdateEmployeeUseCase
    private lateinit var archiveEmployeeUseCase: ArchiveEmployeeUseCase
    private lateinit var restoreDefaultEmployeesUseCase: RestoreDefaultEmployeesUseCase

    @BeforeTest
    fun setup() = runBlocking {
        deptRepo = FakeDepartmentRepository()
        empRepo = FakeEmployeeRepository()

        deptRepo.restoreDefaultPresets(tenantId)

        getEmployeesUseCase = GetEmployeesUseCase(empRepo)
        getTShapeUseCase = GetEmployeeTShapeHierarchyUseCase(empRepo)
        createEmployeeUseCase = CreateEmployeeUseCase(empRepo, deptRepo)
        updateEmployeeUseCase = UpdateEmployeeUseCase(empRepo, deptRepo)
        archiveEmployeeUseCase = ArchiveEmployeeUseCase(empRepo)
        restoreDefaultEmployeesUseCase = RestoreDefaultEmployeesUseCase(empRepo, deptRepo)
    }

    @Test
    fun createEmployee_staff_shouldPersistSuccessfully() = runBlocking {
        val salesDept = deptRepo.findByCode(tenantId, "sales")!!
        val cmd = CreateEmployeeCommand(
            tenantId = tenantId,
            name = "Rahmat Hidayat",
            email = "rahmat@wemade.id",
            departmentId = salesDept.id,
            level = HierarchyLevel.STAFF_OPERATOR,
            roleTitle = "Sales Lapangan",
            phone = "0812345678"
        )

        val result = createEmployeeUseCase(cmd)
        assertTrue(result.isSuccess)
        val emp = result.getOrThrow()
        assertEquals("Rahmat Hidayat", emp.name)
        assertEquals(salesDept.id, emp.department?.id)
        assertEquals(tenantId, emp.tenantId)
    }

    @Test
    fun createEmployee_newHeadOfDept_shouldDemoteOldHeadAndReassignSubordinates() = runBlocking {
        restoreDefaultEmployeesUseCase(tenantId)
        val salesDept = deptRepo.findByCode(tenantId, "sales")!!
        val oldHead = empRepo.findAllByTenant(tenantId).find {
            it.department?.id == salesDept.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
        }
        assertNotNull(oldHead)
        assertEquals("Budi Santoso", oldHead.name)

        // Add a new Head of Sales with DEMOTE_TO_STAFF succession
        val newHeadCmd = CreateEmployeeCommand(
            tenantId = tenantId,
            name = "Ahmad Maulana",
            email = "ahmad.maulana@wemade.id",
            departmentId = salesDept.id,
            level = HierarchyLevel.HEAD_OF_DEPARTMENT,
            roleTitle = "VP of Sales & Marketing",
            successionAction = HeadSuccessionAction.DEMOTE_TO_STAFF
        )

        val newHeadResult = createEmployeeUseCase(newHeadCmd)
        assertTrue(newHeadResult.isSuccess)
        val newHead = newHeadResult.getOrThrow()

        // Verify old head was demoted to staff and now reports to new head
        val updatedOldHead = empRepo.findById(tenantId, oldHead.id)
        assertNotNull(updatedOldHead)
        assertEquals(HierarchyLevel.STAFF_OPERATOR, updatedOldHead.level)
        assertEquals(newHead.id, updatedOldHead.reportsToId)

        // Verify sales staff now report to new head
        val staffList = empRepo.findByDepartment(tenantId, salesDept.id).filter {
            it.id != newHead.id && it.id != oldHead.id
        }
        staffList.forEach { staff ->
            assertEquals(newHead.id, staff.reportsToId)
        }
    }

    @Test
    fun archiveEmployee_shouldReassignSubordinatesToGrandparentSuperior() = runBlocking {
        restoreDefaultEmployeesUseCase(tenantId)
        val salesDept = deptRepo.findByCode(tenantId, "sales")!!
        val budiHead = empRepo.findAllByTenant(tenantId).find {
            it.department?.id == salesDept.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
        }!!
        val superiorOfBudi = budiHead.reportsToId

        // Subordinates of Budi
        val staffBefore = empRepo.findAllByTenant(tenantId).filter { it.reportsToId == budiHead.id }
        assertTrue(staffBefore.isNotEmpty())

        // Archive Budi
        val archiveResult = archiveEmployeeUseCase(tenantId, budiHead.id)
        assertTrue(archiveResult.isSuccess)
        assertTrue(empRepo.findById(tenantId, budiHead.id)!!.isArchived)

        // Check that subordinates now report to superior of Budi (Hendra / Executive)
        staffBefore.forEach { staff ->
            val updated = empRepo.findById(tenantId, staff.id)!!
            assertEquals(superiorOfBudi, updated.reportsToId)
        }
    }

    @Test
    fun getTShapeHierarchy_shouldResolveTreeAccurately() = runBlocking {
        restoreDefaultEmployeesUseCase(tenantId)
        val budiHead = empRepo.findAllByTenant(tenantId).find { it.name == "Budi Santoso" }!!

        val tShapeResult = getTShapeUseCase(tenantId, budiHead.id)
        assertTrue(tShapeResult.isSuccess)
        val tree = tShapeResult.getOrThrow()

        assertEquals("Budi Santoso", tree.focusNode.name)
        assertEquals("Bpk. Hendra Kusuma", tree.superior?.name)
        assertTrue(tree.peerHeads.isNotEmpty())
        assertTrue(tree.peerHeads.none { it.department?.id == budiHead.department?.id })
        assertTrue(tree.subordinates.isNotEmpty())
        tree.subordinates.forEach {
            assertEquals(budiHead.department?.id, it.department?.id)
        }
    }

    @Test
    fun createEmployee_withDuplicateEmailOfActiveEmployee_shouldThrowEmailConflictException() = runBlocking {
        restoreDefaultEmployeesUseCase(tenantId)
        val activeEmp = empRepo.findAllByTenant(tenantId).first { it.department != null }

        val result = createEmployeeUseCase(
            CreateEmployeeCommand(
                tenantId = tenantId,
                name = "Karyawan Baru",
                email = activeEmp.email,
                departmentId = activeEmp.department?.id,
                level = HierarchyLevel.STAFF_OPERATOR
            )
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertIs<EmailConflictException>(ex)
        assertEquals(activeEmp.email, ex.email)
        assertEquals(activeEmp.name, ex.existingEmployeeName)
        assertEquals(activeEmp.department?.displayName ?: "Direksi", ex.existingDepartmentName)
        assertFalse(ex.isArchived)
    }

    @Test
    fun createEmployee_withDuplicateEmailOfArchivedEmployee_shouldThrowEmailConflictExceptionWithArchivedTrue() = runBlocking {
        restoreDefaultEmployeesUseCase(tenantId)
        val emp = empRepo.findAllByTenant(tenantId).find { it.name.contains("Maya") }!!
        archiveEmployeeUseCase(tenantId, emp.id)

        val result = createEmployeeUseCase(
            CreateEmployeeCommand(
                tenantId = tenantId,
                name = "Maya Baru",
                email = emp.email,
                departmentId = emp.department?.id,
                level = HierarchyLevel.STAFF_OPERATOR
            )
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertIs<EmailConflictException>(ex)
        assertEquals(emp.email, ex.email)
        assertEquals(emp.name, ex.existingEmployeeName)
        assertTrue(ex.isArchived)
    }

    @Test
    fun updateEmployee_withDuplicateEmailOfAnotherEmployee_shouldThrowEmailConflictException() = runBlocking {
        restoreDefaultEmployeesUseCase(tenantId)
        val all = empRepo.findAllByTenant(tenantId)
        val emp1 = all[0]
        val emp2 = all[1]

        val result = updateEmployeeUseCase(
            UpdateEmployeeCommand(
                tenantId = tenantId,
                id = emp1.id,
                email = emp2.email
            )
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertIs<EmailConflictException>(ex)
        assertEquals(emp2.email, ex.email)
        assertEquals(emp2.name, ex.existingEmployeeName)
    }
}


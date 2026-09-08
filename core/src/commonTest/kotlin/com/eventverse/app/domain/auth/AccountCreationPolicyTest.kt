package com.eventverse.app.domain.auth

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import kotlin.test.*

class AccountCreationPolicyTest {

    private val salesHeadNode = OrgNode(
        id = OrgNodeId("emp-budi"),
        name = "Budi Santoso",
        email = "budi.sales@wemade.id",
        department = Department.SALES,
        level = HierarchyLevel.HEAD_OF_DEPARTMENT,
        roleTitle = "Head of Sales"
    )

    private val validSubordinateAccount = SimpleEmployeeAccount(
        email = EmailAddress("dimas@wemade.id"),
        name = "Dimas Pratama",
        phone = "08123456789",
        department = Department.SALES,
        level = HierarchyLevel.STAFF_OPERATOR,
        roleTitle = "Sales Eksekutif",
        reportsToId = salesHeadNode.id
    )

    @Test
    fun owner_and_tenant_admin_should_have_unrestricted_creation() {
        val resultOwner = AccountCreationPolicy.validateCreation(
            creatorRole = Role.TENANT_ADMIN,
            creatorNode = null,
            target = validSubordinateAccount
        )
        assertTrue(resultOwner.isSuccess)

        // Owner can also create a new Head of Department in any division
        val newHeadAccount = validSubordinateAccount.copy(
            department = Department.WAREHOUSE,
            level = HierarchyLevel.HEAD_OF_DEPARTMENT,
            roleTitle = "Kepala Gudang Baru"
        )
        val resultOwnerHead = AccountCreationPolicy.validateCreation(
            creatorRole = Role.TENANT_ADMIN,
            creatorNode = null,
            target = newHeadAccount
        )
        assertTrue(resultOwnerHead.isSuccess)
    }

    @Test
    fun department_head_should_succeed_creating_subordinate_in_same_department() {
        val result = AccountCreationPolicy.validateCreation(
            creatorRole = Role.SALES,
            creatorNode = salesHeadNode,
            target = validSubordinateAccount
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun department_head_should_fail_creating_account_for_different_department() {
        val crossDeptAccount = validSubordinateAccount.copy(
            department = Department.PRODUCTION_PPIC
        )
        val result = AccountCreationPolicy.validateCreation(
            creatorRole = Role.SALES,
            creatorNode = salesHeadNode,
            target = crossDeptAccount
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("hanya diizinkan membuat akun untuk divisinya sendiri") == true)
    }

    @Test
    fun department_head_should_fail_creating_another_head_or_executive() {
        val illegalLevelAccount = validSubordinateAccount.copy(
            level = HierarchyLevel.HEAD_OF_DEPARTMENT
        )
        val result = AccountCreationPolicy.validateCreation(
            creatorRole = Role.SALES,
            creatorNode = salesHeadNode,
            target = illegalLevelAccount
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("hanya diizinkan membuat akun untuk tingkat Staf Pelaksana") == true)
    }

    @Test
    fun department_head_should_fail_if_reports_to_is_not_themselves() {
        val wrongSuperiorAccount = validSubordinateAccount.copy(
            reportsToId = OrgNodeId("emp-other-superior")
        )
        val result = AccountCreationPolicy.validateCreation(
            creatorRole = Role.SALES,
            creatorNode = salesHeadNode,
            target = wrongSuperiorAccount
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("wajib melapor langsung ke Kepala Divisi") == true)
    }

    @Test
    fun regular_operator_should_be_forbidden_from_creating_accounts() {
        val operatorNode = OrgNode(
            id = OrgNodeId("emp-agus"),
            name = "Agus",
            email = "agus@wemade.id",
            department = Department.PRODUCTION_PPIC,
            level = HierarchyLevel.STAFF_OPERATOR,
            roleTitle = "Operator Jahit"
        )
        val result = AccountCreationPolicy.validateCreation(
            creatorRole = Role.OPERATOR,
            creatorNode = operatorNode,
            target = validSubordinateAccount
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("tidak memiliki wewenang") == true)
    }
}

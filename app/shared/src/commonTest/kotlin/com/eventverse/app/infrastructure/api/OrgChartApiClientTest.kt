package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.orgchart.HierarchyLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OrgChartApiClientTest {

    @Test
    fun testParseDepartment() {
        val json = """{"id":"dept-sales","code":"SALES","displayName":"Sales & Marketing","shortName":"Sales","colorHex":4280572883,"isCustom":false,"tenantId":"ten-demo-001"}"""
        val dept = OrgChartApiClient.parseDepartment(json)
        assertEquals("dept-sales", dept.id.value)
        assertEquals("SALES", dept.code)
        assertEquals("Sales & Marketing", dept.displayName)
        assertEquals("Sales", dept.shortName)
        assertEquals(4280572883L, dept.colorHex)
        assertEquals(false, dept.isCustom)
        assertEquals("ten-demo-001", dept.tenantId?.value)
    }

    @Test
    fun testParseOrgNode() {
        val json = """{"id":"emp-budi","name":"Budi Santoso","email":"budi.santoso@wemade.id","department":{"id":"dept-sales","code":"SALES","displayName":"Sales & Marketing","shortName":"Sales","colorHex":4280572883,"isCustom":false,"tenantId":""},"level":"HEAD_OF_DEPARTMENT","roleTitle":"Head of Sales & Marketing","reportsToId":"emp-direktur","phone":"08123456789","tenantId":"ten-demo-001"}"""
        val node = OrgChartApiClient.parseOrgNode(json)
        assertEquals("emp-budi", node.id.value)
        assertEquals("Budi Santoso", node.name)
        assertEquals("budi.santoso@wemade.id", node.email)
        assertEquals("dept-sales", node.department?.id?.value)
        assertEquals(HierarchyLevel.HEAD_OF_DEPARTMENT, node.level)
        assertEquals("Head of Sales & Marketing", node.roleTitle)
        assertEquals("emp-direktur", node.reportsToId?.value)
        assertEquals("08123456789", node.phone)
        assertEquals("ten-demo-001", node.tenantId?.value)
    }

    @Test
    fun testParseJsonArray() {
        val json = """[{"id":"1","name":"A"},{"id":"2","name":"B"}]"""
        val items = OrgChartApiClient.parseJsonArray(json)
        assertEquals(2, items.size)
        assertEquals("""{"id":"1","name":"A"}""", items[0])
        assertEquals("""{"id":"2","name":"B"}""", items[1])
    }

    @Test
    fun testParseEmailConflict() {
        val json = """{"error":"EMAIL_CONFLICT","email":"dimas.sales@wemade.id","existingEmployeeId":"emp-dimas-001","existingEmployeeName":"Dimas Pratama","existingDepartmentName":"Sales & Pemasaran","existingRoleTitle":"Sales Eksekutif","isArchived":true,"message":"Email sudah terdaftar"}"""
        val conflict = OrgChartApiClient.parseEmailConflict(json)
        assertNotNull(conflict)
        assertEquals("dimas.sales@wemade.id", conflict.email)
        assertEquals("emp-dimas-001", conflict.existingEmployeeId)
        assertEquals("Dimas Pratama", conflict.existingEmployeeName)
        assertEquals("Sales & Pemasaran", conflict.existingDepartmentName)
        assertEquals("Sales Eksekutif", conflict.existingRoleTitle)
        assertEquals(true, conflict.isArchived)
    }
}


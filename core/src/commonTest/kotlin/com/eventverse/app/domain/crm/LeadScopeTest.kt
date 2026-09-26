package com.eventverse.app.domain.crm

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeadScopeTest {

    private val tenantId = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private val sales = Department(
        id = DepartmentId("dept-sales"), code = "sales", displayName = "Penjualan",
        shortName = "Sales", colorHex = 0xFF2563EB
    )

    private fun employee(id: String, dept: Department?, reportsTo: String? = null) = OrgNode(
        id = OrgNodeId(id), name = id, email = "$id@pabrik.test", department = dept,
        level = HierarchyLevel.STAFF_OPERATOR, roleTitle = id,
        reportsToId = reportsTo?.let { OrgNodeId(it) }
    )

    private val kepalaSales = employee("emp-budi", sales)
    private val salesA = employee("emp-ani", sales, "emp-budi")
    private val outsider = employee("emp-joko", null)

    private val employees = listOf(kepalaSales, salesA, outsider)

    private fun lead(id: String, owner: OrgNodeId?) = CrmLead(
        id = LeadId(id), tenantId = tenantId, brandName = BrandName("Brand $id"),
        ownerEmployeeId = owner, createdAt = now, updatedAt = now
    )

    private val leadOwnedByBudi = lead("lead-1", kepalaSales.id)
    private val leadOwnedByAni = lead("lead-2", salesA.id)
    private val leadUnassigned = lead("lead-3", null)

    private val allLeads = listOf(leadOwnedByBudi, leadOwnedByAni, leadUnassigned)

    @Test
    fun allTenantData_returnsEverything_includingUnassigned() {
        val visible = LeadScope.visibleTo(allLeads, DataScope.ALL_TENANT_DATA, employees, kepalaSales.id, sales.id.value)
        assertEquals(allLeads, visible)
    }

    @Test
    fun ownDataOnly_returnsOnlyOwnLeads() {
        val visible = LeadScope.visibleTo(allLeads, DataScope.OWN_DATA_ONLY, employees, kepalaSales.id, sales.id.value)
        assertEquals(listOf(leadOwnedByBudi), visible)
    }

    @Test
    fun subordinateData_includesCommandChainReports() {
        val visible = LeadScope.visibleTo(allLeads, DataScope.SUBORDINATE_DATA, employees, kepalaSales.id, sales.id.value)
            .map { it.id.value }.toSet()
        assertEquals(setOf("lead-1", "lead-2"), visible)
    }

    @Test
    fun subordinateData_excludesUnassignedLeads() {
        // Unassigned leads are visible only to an unrestricted (ALL_TENANT_DATA) caller —
        // mirroring OrgChartDataReach's rule for a null department.
        val visible = LeadScope.visibleTo(allLeads, DataScope.SUBORDINATE_DATA, employees, kepalaSales.id, sales.id.value)
        assertTrue(leadUnassigned !in visible)
    }

    @Test
    fun ownDataOnly_viewerNotAnEmployee_returnsNothing() {
        val visible = LeadScope.visibleTo(allLeads, DataScope.OWN_DATA_ONLY, employees, null, null)
        assertTrue(visible.isEmpty())
    }

    @Test
    fun reachableOwnerIds_allTenantData_returnsNullMeaningUnrestricted() {
        val ids = LeadScope.reachableOwnerIds(DataScope.ALL_TENANT_DATA, employees, kepalaSales.id, sales.id.value)
        assertEquals(null, ids)
    }
}

package com.eventverse.app.domain.orgchart

import com.eventverse.app.domain.rbac.DataScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrgChartVisibilityTest {

    private val sales = Department(
        id = DepartmentId("dept-sales"),
        code = "sales",
        displayName = "Penjualan",
        shortName = "Sales",
        colorHex = 0xFF2563EB
    )

    private val warehouse = Department(
        id = DepartmentId("dept-warehouse"),
        code = "warehouse",
        displayName = "Gudang",
        shortName = "Gudang",
        colorHex = 0xFFD97706
    )

    private fun employee(
        id: String,
        name: String,
        department: Department?,
        level: HierarchyLevel,
        reportsTo: String? = null
    ) = OrgNode(
        id = OrgNodeId(id),
        name = name,
        email = "$id@pabrik.test",
        department = department,
        level = level,
        roleTitle = name,
        reportsToId = reportsTo?.let { OrgNodeId(it) }
    )

    // Direktur → kepala sales → dua staf sales; ditambah satu orang gudang yang tidak berelasi.
    private val direktur = employee("emp-direktur", "Hendra", null, HierarchyLevel.EXECUTIVE)
    private val kepalaSales = employee("emp-budi", "Budi", sales, HierarchyLevel.HEAD_OF_DEPARTMENT, "emp-direktur")
    private val salesA = employee("emp-ani", "Ani", sales, HierarchyLevel.STAFF_OPERATOR, "emp-budi")
    private val salesB = employee("emp-rina", "Rina", sales, HierarchyLevel.STAFF_OPERATOR, "emp-budi")
    private val gudang = employee("emp-joko", "Joko", warehouse, HierarchyLevel.STAFF_OPERATOR, "emp-direktur")

    private val all = listOf(direktur, kepalaSales, salesA, salesB, gudang)

    @Test
    fun allTenantData_shouldReturnEveryone() {
        val visible = OrgChartVisibility.visibleTo(
            nodes = all,
            scope = DataScope.ALL_TENANT_DATA,
            viewerEmployeeId = kepalaSales.id,
            viewerDepartmentId = sales.id.value
        )

        assertEquals(all, visible)
    }

    @Test
    fun ownDataOnly_shouldReturnOnlyTheViewer() {
        val visible = OrgChartVisibility.visibleTo(
            nodes = all,
            scope = DataScope.OWN_DATA_ONLY,
            viewerEmployeeId = kepalaSales.id,
            viewerDepartmentId = sales.id.value
        )

        assertEquals(listOf(kepalaSales), visible)
    }

    @Test
    fun subordinateData_shouldCoverOwnDepartmentAndNotOthers() {
        val visible = OrgChartVisibility.visibleTo(
            nodes = all,
            scope = DataScope.SUBORDINATE_DATA,
            viewerEmployeeId = kepalaSales.id,
            viewerDepartmentId = sales.id.value
        ).map { it.id.value }.toSet()

        assertEquals(setOf("emp-budi", "emp-ani", "emp-rina"), visible)
    }

    @Test
    fun subordinateData_shouldFollowTheCommandChainAcrossDepartments() {
        // Kepala sales yang juga membawahi seorang staf gudang harus melihat staf itu, meski
        // divisinya berbeda. Memakai divisi saja akan memotong bawahan lintas divisi.
        val gudangUnderBudi = gudang.copy(reportsToId = kepalaSales.id)
        val nodes = listOf(direktur, kepalaSales, salesA, salesB, gudangUnderBudi)

        val visible = OrgChartVisibility.visibleTo(
            nodes = nodes,
            scope = DataScope.SUBORDINATE_DATA,
            viewerEmployeeId = kepalaSales.id,
            viewerDepartmentId = sales.id.value
        ).map { it.id.value }.toSet()

        assertTrue("emp-joko" in visible, "Bawahan lintas divisi harus ikut terlihat")
    }

    @Test
    fun subordinateData_shouldReachIndirectReports() {
        // Direktur tanpa divisi: satu-satunya sumbunya adalah rantai komando, dan ia harus menembus
        // lebih dari satu tingkat.
        val visible = OrgChartVisibility.visibleTo(
            nodes = all,
            scope = DataScope.SUBORDINATE_DATA,
            viewerEmployeeId = direktur.id,
            viewerDepartmentId = null
        ).map { it.id.value }.toSet()

        assertEquals(setOf("emp-direktur", "emp-budi", "emp-ani", "emp-rina", "emp-joko"), visible)
    }

    @Test
    fun subordinateData_withCyclicReportingShouldTerminate() {
        // Hierarki diketik manusia dan bisa memuat siklus akibat salah input. Penelusuran harus
        // berhenti dan menampilkan bagan yang sedikit keliru, bukan menggantung selamanya.
        val budiReportsToAni = kepalaSales.copy(reportsToId = salesA.id)
        val nodes = listOf(budiReportsToAni, salesA)

        val visible = OrgChartVisibility.visibleTo(
            nodes = nodes,
            scope = DataScope.SUBORDINATE_DATA,
            viewerEmployeeId = budiReportsToAni.id,
            viewerDepartmentId = sales.id.value
        )

        assertEquals(2, visible.size)
    }

    @Test
    fun ownDataOnly_whenViewerIsNotAnEmployee_shouldReturnNothing() {
        // Akun platform yang tidak punya baris karyawan. Mengembalikan seluruh pabrik di sini akan
        // membuat jangkauan tersempit justru menjadi yang terluas.
        val visible = OrgChartVisibility.visibleTo(
            nodes = all,
            scope = DataScope.OWN_DATA_ONLY,
            viewerEmployeeId = null,
            viewerDepartmentId = sales.id.value
        )

        assertTrue(visible.isEmpty())
    }
}

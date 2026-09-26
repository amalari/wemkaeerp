package com.eventverse.app.domain.orgchart

import com.eventverse.app.domain.rbac.DataScope

/**
 * Menyaring daftar karyawan menurut jangkauan data ([DataScope]) yang diberikan kepada penonton.
 *
 * Ada karena modul Bagan Organisasi bersifat [com.eventverse.app.domain.rbac.ScopeCapability.HIERARCHICAL]:
 * memilih "Data Bawahan" pada matriks wewenang harus benar-benar mempersempit isi layar, bukan hanya
 * mengubah label. Tanpa fungsi ini, admin yang memilih jangkauan sempit tetap melihat seluruh pabrik
 * dan menyimpulkan bahwa pengaturannya tidak berfungsi — kesimpulan yang tidak akan pernah terbantah
 * oleh layar.
 *
 * Layanan domain murni: tanpa I/O, tanpa framework. Itu disengaja, sebab penyaringan yang sama harus
 * dijalankan **dua kali** — di server sebagai penentu (klien tidak boleh dipercaya menyembunyikan
 * datanya sendiri) dan di klien supaya layar langsung konsisten. Satu fungsi, dua pemanggil, mustahil
 * keduanya menyimpang.
 */
object OrgChartVisibility {

    /**
     * @param nodes seluruh karyawan satu tenant (isolasi antar-tenant sudah terjadi di lapisan atas)
     * @param scope jangkauan efektif penonton atas modul [com.eventverse.app.domain.rbac.BusinessModule.ORG_CHART]
     * @param viewerEmployeeId baris karyawan milik penonton sendiri, bila ia memang seorang karyawan
     * @param viewerDepartmentId divisi penonton
     */
    fun visibleTo(
        nodes: List<OrgNode>,
        scope: DataScope,
        viewerEmployeeId: OrgNodeId?,
        viewerDepartmentId: String?
    ): List<OrgNode> = when (scope) {
        DataScope.ALL_TENANT_DATA -> nodes

        // Logika "bawahan" (union divisi + rantai komando) kini hidup di [SubordinateResolver],
        // dipakai bersama modul manapun yang bersifat HIERARCHICAL (CRM Leads termasuk) —
        // menyalin-tempelnya di sini akan membuka peluang dua definisi "bawahan" yang menyimpang.
        DataScope.SUBORDINATE_DATA -> {
            val reachIds = SubordinateResolver.reachableEmployeeIds(nodes, viewerEmployeeId, viewerDepartmentId)
            nodes.filter { it.id in reachIds }
        }

        // Bagan yang hanya berisi satu orang memang tidak berguna sebagai bagan, dan itulah maksud
        // "Data Sendiri": jangkauan ini dipakai untuk jabatan yang tidak berkepentingan atas struktur
        // orang lain. Yang ingin melihat timnya diberi SUBORDINATE_DATA.
        DataScope.OWN_DATA_ONLY ->
            viewerEmployeeId?.let { id -> nodes.filter { it.id == id } }.orEmpty()
    }
}

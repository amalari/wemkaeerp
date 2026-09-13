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

        DataScope.SUBORDINATE_DATA -> {
            // Dua sumbu disatukan, bukan dipilih salah satu: "bawahan" di pabrik berarti satu divisi
            // (kepala gudang atas stafnya) *dan* rantai komando (kepala produksi atas lead yang
            // berada di divisi lain). Memakai hanya divisi akan memotong bawahan lintas divisi;
            // memakai hanya rantai komando akan menyembunyikan rekan sedivisi yang tidak melapor
            // langsung, sehingga bagannya tampak bolong.
            val byDepartment = viewerDepartmentId
                ?.let { deptId -> nodes.filter { it.department?.id?.value == deptId } }
                .orEmpty()

            val byCommandChain = viewerEmployeeId
                ?.let { subordinateClosure(nodes, it) }
                .orEmpty()

            val self = viewerEmployeeId?.let { id -> nodes.filter { it.id == id } }.orEmpty()

            // distinctBy id: seseorang lazim tercakup lewat kedua sumbu sekaligus.
            (self + byDepartment + byCommandChain).distinctBy { it.id.value }
        }

        // Bagan yang hanya berisi satu orang memang tidak berguna sebagai bagan, dan itulah maksud
        // "Data Sendiri": jangkauan ini dipakai untuk jabatan yang tidak berkepentingan atas struktur
        // orang lain. Yang ingin melihat timnya diberi SUBORDINATE_DATA.
        DataScope.OWN_DATA_ONLY ->
            viewerEmployeeId?.let { id -> nodes.filter { it.id == id } }.orEmpty()
    }

    /**
     * Seluruh bawahan [rootId] sampai ke bawah, mengikuti [OrgNode.reportsToId].
     *
     * Ditelusuri iteratif dengan himpunan "sudah dikunjungi", bukan rekursif. Data hierarki diketik
     * manusia dan bisa memuat siklus (A melapor ke B, B melapor ke A) akibat salah input; penelusuran
     * rekursif polos akan menggantung selamanya alih-alih menampilkan bagan yang sedikit keliru.
     */
    private fun subordinateClosure(nodes: List<OrgNode>, rootId: OrgNodeId): List<OrgNode> {
        val childrenByParent: Map<String, List<OrgNode>> = nodes
            .mapNotNull { node -> node.reportsToId?.let { it.value to node } }
            .groupBy({ it.first }, { it.second })

        val collected = mutableListOf<OrgNode>()
        val visited = mutableSetOf(rootId.value)
        val queue = ArrayDeque<String>().apply { add(rootId.value) }

        while (queue.isNotEmpty()) {
            val parentId = queue.removeFirst()
            childrenByParent[parentId].orEmpty().forEach { child ->
                if (visited.add(child.id.value)) {
                    collected += child
                    queue.add(child.id.value)
                }
            }
        }

        return collected
    }
}

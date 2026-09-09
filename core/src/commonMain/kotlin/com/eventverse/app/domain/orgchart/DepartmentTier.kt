package com.eventverse.app.domain.orgchart

/**
 * Tingkat wewenang dinamis di dalam suatu divisi (contoh: Kepala Divisi, Kepala Tim, Mandor, Staf).
 * Menempel langsung ke divisi bersangkutan sehingga tiap divisi bisa memiliki struktur tier yang berbeda.
 */
data class DepartmentTier(
    val id: String,
    val name: String,
    val rank: Int = 1
) {
    init {
        require(id.isNotBlank()) { "DepartmentTier ID cannot be blank" }
        require(name.isNotBlank()) { "DepartmentTier name cannot be blank" }
    }

    val isHead: Boolean get() = rank == 1 || id == "head"
}

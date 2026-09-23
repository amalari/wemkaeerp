package com.eventverse.app.domain.vendor

import kotlinx.datetime.LocalDate

/**
 * Satu proses berstatus "Vendor Luar" di alur sebuah order — kebutuhan yang menunggu admin
 * produksi menunjuk vendornya.
 *
 * Kebutuhan ini **diturunkan**, bukan disimpan: staf sampling cukup memilih "Vendor Luar" saat
 * menyusun alur, dan setiap proses `SUBCONTRACTED` otomatis muncul di antrean. Tidak ada tombol
 * "minta vendor" yang bisa lupa ditekan.
 */
data class SubcontractNeed(
    val subjectId: String,
    val subjectLabel: String,
    val clientName: String,
    val styleName: String,
    val processCode: String,
    val processName: String,
    val quantityPcs: Int,
    val dueDate: LocalDate? = null
)

/** Satu baris antrean: kebutuhan beserta penugasannya bila sudah ada. */
data class VendorQueueItem(
    val need: SubcontractNeed,
    val assignment: VendorAssignment? = null
) {
    val isPending: Boolean get() = assignment == null
}

object VendorAssignmentQueue {
    /**
     * Memasangkan kebutuhan dengan penugasan aktifnya. Yang belum punya pasangan diletakkan di
     * depan dan diurutkan dari tenggat terdekat — itulah yang harus dikerjakan admin lebih dulu.
     */
    fun build(needs: List<SubcontractNeed>, activeAssignments: List<VendorAssignment>): List<VendorQueueItem> =
        needs
            .map { need ->
                VendorQueueItem(
                    need = need,
                    assignment = activeAssignments.firstOrNull { it.isActive && it.covers(need.subjectId, need.processCode) }
                )
            }
            .sortedWith(
                compareByDescending<VendorQueueItem> { it.isPending }
                    .thenBy(nullsLast()) { it.need.dueDate }
                    .thenBy { it.need.subjectLabel }
            )
}

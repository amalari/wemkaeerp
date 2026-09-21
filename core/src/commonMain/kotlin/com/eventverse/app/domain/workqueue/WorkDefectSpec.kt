package com.eventverse.app.domain.workqueue

import com.eventverse.app.domain.pipeline.DefectLiability

/**
 * Spesifikasi jenis cacat dan rute penanganan revisinya.
 *
 * [targetStationCode] menentukan stasiun/rak tujuan di mana perbaikan harus dilakukan.
 * [liability] menentukan pihak yang menanggung biaya pengerjaan ulang (Kontrak 5).
 */
data class WorkDefectSpec(
    val code: DefectCode,
    val displayName: String,
    val liability: DefectLiability,
    val targetStationCode: WorkStationCode,
    val description: String = ""
) {
    init {
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
    }
}

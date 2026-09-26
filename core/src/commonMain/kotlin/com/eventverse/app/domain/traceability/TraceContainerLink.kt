package com.eventverse.app.domain.traceability

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Satu ruas silsilah: sekian set dari sebuah bundel masuk ke sebuah karung.
 *
 * Disimpan sebagai tautan berkuantitas, bukan `consumedBundleIds: List<String>` di dalam karung.
 * Alasannya bukan kerapian: bundel yang set-nya tidak lengkap cepat atau lambat akan terpakai
 * sebagian di dua karung, dan daftar id tidak punya tempat untuk menaruh "sebagian". Menyimpan
 * [consumedPcs] sejak hari pertama membuat pembukaan kasus itu nanti cukup melonggarkan validasi —
 * tanpa migrasi database.
 */
data class TraceContainerLink(
    val tenantId: TenantId,
    val parentId: TraceContainerId,
    val childId: TraceContainerId,
    val consumedPcs: Int,
    val linkedAt: Instant
) {
    init {
        require(parentId != childId) { "Wadah tidak boleh menjadi induk bagi dirinya sendiri" }
        require(consumedPcs > 0) { "Jumlah yang dituang harus lebih dari 0 pcs" }
    }
}

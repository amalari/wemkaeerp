package com.eventverse.app.domain.sampling.storage

import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Catatan kustodi satu SPK selama berada di penyimpanan: siapa yang menerimanya dari
 * pengemasan, disimpan di mana, dan siapa yang melepasnya ke pengiriman.
 *
 * ## Kenapa aggregate sendiri, bukan field di `SamplingOrder`
 *
 * Kustodi punya siklus hidupnya sendiri (masuk → dilepas) dan pemegangnya sendiri. Barang
 * selesai packing tidak pernah langsung dikirim — ia ditaruh dulu, lalu dikeluarkan oleh
 * orang yang bertanggung jawab. Dua orang itulah yang ditanyai saat sampel dicari, jadi
 * keduanya wajib tercatat di sini, bukan disimpulkan dari jejak audit tahap.
 */
data class SampleStorageRecord(
    val id: SampleStorageRecordId,
    val tenantId: TenantId,
    val orderId: SamplingOrderId,
    val dealId: String? = null,
    val location: StorageLocationLabel,
    val qtyPcs: Int,
    val storedBy: StorageCustodian,
    val storedAt: Instant,
    val releasedBy: StorageCustodian? = null,
    val releasedAt: Instant? = null,
    /** Terisi = barang dilepas sebelum seluruh SPK sedeal tersimpan (kirim parsial). */
    val partialReason: String? = null
) {
    init {
        require(qtyPcs > 0) { "Jumlah yang disimpan wajib lebih dari 0 pcs" }
    }

    val isReleased: Boolean get() = releasedAt != null

    fun release(pic: StorageCustodian, now: Instant, partialReason: String? = null): SampleStorageRecord {
        require(!isReleased) {
            "Barang sudah dilepas dari penyimpanan oleh ${releasedBy?.displayName ?: "-"}"
        }
        return copy(
            releasedBy = pic,
            releasedAt = now,
            partialReason = partialReason?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}

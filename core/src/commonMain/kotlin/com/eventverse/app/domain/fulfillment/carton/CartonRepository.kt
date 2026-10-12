package com.eventverse.app.domain.fulfillment.carton

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline

/**
 * Operasi domain karton packing — bukan DAO generik.
 *
 * Tidak ada `delete`: karton yang salah diselesaikan dengan reopen dan perbaikan,
 * bukan dengan menghapus jejaknya. Setiap perubahan status wajib meninggalkan event
 * karena pengawasan adalah alasan modul ini ada.
 */
interface CartonRepository {

    suspend fun findById(tenantId: TenantId, id: CartonId): PackingCarton?

    suspend fun findAll(tenantId: TenantId): List<PackingCarton>

    suspend fun findByStatus(tenantId: TenantId, status: CartonStatus): List<PackingCarton>

    suspend fun findByNumber(tenantId: TenantId, number: CartonNumber): PackingCarton?

    suspend fun findOpenCartons(tenantId: TenantId): List<PackingCarton>

    suspend fun save(carton: PackingCarton): PackingCarton

    suspend fun recordEvent(
        tenantId: TenantId,
        cartonId: CartonId,
        eventType: String,
        actor: String,
        detail: String,
        occurredAt: Instant
    )

    suspend fun getPackingQueue(tenantId: TenantId): List<PackingQueueItem>

    /**
     * Nomor urut karton berikutnya untuk `(tenant, tahun)` — dipakai use case U2
     * untuk membentuk `CartonNumber` (`CTN-<tahun>-<seq>`). Implementasi wajib
     * transaksional (counter tabel / max+1 dalam transaksi) agar dua pembuat karton
     * bersamaan tidak mendapat nomor sama; bentrok unik → 409 di lapisan server.
     */
    suspend fun getNextCartonSequence(tenantId: TenantId, year: Int): Int
}

@JvmInline
value class CartonId(val value: String) {
    init {
        require(value.isNotBlank()) { "CartonId tidak boleh kosong" }
        require(value.length <= 64) { "CartonId maksimal 64 karakter" }
    }
}

data class PackingQueueItem(
    val unitTicketId: String,
    val productionRef: String,
    val sizeLabel: String,
    val colorway: String,
    val receivedAt: Instant
)

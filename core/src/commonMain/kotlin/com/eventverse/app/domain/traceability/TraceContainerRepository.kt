package com.eventverse.app.domain.traceability

import com.eventverse.app.domain.tenant.TenantId

/**
 * Operasi domain telusur — bukan DAO generik.
 *
 * Tidak ada `delete`: kartu yang sudah di-scan adalah catatan kejadian di lantai produksi. Koreksi
 * dilakukan dengan penyesuaian yang tercatat, bukan dengan menghapus jejaknya.
 */
interface TraceContainerRepository {

    suspend fun findByCode(tenantId: TenantId, code: TraceCode): TraceContainer?

    suspend fun findByWorkOrder(tenantId: TenantId, ref: TraceWorkOrderRef): List<TraceContainer>

    suspend fun findByIds(tenantId: TenantId, ids: List<TraceContainerId>): List<TraceContainer>

    /**
     * Menyimpan wadah baru hanya bila kodenya belum ada, lalu selalu mengembalikan yang berlaku.
     *
     * Bentuk "sisipkan-kalau-belum-ada lalu baca" inilah yang membuat scan ganda aman: operator yang
     * menekan dua kali — dan dia akan menekan dua kali — menghasilkan satu baris dan dua respons
     * identik, bukan dua bundel kembar yang harus dibereskan manual.
     */
    suspend fun openIfAbsent(container: TraceContainer): TraceContainer

    suspend fun save(container: TraceContainer): TraceContainer

    suspend fun linksForWorkOrder(tenantId: TenantId, ref: TraceWorkOrderRef): List<TraceContainerLink>

    suspend fun linksForParent(tenantId: TenantId, parentId: TraceContainerId): List<TraceContainerLink>

    /** Menyimpan silsilah dan status bundel dalam satu transaksi — silsilah tanpa status adalah bohong. */
    suspend fun consumeIntoSack(sack: TraceContainer, links: List<TraceContainerLink>, bundles: List<TraceContainer>)

    /** Ordinal SPK untuk segmen kode; dibuat sekali lalu stabil selamanya. */
    suspend fun ensureWorkOrderOrdinal(tenantId: TenantId, ref: TraceWorkOrderRef): Int

    /** Jalur balik saat kartu pra-cetak di-scan sebelum wadahnya pernah dibuat. */
    suspend fun findWorkOrderByOrdinal(
        tenantId: TenantId,
        ordinal: Int,
        kind: TraceWorkOrderKind
    ): TraceWorkOrderRef?

    suspend fun tenantOrdinal(tenantId: TenantId): Int
}

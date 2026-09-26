package com.eventverse.app.domain.vendor

import com.eventverse.app.domain.tenant.TenantId

/**
 * Pintu dari domain vendor ke alur order yang memakai vendor.
 *
 * Domain vendor tidak tahu apa itu `SamplingOrder`; ia hanya perlu tiga hal dari "pemilik
 * alur": daftar proses yang butuh vendor, apakah barangnya sudah keluar pabrik, dan cara
 * menuliskan vendor terpilih ke alur. Implementasi untuk SPK sampling ada di server; PO
 * produksi massal kelak cukup menambah implementasi kedua.
 */
interface SubcontractFlowGateway {
    /** Proses `SUBCONTRACTED` pada order yang masih berjalan. */
    suspend fun openNeeds(tenantId: TenantId): List<SubcontractNeed>

    /** Satu kebutuhan, atau `null` bila prosesnya sudah tidak di-subkon-kan / ordernya tidak ada. */
    suspend fun findNeed(tenantId: TenantId, subjectId: String, processCode: String): SubcontractNeed?

    /**
     * `true` bila Surat Jalan ke vendor untuk proses ini sudah terbit. Setelah barang keluar,
     * mengganti vendor berarti memproses retur — bukan sekadar mengganti nama di layar.
     */
    suspend fun isDispatched(tenantId: TenantId, subjectId: String, processCode: String): Boolean

    /**
     * Menulis vendor terpilih ke alur order sebagai `vendorRef` (atau menghapusnya bila `null`).
     * Inilah yang membuka leg Surat Jalan ke vendor: proses subkon tanpa `vendorRef` tidak
     * menghasilkan leg sama sekali.
     */
    suspend fun linkVendor(tenantId: TenantId, subjectId: String, processCode: String, vendorRef: String?)
}

package com.eventverse.app.domain.traceability

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.tenant.TenantId

/**
 * Penunjuk ke SPK hulu tanpa mengimpor agregatnya.
 *
 * Traceability sengaja memegang `(kind, id)` dan bukan `SamplingOrderId`/`BulkWorkOrderId`, supaya
 * satu ruang kode bisa melayani dua penomoran SPK yang berbeda tanpa kedua agregat itu saling tahu —
 * dan supaya arah ketergantungannya tetap satu arah: traceability -> hulu, tidak pernah sebaliknya.
 */
data class TraceWorkOrderRef(
    val kind: TraceWorkOrderKind,
    val id: String
) {
    init {
        require(id.isNotBlank()) { "Id SPK rujukan tidak boleh kosong" }
        require(id.length <= 64) { "Id SPK rujukan maksimal 64 karakter" }
    }
}

/** Satu baris size pada SPK, beserta rencana jumlahnya. */
data class TraceSizeLine(
    val sizeLabel: String,
    val orderedPcs: Int
) {
    init {
        require(sizeLabel.isNotBlank()) { "Label size tidak boleh kosong" }
        require(orderedPcs >= 0) { "Jumlah pesanan size $sizeLabel tidak boleh negatif" }
    }
}

/**
 * Berapa lembar satu panel dibutuhkan untuk membentuk satu baju utuh.
 *
 * Wajib eksplisit, tidak boleh diturunkan dari `panelYields`: `PanelWeightGrams` hanya punya satu
 * field `sleeve` dan pemetaannya menghasilkan `SLEEVE_LEFT` saja, padahal satu baju butuh dua lengan.
 * Membacanya apa adanya membuat bundel berisi 10 lengan terhitung 10 set lengkap, bukan 5 — dan
 * seluruh angka rekonsiliasi karung ikut salah.
 */
data class PanelRequirement(
    val panel: GarmentPanel,
    val piecesPerGarment: Int
) {
    init {
        require(piecesPerGarment > 0) {
            "Panel ${panel.displayName} minimal 1 lembar per baju, diterima $piecesPerGarment"
        }
    }
}

/**
 * Potret SPK yang dibutuhkan traceability — sengaja minim dan netral terhadap agregat asalnya.
 *
 * Kalau read model ini tumbuh sampai menyerupai SPK aslinya, itu tanda traceability mulai
 * mengerjakan pekerjaan modul lain.
 */
data class TraceWorkOrderSnapshot(
    val ref: TraceWorkOrderRef,
    val tenantId: TenantId,
    /** Ordinal tenant, ikut terkode agar kartu milik pabrik lain tidak resolve di sini. */
    val tenantOrdinal: Int,
    /** Ordinal SPK dalam tenant — sumber segmen `oooo` pada kode, bukan nomor SPK yang tercetak. */
    val ordinal: Int,
    val spkNumber: String,
    val styleName: String,
    val clientName: String,
    val sizes: List<TraceSizeLine>,
    val panelRequirements: List<PanelRequirement>,
    val colorways: List<String> = emptyList()
) {
    fun sizeIndexOf(sizeLabel: String): Int? =
        sizes.indexOfFirst { it.sizeLabel.equals(sizeLabel, ignoreCase = true) }.takeIf { it >= 0 }

    fun sizeAt(index: Int): TraceSizeLine? = sizes.getOrNull(index)
}

/**
 * Penyedia potret SPK. Implementasinya hidup di `server/`, bukan di domain, supaya paket ini tetap
 * tidak tahu-menahu soal sampling maupun produksi massal.
 */
interface TraceWorkOrderProvider {
    suspend fun snapshot(tenantId: TenantId, ref: TraceWorkOrderRef): TraceWorkOrderSnapshot?
}

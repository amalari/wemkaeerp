package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.transfer.FlowNodeRef
import kotlin.jvm.JvmInline

/**
 * Kunci stabil sebuah rute serah terima karung, mis. `QC_RAJUT_TO_FINISHING`.
 *
 * **Kenapa string tersimpan, bukan enum** (Uji Variabilitas, TRD-FLOW-003 D1): daftar rute berbeda
 * antar tenant dan antar industri, dan admin pabrik boleh mengubahnya. Kode rajut lama identik dengan
 * nama enum `SackRoute` (D3), jadi nilai yang sudah tersimpan tetap sah tanpa backfill.
 *
 * Parser tunggalnya [parse]: nilai tak sah **ditolak**, tidak pernah jatuh ke rute lain.
 * Lebar maksimal 40 sepadan dengan kolom `fulfillment_transfers.leg` dan `fulfillment_route_settings.route`.
 */
@JvmInline
value class HandoverRouteCode(val value: String) {
    init {
        require(PATTERN.matches(value)) {
            "Kode rute tidak valid: '$value' (huruf besar, angka, garis bawah; diawali huruf; maks $MAX_LENGTH karakter)"
        }
    }

    companion object {
        const val MAX_LENGTH = 40
        private val PATTERN = Regex("^[A-Z][A-Z0-9_]{0,${MAX_LENGTH - 1}}$")

        /** Satu-satunya jalan membaca kode dari input luar (JSON, kolom DB, narasi). */
        fun parse(raw: String?): Result<HandoverRouteCode> = runCatching {
            HandoverRouteCode(requireNotNull(raw) { "Kode rute wajib diisi" })
        }
    }
}

/**
 * Satu rute serah terima karung milik tenant: dari mana ke mana barang berpindah antar divisi.
 *
 * [from] dan [to] **opsional** dan memakai [FlowNodeRef], kosakata yang sama dengan leg
 * (`FlowTransferLeg`). Rute **tidak diturunkan dari leg** (TRD-FLOW-003 D2): tenant satu atap tidak
 * punya leg sama sekali, tetapi tetap punya rute. Keduanya terisi hanya bila admin ingin rute ini
 * dapat dicocokkan ke simpul alur.
 *
 * Rute **tidak pernah dihapus** bila pernah dipakai perjalanan; ia dinonaktifkan ([active] = false)
 * supaya riwayat tetap terbaca.
 */
data class HandoverRoute(
    val code: HandoverRouteCode,
    val label: String,
    val from: FlowNodeRef? = null,
    val to: FlowNodeRef? = null,
    val sortOrder: Int = 0,
    val active: Boolean = true
) {
    init {
        require(label.isNotBlank()) { "Label rute ${code.value} tidak boleh kosong" }
        require(label.length <= MAX_LABEL_LENGTH) { "Label rute ${code.value} maksimal $MAX_LABEL_LENGTH karakter" }
        require(sortOrder >= 0) { "Urutan rute ${code.value} tidak boleh negatif" }
        require((from == null) == (to == null)) { "Rute ${code.value}: from dan to harus terisi bersamaan atau kosong bersamaan" }
        require(from == null || from != to) { "Rute ${code.value}: from dan to tidak boleh simpul yang sama" }
    }

    companion object {
        const val MAX_LABEL_LENGTH = 120
    }
}

/**
 * Daftar rute efektif sebuah tenant.
 *
 * ## Template, salinan, dan paritas (Kontrak 5)
 *
 * Template bawaan hidup di pack (`DomainPack.handoverRouteTemplate`). Tenant **tanpa baris tersimpan**
 * memakai template itu secara efektif ([resolve]) tanpa menyimpan apa pun: perilaku tenant lama
 * identik dengan kemarin, tidak ada seed diam-diam. Salinan baru tersimpan saat admin pertama kali
 * mengubah daftar.
 *
 * - Mengubah template pack **tidak** sampai ke tenant yang sudah punya salinan.
 * - Mengubah salinan tenant **tidak** mengubah perjalanan yang sudah berangkat (mereka membeku lewat
 *   `handover_mode` per baris).
 * - Daftar tersimpan yang **kosong** dibaca sebagai "belum disentuh"; untuk mematikan semua rute,
 *   nonaktifkan, jangan kosongkan.
 */
data class TenantHandoverRoutes(
    val tenantId: TenantId,
    val routes: List<HandoverRoute> = emptyList()
) {
    init {
        routes.groupingBy { it.code }.eachCount().filterValues { it > 1 }.keys.firstOrNull()?.let {
            error("Kode rute ganda '${it.value}' pada tenant ${tenantId.value}")
        }
    }

    /** Rute yang boleh dipilih untuk perjalanan baru, berurutan. */
    val active: List<HandoverRoute> get() = routes.filter { it.active }.sortedBy { it.sortOrder }

    fun find(code: HandoverRouteCode): HandoverRoute? = routes.firstOrNull { it.code == code }

    companion object {
        /** Salinan tersimpan bila ada, jika tidak template pack. Tidak menyimpan apa pun. */
        fun resolve(tenantId: TenantId, stored: TenantHandoverRoutes?, template: List<HandoverRoute>): TenantHandoverRoutes =
            stored?.takeIf { it.routes.isNotEmpty() } ?: TenantHandoverRoutes(tenantId, template)
    }
}

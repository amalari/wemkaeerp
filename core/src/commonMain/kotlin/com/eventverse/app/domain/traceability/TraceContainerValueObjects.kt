package com.eventverse.app.domain.traceability

import com.eventverse.app.domain.contracts.GarmentPanel
import kotlin.jvm.JvmInline

@JvmInline
value class TraceContainerId(val value: String) {
    init {
        require(value.isNotBlank()) { "TraceContainerId tidak boleh kosong" }
        require(value.length <= 64) { "TraceContainerId maksimal 64 karakter" }
    }
}

/**
 * Daur hidup satu wadah fisik.
 *
 * [ALLOCATED] adalah keadaan kartu yang sudah tercetak tapi belum disentuh siapa pun — tidak punya
 * baris database sama sekali. Barisnya lahir saat scan pertama ([OPENED]). Itulah yang membuat
 * pencetakan 200 kartu tidak meninggalkan 200 baris kosong yang harus dibersihkan kalau kartunya
 * hilang atau tidak jadi dipakai.
 */
enum class TraceContainerState(val displayName: String) {
    ALLOCATED("Kartu Tercetak"),
    OPENED("Sedang Diisi"),
    TALLIED("Sudah Dihitung"),
    CONSUMED("Sudah Dituang"),
    CLOSED("Ditutup");

    val isFinal: Boolean get() = this == CONSUMED || this == CLOSED
}

/** Hitungan satu jenis panel di dalam sebuah bundel. */
data class PanelTally(
    val panel: GarmentPanel,
    val pieces: Int
) {
    init { require(pieces >= 0) { "Hitungan panel ${panel.displayName} tidak boleh negatif" } }
}

/**
 * Penanda shift, ditulis operator dan disimpan terpisah dari `createdAt`.
 *
 * Dipisah karena jaringan pabrik putus adalah kejadian biasa: bundel dibentuk pukul 22.00, dikunci
 * ke sistem pukul 07.00 keesokan harinya. Kalau shift diturunkan dari jam penyimpanan, catatan akan
 * berbohong setiap kali ada gangguan — dan yang paling sering terganggu justru shift malam.
 */
@JvmInline
value class ShiftLabel(val value: String) {
    init { require(value.length <= 40) { "Label shift maksimal 40 karakter" } }

    val isBlank: Boolean get() = value.isBlank()
}

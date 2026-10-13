package com.eventverse.app.domain.pack

/**
 * Keputusan **eksplisit** apakah sebuah layar atau fitur punya baris sendiri di menu (TRD-I18N-003 F6,
 * `module-integration-rules.md` §5.7). Dipakai sebagai **parameter wajib tanpa nilai bawaan** supaya kompilator memaksa
 * keputusan itu pada setiap layar/fitur baru — "tampil karena default" tidak ada lagi.
 *
 * Enum/sealed sah (Uji Variabilitas): ini konsep **platform** (cara menu disusun), bukan kosakata industri.
 */
sealed interface MenuPlacement {

    /** Punya baris menu sendiri; entri modul/fitur yang `Shown` wajib membawa judul dua bahasa (`menuLabelText`). */
    data object Shown : MenuPlacement

    /** Tidak punya baris menu sendiri. [reason] wajib tak kosong — mis. "layar anak, dibuka dari workspace modul". */
    data class Hidden(val reason: String) : MenuPlacement {
        init { require(reason.isNotBlank()) { "Alasan Hidden wajib diisi (module-integration-rules §5.7)" } }
    }

    val isShown: Boolean get() = this is Shown
}

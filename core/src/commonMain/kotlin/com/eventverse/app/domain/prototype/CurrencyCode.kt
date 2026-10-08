package com.eventverse.app.domain.prototype

/**
 * Kode mata uang field `NUMBER(format = CURRENCY)` (C4 Irisan 2, keputusan D3): tiga huruf besar bergaya ISO 4217
 * (`IDR`, `USD`). Sengaja **bukan** daftar tertutup — mata uang adalah data per field (satu entitas boleh punya harga
 * IDR dan harga ekspor USD), jadi daftar di kode akan basi. Yang dijaga hanya bentuknya; kode tak berbentuk ini
 * **ditolak**, tidak diganti diam-diam menjadi `IDR`.
 *
 * Kode bawaan dari pack hanya mengisi usulan saat dibuat; setelah masuk dokumen, nilainya milik field.
 */
object CurrencyCode {
    private val SHAPE = Regex("[A-Z]{3}")

    fun isValid(code: String): Boolean = SHAPE.matches(code)
}
